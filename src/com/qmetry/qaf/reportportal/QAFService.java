/*******************************************************************************
 * QMetry Automation Framework provides a powerful and versatile platform to
 * author
 * Automated Test Cases in Behavior Driven, Keyword Driven or Code Driven
 * approach
 * Copyright 2016 Infostretch Corporation
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT
 * OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 * You should have received a copy of the GNU General Public License along with
 * this program in the name of LICENSE.txt in the root folder of the
 * distribution. If not, see https://opensource.org/licenses/gpl-3.0.html
 * See the NOTICE.TXT file in root folder of this source files distribution
 * for additional information regarding copyright ownership and licenses
 * of other open source software / files used by QMetry Automation Framework.
 * For any inquiry or need additional information, please contact
 * support-qaf@infostretch.com
 *******************************************************************************/
package com.qmetry.qaf.reportportal;

import static com.qmetry.qaf.automation.core.ConfigurationManager.getBundle;
import static rp.com.google.common.base.Optional.fromNullable;
import static rp.com.google.common.base.Strings.isNullOrEmpty;
import static rp.com.google.common.base.Throwables.getStackTraceAsString;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.IAttributes;
import org.testng.IRetryAnalyzer;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.testng.collections.Lists;
import org.testng.internal.ConstructorOrMethod;

import com.epam.reportportal.annotations.ParameterKey;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.testng.TestMethodType;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.issue.Issue;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.qmetry.qaf.automation.core.CheckpointResultBean;
import com.qmetry.qaf.automation.core.QAFTestBase;
import com.qmetry.qaf.automation.core.TestBaseProvider;
import com.qmetry.qaf.automation.keys.ApplicationProperties;
import com.qmetry.qaf.automation.step.StepExecutionTracker;
import com.qmetry.qaf.automation.step.client.TestNGScenario;
import com.qmetry.qaf.automation.step.client.text.BDDDefinitionHelper;
import com.qmetry.qaf.automation.step.client.text.BDDDefinitionHelper.ParamType;
import com.qmetry.qaf.automation.ui.webdriver.CommandTracker;
import com.qmetry.qaf.automation.util.StringMatcher;
import com.qmetry.qaf.automation.util.StringUtil;

import io.reactivex.Maybe;
import rp.com.google.common.annotations.VisibleForTesting;
import rp.com.google.common.base.Function;
import rp.com.google.common.base.Supplier;

/**
 * @author chirag
 *
 */
public class QAFService implements IQAFService {
	public QAFService() {
		this.launch = new MemoizingSupplier<Launch>(new Supplier<Launch>() {
			@Override
			public Launch get() {
				// this reads property, so we want to
				// init ReportPortal object each time Launch object is going to be created
				final ReportPortal reportPortal = ReportPortal.builder().build();
				StartLaunchRQ rq = buildStartLaunchRq(reportPortal.getParameters());
				rq.setStartTime(Calendar.getInstance().getTime());
				return reportPortal.newLaunch(rq);
			}
		});
	}

	public QAFService(Supplier<Launch> launch) {
		this.launch = new MemoizingSupplier<Launch>(launch);
	}

	public void sendLog(final String message, final String level, final long ts, final File file) {

		ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
			@Override
			public SaveLogRQ apply(String itemId) {
				SaveLogRQ rq = new SaveLogRQ();
				rq.setTestItemId(itemId);
				rq.setLevel(level);
				rq.setLogTime(new Date(ts));
				rq.setMessage(message);
				if (file != null) {
					rq.setFile(file);
				}
				return rq;
			}
		});
	}

	@Override
	public void logCommand(final CommandTracker commandHandler) {
		String message = "\t \t[" + commandHandler.getCommand() + "]:" + new Gson().toJson(commandHandler.getParameters());
		if(commandHandler.getResponce()!=null) {
			message=commandHandler.getResponce().toString();
		}
		String level = "DEBUG";
		if (commandHandler.hasException()) {
			message = message + "\n\t" + getStackTraceAsString(commandHandler.getException());
			// level = "ERROR";
		}
		sendLog(message, level);
	}

	@Override
	public void sendLog(String message, String level) {
		sendLog(message, level, System.currentTimeMillis(), null);
	}

	@Override
	public void beforeStep(StepExecutionTracker stepExecutionTracker) {
		stepExecutionTracker.getContext().put("parent", getBundle().getObject("rp_current_step"));
		getBundle().setProperty("rp_current_step", stepExecutionTracker);

		if (getBundle().getBoolean("rp.step.astest", false)) {
			Maybe<String> stepMaybe = startNonLeafNode(this.<Maybe<String>>getAttribute(stepExecutionTracker),
					stepExecutionTracker);
			stepExecutionTracker.getContext().put(RP_ID, stepMaybe);
		} else {
			sendLog(getDisplayName(stepExecutionTracker), "INFO", stepExecutionTracker.getStartTime(), null);
		}
	}

	@Override
	public void afterStepExecute(StepExecutionTracker stepExecutionTracker, String status) {
		getBundle().setProperty("rp_current_step", stepExecutionTracker.getContext().get("parent"));
		if (getBundle().getBoolean("rp.step.astest", false)) {
			finishStepItem(stepExecutionTracker, status);
		} else {
			File file = null;
			String screenshot = TestBaseProvider.instance().get().getLastCapturedScreenShot();
			if (StringUtil.isNotBlank(screenshot)) {
				try {
					file = new File();
					file.setContent(Resources.asByteSource(Resources.getResource(screenshot)).read());
					file.setContentType("image/png");
					file.setName(UUID.randomUUID().toString());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			String logLevel = getStepMessageType(stepExecutionTracker);
			sendLog(getDisplayName(stepExecutionTracker), logLevel, stepExecutionTracker.getStartTime(), file);
		}
	}

	@SuppressWarnings("unchecked")
	private void finishStepItem(StepExecutionTracker stepExecutionTracker, String status) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setStatus(status);
		rq.setEndTime(new Date());
		launch.get().finishTestItem((Maybe<String>) stepExecutionTracker.getContext().get(RP_ID), rq);
	}

	private Maybe<String> startNonLeafNode(Maybe<String> rootItemId, StepExecutionTracker stepExecutionTracker) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(stepExecutionTracker.getStep().getName());
		rq.setDescription(getDisplayName(stepExecutionTracker));
		// rq.setParameters(getDisplayName(stepExecutionTracker));

		// rq.setTags(extractTags(stepExecutionTracker.getStep().getMetaData()));
		rq.setStartTime(new Date());
		rq.setType("STEP");

		return launch.get().startTestItem(rootItemId, rq);
	}

	private String getDisplayName(StepExecutionTracker stepExecutionTracker) {
		String description = getIndent(stepExecutionTracker) + "\u261E "
				+ stepExecutionTracker.getStep().getDescription();
		Object[] actualArgs = stepExecutionTracker.getStep().getActualArgs();
		if (null == actualArgs || actualArgs.length <= 0)
			return description;
		List<String> args = BDDDefinitionHelper.getArgNames(description);
		if (args.isEmpty() || args.size() != actualArgs.length)
			return description;
		try {
			for (int i = 0; i < actualArgs.length; i++) {
				if ((actualArgs[i] instanceof String)) {
					if (description.indexOf("$") >= 0) {
						// If argument is already replaced
						// like COMMENT: {0} replaced with COMMENT: '${uname}'
						description = StringUtil.replace(description, "$" + args.get(i),
								getParam((String) actualArgs[i]), 1);
					} else {
						// If argument is not processed
						description = StringUtil.replace(description, args.get(i),
								"'" + getParam((String) actualArgs[i]) + "'", 1);
					}
				} else {
					description = StringUtil.replace(description, args.get(i),
							"'" + String.valueOf(actualArgs[i]) + "'", 1);
				}
			}
		} catch (Exception e) {
			// ignore
		}
		return description;
	}

	private String getParam(String text) {
		String result = getBundle().getSubstitutor().replace(text);
		ParamType ptype = ParamType.getType(result);
		if (ptype.equals(ParamType.MAP)) {
			@SuppressWarnings("unchecked")
			Map<String, Object> kv = new Gson().fromJson(result, Map.class);
			if (kv.containsKey("desc")) {
				result = String.valueOf(kv.get("desc"));
			} else if (kv.containsKey("description")) {
				result = String.valueOf(kv.get("description"));
			}
		}
		return result;
	}

	private String getStepMessageType(StepExecutionTracker stepExecutionTracker) {
		if (stepExecutionTracker.hasException() || StringUtil.isNotBlank(stepExecutionTracker.getVerificationError())) {
			return "ERROR";
		}
		QAFTestBase stb = TestBaseProvider.instance().get();
		List<CheckpointResultBean> subSteps = stb.getCheckPointResults();
		String type = "INFO";
		for (CheckpointResultBean subStep : subSteps) {
			if (StringMatcher.containsIgnoringCase("fail").match(subStep.getType())) {
				return "ERROR";
			}
			if (StringMatcher.containsIgnoringCase("warn").match(subStep.getType())) {
				type = "WARN";
			}
		}
		return type;
	}

	private String getIndent(StepExecutionTracker stepExecutionTracker) {
		if (stepExecutionTracker.getContext().get("parent") != null) {
			return " \u25AD\u25AD" + getIndent((StepExecutionTracker) stepExecutionTracker.getContext().get("parent"));
		}
		return "";
	}

	@SuppressWarnings("unchecked")
	protected <T> T getAttribute(StepExecutionTracker tracker) {
		StepExecutionTracker parent = (StepExecutionTracker) tracker.getContext().get("parent");
		if (null != parent) {
			return (T) parent.getContext().get(RP_ID);
		}

		ITestResult testResult = (ITestResult) getBundle().getObject(ApplicationProperties.CURRENT_TEST_RESULT.key);
		return getAttribute(testResult, RP_ID);

	}

	@Override
	public void finishTestMethod(String status, ITestResult testResult) {
		if (Statuses.SKIPPED.equals(status) && null == testResult.getAttribute(RP_ID)) {
			startTestMethod(testResult);
		}

		File logFile = QAFLogAppender.getFile();
		if (null != logFile) {
			sendLog("Log", "INFO", System.currentTimeMillis(), logFile);
		}
		FinishTestItemRQ rq = buildFinishTestMethodRq(status, testResult);
		launch.get().finishTestItem(this.<Maybe<String>>getAttribute(testResult, RP_ID), rq);
	}

	/**
	 * Extension point to customize test step creation event/request
	 *
	 * @param testResult
	 *            TestNG's testResult context
	 * @return Request to ReportPortal
	 */
	protected StartTestItemRQ buildStartStepRq(ITestResult testResult) {
		// if (testResult.getAttribute(RP_ID) != null) {
		// return null;
		// }
		StartTestItemRQ rq = new StartTestItemRQ();
		String testStepName;
		if (testResult.getTestName() != null) {
			testStepName = testResult.getTestName();
		} else {
			testStepName = testResult.getMethod().getMethodName();
		}
		rq.setName(testStepName);

		rq.setDescription(createStepDescription(testResult));
		rq.setParameters(createStepParameters(testResult));
		rq.setUniqueId(extractUniqueID(testResult));
		rq.setStartTime(new Date(testResult.getStartMillis()));
		rq.setType(TestMethodType.getStepType(testResult.getMethod()).toString());
		Set<String> tags = getTags(((TestNGScenario) testResult.getMethod()));
		rq.setTags(tags);
		rq.setRetry(isRetry(testResult));
		return rq;
	}

	private Set<String> getTags(TestNGScenario scenario) {
		Set<String> tags = new HashSet<String>(Arrays.asList(scenario.getGroups()));

		Map<String, Object> metadata = scenario.getMetaData();
		for (Entry<String, Object> entry : metadata.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (key.equalsIgnoreCase("groups") || key.equalsIgnoreCase("name") || key.equalsIgnoreCase("sign")
					|| key.equalsIgnoreCase("description") || null == value) {
				continue;
			}
			tags.add(key + ":" + value);
		}
		return tags;
	}

	///////////////////////

	public static final String NOT_ISSUE = "NOT_ISSUE";
	public static final String RP_ID = "rp_id";
	public static final String ARGUMENT = "arg";

	private final AtomicBoolean isLaunchFailed = new AtomicBoolean();

	protected MemoizingSupplier<Launch> launch;

	@Override
	public void startLaunch() {
		this.launch.get().start();
	}

	@Override
	public void finishLaunch() {
		FinishExecutionRQ rq = new FinishExecutionRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		rq.setStatus(isLaunchFailed.get() ? Statuses.FAILED : Statuses.PASSED);
		launch.get().finish(rq);

		this.launch.reset();

	}

	@Override
	public synchronized void startTestSuite(ISuite suite) {
		StartTestItemRQ rq = buildStartSuiteRq(suite);
		final Maybe<String> item = launch.get().startTestItem(rq);
		suite.setAttribute(RP_ID, item);
	}

	@Override
	public synchronized void finishTestSuite(ISuite suite) {
		if (null != suite.getAttribute(RP_ID)) {
			FinishTestItemRQ rq = buildFinishTestSuiteRq(suite);
			launch.get().finishTestItem(this.<Maybe<String>>getAttribute(suite, RP_ID), rq);
			suite.removeAttribute(RP_ID);
		}
	}

	@Override
	public void startTest(ITestContext testContext) {
		if (hasMethodsToRun(testContext)) {
			StartTestItemRQ rq = buildStartTestItemRq(testContext);
			final Maybe<String> testID = launch.get()
					.startTestItem(this.<Maybe<String>>getAttribute(testContext.getSuite(), RP_ID), rq);
			testContext.setAttribute(RP_ID, testID);
		}
	}

	@Override
	public void finishTest(ITestContext testContext) {
		if (hasMethodsToRun(testContext)) {
			FinishTestItemRQ rq = buildFinishTestRq(testContext);
			launch.get().finishTestItem(this.<Maybe<String>>getAttribute(testContext, RP_ID), rq);
		}
	}

	@Override
	public void startTestMethod(ITestResult testResult) {
		StartTestItemRQ rq = buildStartStepRq(testResult);
		if (rq == null) {
			return;
		}

		Maybe<String> stepMaybe = launch.get()
				.startTestItem(this.<Maybe<String>>getAttribute(testResult.getTestContext(), RP_ID), rq);
		testResult.setAttribute(RP_ID, stepMaybe);
	}

	@Override
	public void startConfiguration(ITestResult testResult) {
		TestMethodType type = TestMethodType.getStepType(testResult.getMethod());
		StartTestItemRQ rq = buildStartConfigurationRq(testResult, type);

		Maybe<String> parentId = getConfigParent(testResult, type);
		final Maybe<String> itemID = launch.get().startTestItem(parentId, rq);
		testResult.setAttribute(RP_ID, itemID);
	}

	@Override
	public void sendReportPortalMsg(final ITestResult result) {
		ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
			@Override
			public SaveLogRQ apply(String itemId) {
				SaveLogRQ rq = new SaveLogRQ();
				rq.setTestItemId(itemId);
				rq.setLevel("ERROR");
				rq.setLogTime(Calendar.getInstance().getTime());
				if (result.getThrowable() != null) {
					rq.setMessage(getStackTraceAsString(result.getThrowable()));
				} else {
					rq.setMessage("Test has failed without exception");
				}
				rq.setLogTime(Calendar.getInstance().getTime());

				return rq;
			}
		});

	}

	/**
	 * Extension point to customize suite creation event/request
	 *
	 * @param suite
	 *            TestNG suite
	 * @return Request to ReportPortal
	 */
	protected StartTestItemRQ buildStartSuiteRq(ISuite suite) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(suite.getName());
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType("SUITE");
		return rq;
	}

	/**
	 * Extension point to customize test creation event/request
	 *
	 * @param testContext
	 *            TestNG test context
	 * @return Request to ReportPortal
	 */
	protected StartTestItemRQ buildStartTestItemRq(ITestContext testContext) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(testContext.getName());
		rq.setStartTime(testContext.getStartDate());
		rq.setType("TEST");
		return rq;
	}

	/**
	 * Extension point to customize launch creation event/request
	 *
	 * @param parameters
	 *            Launch Configuration parameters
	 * @return Request to ReportPortal
	 */
	protected StartLaunchRQ buildStartLaunchRq(ListenerParameters parameters) {
		StartLaunchRQ rq = new StartLaunchRQ();
		rq.setName(parameters.getLaunchName());
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setTags(parameters.getTags());
		rq.setMode(parameters.getLaunchRunningMode());
		if (!isNullOrEmpty(parameters.getDescription())) {
			rq.setDescription(parameters.getDescription());
		}
		return rq;
	}

	/**
	 * Extension point to customize beforeXXX creation event/request
	 *
	 * @param testResult
	 *            TestNG's testResult context
	 * @param type
	 *            Type of method
	 * @return Request to ReportPortal
	 */
	protected StartTestItemRQ buildStartConfigurationRq(ITestResult testResult, TestMethodType type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		String configName = testResult.getMethod().getMethodName();
		rq.setName(configName);

		rq.setDescription(testResult.getMethod().getDescription());
		rq.setStartTime(new Date(testResult.getStartMillis()));
		rq.setType(type == null ? null : type.toString());
		return rq;
	}

	/**
	 * Extension point to customize test suite on it's finish
	 *
	 * @param suite
	 *            TestNG's suite context
	 * @return Request to ReportPortal
	 */
	protected FinishTestItemRQ buildFinishTestSuiteRq(ISuite suite) {
		/* 'real' end time */
		Date now = Calendar.getInstance().getTime();
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(now);
		rq.setStatus(getSuiteStatus(suite));
		return rq;
	}

	/**
	 * Extension point to customize test on it's finish
	 *
	 * @param testContext
	 *            TestNG test context
	 * @return Request to ReportPortal
	 */
	protected FinishTestItemRQ buildFinishTestRq(ITestContext testContext) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(testContext.getEndDate());
		String status = isTestPassed(testContext) ? Statuses.PASSED : Statuses.FAILED;
		rq.setStatus(status);
		return rq;
	}

	/**
	 * Extension point to customize test method on it's finish
	 *
	 * @param testResult
	 *            TestNG's testResult context
	 * @return Request to ReportPortal
	 */
	protected FinishTestItemRQ buildFinishTestMethodRq(String status, ITestResult testResult) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		StringBuilder description = new StringBuilder();
		if(null!=testResult.getMethod().getDescription()) {
			description.append(testResult.getMethod().getDescription());
		}

		for(CheckpointResultBean bean : TestBaseProvider.instance().get().getCheckPointResults()) {
			description.append("\n");
			if(bean.getType().toUpperCase().contains("PASS")) {
				description.append("\u2611 ");
				description.append(bean.getMessage());
			}else if (bean.getType().toUpperCase().contains("FAIL")){
				description.append("`\u2612 ");
				description.append(bean.getMessage());
				description.append("`");
			}else if (bean.getType().toUpperCase().contains("WARN")){
				description.append("`\u26A0 ");
				description.append(bean.getMessage());
				description.append("`");			}
		}
		
		System.out.println(description);
		rq.setDescription(description.toString());
		rq.setEndTime(new Date(testResult.getEndMillis()));
		rq.setStatus(status);
		// Allows indicate that SKIPPED is not to investigate items for WS
		if (Statuses.SKIPPED.equals(status)
				&& !fromNullable(launch.get().getParameters().getSkippedAnIssue()).or(false)) {
			Issue issue = new Issue();
			issue.setIssueType(NOT_ISSUE);
			rq.setIssue(issue);
		}
		return rq;
	}

	/**
	 * Extension point to customize Report Portal test parameters
	 *
	 * @param testResult
	 *            TestNG's testResult context
	 * @return Test/Step Parameters being sent to Report Portal
	 */
	protected List<ParameterResource> createStepParameters(ITestResult testResult) {
		List<ParameterResource> parameters = Lists.newArrayList();
		Test testAnnotation = getMethodAnnotation(Test.class, testResult);
		Parameters parametersAnnotation = getMethodAnnotation(Parameters.class, testResult);
		if (null != testAnnotation && !isNullOrEmpty(testAnnotation.dataProvider())) {
			parameters = createDataProviderParameters(testResult);
		} else if (null != parametersAnnotation) {
			parameters = createAnnotationParameters(testResult, parametersAnnotation);
		}
		return parameters.isEmpty() ? null : parameters;
	}

	/**
	 * Process testResult to create parameters provided via {@link Parameters}
	 *
	 * @param testResult
	 *            TestNG's testResult context
	 * @param parametersAnnotation
	 *            Annotation with parameters
	 * @return Step Parameters being sent to Report Portal
	 */
	private List<ParameterResource> createAnnotationParameters(ITestResult testResult,
			Parameters parametersAnnotation) {
		List<ParameterResource> params = Lists.newArrayList();
		String[] keys = parametersAnnotation.value();
		Object[] parameters = testResult.getParameters();
		if (parameters.length != keys.length) {
			return params;
		}
		for (int i = 0; i < keys.length; i++) {
			ParameterResource parameter = new ParameterResource();
			parameter.setKey(keys[i]);
			parameter.setValue(parameters[i] != null ? parameters[i].toString() : null);
			params.add(parameter);
		}
		return params;
	}

	/**
	 * Processes testResult to create parameters provided by
	 * {@link org.testng.annotations.DataProvider} If parameter key isn't provided
	 * by {@link ParameterKey} annotation then it will be 'arg[index]'
	 *
	 * @param testResult
	 *            TestNG's testResult context
	 * @return Step Parameters being sent to ReportPortal
	 */

	private List<ParameterResource> createDataProviderParameters(ITestResult testResult) {
		List<ParameterResource> result = Lists.newArrayList();
		Annotation[][] parameterAnnotations = testResult.getMethod().getConstructorOrMethod().getMethod()
				.getParameterAnnotations();
		Object[] values = testResult.getParameters();
		int length = parameterAnnotations.length;
		if (length != values.length) {
			return result;
		}
		for (int i = 0; i < length; i++) {
			ParameterResource parameter = new ParameterResource();
			String key = ARGUMENT + i;
			String value = values[i] != null ? values[i].toString() : null;
			if (parameterAnnotations[i].length > 0) {
				for (int j = 0; j < parameterAnnotations[i].length; j++) {
					Annotation annotation = parameterAnnotations[i][j];
					if (annotation.annotationType().equals(ParameterKey.class)) {
						key = ((ParameterKey) annotation).value();
					}
				}
			}
			parameter.setKey(key);
			parameter.setValue(value);
			result.add(parameter);
		}
		return result;
	}

	/**
	 * Extension point to customize test step description
	 *
	 * @param testResult
	 *            TestNG's testResult context
	 * @return Test/Step Description being sent to ReportPortal
	 */
	protected String createStepDescription(ITestResult testResult) {
		StringBuilder stringBuffer = new StringBuilder();
		if (testResult.getMethod().getDescription() != null) {
			stringBuffer.append(testResult.getMethod().getDescription());
		}
		return stringBuffer.toString();
	}

	/**
	 * Extension point to customize test suite status being sent to ReportPortal
	 *
	 * @param suite
	 *            TestNG's suite
	 * @return Status PASSED/FAILED/etc
	 */
	protected String getSuiteStatus(ISuite suite) {
		Collection<ISuiteResult> suiteResults = suite.getResults().values();
		String suiteStatus = Statuses.PASSED;
		for (ISuiteResult suiteResult : suiteResults) {
			if (!(isTestPassed(suiteResult.getTestContext()))) {
				suiteStatus = Statuses.FAILED;
				break;
			}
		}
		// if at least one suite failed launch should be failed
		isLaunchFailed.compareAndSet(false, suiteStatus.equals(Statuses.FAILED));
		return suiteStatus;
	}

	/**
	 * Check is current method passed according the number of failed tests and
	 * configurations
	 *
	 * @param testContext
	 *            TestNG's test content
	 * @return TRUE if passed, FALSE otherwise
	 */
	protected boolean isTestPassed(ITestContext testContext) {
		return testContext.getFailedTests().size() == 0 && testContext.getFailedConfigurations().size() == 0
				&& testContext.getSkippedConfigurations().size() == 0 && testContext.getSkippedTests().size() == 0;
	}

	@SuppressWarnings("unchecked")
	protected <T> T getAttribute(IAttributes attributes, String attribute) {
		return (T) attributes.getAttribute(attribute);
	}

	/**
	 * Returns test item ID from annotation if it provided.
	 *
	 * @param testResult
	 *            Where to find
	 * @return test item ID or null
	 */
	private String extractUniqueID(ITestResult testResult) {
		try {
			return (String) ((TestNGScenario) testResult.getMethod()).getMetaData().get("rpUniqueID");
		} catch (Exception e) {
			return null;
		}

	}

	/**
	 * Returns method annotation by specified annotation class from from TestNG
	 * Method or null if the method does not contain such annotation.
	 *
	 * @param annotation
	 *            Annotation class to find
	 * @param testResult
	 *            Where to find
	 * @return {@link Annotation} or null if doesn't exists
	 */
	private <T extends Annotation> T getMethodAnnotation(Class<T> annotation, ITestResult testResult) {
		ITestNGMethod testNGMethod = testResult.getMethod();
		if (null != testNGMethod) {
			ConstructorOrMethod constructorOrMethod = testNGMethod.getConstructorOrMethod();
			if (null != constructorOrMethod) {
				Method method = constructorOrMethod.getMethod();
				if (null != method) {
					return method.getAnnotation(annotation);
				}
			}
		}
		return null;
	}

	/**
	 * Checks if test suite has any methods to run. It can be useful with writing
	 * test with "groups". So there could be created a test suite that has some
	 * methods but doesn't fit the condition of a group. Such suite should be
	 * ignored for rp.
	 *
	 * @param testContext
	 *            Test context
	 * @return True if item has any tests to run
	 */
	private boolean hasMethodsToRun(ITestContext testContext) {
		return null != testContext && null != testContext.getAllTestMethods()
				&& 0 != testContext.getAllTestMethods().length;
	}

	/**
	 * Calculate parent id for configuration
	 */
	@VisibleForTesting
	Maybe<String> getConfigParent(ITestResult testResult, TestMethodType type) {
		Maybe<String> parentId;
		if (TestMethodType.BEFORE_SUITE.equals(type) || TestMethodType.AFTER_SUITE.equals(type)) {
			parentId = getAttribute(testResult.getTestContext().getSuite(), RP_ID);
		} else {
			parentId = getAttribute(testResult.getTestContext(), RP_ID);
		}
		return parentId;
	}

	private boolean isRetry(ITestResult result) {
		IRetryAnalyzer retryAnalyzer = result.getMethod().getRetryAnalyzer();
		return retryAnalyzer != null && result.getMethod().getInvocationNumbers().size()>result.getMethod().getCurrentInvocationCount();
	}

	@VisibleForTesting
	protected static class MemoizingSupplier<T> implements Supplier<T>, Serializable {
		final Supplier<T> delegate;
		transient volatile boolean initialized;
		transient T value;
		private static final long serialVersionUID = 0L;

		MemoizingSupplier(Supplier<T> delegate) {
			this.delegate = delegate;
		}

		public T get() {
			if (!this.initialized) {
				synchronized (this) {
					if (!this.initialized) {
						T t = this.delegate.get();
						this.value = t;
						this.initialized = true;
						return t;
					}
				}
			}

			return this.value;
		}

		public synchronized void reset() {
			this.initialized = false;
		}

		public String toString() {
			return "Suppliers.memoize(" + this.delegate + ")";
		}
	}

}

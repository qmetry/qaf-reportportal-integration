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

import org.testng.IExecutionListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.internal.IResultListener2;

import com.epam.reportportal.listeners.Statuses;

/**
 * @author chirag
 *
 */
public class BaseQAFListener implements IExecutionListener, ISuiteListener, IResultListener2 {
	private IQAFService qafService;

	public BaseQAFListener(IQAFService qafService) {
		this.qafService = qafService;
	}

	@Override
	public void beforeConfiguration(ITestResult testResult) {
		if (!testResult.getMethod().getQualifiedName().startsWith("com.qmetry")) {
			qafService.startConfiguration(testResult);
		}
	}

	@Override
	public void onConfigurationFailure(ITestResult testResult) {
		if (!testResult.getMethod().getQualifiedName().startsWith("com.qmetry")) {
			qafService.sendReportPortalMsg(testResult);
			qafService.finishTestMethod(Statuses.FAILED, testResult);
		}
	}

	@Override
	public void onConfigurationSuccess(ITestResult testResult) {
		if (!testResult.getMethod().getQualifiedName().startsWith("com.qmetry")) {
			qafService.finishTestMethod(Statuses.PASSED, testResult);
		}
	}

	@Override
	public void onExecutionStart() {
		qafService.startLaunch();
	}

	@Override
	public void onExecutionFinish() {
		qafService.finishLaunch();
	}

	@Override
	public void onStart(ISuite suite) {
		qafService.startTestSuite(suite);
	}

	@Override
	public void onFinish(ISuite suite) {
		qafService.finishTestSuite(suite);
	}

	@Override
	public void onStart(ITestContext testContext) {
		qafService.startTest(testContext);
	}

	@Override
	public void onFinish(ITestContext testContext) {
		qafService.finishTest(testContext);
	}

	@Override
	public void onTestStart(ITestResult testResult) {
		qafService.startTestMethod(testResult);
	}

	@Override
	public void onTestSuccess(ITestResult testResult) {
		qafService.finishTestMethod(Statuses.PASSED, testResult);
	}

	@Override
	public void onTestFailure(ITestResult testResult) {
		qafService.sendReportPortalMsg(testResult);
		qafService.finishTestMethod(Statuses.FAILED, testResult);
	}

	@Override
	public void onTestSkipped(ITestResult testResult) {
		qafService.finishTestMethod(Statuses.SKIPPED, testResult);
	}

	@Override
	public void onConfigurationSkip(ITestResult testResult) {
		qafService.sendReportPortalMsg(testResult);
		qafService.finishTestMethod(Statuses.SKIPPED, testResult);
	}

	// this action temporary doesn't supported by report portal
	@Override
	public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
		qafService.finishTestMethod(Statuses.FAILED, result);
	}

}

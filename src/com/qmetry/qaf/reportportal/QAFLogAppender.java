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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

import com.epam.reportportal.message.HashMarkSeparatedMessageParser;
import com.epam.reportportal.message.MessageParser;
import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;

import rp.com.google.common.base.Function;

/**
 * @author chirag.jayswal
 *
 */
public class QAFLogAppender extends AppenderSkeleton {
	private static final MessageParser MESSAGE_PARSER = new HashMarkSeparatedMessageParser();
	private static final List<String> PACKAGES_TO_SKIP = Arrays.asList("rp.", "com.epam.reportportal.",
			"com.qmetry.qaf.automation");
	static {
		getBundle();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.log4j.Appender#close()
	 */
	@Override
	public void close() {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.log4j.Appender#requiresLayout()
	 */
	@Override
	public boolean requiresLayout() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.log4j.AppenderSkeleton#append(org.apache.log4j.spi.LoggingEvent)
	 */
	@Override
	protected void append(final LoggingEvent event) {
		if (isFromReporter(event)) {
			ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
				@Override
				public SaveLogRQ apply(String itemId) {
					SaveLogRQ rq = new SaveLogRQ();
					String message = getMessage(event, false);
					rq.setLevel(getLevel(message));
					rq.setLogTime(new Date(event.getTimeStamp()));
					rq.setTestItemId(itemId);
					rq.setMessage(message);
					return rq;
				}
			});
			return;
		}
		if (skipLoging(event)) {
			return;
		}
		final ReportPortalMessage rpMessage = getReportPortalMessage(event);
		if (null != rpMessage || getBundle().getBoolean("rp.step.astest", false)) {
			ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
				@Override
				public SaveLogRQ apply(String itemId) {

					SaveLogRQ rq = new SaveLogRQ();
					rq.setLevel(event.getLevel().toString());
					rq.setLogTime(new Date(event.getTimeStamp()));
					rq.setTestItemId(itemId);

					String logMessage = null;
					try {
						// There is some binary data reported
						if (null != rpMessage) {
							logMessage = rpMessage.getMessage();

							SaveLogRQ.File file = new SaveLogRQ.File();
							file.setContentType(rpMessage.getData().getMediaType());
							file.setContent(rpMessage.getData().read());
							file.setName(UUID.randomUUID().toString());
							rq.setFile(file);

						} else {
							// Plain string message is reported
							logMessage = getMessage(event);
						}

					} catch (IOException e) {
						// do nothing
					}
					rq.setMessage(logMessage);
					return rq;

				}
			});

		} else {
			getLogger().append(getMessage(event));
		}
	}

	public static StringBuffer getLogger() {
		StringBuffer logger = (StringBuffer) getBundle().getObject("rp_tc_logger");
		if (null == logger) {
			logger = new StringBuffer();
			getBundle().setProperty("rp_tc_logger", logger);
		}
		return logger;
	}

	private String getMessage(LoggingEvent event) {
		return getMessage(event, true);
	}

	private String getMessage(LoggingEvent event, boolean formatted) {

		if (QAFLogAppender.this.layout == null || !formatted) {
			return event.getRenderedMessage();
		} else {
			/*
			 * If additional parameter used in logger, for example:
			 * org.apache.log4j.Logger.debug("message", new Throwable()); Then add
			 * stack-trace to logged message string
			 */
			StringBuilder throwable = new StringBuilder();
			if (null != event.getThrowableInformation()) {
				for (String oneLine : event.getThrowableStrRep())
					throwable.append(oneLine);
			}
			return QAFLogAppender.this.layout.format(event).concat(throwable.toString());
		}
	}

	private ReportPortalMessage getReportPortalMessage(LoggingEvent event) {
		ReportPortalMessage message = null;
		try {
			// ReportPortalMessage is reported
			if (event.getMessage() instanceof ReportPortalMessage) {
				message = (ReportPortalMessage) event.getMessage();

				// File is reported
			} else if (event.getMessage() instanceof File) {

				message = new ReportPortalMessage((File) event.getMessage(), "Binary data reported");

				// Parsable String is reported
			} else if (event.getMessage() instanceof String && MESSAGE_PARSER.supports((String) event.getMessage())) {
				message = MESSAGE_PARSER.parse((String) event.getMessage());
			}
		} catch (IOException e) {
			// do nothing
		}
		return message;
	}

	public static SaveLogRQ.File getFile() {
		StringBuffer logger = QAFLogAppender.getLogger();
		if (null != logger && logger.length() > 0) {
			SaveLogRQ.File file = new SaveLogRQ.File();
			file.setContent(logger.toString().getBytes(Charset.forName("UTF-8")));
			file.setContentType("text/plain");
			file.setName(UUID.randomUUID().toString());
			logger.delete(0, logger.length());
			return file;
		}
		return null;
	}

	private boolean skipLoging(LoggingEvent event) {
		String loggerName = event.getLoggerName();
		if (null == event.getMessage() || null == loggerName) {
			return true;
		}
		if(event.getLocationInformation().getClassName().endsWith("RequestLogger"))
			return false;
		for (String packagePrefix : PACKAGES_TO_SKIP) {
			if (loggerName.startsWith(packagePrefix)) {
				return true;
			}
		}
		return false;
	}
	
	private boolean isFromReporter(LoggingEvent event) {
		return (event.getLocationInformation().getClassName().endsWith("QAFTestBase")
				&& event.getLocationInformation().getMethodName().equalsIgnoreCase("addAssertionLog")) ;
	}

	private String getLevel(String message) {
		if (message.trim().toUpperCase().startsWith("FAIL")) {
			return "ERROR";
		}
		if (message.trim().toUpperCase().startsWith("WARN")) {
			return "WARN";
		}
		return "INFO";
	}
}

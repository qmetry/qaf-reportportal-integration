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

import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.openqa.selenium.remote.DriverCommand;

import com.epam.reportportal.listeners.Statuses;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import com.qmetry.qaf.automation.core.QAFListenerAdapter;
import com.qmetry.qaf.automation.step.StepExecutionTracker;
import com.qmetry.qaf.automation.ui.webdriver.CommandTracker;
import com.qmetry.qaf.automation.ui.webdriver.QAFExtendedWebDriver;
import com.qmetry.qaf.automation.ui.webdriver.QAFExtendedWebElement;

public class QAFListener extends QAFListenerAdapter {
	@Override
	public void beforeCommand(QAFExtendedWebDriver driver, CommandTracker commandTracker) {
		ReportPortalQAFListener.getQAFService().logCommand(commandTracker);
	}
	@Override
	public void beforeCommand(QAFExtendedWebElement element, CommandTracker commandTracker) {
		ReportPortalQAFListener.getQAFService().logCommand(commandTracker);
	}

	@Override
	public void afterCommand(QAFExtendedWebElement element, CommandTracker commandTracker) {
		ReportPortalQAFListener.getQAFService().logCommand(commandTracker);
	}

	@Override
	public void afterCommand(QAFExtendedWebDriver driver, CommandTracker commandTracker) {
		if (!commandTracker.getCommand().equalsIgnoreCase(DriverCommand.SCREENSHOT)) {
			ReportPortalQAFListener.getQAFService().logCommand(commandTracker);
		} else {
			String base64Str = (String) commandTracker.getResponce().getValue();
			File file = new File();
			file.setContent(Base64.decodeBase64(base64Str.getBytes()));
			file.setContentType("image/png");
			file.setName(UUID.randomUUID().toString());
			ReportPortalQAFListener.getQAFService().sendLog("[screenshot]:{}", "INFO", commandTracker.getStartTime(), file);
		}
	}

	@Override
	public void onInitialize(QAFExtendedWebDriver driver) {
		String message = "Driver initialized with capabilities: " + driver.getCapabilities().toString();
		ReportPortalQAFListener.getQAFService().sendLog(message, "TRACE");
	}

	@Override
	public void onFailure(StepExecutionTracker stepExecutionTracker) {
		ReportPortalQAFListener.getQAFService().afterStepExecute(stepExecutionTracker, Statuses.FAILED);
	}

	@Override
	public void beforExecute(StepExecutionTracker stepExecutionTracker) {
		ReportPortalQAFListener.getQAFService().beforeStep(stepExecutionTracker);

	}

	@Override
	public void afterExecute(StepExecutionTracker stepExecutionTracker) {
		ReportPortalQAFListener.getQAFService().afterStepExecute(stepExecutionTracker, Statuses.PASSED);
	}
}

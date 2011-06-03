/*
Copyright 2011 Trampus Richmond. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

   1. Redistributions of source code must retain the above copyright notice, this list of
      conditions and the following disclaimer.

   2. Redistributions in binary form must reproduce the above copyright notice, this list
      of conditions and the following disclaimer in the documentation and/or other materials
      provided with the distribution.

THIS SOFTWARE IS PROVIDED BY TRAMPUS RICHMOND ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL 
TRAMPUS RICHMOND OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those of the authors and 
should not be interpreted as representing official policies, either expressed or implied, of Trampus Richmond.
 
 */

package soda;

import java.io.File;
import java.util.Date;
import org.openqa.selenium.WebDriver;

public class SodaTest {

	private SodaSupportedBrowser BrowserType = null;
	private WebDriver Driver = null;
	private SodaBrowser Browser = null;
	private String testFile = "";
	private SodaEventDriver eventDriver = null;
	private SodaEvents events = null;
	private SodaReporter reporter = null;
	private SodaHash GVars = null;
	private SodaHash OldVars = null;
	private SodaHash HiJacks = null;
	private SodaBlockList blocked = null;
	private static final int ThreadTimeout = 60 * 5; // 5 minute timeout //
	
	public SodaTest(String testFile, SodaBrowser browser, SodaHash gvars, SodaHash hijacks, 
			SodaBlockList blocklist, SodaHash oldvars, String suitename, String reportDir) {
		boolean master_result = false;
		this.Browser = browser;
		this.testFile = testFile;
		this.HiJacks = hijacks;
		this.GVars = gvars;
		this.blocked = blocklist;
		this.OldVars = oldvars;
		String resultsdir = reportDir;
		String report_name = "";
		File tmp_file = new File(testFile);
		
		report_name = tmp_file.getName();
		report_name = report_name.replaceAll(".xml$", "");
		master_result = loadTestFile();
		
		if (suitename != null) {
			resultsdir = resultsdir + "/" + suitename;
		}
		
		this.reporter = new SodaReporter(report_name, resultsdir);
		this.Browser.setReporter(this.reporter);
	}
	
	public SodaEventDriver getSodaEventDriver() {
		return this.eventDriver;
	}
	
	public SodaReporter getReporter() {
		return this.reporter;
	}
	
	private boolean loadTestFile() {
		boolean result = false;
		SodaXML xml = null;
		
		try {
			System.out.printf("Loading Soda Test: '%s'.\n", testFile);
			xml = new SodaXML(testFile);
			this.events = xml.getEvents();
			System.out.printf("Finished.\n");
		} catch (Exception exp) {
			exp.printStackTrace();
			result = false;
		}
		
		return result;
	}
	
	public boolean runTest(boolean isSuitetest) {
		boolean result = false;
		boolean watchdog = false;
		
		result = CheckTestBlocked();
		if (!result) {
			long current = 0;
			eventDriver = new SodaEventDriver(this.Browser, events, this.reporter, this.GVars, this.HiJacks, 
					this.OldVars);
			
			while(eventDriver.isAlive() && watchdog != true) {
				Date current_time = new Date();
				Date thread_time = eventDriver.getThreadTime();
				current = current_time.getTime();
				long thread = thread_time.getTime();

				current = (long)current / 1000;
				thread = (long)thread / 1000;
				long seconds = (current - thread);
			
				if (seconds > ThreadTimeout) {
					watchdog = true;
					eventDriver.stop();
					String msg = String.format("Test watchdogged out after: '%d' seconds!\n", seconds);
					this.reporter.ReportError(msg);
					
					try {
						eventDriver.getThread().join();				
					} catch (Exception exp) {
						this.reporter.ReportException(exp);
					}
					break;
				}
				
				try {
					Thread.sleep(4000);
				} catch (Exception exp) {
					exp.printStackTrace();
					System.exit(-1);
				}
			}
		}
		
		if (isSuitetest != true) {
			if (!this.Browser.getBrowserCloseState()) {
				this.Browser.close();
			}
		}
		
		this.reporter.closeLog();
		return result;
	}
	
	private boolean CheckTestBlocked() {
		boolean result = false;
		File fd = null;
		String test_file = this.testFile;
		
		fd = new File(test_file);
		test_file = fd.getName();
		test_file = test_file.substring(0, test_file.length() -4);

		for (int i = 0; i <= this.blocked.size() -1; i++) {
			String blocked_file = this.blocked.get(i).get("testfile").toString();
			if (test_file.equals(blocked_file)) {
				result = true;
				String module_name = this.blocked.get(i).get("modulename").toString();
				String bug_number = this.blocked.get(i).get("bugnumber").toString();
				String reason = this.blocked.get(i).get("reason").toString();
				String msg = String.format("Test is currently blocked, Bug Number: '%s', Module Name: '%s'"+
						", Reason: '%s'", bug_number, module_name, reason);
				this.reporter.Log(msg);
				break;
			}
		}
		
		return result;
	}
	
}

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

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.firefox.FirefoxDriver;

import voodoodriver.SodaBlockList;
import voodoodriver.SodaBlockListParser;
import voodoodriver.SodaBrowser;
import voodoodriver.SodaCSV;
import voodoodriver.SodaCSVData;
import voodoodriver.SodaChrome;
import voodoodriver.SodaCmdLineOpts;
import voodoodriver.SodaEvents;
import voodoodriver.SodaFirefox;
import voodoodriver.SodaHash;
import voodoodriver.SodaIE;
import voodoodriver.SodaOSInfo;
import voodoodriver.SodaReporter;
import voodoodriver.SodaSuiteParser;
import voodoodriver.SodaSupportedBrowser;
import voodoodriver.SodaTest;
import voodoodriver.SodaTestList;
import voodoodriver.SodaTestResults;

public class VooDooDriver {

	public static String VERSION = "0.0.1";
	
	public static void printUsage() {
		String msg = "SodaSuite\n"+
		"Usage: SodaSuite --browser=\"firefox\" --test=\"sodatest1.xml\""+
		" --test=\"sodatest2.xml\" ...\n\n"+
		"Required Flags:\n"+
		"   --browser: This is any of the following supported web browser name.\n"+
		"      [ firefox, safari, ie ]\n\n"+
		"   --test: This is a soda test file.  This argument can be used more then"+
		"once when there are more then one soda tests to run.\n\n"+
		"   --savehtml: This flag will cause html pages to be saved when there is an"+
		" error testing the page.\n\n"+
		"   --hijack: This is a key/value pair that is used to hi jack any csv file\n"+
		"      values of the same name.  The key and value are split using \"::\"\n"+  
		"      Example: --hijack=\"username::sugaruser\"\n\n"+
		"   --resultdir: This allows you to override the default results directory.\n\n"+
		"   --gvar: This is a global var key/value pair to be injected into Soda.\n"+
		"      The key and value are split using \"::\"\n"+
		"      Example: --gvar=\"slayerurl::http://www.slayer.net\"\n\n"+
		"   --suite: This is a Soda suite xml test file.\n\n"+
		"   --skipcsserrors: This tells soda to not report on css errors.\n\n"+
		"   --testdelay: This forces a 10 second delay in between tests that run in a"+
		" suite.\n\n"+
		"   --blocklistfile: This is the XML file containing tests to block from running.\n\n"+
		"	--profile: This is the browser profile name use start the browser with.\n\n"+
		"	--plugin: This is a plugin XML file.\n\n"+
		"   --version: Print the Soda Version string.\n\n";
		
		System.out.printf("%s\n", msg);
	}
	
	public static void main(String[] args) {
		String sodaConfigFile = "soda-config.xml";
		File sodaConfigFD = null;
		String blockListFile = null;
		SodaBlockList blockList = null;
		SodaCmdLineOpts opts = null;
		SodaHash cmdOpts = null;
		SodaSupportedBrowser browserType = null;
		ArrayList<String> SodaSuitesList = null;
		ArrayList<String> SodaTestsList = null;
		String pluginFile = null;
		SodaPluginParser plugParser = null;
		SodaEvents plugins = null;
		
		System.out.printf("Starting SodaSuite...\n");
		try {
			opts = new SodaCmdLineOpts(args);
			cmdOpts = opts.getOptions();
			
			sodaConfigFD = new File(sodaConfigFile);
			if (sodaConfigFD.exists()) {
				System.out.printf("(*)Found SodaSuite config file: %s\n", sodaConfigFile);
				SodaConfigParser scp = new SodaConfigParser(sodaConfigFD);
			}
			
			if ((Boolean)cmdOpts.get("help")) {
				printUsage();
				System.exit(0);
			}
			
			if ((Boolean)cmdOpts.get("version")) {
				System.out.printf("(*)SodaSuite Version: %s\n", VooDooDriver.VERSION);
				System.exit(0);
			}
			
			if (cmdOpts.get("browser") == null) {
				System.out.printf("(!)Error: Missing --browser commandline option!\n\n");
				System.exit(-1);
			}
			
			pluginFile = (String)cmdOpts.get("plugin");
			if (pluginFile != null) {
				System.out.printf("(*)Loading Plugins from file: '%s'.\n", pluginFile);
				plugParser = new SodaPluginParser(pluginFile);
				plugins = plugParser.parse();
			}
			
			try {
				browserType = SodaSupportedBrowser.valueOf(cmdOpts.get("browser").toString().toUpperCase());
			} catch (Exception expBrowser) {
				System.out.printf("(!)Unsupported browser: '%s'!\n", cmdOpts.get("browser").toString());
				System.out.printf("(!)Exiting!\n\n");
				System.exit(2);
			}
			
			blockListFile = (String)cmdOpts.get("blocklistfile");
			if (blockListFile != null) {
				SodaBlockListParser sbp = new SodaBlockListParser(blockListFile);
				blockList = sbp.parse();
			} else {
				System.out.printf("(*)No Block list file to parse.\n");
				blockList = new SodaBlockList();
			}
			
			String resultdir = (String)cmdOpts.get("resultdir");
			if (resultdir == null) {
				DateFormat df = null;
				String cwd = System.getProperty("user.dir");
				df = new SimpleDateFormat("MM-d-yyyy-hh-m-s.S");
				String date_str = df.format(new Date());
				cwd = cwd.concat("/");
				cwd = cwd.concat(date_str);
				resultdir = cwd;
			}
			
			SodaSuitesList = (ArrayList<String>)cmdOpts.get("suites");
			if ((SodaSuitesList != null) && (!SodaSuitesList.isEmpty())) {
				if (resultdir == null) {
					System.out.printf("(!)Error: Missing command line flag --resultdir!\n");
					System.out.printf("--)--resultdir is needed when running SODA suites.\n\n");
					System.exit(3);
				}
				
				RunSuites(SodaSuitesList, resultdir, browserType, (SodaHash)cmdOpts.get("gvars"), 
						(SodaHash)cmdOpts.get("hijacks"), blockList, plugins);
			}
			
			SodaTestsList = (ArrayList<String>)cmdOpts.get("tests");
			if (!SodaTestsList.isEmpty()) {
				RunTests(SodaTestsList, resultdir, browserType, (SodaHash)cmdOpts.get("gvars"), (SodaHash)cmdOpts.get("hijacks"),
						plugins);
			}
		} catch (Exception exp) {
			exp.printStackTrace();
		}
		
		System.out.printf("(*)SodaSuite Finished.\n");
		System.exit(0);
	}
	
	private static void RunTests(ArrayList<String> tests, String resultdir, SodaSupportedBrowser browserType,
			SodaHash gvars, SodaHash hijacks, SodaEvents plugins) {
		File resultFD = null;
		SodaBrowser browser = null;
		int len = 0;
		SodaTest testobj = null;
		
		System.out.printf("(*)Running Soda Tests now...\n");
		
		resultFD = new File(resultdir);
		if (!resultFD.exists()) {
			System.out.printf("(*)Result directory doesn't exists, trying to create dir: '%s'\n", resultdir);
			
			try {
				resultFD.mkdirs();
			} catch (Exception exp) {
				System.out.printf("(!)Error: Failed to create reportdir: '%s'!\n", resultdir);
				System.out.printf("(!)Exception: %s\n", exp.getMessage());
				System.exit(3);
			}
		}
		
		switch (browserType) {
		case FIREFOX:
			browser = new SodaFirefox();
			break;
		case CHROME:
			browser = new SodaChrome();
			break;
		case IE:
			browser = new SodaIE();
			break;
		}
		
		browser.newBrowser();
		
		len = tests.size() -1;
		for (int i = 0; i <= len; i++) {
			System.out.printf("Starting Test: '%s'.\n", tests.get(i));
			
			testobj = new SodaTest(tests.get(i), browser, gvars, hijacks, null, null, null, resultdir);
			testobj.setPlugins(plugins);
			testobj.runTest(false);
			
		}
		
		
	}
	
	private static void writeSummary(FileOutputStream in, String msg) {
		try {
			in.write(msg.getBytes());
		} catch (Exception exp) {
			exp.printStackTrace();
		}
	}
	
	private static void RunSuites(ArrayList<String> suites, String resultdir, SodaSupportedBrowser browserType,
			SodaHash gvars, SodaHash hijacks, SodaBlockList blockList, SodaEvents plugins) {
		int len = suites.size() -1;
		File resultFD = null;
		String report_file_name = resultdir;
		String hostname = "";
		FileOutputStream suiteRptFD = null;
		SodaBrowser browser = null;
		DateFormat df = null;
		Date now = null;
		
		System.out.printf("(*)Running Suite files now...\n");
		
		resultFD = new File(resultdir);
		if (!resultFD.exists()) {
			System.out.printf("(*)Result directory doesn't exists, trying to create dir: '%s'\n", resultdir);
			
			try {
				resultFD.mkdirs();
			} catch (Exception exp) {
				System.out.printf("(!)Error: Failed to create reportdir: '%s'!\n", resultdir);
				System.out.printf("(!)Exception: %s\n", exp.getMessage());
				System.exit(3);
			}
		}
		
		try {
			InetAddress addr = InetAddress.getLocalHost();
			hostname = addr.getHostName();
			addr.getHostAddress();
			
			if (hostname.isEmpty()) {
				hostname = addr.getHostAddress();
			}
		} catch (Exception exp) {
			System.out.printf("(!)Error: %s!\n", exp.getMessage());
			System.exit(4);
		}
		
		df = new SimpleDateFormat("MM-d-yyyy-hh-m-s.S");
		String date_str = df.format(new Date());
		report_file_name += "/"+ hostname + "-" + date_str + ".xml";
		
		try {
			suiteRptFD = new FileOutputStream(report_file_name);
			System.out.printf("(*)Report: %s\n", report_file_name);
		} catch (Exception exp) {
			System.out.printf("(!)Error: %s!\n", exp.getMessage());
			System.exit(5);
		}
		
		switch (browserType) {
		case FIREFOX:
			browser = new SodaFirefox();
			break;
		case CHROME:
			browser = new SodaChrome();
			break;
		case IE:
			browser = new SodaIE();
			break;
		}
		
		browser.newBrowser();
		
		writeSummary(suiteRptFD, "<data>\n");
		
		for (int i = 0; i <= len; i++) {
			String suite_base_noext = "";
			String suite_name = suites.get(i);
			String suite_base_name = "";
			File suite_fd = new File(suite_name);
			suite_base_name = suite_fd.getName();
		
			writeSummary(suiteRptFD, "\t<suite>\n\n");
			writeSummary(suiteRptFD, String.format("\t\t<suitefile>%s</suitefile>\n", suite_base_name));
			
			Pattern p = Pattern.compile("\\.xml$", Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(suite_base_name);
			suite_base_noext = m.replaceAll("");

			suite_fd = null;
			SodaTest testobj = null;
			System.out.printf("(*)Executing Suite: %s\n", suite_base_name);
			System.out.printf("(*)Parsing Suite file...\n");
			SodaSuiteParser suiteP = new SodaSuiteParser(suite_name);
			SodaTestList suite_test_list = suiteP.getTests();
			SodaHash vars = null;
			SodaTestResults test_results_hash = null;
			String test_res_str = "";
			
			for (int test_index = 0; test_index <= suite_test_list.size() -1; test_index++) {
				writeSummary(suiteRptFD, "\t\t<test>\n");
				boolean test_result = false;
				String current_test = suite_test_list.get(test_index);
				writeSummary(suiteRptFD, String.format("\t\t\t<testfile>%s</testfile>\n", current_test));
				System.out.printf("(*)Executing Test: '%s'\n", current_test);
				now = new Date();
				date_str = df.format(now);
				writeSummary(suiteRptFD, String.format("\t\t\t<starttime>%s</starttime>\n", date_str));
				
				if (browser.isClosed()) {
					System.out.printf("(*)Browser was closed by another suite, creating new browser...\n");
					browser.newBrowser();
					System.out.printf("(*)New browser created.\n");
				}
				
				testobj = new SodaTest(current_test, browser, gvars, hijacks, blockList, vars, 
						suite_base_noext, resultdir);
				
				if (plugins != null) {
					testobj.setPlugins(plugins);
				}
				
				testobj.runTest(false);
				
				now = new Date();
				date_str = df.format(now);
				writeSummary(suiteRptFD, String.format("\t\t\t<stoptime>%s</stoptime>\n", date_str));
				
				
				if (testobj.getSodaEventDriver() != null) {
					vars = testobj.getSodaEventDriver().getSodaVars();
				}
				
				test_results_hash = testobj.getReporter().getResults();
				for (int res_index = 0; res_index <= test_results_hash.keySet().size() -1; res_index++) {
					String key = test_results_hash.keySet().toArray()[res_index].toString();
					String value = test_results_hash.get(key).toString();
					
					if (key.contains("result")) {
						if (Integer.valueOf(value) != 0) {
							value = "Failed";	
						} else {
							value = "Passed";	
						}
					}
					writeSummary(suiteRptFD, String.format("\t\t\t<%s>%s</%s>\n", key, value, key));
				}
				writeSummary(suiteRptFD, "\t\t</test>\n\n");
				
				Integer watchdog = Integer.valueOf(test_results_hash.get("watchdog"));
				if (watchdog > 0) {
					System.out.printf("Exiting from finishing the other tests due to watch dog!\n");
					break;
				}
			}
			writeSummary(suiteRptFD, "\t</suite>\n");
		}
		writeSummary(suiteRptFD, "</data>\n\n");
	}
}

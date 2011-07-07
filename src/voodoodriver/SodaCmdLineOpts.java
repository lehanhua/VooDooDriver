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

package voodoodriver;

import java.util.ArrayList;

/**
 * A class for parsing all the of needed command line options for voodoodriver.
 * 
 * @author trampus
 *
 */
public class SodaCmdLineOpts {

	private SodaHash options = null;
	private SodaHash gvars = null;
	private ArrayList<String> tests = null;
	private ArrayList<String> suites = null;
	private String flavor = null;
	private Boolean saveHtml = false;
	private SodaHash hijacks = null;
	private String resultDir = null;
	private String blocklistFile = null;
	private Boolean version = false;
	private String browser = null;
	private Boolean testdelay = false;
	private Boolean skipcssErrors = false;
	private Boolean help = false;
	private String profile = null;
	private String plugin = null;
	private String configfile = null;
	
	public SodaCmdLineOpts(String[] args) {
		
		try {
			this.gvars = new SodaHash();
			this.hijacks = new SodaHash();
			this.tests = new ArrayList<String>();
			this.suites = new ArrayList<String>();
			
			for (int i = 0; i <= args.length -1; i++) {
				if (args[i].contains("--hijack")) {
					this.handleHijackValue(args[i]);
				} else if(args[i].contains("--gvar")) {
					this.handleGvarsValue(args[i]);
				} else if (args[i].contains("--suite")) {
					handleSuites(args[i]);
				} else if (args[i].contains("--test")) {
					handleTests(args[i]);
				} else if (args[i].contains("--browser")) {
					args[i] = args[i].replaceAll("--browser=", "");
					this.browser = args[i];
					System.out.printf("(*)Browser: %s\n", this.browser);
				} else if (args[i].contains("--flavor")) {
					args[i] = args[i].replaceAll("--flavor=", "");
					this.flavor = args[i];
					System.out.printf("(*)Flavor: %s\n", this.flavor);
				} else if (args[i].contains("--savehtml")) {
					this.saveHtml = true;
					System.out.printf("(*)SaveHTML: %s\n", this.saveHtml);
				} else if (args[i].contains("--version")) {
					this.version = true;
					System.out.printf("(*)Version: %s\n", this.version);
				} else if (args[i].contains("--resultdir")) {
					args[i] = args[i].replaceAll("--resultdir=", "");
					this.resultDir = args[i];
					System.out.printf("(*)Result Dir: %s\n", this.resultDir);
				} else if (args[i].contains("--blocklistfile")) {
					args[i] = args[i].replaceAll("--blocklistfile=", "");
					this.blocklistFile = args[i];
					System.out.printf("(*)Blocklistfile: %s\n", this.blocklistFile);
				} else if (args[i].contains("--testdelay")) {
					this.testdelay = true;
					System.out.printf("(*)Testdelay: %s\n", this.testdelay);
				} else if (args[i].contains("--skipcsserrors")) {
					this.skipcssErrors = true;
					System.out.printf("(*)Skip CSS Errors: %s\n", this.skipcssErrors);
				} else if (args[i].contains("--help")) {
					this.help = true;
				} else if (args[i].contains("--help")) {
					this.profile = args[i];
					System.out.printf("(*)Browser Profile: %s\n", this.profile);
				} else if (args[i].contains("--plugin")) {
					this.plugin = args[i];
					this.plugin = this.plugin.replace("--plugin=", "");
					System.out.printf("(*)Soda Plugin File: %s\n", this.plugin);
				} else if (args[i].contains("--config")) {
					this.configfile = args[i];
					this.configfile = this.configfile.replace("--config=", "");
					System.out.printf("(*)Soda Config File: %s\n", this.configfile);
				}
			}
			
			this.options = new SodaHash();
			this.options.put("skipcsserrors", this.skipcssErrors);
			this.options.put("testdelay", this.testdelay);
			this.options.put("blocklistfile", this.blocklistFile);
			this.options.put("resultdir", this.resultDir);
			this.options.put("version", this.version);
			this.options.put("savehtml", this.saveHtml);
			this.options.put("flavor", this.flavor);
			this.options.put("browser", this.browser);
			this.options.put("tests", this.tests);
			this.options.put("suites", this.suites);
			this.options.put("gvars", this.gvars);
			this.options.put("hijacks", this.hijacks);
			this.options.put("help", this.help);
			this.options.put("profile", this.profile);
			this.options.put("plugin", this.plugin);
			this.options.put("config", this.configfile);
		} catch (Exception exp) {
			exp.printStackTrace();
			this.options = null;
		}
	}
	
	/**
	 * Parses the --gvar command line option.
	 * 
	 * @param str The gvar command line.
	 */
	private void handleGvarsValue(String str) {
		str = str.replace("--gvar=", "");
		String[] data = str.split("::");
		this.gvars.put("global."+data[0], data[1]);
		System.out.printf("(*)GVar: %s => %s\n", "global."+data[0], data[1]);
	}
	
	/**
	 * Parses the --hijack command line option.
	 * 
	 * @param str The hijack command line.
	 */
	private void handleHijackValue(String str) {
		str = str.replace("--hijack=", "");
		String[] data = str.split("::");
		this.hijacks.put(data[0], data[1]);
		System.out.printf("(*)HiJack: %s => %s\n", data[0], data[1]);
	}

	/**
	 * Parses the --test command line option.
	 * 
	 * @param str The test command line.
	 */
	private void handleTests(String str) {
		str = str.replace("--test=", "");
		this.tests.add(str);
		System.out.printf("(*)Test Added: %s\n", str);
	}
	
	/**
	 * Parses the --suite command line option.
	 * 
	 * @param str The suite command line.
	 */
	private void handleSuites(String str) {
		str = str.replace("--suite=", "");
		this.suites.add(str);
		System.out.printf("(*)Suite Added: %s\n", str);
	}
	
	/**
	 * Returns the parsed options.
	 * 
	 * @return {@link SodaHash}
	 */
	public SodaHash getOptions() {
		return this.options;
	}
	
}

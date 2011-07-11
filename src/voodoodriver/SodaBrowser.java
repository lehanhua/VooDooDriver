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

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * An base class for adding new web browser support for VooDooDriver.
 * 
 * @author trampus
 *
 */
public abstract class SodaBrowser implements SodaBrowserInterface {
	
	private WebDriver Driver = null;
	private boolean closed = true;
	private String profile = null;
	private SodaReporter reporter = null;
	private String assertPageFile = null;
	private SodaPageAsserter asserter = null;
	
	/**
	 * Constructor
	 * 
	 */
	public SodaBrowser() {
		
	}
	
	/**
	 * Tells you if you the current browser object closed.
	 * 
	 * @return boolean
	 */
	public boolean isClosed() {
		return this.closed;
	}
	
	/**
	 * Set the internal {@link SodaReporter} object.
	 * 
	 * @param rep An existing {@link SodaReporter} object.
	 */
	public void setReporter(SodaReporter rep) {
		this.reporter = rep;
	}
	
	/**
	 * Sets the name of the browser profile to use.
	 * 
	 * @param profile The name of the browser profile to use.
	 */
	public void setProfile(String profile) {
		this.profile = profile;
	}
	
	/**
	 * Gets the current set browser profile name.
	 * 
	 * @return {@linkplain String} The current browser profile name.
	 */
	public String getProfile() {
		return this.profile;
	}
	
	/**
	 * tells the class that the browser window has been closed.
	 */
	public void setBrowserClosed() {
		this.closed = true;
	}
	
	/**
	 * Tells you if the browser windows was closed for this object.
	 * 
	 * @return {@linkplain boolean}
	 */
	public boolean getBrowserCloseState() {
		return this.closed;
	}
	
	/**
	 * Sets a new WebDriver for the browser to use.
	 * 
	 * @param driver
	 */
	public void setDriver(WebDriver driver) {
		this.Driver = driver;
	}
	
	/**
	 * Returns the current {@link WebDriver} instance.
	 * 
	 * @return {@link WebDriver}
	 */
	public WebDriver getDriver() {
		return this.Driver;
	}
	
	/**
	 *  creates a newBrowser
	 */
	public void newBrowser() {
		this.closed = false;
	}
	
	/**
	 * Sets the state if the browser is open or closed.
	 * 
	 * @param state Sets the closed state for the browser.
	 */
	public void setBrowserState(boolean state) {
		this.closed = state;
	}

	/**
	 * Tells the browser to bypass java Alert & confirm dialogs.
	 * 
	 * @param alert	sets how you want to handle a confirm dialog.
	 */
    public void alertHack(boolean alert) {
    	
    }
	
    /**
     * Executed javascript in the browser, but also creates an auto var called "CONTROL", which 
     * can be used by the script being executed.
     * 
     * @param script	The javascript to run in the browser.
     * @param element	The Element to use on the page as the CONTROL var.
     * @return {@link Object}
     */
	public Object executeJS(String script, WebElement element) {
		Object result = null;
		JavascriptExecutor js = (JavascriptExecutor)this.Driver;
		
		if (element != null) {
			result = js.executeScript(script, element);
		} else {
			result = js.executeScript(script);
		}
		
		if (result == null) {
			result = null;
		}
		
		return result;
	}
	
	/**
	 * Fires a javascript event in the browser for a given html element.
	 * 
	 * @param element
	 * @param eventType
	 * @return {@link String}
	 */
	public String fire_event(WebElement element, String eventType) {
		String result = "";
		String eventjs_src = "";
		JavascriptEventTypes type = null;
		eventType = eventType.toLowerCase();
		String tmp_type = eventType.replaceAll("on", "");
		
		try {
			UIEvents.valueOf(tmp_type.toUpperCase());
			type = JavascriptEventTypes.UIEvent;
		} catch (Exception exp) {
			type = null;
		}
		
		if (type == null) {
			try {
				HTMLEvents.valueOf(tmp_type.toUpperCase());
				type = JavascriptEventTypes.HTMLEvent;
			} catch (Exception exp) {
				type = null;
			}
		}
		
		if (type == null) {
			return null;
		}
		
		switch (type) {
		case HTMLEvent:
			
			break;
		case UIEvent:
			eventjs_src = this.generateUIEvent(UIEvents.valueOf(tmp_type.toUpperCase()));
			break;
		}
		
		result = this.executeJS(eventjs_src, element).toString();
		
		return result;
	}
	

	/**
	 * Generates a browser event to be fired on a given control.
	 *
	 * @param type
	 * 
	 * @return {@link String} results
	 */
	public String generateUIEvent(UIEvents type) {
		String result = "var ele = arguments[0];\n"; 
		result += "var evObj = document.createEvent('MouseEvents');\n";
		result += "evObj.initMouseEvent( '" + type.toString().toLowerCase() + "', true, true, window, 1, 12, 345, 7, 220,"+ 
         "false, false, true, false, 0, null );\n";
		result += "ele.dispatchEvent(evObj);\n";
		result += "return 0;\n";
		
		return result;
	}
	
	/**
	 * Calls refresh in the browser.
	 */
	public void refresh() {
		this.Driver.navigate().refresh();
	}
	
	/**
	 * Calls forward in the browser.
	 */
	public void forward() {
		this.Driver.navigate().forward();
	}
	
	/**
	 * Calls back in the browser.
	 */
	public void back() {
		this.Driver.navigate().back();
	}
	
	/**
	 * Calls close on the browser.
	 */
	public void close() {
		this.Driver.close();
		this.setBrowserClosed();
	}
	
	/**
	 * Gets the page sources in the current browser window.
	 * 
	 * @return {@link String} result
	 */
	public String getPageSource() {
		String result = "";
		int max = 20;
		boolean failed = false;
		
		for (int i = 0; i <= max; i++) {
			try {
				result = this.Driver.getPageSource();
			} catch (Exception exp) {
				failed = true;
			}
			
			if (failed) {
				try {
					Thread.currentThread().sleep(1000);
				} catch (Exception exp) {
					exp.printStackTrace();
				}
			} else {
				break;
			}
		}
		
		return result;
	}
	
	/**
	 * Asserts if the given value exists in the browser text.
	 * 
	 * @param value The value to check if exists in the browser.
	 * 
	 * @return {@link boolean} result
	 */
	public boolean Assert(String value) {
		boolean result = false;
		result = this.reporter.Assert(value, this.getPageSource());
		return result;
	}
	
	/**
	 * Asserts if the given value does not exist in the browser text.
	 * 
	 * @param value The value to check if not exists in the browser.
	 * 
	 * @return boolean
	 */
	public boolean AssertNot(String value) {
		boolean result = false;
		result = this.reporter.AssertNot(value, this.getPageSource());
		return result;
	}
	
	/**
	 * 
	 */
	public boolean assertPage() {
		boolean result = false;
		
		if (this.asserter == null && this.assertPageFile != null) {
			try {
				System.out.printf("FOOBAR!\n\n");
				this.asserter = new SodaPageAsserter(this.assertPageFile, this.reporter);
			} catch (Exception exp) {
				this.reporter.ReportException(exp);
			}
		}
		
		if (this.asserter != null) {
			this.asserter.assertPage(this.getPageSource());	
		}
		
		return result;
	}
	
	/**
	 * Find an element in the browser's current dom.
	 * 
	 * @param by
	 * @param retryTime
	 * 
	 * @return {@link WebElement}
	 */
	public WebElement findElement(By by, int retryTime) {
		WebElement result = null;
		long end = System.currentTimeMillis() + retryTime * 1000;
		
		while (System.currentTimeMillis() < end) {
			try {
				result = this.Driver.findElement(by);
			} catch (Exception exp) {
				result = null;
			}
			
			if (result != null) {
				break;
			}
		}
		
		return result;
	}
	
	/**
	 * Finds more then one element in the browser's current DOM.
	 * 
	 * @param by
	 * @param retryTime
	 * @param index
	 * @param required
	 * @return {@link WebElement}
	 */
	public WebElement findElements(By by, int retryTime, int index, boolean required) {
		WebElement result = null;
		List<WebElement> elements = null;
		int len = 0;
		String msg = "";
		
		long end = System.currentTimeMillis() + retryTime * 1000;
		
		while (System.currentTimeMillis() < end) {
			try {
				elements = this.Driver.findElements(by);
				len = elements.size() -1;
				if (len >= index) {
					result = elements.get(index);
				}
			} catch (Exception exp) {
				result = null;
				this.reporter.ReportException(exp);
			}
			
			if (result != null) {
				break;
			}
		}

		if (len < index && result == null && required != false) {
			msg = String.format("Failed to find element by index '%d', index is out of bounds!", index);
			this.reporter.ReportError(msg);
			result = null;
		}
		
		return result;
	}
	
	/**
	 * Tells the browser to go to this URL.
	 * 
	 * @param url
	 */
	public void url(String url) {
		try {
			this.Driver.navigate().to(url);
		} catch (Exception exp) {
			exp.printStackTrace();
		}
	}
	
	/**
	 * Sets the classes internal var for where the assertpage config xml file is.
	 * 
	 * @param filename
	 */
	public void setAssertPageFile(String filename, SodaReporter reporter) {
		this.assertPageFile = filename;
		this.asserter = new SodaPageAsserter(filename, reporter);
	}
	
	/**
	 * Gets the current assertpage file if any.
	 * 
	 * @return null for no file, or a path to the file.
	 */
	public String getAssertPageFile() {
		return this.assertPageFile;
	}
	
}

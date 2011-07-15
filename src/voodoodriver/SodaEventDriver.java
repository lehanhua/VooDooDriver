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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;

public class SodaEventDriver implements Runnable {

	private SodaEvents testEvents = null;
	private SodaBrowser Browser = null;
	private SodaHash sodaVars = null;
	private SodaReporter report = null;
	private SodaHash hijacks = null;
	private Date threadTime = null;
	private volatile Thread runner;
	private volatile Boolean threadStop = false;
	private SodaEvents plugIns = null;
	private SodaHash ElementStore = null;
	
	public SodaEventDriver(SodaBrowser browser, SodaEvents events, SodaReporter reporter, SodaHash gvars,
			SodaHash hijacks, SodaHash oldvars, SodaEvents plugins) {
		testEvents = events;
		this.Browser = browser;
		this.report = reporter;
		this.hijacks = hijacks;
		
		if (oldvars != null) {
			sodaVars = oldvars;
		} else {
			sodaVars = new SodaHash();
		}
		
		this.ElementStore = new SodaHash();
		
		this.plugIns = plugins;
		
		if (gvars != null) {
			int len = gvars.keySet().size();
			
			for (int i = 0; i <= len -1; i++) {
				String key = gvars.keySet().toArray()[i].toString();
				String value = gvars.get(key).toString();
				System.out.printf("--)'%s' => '%s'\n", key, value);
				this.sodaVars.put(key, value);
			}
		}
		
		this.stampEvent();
		this.threadTime = new Date();
		this.runner = new Thread(this, "SodaEventDriver-Thread");
		runner.start();
	}
	
	private void assertPage(SodaHash event) {
		boolean assertpage = true;
		
		if (event.containsKey("assertPage")) {
			assertpage = this.clickToBool(event.get("assertPage").toString());
		}
		
		if (assertpage) {
			this.Browser.assertPage();
		}
	}
	
	private void saveElement(SodaHash event, WebElement element) {
		if (!event.containsKey("save")) {
			return;
		}
		
		String name = event.get("save").toString();
		if (this.ElementStore.containsKey(name)) {
			String msg = String.format("Found existing saved element var: '%s', Overwriting now.", name);
			this.report.Warn(msg);
		}
		
		if (element == null) {
			String msg = String.format("Element trying to be saved: '%s => 'NULL', not storing NULL element!", name);
			this.report.ReportError(msg);
		} else {
			this.ElementStore.put(name, element);
			String msg = String.format("Stored HTML element to be referenced by: '%s'.", name);
			this.report.Log(msg);
		}
	}
	
	public SodaHash getSodaVars() {
		return this.sodaVars;
	}
	
	public void appedSodaVars(SodaHash vars) {
		int len = 0;
		
		if (vars == null) {
			return;
		}
		
		len = vars.keySet().size() -1;
		for (int i = 0; i <= len; i++) {
			String name = vars.keySet().toArray()[i].toString();
			String value = vars.get(name).toString();
			this.sodaVars.put(name, value);
		}
		
	}
	
	public boolean isAlive() {
		return this.runner.isAlive();
	}
	
	public Thread getThread() {
		return this.runner;
	}
	
	public void stop() {
		synchronized(this.threadStop) {
			this.threadStop = true;
			this.runner.interrupt();
		}
	}
	
	public boolean isStopped() {
		boolean result = false;
		
		synchronized(this.threadStop) {
			result = this.threadStop;
		}
		
		return result;
	}
	
	public void run() {
		System.out.printf("Thread Running...\n");
		this.threadTime = new Date();
		int i = 0;
		int event_count = this.testEvents.size() -1;
		
		while ( (!this.threadStop) && (i <= event_count)) {
			handleSingleEvent(this.testEvents.get(i), null);
			i += 1;
		}
	}
	
	private void resetThreadTime() {
		synchronized (this.threadTime) {
			this.threadTime = new Date();
		}
	}
	
	public Date getThreadTime() {
		Date tmp = null;
		
		synchronized (this.threadTime) {
			tmp = this.threadTime;
		}
		
		return tmp;
	}
	
	private void processEvents(SodaEvents events, WebElement parent) {
		int event_count = events.size() -1;
		
		for (int i = 0; i <= event_count; i++) {
			if (isStopped()) {
				break;
			}
			
			handleSingleEvent(events.get(i), parent);
		}
	}
	
	public SodaEvents getElements() {
		return testEvents;
	}
	
	private boolean handleSingleEvent(SodaHash event, WebElement parent) {
		boolean result = false;
		WebElement element = null;
		
		if (isStopped()) {
			return result;
		}
		
		this.resetThreadTime();
		
		switch ((SodaElements)event.get("type")) {
		case BROWSER: 
			result = browserEvent(event);
			break;
		case PUTS:
			result = putsEvent(event);
			break;
		case WAIT:
			result = waitEvent(event);
			break;
		case TEXTFIELD:
			element = textfieldEvent(event, parent);
			break;
		case BUTTON:
			element = buttonEvent(event, parent);
			break;
		case CSV:
			result = csvEvent(event);
			break;
		case LINK:
			element = linkEvent(event, parent);
			break;
		case CHECKBOX:
			element = checkboxEvent(event, parent);
			break;
		case VAR:
			result = varEvent(event);
			break;
		case SCRIPT:
			result = scriptEvent(event);
			break;
		case DIV:
			element = divEvent(event, parent);
			break;
		case ATTACH:
			result = attachEvent(event);
			break;
		case TABLE:
			element = tableEvent(event, parent);
			break;
		case FORM:
			element = formEvent(event, parent);
			break;
		case SELECT:
			element = selectEvent(event, parent);
			break;
		case STAMP:
			result = stampEvent();
			break;
		case TIMESTAMP:
			result = stampEvent();
			break;			
		case SPAN:
			element = spanEvent(event, parent);
			break;
		case HIDDEN:
			result = hiddenEvent(event, parent);
			break;
		case TR:
			element = trEvent(event, parent);
			break;
		case TD:
			element = tdEvent(event, parent);
			break;
		case FILEFIELD:
			element = filefieldEvent(event, parent);
			break;
		case IMAGE:
			element = imageEvent(event, parent);
			break;
		case DND:
			result = dndEvent(event);
			break;
		case TEXTAREA:
			element = textareaEvent(event, parent);
			break;
		case LI:
			element = liEvent(event, parent);
			break;
		case RADIO:
			element = radioEvent(event, parent);
			break;
		case EXECUTE:
			result = executeEvent(event);
			break;
		case JAVASCRIPT:
			result = javascriptEvent(event);
			break;
		case UL:
			result = ulEvent(event);
			break;
		case OL:
			result = olEvent(event);
			break;
		case MAP:
			result = mapEvent(event);
			break;
		case AREA:
			result = areaEvent(event);
			break;
		default:
			System.out.printf("(*)Unknown command: '%s'!\n", event.get("type").toString());
			System.exit(1);
		}
			
		this.resetThreadTime();
		
		if (element != null) {
			this.saveElement(event, element);
		}
		
		this.assertPage(event);
		
		return result;
	}
	
	private boolean ulEvent(SodaHash event) {
		boolean required = true;
		boolean click = false;
		boolean result = false;
		WebElement element = null;
		
		this.report.Log("UL event Started.");
		this.resetThreadTime();
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, null, required);
			if (element == null) {
				this.report.Log("UL event finished.");
				result = false;
				return result;
			}
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.report.Log("UL click started.");
				this.firePlugin(element, SodaElements.UL, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.UL, SodaPluginEventType.AFTERCLICK);
				this.report.Log("UL click finished.");
			}
			
			if (event.containsKey("children")) {
				this.processEvents((SodaEvents)event.get("children"), element);
			}
		} catch (Exception exp) {
			element = null;
			this.report.ReportException(exp);
		}
		
		this.report.Log("UL event Finished.");
		
		return result;	
	}

	private boolean areaEvent(SodaHash event) {
		boolean required = true;
		boolean click = false;
		boolean result = false;
		WebElement element = null;
		
		this.report.Log("Area event Started.");
		this.resetThreadTime();
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, null, required);
			if (element == null) {
				this.report.Log("Area event finished.");
				result = false;
				return result;
			}
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.report.Log("Area click started.");
				this.firePlugin(element, SodaElements.AREA, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.AREA, SodaPluginEventType.AFTERCLICK);
				this.report.Log("Area click finished.");
			}
		} catch (Exception exp) {
			element = null;
			this.report.ReportException(exp);
		}
		
		this.report.Log("Area event Finished.");		
		return result;	
	}

	private boolean mapEvent(SodaHash event) {
		boolean required = true;
		boolean click = false;
		boolean result = false;
		WebElement element = null;
		
		this.report.Log("Map event Started.");
		this.resetThreadTime();
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, null, required);
			if (element == null) {
				this.report.Log("Map event finished.");
				result = false;
				return result;
			}
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.report.Log("Map click started.");
				this.firePlugin(element, SodaElements.MAP, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.MAP, SodaPluginEventType.AFTERCLICK);
				this.report.Log("Map click finished.");
			}
			
			if (event.containsKey("children")) {
				this.processEvents((SodaEvents)event.get("children"), element);
			}
		} catch (Exception exp) {
			element = null;
			this.report.ReportException(exp);
		}
		
		this.report.Log("Map event Finished.");
		
		return result;	
	}
	
	private boolean olEvent(SodaHash event) {
		boolean required = true;
		boolean click = false;
		boolean result = false;
		WebElement element = null;
		
		this.report.Log("OL event Started.");
		this.resetThreadTime();
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, null, required);
			if (element == null) {
				this.report.Log("OL event finished.");
				result = false;
				return result;
			}
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.report.Log("OL click started.");
				this.firePlugin(element, SodaElements.OL, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.OL, SodaPluginEventType.AFTERCLICK);
				this.report.Log("OL click finished.");
			}
		} catch (Exception exp) {
			element = null;
			this.report.ReportException(exp);
		}
		
		this.report.Log("OL event Finished.");
		
		return result;	
	}
	
	private boolean javascriptEvent(SodaHash event) {
		boolean result = false;
		String scriptdata = "";
		
		this.report.Log("Javascript event starting.");
		
		if (event.containsKey("content")) {
			this.report.Warn("Using javascript contents is deprecated, please use the file attribute!");
			scriptdata = event.get("content").toString();
		}
		
		if (event.containsKey("file")) {
			scriptdata = "";
			String filename = event.get("file").toString();
			filename = this.replaceString(filename);
			
			File fd = new File(filename);
			try {
				BufferedReader reader = new BufferedReader(new FileReader(fd));
				String tmp;
				
				while ( (tmp = reader.readLine()) != null) {
					scriptdata = scriptdata.concat(tmp);
				}
				result = true;
			} catch (Exception exp) {
				this.report.ReportException(exp);
				result = false;
			}
		}
		
		this.Browser.executeJS(scriptdata, null);
		
		this.report.Log("Javascript event finished.");	
		return result;
	}
	
	private boolean executeEvent(SodaHash event) {
		boolean result = false;
		Process proc = null;
		int proc_ret = 0;
		
		this.report.Log("Execute event starting...\n");
		this.resetThreadTime();
		
		if (event.containsKey("args")) {
			String[] list = (String[])event.get("args");
			int len = list.length -1;
			
			for (int i = 0; i <= len; i++) {
				System.out.printf("(%s) => '%s'\n", i, list[i]);
			}
			
			try {
				this.report.Log("Executing process now...");
				proc = Runtime.getRuntime().exec(list);
				this.resetThreadTime();
				proc.waitFor();
				this.resetThreadTime();
				this.report.Log("Process finished executing.");
				proc_ret = proc.exitValue();
				if (proc_ret != 0) {
					String msg = String.format("Error the command being executed returned a non-zero value: '%s'!",
							proc_ret);
					this.report.ReportError(msg);
				} else {
					this.report.Log("Execute was successful.");
					result = true;
				}
			} catch (Exception exp) {
				this.report.ReportException(exp);
				result = false;
			}
		} else {
			this.report.ReportError("Error no args for Execute Event!");
			result = false;
			this.report.Log("Execute event finished.");
			return result;
		}
		
		return result;
	}
	
	private boolean dndEvent(SodaHash event) {
		boolean result = true;
		String src = null;
		String dst = null;
		
		if (event.containsKey("src")) {
			src = event.get("src").toString();
		}
		
		if (event.containsKey("dst")) {
			dst = event.get("dst").toString();
		}
		
		if (src == null) {
			this.report.ReportError("DVD command is missing 'src' attribute!");
			result = false;
		}
		
		if (dst == null) {
			this.report.ReportError("DVD command is missing 'dst' attribute!");
			result = false;
		}
		
		if (result) {
			Actions builder = null;
			Action dnd = null;
			WebElement Esrc = (WebElement)this.ElementStore.get(src);
			WebElement Edst = (WebElement)this.ElementStore.get(dst);
			
			builder = new Actions(this.Browser.getDriver());
			builder.dragAndDrop(Esrc, Edst);
			dnd = builder.build();
			dnd.perform();
			//dnd = builder.clickAndHold(Esrc).moveToElement(Edst).release(Edst).build();
			//dnd.perform();
			
			//action = new Actions(this.Browser.getDriver());
			//System.out.printf("Starting DND!\n");
			//action.dragAndDrop(Esrc, Edst);
			//action.clickAndHold(Esrc);
			//action.moveToElement(Edst);
			//action.release(Edst);
			System.out.printf("Finished DND!\n");
		}
		
		return result;
	}
	
	private WebElement imageEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		boolean click = false;
		WebElement element = null;
		
		this.report.Log("Image event Started.");
		this.resetThreadTime();
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("Image event finished.");
				return element;
			}
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.report.Log("Image click started.");
				this.firePlugin(element, SodaElements.IMAGE, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.IMAGE, SodaPluginEventType.AFTERCLICK);
				this.report.Log("Image click finished.");
			}
		} catch (Exception exp) {
			element = null;
			this.report.ReportException(exp);
		}
		
		this.report.Log("Image event Finished.");	
		return element;
	}
	
	private WebElement filefieldEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		WebElement element = null;
		
		this.report.Log("FileField event Started.");
		this.resetThreadTime();
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}

		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("FileField event finished.");
				return element;
			}
			
			this.firePlugin(element, SodaElements.FILEFIELD, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("set")) {
				String setvalue = event.get("set").toString();
				setvalue = this.replaceString(setvalue);
				setvalue = FilenameUtils.separatorsToSystem(setvalue);
				this.report.Log(String.format("Setting filefield to: '%s'.", setvalue));
				element.sendKeys(setvalue);
				this.report.Log("Finished set.");
			}
		} catch (Exception exp) {
			element = null;
			this.report.ReportException(exp);
		}		
		
		this.report.Log("FileField event finished..");
		this.resetThreadTime();	
		return element;
	}
	
	private WebElement liEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		boolean click = false;
		WebElement element = null;
		
		this.report.Log("LI event Started.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("LI event finished.");
				return element;
			}
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.report.Log("Click element.");
				this.firePlugin(element, SodaElements.LI, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.LI, SodaPluginEventType.AFTERCLICK);
				this.report.Log("Click finished.");
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
		}
		
		if (event.containsKey("children")) {
			this.processEvents((SodaEvents)event.get("children"), element);
		}
		
		this.report.Log("LI event finished.");
		return element;
	}
	
	private WebElement trEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		boolean click = false;
		WebElement element = null;
		
		this.report.Log("TR event Started.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("TR event finished.");
				return element;
			}
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.report.Log("Click element.");
				this.firePlugin(element, SodaElements.TR, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.TR, SodaPluginEventType.AFTERCLICK);
				this.report.Log("Click finished.");
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
		}
		
		if (event.containsKey("children")) {
			this.processEvents((SodaEvents)event.get("children"), element);
		}
		
		this.report.Log("TR event finished.");
		return element;
	}
	
	private WebElement tdEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		boolean click = false;
		WebElement element = null;
		
		this.report.Log("TD event Started.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("TD event finished.");
				return element;
			}
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.report.Log("Click element.");
				this.firePlugin(element, SodaElements.TD, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.TD, SodaPluginEventType.AFTERCLICK);
				this.report.Log("Click finished.");
			}
		} catch (Exception exp) {
			element = null;
			this.report.ReportException(exp);
		}
		
		if (event.containsKey("children")) {
			this.processEvents((SodaEvents)event.get("children"), element);
		}
		
		this.report.Log("TD event finished.");
		return element;
	}
	
	private boolean hiddenEvent(SodaHash event, WebElement parent) {
		boolean result = false;
		boolean required = true;
		WebElement element = null;
		
		this.report.Log("Hidden event Started.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				result = false;
				this.report.Log("Hidden event finished.");
				return result;
			}
			
			if (event.containsKey("var")) {
				String name = event.get("var").toString();
				//String value = element.getValue();
				String value = element.getAttribute("value");
				SodaHash tmp = new SodaHash();
				tmp.put("set", value);
				tmp.put("var", name);
				this.varEvent(tmp);
			}
			
			result = true;
		} catch (Exception exp) {
			this.report.ReportException(exp);
			result = false;
		}
		
		this.report.Log("Hidden event finished.");
		return result;
	}
	
	private boolean stampEvent() {
		boolean result = false;
		Date now = new Date();
		DateFormat df = new SimpleDateFormat("yyMMdd_hhmmss");
		String date_str = df.format(now);
		
		this.report.Log(String.format("Setting STAMP => '%s'.", date_str));
		this.sodaVars.put("stamp", date_str);		
		return result;
	}
	
	private WebElement spanEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		boolean click = false;
		WebElement element = null;

		this.report.Log("span event starting.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("Span event finished.");
				return element;
			}
			
			this.firePlugin(element, SodaElements.SPAN, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (event.containsKey("vartext")) {
				String name = event.get("vartext").toString();
				String value = element.getText();
				SodaHash tmp = new SodaHash();
				tmp.put("set", value);
				tmp.put("var", name);
				this.varEvent(tmp);
			}
			
			if (click) {
				this.firePlugin(element, SodaElements.SPAN, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.SPAN, SodaPluginEventType.AFTERCLICK);
			}
			
			if (event.containsKey("assert")) {
				String src = element.getText();
				String val = this.replaceString(event.get("assert").toString());
				this.report.Assert(val, src);
			}
			
			if (event.containsKey("assertnot")) {
				String src = element.getText();
				String val = this.replaceString(event.get("assertnot").toString());
				this.report.AssertNot(val, src);
			}
			
			if (event.containsKey("jscriptevent")) {
				this.report.Log("Firing Javascript Event: "+ event.get("jscriptevent").toString());
				this.Browser.fire_event(element, event.get("jscriptevent").toString());
				Thread.sleep(1000);
				this.report.Log("Javascript event finished.");
			}
		
			if (event.containsKey("children") && element != null) {
				this.processEvents((SodaEvents)event.get("children"), element);
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
			element = null;
		}
		
		this.report.Log("Span event finished.");
		return element;
	}

	private WebElement radioEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		boolean click = false;
		WebElement element = null;

		this.report.Log("Radio event starting.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("Radio event finished.");
				return element;
			}
			
			this.firePlugin(element, SodaElements.RADIO, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			// this is a hack to make older soda tests work //
			if (event.containsKey("set")) {
				this.report.Warn("Using the 'set' command for a radio element is not supported anymore!  Use click!");
				click = this.clickToBool(event.get("set").toString());
			}
			
			if (event.containsKey("vartext")) {
				String name = event.get("vartext").toString();
				String value = element.getText();
				SodaHash tmp = new SodaHash();
				tmp.put("set", value);
				tmp.put("var", name);
				this.varEvent(tmp);
			}
			
			if (click) {
				this.firePlugin(element, SodaElements.RADIO, SodaPluginEventType.BEFORECLICK);
				this.report.Log("Clicking Element.");
				element.click();
				this.report.Log("Click finished.");
				this.firePlugin(element, SodaElements.RADIO, SodaPluginEventType.AFTERCLICK);
			}
			
			if (event.containsKey("assert")) {
				String src = element.getText();
				String val = this.replaceString(event.get("assert").toString());
				this.report.Assert(val, src);
			}
			
			if (event.containsKey("assertnot")) {
				String src = element.getText();
				String val = this.replaceString(event.get("assertnot").toString());
				this.report.AssertNot(val, src);
			}
			
			if (event.containsKey("jscriptevent")) {
				this.report.Log("Firing Javascript Event: "+ event.get("jscriptevent").toString());
				this.Browser.fire_event(element, event.get("jscriptevent").toString());
				Thread.sleep(1000);
				this.report.Log("Javascript event finished.");
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
			element = null;
		}
		
		this.report.Log("Radio event finished.");
		return element;		
	}

	private WebElement selectEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		WebElement element = null;
		String setvalue = null;
		String msg = "";
		boolean was_set = false;
		
		this.report.Log("Select event Started.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			List<WebElement> list = null;
			element = this.findElement(event, parent, required);
			if (element != null) {
				list = element.findElements(By.tagName("option"));
				int len = list.size() -1;
				
				if (event.containsKey("set")) {
					setvalue = event.get("set").toString();
					setvalue = this.replaceString(setvalue);
				}
				
				if (setvalue != null) {
					for (int i = 0; i <= len; i++) {
						String tmp = list.get(i).getText();
						if (setvalue.equals(tmp)) {
							msg = String.format("Setting Select value to: '%s'.", setvalue);
							this.report.Log(msg);
							list.get(i).click();
							this.firePlugin(element, SodaElements.SELECT, SodaPluginEventType.AFTERSET);
							was_set = true;
							break;
						}
					}
					
					if (!was_set) {
						this.report.ReportError(String.format("Failed to find option in select with text matching: '%s'!", setvalue));
					}
				}
				
				if (event.containsKey("assert")) {
					String assvalue = event.get("assert").toString();
					assvalue = this.replaceString(assvalue);
					
					List<WebElement> opts = element.findElements(By.tagName("option"));
					int opts_len = opts.size() -1;
					String select_val = "";
					
					for (int opts_index = 0; opts_index <= opts_len; opts_index++) {
						if (opts.get(opts_index).isSelected()) {
							select_val = opts.get(opts_index).getText();
							break;
						}
					}
					
					this.report.Assert(assvalue, select_val);
				}
				
				if (event.containsKey("assertnot")) {
					String assvalue = event.get("assertnot").toString();
					assvalue = this.replaceString(assvalue);
					
					List<WebElement> opts = element.findElements(By.tagName("option"));
					int opts_len = opts.size() -1;
					String select_val = "";
					
					for (int opts_index = 0; opts_index <= opts_len; opts_index++) {
						if (opts.get(opts_index).isSelected()) {
							select_val = opts.get(opts_index).getText();
							break;
						}
					}
					
					this.report.AssertNot(assvalue, select_val);
				}
				
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
		}
		
		this.report.Log("Select event finished.");
		return element;
	}
	
	private WebElement formEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		boolean click = false;
		WebElement element = null;
		
		this.report.Log("Form event starting.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("Form event finished.");
				return element;
			}
			
			this.firePlugin(element, SodaElements.FORM, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.firePlugin(element, SodaElements.FORM, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.FORM, SodaPluginEventType.AFTERCLICK);
			}
			
			if (event.containsKey("children") && element != null) {
				this.processEvents((SodaEvents)event.get("children"), element);
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
		}
		
		this.report.Log("Form event finished.");
		return element;
	}
	
	private WebElement tableEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		boolean click = false;
		WebElement element = null;
		
		this.report.Log("Table event started.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("Table event finished.");
				return element;
			}
			
			this.firePlugin(element, SodaElements.TABLE, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.firePlugin(element, SodaElements.TABLE, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.TABLE, SodaPluginEventType.AFTERCLICK);
			}
			
			if (event.containsKey("assert")) {
				String src = element.getText();
				String value = event.get("assert").toString();
				value = this.replaceString(value);
				this.report.Assert(value, src);
			}
			
			if (event.containsKey("assertnot")) {
				String src = element.getText();
				String value = event.get("assertnot").toString();
				value = this.replaceString(value);
				this.report.AssertNot(value, src);
			}
			
			if (event.containsKey("jscriptevent")) {
				this.report.Log("Firing Javascript Event: "+ event.get("jscriptevent").toString());
				this.Browser.fire_event(element, event.get("jscriptevent").toString());
				Thread.sleep(1000);
				this.report.Log("Javascript event finished.");
			}
			
			if (event.containsKey("children")) {
				this.processEvents((SodaEvents)event.get("children"), element);
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
			element = null;
		}
		
		this.report.Log("Table event finished.");	
		return element;
	}
	
	private boolean attachEvent(SodaHash event) {
		boolean result = false;
		Set<String> handles = null;
		int len = 0;
		boolean use_URL = false;
		boolean is_REGEX = false;
		String finder = "";
		String found_handle = null;
		int timeout = 10;
		
		try {
			this.report.Log("Starting attach event.");
			String currentWindow = this.Browser.getDriver().getWindowHandle();
			this.report.Log(String.format("Current Window Handle: '%s'.", currentWindow));
			
			if (event.containsKey("url")) {
				use_URL = true;
				finder = event.get("url").toString();
			} else {
				use_URL = false;
				finder = event.get("title").toString();
			}

			finder = this.replaceString(finder);

			if (this.report.isRegex(finder)) {
				is_REGEX = true;
			}
			
			for (int timer = 0; timer <= timeout; timer++) {
				handles = this.Browser.getDriver().getWindowHandles();
				len = handles.size() -1;
				for (int i = 0; i <= len; i++) {
					String tmp_handle = handles.toArray()[i].toString();
					String tmp_url = this.Browser.getDriver().switchTo().window(tmp_handle).getCurrentUrl();
					String tmp_title = this.Browser.getDriver().switchTo().window(tmp_handle).getTitle();
					this.report.Log(String.format("[%d]: Window Handle: '%s'", i, tmp_handle));
					this.report.Log(String.format("[%d]: Window Title: '%s'", i, tmp_title));
					this.report.Log(String.format("[%d]: Window URL: '%s'", i, tmp_url));
					
					if (!is_REGEX) {
						if (!use_URL) {
							if (tmp_title.equals(finder)) {
								found_handle = tmp_handle;
								this.report.Log(String.format("Found Window Title '%s'",finder));
								break;
							}
						} else {
							if (tmp_url.equals(finder)) {
								found_handle = tmp_handle;
								this.report.Log(String.format("Found Window URL '%s'",finder));
								break;
							}
						}
					} else {
						if (!use_URL) {
							Pattern p = Pattern.compile(finder);
							Matcher m = p.matcher(tmp_title);
							if (m.find()) {
								found_handle = tmp_handle;
								this.report.Log(String.format("Found Window Title '%s'", finder));
								break;
							}
						} else {
							Pattern p = Pattern.compile(finder);
							Matcher m = p.matcher(tmp_url);
							if (m.find()) {
								found_handle = tmp_handle;
								this.report.Log(String.format("Found Window URL '%s'",finder));
								break;
							}
						}
					}
				} // end for loop //
				
				if (found_handle != null) {
					break;
				}
				Thread.sleep(1000);
			} // end timer loop //
			
			if (found_handle == null) {
				String msg = String.format("Failed to find window matching: '%s!'", finder);
				this.report.ReportError(msg);
				result = false;
				this.Browser.getDriver().switchTo().window(currentWindow);
				Thread.sleep(2000); // helps control when the page is loaded, might remove this later... //
				return result;
			}
			
			this.Browser.getDriver().switchTo().window(found_handle);
			if (event.containsKey("children")) {
				this.processEvents((SodaEvents)event.get("children"), null);
			}
			
			this.Browser.getDriver().switchTo().window(currentWindow);
			
		} catch (Exception exp) {
			exp.printStackTrace();
		}
		
		return result;
	}
	
	private String replaceString(String str) {
		String result = str;
		Pattern patt = null;
		Matcher matcher = null;
		
		this.resetThreadTime();
		
		patt = Pattern.compile("\\{@[\\w\\.]+\\}", Pattern.CASE_INSENSITIVE);
		matcher = patt.matcher(str);
		
		while (matcher.find()) {
			String m = matcher.group();
			String tmp = m;
			tmp = tmp.replace("{@", "");
			tmp = tmp.replace("}", "");
			
			if (this.hijacks.containsKey(tmp)) {
				String value = this.hijacks.get(tmp).toString();
				result = result.replace(m, value);
			} else if (this.sodaVars.containsKey(tmp)) {	
				String value = this.sodaVars.get(tmp).toString();
				result = result.replace(m, value);
			}
		}
		
		result = result.replaceAll("\\\\n", "\n");
		this.resetThreadTime();
		
		return result;
	}
	
	private WebElement divEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		boolean click = false;
		WebElement element = null;
		
		this.report.Log("Div event starting.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("Div event finished.");
				return element;
			}
			
			this.firePlugin(element, SodaElements.DIV, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				this.firePlugin(element, SodaElements.DIV, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.DIV, SodaPluginEventType.AFTERCLICK);
			}
			
			if (event.containsKey("assert")) {
				String src = element.getText();
				String value = event.get("assert").toString();
				value = this.replaceString(value);
				this.report.Assert(value, src);
			}
			
			if (event.containsKey("assertnot")) {
				String src = element.getText();
				String value = event.get("assertnot").toString();
				value = this.replaceString(value);
				this.report.AssertNot(value, src);
			}
			
			if (event.containsKey("jscriptevent")) {
				this.report.Log("Firing Javascript Event: "+ event.get("jscriptevent").toString());
				this.Browser.fire_event(element, event.get("jscriptevent").toString());
				Thread.sleep(1000);
				this.report.Log("Javascript event finished.");
			}
			
			if (event.containsKey("children")) {
				this.processEvents((SodaEvents)event.get("children"), element);
			}
			
		} catch (Exception exp) {
			this.report.ReportException(exp);
			element = null;
		}
		
		this.report.Log("Div event finished.");	
		return element;
	}
	
	private boolean scriptEvent(SodaHash event) {
		boolean result = false;
		SodaXML xml = null;
		String testfile = "";	
		File fd = null;
		SodaEvents newEvents = null;
		
		testfile = event.get("file").toString();
		testfile = this.replaceString(testfile);
		
		try {
			fd = new File(testfile);
			if (!fd.exists()) {
				String msg = String.format("Failed to find file: '%s'!", testfile);
				this.report.ReportError(msg);
				return false;
			}
			fd = null;
			
			xml = new SodaXML(testfile, null);
			newEvents = xml.getEvents();
			this.processEvents(newEvents, null);
			
		} catch (Exception exp) {
			exp.printStackTrace();
			result = false;
		}
	
		return result;
	}
	
	/*
	 * varEvent -- method
	 * 	This method sets a SODA var that can then be used in the follow script.
	 * 
	 * Input:
	 * 	event: A Soda event.
	 * 
	 * Output:
	 * 	returns true on success or false on fail.
	 * 
	 */
	private boolean varEvent(SodaHash event) {
		boolean result = false;
		String var_name = "";
		String var_value = "";
		
		try {
			if (event.containsKey("set")) {
				var_name = event.get("var").toString();
				var_name = this.replaceString(var_name);
				var_value = event.get("set").toString();
				var_value = this.replaceString(var_value);
				this.sodaVars.put(var_name, var_value);
				this.report.Log("Setting SODA variable: '"+ var_name + "' => '" + var_value + "'.");
			}
			
			if (event.containsKey("unset")) {
				var_name = event.get("var").toString();
				var_name = this.replaceString(var_name);
				this.report.Log("Unsetting SODA variable: '" + var_name + "'.");
				if (!this.sodaVars.containsKey(var_name)) {
					this.report.Log("SODA variable: '" + var_name + "' not found, nothing to unset.");
				} else {
					this.sodaVars.remove(var_name);
				}
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
			result = false;
		}
		
		return result;
	}
	
	private WebElement checkboxEvent(SodaHash event, WebElement parent) {
		boolean click = false;
		boolean required = true;
		WebElement element = null;
		
		this.resetThreadTime();
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				return element;
			}
			
			this.firePlugin(element, SodaElements.CHECKBOX, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
				if (click) {
					this.firePlugin(element, SodaElements.CHECKBOX, SodaPluginEventType.BEFORECLICK);
					element.click();
					this.firePlugin(element, SodaElements.CHECKBOX, SodaPluginEventType.AFTERCLICK);
				}
			}
			
			if (event.containsKey("set")) {
				boolean check = this.clickToBool(event.get("set").toString());
				
				if (!check) {
					this.report.Log("Unchecking checkbox.");
					element.clear();
					this.report.Log("Unchecking finished.");
				} else {
					this.report.Log("Checking checkbox.");
					element.click();
					this.report.Log("Checking finished.");
				}
			}
			
		} catch (Exception exp) {
			this.report.ReportException(exp);
		}
		
		this.resetThreadTime();
		return element;
	}
	
	private WebElement linkEvent(SodaHash event, WebElement parent) {
		boolean click = true;
		boolean required = true;
		WebElement element = null;
		
		this.resetThreadTime();
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			this.report.Log("Link Event Started.");
			String how = event.get("how").toString();
			how = this.replaceString(how);
			String value = event.get(how).toString();
			value = this.replaceString(value);
			element = this.findElement(event, parent, required);

			if (element == null) {
				if (required) {
					String msg = String.format("Failed to find link: '%s' => '%s'!", how, value);
					this.report.ReportError(msg);
				}
				
				element = null;
				this.report.Log("Link Event Finished.");
				return element;
			}

			this.firePlugin(element, SodaElements.LINK, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("alert")) {
				boolean alert = this.clickToBool(event.get("alert").toString());
				this.report.Log(String.format("Setting Alert Hack to: '%s'", alert));
				this.Browser.alertHack(alert);
			}
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (click) {
				value = this.replaceString(value);
				this.report.Log(String.format("Clicking Link: '%s' => '%s'", how, value));
				this.firePlugin(element, SodaElements.LINK, SodaPluginEventType.BEFORECLICK);
				element.click();
				this.firePlugin(element, SodaElements.LINK, SodaPluginEventType.AFTERCLICK);
			} else {
				String msg = String.format("Found Link: '%s' but not clicking as click => '%s'.", value, click);
				this.report.Log(msg);
			}
			
			if (event.containsKey("jscriptevent")) {
				this.report.Log("Firing Javascript Event: "+ event.get("jscriptevent").toString());
				this.Browser.fire_event(element, event.get("jscriptevent").toString());
				Thread.sleep(1000);
				this.report.Log("Javascript event finished.");
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
			element = null;
		}
		
		this.resetThreadTime();
		this.report.Log("Link Event Finished.");
		return element;
	}
	
	private boolean csvEvent(SodaHash event) {
		boolean result = false;
		SodaCSV csv = null;
		SodaCSVData csv_data = null;
		String var_name = event.get("var").toString();
		String csv_filename = "";

		this.resetThreadTime();
		
		csv_filename = event.get("file").toString();
		csv_filename = replaceString(csv_filename);
		
		csv = new SodaCSV(csv_filename, this.report);
		csv_data = csv.getData();
		
		for (int i = 0; i <= csv_data.size() -1; i++) {
			int keys_len = csv_data.get(i).keySet().size() -1;
			
			for (int key_index = 0; key_index <= keys_len; key_index++) {
				String key = csv_data.get(i).keySet().toArray()[key_index].toString();
				String sodavar_name = var_name + "." + key;
				String sodavar_value = csv_data.get(i).get(key).toString();
				
				if (this.hijacks.containsKey(sodavar_name)) {
					sodavar_value = this.hijacks.get(sodavar_name).toString();
					this.report.Log("Hijacking SodaVar: '" + sodavar_name+"' => '" +sodavar_value+"'.");
				}
				this.sodaVars.put(sodavar_name, sodavar_value);
			}
			
			if (event.containsKey("children")) {
				this.processEvents((SodaEvents)event.get("children"), null);
			}
		}
		
		this.resetThreadTime();
		return result;
	}
	
	private boolean waitEvent(SodaHash event) {
		boolean result = false;
		int default_timeout = 5;
		
		this.resetThreadTime();	
		this.report.Log("Starting Wait event.");
		
		if (event.containsKey("timeout")) {
			Integer int_out = new Integer(event.get("timeout").toString());
			default_timeout = int_out.intValue();
			this.report.Log(String.format("Setting timeout to: %d seconds.", default_timeout));
		} else {
			this.report.Log(String.format("default timeout: %d seconds.", default_timeout));
		}
		
		default_timeout = default_timeout * 1000;
		
		try {
			this.report.Log(String.format("waiting: '%d' seconds.", (default_timeout / 1000)));
			int wait_seconds = default_timeout / 1000;
			
			for (int i = 0; i <= wait_seconds -1; i++) {
				if (isStopped()) {
					break;
				}
				Thread.sleep(1000);
			}
			
			result = true;
		} catch (InterruptedException exp) {
			result = false;
		}
		
		this.resetThreadTime();
		this.report.Log("Wait event finished.");
		return result;
	}

	private boolean browserEvent(SodaHash event) {
		boolean result = false;
		boolean assertPage = true;
		SodaBrowserActions browser_action = null;
		
		this.resetThreadTime();
		
		this.report.Log("Browser event starting...");
		
		try {
			if (event.containsKey("action")) {
				browser_action = SodaBrowserActions.valueOf(event.get("action").toString().toUpperCase());
				switch (browser_action) {
				case REFRESH:
					this.report.Log("Calling Browser event refresh.");
					this.Browser.refresh();
					break;
				case CLOSE:
					this.report.Log("Calling Browser event close.");
					this.Browser.close();
					break;	
				case BACK:
					this.report.Log("Calling Browser event back.");
					this.Browser.back();
					break;
				case FORWARD:
					this.report.Log("Calling Browser event forward.");
					this.Browser.forward();
					break;
				}
			} else {
				int event_count = event.keySet().size() -1;
				for (int i = 0; i <= event_count; i++) {
					String key = event.keySet().toArray()[i].toString();
					String key_id = "BROWSER_" + key;
					SodaBrowserMethods method = null;
					
					if (SodaBrowserMethods.isMember(key_id)) {
						method = SodaBrowserMethods.valueOf(key_id); 
					} else {
						continue;
					}
					
					String value = "";
					switch (method) {
					case BROWSER_url:
						String url = event.get(key).toString();
						url = this.replaceString(url);
						this.report.Log(String.format("URL: '%s'", url));
						this.Browser.url(url);
						break;
					case BROWSER_assert:
						value = event.get("assert").toString();
						value = this.replaceString(value);
						result = this.Browser.Assert(value);
						
						if (!result) {
							String msg = String.format("Browser Assert Failed to find this in page: '%s'", value);
							this.report.ReportError(msg);
						}
						break;
					case BROWSER_assertnot:
						value = event.get("assertnot").toString();
						value = this.replaceString(value);
						result = this.Browser.AssertNot(value);

						if (!result) {
							String msg = String.format("Browser AssertNot Found text in page: '%s'", value);
							this.report.ReportError(msg);
						}
						break;
					
					case BROWSER_assertPage:
						assertPage = this.clickToBool(event.get("assertPage").toString());
						this.report.Log(String.format("Borwser assertPage => '%s'.", assertPage));
						break;
					default:
						System.out.printf("(!)ERROR: Unknown browser method: '%s'!\n", key_id);
						System.exit(3);
					}
				}
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
		}

		this.resetThreadTime();
		this.report.Log("Browser Event finished.");
		return result;
	}
	
	private WebElement findElement(SodaHash event, WebElement parent, boolean required) {
		WebElement element = null;
		By by = null;
		boolean href = false;
		boolean value = false;
		String how = "";
		String what = "";
		int index = -1;
		int timeout = 5;
		
		if (event.containsKey("timeout")) {
			timeout = Integer.valueOf(event.get("timeout").toString());
			String msg = String.format("Resetting default element finding timeout to: '%d' seconds.", timeout);
			this.report.Log(msg);
		}
		
		if (event.containsKey("index")) {
			index = Integer.valueOf(event.get("index").toString()).intValue();
		}
		
		this.resetThreadTime();
		
		try {
			String msg = "";
			how = event.get("how").toString();
			what = event.get(how).toString(); 
			what = this.replaceString(what);
			String dowhat = event.get("do").toString();
			
			if (index > -1) {
			msg = String.format("Tring to find page element '%s' by: '%s' => '%s' index => '%s'.", dowhat, how, what,
					index);
			} else {
				msg = String.format("Tring to find page element '%s' by: '%s' => '%s'.", dowhat, how, what);	
			}
			this.report.Log(msg);
			
			if (how.matches("class") && what.matches(".*\\s+.*")) {
				String elem_type = event.get("do").toString();
				String old_how = how;
				how = "css";
				String css_sel = String.format("%s[%s=\"%s\"]",elem_type, old_how, what);
				what = css_sel;
			}
			
			if (how.contains("index")) {
				how = "tagname";
				what = event.get("do").toString();
				
				if (what.contains("image")) {
					what = "img";
				}
			}
			
			switch (SodaElementsHow.valueOf(how.toUpperCase())) {
			case ID:
				by = By.id(what);
				break;
			case CLASS:
				by = By.className(what);
				break;
			case CSS:
				by = By.cssSelector(what);
				break;
			case LINK:
				by = By.linkText(what);
				break;
			case HREF:
				by = By.tagName("a");
				href = true;
				break;
			case TEXT:
				by = By.linkText(what);
				break;
			case NAME:
				by = By.name(what);
				break;
			case PARLINK:
				by = By.partialLinkText(what);
				break;
			case TAGNAME:
				by = By.tagName(what);
				break;
			case XPATH:
				by = By.xpath(what);
				break;
			case VALUE:
				value = true;
				break;
			default:
				this.report.ReportError(String.format("Error: findElement, unknown how: '%s'!\n", how));
				System.exit(4);
				break;
			}
			
			if (href) {
				element = this.findElementByHref(event.get("href").toString(), parent);
			} else if (value) {
				element = this.slowFindElement(event.get("do").toString(), what, parent);
			}else {
				if (parent == null) {
					if (index > -1) {
						element = this.Browser.findElements(by, timeout, index, required);
					} else {
						element = this.Browser.findElement(by, timeout);
					}
				} else {
					List<WebElement> elements;
					if (index > 0) {
						elements = parent.findElements(by);
						if (elements.size() -1 < index && required != false) {
							if (required) {
								msg = String.format("Failed to find element by index '%d', index is out of bounds!", index);
								this.report.ReportError(msg);
							}
							element = null;
						} else {
							element = elements.get(index);
						}
					} else {
						element = parent.findElement(by);
					}
				}
			}
		} catch (NoSuchElementException exp) {
			if (required) {
				this.report.ReportException(exp);
				element = null;
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
			element = null;
		}
		
		this.resetThreadTime();
		
		if (element == null) {
			if (required) {
				String msg = String.format("Failed to find element: '%s' => '%s'", how, what);
				this.report.ReportError(msg);
			} else {
				String msg = String.format("Failed to find element, but required => 'false' : '%s' => '%s'", how, what);
				this.report.Log(msg);
			}
		} else {
			this.report.Log("Found element.");
		}
		
		return element;
	}
	
	private WebElement slowFindElement(String ele_type, String how, WebElement parent) {
		WebElement element = null;
		String msg = "";
		String js = "";
		
		msg = String.format("Looking for elements by value is very very slow!  You should never do this!");
		this.report.Log(msg);
		msg = String.format("Looking for element: '%s' => '%s'.", ele_type, how);
		this.report.Log(msg);
		
		if (how.contains("OK")) {
			System.out.print("");
		}
		
		if (ele_type.contains("button")) {
			js = String.format("querySelector('input[type=\"button\"][value=\"%s\"],button[value=\"%s\"],"+
					"input[type=\"submit\"][value=\"%s\"], input[type=\"reset\"][vaue=\"%s\"]', true);", how, how, how, how);
		} else {
			js = String.format("querySelector('input[type=\"%s\"][value=\"%s\"],%s[value=\"%s\"]', true)", 
					ele_type, how, ele_type, how);
		}
		
		if (parent == null) {
			js = "return document." + js;
			element = (WebElement)this.Browser.executeJS(js, null);
		} else {
			js = "return arguments[0]." + js;
			element = (WebElement)this.Browser.executeJS(js, parent);
		}
		
		return element;
	}
	
	private WebElement findElementByHref(String href, WebElement parent) {
		WebElement element = null;
		List<WebElement> list = null;
		
		href = this.replaceString(href);
		
		if (parent != null) {
			list = parent.findElements(By.tagName("a"));
		} else {
			list = this.Browser.getDriver().findElements(By.tagName("a"));
		}
		
		int len = list.size() -1;
		for (int i = 0; i <= len; i++) {
			String value = list.get(i).getAttribute("href");
			
			if (value != null && href.compareTo(value) == 0) {
				element = list.get(i);
				break;
			}
		}
		
		return element;
	}
	
	/*
	 * clickToBool -- method
	 * 	This method converts a string into a boolean type.
	 * 
	 * Input:
	 *  clickstr: a string containing "true" or "false".  Case doesn't matter.
	 *  
	 *  Output:
	 *   returns a boolean type.
	 */
	private boolean clickToBool(String clickstr) {
		boolean result = false;
		
		if (clickstr.toLowerCase().contains("true") || clickstr.toLowerCase().contains("false")) {
			result = Boolean.valueOf(clickstr).booleanValue();
		} else {
			if (clickstr.contains("0")) {
				result = false;
			} else {
				result = true;
			}
		}
		
		return result;
	}
	
	private boolean firePlugin(WebElement element, SodaElements type, SodaPluginEventType eventType) {
		boolean result = false;
		int len = 0;
		String js = "var CONTROL = arguments[0];\n\n";
		
		if (this.plugIns == null) {
			return true;
		}
		
		len = this.plugIns.size() -1;
		
		for (int i = 0; i <= len; i++) {
			SodaHash tmp = this.plugIns.get(i);

			if (tmp.get("control").toString().contains((type.toString().toLowerCase()))) {
				if (tmp.get("event").toString().contains(eventType.toString().toLowerCase())) {
					String jsfile = (String)tmp.get("jsfile");
					jsfile = FilenameUtils.separatorsToSystem(jsfile);
					String user_js = SodaUtils.FileToStr(jsfile);
					if (user_js != null) {
						js = js.concat(user_js);
						result = true;
						break;
					} else {
						String msg = String.format("Failed trying to read plugin source file: '%s'!", 
								(String)tmp.get("jsfile"));
						this.report.ReportError(msg);
						result = false;
					}
				}
			}
		}
		
		if (result) {
			this.report.Log("Plugin Event Started.");
			Object res = this.Browser.executeJS(js, element);
			int tmp = Integer.valueOf(res.toString());
			if (tmp == 0) {
				result = true;
			} else {
				result = false;
				String msg = String.format("Plugin Event Failed with return value: '%s'!", tmp);
				this.report.ReportError(msg);
			}
			
			this.report.Log("Plugin Event Finished.");
		}
		
		return result;
	}
	
	private WebElement buttonEvent(SodaHash event, WebElement parent) {
		boolean click = true;
		boolean required = true;
		WebElement element = null;
		
		this.resetThreadTime();	
		this.report.Log("Starting button event.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				if (required) {
					this.report.ReportError("Failed to find button!");
				} else {
					String msg = String.format("failed to find button, but required => '%s'.", required);
					this.report.Log(msg);
				}
				
				return null;
			}
			
			this.firePlugin(element, SodaElements.BUTTON, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("click")) {
				click = this.clickToBool(event.get("click").toString());
			}
			
			if (event.containsKey("alert")) {
				boolean alert = this.clickToBool(event.get("alert").toString());
				this.report.Log(String.format("Setting Alert Hack to: '%s'", alert));
				this.Browser.alertHack(alert);
			}
			
			if (click) {
				this.firePlugin(element, SodaElements.BUTTON, SodaPluginEventType.BEFORECLICK);
				this.report.Log("Clicking button.");
				element.click();
				this.report.Log("Finished clicking button.");
				this.firePlugin(element, SodaElements.BUTTON, SodaPluginEventType.AFTERCLICK);	
			}
			
			if (event.containsKey("jscriptevent")) {
				this.report.Log("Firing Javascript Event: "+ event.get("jscriptevent").toString());
				this.Browser.fire_event(element, event.get("jscriptevent").toString());
				Thread.sleep(1000);
				this.report.Log("Javascript event finished.");
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
			element = null;
		}
		
		this.resetThreadTime();
		this.report.Log("Finished button event.");
		
		return element;
	}

	private WebElement textareaEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		WebElement element = null;
		
		this.resetThreadTime();
		
		this.report.Log("Starting textarea event.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("Finished textarea event.");
				return null;
			}
			
			this.firePlugin(element, SodaElements.TEXTAREA, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("clear")) {
				if (this.clickToBool(event.get("clear").toString())) {
					this.report.Log("Clearing textarea.");
					element.clear();
				}
			}
			
			if (event.containsKey("set")) {
				String value = event.get("set").toString();
				value = this.replaceString(value);
				this.report.Log(String.format("Setting Value to: '%s'.", value));
				element.sendKeys(value);
			}
			
			if (event.containsKey("jscriptevent")) {
				this.report.Log("Firing Javascript Event: "+ event.get("jscriptevent").toString());
				this.Browser.fire_event(element, event.get("jscriptevent").toString());
				Thread.sleep(1000);
				this.report.Log("Javascript event finished.");
			}
			
			if (event.containsKey("assert")) {
				String assvalue = event.get("assert").toString();
				assvalue = this.replaceString(assvalue);
				this.report.Assert(assvalue, element.getAttribute("value"));
			}
			
			if (event.containsKey("assertnot")) {
				String assvalue = event.get("assertnot").toString();
				assvalue = this.replaceString(assvalue);
				this.report.AssertNot(assvalue, element.getAttribute("value"));
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
			element = null;
		}
		
		this.resetThreadTime();
		this.report.Log("Finished textarea event.");
		return element;
	}
	
	private WebElement textfieldEvent(SodaHash event, WebElement parent) {
		boolean required = true;
		WebElement element = null;
		
		this.resetThreadTime();
		this.report.Log("Starting textfield event.");
		
		if (event.containsKey("required")) {
			required = this.clickToBool(event.get("required").toString());
		}
		
		try {
			element = this.findElement(event, parent, required);
			if (element == null) {
				this.report.Log("Finished textfield event.");
				return null;
			}
			
			this.firePlugin(element, SodaElements.TEXTFIELD, SodaPluginEventType.AFTERFOUND);
			
			if (event.containsKey("clear")) {
				if (this.clickToBool(event.get("clear").toString())) {
					this.report.Log("Clearing textfield.");
					element.clear();
				}
			}
			
			if (event.containsKey("set")) {
				String value = event.get("set").toString();
				value = this.replaceString(value);
				this.report.Log(String.format("Setting Value to: '%s'.", value));
				element.sendKeys(value);
			}
			
			if (event.containsKey("jscriptevent")) {
				this.report.Log("Firing Javascript Event: "+ event.get("jscriptevent").toString());
				this.Browser.fire_event(element, event.get("jscriptevent").toString());
				Thread.sleep(1000);
				this.report.Log("Javascript event finished.");
			}
			
			if (event.containsKey("assert")) {
				String assvalue = event.get("assert").toString();
				assvalue = this.replaceString(assvalue);
				this.report.Assert(assvalue, element.getAttribute("value"));
			}
			
			if (event.containsKey("assertnot")) {
				String assvalue = event.get("assertnot").toString();
				assvalue = this.replaceString(assvalue);
				this.report.AssertNot(assvalue, element.getAttribute("value"));
			}
		} catch (Exception exp) {
			this.report.ReportException(exp);
			element = null;
		}
		
		this.resetThreadTime();
		this.report.Log("Finished textfield event.");
		return element;
	}
	
	private boolean putsEvent(SodaHash event) {
		boolean result = false;
		String msg = "";
		
		this.resetThreadTime();		
		msg = this.replaceString(event.get("text").toString());
		this.report.Log(msg);
		result = true;
		this.resetThreadTime();
		return result;
	}
}

/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package edu.vu.isis.logger.ui;

import java.util.List;

import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.joran.action.Action;
import ch.qos.logback.core.joran.spi.ActionException;
import ch.qos.logback.core.joran.spi.InterpretationContext;

public class LoggerConfigureAction extends Action {

	public static final String NO_APPENDER_STR = "none";
	public static final String NO_LEVEL_STR = "null";
	
	public static final String NAME_ATR = "name";
	public static final String LEVEL_ATR = "level";
	public static final String APPENDER_ATR = "appender";
	public static final String ADDITIVITY_ATR = "additivity";
	
	private final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
	
	@Override
	public void begin(InterpretationContext ic, String name,
			Attributes attributes) throws ActionException {
		
		final String loggerName = attributes.getValue(NAME_ATR);
		
		// If we don't have a logger name then we can't do anything
		if(loggerName == null) return;
		// If that logger doesn't exist yet then we have no reason to configure it
		if(!containsByName(lc.getLoggerList(), loggerName)) return;
		
		final Logger logger = lc.getLogger(loggerName);
		
		// Set the logger's level
		final String levelStr = attributes.getValue(LEVEL_ATR);
		if(levelStr != null) {
			logger.setLevel(Level.toLevel(levelStr, null));
		}
		
		// Attach the appropriate appenders only if those appenders have been created
		// by Joran from the logback config file
		final String[] appenderNames = attributes.getValue(APPENDER_ATR).split(" ");
		if (appenderNames != null) {
			logger.detachAndStopAllAppenders();
			for(int i=0; i<appenderNames.length; i++) {
				//TODO: This will have to be made to work with a content provider
//				final Appender<ILoggingEvent> appender = findMatchingAppender(
//						AppenderStore.getInstance().getAppenders(), appenderNames[i]);
//				if (appender != null)
//					logger.addAppender(appender);
			}
		}
		
		// Set the logger's additivity
		final String additivityStr = attributes.getValue(ADDITIVITY_ATR);
		if(additivityStr != null) logger.setAdditive(Boolean.valueOf(additivityStr));
		
	}

	@Override
	public void end(InterpretationContext ic, String name)
			throws ActionException {
		//NOP
	}
	
	
	private boolean containsByName(List<Logger> list, String name) {
		for(Logger logger : list) {
			if(logger.getName().equals(name)) return true;
		}
		return false;
	}
	
	
	private Appender<ILoggingEvent> findMatchingAppender(List<Appender<ILoggingEvent>> list, String name) {
		for(Appender<ILoggingEvent> a : list) {
			if(a.getName().equals(name)) return a;
		}
		return null;
	}
	

}

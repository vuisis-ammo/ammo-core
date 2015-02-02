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

import java.util.Map;

public class AppenderStore {

	private final Map appenderBag;
	private static AppenderStore instance = null;

	private AppenderStore() { 
		appenderBag = null; 
		throw new AssertionError("This constructor should never be called");
	}
	
	private AppenderStore(Map map) {
		appenderBag = map;
	}
	
	/**
	 * Stores a reference to the map of appenders created by Joran.  This method
	 * may only be called once per classloader.
	 * @param map The map of appenders
	 * @throws IllegalStateException if this method has already been called.
	 */
	public static synchronized void storeReference(Map map) {
		if (instance != null) {
			throw new IllegalStateException(
					"A reference has already been stored.");
		}
		instance = new AppenderStore(map);
	}

	/**
	 * Gets the map of appenders created by Joran.
	 * @return the map of appenders
	 * @throws IllegalStateException if no reference to the map has yet been stored
	 */
	public static synchronized Map getAppenderMap() {
		if (instance == null) {
			throw new IllegalStateException(
					"No reference has yet been stored.");
		}
		return instance.getAppenderBag();
	}

	private Map getAppenderBag() {
		return appenderBag;
	}
	
	public static synchronized void reset() {
	  instance = null;
	}

}

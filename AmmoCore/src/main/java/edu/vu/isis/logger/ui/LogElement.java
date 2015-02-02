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

/**
 * Represents a single element in a list of log entries
 * @author Nick King
 *
 */
public class LogElement {
	
	private LogLevel mLevel;
	private String mMessage;
	
	/**
	 * Make a new LogElement with the given level and message
	 * @param level -- the level corresponding to the entry
	 * @param message -- the logged message
	 */
	public LogElement(LogLevel level, String message) {
		this.mLevel = level;
		this.mMessage = message;
	}
	
	public LogLevel getLogLevel() {
		return this.mLevel;
	}
	
	public String getMessage() {
		return this.mMessage;
	}
	
	@Override
	public String toString() {
		return "LogElement Level: [" + mLevel.toString() + "] Message: [" + mMessage + "]";
	}
	
	@Override
	public int hashCode() {
		int result = 731;
		result = 31 * result + mLevel.hashCode();
		result = 31 * result + mMessage.hashCode();
		return result;
	}
	
	
	@Override
	public boolean equals(Object other) {
		if(!(other instanceof LogElement)) return false;
		LogElement otherElement = (LogElement) other;
		return otherElement.mMessage.equals(this.mMessage) && otherElement.mLevel.equals(this.mLevel);
	}
	
}

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

import android.content.Context;
import edu.vu.isis.ammo.core.R;

/**
 * Represents the corresponding names and color values of all possible log levels
 * @author Nick King
 *
 */
public enum LogLevel {
	
	Verbose(R.string.verbose_text_name, R.color.verbose_text_color),
	Trace(R.string.trace_text_name, R.color.trace_text_color),
	Debug(R.string.debug_text_name, R.color.debug_text_color),
	Info(R.string.info_text_name, R.color.info_text_color),
	Warn(R.string.warn_text_name, R.color.warn_text_color),
	Error(R.string.error_text_name, R.color.error_text_color),
	Fail(R.string.fail_text_name, R.color.fail_text_color),
	None(R.string.none_text_name, R.color.none_text_color);
	
	
	private int mColorResId;
	private int mNameResId;
	
	private LogLevel(int nameResId, int colorResId) {
		this.mNameResId = nameResId;
		this.mColorResId = colorResId;
	}
	
	public String getName(Context context) {
		return (String) context.getResources().getText(this.mNameResId);
	}
	
	public int getColor(Context context) {
		return context.getResources().getColor(this.mColorResId);
	}
	
}

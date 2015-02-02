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

package edu.vu.isis.ammo.core;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.widget.Toast;

/**
 * EditText widget that appears in a dialog when preference item is selected. 
 * @author demetri
 *
 */
public class MyEditTextPreference extends EditTextPreference {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger("ui.etprefs");
	public static enum Type {
		IP, DEVICE_ID, OPERATOR_ID, OPERATOR_KEY,
		LOG_LEVEL
	};
	
	// ===========================================================
	// Fields
	// ===========================================================
	private String summaryPrefix = "";
	private String summarySuffix = "";
	private OnPreferenceClickListener mOnPreferenceClickListener;
	private Type mType;
	private Context context;
	
	
	// ===========================================================
	// Lifecycle
	// ===========================================================
	public MyEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
	}

	public MyEditTextPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;	
	}
	
	public MyEditTextPreference(Context context) {
		super(context);
		this.context = context;
	}
	
	public MyEditTextPreference(Context context, String aSummaryPrefix, String aSummarySuffix) {
		super(context);
		this.context = context;
		summaryPrefix = aSummaryPrefix;
		summarySuffix = aSummarySuffix;
	}
	
	// ===========================================================
	// IP/Port Input Management
	// ===========================================================
	
	@Override
	public String getText() {
		final String base = super.getText();
		return base;
	}
	
	@Override
	public void setText(String uncheckedText) {
		// We should do some bounds checking here based on type of ETP.
		String checkedText = uncheckedText;
		
		if (mType == null) { 
			super.setText(checkedText);
			this.setSummary(new StringBuilder().append(summaryPrefix).append(checkedText).toString());
			return;
		}
		
		switch (mType) {
		case IP:
			if (!this.validateIP(uncheckedText)) {
				Toast.makeText(context, "Invalid IP, please try again", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			}
			
			break;
	
		case DEVICE_ID:
			//checkedText = this.getText();
			break;
			
		case OPERATOR_ID:
			// checkedText = this.getText();
			break;
			
		case OPERATOR_KEY:
			// checkedText = this.getText();
			
		case LOG_LEVEL:
			// checkedText = this.getText();
			
		default:
				// do nothing.
		}
		super.setText(checkedText);
		this.setSummary(new StringBuilder().append(summaryPrefix).append(checkedText).append(summarySuffix).toString());
	}
		
	/**
	 *  Checks whether or not the input ip address is valid for IPv4 protocol.
	 *  @see http://forums.sun.com/thread.jspa?threadID=584205
	 *  
	 * @param ip
	 * @return
	 */
	public boolean validateIP(String ip) {
		 String two_five_five = "(?:[0-9]|[1-9][0-9]|1[0-9][0-9]|2(?:[0-4][0-9]|5[0-5]))";
	        Pattern IPPattern = Pattern.compile("^(?:"+two_five_five+"\\.){3}"+two_five_five+"$");
		return IPPattern.matcher(ip).matches();
	}
	
	@Override 
	protected void onDialogClosed (boolean positiveResult) {
		super.onDialogClosed(positiveResult);
		
		// Set the summary field to newly set value in the edit text.
		this.refresh();
	}
	
	/**
	 *  Set the summary field such that it displays the value of the edit text.
	 */
	public void refresh() {
		final String value = this.getPersistedString(this.getText());
		this.setText(value);
		this.setSummary(new StringBuilder().append(summaryPrefix).append(value).append(summarySuffix).toString());	
	}

	@Override
	public void onClick() {
		if(mOnPreferenceClickListener == null) {
			super.onClick();
		} else {
			mOnPreferenceClickListener.onPreferenceClick(this);
		}
	}
	

	// ===========================================================
	// Getters/Setters Methods
	// ===========================================================
	public String getSummaryPrefix() {
		return summaryPrefix;
	}

	public void setSummaryPrefix(String summaryPrefix) {
		this.summaryPrefix = summaryPrefix;
	}
	
	public String getSummarySuffix() {
		return summarySuffix;
	}
	
	public void setSummarySuffix(String summarySuffix) {
		this.summarySuffix = summarySuffix;
	}
	
	@Override
	public void setOnPreferenceClickListener(OnPreferenceClickListener listener) {
		this.mOnPreferenceClickListener = listener;
	}

	public void setType(Type mType) {
		this.mType = mType;
	}

	public Type getType() {
		return mType;
	}
	
	
}

/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
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
	public static final Logger logger = LoggerFactory.getLogger("ammo.class.MyEditTextPreference");
	public static enum Type {
		IP, DEVICE_ID, OPERATOR_ID, OPERATOR_KEY,
		LOG_LEVEL
	};
	
	// ===========================================================
	// Fields
	// ===========================================================
	private String summaryPrefix = "";
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
	
	public MyEditTextPreference(Context context, String aSummaryPrefix) {
		super(context);
		this.context = context;
		summaryPrefix = aSummaryPrefix;
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
		this.setSummary(new StringBuilder().append(summaryPrefix).append(checkedText).toString());
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
		this.setSummary(new StringBuilder().append(summaryPrefix).append(value).toString() );	
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

	public void setType(Type mType) {
		this.mType = mType;
	}

	public Type getType() {
		return mType;
	}
	
	
}

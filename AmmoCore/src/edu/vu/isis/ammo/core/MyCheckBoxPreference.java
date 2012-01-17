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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
/**
 * This is a checkbox widget that can be used in the preferences screen of 
 * AmmoCore. It currently is not in use.
 * @author demetri
 *
 */
public class MyCheckBoxPreference extends CheckBoxPreference {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger(MyCheckBoxPreference.class);
	
	public static enum Type {
		JOURNAL
	};
	// ===========================================================
	// Fields
	// ===========================================================
	private String summaryPrefix = "";
	private Type mType;
	@SuppressWarnings("unused")
	private Context context;
	
	// ===========================================================
	// Lifecycle
	// ===========================================================
	public MyCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
	}

	public MyCheckBoxPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;	
	}
	
	public MyCheckBoxPreference(Context context) {
		super(context);
		this.context = context;
	}
	
	public MyCheckBoxPreference(Context context, String aSummaryPrefix) {
		super(context);
		this.context = context;
		summaryPrefix = aSummaryPrefix;
	}
	
	// ===========================================================
	// 
	// ===========================================================

	/**
	 *  Set the summary field such that it displays the value of the edit text.
	 */
	public void refreshSummaryField() {
		if (!summaryPrefix.equals("")) {
			this.setSummary(summaryPrefix + this.isChecked());	
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

	public void setType(Type mType) {
		this.mType = mType;
	}

	public Type getType() {
		return mType;
	}
	
	
}

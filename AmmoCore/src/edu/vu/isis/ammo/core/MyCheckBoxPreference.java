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
import android.preference.Preference.OnPreferenceClickListener;
import android.util.AttributeSet;

/**
 * This is a checkbox widget that can be used in the preferences screen of
 * AmmoCore.
 * 
 * @author demetri
 * 
 */
public class MyCheckBoxPreference extends CheckBoxPreference {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger("ui.cbprefs");
	private static final String DEFAULT_SUMMARY = "Touch me to change in Pather Prefs";

	public static enum Type {
		JOURNAL
	};

	// ===========================================================
	// Fields
	// ===========================================================
	private String mFalseTitle = "False";
	private String mTrueTitle = "True";

	private Type mType;
	@SuppressWarnings("unused")
	private Context context;
	private OnPreferenceClickListener mOnClickListener;

	// ===========================================================
	// Lifecycle
	// ===========================================================
	public MyCheckBoxPreference(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
		setSummary(DEFAULT_SUMMARY);
	}

	public MyCheckBoxPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		setSummary(DEFAULT_SUMMARY);
	}

	public MyCheckBoxPreference(Context context) {
		super(context);
		this.context = context;
		setSummary(DEFAULT_SUMMARY);
	}

	public MyCheckBoxPreference(Context context, String falseTitle,
			String trueTitle) {
		super(context);
		this.context = context;
		mFalseTitle = falseTitle;
		mTrueTitle = trueTitle;
		setSummary(DEFAULT_SUMMARY);
	}

	// ===========================================================
	//
	// ===========================================================

	/**
	 * Set the summary field such that it displays the current state
	 */
	public void refresh() {
		final boolean value = this.getPersistedBoolean(this.isChecked());
		this.setChecked(value);
	}

	// ===========================================================
	// Getters/Setters Methods
	// ===========================================================
	public String getFalseTitle() {
		return mFalseTitle;
	}

	public void setFalseTitle(String falseTitle) {
		this.mFalseTitle = falseTitle;
	}

	public String getTrueTitle() {
		return mTrueTitle;
	}

	public void setTrueTitle(String trueTitle) {
		mTrueTitle = trueTitle;
	}

	public void setType(Type mType) {
		this.mType = mType;
	}

	public Type getType() {
		return mType;
	}

	/*
	 * We override this method so that we can fire our own
	 * OnPreferenceClickListener in our overridden onClick() method. The
	 * OnPreferenceClickListener stored in the base Preference class is private.
	 */
	@Override
	public void setOnPreferenceClickListener(
			OnPreferenceClickListener onPreferenceClickListener) {
		mOnClickListener = onPreferenceClickListener;
	}

	/*
	 * We override this method because of the request that Ammo should not be
	 * changing the actual state of the check boxes. We were told that only
	 * Panther Prefs should be changing their state. Overriding this allows us
	 * to only fire the OnPreferenceClickListener and do nothing else. A regular
	 * CheckBoxPreference does its own management of changing its state in this
	 * method. If it is necessary to change the state of this
	 * CheckBoxPreference, then this can be accomplished in the
	 * OnPreferenceClickListener
	 */
	@Override
	protected void onClick() {
		if (mOnClickListener == null)
			return;
		mOnClickListener.onPreferenceClick(this);
	}

	/**
	 * Convenience method to force the CheckBoxPreference to toggle itself. A
	 * call will not be made to the OnPreferenceClickListener. An example of why
	 * this is useful would be putting this method inside of an
	 * OnPreferenceClickListener to cause the CheckBoxPreference to still toggle
	 * itself on a click as one would expect.
	 */
	public void toggle() {
		super.onClick();
	}

	@Override
	public boolean isChecked() {
		return super.isChecked();
	}

	@Override
	public void setChecked(boolean checked) {
		super.setChecked(checked);
		
		// XXX: The logic here is inverted because Panther Prefs
		// is still using inverted logic to choose whether a channel is
		// enabled or disabled.  Also see status_indicator.xml and
		// switch the android:state_checked attributes once this has
		// been fixed.
		this.setTitle(isChecked() ? mFalseTitle : mTrueTitle);
	}

}

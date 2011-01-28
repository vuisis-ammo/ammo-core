package edu.vu.isis.ammo.core;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.widget.Toast;

public class CustomCheckBoxPreference extends CheckBoxPreference {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger(CustomCheckBoxPreference.class);
	
	public static enum Type {
		JOURNAL
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
	public CustomCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
	}

	public CustomCheckBoxPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;	
	}
	
	public CustomCheckBoxPreference(Context context) {
		super(context);
		this.context = context;
	}
	
	public CustomCheckBoxPreference(Context context, String aSummaryPrefix) {
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

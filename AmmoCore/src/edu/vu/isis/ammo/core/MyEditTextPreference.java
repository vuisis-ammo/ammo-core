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
	public static final Logger logger = LoggerFactory.getLogger(MyEditTextPreference.class);
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
	public void setText(String uncheckedText) {
		// We should do some bounds checking here based on type of ETP.
		String checkedText = uncheckedText;
		
		if (mType == null) { 
			super.setText(checkedText);
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
			checkedText = this.getText();
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
		this.refreshSummaryField();
	}
	
	/**
	 *  Set the summary field such that it displays the value of the edit text.
	 */
	public void refreshSummaryField() {
		this.setSummary(summaryPrefix + this.getText());	
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

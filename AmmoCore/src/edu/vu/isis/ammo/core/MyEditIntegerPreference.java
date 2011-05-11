package edu.vu.isis.ammo.core;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.widget.Toast;

/**
 * EditText widget used in preferences and holds number values. 
 * @author demetri
 *
 */
public class MyEditIntegerPreference extends EditTextPreference {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger(MyEditIntegerPreference.class);
	public static enum Type {
		PORT, TIMEOUT
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
	public MyEditIntegerPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
	}

	public MyEditIntegerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;	
	}
	
	public MyEditIntegerPreference(Context context) {
		super(context);
		this.context = context;
	}
	
	public MyEditIntegerPreference(Context context, String aSummaryPrefix) {
		super(context);
		this.context = context;
		summaryPrefix = aSummaryPrefix;
	}
	
	// ===========================================================
	// IP/Port Input Management
	// ===========================================================
	public void setText(String uncheckedText) {
		// We should do some bounds checking here based on type of ETP.
		String checkedText = uncheckedText;

		if (mType == null) { 
			super.setText(checkedText);
			return;
		}

		switch (mType) {
		case PORT:
			if (!this.validatePort(uncheckedText)) {
				Toast.makeText(context, "Invalid port, please try again", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			}
			
			break;

		case TIMEOUT:
			if (!this.validateTimeout(uncheckedText)) {
				Toast.makeText(context, "Invalid timeout value", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
				
			}
			else
			{
				//Convert to milliseconds
				checkedText = Integer.toString((Integer.parseInt(uncheckedText)*1000));
			}
			
			
			break;
		default:
			// do nothing.
		}
		
		
		super.setText(checkedText);
	}
	
	
	
	
	public String getText() {
		// We should do some bounds checking here based on type of ETP.
		String value = super.getText();
		switch (mType)
		{
		case TIMEOUT:
			return Integer.toString(Integer.parseInt(value)/1000);
		default:
			return value;
		}

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
	/**
	 * Check that the port value supplied is appropriate.
	 * @see http://en.wikipedia.org/wiki/List_of_TCP_and_UDP_port_numbers
	 * 
	 * @param port
	 * @return
	 */
	public boolean validatePort(String port) {
		try {
			if (port.length() > 5) return false;
			if (port.length() < 2) return false;
			int portAsInt = Integer.valueOf(port);
			if (portAsInt < 1) return false;
			if (portAsInt < 1024) {
				logger.debug(context.getResources().getString(R.string.well_known_port));
				return false;
			}
			if (portAsInt < 49151) {
				logger.debug(context.getResources().getString(R.string.reserved_port));
				return true;
			}
			return true;
		} catch (NumberFormatException e) {
			logger.debug("Invalid port number");
			return false;
		}
	}
	
	/**
	 * Convert the timeout parameter to a string and make sure it is non-negative.
	 * @param timeout
	 * @return
	 */
	public boolean validateTimeout(String timeout) {
		boolean returnValue = false;
		try {
			Integer intValue = Integer.valueOf(timeout);
			if (intValue > 0) {
				returnValue = true;
			}
		} catch (NumberFormatException e) {
			logger.debug("::validateTimeout - NumberFormatException");
		}
		
		return returnValue;
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
		if (!summaryPrefix.equals("")) {
			this.setSummary(summaryPrefix + this.getText());	
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

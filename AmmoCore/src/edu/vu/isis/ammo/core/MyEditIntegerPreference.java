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
		PORT, TIMEOUT, BAUDRATE, SLOT_NUMBER, RADIOS_IN_GROUP, SLOT_DURATION, TRANSMIT_DURATION, TTL
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
		case TIMEOUT:
			if (!this.validateTimeout(uncheckedText)) {
				Toast.makeText(context, "Invalid timeout value", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			}
			else
			{
				//Shared enum solution. It's not exactly graceful, but it works.
				if(this.getKey().equals("AMMO_NET_CONN_FLAT_LINE_TIME"))
				{
					//Input is in seconds, we need to store as minutes
					checkedText = Integer.toString((Integer.parseInt(uncheckedText)/60));

				}
				else if(this.getKey().equals("CORE_SOCKET_TIMEOUT"))
				{
					//Input is in seconds, we need to store as milliseconds
					checkedText = Integer.toString((Integer.parseInt(uncheckedText)*1000));
				}
			}
            break;
		case PORT:
			if (!this.validatePort(uncheckedText)) {
				Toast.makeText(context, "Invalid port", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			}
			break;
		case BAUDRATE:
			if(!this.validateBaudrate(uncheckedText)) {
				Toast.makeText(context, "Invalid baud rate", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			} else {
				checkedText = uncheckedText;
			}
            break;
		case SLOT_NUMBER:
			if(!this.validateSlotNumber(uncheckedText)) {
				Toast.makeText(context, "Invalid slot number", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			} else {
				checkedText = uncheckedText;
			}
            break;
		case RADIOS_IN_GROUP:
			if(!this.validateRadiosInGroup(uncheckedText)) {
				Toast.makeText(context, "Invalid radios in group", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			} else {
				checkedText = uncheckedText;
			}
			break;
		case SLOT_DURATION:
			if(!this.validateSlotDuration(uncheckedText)) {
				Toast.makeText(context, "Invalid slot duration", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			} else {
				checkedText = uncheckedText;
			}
			break;
		case TRANSMIT_DURATION:
			if(!this.validateTransmitDuration(uncheckedText)) {
				Toast.makeText(context, "Invalid transmit duration", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			} else {
				checkedText = uncheckedText;
			}
			break;
		case TTL:
			if (!this.validateTTL(uncheckedText)) {
				Toast.makeText(context, "Invalid TTL value", Toast.LENGTH_SHORT).show();
				checkedText = this.getText();
			}
			break;
		default:
			// do nothing.
            break;
		}

		super.setText(checkedText);
	}


    //
    // Validation methods
    //

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


	private boolean validateBaudrate(String uncheckedText) {
        try {
            // Will we need something different here?  Are other baud rates
            // even supported?
            return Integer.parseInt(uncheckedText) == 9600;
        } catch ( NumberFormatException e ) {
            return false;
        }
	}


    // It would be good to make sure that slot number < radios in group - 1.
	private boolean validateSlotNumber(String uncheckedText) {
        try {
            return Integer.parseInt(uncheckedText) > 0;
        } catch ( NumberFormatException e ) {
            return false;
        }
	}


	private boolean validateRadiosInGroup( String uncheckedText ) {
        try {
            return Integer.parseInt(uncheckedText) > 0;
        } catch ( NumberFormatException e ) {
            return false;
        }
	}


	private boolean validateSlotDuration( String uncheckedText ) {
        try {
            return Integer.parseInt(uncheckedText) > 0;
        } catch ( NumberFormatException e ) {
            return false;
        }
	}

    // It would be good to make sure that transmit duration < slot duration.
	private boolean validateTransmitDuration( String uncheckedText ) {
        try {
            return Integer.parseInt(uncheckedText) > 0;
        } catch ( NumberFormatException e ) {
            return false;
        }
	}


	public String getText() {
		// We should do some bounds checking here based on type of ETP.
		String value = super.getText();
		switch (mType)
		{
		case TIMEOUT:
				if(this.getKey().equals("AMMO_NET_CONN_FLAT_LINE_TIME"))
				{
					return Integer.toString(Integer.parseInt(value)*60);
				}
				else if(this.getKey().equals("CORE_SOCKET_TIMEOUT"))
				{
					return Integer.toString(Integer.parseInt(value)/1000);
				}

		default:
			return value;
		}

	}

	/**
	 * Check that the TTL value supplied is appropriate (1 <= ttl <= 255)
	 *
	 * @param ttl
	 * @return
	 */
	public boolean validateTTL(String ttl) {
		try {
			int ttlAsInt = Integer.valueOf(ttl);
			if (ttlAsInt < 1)
                return false;
			if (ttlAsInt > 255)
                return false;
			return true;
		} catch (NumberFormatException e) {
			logger.debug("Invalid TTL number");
			return false;
		}
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

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
import edu.vu.isis.ammo.INetPrefKeys;

/**
 * EditText widget used in preferences and holds number values. 
 * @author demetri
 *
 */
public class MyEditIntegerPreference extends EditTextPreference {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger("ui.eiprefs");
	public static enum Type {
		PORT, TIMEOUT, BAUDRATE, SLOT_NUMBER, RADIOS_IN_GROUP, SLOT_DURATION, TRANSMIT_DURATION, TTL
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
	
	public MyEditIntegerPreference(Context context, String aSummaryPrefix, String aSummarySuffix) {
		super(context);
		this.context = context;
		summaryPrefix = aSummaryPrefix;
		summarySuffix = aSummarySuffix;
	}

	// ===========================================================
	// IP/Port Input Management
	// ===========================================================
	public void setText(String uncheckedText) {
		// We should do some bounds checking here based on type of ETP.
		String checkedText = uncheckedText;

		if (mType == null) {
			super.setText(checkedText);
			this.setSummary(new StringBuilder().append(summaryPrefix).append(checkedText).toString());
			return;
		}

		switch (mType) {
		case TIMEOUT:
			if (!this.validateTimeout(uncheckedText)) {
				Toast.makeText(context, new StringBuilder()
                                                   .append("Invalid timeout value: ")
                                                   .append(uncheckedText)
                                                   .toString(), 
                                               Toast.LENGTH_SHORT)
                                     .show();
				checkedText = this.getText();
			}
			else
			{
			
				//Shared enum solution. It's not exactly graceful, but it works.
				if(this.getKey().equals(INetPrefKeys.GATEWAY_FLAT_LINE_TIME))
				{
					//Input is in seconds, we need to store as minutes
					checkedText = Integer.toString((Integer.parseInt(uncheckedText)/60));

				} else 
				if(this.getKey().equals(INetPrefKeys.GATEWAY_TIMEOUT))
				{
					//Input is in seconds, we need to store as milliseconds
					checkedText = Integer.toString((Integer.parseInt(uncheckedText)*1000));
				}
				else {
					checkedText = uncheckedText;
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
		this.setSummary(new StringBuilder().append(summaryPrefix).append(checkedText).append(summarySuffix).toString());
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
			} else {
			   logger.warn("invalid timeout value {}", timeout);
                        }
		} catch (NumberFormatException e) {
			logger.warn("::validateTimeout - NumberFormatException {}", timeout);
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
        	// We were only allowing a baudrate of 9600 before.  Sandeep
        	// wanted to lift this constraint for testing purposes.  I'm
        	// leaving the previous code here since we may want it in the
        	// future.
            return Integer.parseInt(uncheckedText) > 0;
            //return Integer.parseInt(uncheckedText) == 9600;
        } catch ( NumberFormatException e ) {
            return false;
        }
	}


    // It would be good to make sure that slot number < radios in group - 1.
	private boolean validateSlotNumber(String uncheckedText) {
        try {
            return Integer.parseInt(uncheckedText) >= 0;
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
		final String value = super.getText();
		
		if(mType == null) {
		    return value;
		}
		
		switch (mType)
		{
		case TIMEOUT:
				if(this.getKey().equals(INetPrefKeys.GATEWAY_FLAT_LINE_TIME))
				{
					return Integer.toString(Integer.parseInt(value)*60);
				}
				else if(this.getKey().equals(INetPrefKeys.GATEWAY_TIMEOUT))
				{
					return Integer.toString(Integer.parseInt(value)/1000);
				}
				else {
					return value;
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
		this.refresh();
	}
	
	/**
	 *  Set the summary field such that it displays the value of the edit text.
	 */
	public void refresh() {
		final String raw_value = this.getPersistedString(super.getText());
		final String cooked_value;
		
		if (mType == null) {
			cooked_value = raw_value;
    } else	 {
  		switch (mType)
  		{
  		case TIMEOUT:
  				if(this.getKey().equals(INetPrefKeys.GATEWAY_FLAT_LINE_TIME))
  				{
  					cooked_value = Integer.toString(Integer.parseInt(raw_value)*60);
  				}
  				else if(this.getKey().equals(INetPrefKeys.GATEWAY_TIMEOUT))
  				{
  					cooked_value =  Integer.toString(Integer.parseInt(raw_value)/1000);
  				}
  				else {
  					cooked_value = raw_value;
  				}
              break;
  		default:
  			cooked_value = raw_value;
  		}
    }
		this.setSummary(new StringBuilder()
		    .append(summaryPrefix)
		    .append(cooked_value)
		    .append(summarySuffix)
		    .toString() );
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

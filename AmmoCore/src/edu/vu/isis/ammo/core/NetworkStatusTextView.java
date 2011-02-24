package edu.vu.isis.ammo.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;
import edu.vu.isis.ammo.PrefKeys;

/**
 * TextView subclass used to format text based on the status of network
 * connection.
 * 
 * @author Demetri Miller
 * 
 */
public class NetworkStatusTextView extends TextView {

	/**
	 * @category Fields
	 */
	private Context mContext;

	/**
	 * @category Constructors
	 */

	/**
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public NetworkStatusTextView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
	}

	public NetworkStatusTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
	}

	public NetworkStatusTextView(Context context) {
		super(context);
	}

	/**
	 * Sets the text for this text view to the status and changes the color of
	 * the text appropriately.
	 * 
	 * @param prefs - Preference reference
	 * @param key - Preference key to be read to get status
	 * @param isUsingConnection - True if the user wants to use this connection. We 
	 * are only concerned with this field for display reasons
	 */
	
	public void notifyNetworkStatusChanged(SharedPreferences prefs, String statusKey) {
		// A connection can only have one status at a time so we can short circuit 
		// the if-else.
		boolean isConnected = prefs.getBoolean(statusKey + PrefKeys.NET_IS_ACTIVE, false);
		boolean shouldUse = prefs.getBoolean(statusKey + PrefKeys.NET_SHOULD_USE, false);
		boolean isAvailable = prefs.getBoolean(statusKey + PrefKeys.NET_IS_AVAILABLE, false);
		boolean isStale = prefs.getBoolean(statusKey + PrefKeys.NET_IS_STALE, false);
		
		int textColor = Color.WHITE;
		String text = "<undefined>";
		if (isConnected) {
			textColor = Color.argb(255, 66, 209, 66); // Green
			text = "Connected";
		} else if (isAvailable) {
			textColor = Color.argb(255, 255, 161, 66); // Orange
			text = "Available";
		} else if (isStale) {
			textColor = Color.argb(255, 212, 81, 0); // Dark orange 
			text = "Connecting...";
		} else {	// Default to not available
			textColor = Color.RED; 
			text = "Not available";
		}

		this.setText(text);
		this.setTextColor(textColor);
	}
}

package edu.vu.isis.ammo.core;

import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.PrefKeys;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;

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
		PrefKeys.ConnectionStatus status = PrefKeys.ConnectionStatus.values()[prefs.getInt(statusKey, 0)];
		int textColor = Color.WHITE;
		String text = "<undefined>";
		switch (status) {
		case NO_CONNECTION:
			text="Not Connected";
			textColor = Color.RED;
			break;
		case NOT_AVAILABLE:
			text="Connection Not Available";
			textColor = Color.RED;
			break;
		case CONNECTED:
			textColor = Color.argb(255, 66, 209, 66);
			text = "Connected";
			break;
		case AVAILABLE_NOT_CONNECTED:
			textColor = Color.argb(255, 255, 161, 66);
			text = "Available, but not connected";
			break;
		case PENDING:
			textColor = Color.argb(255, 255, 161, 66);
			text = "Connecting...";
			break;
		default:
			// do nothing;
		}

		this.setText(text);
		this.setTextColor(textColor);
	}
}

package edu.vu.isis.ammo.core.receiver;

import edu.vu.isis.ammo.core.MainActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;

/**
 * Catches changes in connection state (currently only wifi) and sets system preference
 * based on new status.
 * @author Demetri Miller
 *
 * TODO: This class should catch all connectivity related broadcasts and 
 * set flags appropriately.
 */
public class ConnectionReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// If the wifi state changed, update that flag.
		if (intent.getAction().equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
			boolean isConnected = intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false);
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			Editor editor = prefs.edit();
			editor.putBoolean(MainActivity.WIFI_LINK_PREF_KEY, isConnected).commit();
		}
	}

}

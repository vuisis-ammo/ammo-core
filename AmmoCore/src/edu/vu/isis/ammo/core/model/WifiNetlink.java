package edu.vu.isis.ammo.core.model;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.ui.TabActivityEx;


public class WifiNetlink extends Netlink {
	private WifiReceiver wifiReceiver;

	private WifiNetlink(TabActivityEx context) {
		super(context, "Wifi Netlink", "wifi");
	}
	
	public static Netlink getInstance(TabActivityEx context) {
		// initialize the gateway from the shared preferences
		return new WifiNetlink(context);
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(INetPrefKeys.WIFI_PREF_SHOULD_USE)) {
		      //shouldUse(prefs);
		}
	}
	
	/**
	 * Each time we start this activity, we need to update the status message
	 * for each connection since it may have changed since this activity was
	 * last loaded.
	 */
	private void setWifiStatus() {
		final Activity self = this.context;
		final Thread wifiThread = new Thread() {
			public void run() {
				logger.trace("::setWifiStatus");
				final ConnectivityManager connManager =
					(ConnectivityManager) WifiNetlink.this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
				final NetworkInfo info = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
				
				final int[] state = new int[1];
				switch( info.getDetailedState() ) {
				case DISCONNECTED      : state[0] = NETLINK_DISCONNECTED; break;
				case IDLE              : state[0] = NETLINK_IDLE ; break;
				case SCANNING          : state[0] = NETLINK_SCANNING; break;
				case CONNECTING        : state[0] = NETLINK_CONNECTING ; break;
				case AUTHENTICATING    : state[0] = NETLINK_AUTHENTICATING; break;
				case OBTAINING_IPADDR  : state[0] = NETLINK_OBTAINING_IPADDR ; break;
				case FAILED            : state[0] = NETLINK_FAILED ; break;
				}
				
				self.runOnUiThread(new Runnable() {
					public void run() {
						statusListener.onStatusChange(statusView, state);
					}});
			}
		};
		wifiThread.start();
	}


	// ===========================================================
	// UI Management
	// ===========================================================

	public void initialize() {
		// get the starting value
		logger.trace("WifiThread::run");
		setWifiStatus();

		// get updates as they happen
		this.wifiReceiver = new WifiReceiver();
		IntentFilter wifiFilter = new IntentFilter(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		this.context.registerReceiver(this.wifiReceiver, wifiFilter);
	}
	
	public void teardown() {
		try {
			this.context.unregisterReceiver(this.wifiReceiver);
		} catch(IllegalArgumentException ex) {
			logger.trace("tearing down the Wifi netlink object");
		} 
	}

	// ===========================================================
	// Inner Classes
	// ===========================================================
	private class WifiReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			logger.trace("WifiReceiver::onReceive");
		    setWifiStatus();
		}
	}

}

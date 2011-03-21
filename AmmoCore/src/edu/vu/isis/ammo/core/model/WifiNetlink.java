package edu.vu.isis.ammo.core.model;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
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
		Thread wifiThread = new Thread() {
			public void run() {
				logger.trace("::setWifiStatus");
				WifiManager manager = (WifiManager)WifiNetlink.this.context.getSystemService(Context.WIFI_SERVICE);
				WifiInfo info = manager.getConnectionInfo();
				logger.debug( "WifiInfo: " +  info.toString() );
				final int wifiConn = (info == null) 
				        ? NetworkInfo.DetailedState.FAILED.ordinal() 
						: WifiInfo.getDetailedStateOf(info.getSupplicantState()).ordinal();

				self.runOnUiThread(new Runnable() {
					public void run() {
						statusListener.onStatusChange(statusView, new int[]{ wifiConn });
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

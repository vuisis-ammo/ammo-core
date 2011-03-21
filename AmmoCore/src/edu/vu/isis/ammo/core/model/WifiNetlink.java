package edu.vu.isis.ammo.core.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
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
	 * 
	 */
	public void setWifiStatus() {
		logger.trace("::setWifiStatus");
		
	    Thread wifiThread = new Thread() {
		    public void run() {
		    	logger.trace("WifiThread::run");
		    	
				WifiManager manager = (WifiManager)WifiNetlink.this.context.getSystemService(Context.WIFI_SERVICE);
				WifiInfo info = manager.getConnectionInfo();
				logger.debug( "WifiInfo: " +  info.toString() );
				boolean wifiConn = (info != null && info.getSupplicantState() == SupplicantState.COMPLETED);
				Editor editor = PreferenceManager.getDefaultSharedPreferences(WifiNetlink.this.context).edit();
				// editor.putBoolean(INetPrefKeys.WIFI_PREF_IS_CONNECTED, wifiConn);
				editor.putBoolean(INetPrefKeys.WIFI_PREF_IS_AVAILABLE, wifiConn);
				// editor.putBoolean(INetPrefKeys.WIFI_PREF_SHOULD_USE, cbWifi.isChecked());		
				editor.commit();
		    }
		};
		wifiThread.start();
	}
	
	// ===========================================================
	// UI Management
	// ===========================================================

	public void initialize() {
		int[] status = new int[]{ 3 };
		this.statusListener.onStatusChange(this.statusView, status);
		this.registerReceivers();
	}
	
	// Broadcast Receivers
	// ===========================================================
	public void registerReceivers() {
		logger.trace("::registerReceivers");
		
		this.wifiReceiver = new WifiReceiver();
		IntentFilter wifiFilter = new IntentFilter(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		this.registerReceiver(this.wifiReceiver, wifiFilter);
	}
	
	private void registerReceiver(WifiReceiver wifiReceiver2, IntentFilter wifiFilter) {
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

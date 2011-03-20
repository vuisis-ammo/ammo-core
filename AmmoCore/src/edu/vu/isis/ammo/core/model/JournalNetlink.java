package edu.vu.isis.ammo.core.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.R;


public class JournalNetlink extends Netlink {
	private WifiReceiver wifiReceiver;
	
	private JournalNetlink(Context context, String type) {
		super(context, type);
	}
	
	public static Netlink getInstance(Context context) {
		// initialize the gateway from the shared preferences
		return new Netlink(context, "Journal Netlink");
	}
	

//	prefChannelJournal = (MyCheckBoxPreference) findPreference(INetPrefKeys.CORE_IS_JOURNALED);
//	prefChannelJournal.setSummaryPrefix(res.getString(R.string.channel_journal_label));
//	prefChannelJournal.setType(MyCheckBoxPreference.Type.JOURNAL);
//	
	/**
	 * 
	 * Each time we start this activity, we need to update the status message
	 * for each connection since it may have changed since this activity was
	 * last loaded.
	 * 
	 * TODO: Clean this up.
	 */
	public void setWifiStatus() {
		logger.trace("::setWifiStatus");
		
	    Thread wifiThread = new Thread() {
		    public void run() {
		    	logger.trace("WifiThread::run");
		    	
				WifiManager manager = (WifiManager)JournalNetlink.this.context.getSystemService(Context.WIFI_SERVICE);
				WifiInfo info = manager.getConnectionInfo();
				logger.debug( "WifiInfo: " +  info.toString() );
				boolean wifiConn = (info != null && info.getSupplicantState() == SupplicantState.COMPLETED);
				Editor editor = PreferenceManager.getDefaultSharedPreferences(JournalNetlink.this.context).edit();
				// editor.putBoolean(INetPrefKeys.WIFI_PREF_IS_CONNECTED, wifiConn);
				editor.putBoolean(INetPrefKeys.WIFI_PREF_IS_AVAILABLE, wifiConn);
				// editor.putBoolean(INetPrefKeys.WIFI_PREF_SHOULD_USE, cbWifi.isChecked());		
				editor.commit();
		    }
		};
		wifiThread.start();
	}
	
	// =======
	// ===========================================================
	// UI Management
	// ===========================================================

	// Broadcast Receivers
	// ===========================================================
	public void registerReceivers() {
		logger.trace("::registerReceivers");
		
		this.wifiReceiver = new WifiReceiver();
		IntentFilter wifiFilter = new IntentFilter(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		this.registerReceiver(this.wifiReceiver, wifiFilter);
	}
	
	private void registerReceiver(WifiReceiver wifiReceiver2,
			IntentFilter wifiFilter) {
		// TODO Auto-generated method stub
		
	}

	// ===========================================================
	// Inner Classes
	// ===========================================================
	private class WifiReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			logger.trace("WifiReceiver::onReceive");
		    // updateConnectionStatus(prefs);
		    setWifiStatus();
		}
	}
	
}

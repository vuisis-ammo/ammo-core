package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import edu.vu.isis.ammo.AmmoPrefKeys;
import edu.vu.isis.ammo.core.network.INetworkBinder;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.util.UniqueIdentifiers;

/**
 * The principle activity for the ammo core application.
 * Provides a means for...
 * ...changing the user preferences.
 * ...checking delivery status of various messages.
 * ...registering/unregistering content interest requests.
 * 
 * @author phreed
 *
 */
public class MainActivity extends Activity 
implements OnClickListener, OnSharedPreferenceChangeListener 
{

	public static final Logger logger = LoggerFactory.getLogger(MainActivity.class);
	private static final int PREFERENCES_MENU = Menu.NONE + 0;
	private static final int DELIVERY_STATUS_MENU = Menu.NONE + 1;
	private static final int SUBSCRIPTION_MENU = Menu.NONE + 2;
	private static final int SUBSCRIBE_MENU = Menu.NONE + 3;
	private static final int LOGGING_MENU = Menu.NONE + 4;
	
	// ===========================================================
	// Fields
	// ===========================================================
	private NetworkStatusTextView tvPhysicalLink, tvWifi;
	private Button btnConnect;
	private CheckBox cbPhysicalLink, cbWifi;
	private WifiReceiver wifiReceiver;
	private SharedPreferences prefs;

	
	/**
	 * @Cateogry Lifecycle
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		this.setViewReferences();
		this.setOnClickListeners();
		this.registerReceivers();
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String deviceId = UniqueIdentifiers.device(this);
		prefs.registerOnSharedPreferenceChangeListener(this);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(CorePreferences.PREF_DEVICE_ID, deviceId).commit();
		
		this.startService(ICoreService.CORE_APPLICATION_LAUNCH_SERVICE_INTENT);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		setWifiStatus();
		this.updateConnectionStatus(prefs);
	}

	// Create a menu which contains a preferences button.
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
			super.onCreateContextMenu(menu, v, menuInfo);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(Menu.NONE, PREFERENCES_MENU, Menu.NONE, getResources().getString(R.string.pref_label));
		menu.add(Menu.NONE, DELIVERY_STATUS_MENU, Menu.NONE, getResources().getString(R.string.delivery_status_label));
		menu.add(Menu.NONE, SUBSCRIPTION_MENU, Menu.NONE, getResources().getString(R.string.subscription_label));
		menu.add(Menu.NONE, SUBSCRIBE_MENU, Menu.NONE, getResources().getString(R.string.subscribe_label));
		menu.add(Menu.NONE, LOGGING_MENU, Menu.NONE, getResources().getString(R.string.logging_label));
		return true;
	}
	
	@Override 
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = new Intent();
		switch (item.getItemId()) {
		case PREFERENCES_MENU:
			intent.setAction(CorePreferences.LAUNCH);
			this.startActivity(intent);
			return true;
		case DELIVERY_STATUS_MENU:
			intent.setAction(DeliveryStatus.LAUNCH);
			this.startActivity(intent);
			return true;
		case SUBSCRIPTION_MENU:
			intent.setAction(SubscriptionStatus.LAUNCH);
			this.startActivity(intent);
			return true;
		case SUBSCRIBE_MENU:
			intent.setAction(Subscribe.LAUNCH);
			this.startActivity(intent);
			return true;
		case LOGGING_MENU:
			intent.setAction(LoggingPreferences.LAUNCH);
			this.startActivity(intent);
			return true;
		}
		return false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		this.unregisterReceiver(this.wifiReceiver);
	}
	
	@Override
	public void onClick(View view) {
		Editor editor = prefs.edit();
		if (view.equals(this.cbWifi)) {
			editor.putBoolean(AmmoPrefKeys.WIFI_SHOULD_USE_PREF_KEY, cbWifi.isChecked());
		} else if (view.equals(this.cbPhysicalLink)) {
			// TODO: Need a way to disable physical link service.
			editor.putBoolean(AmmoPrefKeys.PHYSICAL_LINK_SHOULD_USE_PREF_KEY, cbPhysicalLink.isChecked());
		} else if (view.equals(this.btnConnect)) {
			// Tell the network service to disconnect and reconnect.
			Intent intent = new Intent(INetworkBinder.ACTION_RECONNECT);
			this.sendBroadcast(intent);
		}
		editor.commit();
		setWifiStatus();
		// updateConnectionStatus(prefs);
	}
	
		

	/**
	 * If the key relates to our physical link, update the UI.
	 * Note: This method is called on the thread that changed the preferences.
	 * To update the UI, explicity call the main thread.
	 */
	@Override
	public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
		if (key.equals(AmmoPrefKeys.PHYSICAL_LINK_PREF_STATUS_KEY) || key.equals(AmmoPrefKeys.WIFI_PREF_STATUS_KEY)) {
			this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateConnectionStatus(prefs);					
				}
			});
			return;
		} 
		if (key.equals(LoggingPreferences.PREF_LOG_LEVEL)) {
			logger.debug("attempting to disable logging");
			return;
		} 
	}
	
	// ===========================================================
	// UI Management
	// ===========================================================
	
	/**
	 * Tell our text views to 
	 * update since network status has changed.
	 */
	public void updateConnectionStatus(SharedPreferences prefs) {
		tvPhysicalLink.notifyNetworkStatusChanged(prefs, AmmoPrefKeys.PHYSICAL_LINK_PREF_STATUS_KEY);
		tvWifi.notifyNetworkStatusChanged(prefs, AmmoPrefKeys.WIFI_PREF_STATUS_KEY);
	}
	
	public void setViewReferences() {
		this.tvPhysicalLink = (NetworkStatusTextView)findViewById(R.id.main_activity_physical_link_status);
		this.tvWifi = (NetworkStatusTextView)findViewById(R.id.main_activity_wifi_status);
		this.cbPhysicalLink = (CheckBox)findViewById(R.id.main_activity_physical_link);
		this.cbWifi = (CheckBox)findViewById(R.id.main_activity_wifi);
		this.btnConnect = (Button)findViewById(R.id.main_activity_connect_button);
	}
	
	public void setOnClickListeners() {
		cbPhysicalLink.setOnClickListener(this);
		cbWifi.setOnClickListener(this);
		btnConnect.setOnClickListener(this);
	}
	
	/**
	 * Each time we start this activity, we need to update the status message
	 * for each connection since it may have changed since this activity was
	 * last loaded.
	 * 
	 * TODO: Clean this up.
	 */
	public void setWifiStatus() {
	    Thread t = new Thread() {
		    public void run() {
			WifiManager manager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
			WifiInfo info = manager.getConnectionInfo();
			logger.debug( "WifiInfo: " +  info.toString() );
			boolean wifiConn = (info != null && info.getSupplicantState() == SupplicantState.COMPLETED);
			Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
			if (!wifiConn && !cbWifi.isChecked()) {  // info.getSSID() == null
			    editor.putInt(AmmoPrefKeys.WIFI_PREF_STATUS_KEY, NetworkService.ConnectionStatus.NO_CONNECTION.ordinal());
			} else if (!wifiConn && cbWifi.isChecked()) { // info.getSSID() == null
			    editor.putInt(AmmoPrefKeys.WIFI_PREF_STATUS_KEY, NetworkService.ConnectionStatus.NOT_AVAILABLE.ordinal());
			} else if (wifiConn && !cbWifi.isChecked()) { // info.getSSID() != null
			    editor.putInt(AmmoPrefKeys.WIFI_PREF_STATUS_KEY, NetworkService.ConnectionStatus.AVAILABLE_NOT_CONNECTED.ordinal());
			} else if (wifiConn && cbWifi.isChecked()) { // info.getSSID() != null
			    editor.putInt(AmmoPrefKeys.WIFI_PREF_STATUS_KEY, NetworkService.ConnectionStatus.CONNECTED.ordinal());
			} 
		
			editor.commit();
		    }
		};
	    t.start();
	}
	
	// ===========================================================
	// Broadcast Receivers
	// ===========================================================
	public void registerReceivers() {
		this.wifiReceiver = new WifiReceiver();
		IntentFilter wifiFilter = new IntentFilter(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		this.registerReceiver(this.wifiReceiver, wifiFilter);
	}
	
	private class WifiReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
		    // updateConnectionStatus(prefs);
		    setWifiStatus();
		}
	}
	
	
}

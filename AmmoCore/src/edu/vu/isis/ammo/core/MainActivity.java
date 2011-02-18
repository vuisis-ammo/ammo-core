package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.core.network.INetworkBinder;
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
public class MainActivity extends Activity implements OnClickListener, OnSharedPreferenceChangeListener {

	public static final Logger logger = LoggerFactory.getLogger(MainActivity.class);
	public static final String PHYSICAL_LINK_PREF_KEY = "edu.vu.isis.ammo.core.physical_link";
	public static final String WIFI_LINK_PREF_KEY = "edu.vu.isis.ammo.core.wifi_link";
	private static final int PREFERENCES_MENU = Menu.NONE + 0;
	private static final int DELIVERY_STATUS_MENU = Menu.NONE + 1;
	private static final int SUBSCRIPTION_MENU = Menu.NONE + 2;
	private static final int SUBSCRIBE_MENU = Menu.NONE + 3;
	
	// ===========================================================
	// Fields
	// ===========================================================
	private TextView tvPhysicalLink, tvWifi;
	private ToggleButton tbPhysicalLink, tbWifi;
	private WifiReceiver wifiReceiver;
	
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
		this.updateConnectionStatus();
				
		String deviceId = UniqueIdentifiers.device(this);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(CorePreferences.PREF_DEVICE_ID, deviceId).commit();
		
		this.startService(ICoreService.CORE_APPLICATION_LAUNCH_SERVICE_INTENT);
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
		if (view.equals(this.tbWifi)) {
			logger.debug("physical link button clicked");
			if (this.tbWifi.isChecked()) {
				Intent intent = new Intent(INetworkBinder.ACTION_RECONNECT);
				this.sendBroadcast(intent);
			} else {
				Intent intent = new Intent(INetworkBinder.ACTION_DISCONNECT);
				this.sendBroadcast(intent);
			}
		} else if (view.equals(this.tbPhysicalLink)) {
			// TODO: Need a way to disable physical link service.
			if (this.tbPhysicalLink.isChecked()) {
				// Launch the ethernet service.
				Log.d("MainActivity","Launching ethernet service");
				Intent intent = new Intent("edu.vu.isis.ammo.core.ethtracksvc.LAUNCH");
				this.startService(intent);
			}
		}
	}
	
	/**
	 * If the key relates to our physical link, update the UI.
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		if (key.equals(MainActivity.PHYSICAL_LINK_PREF_KEY)) {
			// This method is called on the thread that changed the preferences.
			// This method needs to be called on the main thread so explicitly tell
			// it to do so.
			this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateConnectionStatus();					
				}
			});
		}
	}
	
	// ===========================================================
	// UI Management
	// ===========================================================
	
	/**
	 * Check the system prefs for the physical link flag. Update the UI appropriately. 
	 */
	public void updateConnectionStatus() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean isPhysicalLinkConnected = prefs.getBoolean(MainActivity.PHYSICAL_LINK_PREF_KEY, false);
		if (isPhysicalLinkConnected) {
			tvPhysicalLink.setText("Connected");
			tvPhysicalLink.setTextColor(Color.rgb(66, 209, 66));
			//tbPhysicalLink.setChecked(true);
		} else {
			tvPhysicalLink.setText("Not Connected");
			tvPhysicalLink.setTextColor(Color.RED);
			//tbPhysicalLink.setChecked(false);
		}
		
		this.updateWifiConnectionStatus();
	}
	
	private void updateWifiConnectionStatus() {
		WifiManager manager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		tbWifi.setChecked(manager.isWifiEnabled());
		WifiInfo info = manager.getConnectionInfo();
		String connectionText = "Not Connected";
		int textColor = Color.RED;
		if (info.getSSID() != null) {
			connectionText = "Connected to " + info.getSSID();
			textColor = Color.rgb(66, 209, 66);
		} 
		
		tvWifi.setText(connectionText);
		tvWifi.setTextColor(textColor);
	}
	
	public void setViewReferences() {
		//this.disconnectButton = (Button) findViewById(R.id.disconnect_button);
		//this.reconnectButton = (Button) findViewById(R.id.reconnect_button);
		this.tvPhysicalLink = (TextView)findViewById(R.id.main_activity_physical_link_status);
		this.tvWifi = (TextView)findViewById(R.id.main_activity_wifi_status);
		this.tbPhysicalLink = (ToggleButton)findViewById(R.id.main_activity_physical_link_toggle);
		this.tbWifi = (ToggleButton)findViewById(R.id.main_activity_wifi_toggle);
	}
	
	public void setOnClickListeners() {
		tbPhysicalLink.setOnClickListener(this);
		tbWifi.setOnClickListener(this);
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
			updateWifiConnectionStatus();
		}
	}
	
	
}

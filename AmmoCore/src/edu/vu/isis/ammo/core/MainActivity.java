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
import android.widget.TextView;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IPrefKeys;
import edu.vu.isis.ammo.api.AmmoPreference;
import edu.vu.isis.ammo.core.receiver.StartUpReceiver;
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
	private static final int SERVICE_MENU = Menu.NONE + 5;
		
	// ===========================================================
	// Fields
	// ===========================================================
	private NetworkStatusTextView tvPhysicalLink, tvWifi;
	private TextView tvConnectionStatus;
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
		logger.trace("::onCreate");
		
		setContentView(R.layout.main_activity);
		this.setViewReferences();
		this.setOnClickListeners();
		this.initializeCheckboxes();
		this.registerReceivers();
		
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String deviceId = UniqueIdentifiers.device(this);
		prefs.registerOnSharedPreferenceChangeListener(this);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(INetPrefKeys.PREF_DEVICE_ID, deviceId).commit();

		AmmoPreference ap = AmmoPreference.getInstance(getApplicationContext());
		Intent i = new Intent(IPrefKeys.AMMO_PREF_UPDATE);
		i.putExtra("operatorId", prefs.getString(IPrefKeys.PREF_OPERATOR_ID, "foo"));
		this.sendBroadcast(i);
		
		Intent intent = new Intent("edu.vu.isis.ammo.core.CorePreferenceService.LAUNCH");
		this.startService(intent);
		
		intent.setAction(StartUpReceiver.RESET);
		this.sendBroadcast(intent);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		logger.trace("::onStart");
		
		setWifiStatus();
		this.updateConnectionStatus(prefs);
	}

	// Create a menu which contains a preferences button.
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) 
	{
			super.onCreateContextMenu(menu, v, menuInfo);
			logger.trace("::onCreateContextMenu");
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		logger.trace("::onCreateOptionsMenu");
		
		menu.add(Menu.NONE, PREFERENCES_MENU, Menu.NONE, getResources().getString(R.string.pref_label));
		menu.add(Menu.NONE, DELIVERY_STATUS_MENU, Menu.NONE, getResources().getString(R.string.delivery_status_label));
		menu.add(Menu.NONE, SUBSCRIPTION_MENU, Menu.NONE, getResources().getString(R.string.subscription_label));
		menu.add(Menu.NONE, SUBSCRIBE_MENU, Menu.NONE, getResources().getString(R.string.subscribe_label));
		menu.add(Menu.NONE, LOGGING_MENU, Menu.NONE, getResources().getString(R.string.logging_label));
		menu.add(Menu.NONE, SERVICE_MENU, Menu.NONE, getResources().getString(R.string.service_label));
		return true;
	}
	
	@Override 
	public boolean onPrepareOptionsMenu(Menu menu) {
		logger.trace("::onPrepareOptionsMenu");
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		logger.trace("::onOptionsItemSelected");
		
		Intent intent = new Intent();
		switch (item.getItemId()) {
		case PREFERENCES_MENU:
			intent.setAction(CorePreferenceActivity.LAUNCH);
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
		case SERVICE_MENU:
			intent.setAction(StartUpReceiver.RESET);
			this.sendBroadcast(intent);
			return true;
		}
		return false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		logger.trace("::onDestroy");
		this.unregisterReceiver(this.wifiReceiver);
	}
	
	@Override
	public void onClick(View view) {
		logger.trace("::onClick");
		
		Editor editor = prefs.edit();
		if (view.equals(this.cbWifi)) {
			editor.putBoolean(INetPrefKeys.WIFI_PREF_SHOULD_USE, this.cbWifi.isChecked());
		} else if (view.equals(this.cbPhysicalLink)) {
			// TODO: Need a way to disable physical link service.
			editor.putBoolean(INetPrefKeys.PHYSICAL_LINK_PREF_SHOULD_USE, this.cbPhysicalLink.isChecked());
		} else if (view.equals(this.btnConnect)) {
			// Tell the network service to disconnect and reconnect.
			editor.putBoolean(INetPrefKeys.NET_CONN_PREF_SHOULD_USE, this.btnConnect.isPressed());
		}
		editor.commit();
		setWifiStatus();
	}
	
	/**
	 * If the key relates to our physical link, update the UI.
	 * Note: This method is called on the thread that changed the preferences.
	 * To update the UI, explicitly call the main thread.
	 */
	@Override
	public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
		logger.trace("::onSharedPreferenceChanged");
		if (key.endsWith(INetPrefKeys.PREF_DEVICE_ID)) {
			return;
		}
		if (key.startsWith(INetPrefKeys.PHYSICAL_LINK_PREF)) {
			updateConnectionStatusThread(prefs);
			return;
		}
		if (key.startsWith(INetPrefKeys.WIFI_PREF)) {
			updateConnectionStatusThread(prefs);
			return;
		} 
		if (key.startsWith(INetPrefKeys.NET_CONN_PREF)) {
			updateConnectionStatusThread(prefs);
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
	public void updateConnectionStatusThread(final SharedPreferences prefs) {
		logger.trace("::updateConnectionStatusThread");
		
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateConnectionStatus(prefs);
			}
		});
		return;
	}
	
	public void updateConnectionStatus(SharedPreferences prefs) {
		logger.trace("::updateConnectionStatus");
		
		tvPhysicalLink.notifyNetworkStatusChanged(prefs, INetPrefKeys.PHYSICAL_LINK_PREF);
		tvWifi.notifyNetworkStatusChanged(prefs, INetPrefKeys.WIFI_PREF);
		
		boolean isConnected = prefs.getBoolean(INetPrefKeys.NET_CONN_PREF_IS_ACTIVE, false);
		if (isConnected) {
			tvConnectionStatus.setText("Gateway connected");
		} else {
			tvConnectionStatus.setText("Gateway not connected");
		}
	}
	
	public void setViewReferences() {
		logger.trace("::setViewReferences");
		
		this.tvPhysicalLink = (NetworkStatusTextView)findViewById(R.id.main_activity_physical_link_status);
		this.tvWifi = (NetworkStatusTextView)findViewById(R.id.main_activity_wifi_status);
		this.tvConnectionStatus = (TextView)findViewById(R.id.main_activity_connection_status);
		this.cbPhysicalLink = (CheckBox)findViewById(R.id.main_activity_physical_link);
		this.cbWifi = (CheckBox)findViewById(R.id.main_activity_wifi);
		this.btnConnect = (Button)findViewById(R.id.main_activity_connect_button);
	}
	
	public void initializeCheckboxes() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean physLinkEnabled = prefs.getBoolean(INetPrefKeys.PHYSICAL_LINK_PREF_SHOULD_USE, false);
		boolean wifiEnabled = prefs.getBoolean(INetPrefKeys.WIFI_PREF_SHOULD_USE, false);
		cbPhysicalLink.setChecked(physLinkEnabled);
		cbWifi.setChecked(wifiEnabled);
	}
	
	public void setOnClickListeners() {
		logger.trace("::setOnClickListeners");
		
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
		logger.trace("::setWifiStatus");
		
	    Thread wifiThread = new Thread() {
		    public void run() {
		    	logger.trace("WifiThread::run");
		    	
				WifiManager manager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
				WifiInfo info = manager.getConnectionInfo();
				logger.debug( "WifiInfo: " +  info.toString() );
				boolean wifiConn = (info != null && info.getSupplicantState() == SupplicantState.COMPLETED);
				Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
				// editor.putBoolean(INetPrefKeys.WIFI_PREF_IS_CONNECTED, wifiConn);
				editor.putBoolean(INetPrefKeys.WIFI_PREF_IS_AVAILABLE, wifiConn);
				// editor.putBoolean(INetPrefKeys.WIFI_PREF_SHOULD_USE, cbWifi.isChecked());		
				editor.commit();
		    }
		};
		wifiThread.start();
	}
	
	// ===========================================================
	// Broadcast Receivers
	// ===========================================================
	public void registerReceivers() {
		logger.trace("::registerReceivers");
		
		this.wifiReceiver = new WifiReceiver();
		IntentFilter wifiFilter = new IntentFilter(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		this.registerReceiver(this.wifiReceiver, wifiFilter);
	}
	
	private class WifiReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			logger.trace("WifiReceiver::onReceive");
		    // updateConnectionStatus(prefs);
		    setWifiStatus();
		}
	}
	
	
}

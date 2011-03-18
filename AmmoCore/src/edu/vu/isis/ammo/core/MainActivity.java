package edu.vu.isis.ammo.core;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.AmmoPreferenceChangedReceiver;
import edu.vu.isis.ammo.AmmoPreferenceReadOnlyAccess;
import edu.vu.isis.ammo.IAmmoPreferenceChangedListener;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoPreference;
import edu.vu.isis.ammo.core.distributor.DistributorViewerSwitch;
import edu.vu.isis.ammo.core.provider.PreferenceSchema;
import edu.vu.isis.ammo.core.receiver.StartUpReceiver;

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
implements OnClickListener, IAmmoPreferenceChangedListener
{
	public static final Logger logger = LoggerFactory.getLogger(MainActivity.class);
	
	private static final int PREFERENCES_MENU = Menu.NONE + 0;
	private static final int DELIVERY_STATUS_MENU = Menu.NONE + 1;
	private static final int VIEW_TABLES_MENU = Menu.NONE + 3;
	private static final int LOGGING_MENU = Menu.NONE + 4;
	private static final int SERVICE_MENU = Menu.NONE + 5;
		
	// ===========================================================
	// Fields
	// ===========================================================
	private NetworkStatusTextView tvWired, tvWifi;
	private TextView tvConnectionStatus;
	private Button btnConnect;
	private CheckBox cbWired, cbWifi;
	private WifiReceiver wifiReceiver;
	private AmmoPreferenceChangedReceiver receiver;
	private AmmoPreference ap;

	private List<Gateway> model = new ArrayList<Gateway>();
	private GatewayAdapter adapter = null;
	
	/**
	 * @Cateogry Lifecycle
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.trace("::onCreate");
		this.setContentView(R.layout.main_activity);
		ap = AmmoPreference.getInstance(this);
		this.setViewReferences();
		this.setOnClickListeners();
		this.initializeCheckboxes();
		this.registerReceivers();
		
		Intent intent = new Intent("edu.vu.isis.ammo.core.CorePreferenceService.LAUNCH");
		this.startService(intent);
		
		intent.setAction(StartUpReceiver.RESET);
		this.sendBroadcast(intent);
		
		ListView list = (ListView)this.findViewById(R.id.gateway_list);
		this.adapter = new GatewayAdapter (this, model);
		list.setAdapter(adapter);
	}
	
	public void setGateway(Gateway gw) {
		adapter.add(gw);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		logger.trace("::onStart");
		this.initializeAmmoPreferenceChangedReceiver();
		setWifiStatus();
		this.updateConnectionStatus(ap);
		this.setGateway(Gateway.getInstance(this));
	}
	
	@Override
	public void onStop() {
		this.uninitializeAmmoPreferenceChangedReceiver();
		super.onStop();
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
		menu.add(Menu.NONE, VIEW_TABLES_MENU, Menu.NONE, getResources().getString(R.string.view_tables_label));
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
		case VIEW_TABLES_MENU:
			intent.setAction(DistributorViewerSwitch.LAUNCH);
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
		
		try {
			if (view.equals(this.cbWifi)) {
				ap.putBoolean(INetPrefKeys.WIFI_PREF_SHOULD_USE, this.cbWifi.isChecked());
			} else if (view.equals(this.cbWired)) {
				ap.putBoolean(INetPrefKeys.WIRED_PREF_SHOULD_USE, this.cbWired.isChecked());
			} else if (view.equals(this.btnConnect)) {
				// Tell the network service to disconnect and reconnect.
				ap.putBoolean(INetPrefKeys.NET_CONN_PREF_SHOULD_USE, this.btnConnect.isPressed());
			}
		} catch (AmmoPreferenceReadOnlyAccess ex) {
			ex.printStackTrace();
		}
		setWifiStatus();
	}
	
	@Override
	public void onAmmoPreferenceChanged(Context context, Intent intent) {
		Log.d("::onAmmoPreferenceChanged", "ammo pref changed");
		// Get the key and value of the preference changed.
		
		AmmoPreference ap = AmmoPreference.getInstance(this);
		String key = intent.getStringExtra(PreferenceSchema.AMMO_INTENT_KEY_PREF_CHANGED_KEY);
		if (key.endsWith(INetPrefKeys.CORE_DEVICE_ID)) {
			return;
		}
		if (key.startsWith(INetPrefKeys.WIRED_PREF)) {
			this.updateConnectionStatusThread(ap);
			return;
		}
		if (key.startsWith(INetPrefKeys.WIFI_PREF)) {
			this.updateConnectionStatusThread(ap);
			return;
		} 
		if (key.startsWith(INetPrefKeys.NET_CONN_PREF)) {
			this.updateConnectionStatusThread(ap);
			return;
		} 
		if (key.equals(LoggingPreferences.PREF_LOG_LEVEL)) {
			logger.debug("attempting to disable logging");
			return;
		} 
	}
	
	
	public void initializeAmmoPreferenceChangedReceiver() {
		receiver = new AmmoPreferenceChangedReceiver(this);
		this.registerReceiver(receiver, PreferenceSchema.AMMO_PREF_CHANGED_INTENT_FILTER);
	}
	
	public void uninitializeAmmoPreferenceChangedReceiver() {
		this.unregisterReceiver(receiver);
	}
	// ===========================================================
	// UI Management
	// ===========================================================
	
	/**
	 * Tell our text views to 
	 * update since network status has changed.
	 */
	public void updateConnectionStatusThread(final AmmoPreference ap) {
		logger.trace("::updateConnectionStatusThread");
		
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateConnectionStatus(ap);
			}
		});
		return;
	}
	
	public void updateConnectionStatus(AmmoPreference ap) {
		logger.trace("::updateConnectionStatus");
		
		//tvWired.notifyNetworkStatusChanged(ap, INetPrefKeys.WIRED_PREF);
		//tvWifi.notifyNetworkStatusChanged(ap, INetPrefKeys.WIFI_PREF);
		
		boolean isConnected = ap.getBoolean(INetPrefKeys.NET_CONN_PREF_IS_ACTIVE, false);
		if (isConnected) {
			//tvConnectionStatus.setText("Gateway connected");
		} else {
			//tvConnectionStatus.setText("Gateway not connected");
		}
	}
	
	public void setViewReferences() {
		logger.trace("::setViewReferences");
		
		//this.tvWired = (NetworkStatusTextView)findViewById(R.id.main_activity_wired_status);
		//this.tvWifi = (NetworkStatusTextView)findViewById(R.id.main_activity_wifi_status);
		this.tvConnectionStatus = (TextView)findViewById(R.id.gateway_connection_status);
		//this.cbWired = (CheckBox)findViewById(R.id.main_activity_wired);
		//this.cbWifi = (CheckBox)findViewById(R.id.main_activity_wifi);
		// this.btnConnect = (Button)findViewById(R.id.gateway_connect_button);
	}
	
	public void initializeCheckboxes() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		//boolean physLinkEnabled = prefs.getBoolean(INetPrefKeys.WIRED_PREF_SHOULD_USE, true);
		//boolean wifiEnabled = prefs.getBoolean(INetPrefKeys.WIFI_PREF_SHOULD_USE, true);
		//cbWired.setChecked(physLinkEnabled);
		//cbWifi.setChecked(wifiEnabled);
	}
	
	public void setOnClickListeners() {
		logger.trace("::setOnClickListeners");
		
		//cbWired.setOnClickListener(this);
		//cbWifi.setOnClickListener(this);
		//btnConnect.setOnClickListener(this);
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
	
	private class GatewayAdapter extends ArrayAdapter<Gateway> 
	implements OnClickListener, OnFocusChangeListener, OnTouchListener, 
		OnNameChangeListener, OnStatusChangeListener
	{
		GatewayAdapter(MainActivity parent, List<Gateway> model) {
			super(parent,
					android.R.layout.simple_list_item_1,
					model);
		}
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.gateway_item, null);
				row.setOnClickListener(this);
				row.setOnFocusChangeListener(this);
				row.setOnTouchListener(this);
			}
			Gateway gw = model.get(position);
			((TextView)row.findViewById(R.id.gateway_name)).setText(gw.getName());
			((TextView)row.findViewById(R.id.gateway_formal)).setText(gw.getFormal());
			
			ToggleButton icon = (ToggleButton)row.findViewById(R.id.gateway_status);
			// set button icon
			icon.setChecked(gw.isEnabled());
			gw.setOnStatusChangeListener(this, parent);
			
			return row;
		}
		@Override
		public void onClick(View item) {
			//item.setBackgroundColor(Color.GREEN);
		}
		@Override
		public void onFocusChange(View item, boolean hasFocus) {
			if (hasFocus) {
			   item.setBackgroundColor(Color.RED);
			} else {
			   item.setBackgroundColor(Color.TRANSPARENT);
			}
		}
		
		 @Override
         public boolean onTouch(View view, MotionEvent event) {
             // Only perform this transform on image buttons for now.
			 if (view.getClass() != RelativeLayout.class) return false;

			 RelativeLayout item = (RelativeLayout) view;
			 int action = event.getAction();

			 switch (action) {
			 case MotionEvent.ACTION_DOWN:
			 case MotionEvent.ACTION_MOVE:
				 item.setBackgroundResource(R.drawable.select_gradient);
				 //item.setBackgroundColor(Color.GREEN);
				 break;

			 default:
				 item.setBackgroundColor(Color.TRANSPARENT);
			 }

			 return false;
         }
		@Override
		public boolean onStatusChange(View item, int status) {
			View row = item;
			ToggleButton icon = (ToggleButton)row.findViewById(R.id.gateway_status);
			switch (status) {
			case Gateway.ACTIVE: 
				icon.setBackgroundColor(R.color.active); 
				break;
			case Gateway.INACTIVE: 
				icon.setBackgroundColor(R.color.inactive); 
				break;
			case Gateway.DISABLED: 
				icon.setBackgroundColor(R.color.disabled); 
				break;
			default:
				icon.setBackgroundColor(R.color.inactive); 
				return false;
			}
			return true;
		}
		
		@Override
		public boolean onNameChange(View item, String name) {
			((TextView)item.findViewById(R.id.gateway_name)).setText(name);
			return false;
		}
		@Override
		public boolean onFormalChange(View item, String formal) {
			((TextView)item.findViewById(R.id.gateway_formal)).setText(formal);
			return false;
		}
	}
	
}

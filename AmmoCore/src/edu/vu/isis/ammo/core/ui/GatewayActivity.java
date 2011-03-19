package edu.vu.isis.ammo.core.ui;

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
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.OnStatusChangeListener;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.R.color;
import edu.vu.isis.ammo.core.R.drawable;
import edu.vu.isis.ammo.core.R.id;
import edu.vu.isis.ammo.core.R.layout;
import edu.vu.isis.ammo.core.R.string;
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
public class GatewayActivity extends Activity
{
	public static final Logger logger = LoggerFactory.getLogger(GatewayActivity.class);
	
	private static final int PREFERENCES_MENU = Menu.NONE + 0;
	private static final int DELIVERY_STATUS_MENU = Menu.NONE + 1;
	private static final int VIEW_TABLES_MENU = Menu.NONE + 3;
	private static final int LOGGING_MENU = Menu.NONE + 4;
	private static final int SERVICE_MENU = Menu.NONE + 5;
		
	// ===========================================================
	// Fields
	// ===========================================================
	
	private final List<Gateway> model = new ArrayList<Gateway>();
	private GatewayAdapter adapter = null;
	
	
	// ===========================================================
	// Views
	// ===========================================================
	
	private ListView list;
	
	/**
	 * @Cateogry Lifecycle
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.trace("::onCreate");
		this.setContentView(R.layout.gateway_activity);
		
		// set view references
		this.list = (ListView)this.findViewById(R.id.gateway_list);
		this.adapter = new GatewayAdapter(this, model);
		list.setAdapter(adapter);
		
		// set listeners
		
		// register receivers
		
		// start services
		Intent intent = new Intent("edu.vu.isis.ammo.core.CorePreferenceService.LAUNCH");
		this.startService(intent);
		
		// let others know we are running
		intent.setAction(StartUpReceiver.RESET);
		this.sendBroadcast(intent);
		
		this.setGateway(Gateway.getInstance(this));
	}
	
	public void setGateway(Gateway gw) {
		adapter.add(gw);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		logger.trace("::onStart");
	}
	
	@Override
	public void onStop() {
		super.onStop();
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
			intent.setClass(this, CorePreferenceActivity.class);
			this.startActivity(intent);
			return true;
		case DELIVERY_STATUS_MENU:
			intent.setClass(this, DeliveryStatus.class);
			this.startActivity(intent);
			return true;
		case VIEW_TABLES_MENU:
			intent.setClass(this, DistributorViewerSwitch.class);
			this.startActivity(intent);
			return true;
		case LOGGING_MENU:
			intent.setClass(this, LoggingPreferences.class);
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
	}
	
	public void onSettingsButtonClick(View view) {
		logger.trace("::onClick");

		Intent settingIntent = new Intent();
		settingIntent.setClass(this, CorePreferenceActivity.class);
		this.startActivity(settingIntent);
	}
	
	public void onNetlinkButtonClick(View view) {
		logger.trace("::onClick");

		Intent settingIntent = new Intent();
		settingIntent.setClass(this, NetlinkActivity.class);
		this.startActivity(settingIntent);
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

	// ===========================================================
	// Inner Classes
	// ===========================================================
	
	private class GatewayAdapter extends ArrayAdapter<Gateway> 
	implements OnTouchListener, 
		OnNameChangeListener, OnStatusChangeListener
	{
		private final GatewayActivity parent;
		GatewayAdapter(GatewayActivity parent, List<Gateway> model) {
			super(parent,
					android.R.layout.simple_list_item_1,
					model);
			this.parent = parent;
		}
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.gateway_item, null);
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
         public boolean onTouch(View view, MotionEvent event) {
             // Only perform this transform on image buttons for now.
			 if (view.getClass() != RelativeLayout.class) return false;

			 RelativeLayout item = (RelativeLayout) view;
			 int action = event.getAction();

			 switch (action) {
			 case MotionEvent.ACTION_DOWN:
			 case MotionEvent.ACTION_MOVE:
				 item.setBackgroundResource(R.drawable.select_gradient);
				 logger.trace("::onClick");

					Intent gatewayIntent = new Intent();
					gatewayIntent.setClass(this.parent, GatewayDetailActivity.class);
					this.parent.startActivity(gatewayIntent);
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

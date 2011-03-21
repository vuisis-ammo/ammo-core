package edu.vu.isis.ammo.core.ui;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByName;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorViewerSwitch;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.network.INetChannel;
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
public class GatewayActivity extends ActivityEx implements OnStatusChangeListenerByName
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
	
	private List<Gateway> model = new ArrayList<Gateway>();
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
		this.adapter = new GatewayAdapter(this, this.model);
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

        //reset all rows
        for (int ix=0; ix < this.list.getChildCount(); ix++) 
        {
        	View row = this.list.getChildAt(ix);
            row.setBackgroundColor(Color.TRANSPARENT);        
        }
        
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
	

	// ===========================================================
	// UI Management
	// ===========================================================
	
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
	
	public void onGatewayElectionToggle(View view) {
        int position = this.list.getPositionForView(view);
        Gateway gw = (Gateway) this.adapter.getItem(position);
        
        // get the button's row
        RelativeLayout row = (RelativeLayout)view.getParent();
        ToggleButton button = (ToggleButton)view;
        
        if (button.isChecked()) gw.enable(); else gw.enable();
       
     
        row.refreshDrawableState();       
    }

	// ===========================================================
	// Inner Classes
	// ===========================================================
	
	private class GatewayAdapter extends ArrayAdapter<Gateway> 
	implements OnTouchListener, OnNameChangeListener, 
	     OnStatusChangeListenerByView, OnStatusChangeListenerByName
	{
		private final GatewayActivity parent;
		private final Resources res;
		
		GatewayAdapter(GatewayActivity parent, List<Gateway> model) {
			super(parent,
					android.R.layout.simple_list_item_1,
					model);
			this.parent = parent;
			this.res = this.parent.getResources();
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
			gw.setOnNameChangeListener(this, parent);
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
		public boolean onStatusChange(View item, int[] status) {
			if (status == null) return false;
			if (status.length < 1) return false;
			
			View row = item;
			ToggleButton icon = (ToggleButton)row.findViewById(R.id.gateway_status);
			TextView text = (TextView)row.findViewById(R.id.gateway_status_text);
			int color;
			
			switch (status[0]) {
			case INetChannel.CONNECTED:
				color = this.res.getColor(R.color.status_active);
				icon.setTextColor(color); 
				text.setText(R.string.status_active);
				text.setTextColor(color);
				break;
			case INetChannel.DISCONNECTED: 
				color = this.res.getColor(R.color.status_inactive);
				icon.setTextColor(color);
				text.setText(R.string.status_inactive);
				text.setTextColor(color);
				break;
			default:
				color = this.res.getColor(R.color.status_disabled);
				icon.setTextColor(color); 
				text.setText(R.string.status_disabled);
				text.setTextColor(color);
				return false;
			}
			item.refreshDrawableState(); 
			return true;
		}
		
		@Override
		public boolean onNameChange(View item, String name) {
			((TextView)item.findViewById(R.id.gateway_name)).setText(name);
			item.refreshDrawableState(); 
			return false;
		}
		@Override
		public boolean onFormalChange(View item, String formal) {
			((TextView)item.findViewById(R.id.gateway_formal)).setText(formal);
			item.refreshDrawableState(); 
			return false;
		}
		@Override
		public boolean onStatusChange(String itemName, int[] status) {
			for (int ix=0; ix < this.parent.model.size(); ix++) {
				Gateway item = this.parent.model.get(ix);
				if (! item.getName().equalsIgnoreCase(itemName)) continue;
				item.onStatusChanged(status);
				return true;
			}
			return false;
		}
	}

	@Override
	public boolean onStatusChange(String item, int[] status) {
		this.adapter.onStatusChange(item, status);
		return true;
	}
	
}

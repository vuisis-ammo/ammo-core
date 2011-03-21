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
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByName;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorViewerSwitch;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.model.WifiNetlink;
import edu.vu.isis.ammo.core.model.WiredNetlink;
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
public class AmmoActivity extends TabActivityEx implements OnStatusChangeListenerByName
{
	public static final Logger logger = LoggerFactory.getLogger(AmmoActivity.class);
	
	private static final int PREFERENCES_MENU = Menu.NONE + 0;
	private static final int DELIVERY_STATUS_MENU = Menu.NONE + 1;
	private static final int VIEW_TABLES_MENU = Menu.NONE + 3;
	private static final int LOGGING_MENU = Menu.NONE + 4;
	private static final int SERVICE_MENU = Menu.NONE + 5;
		
	// ===========================================================
	// Fields
	// ===========================================================
	
	private List<Gateway> gatewayModel = new ArrayList<Gateway>();
	private GatewayAdapter gatewayAdapter = null;
	
	private List<Netlink> netlinkModel = new ArrayList<Netlink>();
	private NetlinkAdapter netlinkAdapter = null;
	
	// ===========================================================
	// Views
	// ===========================================================
	
	private ListView gatewayList;
	private ListView netlinkList;
	
	
	/**
	 * @Cateogry Lifecycle
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.trace("::onCreate");
		this.setContentView(R.layout.ammo_activity);
		
		// set gateway view references
		this.gatewayList = (ListView)this.findViewById(R.id.gateway_list);
		this.gatewayAdapter = new GatewayAdapter(this, this.gatewayModel);
		gatewayList.setAdapter(gatewayAdapter);
		
		this.setGateway(Gateway.getInstance(this));
		
		// set netlink view references
		this.netlinkList = (ListView)this.findViewById(R.id.netlink_list);
		this.netlinkAdapter = new NetlinkAdapter(this, this.netlinkModel);
		netlinkList.setAdapter(netlinkAdapter);
		
		this.setNetlink(WifiNetlink.getInstance(this));
		this.setNetlink(WiredNetlink.getInstance(this));
		// this.setNetlink(JournalNetlink.getInstance(this));
		
		// set listeners
		
		// register receivers
		
		// start services
		Intent intent = new Intent("edu.vu.isis.ammo.core.CorePreferenceService.LAUNCH");
		this.startService(intent);
		
		
		// let others know we are running
		intent.setAction(StartUpReceiver.RESET);
		this.sendBroadcast(intent);
		
		
		
		// setup tabs
		TabHost.TabSpec spec;
		Resources res = this.getResources();
		
		spec = getTabHost().newTabSpec("tag1");
		spec.setContent(R.id.gateway_layout);
		spec.setIndicator("Gateway", res.getDrawable(R.drawable.gateway_32));	
		getTabHost().addTab(spec);
		
		spec = getTabHost().newTabSpec("tag2");
		spec.setContent(R.id.netlink_layout);
		spec.setIndicator("Netlink", res.getDrawable(R.drawable.netlink_32));
		getTabHost().addTab(spec);
		
//		spec = getTabHost().newTabSpec("tag3");
//		spec.setContent(R.id.preferences_layout);
//		spec.setIndicator("Preferences", res.getDrawable(R.drawable.cog_32));
//		getTabHost().addTab(spec);
		
		getTabHost().setCurrentTab(0);
	}
	
	public void setGateway(Gateway gw) {
		gatewayAdapter.add(gw);
	}
	
	public void setNetlink(Netlink nl) {
		netlinkAdapter.add(nl);
	}
	
	
	@Override
	public void onStart() {
		super.onStart();
		logger.trace("::onStart");

        //reset all rows
        for (int ix=0; ix < this.gatewayList.getChildCount(); ix++) 
        {
        	View row = this.gatewayList.getChildAt(ix);
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
        int position = this.gatewayList.getPositionForView(view);
        Gateway gw = (Gateway) this.gatewayAdapter.getItem(position);
        
        // get the button's row
        RelativeLayout row = (RelativeLayout)view.getParent();
        ToggleButton button = (ToggleButton)view;
        
        if (button.isChecked()) gw.enable(); else gw.enable();
       
     
        row.refreshDrawableState();       
    }

	// ===========================================================
	// Inner Classes
	// ===========================================================
	
	@Override
	public boolean onStatusChange(String item, int[] status) {
		this.gatewayAdapter.onStatusChange(item, status);
		return true;
	}
	
}

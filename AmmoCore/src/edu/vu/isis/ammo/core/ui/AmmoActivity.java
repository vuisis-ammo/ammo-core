package edu.vu.isis.ammo.core.ui;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByName;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.ui.DistributorTabActivity;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.model.PhoneNetlink;
import edu.vu.isis.ammo.core.model.WifiNetlink;
import edu.vu.isis.ammo.core.model.WiredNetlink;
import edu.vu.isis.ammo.core.receiver.StartUpReceiver;
import edu.vu.isis.ammo.core.ui.util.TabActivityEx;

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
	public static final Logger log_status = LoggerFactory.getLogger("scenario.network.status");
	
	private static final int VIEW_TABLES_MENU = Menu.NONE + 0;
	private static final int LOGGING_MENU = Menu.NONE + 1;
	private static final int PREFERENCES_MENU = Menu.NONE + 2;
	private static final int ABOUT_MENU = Menu.NONE + 3;
		
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
		this.setNetlink(PhoneNetlink.getInstance(this));
		// this.setNetlink(JournalNetlink.getInstance(this));
		
		Intent intent = new Intent();
		
		// let others know we are running
		intent.setAction(StartUpReceiver.RESET);
		this.sendBroadcast(intent);
		
		
		// setup tabs
		Resources res = getResources(); // Resource object to get Drawables
	    TabHost tabHost = getTabHost();  // The activity TabHost
	    TabHost.TabSpec spec;  // Reusable TabSpec for each tab
		
		spec = tabHost.newTabSpec("gateway");	
		spec.setIndicator("Gateway", res.getDrawable(R.drawable.gateway_tab));
		spec.setContent(R.id.gateway_layout);
		getTabHost().addTab(spec);
		
		spec = tabHost.newTabSpec("netlink");
		spec.setIndicator("Link Status", res.getDrawable(R.drawable.netlink_32));
		spec.setContent(R.id.netlink_layout);
		getTabHost().addTab(spec);
		
		intent = new Intent().setClass(this, CorePreferenceActivity.class);
		spec = tabHost.newTabSpec("settings");
		spec.setIndicator("Preferences", res.getDrawable(R.drawable.cog_32));
		spec.setContent(intent);
		tabHost.addTab(spec);
		
		tabHost.setCurrentTab(0);
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
		for (Netlink nl : this.netlinkModel) {
			nl.teardown();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		logger.trace("::onCreateOptionsMenu");
		
		menu.add(Menu.NONE, VIEW_TABLES_MENU, Menu.NONE, getResources().getString(R.string.view_tables_label));
		menu.add(Menu.NONE, LOGGING_MENU, Menu.NONE, getResources().getString(R.string.logging_label));
		menu.add(Menu.NONE, PREFERENCES_MENU, Menu.NONE, getResources().getString(R.string.pref_label));
		menu.add(Menu.NONE, ABOUT_MENU, Menu.NONE, getResources().getString(R.string.about_label));
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
		case VIEW_TABLES_MENU:
			intent.setClass(this, DistributorTabActivity.class);
			this.startActivity(intent);
			return true;
		case LOGGING_MENU:
			intent.setClass(this, LoggingPreferences.class);
			this.startActivity(intent);
			return true;
		case ABOUT_MENU:
			intent.setClass(this, AboutActivity.class);
			this.startActivity(intent);
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
	public boolean onGatewayStatusChange(String name, int[] status) {
		Gateway item = this.gatewayAdapter.getItemByName(name);
		item.onStatusChange(status);
		this.gatewayAdapter.notifyDataSetChanged();
		return true;
	}
	
	@Override
	public boolean onNetlinkStatusChange(String type, int[] status) {
		Netlink item = this.netlinkAdapter.getItemByType(type);
		item.onStatusChange(status);
		this.netlinkAdapter.notifyDataSetChanged();
		return true;
	}

	/*
	 * Used to toggle the netlink view between simple and advanced.
	 */
	public void toggleMode(View v)
	{
		if(!netlinkAdvancedView)
		{	
			Button b = (Button)v;
			b.setText(R.string.simple_view);
			netlinkAdvancedView = true;
		}
		else
		{
			Button b = (Button)v;
			b.setText(R.string.advanced_view);
			netlinkAdvancedView = false;
		}
		this.netlinkAdapter.notifyDataSetChanged();
	}
	
}

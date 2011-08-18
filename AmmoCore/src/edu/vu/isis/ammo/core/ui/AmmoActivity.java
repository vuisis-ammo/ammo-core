//There are things in this file that are prepared for the Android 3.0 port
//They are tagged by ANDROID3.0
package edu.vu.isis.ammo.core.ui;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.ui.DistributorTabActivity;
import edu.vu.isis.ammo.core.model.Channel;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.Multicast;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.receiver.StartUpReceiver;
import edu.vu.isis.ammo.core.ui.util.TabActivityEx;

/**
 * The principle activity for the ammo core application.
 * Provides a means for...
 * ...changing the user preferences.
 * ...checking delivery status of various messages.
 * ...registering/unregistering content interest requests.
 *
 */
public class AmmoActivity extends TabActivityEx implements OnItemClickListener
{
    public static final Logger logger = LoggerFactory.getLogger( AmmoActivity.class );

    private static final int VIEW_TABLES_MENU = Menu.NONE + 0;
    private static final int CONFIG_MENU = Menu.NONE + 1;
    private static final int DEBUG_MENU = Menu.NONE + 2;
    private static final int ABOUT_MENU = Menu.NONE + 3;

    // ===========================================================
    // Fields
    // ===========================================================

    private List<Channel> channelModel = null;
    private ChannelAdapter channelAdapter = null;

    private List<Netlink> netlinkModel = null;
    private NetlinkAdapter netlinkAdapter = null;

    public boolean netlinkAdvancedView = false;

    @SuppressWarnings("unused")
	private Menu activity_menu;
    SharedPreferences prefs = null;

    // ===========================================================
    // Views
    // ===========================================================

    private ChannelListView channelList = null;
    private ListView netlinkList = null;

    private INetworkService networkServiceBinder;

    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.info("::onServiceConnected - Network Service");
            networkServiceBinder = ((NetworkService.MyBinder) service).getService();
            initializeGatewayAdapter();
            initializeNetlinkAdapter();
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.info("::onServiceDisconnected - Network Service");
            networkServiceBinder = null;
            // FIXME: what to do here if the NS goes away?
            // Change the model for the adapters to an empty list.
            // This situation should probably never happen, but we should
            // handle it properly anyway.
        }
    };

    private void initializeGatewayAdapter()
    {
        channelModel = networkServiceBinder.getGatewayList();

        // set gateway view references
        channelList = (ChannelListView)findViewById(R.id.gateway_list);
        channelAdapter = new ChannelAdapter(this, channelModel);
        channelList.setAdapter(channelAdapter);
        this.channelList.setOnItemClickListener(this);
        
        //reset all rows
        for (int ix=0; ix < channelList.getChildCount(); ix++)
        {
            View row = channelList.getChildAt(ix);
            row.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void initializeNetlinkAdapter()
    {
        netlinkModel = networkServiceBinder.getNetlinkList();

        // set netlink view references
        netlinkList = (ListView)findViewById(R.id.netlink_list);
        netlinkAdapter = new NetlinkAdapter(this, netlinkModel);
        netlinkList.setAdapter(netlinkAdapter);
    }


    /**
     * @Cateogry Lifecycle
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.trace("::onCreate");
        this.setContentView(R.layout.ammo_activity);

        // Get a reference to the NetworkService.
        Intent networkServiceIntent = new Intent(this, NetworkService.class);
        boolean result = bindService( networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE );
        if ( !result )
            logger.error( "AmmoActivity failed to bind to the NetworkService!" );

        Intent intent = new Intent();

        // let others know we are running
        intent.setAction(StartUpReceiver.RESET);
        this.sendBroadcast(intent);

        // setup tabs
        Resources res = getResources(); // Resource object to get Drawables
        TabHost tabHost = getTabHost();  // The activity TabHost
        TabHost.TabSpec spec;  // Reusable TabSpec for each tab

        spec = tabHost.newTabSpec("gateway");
        spec.setIndicator("Channels", res.getDrawable(R.drawable.gateway_tab));
        spec.setContent(R.id.gateway_layout);
        getTabHost().addTab(spec);

        spec = tabHost.newTabSpec("netlink");
        spec.setIndicator("Link Status", res.getDrawable(R.drawable.netlink_32));
        spec.setContent(R.id.netlink_layout);
        getTabHost().addTab(spec);

        intent = new Intent().setClass(this, CorePreferenceActivity.class);
        /*
        spec = tabHost.newTabSpec("settings");
        spec.setIndicator("Preferences", res.getDrawable(R.drawable.cog_32));
        spec.setContent(intent);
        tabHost.addTab(spec);
		*/
        tabHost.setCurrentTab(0);
        
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        this.netlinkAdvancedView = prefs.getBoolean("debug_mode", this.netlinkAdvancedView);
        
    }

    @Override
    public void onStart() {
        super.onStart();
        logger.trace("::onStart");

        //reset all rows
        if ( channelList != null )
        {
            for (int ix=0; ix < channelList.getChildCount(); ix++)
            {
                View row = channelList.getChildAt(ix);
                row.setBackgroundColor(Color.TRANSPARENT);
            }
            this.channelList.setOnItemClickListener(this);
        }
        
        mReceiver = new StatusReceiver();

        final IntentFilter statusFilter = new IntentFilter();
        statusFilter.addAction( AmmoIntents.AMMO_ACTION_GATEWAY_STATUS_CHANGE );
        statusFilter.addAction( AmmoIntents.AMMO_ACTION_NETLINK_STATUS_CHANGE );
        registerReceiver( mReceiver, statusFilter );

        if ( channelAdapter != null )
            channelAdapter.notifyDataSetChanged();
        if ( netlinkAdapter != null )
            netlinkAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            unregisterReceiver( mReceiver );
        } catch(IllegalArgumentException ex) {
            logger.trace("tearing down the gateway status object");
        }
    }

    private StatusReceiver mReceiver = null;

    private class StatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent iIntent) {
            final String action = iIntent.getAction();

            if ( action.equals( AmmoIntents.AMMO_ACTION_GATEWAY_STATUS_CHANGE ))
            {
                if ( channelAdapter != null )
                    channelAdapter.notifyDataSetChanged();
            }
            else if ( action.equals( AmmoIntents.AMMO_ACTION_NETLINK_STATUS_CHANGE ))
            {
                if ( netlinkAdapter != null )
                    netlinkAdapter.notifyDataSetChanged();
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        logger.trace("::onCreateOptionsMenu");
        menu.add(Menu.NONE, VIEW_TABLES_MENU, Menu.NONE, getResources().getString(R.string.view_tables_label));
        menu.add(Menu.NONE, CONFIG_MENU, Menu.NONE, getResources().getString(R.string.logging_label));
        menu.add(Menu.NONE, DEBUG_MENU, Menu.NONE, getResources().getString((!this.netlinkAdvancedView)?(R.string.debug_label):(R.string.user_label)));
        menu.add(Menu.NONE, ABOUT_MENU, Menu.NONE, getResources().getString(R.string.about_label));

        //ANDROID3.0
        //Store the reference to the menu so we can use it in the toggle
        //function
        //this.activity_menu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        logger.trace("::onPrepareOptionsMenu");

        menu.findItem(DEBUG_MENU).setTitle((!this.netlinkAdvancedView)?(R.string.debug_label):(R.string.user_label));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        logger.trace("::onOptionsItemSelected");
        Intent intent = new Intent();
        switch (item.getItemId()) {
        case DEBUG_MENU:
            toggleMode();
            return true;
        case VIEW_TABLES_MENU:
            intent.setClass(this, DistributorTabActivity.class);
            this.startActivity(intent);
            return true;
        case CONFIG_MENU:
            intent.setClass(this, GeneralPreferences.class);
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
        unbindService( networkServiceConnection );
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
        int position = this.channelList.getPositionForView(view);
        Gateway gw = (Gateway) this.channelAdapter.getItem(position);

        // get the button's row
        RelativeLayout row = (RelativeLayout)view.getParent();
        ToggleButton button = (ToggleButton)view;

        if (button.isChecked()) {
            gw.enable();
        }
        else {
            TextView t = (TextView)row.findViewById(R.id.gateway_status_text_one);
            t.setText("Disabling...");
            gw.disable();
        }

        row.refreshDrawableState();
    }
    
    public void onMulticastElectionToggle(View view)
    {
    	int position = this.channelList.getPositionForView(view);
    	Multicast  mc = (Multicast) this.channelAdapter.getItem(position);
    	
    	RelativeLayout row = (RelativeLayout)view.getParent();
    	ToggleButton button = (ToggleButton)view;
    	
    	if(button.isChecked())
    		mc.enable();
    	else
    		mc.disable();
    	
    	row.refreshDrawableState();
    }


    /*
     * Used to toggle the netlink view between simple and advanced.
     */
    public void toggleMode()
    {
        this.netlinkAdvancedView = !this.netlinkAdvancedView;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("debug_mode", this.netlinkAdvancedView).commit();
        this.netlinkAdapter.notifyDataSetChanged();
        this.channelAdapter.notifyDataSetChanged();

        //ANDROID3.0
        //Ideally, when this toggles, we need to
        //refresh the menu. This line will invalidate it so that
        //onPrepareOptionsMenu(...) will be called when the user
        //opens it again.
        //this.activity_menu.invalidateOptionsMenu();

    }

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		/*Channel c = this.channelAdapter.getItem(arg2);
		
        logger.trace("::onClick");
        Intent gatewayIntent = new Intent();
        if(c.getClass().equals(Gateway.class))
        {
        	gatewayIntent.putExtra("IPKEY", INetPrefKeys.CORE_IP_ADDR);
        	gatewayIntent.putExtra("PORTKEY", INetPrefKeys.CORE_IP_PORT);
        	gatewayIntent.putExtra("NetConnTimeoutKey", INetPrefKeys.CORE_SOCKET_TIMEOUT);
        	gatewayIntent.putExtra("ConIdleTimeoutKey", "AMMO_NET_CONN_FLAT_LINE_TIME");
        }
        else
        {
        	gatewayIntent.putExtra("IPKEY", INetPrefKeys.MULTICAST_IP_ADDRESS);
        	gatewayIntent.putExtra("PORTKEY", INetPrefKeys.MULTICAST_PORT);
        	gatewayIntent.putExtra("NetConnTimeoutKey", INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT);
        	gatewayIntent.putExtra("ConIdleTimeoutKey", INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT);
        }
        gatewayIntent.setClass(this, ChannelDetailActivity.class);
        this.startActivity(gatewayIntent);
		*/
		
	}

	public void editPreferences(View v)
	{
		ListView lv = (ListView) v.getParent().getParent();
		int position = lv.getPositionForView((View) v.getParent());
		Channel c = (Channel) lv.getAdapter().getItem(position);
        Intent gatewayIntent = new Intent();
        if(c.getClass().equals(Gateway.class))
        {
        	gatewayIntent.putExtra(ChannelDetailActivity.PREF_TYPE, ChannelDetailActivity.GATEWAY_PREF);
        }
        else
        {
        	gatewayIntent.putExtra(ChannelDetailActivity.PREF_TYPE, ChannelDetailActivity.MULTICAST_PREF);
        }       
        gatewayIntent.setClass(this, ChannelDetailActivity.class);
        this.startActivity(gatewayIntent);
	}
}

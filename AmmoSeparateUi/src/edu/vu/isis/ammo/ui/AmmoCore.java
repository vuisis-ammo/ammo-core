/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
 */
//There are things in this file that are prepared for the Android 3.0 port
//They are tagged by ANDROID3.0
package edu.vu.isis.ammo.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import edu.vanderbilt.isis.ammo.ui.R;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.distributor.ui.DistributorTabActivity;
import edu.vu.isis.ammo.core.model.ModelChannel;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.provider.ChannelProvider;
import edu.vu.isis.ammo.core.receiver.StartUpReceiver;
import edu.vu.isis.ammo.core.ui.AboutActivity;
import edu.vu.isis.ammo.ui.preferences.GatewayPreferences;
import edu.vu.isis.ammo.ui.preferences.MulticastPreferences;
import edu.vu.isis.ammo.ui.preferences.ReliableMulticastPreferences;
import edu.vu.isis.ammo.ui.preferences.SerialPreferences;
import edu.vu.isis.ammo.core.ui.util.ActivityEx;
import edu.vu.isis.logger.ui.LoggerEditor;

/**
 * The principle activity for ammo core. Provides a means for... ...changing the
 * user preferences. ...checking delivery status of various messages.
 * ...registering/unregistering content interest requests.
 * 
 */
public class AmmoCore extends ActivityEx {
    private int receiveCount = 0;
    
    static private final Map<String, ModelChannel> modelChannelMap;
    static {
        modelChannelMap = new HashMap<String, ModelChannel>();
    }
    
    //public edu.vu.isis.ammo.core.ui.AmmoCore _parent;
    public static final Logger logger = LoggerFactory.getLogger("ui");

    public static final String PREF_KEY = "prefkey";

    public static final int MULTICAST = 0;
    public static final int RELIABLE_MULTICAST = 1;
    public static final int SERIAL = 2;
    public static final int GATEWAY = 3;

    // ===========================================================
    // Fields
    // ===========================================================

    private ArrayList<AmmoListItem> channelModel = new ArrayList<AmmoListItem>();
    private ChannelAdapter2 channelAdapter = null;

    private List<Netlink> netlinkModel = null;
    private NetlinkAdapter netlinkAdapter = null;

    public static final boolean netlinkAdvancedView = true;

    @SuppressWarnings("unused")
    private Menu activity_menu;
    SharedPreferences prefs = null;

    // ===========================================================
    // Views
    // ===========================================================

    private TextView operatorTv;
    private ChannelListView channelList = null;
    private ListView netlinkList = null;

    private INetworkService networkServiceBinder;

    /*
     * FIXME : I believe that since the services were combined into a single
     * service this is no longer necessary. That is the calls should not be
     * deferred but performed directly here.
     */

    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        final private AmmoCore parent = AmmoCore.this; 

        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.trace("::onServiceConnected - Network Service");
            /*final AmmoService.DistributorServiceAidl binder = (AmmoService.DistributorServiceAidl) service;
            parent.networkServiceBinder = binder.getService();*/ //using content provider for this
            initializeGatewayAdapter();
            
            
            Toast.makeText(getApplicationContext(), "Service should be retrieved", Toast.LENGTH_SHORT).show();

            // Netlink Adapter is disabled for now (doesn't work)
//            initializeNetlinkAdapter();
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.trace("::onServiceDisconnected - Network Service");
            parent.networkServiceBinder = null;
        }
    };
    
    private Cursor currentCursor;
    
    /*private Thread makeCursorLooper(){
        return new Thread(new Runnable() {
            public void run() {
                while (true){
                    if ((receiveCount%5) == 0 && receiveCount != 0){
                    	Cursor tempcurs = getContentResolver().query(Uri.parse("content://edu.vu.isis.ammo.core.provider.channel/Channel"), null, null, null, null);
                    	if (tempcurs != null){
                    		currentCursor = tempcurs;
                    	} else {
                    		Toast.makeText(getApplicationContext(), "Attempt to re-query cursor at count " + receiveCount + " failed.", Toast.LENGTH_SHORT).show();
                    	}
                    }
                }
            }
        });
    }
    
    private Thread cursorLooper;*/
    
    AmmoListItem oneALI;
    AmmoListItem twoALI;
    AmmoListItem threeALI;
    AmmoListItem fourALI;
    
    
    private void initializeGatewayAdapter() {
        //channelModel = networkServiceBinder.getGatewayList(); //Using content provider for this
        //Contacts.People.CONTENT_URI
        
        //final Cursor channelCursor; /*= getContentResolver().query(
                    /*ContentUris.withAppendedId(edu.vu.isis.ammo.core.provider.ChannelProvider.CONTENT_URI, 0)
                     * This should be it. However, I'm hardcoding the value in for testing purposes. 
                    Uri.parse("content://edu.vu.isis.ammo.core.provider.channel/Channel"),
                    null,    // Which columns to return.
                    null,          // WHERE clause.
                    null,          // WHERE clause value substitution
                    null);   // Sort order.*/
        //ContentResolver x;
        //Toast.makeText(getApplicationContext(), "count is : " + managedCursor.getCount() + "", Toast.LENGTH_SHORT).show();
        //logger.error("count is : " + managedCursor.getCount() + "");
         
        /*AmmoListItem gateway*/ oneALI= new AmmoListItem("GatewayTest", "1","2","3");
        /*AmmoListItem serial*/ twoALI= new AmmoListItem("SerialTest", "1","2","3");
        /*AmmoListItem multicast*/ threeALI= new AmmoListItem("MulticastTest", "1","2","3");
        /*AmmoListItem rmulticast*/ fourALI= new AmmoListItem("ReliableMulticastTest", "1","2","3");
        //Cursor channelCursor;
        currentCursor = getContentResolver().query(Uri.parse("content://edu.vu.isis.ammo.core.provider.channel/Channel"), null, null, null, null);

        Handler handler = new Handler();
        
        final TextView temptest = (TextView) findViewById(R.id.ammo_test_tv11);
        //final AmmoCore parent = this;
        
        
        
        if (currentCursor != null){ //if that worked...
            currentCursor.moveToFirst();
            oneALI/*gateway*/ = new AmmoListItem(currentCursor.getString(0), 
                    currentCursor.getString(1), currentCursor.getString(2), currentCursor.getString(3));
            currentCursor.moveToNext();
            twoALI/*serial*/ = new AmmoListItem(currentCursor.getString(0), 
                    currentCursor.getString(1), currentCursor.getString(2), currentCursor.getString(3));
            currentCursor.moveToNext();
            threeALI/*multicast*/ = new AmmoListItem(currentCursor.getString(0), 
                    currentCursor.getString(1), currentCursor.getString(2), currentCursor.getString(3));
            currentCursor.moveToNext();
            fourALI/*rmulticast*/ = new AmmoListItem(currentCursor.getString(0), 
                    currentCursor.getString(1), currentCursor.getString(2), currentCursor.getString(3));
            
            channelList = (ChannelListView) findViewById(R.id.gateway_list);
            
            channelModel.clear();
            channelModel.add(oneALI);
            channelModel.add(twoALI);
            channelModel.add(threeALI);
            channelModel.add(fourALI);
            
            channelAdapter = new ChannelAdapter2(this, channelModel);
            channelAdapter.notifyDataSetChanged();
            channelList.setAdapter(channelAdapter);
        } else {
        	Toast.makeText(getApplicationContext(), "Cursor was null; cannot initialize UI", Toast.LENGTH_SHORT).show();
        }
        
        
        
        currentCursor.registerContentObserver(new ContentObserver(handler){
                
             @Override
             public void onChange(boolean selfChange) {
                 receiveCount++;
                 //Toast.makeText(getApplicationContext(), "Inside onChange1 method", Toast.LENGTH_SHORT).show();
                 //Toast.makeText(getApplicationContext(), "Received notification in activity "+ receiveCount, Toast.LENGTH_SHORT).show();
                 Cursor tempcursor = getContentResolver().query(Uri.parse("content://edu.vu.isis.ammo.core.provider.channel/Channel"), null, null, null, null);
                 tempcursor.moveToFirst();
                 /*channelModel.clear();
                 //////testing
                 channelAdapter = new ChannelAdapter2(parent, channelModel);
                 channelAdapter.notifyDataSetChanged();
                 channelList.setAdapter(channelAdapter);
                 channelList.invalidateViews();
                 //////end test
                 channelModel.add(new AmmoListItem(tempcursor.getString(0), 
                         tempcursor.getString(1), tempcursor.getString(2), tempcursor.getString(3)));
                 tempcursor.moveToNext();
                 channelModel.add(new AmmoListItem(tempcursor.getString(0), 
                         tempcursor.getString(1), tempcursor.getString(2), tempcursor.getString(3)));
                 tempcursor.moveToNext();
                 channelModel.add(new AmmoListItem(tempcursor.getString(0), 
                         tempcursor.getString(1), tempcursor.getString(2), tempcursor.getString(3)));
                 tempcursor.moveToNext();
                 channelModel.add(new AmmoListItem(tempcursor.getString(0), 
                         tempcursor.getString(1), tempcursor.getString(2), tempcursor.getString(3)));
                 channelList = (ChannelListView) findViewById(R.id.gateway_list);
                 channelAdapter = new ChannelAdapter2(parent, channelModel);
                 channelAdapter.notifyDataSetChanged();
                 channelList.setAdapter(channelAdapter);
                 channelList.invalidateViews();*/
                 
                 channelAdapter.setNotifyOnChange(true);
                 
                 oneALI.update(tempcursor.getString(1), 
                         tempcursor.getString(2), tempcursor.getString(3), tempcursor.getString(4));
                 tempcursor.moveToNext();
                 
                 twoALI.update(tempcursor.getString(1), 
                         tempcursor.getString(2), tempcursor.getString(3), tempcursor.getString(4));
                 tempcursor.moveToNext();
                 
                 threeALI.update(tempcursor.getString(0), 
                         tempcursor.getString(1), tempcursor.getString(2), tempcursor.getString(3));
                 tempcursor.moveToNext();
                 
                 fourALI.update(tempcursor.getString(0), 
                         tempcursor.getString(1), tempcursor.getString(2), tempcursor.getString(3));

                 channelAdapter.notifyDataSetChanged();
                 
                 tempcursor.close();
                 temptest.setText(/*"Stuff just happened!!"*/""+receiveCount);
             } 
        });
         
         /*
         cursorLooper = makeCursorLooper();
         cursorLooper.start();*/
         
         /*
         modelChannelMap.put(gateway.getName(),
                    Gateway.getInstance(getBaseContext(), null));
         modelChannelMap.put(multicastChannel.getName(),
                    Multicast.getInstance(getBaseContext(), null));
         modelChannelMap.put(reliableMulticast.getName(),
                    ReliableMulticast.getInstance(getBaseContext(), null));
         modelChannelMap.put(serialChannel.getName(),
                    Serial.getInstance(getBaseContext(), null));*/
         //channelModel = (List<ModelChannel>) modelChannelMap.values();
         //channelModel.add(/*gateway*/oneALI);
         //channelModel.add(/*multicast*/twoALI);
         //channelModel.add(/*rmulticast*/threeALI);
         //channelModel.add(/*serial*/fourALI);
         
        // set gateway view references
        /*
        channelList = (ChannelListView) findViewById(R.id.gateway_list);
        channelAdapter = new ChannelAdapter2(this, channelModel);
        channelAdapter.notifyDataSetChanged();
        channelList.setAdapter(channelAdapter);*/

        // reset all rows
        for (int ix = 0; ix < channelList.getChildCount(); ix++) {
            View row = channelList.getChildAt(ix);
            row.setBackgroundColor(Color.TRANSPARENT);
        }

        // add click listener to channelList
        channelList.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                Intent intent = new Intent();
                AmmoListItem selectedChannel = channelAdapter.getItem(position);
                if (selectedChannel.getName().contains("Gateway")) {
                    intent.setClass(AmmoCore.this, GatewayPreferences.class);
                } else if (selectedChannel.getName().contains("Serial")) {
                    intent.setClass(AmmoCore.this, SerialPreferences.class);
                } else if (selectedChannel.getName().contains("Reliable")) {
                    intent.setClass(AmmoCore.this, ReliableMulticastPreferences.class);
                } else if (selectedChannel.getName().contains("Multicast")) {
                    intent.setClass(AmmoCore.this, MulticastPreferences.class);
                } else {
                    Toast.makeText(AmmoCore.this, "Did not recognize channel",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                AmmoCore.this.startActivity(intent);
            }
        });
    }

    private void initializeNetlinkAdapter() {
        netlinkModel = networkServiceBinder.getNetlinkList();

        // set netlink view references
        netlinkList = (ListView) findViewById(R.id.netlink_list);
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
        operatorTv = (TextView) findViewById(R.id.operator_id_tv);

        // Get a reference to the AmmoService.
        final Intent networkServiceIntent = new Intent();//(this, AmmoService.class);
        networkServiceIntent.setComponent(ComponentName.unflattenFromString("edu.vu.isis.ammo.core/edu.vu.isis.ammo.core.AmmoService"));
        boolean result = getApplicationContext().bindService(networkServiceIntent,
                networkServiceConnection, BIND_AUTO_CREATE);
        String _result;
        if (result){
            _result = "TRUE";
        }else{
            _result = "FALSE";
        }
        Toast.makeText(getApplicationContext(), "Service Connection should be used here in the bind", Toast.LENGTH_SHORT).show();
        Toast.makeText(getApplicationContext(), "result:  " + _result, Toast.LENGTH_SHORT).show();
        if (!result)
            logger.error("AmmoActivity failed to bind to the AmmoService!");

        final Intent intent = new Intent();

        // let others know we are running
        intent.setAction(StartUpReceiver.RESET);
        this.sendBroadcast(intent);

        /*
         * Commented out for NTCNIE branch
         * 
         * spec = tabHost.newTabSpec("message_queue");
         * spec.setIndicator("Message Queue",
         * res.getDrawable(R.drawable.mailbox_icon)); spec.setContent(new
         * Intent("edu.vu.isis.ammo.core.ui.MessageQueueActivity.LAUNCH"));
         * getTabHost().addTab(spec);
         */

        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Advanced view is now the one and only view
        // this.netlinkAdvancedView = prefs.getBoolean("debug_mode",
        // this.netlinkAdvancedView);

    }

    @Override
    public void onStart() {
        super.onStart();
        logger.trace("::onStart");
        operatorTv = (TextView) findViewById(R.id.operator_id_tv_ref);
        // reset all rows
        if (channelList != null) {
            for (int ix = 0; ix < channelList.getChildCount(); ix++) {
                View row = channelList.getChildAt(ix);
                row.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        mReceiver = new StatusReceiver();

        final IntentFilter statusFilter = new IntentFilter();
        statusFilter.addAction(AmmoIntents.AMMO_ACTION_GATEWAY_STATUS_CHANGE);
        statusFilter.addAction(AmmoIntents.AMMO_ACTION_NETLINK_STATUS_CHANGE);
        registerReceiver(mReceiver, statusFilter);

        if (channelAdapter != null)
            channelAdapter.notifyDataSetChanged();
        if (netlinkAdapter != null)
            netlinkAdapter.notifyDataSetChanged();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        String operatorId = prefs.getString(INetPrefKeys.CORE_OPERATOR_ID, "operator");
        operatorTv.setText("Operator ID: " + operatorId);
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException ex) {
            logger.trace("tearing down the gateway status object");
        }
    }

    private StatusReceiver mReceiver = null;

    private class StatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent iIntent) {
            final String action = iIntent.getAction();

            if (action.equals(AmmoIntents.AMMO_ACTION_GATEWAY_STATUS_CHANGE)) {
                if (channelAdapter != null)
                    channelAdapter.notifyDataSetChanged();
            } else if (action
                    .equals(AmmoIntents.AMMO_ACTION_NETLINK_STATUS_CHANGE)) {
                if (netlinkAdapter != null)
                    netlinkAdapter.notifyDataSetChanged();
            }
        }
    }
    

    @Override
    public void onDestroy() {
        super.onDestroy();
        logger.trace("::onDestroy");
        //cursorLooper.interrupt();
        //unbindService(networkServiceConnection);
    }

    // ===========================================================
    // UI Management
    // ===========================================================

    public void viewTablesClick(View v) {
        startActivity(new Intent().setClass(this, DistributorTabActivity.class));
    }

    

    public void debugModeClick(View v) {
        Toast.makeText(this, "Debugging tools are not yet available",
                Toast.LENGTH_LONG).show();
    }

    public void loggingToolsClick(View v) {
        startActivity(new Intent().setClass(this, LoggerEditor.class));
    }

    public void hardResetClick(View v) {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    Intent intent = new Intent();
                    intent.setAction("edu.vu.isis.ammo.AMMO_HARD_RESET");
                    intent.setClass(AmmoCore.this, AmmoService.class);
                    startService(intent);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    break;
                }

            }
        };
        AlertDialog.Builder bldr = new AlertDialog.Builder(this);
        bldr.setMessage("Are you sure you want to reset the service?")
                .setPositiveButton("Yes", listener)
                .setNegativeButton("No", listener).show();
    }

    public void helpClick(View v) {
        startActivity(new Intent().setClass(this, AboutActivity.class));
    }
    
    public void operatorIdClick(View v) {
        startActivity(new Intent()
        .setComponent(new ComponentName("transapps.settings",
                "transapps.settings.SettingsActivity")));
    }
    
}

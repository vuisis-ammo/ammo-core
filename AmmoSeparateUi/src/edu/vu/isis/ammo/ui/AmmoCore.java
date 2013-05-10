/*Copyright (C) 2010-2013 Institute for Software Integrated Systems (ISIS)
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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
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
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.model.ModelChannel;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.receiver.StartUpReceiver;
import edu.vu.isis.ammo.ui.AboutActivity;
import edu.vu.isis.ammo.core.ui.util.ActivityEx;
import edu.vu.isis.ammo.coreui.distributor.ui.DistributorTabActivity;
import edu.vu.isis.ammo.ui.preferences.GatewayPreferences;
import edu.vu.isis.ammo.ui.preferences.MulticastPreferences;
import edu.vu.isis.ammo.ui.preferences.ReliableMulticastPreferences;
import edu.vu.isis.ammo.ui.preferences.SerialPreferences;
import edu.vu.isis.logger.ui.LogcatLogViewer;
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
    private String operatorId;
    
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
            
            
            //Toast.makeText(getApplicationContext(), "Service should be retrieved", Toast.LENGTH_SHORT).show();

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
    
    AmmoListItem[] AmmoChannelList;
    
    
    private void initializeGatewayAdapter() {
        //final Cursor channelCursor; /*= getContentResolver().query(
                    /*ContentUris.withAppendedId(edu.vu.isis.ammo.core.provider.ChannelProvider.CONTENT_URI, 0)
                     * This should be it. However, I'm hardcoding the value in for testing purposes. 
                    Uri.parse("content://edu.vu.isis.ammo.core.provider.channel/Channel"),
                    null,    // Which columns to return.
                    null,          // WHERE clause.
                    null,          // WHERE clause value substitution
                    null);   // Sort order.*/

        AmmoChannelList = new AmmoListItem[4];
        AmmoChannelList[0]= new AmmoListItem("THIS", " ","NO","","NO"," ");
        AmmoChannelList[1]= new AmmoListItem("IS", " ","UI"," ","UI"," ");
        AmmoChannelList[2]= new AmmoListItem("A", " ","HERE"," ","HERE"," ");
        AmmoChannelList[3]= new AmmoListItem("TEST", "","","",""," ");
        
        channelModel.add(AmmoChannelList[0]);
        channelModel.add(AmmoChannelList[1]);
        channelModel.add(AmmoChannelList[2]);
        channelModel.add(AmmoChannelList[3]);
        
        currentCursor = getContentResolver().query(Uri.parse("content://edu.vu.isis.ammo.core.provider.channel/Channel"), null, "colors", null, null);

        
        Handler handler = new Handler();
        
        if (currentCursor != null){ //if that worked...
            
            AmmoChannelList = new AmmoListItem[currentCursor.getCount()];
            
            channelModel.clear();
            
            for (int i = 0; i < AmmoChannelList.length; i++){
                
                if (currentCursor.moveToNext()){
                    if (Integer.parseInt(currentCursor.getString(0)) == 0) {
                        operatorId = currentCursor.getString(1);
                    } else {
                        AmmoChannelList[i] = new AmmoListItem(currentCursor.getString(1), 
                            currentCursor.getString(2), currentCursor.getString(3), currentCursor.getString(4)
                            , currentCursor.getString(5), currentCursor.getString(6));
                    
                        AmmoChannelList[i].setColorOne(Integer.parseInt(currentCursor.getString(7)));
                    
                        AmmoChannelList[i].setColorTwo(Integer.parseInt(currentCursor.getString(8)));
                        channelModel.add(AmmoChannelList[i]);
                    }
                } 
                
            }
            
            currentCursor.registerContentObserver(new ContentObserver(handler){
                
                @Override
                public void onChange(boolean selfChange) {
                    receiveCount++;
                    //Toast.makeText(getApplicationContext(), "Inside onChange1 method", Toast.LENGTH_SHORT).show();
                    //Toast.makeText(getApplicationContext(), "Received notification in activity "+ receiveCount, Toast.LENGTH_SHORT).show();
                    
                    boolean getColors = false;
                    if (receiveCount != 0 && receiveCount%5 == 0){ //every fifth time, except the very first time...
                        getColors = true;
                    }
                    
                    String colors = null;
                    if (getColors){
                        colors = "colors";
                    }//send null as the selection except every fifth time when we send "colors"
                     //so the provider will send us the color information for those times
                    
                    Cursor tempCursor = getContentResolver().query(Uri.parse("content://edu.vu.isis.ammo.core.provider.channel/Channel"), null, colors, null, null);
                    //TODO tempCursor is used here for testing purposes. The code in the onChange should ACTUALLY requery currentCursor...
                    // and then use the new values in there. 
                    channelAdapter.setNotifyOnChange(true);
                    
                    for (int i = 0; i < AmmoChannelList.length; i++){
                        
                        if (tempCursor.moveToNext()){
                            if (tempCursor.getString(0).equals(Integer.toString(0))) {
                                operatorId = tempCursor.getString(1);
                                operatorTv.setText("Operator ID: " + operatorId);
                            } else {
                                AmmoChannelList[i].update(tempCursor.getString(1), 
                                   tempCursor.getString(2), tempCursor.getString(3), tempCursor.getString(4),
                                   tempCursor.getString(5), tempCursor.getString(6));
                               if (getColors){ //if we asked for and got colors, set them here
                                   AmmoChannelList[i].setColorOne(Integer.parseInt(tempCursor.getString(7)));
                                   AmmoChannelList[i].setColorTwo(Integer.parseInt(tempCursor.getString(8)));
                               }
                            }
                        }
                    }

                    channelAdapter.notifyDataSetChanged();
                    
                    tempCursor.close();
                    
                    //TODO this count is for debugging; remove this
                    final TextView temptest = (TextView) findViewById(R.id.ammo_test_tv11);
                    temptest.setText(/*"Stuff just happened!!"*/""+receiveCount);
                } 
            });
            
        } else {
            Toast.makeText(getApplicationContext(), "Cursor was null; cannot initialize UI", Toast.LENGTH_LONG).show();//this is important; long toast
        }
        
        channelList = (ChannelListView) findViewById(R.id.gateway_list);
        
        channelAdapter = new ChannelAdapter2(this, channelModel);
        channelAdapter.notifyDataSetChanged();
        channelList.setAdapter(channelAdapter);

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
        operatorId = "operator";//init value
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
        //Toast.makeText(getApplicationContext(), "Service Connection should be used here in the bind", Toast.LENGTH_SHORT).show();
        //Toast.makeText(getApplicationContext(), "result:  " + _result, Toast.LENGTH_SHORT).show();
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
        String[] tools = {
                "Logcat Viewer", "Shell Command Buttons"
        };
        OnClickListener dialogListener = new OnClickListener() {
            private final int LOGCAT = 0;
            private final int AUTOBOT = 1;

            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent();
                switch (which) {
                    case LOGCAT:
                    	//TODO make this trigger the SEPARATE APPLICATION laui
                    	//Don't import the classes into ammo
                        intent.setClass(AmmoCore.this, LogcatLogViewer.class);
                        break;
                    case AUTOBOT:
                        intent.setAction("edu.vu.isis.tools.autobot.action.LAUNCH_AUTOBOT");
                        break;
                    default:
                        logger.warn("Invalid choice selected in debugging tools dialog");
                }
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(AmmoCore.this, "Tool not found on this device",
                            Toast.LENGTH_LONG).show();
                    logger.warn("Activity not found for debugging tools", e);
                }
            }
        };

        AlertDialog.Builder bldr = new AlertDialog.Builder(this);
        bldr.setTitle("Select a Tool").setItems(tools, dialogListener);
        bldr.create().show();
    }

    public void loggingToolsClick(View v) {
        try {
        	startActivity(new Intent().setClass(this, LoggerEditor.class));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(AmmoCore.this, "Tool not found on this device",
                    Toast.LENGTH_LONG).show();
            logger.warn("Activity not found for logging tools", e);
        }
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

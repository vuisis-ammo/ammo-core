/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

//There are things in this file that are prepared for the Android 3.0 port
//They are tagged by ANDROID3.0

package edu.vu.isis.ammo.core.ui;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.ui.DistributorTabActivity;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.ModelChannel;
import edu.vu.isis.ammo.core.model.Multicast;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.model.ReliableMulticast;
import edu.vu.isis.ammo.core.model.Serial;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.receiver.StartUpReceiver;
import edu.vu.isis.ammo.core.ui.util.ActivityEx;
import edu.vu.isis.logger.ui.LogcatLogViewer;
import edu.vu.isis.logger.ui.LoggerEditor;

/**
 * The principle activity for ammo core. Provides a means for... ...changing the
 * user preferences. ...checking delivery status of various messages.
 * ...registering/unregistering content interest requests.
 */
public class AmmoCore extends ActivityEx {
    public static final Logger logger = LoggerFactory.getLogger("ui");

    public static final String PREF_KEY = "prefkey";

    public static final int MULTICAST = 0;
    public static final int RELIABLE_MULTICAST = 1;
    public static final int SERIAL = 2;
    public static final int GATEWAY = 3;

    // ===========================================================
    // Fields
    // ===========================================================

    private List<ModelChannel> channelModel = null;
    private ChannelAdapter channelAdapter = null;

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
            final AmmoService.DistributorServiceAidl binder = (AmmoService.DistributorServiceAidl) service;
            parent.networkServiceBinder = binder.getService();
            initializeGatewayAdapter();

            // Netlink Adapter is disabled for now (doesn't work)
            // initializeNetlinkAdapter();
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.trace("::onServiceDisconnected - Network Service");
            parent.networkServiceBinder = null;
        }
    };

    private void initializeGatewayAdapter() {
        channelModel = networkServiceBinder.getGatewayList();

        // set gateway view references
        channelList = (ChannelListView) findViewById(R.id.gateway_list);
        channelAdapter = new ChannelAdapter(this, channelModel);
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
                ModelChannel selectedChannel = channelAdapter.getItem(position);
                if (selectedChannel instanceof Gateway) {
                    intent.setClass(AmmoCore.this, GatewayPreferences.class);
                } else if (selectedChannel instanceof Serial) {
                    intent.setClass(AmmoCore.this, SerialPreferences.class);
                } else if (selectedChannel instanceof ReliableMulticast) {
                    intent.setClass(AmmoCore.this, ReliableMulticastPreferences.class);
                } else if (selectedChannel instanceof Multicast) {
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

    @SuppressWarnings("unused")
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
        final Intent networkServiceIntent = new Intent(this, AmmoService.class);
        boolean result = bindService(networkServiceIntent,
                networkServiceConnection, BIND_AUTO_CREATE);
        if (!result)
            logger.error("AmmoActivity failed to bind to the AmmoService!");

        final Intent intent = new Intent();

        // let others know we are running
        intent.setAction(StartUpReceiver.RESET);
        this.sendBroadcast(intent);

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
        unbindService(networkServiceConnection);
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
    };

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

    public void refreshList() {
        channelList.invalidateViews();
    }

}

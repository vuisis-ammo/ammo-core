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

package edu.vu.isis.ammo.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import transapps.settings.Keys;
import transapps.settings.Settings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IntentNames;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.ChannelFilter;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelStatus;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy;
import edu.vu.isis.ammo.core.distributor.DistributorThread;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.ModelChannel;
import edu.vu.isis.ammo.core.model.Multicast;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.model.PhoneNetlink;
import edu.vu.isis.ammo.core.model.ReliableMulticast;
import edu.vu.isis.ammo.core.model.Serial;
import edu.vu.isis.ammo.core.model.WifiNetlink;
import edu.vu.isis.ammo.core.model.WiredNetlink;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.IChannelManager;
import edu.vu.isis.ammo.core.network.INetChannel;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.JournalChannel;
import edu.vu.isis.ammo.core.network.MulticastChannel;
import edu.vu.isis.ammo.core.network.NetChannel;
import edu.vu.isis.ammo.core.network.ReliableMulticastChannel;
import edu.vu.isis.ammo.core.network.SerialChannel;
import edu.vu.isis.ammo.core.network.TcpChannel;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.receiver.CellPhoneListener;
import edu.vu.isis.ammo.core.receiver.WifiReceiver;
import edu.vu.isis.ammo.util.IRegisterReceiver;
import edu.vu.isis.ammo.util.UniqueIdentifiers;

/**
 * <p>
 * The NetworkManager is responsible for prioritizing and serializing requests for
 * data communications between distributed application databases. The
 * NetworkManager issues calls to the NetworkManager for updates and then writes the
 * results to the correct content provider using the deserialization mechanism
 * defined by each content provider.
 * <p>
 * Any activity or application wishing to send data via the NetworkManager should
 * use one of the AmmoRequest API methods for communication between said
 * application and AmmoCore.
 * <p>
 * Any activity or application wishing to receive updates when a content
 * provider has been modified can register via a custom ContentObserver
 * subclass.
 * <p>
 * The real work is delegated to the Distributor Thread, which maintains a
 * queue.
 */
public enum NetworkManager  implements INetworkService,
        INetworkService.OnSendMessageHandler, IChannelManager {
	INSTANCE;
	
	private Context context = null;
	public static NetworkManager getInstance (final Context context) {
		if (INSTANCE.context == null) {
			INSTANCE.context = context;
			INSTANCE.onCreate();
		}
		
		return INSTANCE;
	}
	
	public Context getContext () {
		return context;
	}
	
	
    // ===========================================================
    // Constants
    // ===========================================================
    public static final Logger logger = LoggerFactory.getLogger("network.manager");


    public static final Intent LAUNCH = new Intent("edu.vu.isis.ammo.core.distributor.AmmoService.LAUNCH");
    public static final String BIND = "edu.vu.isis.ammo.core.distributor.AmmoService.BIND";
    public static final String PREPARE_FOR_STOP = "edu.vu.isis.ammo.core.distributor.AmmoService.PREPARE_FOR_STOP";
    public static final String SEND_SERIALIZED = "edu.vu.isis.ammo.core.distributor.AmmoService.SEND_SERIALIZED";

    /**
     * Various link types:
     * <p>
     * [0-2] available for immediate use
     * <p>
     * [3-7] added in anticipation
     * <p>
     */
    public static enum linkTypes {
        /** any wired connection */
        WIRED(0),
        /** 802.11 (a/b/g/n) */
        WIFI(1),
        /** phone 3G */
        MOBILE_3G(2),
        /** phone 2G */
        MOBILE_2G(3),
        /** phone 4G */
        MOBILE_4G(4),
        /** bluetooth(R) */
        BLUETOOTH(5),
        /** near field communication */
        NFC(6),
        /** infrared */
        IR(7);
        final public int value;

        private linkTypes(int num) {
            this.value = num;
        }
    }

    public static final String SIZE_KEY = "sizeByteArrayKey";
    public static final String CHECKSUM_KEY = "checksumByteArrayKey";

    // Interfaces

    // ===========================================================
    // Fields
    // ===========================================================

    private String sessionId = "";
    private String deviceId = INetPrefKeys.DEFAULT_CORE_DEVICE_ID;
    private String operatorId = INetPrefKeys.DEFAULT_CORE_OPERATOR_ID;
    private String operatorKey = INetPrefKeys.DEFAULT_CORE_OPERATOR_KEY;

    // isJournalUserDisabled
    private boolean isJournalUserDisabled = INetPrefKeys.DEFAULT_JOURNAL_ENABLED;

    // Determine if the connection is enabled
    private boolean isGatewaySuppressed = INetPrefKeys.DEFAULT_GATEWAY_ENABLED;
    private boolean isMulticastSuppressed = INetPrefKeys.DEFAULT_MULTICAST_ENABLED;
    private boolean isReliableMulticastSuppressed = INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_ENABLED;
    private boolean isSerialSuppressed = INetPrefKeys.DEFAULT_SERIAL_ENABLED;

    static final private AtomicBoolean isStartCommandSuppressed = new AtomicBoolean(false);

    static public void suppressStartCommand() {
        NetworkManager.isStartCommandSuppressed.set(true);
    }

    static public void activateStartCommand() {
        NetworkManager.isStartCommandSuppressed.set(false);
    }

    // for providing networking support
    // should this be using IPv6?
    private boolean networkingSwitch = true;

    public boolean isNetworking() {
        return networkingSwitch;
    }

    public void setNetworkingSwitch(boolean value) {
        networkingSwitch = value;
    }

    public boolean getNetworkingSwitch() {
        return networkingSwitch;
    }

    public boolean toggleNetworkingSwitch() {
        return networkingSwitch = networkingSwitch ? false : true;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getDeviceId() {
        if (this.deviceId == null) {
            this.deviceId = UniqueIdentifiers.device(this.context);
            this.updatePref(INetPrefKeys.CORE_DEVICE_ID, this.deviceId);
            logger.warn("no device specified, generating: [{}]", this.deviceId);
        }
        return deviceId;
    }

    private NetworkBroadcastReceiver myNetworkReceiver = null;

    private DistributorThread distThread;

    private TelephonyManager tm;
    private CellPhoneListener cellPhoneListener;
    private WifiReceiver wifiReceiver;

    private ReadyBroadcastReceiver mReadyResourceReceiver = null;
    private boolean mNetworkConnected = false;
    private boolean mSdCardAvailable = false;

    static private AtomicReference<String> distributionPolicyFileName = new AtomicReference<String>();

    static public void distributionPolicyFileName(String name) {
        NetworkManager.distributionPolicyFileName.set(name);
    }

    private IRegisterReceiver mReceiverRegistrar = null;

    final private AtomicReference<DistributorPolicy> policy =
            new AtomicReference<DistributorPolicy>();

    /**
     * The loading of the policy is lazy; the first time it is requested it is
     * loaded.
     * 
     * @return the loaded policy.
     */
    public DistributorPolicy policy() {
        if (this.policy.get() == null) {
            this.policy(DistributorPolicy.newInstance(this.context,
                    NetworkManager.distributionPolicyFileName.get()));
        }
        return this.policy.get();
    }

    public DistributorPolicy policy(DistributorPolicy policy) {
        logger.info("setting new policy: \n{}", policy);
        return this.policy.getAndSet(policy);
    }


    private PhoneStateListener mListener;

    private Settings globalSettings; // from tasettings
    private SharedPreferences localSettings; // local copy

    /**
     * moving the variable to be a class variable instead of being a local
     * variable within the onCreate method
     */
    private WifiManager.MulticastLock multicastLock = null;

    public Handler notifyMsg = null;

    /**
     * When the service is first created, we should grab the IP and Port values
     * from the SystemPreferences.
     */
   
    private void onCreate() {
        logger.info("ammo service on create {}",
                Integer.toHexString(System.identityHashCode(this)));

        this.journalChannel.init(context);
        this.tcpChannel.init(context);
        this.tcpMediaChannel.init(context);
        this.reliableMulticastChannel.init(context);
        this.multicastChannel.init(context);
        for (NetChannel channel : this.registeredChannels) {
            channel.init(context);
        }

        notifyMsg = new Handler();
        // set up the worker thread
        this.distThread = new DistributorThread(this.context, this);
        this.distThread.start();
        // Initialize our receivers/listeners.
        /*
         * wifiReceiver = new WifiReceiver(); cellPhoneListener = new
         * CellPhoneListener(this); tm = (TelephonyManager) this
         * .getSystemService(Context.TELEPHONY_SERVICE);
         * tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_DATA_ACTIVITY
         * | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
         */

        // Listen for changes to resource availability
        this.mReadyResourceReceiver = new ReadyBroadcastReceiver();
        this.mReceiverRegistrar = new IRegisterReceiver() {
            @Override
            public Intent registerReceiver(final BroadcastReceiver aReceiver,
                    final IntentFilter aFilter) {
                return NetworkManager.this.context.registerReceiver(aReceiver, aFilter);
            }

            @Override
            public void unregisterReceiver(final BroadcastReceiver aReceiver) {
                NetworkManager.this.context.unregisterReceiver(aReceiver);
            }
        };
        final IntentFilter readyFilter = new IntentFilter();

        readyFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        readyFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mReceiverRegistrar.registerReceiver(mReadyResourceReceiver, readyFilter);

        final IntentFilter mediaFilter = new IntentFilter();

        mediaFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mediaFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mediaFilter.addDataScheme("file");
        mReceiverRegistrar.registerReceiver(mReadyResourceReceiver, mediaFilter);

        mReadyResourceReceiver.checkResourceStatus(this.context);

        this.policy();

        this.globalSettings = new Settings(context);
        this.globalSettings
                .registerOnSharedPreferenceChangeListener(this.pantherPreferenceChangeListener);

        this.localSettings = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.localSettings
                .registerOnSharedPreferenceChangeListener(this.ammoPreferenceChangeListener);

        try {
            this.serialChannel = new SerialChannel(ChannelFilter.SERIAL, this, this.context);
         // this.serialChannel.init(context);
        } catch (UnsatisfiedLinkError er) {
            logger.error("serial channel native not found");
        }

        netChannelMap.put("default", tcpChannel);
        netChannelMap.put(tcpChannel.name, tcpChannel);
        netChannelMap.put(tcpMediaChannel.name, tcpMediaChannel);        
        netChannelMap.put(multicastChannel.name, multicastChannel);
        netChannelMap.put(reliableMulticastChannel.name, reliableMulticastChannel);
        netChannelMap.put(journalChannel.name, journalChannel);
        netChannelMap.put(serialChannel.name, serialChannel);

        modelChannelMap.put(tcpChannel.name,
                Gateway.getInstance(this.context, tcpChannel));
        modelChannelMap.put(tcpMediaChannel.name,
                Gateway.getMediaInstance(this.context, tcpMediaChannel));        
        modelChannelMap.put(multicastChannel.name,
                Multicast.getInstance(this.context, multicastChannel));
        modelChannelMap.put(reliableMulticastChannel.name,
                ReliableMulticast.getInstance(this.context, reliableMulticastChannel));
        modelChannelMap.put(serialChannel.name,
                Serial.getInstance(this.context, serialChannel));
        /*
         * Does the mock channel need a UI ?
         * modelChannelMap.put(mockChannel.name,
         * MockChannel.getInstance(this.context, mockChannel));
         */

        mNetlinks.add(WifiNetlink.getInstance(this.context));
        mNetlinks.add(WiredNetlink.getInstance(this.context));
        mNetlinks.add(PhoneNetlink.getInstance(this.context));

        // FIXME: find the appropriate time to release() the multicast lock.
        logger.trace("Acquiring multicast lock()");
        WifiManager wm = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        multicastLock = wm.createMulticastLock("mydebuginfo");

        multicastLock.acquire();
        logger.trace("...acquired multicast lock()");

        // no point in enabling the socket until the preferences have been read
        this.tcpChannel.disable();
        this.multicastChannel.disable();
        this.reliableMulticastChannel.disable();
        this.tcpMediaChannel.disable();
        serialChannel.disable(); // Unnecessary, but the UI needs an
                                 // update after the modelChannelMap
                                 // is initialized.
        // The serial channel is created in a disabled state.

        this.acquirePreferences();

        if (this.networkingSwitch) {
            if (!this.isGatewaySuppressed) {
                this.tcpChannel.enable();
                this.tcpMediaChannel.enable();
            }
            if (!this.isMulticastSuppressed) {
                this.multicastChannel.enable();
                this.multicastChannel.reset(); // This starts the connector
                                               // thread.
            }
            if (!this.isReliableMulticastSuppressed) {
                this.reliableMulticastChannel.enable();
                this.reliableMulticastChannel.reset(); // This starts the
                                                       // connector thread.
            }
            if (!this.isSerialSuppressed) {
                this.serialChannel.enable();
            }
        }

        this.myNetworkReceiver = new NetworkBroadcastReceiver();

        final IntentFilter networkFilter = new IntentFilter();
        networkFilter.addAction(INetworkService.ACTION_RECONNECT);
        networkFilter.addAction(INetworkService.ACTION_DISCONNECT);

        networkFilter.addAction(AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE);
        networkFilter.addAction(AmmoIntents.ACTION_SERIAL_LINK_CHANGE);
        networkFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        networkFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        networkFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        networkFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        networkFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        networkFilter.addAction(Intent.ACTION_TIME_CHANGED); // sync service
                                                             // changes cpu time
                                                             // - need to handle
                                                             // in serial
                                                             // channel

        this.mReceiverRegistrar.registerReceiver(this.myNetworkReceiver, networkFilter);

        this.mListener = new PhoneStateListener() {
            public void onDataConnectionStateChanged(int state) {
                logger.trace("PhoneReceive::onCallStateChanged() - 3G status change {}", state);
                mNetlinks.get(linkTypes.MOBILE_3G.value).updateStatus();
                netlinkStatusChanged();
            }
        };
        final TelephonyManager tm = (TelephonyManager) this.context
                .getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

        final Intent loginIntent = new Intent(IntentNames.AMMO_LOGIN);

        loginIntent.putExtra("operatorId", this.operatorId);
        this.context.sendBroadcast(loginIntent);

        this.refresh(); // refresh subscribe and retrieval tables
    }

    /**
     * FIXME: this probably needs to happen differently.
     * <p>
     * Do we need to add a flag to the intent sent below so that the receiver
     * knows when it is done initializing mcast channel?
     * <p>
     * We broadcast the AMMO_LOGIN intent to force ammo applications to register
     * their subscriptions.
     */
    void refresh() {
        logger.trace("Forcing applications to register their subscriptions");

        this.distThread.clearTables();

        // broadcast login event to apps ...
        final Intent loginIntent = new Intent(IntentNames.AMMO_READY);
        loginIntent.addCategory(IntentNames.RESET_CATEGORY);

        this.tcpChannel.reset();
        this.tcpMediaChannel.reset();
        this.multicastChannel.reset();
        this.reliableMulticastChannel.reset();
        this.serialChannel.reset();
        for (NetChannel channel : this.registeredChannels) {
            channel.reset();
        }

        loginIntent.putExtra("operatorId", this.operatorId);
        this.context.sendBroadcast(loginIntent);
    }

   
    public void onDestroy() {
        logger.warn("::onDestroy - NetworkManager");
        if (tcpChannel != null)
            this.tcpChannel.disable();
        if (tcpMediaChannel != null)
            this.tcpMediaChannel.disable();
        if (multicastChannel != null)
            this.multicastChannel.disable();
        if (reliableMulticastChannel != null)
            this.reliableMulticastChannel.disable();
        if (journalChannel != null)
            this.journalChannel.close();
        if (serialChannel != null)
            this.serialChannel.disable();
        for (NetChannel channel : this.registeredChannels) {
            if (channel != null) {
                channel.disable();
            }
        }

        if (this.tm != null)
            this.tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_NONE);

        if (this.wifiReceiver != null)
            this.wifiReceiver.setInitialized(false);

        if (this.mReceiverRegistrar != null) {
            this.mReceiverRegistrar.unregisterReceiver(this.myNetworkReceiver);
            this.mReceiverRegistrar.unregisterReceiver(this.mReadyResourceReceiver);
        }
        
        if (context != null)
        	context = null;
    }

    // ===========================================================
    // Networking
    // ===========================================================

    /**
     * Get the preference specified, preferring the global value over the local.
     * Make sure the local settings match the working variable as that is what
     * will be displayed by the user interface.
     * 
     * @param key
     * @param def a default value
     * @return the selected value
     */
    private String aggregatePref(final String key, final String def) {
        logger.trace("AggregatePref called: key={}", key);
        final String local = this.localSettings.getString(key, def);
        final String global = this.globalSettings.getString(key, local);

        if (this.localSettings.contains(key) && (local != null) && local.equals(global)) {
            return local;
        }
        if (global == null) {
            return null;
        }
        logger.trace("Committing preferences for key={} value={}", key, global);
        final boolean success = this.localSettings.edit()
                .putString(key, global)
                .commit();
        if (!success) {
            logger.error("cannot aggregate local setting {}", key);
        }
        return global;
    }

    private Boolean aggregatePref(final String key, final boolean def) {
        logger.trace("AggregatePref called: key={}", key);
        final Boolean local = this.localSettings.getBoolean(key, def);
        final Boolean global = Boolean.parseBoolean(this.globalSettings.getString(key,
                String.valueOf(local)));

        if (this.localSettings.contains(key) && (local != null) && local.equals(global)) {
            return local;
        }
        if (global == null) {
            return null;
        }
        logger.trace("Committing preferences for key={} value={}", key, global);
        final boolean success = this.localSettings.edit()
                .putBoolean(key, global)
                .commit();
        if (!success) {
            logger.error("cannot aggregate local setting {}", key);
        }
        return global;
    }

    private String updatePref(final String key, final String def) {
        final String global = this.globalSettings.getString(key, def);
        final String local = this.localSettings.getString(key, def);
        if (this.localSettings.contains(key) && (local != null) && local.equals(global)) {
            return local;
        }
        if (global == null) {
            return null;
        }
        final boolean success = this.localSettings.edit()
                .putString(key, global)
                .commit();
        if (!success) {
            logger.error("cannot update local setting {}", key);
        }
        return global;
    }

    private Boolean updatePref(final String key, final boolean def) {
        final Boolean global = Boolean.parseBoolean(this.globalSettings.getString(key,
                String.valueOf(def)));
        final Boolean local = this.localSettings.getBoolean(key, def);
        if (this.localSettings.contains(key) && (local != null) && local.equals(global)) {
            return local;
        }
        if (global == null) {
            return null;
        }
        final boolean success = this.localSettings.edit()
                .putBoolean(key, global)
                .commit();
        if (!success) {
            logger.error("cannot update local setting {}", key);
        }
        return global;
    }

    private Integer updatePref(final String key, final int def) {
        final String defStr = String.valueOf(def);
        final Integer global = Integer.parseInt(this.globalSettings.getString(key, defStr));
        final Integer local = Integer.parseInt(this.localSettings.getString(key, defStr));
        if (this.localSettings.contains(key) && (local != null) && local.equals(global)) {
            return local;
        }
        if (global == null) {
            return null;
        }
        final boolean success = this.localSettings.edit()
                .putString(key, String.valueOf(global))
                .commit();
        if (!success) {
            logger.error("cannot update local setting {}", key);
        }
        return global;
    }

    /**
     * Read the system preferences for the network connection information.
     */
    void acquirePreferences() {
        logger.trace("::acquirePreferences");

        this.networkingSwitch = this.localSettings
                .getBoolean(INetDerivedKeys.NET_CONN_PREF_SHOULD_USE,
                        this.networkingSwitch);

        this.deviceId = this.localSettings
                .getString(INetPrefKeys.CORE_DEVICE_ID,
                        this.deviceId);

        this.operatorId = this
                .aggregatePref(INetPrefKeys.CORE_OPERATOR_ID,
                        this.operatorId);

        this.operatorKey = this
                .aggregatePref(INetPrefKeys.CORE_OPERATOR_KEY,
                        this.operatorKey);

        PLogger.SET_PANTHR.debug("acquire device={} operator={} pass={}",
                new Object[] {
                        this.deviceId, this.operatorId, this.operatorKey
                });

        // JOURNAL
        this.isJournalUserDisabled = this
                .aggregatePref(INetPrefKeys.JOURNAL_DISABLED,
                        this.isJournalUserDisabled);

        // GATEWAY
        this.isGatewaySuppressed = this
                .aggregatePref(INetPrefKeys.GATEWAY_DISABLED,
                        INetPrefKeys.DEFAULT_GATEWAY_ENABLED);

        final String gatewayHostname = this
                .aggregatePref(INetPrefKeys.GATEWAY_HOST,
                        INetPrefKeys.DEFAULT_GATEWAY_HOST);

        final String gatewayPortStr = this.localSettings
                .getString(INetPrefKeys.GATEWAY_PORT,
                        String.valueOf(INetPrefKeys.DEFAULT_GATEWAY_PORT));
        int gatewayPort = Integer.valueOf(gatewayPortStr);

        final String flatLineTimeStr = this.localSettings.
                getString(INetPrefKeys.GATEWAY_FLAT_LINE_TIME,
                        String.valueOf(INetPrefKeys.DEFAULT_GW_FLAT_LINE_TIME));
        final long flatLineTime = Integer.valueOf(flatLineTimeStr);

        this.tcpChannel.setHost(gatewayHostname);
        this.tcpChannel.setPort(gatewayPort);
        this.tcpChannel.setFlatLineTime(flatLineTime * 60 * 1000);
        this.tcpChannel.toLog("acquire ");

        this.tcpMediaChannel.setHost(gatewayHostname);
        this.tcpMediaChannel.setPort(gatewayPort);
        this.tcpMediaChannel.setFlatLineTime(flatLineTime * 60 * 1000);
        this.tcpMediaChannel.toLog("acquire ");
        // convert minutes into milliseconds

        /*
         * Multicast
         */
        this.isMulticastSuppressed = this
                .aggregatePref(INetPrefKeys.MULTICAST_DISABLED,
                        INetPrefKeys.DEFAULT_MULTICAST_ENABLED);

        final String multicastHost = this.localSettings
                .getString(INetPrefKeys.MULTICAST_HOST,
                        INetPrefKeys.DEFAULT_MULTICAST_HOST);

        int multicastPort = Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.MULTICAST_PORT,
                        String.valueOf(INetPrefKeys.DEFAULT_MULTICAST_PORT)));

        long multicastFlatLine = Long.parseLong(this.localSettings
                .getString(INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT,
                        String.valueOf(INetPrefKeys.DEFAULT_MULTICAST_NET_CONN)));

        int multicastIdleTime = Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT,
                        String.valueOf(INetPrefKeys.DEFAULT_MULTICAST_IDLE_TIME)));

        int multicastTTL = Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.MULTICAST_TTL,
                        String.valueOf(INetPrefKeys.DEFAULT_MULTICAST_TTL)));

        this.multicastChannel.setHost(multicastHost);
        this.multicastChannel.setPort(multicastPort);
        this.multicastChannel.setFlatLineTime(multicastFlatLine);
        this.multicastChannel.setSocketTimeout(multicastIdleTime);
        this.multicastChannel.setTTL(multicastTTL);
        this.multicastChannel.toLog("acquire ");

        /*
         * Reliable Multicast
         */
        this.isReliableMulticastSuppressed = this
                .aggregatePref(INetPrefKeys.RELIABLE_MULTICAST_DISABLED,
                        INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_ENABLED);

        final String reliableMulticastHost = this.localSettings
                .getString(INetPrefKeys.RELIABLE_MULTICAST_HOST,
                        INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_HOST);

        int reliableMulticastPort = Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.RELIABLE_MULTICAST_PORT,
                        String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_PORT)));

        long reliableMulticastFlatLine = Long.parseLong(this.localSettings
                .getString(INetPrefKeys.RELIABLE_MULTICAST_NET_CONN_TIMEOUT,
                        String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_NET_CONN)));

        int reliableMulticastIdleTime = Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.RELIABLE_MULTICAST_CONN_IDLE_TIMEOUT,
                        String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_IDLE_TIME)));

        int reliableMulticastTTL = Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.RELIABLE_MULTICAST_TTL,
                        String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_TTL)));

        this.reliableMulticastChannel.setHost(reliableMulticastHost);
        this.reliableMulticastChannel.setPort(reliableMulticastPort);
        this.reliableMulticastChannel.setFlatLineTime(reliableMulticastFlatLine);
        this.reliableMulticastChannel.setSocketTimeout(reliableMulticastIdleTime);
        this.reliableMulticastChannel.setTTL(reliableMulticastTTL);
        this.reliableMulticastChannel.toLog("acquire ");

        /*
         * SerialChannel
         */
        this.isSerialSuppressed = this
                .aggregatePref(INetPrefKeys.SERIAL_DISABLED,
                        INetPrefKeys.DEFAULT_SERIAL_ENABLED);

        this.serialChannel.setDevice(this.localSettings
                .getString(INetPrefKeys.SERIAL_DEVICE,
                        INetPrefKeys.DEFAULT_SERIAL_DEVICE));

        this.serialChannel.setBaudRate(Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.SERIAL_BAUD_RATE,
                        String.valueOf(INetPrefKeys.DEFAULT_SERIAL_BAUD_RATE))));

        this.serialChannel.setSlotNumber(Integer.parseInt(this
                .aggregatePref(INetPrefKeys.SERIAL_SLOT_NUMBER,
                        String.valueOf(INetPrefKeys.DEFAULT_SERIAL_SLOT_NUMBER))));

        serialChannel.setRadiosInGroup(Integer.parseInt(this
                .aggregatePref(INetPrefKeys.SERIAL_RADIOS_IN_GROUP,
                        String.valueOf(INetPrefKeys.DEFAULT_SERIAL_RADIOS_IN_GROUP))));

        serialChannel.setSlotDuration(Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.SERIAL_SLOT_DURATION,
                        String.valueOf(INetPrefKeys.DEFAULT_SERIAL_SLOT_DURATION))));

        serialChannel.setTransmitDuration(Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.SERIAL_TRANSMIT_DURATION,
                        String.valueOf(INetPrefKeys.DEFAULT_SERIAL_TRANSMIT_DURATION))));

        serialChannel.setSenderEnabled(this.localSettings
                .getBoolean(INetPrefKeys.SERIAL_SEND_ENABLED,
                        INetPrefKeys.DEFAULT_SERIAL_SEND_ENABLED));

        this.serialChannel.toLog("acquire ");

        for (NetChannel channel : this.registeredChannels) {
            channel.toLog("acquire");
        }

    }

    /**
     * Reset the local copies of the shared preference. Also indicate that the
     * gateway connections are stale will need to be refreshed.
     * 
     * @param prefs a sharedPreferencesInterface for accessing and modifying
     *            preference data
     * @param key a string to signal which preference to access
     */
    final OnSharedPreferenceChangeListener pantherPreferenceChangeListener =
            new OnSharedPreferenceChangeListener()
            {
                final private NetworkManager parent = NetworkManager.this;

                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    logger.trace("::onSharedPreferenceChanged panthr {}", key);
                    //
                    // handle network authentication group
                    //
                    if (key.equals(INetPrefKeys.CORE_DEVICE_ID)) {
                        final String id = parent.updatePref(key, parent.deviceId);
                        PLogger.SET_PANTHR.debug("device[{} -> {}]", parent.deviceId, id);
                    }
                    else if (key.equals(INetPrefKeys.CORE_OPERATOR_ID)) {
                        final String id = parent.updatePref(key, parent.operatorId);
                        PLogger.SET_PANTHR.debug("operator[{} -> {}]", parent.operatorId, id);
                    }
                    else if (key.equals(INetPrefKeys.CORE_OPERATOR_KEY)) {
                        final String prev = (parent.operatorKey == null)
                                ? INetPrefKeys.DEFAULT_CORE_OPERATOR_KEY : parent.operatorKey;
                        final String pass = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR.debug("pass[{} -> {}]", prev, pass);
                    }

                    //
                    // Journal
                    //
                    else if (key.equals(INetPrefKeys.JOURNAL_DISABLED)) {
                        final boolean active = parent.updatePref(key, parent.isJournalUserDisabled);
                        PLogger.SET_PANTHR_JOURNAL.debug("suppress[{} -> {}]",
                                parent.isJournalUserDisabled, active);
                    }

                    //
                    // Gateway
                    //
                    else if (key.equals(INetPrefKeys.GATEWAY_DISABLED)) {
                        final boolean active = parent.updatePref(key, parent.isGatewaySuppressed);
                        PLogger.SET_PANTHR_GW.debug("suppress[{} -> {}]",
                                parent.isGatewaySuppressed,
                                active);
                    }
                    else if (key.equals(INetPrefKeys.GATEWAY_HOST)) {
                        final String prev = (parent.tcpChannel == null) ? INetPrefKeys.DEFAULT_GATEWAY_HOST
                                : parent.tcpChannel.getLocalIpAddress();              
                        final String host = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR_GW.debug("host[{} -> {}]", prev, host);
                    }
                    else if (key.equals(INetPrefKeys.GATEWAY_PORT)) {
                        final int port = parent.updatePref(key, INetPrefKeys.DEFAULT_GATEWAY_PORT);
                        PLogger.SET_PANTHR_GW.debug("port[{} -> {}]",
                                INetPrefKeys.DEFAULT_GATEWAY_PORT,
                                port);
                    }
                    else if (key.equals(INetPrefKeys.GATEWAY_TIMEOUT)) {
                        final int to = parent.updatePref(key, INetPrefKeys.DEFAULT_GW_TIMEOUT);
                        PLogger.SET_PANTHR_GW.debug("timeout[{} -> {}]",
                                INetPrefKeys.DEFAULT_GW_TIMEOUT,
                                to);
                    }
                    else if (key.equals(INetPrefKeys.GATEWAY_FLAT_LINE_TIME)) {
                        final int to = parent.updatePref(key,
                                INetPrefKeys.DEFAULT_GW_FLAT_LINE_TIME);
                        PLogger.SET_PANTHR_GW.debug("flatline[{} -> {}]",
                                INetPrefKeys.DEFAULT_GW_FLAT_LINE_TIME, to);
                    }

                    //
                    // Multicast
                    //
                    else if (key.equals(INetPrefKeys.MULTICAST_DISABLED)) {
                        final boolean active = parent.updatePref(key, parent.isMulticastSuppressed);
                        PLogger.SET_PANTHR_MC.debug("suppress[{} -> {}]",
                                parent.isMulticastSuppressed, active);
                    }
                    else if (key.equals(INetPrefKeys.MULTICAST_HOST)) {
                        parent.updatePref(key, INetPrefKeys.DEFAULT_MULTICAST_HOST);
                        final String prev = (parent.multicastChannel == null) ? INetPrefKeys.DEFAULT_MULTICAST_HOST
                                : parent.multicastChannel.getLocalIpAddresses().get(0).toString();
                        final String host = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR_MC.debug("host[{} -> {}]", prev, host);
                    }
                    else if (key.equals(INetPrefKeys.MULTICAST_PORT)) {
                        final int port = parent
                                .updatePref(key, INetPrefKeys.DEFAULT_MULTICAST_PORT);
                        PLogger.SET_PANTHR_MC.debug("port[{} -> {}]",
                                INetPrefKeys.DEFAULT_MULTICAST_PORT,
                                port);
                    }
                    else if (key.equals(INetPrefKeys.MULTICAST_TTL)) {
                        final int ttl = parent.updatePref(key, INetPrefKeys.DEFAULT_MULTICAST_TTL);
                        PLogger.SET_PANTHR_MC.debug("ttl[{} -> {}]",
                                INetPrefKeys.DEFAULT_MULTICAST_TTL,
                                ttl);
                    }

                    //
                    // Reliable Multicast
                    //
                    else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_DISABLED)) {
                        final boolean active = parent.updatePref(key,
                                parent.isReliableMulticastSuppressed);
                        PLogger.SET_PANTHR_RMC.debug("suppress[{} -> {}]",
                                parent.isReliableMulticastSuppressed, active);
                    }
                    else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_HOST)) {
                        parent.updatePref(key, INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_HOST);
                        final String prev = (parent.reliableMulticastChannel == null)
                                ? INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_HOST
                                : parent.reliableMulticastChannel.getLocalIpAddresses().get(0)
                                        .toString();
                        final String host = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR_RMC.debug("host[{} -> {}]", prev, host);
                    }
                    else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_PORT)) {
                        final int port = parent.updatePref(key,
                                INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_PORT);
                        PLogger.SET_PANTHR_RMC.debug("port[{} -> {}]",
                                INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_PORT, port);
                    }
                    else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_TTL)) {
                        final int ttl = parent.updatePref(key,
                                INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_TTL);
                        PLogger.SET_PANTHR_MC.debug("ttl[{} -> {}]",
                                INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_TTL, ttl);
                    }

                    //
                    // Serial port
                    //
                    else if (key.equals(INetPrefKeys.SERIAL_DISABLED)) {
                        final boolean active = parent.updatePref(key, parent.isSerialSuppressed);
                        PLogger.SET_PANTHR_SERIAL.debug("suppress[{} -> {}]",
                                parent.isSerialSuppressed, active);
                    }
                    else if (key.equals(INetPrefKeys.SERIAL_DEVICE)) {
                        final String prev = parent.deviceId;
                        final String device = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR_SERIAL.debug("device[{} -> {}]", prev, device);
                    }
                    else if (key.equals(INetPrefKeys.SERIAL_BAUD_RATE)) {
                        final int prev = INetPrefKeys.DEFAULT_SERIAL_BAUD_RATE;
                        final int baud = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR_SERIAL.debug("baud[{} -> {}]", prev, baud);
                    }
                    else if (key.equals(INetPrefKeys.SERIAL_SLOT_NUMBER)) {
                        final int prev = INetPrefKeys.DEFAULT_SERIAL_SLOT_NUMBER;
                        final int slot = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR_SERIAL.debug("slot[{} -> {}]", prev, slot);
                    }
                    else if (key.equals(INetPrefKeys.SERIAL_RADIOS_IN_GROUP)) {
                        final int prev = INetPrefKeys.DEFAULT_SERIAL_RADIOS_IN_GROUP;
                        final int count = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR_SERIAL.debug("slot$[{} -> {}]", prev, count);
                    }
                    else if (key.equals(INetPrefKeys.SERIAL_SLOT_DURATION)) {
                        final int prev = INetPrefKeys.DEFAULT_SERIAL_SLOT_DURATION;
                        final int duration = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR_SERIAL.debug("slot@[{} -> {}]", prev, duration);
                    }
                    else if (key.equals(INetPrefKeys.SERIAL_TRANSMIT_DURATION)) {
                        final int prev = INetPrefKeys.DEFAULT_SERIAL_TRANSMIT_DURATION;
                        final int xmit = parent.updatePref(key, prev);
                        PLogger.SET_PANTHR_SERIAL.debug("slots%[{} -> {}]", prev, xmit);
                    }
                    else if (key.equals(Keys.UserKeys.UNIT)) {
                        logger.trace("global preference {} is not used", key);
                    }
                    else if (key.equals(Keys.UserKeys.CALLSIGN)) {
                        logger.trace("global preference {} is not used", key);
                    }
                    else if (key.equals(Keys.UserKeys.NAME)) {
                        logger.trace("global preference {} is not used", key);
                    }
                    else if (key.equals(Keys.MapKeys.TILE_DB_DIR)) {
                        logger.trace("global preference {} is not used", key);
                    }
                    else if (key.startsWith("transapps_settings_network_")) {
                        logger.trace("global preference {} is not used", key);
                    }
                    else {
                        logger.error("global preference {} is unknown", key);
                    }
                    return;
                }
            };

    final OnSharedPreferenceChangeListener ammoPreferenceChangeListener =
            new OnSharedPreferenceChangeListener()
            {
                final private NetworkManager parent = NetworkManager.this;

                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    logger.trace("::onSharedPreferenceChanged ammo {}", key);

                    try {

                        if (key.equals(INetDerivedKeys.WIFI_PREF_IS_ACTIVE)) {
                            logger.trace("unprocessed key {}", key);
                        }
                        else if (key.equals(INetDerivedKeys.PHYSICAL_LINK_PREF_IS_ACTIVE)) {
                            logger.trace("unprocessed key {}", key);
                        }
                        else if (key.equals(INetDerivedKeys.PHONE_PREF_IS_ACTIVE)) {
                            logger.trace("unprocessed key {}", key);
                        }
                        else if (key.equals(INetPrefKeys.JOURNAL_DISABLED)) {
                            parent.isJournalUserDisabled = prefs.getBoolean(key,
                                    parent.isJournalUserDisabled);
                            if (parent.isJournalUserDisabled)
                                parent.journalChannel.disable();
                            else
                                parent.journalChannel.enable();
                        }
                        else if (key.equals(INetPrefKeys.CORE_DEVICE_ID)) {
                            // handle network authentication group
                            parent.deviceId = prefs.getString(key, parent.deviceId);
                            parent.auth();
                        }
                        else if (key.equals(INetPrefKeys.CORE_OPERATOR_ID)) {
                            final String prev = (parent.operatorId == null)
                                    ? INetPrefKeys.DEFAULT_CORE_OPERATOR_ID : parent.operatorId;
                            final String id = parent.operatorId = prefs.getString(key, prev);
                            PLogger.SET_PANTHR.debug("operator[{} -> {}]", prev, id);

                            parent.refresh();
                            parent.auth();
                        }
                        else if (key.equals(INetPrefKeys.CORE_OPERATOR_KEY)) {
                            final String prev = (parent.operatorKey == null)
                                    ? INetPrefKeys.DEFAULT_CORE_OPERATOR_KEY : parent.operatorKey;
                            final String pass = parent.operatorKey = prefs.getString(key, prev);
                            PLogger.SET_PANTHR.debug("pass[{} -> {}]", prev, pass);

                            parent.auth();
                        }
                        else if (key.equals(INetPrefKeys.GATEWAY_DISABLED)) {
                            /*
                             * GATEWAY
                             */
                            if (prefs.getBoolean(key, INetPrefKeys.DEFAULT_GATEWAY_ENABLED)) {
                                parent.tcpChannel.disable();
                                parent.tcpMediaChannel.disable();
                            } else {
                                parent.tcpChannel.enable();
                                parent.tcpMediaChannel.enable();
                            }
                        }
                        else if (key.equals(INetPrefKeys.GATEWAY_HOST)) {
                            String gatewayHostname = prefs
                                    .getString(key, INetPrefKeys.DEFAULT_GATEWAY_HOST);
                            parent.tcpChannel.setHost(gatewayHostname);
                            parent.tcpMediaChannel.setHost(gatewayHostname);
                        }
                        else if (key.equals(INetPrefKeys.GATEWAY_PORT)) {
                            int gatewayPort = Integer.valueOf(prefs.getString(
                                    key, String.valueOf(INetPrefKeys.DEFAULT_GATEWAY_PORT)));
                            parent.tcpChannel.setPort(gatewayPort);
                            parent.tcpMediaChannel.setPort(gatewayPort);
                        }
                        else if (key.equals(INetPrefKeys.GATEWAY_TIMEOUT)) {
                            final Integer timeout = Integer.valueOf(prefs.getString(
                                    key, String.valueOf(INetPrefKeys.DEFAULT_GW_TIMEOUT)));
                            parent.tcpChannel.setSocketTimeout(timeout.intValue() * 1000);
                            parent.tcpMediaChannel.setSocketTimeout(timeout.intValue() * 1000);                            
                            // convert seconds into milliseconds
                        }
                        else if (key.equals(INetDerivedKeys.NET_CONN_PREF_SHOULD_USE)) {
                            // handle network connectivity group
                            // if (key.equals(INetPrefKeys.WIRED_PREF_DISABLED))
                            // {
                            // shouldUse(prefs);
                            // }
                            // if (key.equals(INetPrefKeys.WIFI_PREF_DISABLED))
                            // {
                            // shouldUse(prefs);
                            // }
                            logger.trace("explicit opererator reset on channel");
                            parent.networkingSwitch = true;

                            parent.tcpChannel.reset();
                            parent.tcpMediaChannel.reset();
                            parent.multicastChannel.reset();
                            parent.reliableMulticastChannel.reset();
                            parent.serialChannel.reset();
                        }
                        else if (key.equals(INetPrefKeys.GATEWAY_FLAT_LINE_TIME)) {
                            long flatLineTime = Integer.valueOf(prefs.getString(
                                    key, String.valueOf(INetPrefKeys.DEFAULT_GW_FLAT_LINE_TIME)));
                            parent.tcpChannel.setFlatLineTime(flatLineTime * 60 * 1000);
                            parent.tcpMediaChannel.setFlatLineTime(flatLineTime * 60 * 1000);                            
                            // convert from minutes to milliseconds
                        }
                        else if (key.equals(INetPrefKeys.MULTICAST_DISABLED)) {
                            //
                            // Multicast
                            //
                            if (prefs.getBoolean(key, INetPrefKeys.DEFAULT_MULTICAST_ENABLED)) {
                                parent.multicastChannel.disable();
                            } else {
                                parent.multicastChannel.enable();
                            }
                        }
                        else if (key.equals(INetPrefKeys.MULTICAST_HOST)) {
                            String ipAddress = prefs.getString(
                                    key, INetPrefKeys.DEFAULT_MULTICAST_HOST);
                            parent.multicastChannel.setHost(ipAddress);
                        }
                        else if (key.equals(INetPrefKeys.MULTICAST_PORT)) {
                            int port = Integer.parseInt(prefs.getString(
                                    key, String.valueOf(INetPrefKeys.DEFAULT_MULTICAST_PORT)));
                            parent.multicastChannel.setPort(port);
                        }
                        else if (key.equals(INetPrefKeys.MULTICAST_TTL)) {
                            int ttl = Integer.parseInt(prefs.getString(
                                    key, String.valueOf(INetPrefKeys.DEFAULT_MULTICAST_TTL)));
                            parent.multicastChannel.setTTL(ttl);
                        }

                        //
                        // Reliable Multicast
                        //
                        else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_DISABLED)) {
                            if (prefs.getBoolean(key,
                                    INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_ENABLED)) {
                                parent.reliableMulticastChannel.disable();
                            } else {
                                parent.reliableMulticastChannel.enable();
                            }
                        }
                        else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_HOST)) {
                            String ipAddress = prefs.getString(
                                    key, INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_HOST);
                            parent.reliableMulticastChannel.setHost(ipAddress);
                        }
                        else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_PORT)) {
                            int port = Integer.parseInt(prefs.getString(
                                    key,
                                    String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_PORT)));
                            parent.reliableMulticastChannel.setPort(port);
                        }
                        else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_TTL)) {
                            int ttl = Integer.parseInt(prefs.getString(
                                    key,
                                    String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_TTL)));
                            parent.reliableMulticastChannel.setTTL(ttl);
                        }

                        //
                        // Serial port
                        //
                        else if (key.equals(INetPrefKeys.SERIAL_DEVICE)) {
                            serialChannel.setDevice(prefs.getString(key,
                                    INetPrefKeys.DEFAULT_SERIAL_DEVICE));
                        }
                        else if (key.equals(INetPrefKeys.SERIAL_BAUD_RATE)) {
                            serialChannel.setBaudRate(Integer.parseInt(prefs.getString(key,
                                    String.valueOf(INetPrefKeys.DEFAULT_SERIAL_BAUD_RATE))));
                        }
                        else if (key.equals(INetPrefKeys.SERIAL_SLOT_NUMBER)) {
                            serialChannel.setSlotNumber(Integer.parseInt(prefs.getString(key,
                                    String.valueOf(INetPrefKeys.DEFAULT_SERIAL_SLOT_NUMBER))));
                        }
                        else if (key.equals(INetPrefKeys.SERIAL_RADIOS_IN_GROUP)) {
                            serialChannel.setRadiosInGroup(Integer.parseInt(prefs.getString(key,
                                    String.valueOf(INetPrefKeys.DEFAULT_SERIAL_RADIOS_IN_GROUP))));
                        }
                        else if (key.equals(INetPrefKeys.SERIAL_SLOT_DURATION)) {
                            serialChannel.setSlotDuration(Integer.parseInt(prefs.getString(key,
                                    String.valueOf(INetPrefKeys.DEFAULT_SERIAL_SLOT_DURATION))));
                        }
                        else if (key.equals(INetPrefKeys.SERIAL_TRANSMIT_DURATION)) {
                            serialChannel
                                    .setTransmitDuration(Integer.parseInt(prefs.getString(
                                            key,
                                            String.valueOf(INetPrefKeys.DEFAULT_SERIAL_TRANSMIT_DURATION))));
                        }
                        else if (key.equals(INetPrefKeys.SERIAL_SEND_ENABLED)) {
                            serialChannel.setSenderEnabled(prefs.getBoolean(key,
                                    !INetPrefKeys.DEFAULT_SERIAL_SEND_ENABLED));
                        }
                        else if (key.equals(INetPrefKeys.SERIAL_DISABLED)) {
                            if (prefs.getBoolean(key, INetPrefKeys.DEFAULT_SERIAL_ENABLED))
                                parent.serialChannel.disable();
                            else
                                parent.serialChannel.enable();
                        }
                        else {
                            logger.warn("shared preference key {} is unknown", key);
                        }
                    } catch (NumberFormatException ex) {
                        logger.error("invalid number value for {}", key);
                    } catch (ClassCastException ex) {
                        logger.error("invalid cast for {}", key);
                    }

                    return;
                }
            };

    // ===========================================================
    // Protocol Buffers Methods
    // ===========================================================

    /**
     * Authentication requests are sent via TCP. They are primarily concerned
     * with obtaining the sessionId.
     */
    public AmmoMessages.MessageWrapper.Builder buildAuthenticationRequest() {
        logger.trace("::buildAuthenticationRequest");

        final AmmoMessages.MessageWrapper.Builder mw =
                AmmoMessages.MessageWrapper.newBuilder();
        mw.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
        mw.setSessionUuid(sessionId);

        final AmmoMessages.AuthenticationMessage.Builder authreq =
                AmmoMessages.AuthenticationMessage.newBuilder();
        authreq.setDeviceId(
                UniqueIdentifiers.device(this.context))
                .setUserId(this.operatorId).setUserKey(this.operatorKey);

        mw.setAuthenticationMessage(authreq);
        mw.setMessagePriority(AmmoGatewayMessage.PriorityLevel.AUTH.v);
        return mw;
    }

    // ===========================================================
    // Gateway Communication Methods
    // ===========================================================

    /**
     * Used to send a message to the android gateway plugin. This takes an
     * argument indicating the channel type [tcpchannel, multicast,
     * reliablemulticast, serial, journal].
     * 
     * @param outstream
     * @param size
     * @param payload_checksum
     * @param message
     */
    @Override
    public DisposalState sendRequest(AmmoGatewayMessage agm, String channelName) {
        logger.info("Ammo sending Request size ({}) priority({}) to Channel {}",
                new Object[] {
                        agm.size, agm.priority, channelName
                });
        // agm.setSessionUuid( sessionId );
        if (!netChannelMap.containsKey(channelName))
            return DisposalState.REJECTED;
        final NetChannel channel = netChannelMap.get(channelName);
        if (!channel.isConnected())
            return DisposalState.PENDING;
        return channel.sendRequest(agm);
    }

    public ChannelStatus checkChannel(String channelName) {
        if (!netChannelMap.containsKey(channelName))
            return ChannelStatus.DOWN;

        final NetChannel channel = netChannelMap.get(channelName);
        if (channel.isBusy()) // this is to improve performance
            return ChannelStatus.FULL;
        if (!channel.isConnected())
            return ChannelStatus.DOWN;
        return ChannelStatus.READY;
    }

    abstract public class TotalChannel implements INetChannel {
    }

    // ===========================================================
    // Helper classes
    // ===========================================================

    /**
     * Processes and delivers messages received from the gateway.
     * 
     * @param instream
     * @return was the message clean (true) or garbled (false).
     */
    public boolean deliver(AmmoGatewayMessage agm) {
        return distThread.distributeResponse(agm);
    }

    /**
     * This method is called just prior to onDestroy or when the service is
     * being intentionally shut down.
     */
    public void teardown() {
        logger.trace("Tearing down NPS");
        this.tcpChannel.disable();
        this.tcpMediaChannel.disable();
        this.multicastChannel.disable();
        this.reliableMulticastChannel.disable();
        this.serialChannel.disable();

        for (NetChannel channel : this.registeredChannels) {
            channel.disable();
        }
    }

    /**
     * Check to see if there are any open connections.
     * 
     * @return
     */
    public boolean isConnected() {
        boolean any = (tcpChannel.isConnected()
        		|| tcpMediaChannel.isConnected()
                || multicastChannel.isConnected()
                || reliableMulticastChannel.isConnected()
                || ((serialChannel != null) && serialChannel.isConnected()));

        for (NetChannel channel : this.registeredChannels) {
            any = any || channel.isConnected();
        }
        logger.debug("::isConnected ? {}", any);
        return any;
    }

    /**
     * For the following methods there is an expectation that the connection has
     * been pre-verified.
     */
    public boolean auth() {
        logger.trace("::authenticate");
        if (!this.isConnected()) {
            logger.warn("no active connection for authentication");
            return false;
        }
        if (this.deviceId == null) {
            logger.warn("no device for authentication");
            return false;
        }
        if (this.operatorId == null) {
            logger.warn("no named operator for authentication");
            return false;
        }
        if (this.operatorKey == null) {
            logger.warn("no operator key for authentication");
            return false;
        }

        /** Message Building */
        final AmmoMessages.MessageWrapper.Builder mwb = buildAuthenticationRequest();
        final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(mwb, this);
        agmb.isGateway(true);
        switch (sendRequest(agmb.build(), DistributorPolicy.DEFAULT)) {
            case SENT:
            case DELIVERED:
            case QUEUED:
                return true;
            default:
                return false;
        }
    }

    /**
     * This routine is called when a message is successfully sent or when it is
     * discarded. It is currently unused, but we may want to do something with
     * it in the future.
     */
    @Override
    public boolean ack(String channel, DisposalState status) {
        return false;
    }

    /**
     *  The channel lets the NetworkManager know that the channel was
     *  successfully authorized by calling this method.
     */
    public void authorizationSucceeded(NetChannel channel, AmmoGatewayMessage agm) {
        // HACK! Fixme
        final AmmoMessages.MessageWrapper mw;
        try {
            mw = AmmoMessages.MessageWrapper.parseFrom(agm.payload);
        } catch (InvalidProtocolBufferException ex) {
            logger.error("parsing payload failed", ex);
            return;
        }
        if (mw == null) {
            logger.error("mw was null!");
            return;
        }

        PreferenceManager
                .getDefaultSharedPreferences(this.context)
                .edit()
                .putBoolean(INetDerivedKeys.NET_CONN_PREF_IS_ACTIVE, true)
                .commit();
        sessionId = mw.getSessionUuid();

        logger.trace("authentication complete, repost subscriptions and pending data {}", channel);
        this.distThread.onChannelChange(this.context, channel.name, ChannelChange.ACTIVATE);

        logger.trace("authentication complete inform applications : ");
        // TBD SKN - this should not be sent now ...
        // broadcast login event to apps ...
        Intent loginIntent = new Intent(IntentNames.AMMO_LOGIN);
        loginIntent.putExtra("operatorId", this.operatorId);
        this.context.sendBroadcast(loginIntent);

        // broadcast gateway connected to apps ...
        loginIntent = new Intent(IntentNames.AMMO_CONNECTED);
        loginIntent.putExtra("channel", channel.name);
        this.context.sendBroadcast(loginIntent);

    }

    public void receivedCorruptPacketOnSerialChannel()
    {
        serialChannel.receivedCorruptPacket();
    }

    /**
     * Deal with the status of the connection changing. Report the status to the
     * application who acts as a broker.
     */
    @Override
	public void statusChange(NetChannel channel, int lastConnStatus,
			int connStatus, int lastSendStatus, int sendStatus,
			int lastRecvStatus, int recvStatus) {
        if (logger.isDebugEnabled()) {
            logger.debug("change channel=[{}] status=[{}]", channel.name,
                    NetChannel.showState(connStatus));
        }

        final ModelChannel modelChannel = modelChannelMap.get(channel.name);
        if (modelChannel == null) {
            logger.debug("no model for channel=[{}]", channel.name);
        } else {
			modelChannel.setStatus(new int[] { connStatus, sendStatus, recvStatus });
		}

		if (lastConnStatus != connStatus) {
			final Intent broadcastIntent = new Intent(
					AmmoIntents.AMMO_ACTION_CONNECTION_STATUS_CHANGE);
			broadcastIntent.putExtra(AmmoIntents.EXTRA_CHANNEL, channel.name);
			broadcastIntent.putExtra(AmmoIntents.EXTRA_CONNECT_STATUS, connStatus);
			this.context.sendBroadcast(broadcastIntent);
        }

        switch (connStatus) {
            case NetChannel.CONNECTED:
                if (channel.isAuthenticatingChannel())
                    break;
            case NetChannel.READY:
            case NetChannel.SENDING:
            case NetChannel.TAKING:
                this.distThread.onChannelChange(this.context, channel.name,
                        ChannelChange.ACTIVATE);
                break;

            case NetChannel.BUSY:
            case NetChannel.DISCONNECTED:
            case NetChannel.DISABLED:
            default:
                this.distThread.onChannelChange(this.context, channel.name,
                        ChannelChange.DEACTIVATE);
        }

        final Intent broadcastIntent = new Intent(AmmoIntents.AMMO_ACTION_GATEWAY_STATUS_CHANGE);
        this.context.sendBroadcast(broadcastIntent);
    }

    private void netlinkStatusChanged() {
        final Intent broadcastIntent = new Intent(
                AmmoIntents.AMMO_ACTION_NETLINK_STATUS_CHANGE);
        this.context.sendBroadcast(broadcastIntent);
    }

    public boolean isWiredLinkUp() {
        return mNetlinks.get(linkTypes.WIRED.value).isLinkUp();
    }

    public boolean isWifiLinkUp() {
        return mNetlinks.get(linkTypes.WIFI.value).isLinkUp();
    }

    public boolean is3GLinkUp() {
        return mNetlinks.get(linkTypes.MOBILE_3G.value).isLinkUp();
    }

    public boolean isAnyLinkUp() {
        return isWiredLinkUp() || isWifiLinkUp() || is3GLinkUp();
    }

    static private final Map<String, ModelChannel> modelChannelMap;
    // Network Channels
    final private TcpChannel tcpChannel =
            TcpChannel.getInstance(ChannelFilter.GATEWAY, this);
    final private MulticastChannel multicastChannel =
            MulticastChannel.getInstance(ChannelFilter.MULTICAST, this);
    final private ReliableMulticastChannel reliableMulticastChannel =
            ReliableMulticastChannel.getInstance(ChannelFilter.RELIABLE_MULTICAST, this, this.context);
    final private JournalChannel journalChannel =
            JournalChannel.getInstance(ChannelFilter.JOURNAL, this);
    private SerialChannel serialChannel = null;
    
    final private TcpChannel tcpMediaChannel =
            TcpChannel.getInstance(ChannelFilter.GATEWAYMEDIA, this);

    final public List<NetChannel> registeredChannels =
            new ArrayList<NetChannel>();

    /**
     * No channels can be registered until after onCreate() The channel must be
     * created in a disabled state.
     * <p>
     * Properly the channel should be enabled only after any relevant
     * preferences have been read. That being the case registration should be
     * after any relevant preferences are loaded. The channel is suppressed by
     * simply not registering.
     * 
     * @param channel
     */
    public void registerChannel(NetChannel channel) {
        this.registeredChannels.add(channel);
        NetworkManager.netChannelMap.put(channel.name, channel);

        // TODO load any preferences here.

        channel.reset();
        channel.enable();
    }

    static final private Map<String, NetChannel> netChannelMap;

    static {
        modelChannelMap = new HashMap<String, ModelChannel>();
        netChannelMap = new HashMap<String, NetChannel>();
    }

    static void addChannel() {

    }

    private final List<Netlink> mNetlinks = new ArrayList<Netlink>();

    public List<ModelChannel> getGatewayList() {
        return new ArrayList<ModelChannel>(modelChannelMap.values());
    }

    public List<Netlink> getNetlinkList() {
        return mNetlinks;
    }

    public DistributorDataStore store() {
        return this.distThread.store();
    }

    /**
     * This should handle the link state behavior. This is really the main job
     * of the Network service; matching up links with channels.
     */
    private class NetworkBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent aIntent) {
            final String action = aIntent.getAction();
            logger.debug("onReceive: {}", action);

            if (AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE.equals(action)) {
                logger.trace("Ether Link state changed");
                int state = aIntent.getIntExtra("state", 0);

                // Should we be doing this here?
                // It's not parallel with the wifi and 3G below.
                if (state != 0) {
                    switch (state) {
                        case AmmoIntents.LINK_UP:
                            logger.trace("onReceive: Link UP {}", action);
                            tcpChannel.linkUp(null);
                            multicastChannel.linkUp(null);
                            reliableMulticastChannel.linkUp(null);
                            tcpMediaChannel.linkUp(null);
                            for (NetChannel channel : NetworkManager.this.registeredChannels) {
                                channel.linkUp(null);
                            }

                            break;
                        case AmmoIntents.LINK_DOWN:
                            logger.trace("onReceive: Link DOWN {}", action);
                            tcpChannel.linkDown(null);
                            tcpMediaChannel.linkDown(null);
                            multicastChannel.linkDown(null);
                            reliableMulticastChannel.linkDown(null);
                            for (NetChannel channel : NetworkManager.this.registeredChannels) {
                                channel.linkDown(null);
                            }
                            break;
                    }
                }

                // This intent comes in for both wired and wifi.
                mNetlinks.get(linkTypes.WIRED.value).updateStatus();
                mNetlinks.get(linkTypes.WIFI.value).updateStatus();
                netlinkStatusChanged();
            } else if (AmmoIntents.ACTION_SERIAL_LINK_CHANGE.equals(action)) {
                int state = aIntent.getIntExtra("state", 0);
                String devname = aIntent.getStringExtra("devname");
                logger.error("Serial link changed. devname: {}, state: {}", devname, state);

                if (state == 1)
                    serialChannel.linkUp(devname);
                else
                    serialChannel.linkDown(devname);

            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)
                    || WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
                    || WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION.equals(action)
                    || WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                logger.trace("WIFI state changed");
                mNetlinks.get(linkTypes.WIRED.value).updateStatus();
                mNetlinks.get(linkTypes.WIFI.value).updateStatus();
                netlinkStatusChanged();
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                logger.trace("3G state changed");
                mNetlinks.get(linkTypes.MOBILE_3G.value).updateStatus();
                netlinkStatusChanged();
            } else if (Intent.ACTION_TIME_CHANGED.equals(action)) {
                serialChannel.systemTimeChange();
            }

            // if (INetworkService.ACTION_RECONNECT.equals(action)) {
            // //NetworkManager.this.connectChannels(true);
            // return;
            // }
            // if (INetworkService.ACTION_DISCONNECT.equals(action)) {
            // //NetworkManager.this.disconnectChannels();
            // return;
            // }

            return;
        }
    }

    /**

	 */
    private class ReadyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context aContext, final Intent aIntent) {

            final String action = aIntent.getAction();

            logger.trace("::onReceive: {}", action);
            checkResourceStatus(aContext);

            if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            }
        }

        public void checkResourceStatus(final Context aContext) { //
            logger.trace("::checkResourceStatus");
            {
                final WifiManager wm = (WifiManager) aContext
                        .getSystemService(Context.WIFI_SERVICE);
                final int wifiState = wm.getWifiState(); // TODO check for
                                                         // permission or catch
                                                         // error
                logger.trace("wifi state={}", wifiState);

                final TelephonyManager tm = (TelephonyManager) aContext.getSystemService(
                        Context.TELEPHONY_SERVICE);
                final int dataState = tm.getDataState(); // TODO check for
                                                         // permission or catch
                                                         // error
                logger.trace("telephone data state={}", dataState);

                mNetworkConnected = wifiState == WifiManager.WIFI_STATE_ENABLED
                        || dataState == TelephonyManager.DATA_CONNECTED;
                logger.trace("mConnected={}", mNetworkConnected);
            }
            {
                final String state = Environment.getExternalStorageState();

                logger.trace("sdcard state={}", state);
                mSdCardAvailable = Environment.MEDIA_MOUNTED.equals(state);
                logger.trace("mSdcardAvailable={}", mSdCardAvailable);
            }
        }
    }

	public String distributeRequest(AmmoRequest request) {
		return this.distThread.distributeRequest(request);
	}

	public void reloadGlobalSettings() {
		this.globalSettings.reload();
	}
}

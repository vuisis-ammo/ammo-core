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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import transapps.settings.Keys;
import transapps.settings.Settings;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IntentNames;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.IDistributorService;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelDisposal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelStatus;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy;
import edu.vu.isis.ammo.core.distributor.DistributorThread;
import edu.vu.isis.ammo.core.model.Channel;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.Multicast;
import edu.vu.isis.ammo.core.model.ReliableMulticast;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.model.PhoneNetlink;
import edu.vu.isis.ammo.core.model.Serial;
import edu.vu.isis.ammo.core.model.WifiNetlink;
import edu.vu.isis.ammo.core.model.WiredNetlink;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.IChannelManager;
import edu.vu.isis.ammo.core.network.INetChannel;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.JournalChannel;
import edu.vu.isis.ammo.core.network.MulticastChannel;
import edu.vu.isis.ammo.core.network.ReliableMulticastChannel;
import edu.vu.isis.ammo.core.network.SerialChannel;
import edu.vu.isis.ammo.core.network.NetChannel;
import edu.vu.isis.ammo.core.network.TcpChannel;
import edu.vu.isis.ammo.core.pb.AmmoMessages;

import edu.vu.isis.ammo.core.receiver.CellPhoneListener;
import edu.vu.isis.ammo.core.receiver.WifiReceiver;
import edu.vu.isis.ammo.util.IRegisterReceiver;
import edu.vu.isis.ammo.util.UniqueIdentifiers;

/**
 * Network Service is responsible for all networking between the core
 * application and the server. Currently, this service implements a UDP
 * connection for periodic data updates and a long-polling TCP connection for
 * event driven notifications.
 * 
 * 
 * The AmmoService is responsible for prioritizing and serializing
 * requests for data communications between distributed application databases. 
 * The AmmoService issues calls to the AmmoService for updates and then writes the
 * results to the correct content provider using the deserialization mechanism
 * defined by each content provider.
 * 
 * Any activity or application wishing to send data via the AmmoService
 * should use one of the AmmoRequest API methods for communication between
 * said application and AmmoCore.
 * 
 * Any activity or application wishing to receive updates when a content
 * provider has been modified can register via a custom ContentObserver
 * subclass.
 * 
 * The real work is delegated to the Distributor Thread, which maintains a queue.
 * 
 */
public class AmmoService extends Service implements
OnSharedPreferenceChangeListener, INetworkService,
INetworkService.OnSendMessageHandler, IChannelManager {
	// ===========================================================
	// Constants
	// ===========================================================
	private static final Logger logger = LoggerFactory.getLogger("ammo-service");

	public static final Intent LAUNCH = new Intent("edu.vu.isis.ammo.core.distributor.AmmoService.LAUNCH");
	public static final String BIND = "edu.vu.isis.ammo.core.distributor.AmmoService.BIND";
	public static final String PREPARE_FOR_STOP = "edu.vu.isis.ammo.core.distributor.AmmoService.PREPARE_FOR_STOP";
	public static final String SEND_SERIALIZED = "edu.vu.isis.ammo.core.distributor.AmmoService.SEND_SERIALIZED";

	/**
	 * The channel status map
	 * It should not be changed by the main thread.
	 */
	public enum ChannelChange {
		ACTIVATE(1), DEACTIVATE(2), REPAIR(3);

		final public int o; // ordinal

		private ChannelChange(int o) {
			this.o = o;
		}
		public int cv() {
			return this.o;
		}
		static public ChannelChange getInstance(int ordinal) {
			return ChannelChange.values()[ordinal];
		}
		public String q() {
			return new StringBuilder().append("'").append(this.o).append("'").toString();
		}
	}
	
	public static enum NPSReturnCode {
		NO_CONNECTION, SOCKET_EXCEPTION, UNKNOWN, BAD_MESSAGE, OK
	};

	public static enum linkTypes {
		// existed already, added as enum
		WIRED(0), // any wired connection
		WIFI(1), // 802.11 (a/b/g/n)
		MOBILE_3G(2), // phone 3G
		// added in for future expandability
		MOBILE_2G(3), // phone 2G
		MOBILE_4G(4), // phone 4G
		BLUETOOTH(5), // bluetooth(R)
		NFC(6), // near field communication
		IR(7); // infrared
		final public int value;
		private linkTypes(int num) {
			this.value = num;
		}
	}

	public static final String SIZE_KEY = "sizeByteArrayKey";
	public static final String CHECKSUM_KEY = "checksumByteArrayKey";

	public enum Carrier {
		UDP, TCP
	}

	// Interfaces
	

	// ===========================================================
	// Fields
	// ===========================================================

	private String sessionId = "";
	private String deviceId = INetPrefKeys.DEFAULT_CORE_DEVICE_ID;
	private String operatorId = INetPrefKeys.DEFAULT_CORE_OPERATOR_ID;
	private String operatorKey = INetPrefKeys.DEFAULT_CORE_OPERATOR_KEY;

	// isJournalUserDisabled
	private boolean isJournalUserDisabled = INetPrefKeys.DEFAULT_JOURNAL_DISABLED;

	// Determine if the connection is enabled
	private boolean isGatewayUserDisabled = INetPrefKeys.DEFAULT_GATEWAY_DISABLED;
	private boolean isMulticastUserDisabled = INetPrefKeys.DEFAULT_MULTICAST_DISABLED;
	private boolean isReliableMulticastUserDisabled = INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_DISABLED;
	private boolean isSerialUserDisabled = INetPrefKeys.DEFAULT_SERIAL_DISABLED;
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

    public String getOperatorId() { return operatorId; }
	
	private NetworkBroadcastReceiver myNetworkReceiver = null;
	
	private DistributorThread distThread;


	private TelephonyManager tm;
	private CellPhoneListener cellPhoneListener;
	private WifiReceiver wifiReceiver;

	private ReadyBroadcastReceiver mReadyResourceReceiver = null;
	private boolean mNetworkConnected = false;
	private boolean mSdCardAvailable = false;

	// ===========================================================
	// AIDL Implementation
	// ===========================================================

	public class DistributorServiceAidl extends IDistributorService.Stub {
		@Override
		public String makeRequest(AmmoRequest request) throws RemoteException {
			if (request == null) {
				logger.info("bad request");
				return null;
			}
			logger.trace("make request {}", request.action.toString());
			return AmmoService.this.distThread.distributeRequest(request);
		}
		@Override
		public AmmoRequest recoverRequest(String uuid) throws RemoteException {
			logger.trace("recover data request {}", uuid);
			return null;
		}
		
		public AmmoService getService() {
			logger.trace("MyBinder::getService");
			return AmmoService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		logger.trace("client binding...");
		return new DistributorServiceAidl();
	}

	// ===========================================================
	// Lifecycle
	// ===========================================================

	private AmmoCoreApp application;
	
	private IRegisterReceiver mReceiverRegistrar = null;

	private DistributorPolicy policy;
	public DistributorPolicy policy() { return this.policy; }


	@SuppressWarnings("unused")
	private AmmoCoreApp getApplicationEx() {
		if (this.application == null)
			this.application = (AmmoCoreApp) this.getApplication();
		return this.application;
	}

	/**
	 * In order for the service to be shutdown cleanly the 'serviceStart()'
	 * method may be used to prepare_for_stop, it will be stopped shortly and it
	 * needs to have some things done before that happens.
	 *
	 * When the user changes the configuration 'startService()' is run to change
	 * the settings.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		logger.info("::onStartCommand {}", intent);
		// If we get this intent, unbind from all services 
		// so the service can be stopped.
		if (intent != null) {
			final String action = intent.getAction();
			if (action != null) {
				if (action.equals(AmmoService.PREPARE_FOR_STOP)) {
					this.teardown();
					this.stopSelf();
					return START_NOT_STICKY;
				}
				if (action.equals("edu.vu.isis.ammo.api.MAKE_REQUEST")) {
					try {
						final AmmoRequest request = intent.getParcelableExtra("request");
						if (request == null) {
							logger.info("bad request intent {}", intent);
							return START_NOT_STICKY;
						}
						final String result = this.distThread.distributeRequest(request);
						logger.info("distributing {}", result);
					} catch (ArrayIndexOutOfBoundsException ex) {
						logger.error("could not unmarshall the ammo request parcel");
					}
					return START_NOT_STICKY;
				}
				if (action.equals("edu.vu.isis.ammo.AMMO_HARD_RESET")) {
					this.refresh();
					return START_NOT_STICKY;
				}
			}
			logger.info("::onStartCommand {}", intent);
		} 
		
		logger.info("started");
		return START_STICKY;
	}

	private PhoneStateListener mListener;
	
	private SharedPreferences globalSettings;  // from tasettings
	private SharedPreferences localSettings;   // local copy

	/**
	 * When the service is first created, we should grab the IP and Port values
	 * from the SystemPreferences.
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		logger.info("::onCreate");
		final Context context = this.getBaseContext();

		// We need a context, so do this here instead of in the channel.
		createReliableMulticastConfigFile( context, "udp.xml" );
		
		// set up the worker thread
		this.distThread = new DistributorThread(this.getApplicationContext());
		this.distThread.execute(this);
		// Initialize our receivers/listeners.
		/*
         wifiReceiver = new WifiReceiver();
         cellPhoneListener = new CellPhoneListener(this);
         tm = (TelephonyManager) this .getSystemService(Context.TELEPHONY_SERVICE);
         tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_DATA_ACTIVITY | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
		 */

		// Listen for changes to resource availability
		this.mReadyResourceReceiver = new ReadyBroadcastReceiver();
		this.mReceiverRegistrar = new IRegisterReceiver() {
			@Override
			public Intent registerReceiver(final BroadcastReceiver aReceiver, final IntentFilter aFilter) {
				return AmmoService.this.registerReceiver(aReceiver, aFilter);
			}

			@Override
			public void unregisterReceiver(final BroadcastReceiver aReceiver) {
				AmmoService.this.unregisterReceiver(aReceiver);
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

		mReadyResourceReceiver.checkResourceStatus(this);

		this.policy = DistributorPolicy.newInstance(context);
		

		this.globalSettings = new Settings(context);
		this.globalSettings.registerOnSharedPreferenceChangeListener(this);
		
		this.localSettings = PreferenceManager.getDefaultSharedPreferences(this);
		this.localSettings.registerOnSharedPreferenceChangeListener(this);

		serialChannel = new SerialChannel( "serial",  this, getBaseContext() );
		
		gChannelMap.put("default", this.tcpChannel);
		gChannelMap.put(this.tcpChannel.name, this.tcpChannel);
		gChannelMap.put(this.multicastChannel.name, this.multicastChannel);
		gChannelMap.put(this.reliableMulticastChannel.name, this.reliableMulticastChannel);
		gChannelMap.put(this.journalChannel.name, this.journalChannel);
		gChannelMap.put(this.serialChannel.name, this.serialChannel);

		gChannels.put(this.tcpChannel.name, Gateway.getInstance(getBaseContext()));
		gChannels.put(this.multicastChannel.name, Multicast.getInstance(getBaseContext()));
		gChannels.put(this.reliableMulticastChannel.name, ReliableMulticast.getInstance(getBaseContext()));
		gChannels.put(this.serialChannel.name, Serial.getInstance(getBaseContext()));

		mNetlinks.add(WifiNetlink.getInstance(getBaseContext()));
		mNetlinks.add(WiredNetlink.getInstance(getBaseContext()));
		mNetlinks.add(PhoneNetlink.getInstance(getBaseContext()));

		// FIXME: find the appropriate time to release() the multicast lock.
		logger.error("Acquiring multicast lock()");
		WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		WifiManager.MulticastLock multicastLock =
            wm.createMulticastLock("mydebuginfo");
		multicastLock.acquire();
		logger.error("...acquired multicast lock()");

		// no point in enabling the socket until the preferences have been read
		this.tcpChannel.disable();
		this.multicastChannel.disable();
		this.reliableMulticastChannel.disable();
        // The serial channel is created in a disabled state.
		{
		   final String globalId = this.globalSettings.getString(
				   Keys.UserKeys.USERNAME,
				   Keys.UserKeys.DEFAULT_USERNAME);
		   if (globalId == null) {
				final Editor editor = this.localSettings.edit();
				editor.putString(INetPrefKeys.CORE_OPERATOR_ID, 
						         INetPrefKeys.DEFAULT_CORE_OPERATOR_ID );
				editor.commit();
		   } else {
			    final Editor editor = this.localSettings.edit();
				editor.putString(INetPrefKeys.CORE_OPERATOR_ID, globalId );
				editor.commit();
		   }
		}
		this.acquirePreferences();
		
		if (this.networkingSwitch && ! this.isGatewayUserDisabled) {
			this.tcpChannel.enable();
		}
		if (this.networkingSwitch && ! this.isMulticastUserDisabled) {
			this.multicastChannel.enable();
			this.multicastChannel.reset(); // This starts the connector thread.
		}
		if (this.networkingSwitch && ! this.isReliableMulticastUserDisabled) {
			this.reliableMulticastChannel.enable();
			this.reliableMulticastChannel.reset(); // This starts the connector thread.
		}

		if (this.networkingSwitch && ! this.isSerialUserDisabled) {
			this.serialChannel.enable();
                }

		this.myNetworkReceiver = new NetworkBroadcastReceiver();

		final IntentFilter networkFilter = new IntentFilter();
		networkFilter.addAction(INetworkService.ACTION_RECONNECT);
		networkFilter.addAction(INetworkService.ACTION_DISCONNECT);

		networkFilter.addAction(AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE);
		networkFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		networkFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		networkFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		networkFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		networkFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

		this.mReceiverRegistrar.registerReceiver(this.myNetworkReceiver, networkFilter);

		this.mListener = new PhoneStateListener() {
			public void onDataConnectionStateChanged(int state) {
				logger.info("PhoneReceiver::onCallStateChanged()");
				mNetlinks.get(linkTypes.MOBILE_3G.value).updateStatus();
				netlinkStatusChanged();
			}
		};
		final TelephonyManager tm = (TelephonyManager) getBaseContext()
				.getSystemService(Context.TELEPHONY_SERVICE);
		tm.listen(mListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

		final Intent loginIntent = new Intent(IntentNames.AMMO_LOGIN);
		
		loginIntent.putExtra("operatorId", this.operatorId);
		this.sendBroadcast(loginIntent);
		
		this.refresh(); // refresh subscribe and retrieval tables
	}
	
	/* FIXME: this probably needs to happen differently. 
	 Fred and Demetri discussed it and we need to add a flag to the intent sent
	 below so that the receiver knows done initializing mcast channel
	 - now fire up the AMMO_LOGIN intent to force apps to register their subscriptions
	 */
	private void refresh() {	
		logger.info("Forcing applications to register their subscriptions");
		
		this.distThread.clearTables();
		
		// broadcast login event to apps ...
		final Intent loginIntent = new Intent(IntentNames.AMMO_READY);
		loginIntent.addCategory(IntentNames.RESET_CATEGORY);

		this.acquirePreferences();
		this.tcpChannel.reset();
		this.multicastChannel.reset(); 
		this.reliableMulticastChannel.reset(); 
		this.serialChannel.reset(); 
		
		loginIntent.putExtra("operatorId", this.operatorId);
		this.sendBroadcast(loginIntent);
	}

	@Override
	public void onDestroy() {
		logger.warn("::onDestroy");
		this.tcpChannel.disable();
		this.multicastChannel.disable();
		this.reliableMulticastChannel.disable();
		this.journalChannel.close();
		this.serialChannel.disable();
		
		if (this.tm != null) 
			this.tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_NONE);
		
		if (this.wifiReceiver != null) 
			this.wifiReceiver.setInitialized(false);

		if (this.mReceiverRegistrar != null) {
			this.mReceiverRegistrar.unregisterReceiver(this.myNetworkReceiver);
			this.mReceiverRegistrar.unregisterReceiver(this.mReadyResourceReceiver);
		}

		super.onDestroy();
	}

	// ===========================================================
	// Networking
	// ===========================================================

	/**
	 * The operator id may be set from the global context.
	 * 
	 */
	private void refreshOperatorId() {
		this.operatorId = 
				this.localSettings.getString(INetPrefKeys.CORE_OPERATOR_ID, 
				this.operatorId);
	}
	
	/** 
	 * Get the preference specified, preferring the global value over the local.
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	private String getAgPref(final String key, final String value) {
		final String local = this.localSettings.getString(key, value);
		return this.globalSettings.getString(key, local);
	}
	private boolean getAgPref(final String key, final boolean value) {
		final boolean local = this.localSettings.getBoolean(key, value);
		return this.globalSettings.getBoolean(key, local);
	}

		
	/**
	 * Read the system preferences for the network connection information.
	 */
	private void acquirePreferences() {
		logger.info("::acquirePreferences");
		
		this.networkingSwitch = this.localSettings
				.getBoolean(INetDerivedKeys.NET_CONN_PREF_SHOULD_USE, 
						    this.networkingSwitch);

		this.deviceId = this.localSettings
				.getString(INetPrefKeys.CORE_DEVICE_ID, 
						   this.deviceId);
		refreshOperatorId();
		this.operatorKey = this
				.getAgPref(INetPrefKeys.CORE_OPERATOR_KEY, 
				           this.operatorKey);

		// JOURNAL
		this.isJournalUserDisabled = this
        		.getAgPref(INetPrefKeys.JOURNAL_DISABLED, 
        		           ! this.isJournalUserDisabled);
			
		// GATEWAY
		this.isGatewayUserDisabled = this
				.getAgPref(INetPrefKeys.GATEWAY_DISABLED, 
				           INetPrefKeys.DEFAULT_GATEWAY_DISABLED);
		
		final String gatewayHostname = this
				.getAgPref(INetPrefKeys.GATEWAY_HOST, 
				           INetPrefKeys.DEFAULT_GATEWAY_HOST);
		this.tcpChannel.setHost(gatewayHostname);

		final String gatewayPortStr = this.localSettings
				.getString(INetPrefKeys.GATEWAY_PORT,
				           String.valueOf(INetPrefKeys.DEFAULT_GATEWAY_PORT));
		int gatewayPort = Integer.valueOf(gatewayPortStr);
		this.tcpChannel.setPort(gatewayPort);

		final String flatLineTimeStr = this.localSettings.
				getString(INetPrefKeys.GATEWAY_FLAT_LINE_TIME, 
						  String.valueOf(INetPrefKeys.DEFAULT_GW_FLAT_LINE_TIME));
		final long flatLineTime = Integer.valueOf(flatLineTimeStr);
		this.tcpChannel.setFlatLineTime(flatLineTime * 60 * 1000); 
		// convert minutes into milliseconds

		/*
		 * Multicast
		 */
		this.isMulticastUserDisabled = this
        		.getAgPref(INetPrefKeys.MULTICAST_DISABLED, 
        		           INetPrefKeys.DEFAULT_MULTICAST_DISABLED);
		final String multicastHost = this.localSettings
				.getString(INetPrefKeys.MULTICAST_HOST, 
				           INetPrefKeys.DEFAULT_MULTICAST_HOST);
		int multicastPort = Integer.parseInt(this.localSettings
				.getString(INetPrefKeys.MULTICAST_PORT, 
				           INetPrefKeys.DEFAULT_MULTICAST_PORT));
		long multicastFlatLine = Long.parseLong(this.localSettings
				.getString(INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT,
				           INetPrefKeys.DEFAULT_MULTICAST_NET_CONN));
		int multicastIdleTime = Integer.parseInt(this.localSettings
				.getString(INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT,
				           INetPrefKeys.DEFAULT_MULTICAST_IDLE_TIME));
		int multicastTTL = Integer.parseInt(this.localSettings
				.getString(INetPrefKeys.MULTICAST_TTL,
				           INetPrefKeys.DEFAULT_MULTICAST_TTL));
		this.multicastChannel.setHost(multicastHost);
		this.multicastChannel.setPort(multicastPort);
		this.multicastChannel.setFlatLineTime(multicastFlatLine);
		this.multicastChannel.setSocketTimeout(multicastIdleTime);
		this.multicastChannel.setTTL(multicastTTL);

		/*
		 * Reliable Multicast
		 */
		this.isReliableMulticastUserDisabled = this
        		.getAgPref(INetPrefKeys.RELIABLE_MULTICAST_DISABLED, 
        		           INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_DISABLED);
		final String reliableMulticastHost = this.localSettings
				.getString(INetPrefKeys.RELIABLE_MULTICAST_HOST, 
				           INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_HOST);
		int reliableMulticastPort = Integer.parseInt(this.localSettings
				.getString(INetPrefKeys.RELIABLE_MULTICAST_PORT, 
				           INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_PORT));
		long reliableMulticastFlatLine = Long.parseLong(this.localSettings
				.getString(INetPrefKeys.RELIABLE_MULTICAST_NET_CONN_TIMEOUT,
				           INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_NET_CONN));
		int reliableMulticastIdleTime = Integer.parseInt(this.localSettings
				.getString(INetPrefKeys.RELIABLE_MULTICAST_CONN_IDLE_TIMEOUT,
				           INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_IDLE_TIME));
		int reliableMulticastTTL = Integer.parseInt(this.localSettings
				.getString(INetPrefKeys.RELIABLE_MULTICAST_TTL,
				           INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_TTL));
		this.reliableMulticastChannel.setHost(reliableMulticastHost);
		this.reliableMulticastChannel.setPort(reliableMulticastPort);
		this.reliableMulticastChannel.setFlatLineTime(reliableMulticastFlatLine);
		this.reliableMulticastChannel.setSocketTimeout(reliableMulticastIdleTime);
		this.reliableMulticastChannel.setTTL(reliableMulticastTTL);

		/*
		 * SerialChannel
		 */
        this.isSerialUserDisabled = this
        		.getAgPref(INetPrefKeys.SERIAL_DISABLED, 
        		           INetPrefKeys.DEFAULT_SERIAL_DISABLED);
        this.serialChannel.setDevice(this.localSettings
        		.getString(INetPrefKeys.SERIAL_DEVICE, 
            		       INetPrefKeys.DEFAULT_SERIAL_DEVICE) );
        this.serialChannel.setBaudRate( Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.SERIAL_BAUD_RATE, 
            	           INetPrefKeys.DEFAULT_SERIAL_BAUD_RATE) ));

        this.serialChannel.setSlotNumber(Integer.parseInt(this
        		.getAgPref(INetPrefKeys.SERIAL_SLOT_NUMBER, 
            		       INetPrefKeys.DEFAULT_SERIAL_SLOT_NUMBER)));
        serialChannel.setRadiosInGroup(Integer.parseInt(this
                .getAgPref(INetPrefKeys.SERIAL_RADIOS_IN_GROUP, 
            		       INetPrefKeys.DEFAULT_SERIAL_RADIOS_IN_GROUP)));

        serialChannel.setSlotDuration( Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.SERIAL_SLOT_DURATION, 
            		       INetPrefKeys.DEFAULT_SERIAL_SLOT_DURATION) ));
        serialChannel.setTransmitDuration( Integer.parseInt(this.localSettings
                .getString(INetPrefKeys.SERIAL_TRANSMIT_DURATION, 
            		       INetPrefKeys.DEFAULT_SERIAL_TRANSMIT_DURATION) ));

        serialChannel.setSenderEnabled(this.localSettings
                .getBoolean(INetPrefKeys.SERIAL_SEND_ENABLED, 
            		        INetPrefKeys.DEFAULT_SERIAL_SEND_ENABLED) );
        serialChannel.setReceiverEnabled(this.localSettings
                .getBoolean(INetPrefKeys.SERIAL_RECEIVE_ENABLED, 
            		        INetPrefKeys.DEFAULT_SERIAL_RECEIVE_ENABLED) );
	}

	/**
	 * Reset the local copies of the shared preference. Also indicate that the
	 * gateway connections are stale will need to be refreshed.
	 *
	 * @param prefs   a sharedPreferencesInterface for accessing and modifying preference data
	 * @param key     a string to signal which preference to access
	 *
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		logger.info("::onSharedPreferenceChanged {}", key);

		if (key.equals(INetPrefKeys.GATEWAY_HOST)) {
			String gatewayHostname = prefs.getString(key,INetPrefKeys.DEFAULT_GATEWAY_HOST);
			this.tcpChannel.setHost(gatewayHostname);
			return;
		}
		else
		if (key.equals(INetPrefKeys.GATEWAY_PORT)) {
			int gatewayPort = Integer.valueOf(prefs.getString(
					key, String.valueOf(INetPrefKeys.DEFAULT_GATEWAY_PORT)));
			this.tcpChannel.setPort(gatewayPort);
			return;
		}
		else
		if (key.equals(INetPrefKeys.JOURNAL_DISABLED)) {
			this.isJournalUserDisabled = prefs.getBoolean(key, this.isJournalUserDisabled);
			if (this.isJournalUserDisabled)
				this.journalChannel.disable();
			else
				this.journalChannel.enable();
			return;
		}
		else
		// handle network authentication group
		if (key.equals(INetPrefKeys.CORE_DEVICE_ID)) {
			deviceId = prefs.getString(key, deviceId);
			if (this.isConnected())
				this.auth();
			return;
		}
		else
		if (key.equals(Keys.UserKeys.USERNAME)) {
			String globalId = prefs.getString(key, this.operatorId);
			
			final Editor editor = this.localSettings.edit();
			editor.putString(INetPrefKeys.CORE_OPERATOR_ID, globalId );
			editor.commit();
			 
			//this.refresh();
			//if (this.isConnected()) this.auth(); 
			return;
		}
		else
		if (key.equals(INetPrefKeys.CORE_OPERATOR_ID)) {
			this.operatorId = prefs.getString(key, this.operatorId);
			
			this.refresh();
			if (this.isConnected()) this.auth(); 
			return;
		}
		else
		if (key.equals(INetPrefKeys.CORE_OPERATOR_KEY)) {
			this.operatorKey = prefs.getString(key, this.operatorKey);
			if (this.isConnected())
				this.auth();
			return;
		}
		else
		if (key.equals(INetPrefKeys.GATEWAY_TIMEOUT)) {
			Integer timeout = Integer.valueOf(prefs.getString(
					key, String.valueOf(INetPrefKeys.DEFAULT_GW_TIMEOUT)));
			this.tcpChannel.setSocketTimeout(timeout.intValue() * 1000); // convert
			// seconds
			// into
			// milliseconds
		}
		else
		// handle network connectivity group
		// if (key.equals(INetPrefKeys.WIRED_PREF_DISABLED)) {
		// shouldUse(prefs);
		// }
		// if (key.equals(INetPrefKeys.WIFI_PREF_DISABLED)) {
		// shouldUse(prefs);
		// }
		if (key.equals(INetDerivedKeys.NET_CONN_PREF_SHOULD_USE)) {
			logger.info("explicit opererator reset on channel");
			this.networkingSwitch = true;

			this.tcpChannel.reset();
			this.multicastChannel.reset();
			this.reliableMulticastChannel.reset();
			this.serialChannel.reset();
		}
		else
		if (key.equals(INetPrefKeys.GATEWAY_FLAT_LINE_TIME)) {
			long flatLineTime = Integer.valueOf(prefs.getString(
					key, String.valueOf(INetPrefKeys.DEFAULT_GW_FLAT_LINE_TIME)));
			this.tcpChannel.setFlatLineTime(flatLineTime * 60 * 1000); // convert
			// from
			// minutes
			// to
			// milliseconds
		}
		else
		if (key.equals(INetPrefKeys.GATEWAY_DISABLED)) {
			if (prefs.getBoolean(key, INetPrefKeys.DEFAULT_GATEWAY_DISABLED)) {
				this.tcpChannel.disable();
			} else {
				this.tcpChannel.enable();
			}
		}
		else
        //
        // Multicast
        //
		if (key.equals(INetPrefKeys.MULTICAST_DISABLED)) {
			if (prefs.getBoolean(key, INetPrefKeys.DEFAULT_MULTICAST_DISABLED)) {
				this.multicastChannel.disable();
			} else {
				this.multicastChannel.enable();
			}
		}
		else
		if (key.equals(INetPrefKeys.MULTICAST_HOST)) {
			String ipAddress = prefs.getString(
					key, INetPrefKeys.DEFAULT_MULTICAST_HOST);
			this.multicastChannel.setHost(ipAddress);
		}
		else
		if (key.equals(INetPrefKeys.MULTICAST_PORT)) {
			int port = Integer.parseInt(prefs.getString(
					key, INetPrefKeys.DEFAULT_MULTICAST_PORT));
			this.multicastChannel.setPort(port);
		}
		else
		if (key.equals(INetPrefKeys.MULTICAST_TTL)) {
			int ttl = Integer.parseInt(prefs.getString(
					key, INetPrefKeys.DEFAULT_MULTICAST_TTL));
			this.multicastChannel.setTTL(ttl);
        }
		else
        //
        // Reliable Multicast
        //
		if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_DISABLED)) {
			if (prefs.getBoolean(key, INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_DISABLED)) {
				this.reliableMulticastChannel.disable();
			} else {
				this.reliableMulticastChannel.enable();
			}
		}
		else
		if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_HOST)) {
			String ipAddress = prefs.getString(
					key, INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_HOST);
			this.reliableMulticastChannel.setHost(ipAddress);
		}
		else
		if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_PORT)) {
			int port = Integer.parseInt(prefs.getString(
					key, INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_PORT));
			this.reliableMulticastChannel.setPort(port);
		}
		else
		if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_TTL)) {
			int ttl = Integer.parseInt(prefs.getString(
					key, INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_TTL));
			this.reliableMulticastChannel.setTTL(ttl);
        }
		else
        //
        // Serial port
        //
		if ( key.equals(INetPrefKeys.SERIAL_DEVICE) ) {
			serialChannel.setDevice( prefs.getString( key, 
					INetPrefKeys.DEFAULT_SERIAL_DEVICE));
		}
		else
		if ( key.equals(INetPrefKeys.SERIAL_BAUD_RATE) ) {
			serialChannel.setBaudRate( Integer.parseInt( prefs.getString(key, 
					INetPrefKeys.DEFAULT_SERIAL_BAUD_RATE )));
		}
		else
		if ( key.equals(INetPrefKeys.SERIAL_SLOT_NUMBER) ) {
			serialChannel.setSlotNumber( Integer.parseInt( prefs.getString(key, 
					INetPrefKeys.DEFAULT_SERIAL_SLOT_NUMBER )));
		}
		else
		if ( key.equals(INetPrefKeys.SERIAL_RADIOS_IN_GROUP) ) {
			serialChannel.setRadiosInGroup( Integer.parseInt( prefs.getString(key, 
					INetPrefKeys.DEFAULT_SERIAL_RADIOS_IN_GROUP )));
		}
		else
		if ( key.equals(INetPrefKeys.SERIAL_SLOT_DURATION) ) {
			serialChannel.setSlotDuration( Integer.parseInt( prefs.getString(key, 
					INetPrefKeys.DEFAULT_SERIAL_SLOT_DURATION )));
		}
		else
		if ( key.equals(INetPrefKeys.SERIAL_TRANSMIT_DURATION) ) {
			serialChannel.setTransmitDuration( Integer.parseInt( prefs.getString(key,
					INetPrefKeys.DEFAULT_SERIAL_TRANSMIT_DURATION )));
		}
		else
		if ( key.equals(INetPrefKeys.SERIAL_SEND_ENABLED) ) {
			serialChannel.setSenderEnabled( prefs.getBoolean(key, 
					! INetPrefKeys.DEFAULT_SERIAL_SEND_ENABLED ));
		}
		else
		if ( key.equals(INetPrefKeys.SERIAL_RECEIVE_ENABLED) ) {
			serialChannel.setReceiverEnabled( prefs.getBoolean(key, 
					! INetPrefKeys.DEFAULT_SERIAL_RECEIVE_ENABLED ));
		}
		else
		if ( key.equals(INetPrefKeys.SERIAL_DISABLED) ) {
			if ( prefs.getBoolean(key, INetPrefKeys.DEFAULT_SERIAL_DISABLED ))
				this.serialChannel.disable();
			else
				this.serialChannel.enable();
		}
		else {
			logger.error("shared preference key {} is unknown", key);
		}

		return;
	}

	// ===========================================================
	// Protocol Buffers Methods
	// ===========================================================

	/**
	 * Authentication requests are sent via TCP. They are primarily concerned
	 * with obtaining the sessionId.
	 */
	public AmmoMessages.MessageWrapper.Builder buildAuthenticationRequest() {
		logger.info("::buildAuthenticationRequest");

		final AmmoMessages.MessageWrapper.Builder mw = 
				AmmoMessages.MessageWrapper.newBuilder();
		mw.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
		mw.setSessionUuid(sessionId);

		final AmmoMessages.AuthenticationMessage.Builder authreq = 
				AmmoMessages.AuthenticationMessage.newBuilder();
		authreq.setDeviceId(
				UniqueIdentifiers.device(this.getApplicationContext()))
				.setUserId(this.operatorId).setUserKey(this.operatorKey);

		mw.setAuthenticationMessage(authreq);
		mw.setMessagePriority(AmmoGatewayMessage.PriorityLevel.AUTH.v);
		return mw;
	}

	// ===========================================================
	// Gateway Communication Methods
	// ===========================================================

	/**
	 * Used to send a message to the android gateway plugin.
	 *
	 * This takes an argument indicating the channel type [tcpchannel, multicast,
     * reliablemulticast, serial, journal].
	 *
	 *
	 * @param outstream
	 * @param size
	 * @param payload_checksum
	 * @param message
	 */
	@Override
	public ChannelDisposal sendRequest(AmmoGatewayMessage agm, String channelName) {
		logger.info("::sendGatewayRequest");
		// agm.setSessionUuid( sessionId );
		if (!gChannelMap.containsKey(channelName))
			return ChannelDisposal.REJECTED;
		final NetChannel channel = gChannelMap.get(channelName);
		if (!channel.isConnected())
			return ChannelDisposal.PENDING;
		return channel.sendRequest(agm);
	}

	public ChannelStatus checkChannel(String channelName) {
		if (!gChannelMap.containsKey(channelName))
			return ChannelStatus.DOWN;
        
		final NetChannel channel = gChannelMap.get(channelName);
		if (channel.isBusy()) // this is to improve performance
			return ChannelStatus.FULL;
		if (!channel.isConnected())
			return ChannelStatus.DOWN;
		return ChannelStatus.READY;
	}

	abstract public class TotalChannel implements INetChannel {}

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

	// ===============================================================
	// BINDING CALLS (AmmoServiceBinder)
	//
	// These may be called internally but they are intended to be
	// called by the distributor service.
	// ===============================================================

	/**
	 * This method is called just prior to onDestroy or when the service is
	 * being intentionally shut down.
	 */
	public void teardown() {
		logger.info("Tearing down NPS");
		this.tcpChannel.disable();
		this.multicastChannel.disable();
		this.reliableMulticastChannel.disable();
		this.serialChannel.disable();

		Timer t = new Timer();
		t.schedule(new TimerTask() {
			// Stop this service
			@Override
			public void run() {
				AmmoService.this.stopSelf();
				stopSelf();
			}
		}, 1000);
	}

	/**
	 * Check to see if there are any open connections.
	 *
	 * @return
	 */
	public boolean isConnected() {
	    boolean any = (   tcpChannel.isConnected()
                       || multicastChannel.isConnected()
                       || reliableMulticastChannel.isConnected()
                       || ((serialChannel != null) && serialChannel.isConnected()));
		logger.debug("::isConnected ? {}", any );
		return any;
	}

	/**
	 * For the following methods there is an expectation that the connection has
	 * been pre-verified.
	 */
	public boolean auth() {
		logger.info("::authenticate");

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
	public boolean ack(String channel, ChannelDisposal status) {
		return false;
	}

	// The channel lets the AmmoService know that the channel was
	// successfully authorized by calling this method.
	public void authorizationSucceeded(NetChannel channel, AmmoGatewayMessage agm) {
		// HACK! Fixme
		final AmmoMessages.MessageWrapper mw;
		try {
			mw = AmmoMessages.MessageWrapper.parseFrom(agm.payload);
		} catch (InvalidProtocolBufferException ex) {
			logger.error("parsing payload failed {}", ex.getLocalizedMessage());
			return;
		}
		if (mw == null) {
			logger.error("mw was null!");
			return;
		}

		PreferenceManager
		.getDefaultSharedPreferences(this)
		.edit()
		.putBoolean(INetDerivedKeys.NET_CONN_PREF_IS_ACTIVE, true)
		.commit();
		sessionId = mw.getSessionUuid();

		logger.trace("authentication complete, repost subscriptions and pending data {}", channel);
		this.distThread.onChannelChange(this.getBaseContext(), channel.name, ChannelChange.ACTIVATE);

		logger.info("authentication complete inform applications : ");
		// TBD SKN - this should not be sent now ...
		// broadcast login event to apps ...
		Intent loginIntent = new Intent(IntentNames.AMMO_LOGIN);
		loginIntent.putExtra("operatorId", this.operatorId);
		this.sendBroadcast(loginIntent);

		// broadcast gateway connected to apps ...
		loginIntent = new Intent(IntentNames.AMMO_CONNECTED);
		loginIntent.putExtra("channel", channel.name);
		this.sendBroadcast(loginIntent);

	}


	private void createReliableMulticastConfigFile( Context context, String fileName )
    {
		File outFile = new File( Environment.getExternalStorageDirectory()
				                 + "/support/jgroups/" + fileName);
		if ( !outFile.exists() ) {
			try {
				InputStream inputStream = context.getAssets().open( fileName );
				outFile = new File( Environment.getExternalStorageDirectory()
						            + "/support/jgroups/");
				if ( !outFile.exists() )
					outFile.mkdirs();
				outFile = new File( outFile, fileName );
				OutputStream out = new FileOutputStream( outFile );

				byte[] buffer = new byte[4096];
				int n = 0;
				while ( -1 != (n = inputStream.read(buffer)) ) {
					out.write(buffer, 0, n);
				}
				out.close();
				inputStream.close();
			} catch ( Exception e ) {
				e.printStackTrace();
			}
		}
	}


	/**
	 * Deal with the status of the connection changing. 
	 * Report the status to the application who acts as a broker.
	 */
	@Override
	public void statusChange(NetChannel channel, int connStatus,
			int sendStatus, int recvStatus) {
		logger.debug("status change. channel={}", channel.name );

		gChannels.get(channel.name)
		         .setStatus(new int[] { connStatus, sendStatus, recvStatus });

        switch (connStatus) {
        case NetChannel.CONNECTED:
        	if (channel.isAuthenticatingChannel()) break;
        case NetChannel.SENDING:
        case NetChannel.TAKING:
        	this.distThread.onChannelChange(this.getBaseContext(), channel.name, ChannelChange.ACTIVATE);
        	break;
        default: 
        	this.distThread.onChannelChange(this.getBaseContext(), channel.name, ChannelChange.DEACTIVATE);
        }

		final Intent broadcastIntent = new Intent(AmmoIntents.AMMO_ACTION_GATEWAY_STATUS_CHANGE);
		this.sendBroadcast(broadcastIntent);
	}

	private void netlinkStatusChanged() {
		final Intent broadcastIntent = new Intent(
				AmmoIntents.AMMO_ACTION_NETLINK_STATUS_CHANGE);
		sendBroadcast(broadcastIntent);
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

	static private final Map<String, Channel> gChannels;
	// Network Channels
	final private TcpChannel tcpChannel = TcpChannel.getInstance("gateway", this);
	final private MulticastChannel multicastChannel = MulticastChannel.getInstance("multicast", this);
	final private ReliableMulticastChannel reliableMulticastChannel = ReliableMulticastChannel.getInstance("reliablemulticast", this);
	final private JournalChannel journalChannel = JournalChannel.getInstance("journal", this);
	private SerialChannel serialChannel;

	static final private Map<String,NetChannel> gChannelMap;

	static {
		gChannels = new HashMap<String, Channel>();
		gChannelMap = new HashMap<String,NetChannel>();
	}
	static void addChannel() {
		
	}
	
	private final List<Netlink> mNetlinks = new ArrayList<Netlink>();

	public List<Channel> getGatewayList() {
		return new ArrayList<Channel>(gChannels.values());
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
	 *
	 */
	private class NetworkBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(final Context context, final Intent aIntent) {
			final String action = aIntent.getAction();
			logger.debug("onReceive: {}", action);

			if (AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE.equals(action)) {
				int state = aIntent.getIntExtra("state", 0);

				// Should we be doing this here? It's not parallel with the wifi
				// and 3G below.
				if (state != 0) {
					switch (state) {
					case AmmoIntents.LINK_UP:
						logger.info("onReceive: Link UP " + action);
						tcpChannel.linkUp();
						multicastChannel.linkUp();
						reliableMulticastChannel.linkUp();
						break;
					case AmmoIntents.LINK_DOWN:
						logger.info("onReceive: Link DOWN " + action);
						tcpChannel.linkDown();
						multicastChannel.linkDown();
						reliableMulticastChannel.linkDown();
						break;
					}
				}

				// This intent comes in for both wired and wifi.
				mNetlinks.get(linkTypes.WIRED.value).updateStatus();
				mNetlinks.get(linkTypes.WIFI.value).updateStatus();
				netlinkStatusChanged();
				return;
			} else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)
					|| WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
					|| WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION
					.equals(action)
					|| WifiManager.SUPPLICANT_STATE_CHANGED_ACTION
					.equals(action)) {
				logger.warn("WIFI state changed");
				mNetlinks.get(linkTypes.WIRED.value).updateStatus();
				mNetlinks.get(linkTypes.WIFI.value).updateStatus();
				netlinkStatusChanged();
				return;
			} else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED
					.equals(action)) {
				logger.warn("3G state changed");
				mNetlinks.get(linkTypes.MOBILE_3G.value).updateStatus();
				netlinkStatusChanged();
				return;
			}

			// if (INetworkService.ACTION_RECONNECT.equals(action)) {
			// //AmmoService.this.connectChannels(true);
			// return;
			// }
			// if (INetworkService.ACTION_DISCONNECT.equals(action)) {
			// //AmmoService.this.disconnectChannels();
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

			logger.info("::onReceive: {}", action);
			checkResourceStatus(aContext);

			if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
			}
		}

		public void checkResourceStatus(final Context aContext) { //
			logger.info("::checkResourceStatus");
			{ 
				final WifiManager wm = (WifiManager) aContext.getSystemService(Context.WIFI_SERVICE);
				final int wifiState = wm.getWifiState(); // TODO check for permission or catch error
				logger.info("wifi state={}", wifiState);

				final TelephonyManager tm = (TelephonyManager) aContext.getSystemService(
						Context.TELEPHONY_SERVICE);
				final int dataState = tm.getDataState(); // TODO check for permission or catch error
				logger.info("telephone data state={}", dataState);

				mNetworkConnected = wifiState == WifiManager.WIFI_STATE_ENABLED
						|| dataState == TelephonyManager.DATA_CONNECTED;
				logger.info("mConnected={}", mNetworkConnected);
			} 
			{
				final String state = Environment.getExternalStorageState();

				logger.info("sdcard state={}", state);
				mSdCardAvailable = Environment.MEDIA_MOUNTED.equals(state);
				logger.info("mSdcardAvailable={}", mSdCardAvailable);
			}
		}
	}
}

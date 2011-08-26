/**
 *
 */
package edu.vu.isis.ammo.core.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IPrefKeys;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.ApplicationEx;
import edu.vu.isis.ammo.core.distributor.DistributorService;
import edu.vu.isis.ammo.core.model.Channel;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.Multicast;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.model.PhoneNetlink;
import edu.vu.isis.ammo.core.model.Serial;
import edu.vu.isis.ammo.core.model.WifiNetlink;
import edu.vu.isis.ammo.core.model.WiredNetlink;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.util.IRegisterReceiver;
import edu.vu.isis.ammo.util.UniqueIdentifiers;

/**
 * Network Service is responsible for all networking between the core
 * application and the server. Currently, this service implements a UDP
 * connection for periodic data updates and a long-polling TCP connection for
 * event driven notifications.
 */
public class NetworkService extends Service implements
		OnSharedPreferenceChangeListener, INetworkService,
		INetworkService.OnSendMessageHandler, IChannelManager {
	// ===========================================================
	// Constants
	// ===========================================================
	private static final Logger logger = LoggerFactory.getLogger("net.service");

	// Local constants
	public static final String DEFAULT_GATEWAY_HOST = "129.59.129.189";
	public static final int DEFAULT_GATEWAY_PORT = 32869;
	public static final int DEFAULT_FLAT_LINE_TIME = 20; // 20 minutes
	public static final int DEFAULT_SOCKET_TIMEOUT = 3; // 3 seconds

	public static final String DEFAULT_MULTICAST_HOST = "228.1.2.3";
	public static final String DEFAULT_MULTICAST_PORT = "9982";
	public static final String DEFAULT_MULTICAST_NET_CONN = "20";
	public static final String DEFAULT_MULTICAST_IDLE_TIME = "3";

	@SuppressWarnings("unused")
	private static final String NULL_CHAR = "\0";
	@SuppressWarnings("unused")
	private static final int UDP_BUFFER_SIZE = 4096;

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
	private String deviceId = null;
	private String operatorId = "0004";
	private String operatorKey = "37";

	// journalingSwitch
	private boolean journalingSwitch = false;

	// Determine if the connection is enabled
	private boolean gatewayEnabled = true;
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

	private DistributorService distributor;

	// Channels
	private INetChannel tcpChannel = TcpChannel.getInstance(this);
	//private INetChannel multicastChannel = MulticastChannel.getInstance(this);
	private INetChannel multicastChannel = MulticastChannel.getInstance(this);
	private INetChannel journalChannel = JournalChannel.getInstance(this);
	private SerialChannel serialChannel = SerialChannel.getInstance(this);

	private MyBroadcastReceiver myReceiver = null;
	private IRegisterReceiver mReceiverRegistrar = new IRegisterReceiver() {
		@Override
		public Intent registerReceiver(final BroadcastReceiver aReceiver,
				final IntentFilter aFilter) {
			return NetworkService.this.registerReceiver(aReceiver, aFilter);
		}

		@Override
		public void unregisterReceiver(final BroadcastReceiver aReceiver) {
			NetworkService.this.unregisterReceiver(aReceiver);
		}
	};

	// ===========================================================
	// Lifecycle
	// ===========================================================

	private final IBinder binder = new MyBinder();

	private ApplicationEx application;

	@SuppressWarnings("unused")
	private ApplicationEx getApplicationEx() {
		if (this.application == null)
			this.application = (ApplicationEx) this.getApplication();
		return this.application;
	}

	public class MyBinder extends Binder {
		public NetworkService getService() {
			logger.trace("MyBinder::getService");
			return NetworkService.this;
		}
	}

	/**
	 * Class for clients to access. This service always runs in the same process
	 * as its clients. So no inter-*process* communication is needed.
	 */
	@Override
	public IBinder onBind(Intent arg0) {
		logger.trace("MyBinder::onBind {}", Thread.currentThread().toString());
		return binder;
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
		logger.info("::onStartCommand");
		if (intent.getAction().equals(NetworkService.PREPARE_FOR_STOP)) {
			logger.debug("Preparing to stop NPS");
			this.teardown();
			this.stopSelf();
			return START_NOT_STICKY;
		}
		logger.info("started");
		return START_STICKY;
	}

	private PhoneStateListener mListener;

	/**
	 * When the service is first created, we should grab the IP and Port values
	 * from the SystemPreferences.
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		logger.info("onCreate");
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);

		mChannels.add(Gateway.getInstance(getBaseContext()));
		mChannels.add(Multicast.getInstance(getBaseContext()));
		mChannels.add(Serial.getInstance(getBaseContext()));


		mNetlinks.add(WifiNetlink.getInstance(getBaseContext()));
		mNetlinks.add(WiredNetlink.getInstance(getBaseContext()));
		mNetlinks.add(PhoneNetlink.getInstance(getBaseContext()));

		// FIXME: find the appropriate time to release() the multicast lock.
		logger.error("Acquiring multicast lock()");
		WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		WifiManager.MulticastLock multicastLock = wm
				.createMulticastLock("mydebuginfo");
		multicastLock.acquire();
		logger.error("...acquired multicast lock()");

		// no point in enabling the socket until the preferences have been read
		this.tcpChannel.disable();
		this.multicastChannel.disable();
        this.serialChannel.disable();
		this.acquirePreferences();
		if (this.networkingSwitch && this.gatewayEnabled) {
			this.tcpChannel.enable();
		}
		this.multicastChannel.enable();
		this.multicastChannel.reset(); // This starts the connector thread.

        this.serialChannel.enable();
        this.serialChannel.reset(); // This starts the connector thread.

		this.myReceiver = new MyBroadcastReceiver();

		final IntentFilter networkFilter = new IntentFilter();
		networkFilter.addAction(INetworkService.ACTION_RECONNECT);
		networkFilter.addAction(INetworkService.ACTION_DISCONNECT);

		networkFilter.addAction(AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE);
		networkFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		networkFilter
				.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		networkFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		networkFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		networkFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

		this.mReceiverRegistrar
				.registerReceiver(this.myReceiver, networkFilter);

		mListener = new PhoneStateListener() {
			public void onDataConnectionStateChanged(int state) {
				logger.info("PhoneReceiver::onCallStateChanged()");
				mNetlinks.get(linkTypes.MOBILE_3G.value).updateStatus();
				netlinkStatusChanged();
			}
		};
		final TelephonyManager tm = (TelephonyManager) getBaseContext()
				.getSystemService(Context.TELEPHONY_SERVICE);
		tm.listen(mListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

		// FIXME: this probably needs to happen differently. Fred and
		// I discussed it and we need to add a flag to the intent sent
		// below so that the receiver knows
		// done initializing mcast channel - now fire up the AMMO_LOGIN intent
		// to force apps to register their subscriptions
		logger.info("Forcing applications to register their subscriptions");
		// broadcast login event to apps ...
		Intent loginIntent = new Intent(INetPrefKeys.AMMO_LOGIN);
		loginIntent.putExtra("operatorId", operatorId);
		this.sendBroadcast(loginIntent);
	}

	@Override
	public void onDestroy() {
		logger.warn("::onDestroy");
		this.tcpChannel.disable();
		this.multicastChannel.disable();
		this.journalChannel.close();

		this.mReceiverRegistrar.unregisterReceiver(this.myReceiver);
		super.onDestroy();
	}

	// ===========================================================
	// Networking
	// ===========================================================

	/**
	 * Read the system preferences for the network connection information.
	 */
	private void acquirePreferences() {
		logger.info("::acquirePreferences");
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());

		this.journalingSwitch = prefs.getBoolean(
				INetPrefKeys.CORE_IS_JOURNALED, this.journalingSwitch);

		this.gatewayEnabled = prefs.getBoolean(INetPrefKeys.GATEWAY_SHOULD_USE,
				true);
		this.networkingSwitch = prefs.getBoolean(
				INetPrefKeys.NET_CONN_PREF_SHOULD_USE, this.networkingSwitch);

		this.deviceId = prefs.getString(INetPrefKeys.CORE_DEVICE_ID,
				this.deviceId);
		this.operatorId = prefs.getString(INetPrefKeys.CORE_OPERATOR_ID,
				this.operatorId);
		this.operatorKey = prefs.getString(INetPrefKeys.CORE_OPERATOR_KEY,
				this.operatorKey);

		String gatewayHostname = prefs.getString(INetPrefKeys.CORE_IP_ADDR,
				DEFAULT_GATEWAY_HOST);
		this.tcpChannel.setHost(gatewayHostname);

		String gatewayPortStr = prefs.getString(INetPrefKeys.CORE_IP_PORT,
				String.valueOf(DEFAULT_GATEWAY_PORT));
		int gatewayPort = Integer.valueOf(gatewayPortStr);
		this.tcpChannel.setPort(gatewayPort);

		String flatLineTimeStr = prefs.getString(
				INetPrefKeys.NET_CONN_FLAT_LINE_TIME, String
						.valueOf(DEFAULT_FLAT_LINE_TIME));
		long flatLineTime = Integer.valueOf(flatLineTimeStr);
		this.tcpChannel.setFlatLineTime(flatLineTime * 60 * 1000); // convert
		// minutes
		// into
		// milliseconds

		/*
		 * Multicast
		 */
		String multicastHost = prefs.getString(
				INetPrefKeys.MULTICAST_IP_ADDRESS, DEFAULT_MULTICAST_HOST);
		int multicastPort = Integer.parseInt(prefs.getString(
				INetPrefKeys.MULTICAST_PORT, DEFAULT_MULTICAST_PORT));
		long multicastFlatLine = Long.parseLong(prefs.getString(
				INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT,
				DEFAULT_MULTICAST_NET_CONN));
		int multicastIdleTime = Integer.parseInt(prefs.getString(
				INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT,
				DEFAULT_MULTICAST_IDLE_TIME));
		this.multicastChannel.setHost(multicastHost);
		this.multicastChannel.setPort(multicastPort);
		this.multicastChannel.setFlatLineTime(multicastFlatLine);
		this.multicastChannel.setSocketTimeout(multicastIdleTime);

		/*
		 * SerialChannel
		 */
        this.serialChannel.setBaudRate( Integer.parseInt(
            prefs.getString(INetPrefKeys.SERIAL_BAUD_RATE, "9600") ));
        this.serialChannel.setDebugPeriod( Long.parseLong(
            prefs.getString(INetPrefKeys.SERIAL_DEBUG_PERIOD, "10") ));
        this.serialChannel.setDevice(
            prefs.getString(INetPrefKeys.SERIAL_DEVICE, "/dev/ttyUSB0") );
        this.serialChannel.setReceiverEnabled(
            prefs.getBoolean(INetPrefKeys.SERIAL_RECEIVE_ENABLED, true) );
        this.serialChannel.setSenderEnabled(
            prefs.getBoolean(INetPrefKeys.SERIAL_SEND_ENABLED, true) );

        if ( prefs.getBoolean(INetPrefKeys.SERIAL_SHOULD_USE, false) )
            this.serialChannel.enable();
        else
            this.serialChannel.disable();

        this.serialChannel.setSlotNumber(Integer.parseInt(
            prefs.getString(INetPrefKeys.SERIAL_SLOT_NUMBER, "8")));
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

		if (key.equals(INetPrefKeys.CORE_IP_ADDR)) {
			String gatewayHostname = prefs.getString(INetPrefKeys.CORE_IP_ADDR,
					DEFAULT_GATEWAY_HOST);
			this.tcpChannel.setHost(gatewayHostname);
			return;
		}
		if (key.equals(INetPrefKeys.CORE_IP_PORT)) {
			int gatewayPort = Integer.valueOf(prefs.getString(
					INetPrefKeys.CORE_IP_PORT, String
							.valueOf(DEFAULT_GATEWAY_PORT)));
			this.tcpChannel.setPort(gatewayPort);
			return;
		}
		if (key.equals(INetPrefKeys.CORE_IS_JOURNALED)) {
			this.journalingSwitch = prefs.getBoolean(
					INetPrefKeys.CORE_IS_JOURNALED, this.journalingSwitch);
			if (this.journalingSwitch)
				this.journalChannel.enable();
			else
				this.journalChannel.disable();
			return;
		}

		// handle network authentication group
		if (key.equals(INetPrefKeys.CORE_DEVICE_ID)) {
			deviceId = prefs.getString(INetPrefKeys.CORE_DEVICE_ID, deviceId);
			if (this.isConnected())
				this.auth();
			return;
		}
		if (key.equals(IPrefKeys.CORE_OPERATOR_ID)) {
			operatorId = prefs
					.getString(IPrefKeys.CORE_OPERATOR_ID, operatorId);
			if (this.isConnected())
				this.auth(); // TBD SKN: this should really do a setStale rather
			// than just authenticate
			return;
		}
		if (key.equals(INetPrefKeys.CORE_OPERATOR_KEY)) {
			operatorKey = prefs.getString(INetPrefKeys.CORE_OPERATOR_KEY,
					operatorKey);
			if (this.isConnected())
				this.auth();
			return;
		}

		if (key.equals(INetPrefKeys.CORE_SOCKET_TIMEOUT)) {
			Integer timeout = Integer.valueOf(prefs.getString(
					INetPrefKeys.CORE_SOCKET_TIMEOUT, String
							.valueOf(DEFAULT_SOCKET_TIMEOUT)));
			this.tcpChannel.setSocketTimeout(timeout.intValue() * 1000); // convert
			// seconds
			// into
			// milliseconds
		}

		// handle network connectivity group
		// if (key.equals(INetPrefKeys.WIRED_PREF_SHOULD_USE)) {
		// shouldUse(prefs);
		// }
		// if (key.equals(INetPrefKeys.WIFI_PREF_SHOULD_USE)) {
		// shouldUse(prefs);
		// }
		if (key.equals(INetPrefKeys.NET_CONN_PREF_SHOULD_USE)) {
			logger.info("explicit opererator reset on channel");
			this.networkingSwitch = true;
			this.tcpChannel.reset();
			this.multicastChannel.reset();
			this.serialChannel.reset();
		}

		if (key.equals(INetPrefKeys.NET_CONN_FLAT_LINE_TIME)) {
			long flatLineTime = Integer.valueOf(prefs.getString(
					INetPrefKeys.NET_CONN_FLAT_LINE_TIME, String
							.valueOf(DEFAULT_FLAT_LINE_TIME)));
			this.tcpChannel.setFlatLineTime(flatLineTime * 60 * 1000); // convert
			// from
			// minutes
			// to
			// milliseconds
		}

		if (key.equals(INetPrefKeys.GATEWAY_SHOULD_USE)) {
			if (prefs.getBoolean(key, true)) {

				this.tcpChannel.enable();
			} else {
				this.tcpChannel.disable();
			}
		}

		if (key.equals(INetPrefKeys.MULTICAST_SHOULD_USE)) {
			if (prefs.getBoolean(INetPrefKeys.MULTICAST_SHOULD_USE, true)) {
				this.multicastChannel.enable();
			} else {
				this.multicastChannel.disable();
			}
		}

		if (key.equals(INetPrefKeys.MULTICAST_IP_ADDRESS)) {
			String ipAddress = prefs.getString(
					INetPrefKeys.MULTICAST_IP_ADDRESS, DEFAULT_MULTICAST_HOST);
			this.multicastChannel.setHost(ipAddress);
		}
		if (key.equals(INetPrefKeys.MULTICAST_PORT)) {
			int port = Integer.parseInt(prefs.getString(
					INetPrefKeys.MULTICAST_PORT, DEFAULT_MULTICAST_PORT));
			this.multicastChannel.setPort(port);
		}

		if(key.equals(INetPrefKeys.SERIAL_BAUD_RATE)) {
			this.serialChannel.setBaudRate(Integer.parseInt(prefs.getString(INetPrefKeys.SERIAL_BAUD_RATE, "9600")));
		}

		if(key.equals(INetPrefKeys.SERIAL_DEBUG_PERIOD)) {
			this.serialChannel.setDebugPeriod(Long.parseLong(prefs.getString(INetPrefKeys.SERIAL_DEBUG_PERIOD, "10")));
		}

		if(key.equals(INetPrefKeys.SERIAL_DEVICE)) {
			this.serialChannel.setDevice(prefs.getString(INetPrefKeys.SERIAL_DEVICE, "/dev/ttyUSB0"));
		}

		if(key.equals(INetPrefKeys.SERIAL_RECEIVE_ENABLED)) {
			this.serialChannel.setReceiverEnabled(prefs.getBoolean(INetPrefKeys.SERIAL_RECEIVE_ENABLED, true));
		}

		if(key.equals(INetPrefKeys.SERIAL_SEND_ENABLED)) {
			this.serialChannel.setSenderEnabled(prefs.getBoolean(INetPrefKeys.SERIAL_SEND_ENABLED, true));
		}

		if(key.equals(INetPrefKeys.SERIAL_SHOULD_USE)) {
			if(prefs.getBoolean(INetPrefKeys.SERIAL_SHOULD_USE, false))
				this.serialChannel.enable();
			else
				this.serialChannel.disable();
		}

		if(key.equals(INetPrefKeys.SERIAL_SLOT_NUMBER)) {
			this.serialChannel.setSlotNumber(Integer.parseInt(prefs.getString(INetPrefKeys.SERIAL_SLOT_NUMBER, "8")));
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

		AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper
				.newBuilder();
		mw
				.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
		mw.setSessionUuid(sessionId);

		AmmoMessages.AuthenticationMessage.Builder authreq = AmmoMessages.AuthenticationMessage
				.newBuilder();
		authreq.setDeviceId(
				UniqueIdentifiers.device(this.getApplicationContext()))
				.setUserId(operatorId).setUserKey(operatorKey);

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
	 * This takes an argument indicating the carrier type [udp, tcp, journal].
	 *
	 * @param outstream
	 * @param size
	 * @param payload_checksum
	 * @param message
	 */
	@Override
	public Map<Class<? extends INetChannel>, Boolean> sendRequest(
			AmmoGatewayMessage agm) {
		logger.info("::sendGatewayRequest");
		// agm.setSessionUuid( sessionId );

		Map<Class<? extends INetChannel>, Boolean> status = new HashMap<Class<? extends INetChannel>, Boolean>();

		if (agm.isMulticast) {
			logger.info("   Sending multicast message.");
			status.put( MulticastChannel.class,
                       this.multicastChannel.sendRequest(agm));
		}

		if (agm.isSerialChannel) {
			logger.info("   Sending serialport message.");
			status.put( SerialChannel.class,
                        this.serialChannel.sendRequest(agm) );
		}

		if (agm.isGateway) {
			logger.info("   Sending message to gateway.");
			status.put(TcpChannel.class, this.tcpChannel.sendRequest(agm));
		}
		return status;
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
		logger.info("::deliverGatewayResponse");

		// FIXME: we do this because multicast packets can come in
		// before the distributor is set. This test is a workaround,
		// and we should probably just not create the TcpChannel until
		// the distributor is connected up. That change may have
		// far-reaching effects, so I'll save it for after the demo.
		if (distributor == null)
			return false;

		return this.distributor.deliver(agm);
	}

	// ===============================================================
	// BINDING CALLS (NetworkServiceBinder)
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
		this.serialChannel.disable();

		Timer t = new Timer();
		t.schedule(new TimerTask() {
			// Stop this service
			@Override
			public void run() {
				distributor.finishTeardown();
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
		//logger.info("::isConnected");
		return (   tcpChannel.isConnected()
                || multicastChannel.isConnected()
                || serialChannel.isConnected() );
	}

	/**
	 * For the following methods there is an expectation that the connection has
	 * been pre-verified.
	 */
	public boolean auth() {
		logger.info("::authenticate");

		/** Message Building */
		AmmoMessages.MessageWrapper.Builder mwb = buildAuthenticationRequest();
		AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(mwb,
				this);
		agmb.isGateway(true);
		Map<Class<? extends INetChannel>, Boolean> result = sendRequest(agmb
				.build());
		for (Entry<Class<? extends INetChannel>, Boolean> entry : result
				.entrySet()) {
			if (entry.getValue() == true)
				return true;
		}
		return false;
	}

	public void setDistributorServiceCallback(DistributorService callback) {
		logger.info("::setDistributorServiceCallback");

		distributor = callback;
		// there is now someplace to send the received messages.
		// connectChannels(false); // was true - why should we reconnect if a
		// distributor call back changes
	}

	/**
	 * This should handle the link state behavior. This is really the main job
	 * of the Network service; matching up links with channels.
	 *
	 */
	private class MyBroadcastReceiver extends BroadcastReceiver {
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
						break;
					case AmmoIntents.LINK_DOWN:
						logger.info("onReceive: Link DOWN " + action);
						tcpChannel.linkDown();
						multicastChannel.linkDown();
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
			// //NetworkService.this.connectChannels(true);
			// return;
			// }
			// if (INetworkService.ACTION_DISCONNECT.equals(action)) {
			// //NetworkService.this.disconnectChannels();
			// return;
			// }

			return;
		}
	}

	/**
	 * This routine is called when a message is successfully sent or when it is
	 * discarded. It is currently unused, but we may want to do something with
	 * it in the future.
	 */
	@Override
	public boolean ack(Class<? extends INetChannel> clazz, boolean status) {
		return false;
	}

	// The channel lets the NetworkService know that the channel was
	// successfully authorized by calling this method.
	public void authorizationSucceeded(AmmoGatewayMessage agm) {
		// HACK! Fixme
		AmmoMessages.MessageWrapper mw = null;
		try {
			mw = AmmoMessages.MessageWrapper.parseFrom(agm.payload);
		} catch (InvalidProtocolBufferException ex) {
			ex.printStackTrace();
		}
		if (mw == null) {
			logger.error("mw was null!");
		}

		PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(
				INetPrefKeys.NET_CONN_PREF_IS_ACTIVE, true).commit();
		sessionId = mw.getSessionUuid();

		logger
				.trace("authentication complete, repost subscriptions and pending data : ");
		this.distributor.consumerReady();

		logger.info("authentication complete inform applications : ");
		// broadcast login event to apps ...
		Intent loginIntent = new Intent(INetPrefKeys.AMMO_LOGIN);
		loginIntent.putExtra("operatorId", operatorId);
		this.sendBroadcast(loginIntent);
	}

	/**
	 * Deal with the status of the connection changing. Report the status to the
	 * application who acts as a broker.
	 */

	@Override
	public void statusChange(INetChannel channel, int connStatus,
			int sendStatus, int recvStatus) {
		// FIXME Once we have multiple gateways we'll have to fix this.
		// FIXME MAGIC NUMBERS!
		// If the channel being updated is a MulticastChannel
		if (channel.getClass().equals(MulticastChannel.class)) {
			mChannels.get(1).setStatus(
					new int[] { connStatus, sendStatus, recvStatus });
		}
		// Otherwise it is a gateway channel
		else if(channel.getClass().equals(TcpChannel.class)) {
			mChannels.get(0).setStatus(
					new int[] { connStatus, sendStatus, recvStatus });
		}
		else if(channel.getClass().equals(SerialChannel.class)) {
			mChannels.get(2).setStatus(
					new int[] { connStatus, sendStatus, recvStatus });
		}

		Intent broadcastIntent = new Intent(
				AmmoIntents.AMMO_ACTION_GATEWAY_STATUS_CHANGE);
		this.sendBroadcast(broadcastIntent);
	}

	private void netlinkStatusChanged() {
		Intent broadcastIntent = new Intent(
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

	private List<Channel> mChannels = new ArrayList<Channel>();
	private List<Netlink> mNetlinks = new ArrayList<Netlink>();

	public List<Channel> getGatewayList() {
		return mChannels;
	}

	public List<Netlink> getNetlinkList() {
		return mNetlinks;
	}
}

/**
 * 
 */
package edu.vu.isis.ammo.core.network;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.AmmoPrefKeys;
import edu.vu.isis.ammo.core.CorePreferences;
import edu.vu.isis.ammo.core.ICoreService;
import edu.vu.isis.ammo.core.distributor.IDistributorService;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
import edu.vu.isis.ammo.util.IRegisterReceiver;

/**
 * Network Proxy Service is responsible for all networking between the
 * core application and the server. Currently, this service implements a UDP
 * connection for periodic data updates and a long-polling TCP connection for
 * event driven notifications.
 * 
 * @author Demetri Miller
 * @author Fred Eisele
 * 
 */
public class NetworkService extends Service 
implements OnSharedPreferenceChangeListener
{
	// ===========================================================
	// Constants
	// ===========================================================
	private static final Logger logger = LoggerFactory.getLogger(NetworkService.class);

	@SuppressWarnings("unused")
	private static final String NULL_CHAR = "\0";
	@SuppressWarnings("unused")
	private static final int UDP_BUFFER_SIZE = 4096;
	
	public static enum NPSReturnCode {
		NO_CONNECTION, SOCKET_EXCEPTION, UNKNOWN, BAD_MESSAGE, OK
	};
	
	public static enum ConnectionStatus {
		NO_CONNECTION, CONNECTED, AVAILABLE_NOT_CONNECTED, NOT_AVAILABLE, PENDING
	};

	public static final String SIZE_KEY = "sizeByteArrayKey";
	public static final String CHECKSUM_KEY = "checksumByteArrayKey";
	
	public enum Carrier { UDP , TCP }

	// ===========================================================
	// Fields
	// ===========================================================
	private String sessionId = "";
	private String deviceId = null;
	private String operatorId = "0004";
	private String operatorKey = "37";
	
	// for providing networking support
	// should this be using IPv6?
	private boolean networkingSwitch = true;
	public boolean isNetworking() { return networkingSwitch; }
	public void setNetworkingSwitch(boolean value) { networkingSwitch = value; }
	public boolean getNetworkingSwitch() { return networkingSwitch; }
	public boolean toggleNetworkingSwitch() { return networkingSwitch = networkingSwitch ? false : true; }
	
	private NetworkBinder networkBinder;
	private IDistributorService distributor;
	private String gatewayHostname =  "129.59.129.25"; // Loopback to localhost from android.
	private boolean gatewayConnectionStale = true;
	private InetAddress gatewayIpAddr;
	public int gatewayPort = 33289;
	
	// TCP Fields
	private AmmoTcpSocket tcpSocket = new AmmoTcpSocket(this);
	
	// UDP Fields
	public DatagramSocket udpSocket = null;
	
	// SDCARD Fields
	private boolean journalingSwitch = true;
	public boolean isJournaling() { return journalingSwitch; }
	public void setJournalSwitch(boolean value) { journalingSwitch = value; }
	public boolean getJournalSwitch() { return journalingSwitch; }
	public boolean toggleJournalSwitch() { return journalingSwitch = journalingSwitch ? false : true; }
	
	public static final File journalDir = new File(Environment.getExternalStorageDirectory(), "ammo_core");
	public File journalFile = new File(journalDir, "network_proxy_service.journal");
	private BufferedOutputStream journal = null;
	
	private MyBroadcastReceiver myReceiver = null;
	private IRegisterReceiver mReceiverRegistrar = null;

	
	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	public IBinder onBind(Intent arg0) {
		logger.debug("NPS onBind called");
		this.setupNetworkConnection();
        networkBinder = NetworkBinder.getInstance(this);
		return networkBinder;
	}

	/**
	 * The network connection is used to communicate directly with the ammo android gateway plugin.
	 */
	public void setupNetworkConnection() {
		try {
			gatewayIpAddr = InetAddress.getByName(gatewayHostname);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
//		try {
//			if (udpSocket == null) {
//				logger.debug("Binding udpSocket to port");
//				udpSocket = new DatagramSocket(gatewayPort);
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}
	
	/**
	 * The journal used when direct communication with the 
	 * ammo android gateway plugin is not immediately available.
	 * The jounal is a file containing the PushRequests (not RetrievalRequest's or *Response's).
	 */
	public void setupJournal() {
		if (!journalingSwitch) return;
		if (journal != null) return;
		try {
			if (! journalDir.exists()) { journalDir.mkdirs(); }
			FileOutputStream fos = new FileOutputStream(journalFile.getCanonicalPath(), true);
			journal = new BufferedOutputStream(fos);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * In order for the service to be shutdown cleanly the 'serviceStart()' 
	 * method may be used to prepare_for_stop, it will be stopped shortly
	 * and it needs to have some things done before that happens.
	 * 
	 * When the user changes the configuration 'startService()' is run to 
	 * change the settings.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) 
	{
		if (intent.getAction().equals(INetworkBinder.PREPARE_FOR_STOP)) {
			logger.debug("Preparing to stop NPS");
			this.teardown();
			this.stopSelf();
			return START_NOT_STICKY;
		}

        logger.debug("NPS Started");
//		if (udpSocket == null) {
//			try {
//				logger.debug("Binding socket to port");
//				udpSocket = new DatagramSocket(gatewayPort);
//			} catch (SocketException e) {
//				e.printStackTrace();
//			}
//		}
		return START_STICKY;
	}

	/**
	 * When the service is first created, we should grab 
	 * the IP and Port values from the SystemPreferences.
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		logger.debug("Network Proxy service created...");
		
		this.acquirePreferences();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);
		
		this.myReceiver = new MyBroadcastReceiver();
		mReceiverRegistrar = new IRegisterReceiver() {
			@Override
			public Intent registerReceiver(final BroadcastReceiver aReceiver, final IntentFilter aFilter) {
				return NetworkService.this.registerReceiver(aReceiver, aFilter);
			}
			@Override
			public void unregisterReceiver(final BroadcastReceiver aReceiver) {
				NetworkService.this.unregisterReceiver(aReceiver);
			}
		};

		final IntentFilter networkFilter = new IntentFilter();
		networkFilter.addAction(INetworkBinder.ACTION_RECONNECT);
		networkFilter.addAction(INetworkBinder.ACTION_DISCONNECT);
		this.mReceiverRegistrar.registerReceiver(this.myReceiver, networkFilter);

	}

	@Override
	public void onDestroy() {
		udpSocket.close();
		// this.myReceiver.
	    try {
			journal.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.debug("Network Proxy service destroyed...");
		this.mReceiverRegistrar.unregisterReceiver(this.myReceiver);
		super.onDestroy();
	}	

	/** 
	 * Reset the local copies of the shared preference.
	 * Also indicate that the gateway connections are stale 
	 * will need to be refreshed.
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		gatewayConnectionStale = true;
		// handle network connection group
		if (key.equals(AmmoPrefKeys.PHYSICAL_LINK_SHOULD_USE_PREF_KEY)) {
			boolean enable_intent = prefs.getBoolean(AmmoPrefKeys.PHYSICAL_LINK_SHOULD_USE_PREF_KEY, true);
			if (enable_intent) this.tcpSocket.enable(); else this.tcpSocket.disable();
			this.connectChannels(true);
			return;
		}
		if (key.equals(CorePreferences.PREF_IP_ADDR)) {
			gatewayHostname = prefs.getString(CorePreferences.PREF_IP_ADDR, gatewayHostname);
			this.tcpSocket.setHost(gatewayHostname);
			this.connectChannels(true);
			return;
		}
		if (key.equals(CorePreferences.PREF_IP_PORT)) {
			gatewayPort = Integer.valueOf(prefs.getString(CorePreferences.PREF_IP_PORT, String.valueOf(gatewayPort)));
			this.tcpSocket.setPort(gatewayPort);
			connectChannels(true);
			return;
		}
		if (key.equals(CorePreferences.PREF_IS_JOURNAL)) {
			journalingSwitch = prefs.getBoolean(CorePreferences.PREF_IS_JOURNAL, journalingSwitch);
			return;
		}
		
		// handle network authentication group
		if (key.equals(CorePreferences.PREF_DEVICE_ID)) {
			deviceId = prefs.getString(CorePreferences.PREF_DEVICE_ID, deviceId);
			this.authenticateGatewayConnection();
			return;
		}
		if (key.equals(CorePreferences.PREF_OPERATOR_ID)) {
			operatorId = prefs.getString(CorePreferences.PREF_OPERATOR_ID, operatorId);
			this.authenticateGatewayConnection();
			
			// TBD SKN: broadcast login id change to apps ...
			Intent loginIntent = new Intent(ICoreService.AMMO_LOGIN);
			loginIntent.putExtra("operatorId", operatorId);
			this.sendBroadcast(loginIntent);
			
			return;
		}
		if (key.equals(CorePreferences.PREF_OPERATOR_KEY)) {
			operatorKey = prefs.getString(CorePreferences.PREF_OPERATOR_KEY, operatorKey);
			this.authenticateGatewayConnection();
			return;
		}
		if (key.equals(CorePreferences.PREF_SOCKET_TIMEOUT)) {
			Integer timeout = Integer.valueOf(prefs.getString(CorePreferences.PREF_SOCKET_TIMEOUT, "3000"));
			this.tcpSocket.setSocketTimeout(timeout.intValue());
		}
		return;
	}
	
	/**
	 * Connect all channels indiscriminately.
	 * 
	 * @return
	 */
	private boolean connectChannels(boolean reconnect) {
		
		boolean tcp = connectTcpChannel(reconnect);
        //boolean udp = connectUdpChannel();
        if (tcp)
        	distributor.repostToGateway();
		return tcp; //&& udp;
	}
	
	/**
	 * Connect the tcp socket for sending and receiving.
	 * For sending nothing special need be done outside of creating the socket.
	 * The receiving of messages needs a thread.
	 * 
	 * @return
	 */
	private boolean connectTcpChannel(boolean reconnect) {
		if (reconnect) {
			this.tcpSocket.setStale();
		}		
		if (this.tcpSocket.isConnected()) return true;
		
		tcpSocket.tryConnect();
		
		if (! tcpSocket.isConnected()) {
			String msg = "could not connect to "+gatewayHostname+" on port "+gatewayPort;
			// Toast.makeText(NetworkService.this,msg, Toast.LENGTH_SHORT).show();
			logger.warn(msg);
			return false;
		}
		
		String msg = "connected to "+gatewayHostname+" on port "+gatewayPort;
		// TBD SKN: broadcast ammo connected to apps ...
		Intent connIntent = new Intent(ICoreService.AMMO_CONNECTED);
		connIntent.putExtra("operatorId", operatorId);
		this.sendBroadcast(connIntent);
		
		// Toast.makeText(NetworkService.this,msg, Toast.LENGTH_SHORT).show();
		authenticateGatewayConnection();
		tcpSocket.startReceiverThread();
		return true;
	}
	
	/**
	 * Connect the tcp socket for sending and receiving.
	 * For sending nothing special need be done outside of creating the socket.
	 * The receiving of messages needs a thread.
	 * 
	 * @return
	 */
	private boolean connectUdpChannel() {
		if (udpSocket != null) {
			if (udpSocket.isConnected()) return true;
		}
		
		try {
			udpSocket = new DatagramSocket(gatewayPort);
		} catch (IOException e) {
			udpSocket = null;
		}
		if (udpSocket == null) {
			String msg = "could not connect to "+gatewayHostname+" on port "+gatewayPort;
			Toast.makeText(NetworkService.this,msg, Toast.LENGTH_SHORT).show();
			logger.warn(msg);
			return false;
		}
		authenticateGatewayConnection();
		// udpReceiverThread = UdpReceiverThread.getInstance(this, distributor, udpSocket);
		return true;
	}
	
	/**
	 * Connect all channels indiscriminately.
	 */
	
	private boolean disconnectChannels() {
		return (disconnectTcpChannel() &&
				disconnectUdpChannel());
	}
	/**
	*  When tearing down, we should close the thread's socket from here
	*  so if the socket is blocking, the thread can still exit.
	*/
   private boolean disconnectTcpChannel() { return this.tcpSocket.disconnect(); }
   
   private boolean disconnectUdpChannel() { return true; }
	

	// ===========================================================
	// Networking
	// ===========================================================
	
	/**
	 * Read the system preferences for the network connection information.
	 */
	private void acquirePreferences() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		
		gatewayHostname = prefs.getString(CorePreferences.PREF_IP_ADDR, gatewayHostname);
		gatewayConnectionStale = true;
		gatewayPort = Integer.valueOf(prefs.getString(CorePreferences.PREF_IP_PORT, String.valueOf(gatewayPort)));
		
		journalingSwitch = prefs.getBoolean(CorePreferences.PREF_IS_JOURNAL, journalingSwitch);
		
		deviceId = prefs.getString(CorePreferences.PREF_DEVICE_ID, deviceId);
		operatorId = prefs.getString(CorePreferences.PREF_OPERATOR_ID, operatorId);
		operatorKey = prefs.getString(CorePreferences.PREF_OPERATOR_KEY, operatorKey);
	}
	
	// ===========================================================
	// Protocol Buffers Methods
	// ===========================================================
	
	/**
	 * Authentication requests are sent via TCP.
	 * They are primarily concerned with obtaining the sessionId.
	 */
	private AmmoMessages.MessageWrapper.Builder buildAuthenticationRequest() {
		AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
		mw.setSessionUuid(sessionId);
		
		AmmoMessages.AuthenticationMessage.Builder authreq = AmmoMessages.AuthenticationMessage.newBuilder();
		authreq.setDeviceId(deviceId)
		       .setUserId(operatorId)
		       .setUserKey(operatorKey);
		
		mw.setAuthenticationMessage(authreq);
		return mw;
	}
	
	/**
	 * Get the session id set by the gateway.
	 * 
	 * @param mw
	 * @return
	 */
	private boolean receiveAuthenticationResponse(AmmoMessages.MessageWrapper mw) {
		if (mw == null) return false;
		if (! mw.hasAuthenticationResult()) return false;
		if (mw.getAuthenticationResult().getResult() != AmmoMessages.AuthenticationResult.Status.SUCCESS) return false;
		
		sessionId = mw.getSessionUuid();

		// the distributor doesn't need to know about authentication results.
		return true;
	}
	
	/**
	 * Push requests are set via UDP.
	 * (PushRequest := DataMessage)
	 * 
	 * @param uri
	 * @param mimeType
	 * @param data
	 * @return
	 */
	private AmmoMessages.MessageWrapper.Builder buildPushRequest(String uri, String mimeType, byte[] data) 
	{
		AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType(AmmoMessages.MessageWrapper.MessageType.DATA_MESSAGE);
		mw.setSessionUuid(sessionId);
		
		AmmoMessages.DataMessage.Builder pushReq = AmmoMessages.DataMessage.newBuilder();
		pushReq.setUri(uri)
		       .setMimeType(mimeType)
		       .setData(ByteString.copyFrom(data));
		
		mw.setDataMessage(pushReq);
		return mw;
	}	
	
	/**
	 * Get response to PushRequest from the gateway.
	 * (PushResponse := PushAcknowledgement)
	 * 
	 * @param mw
	 * @return
	 */
	private boolean receivePushResponse(AmmoMessages.MessageWrapper mw) {
		if (mw == null) return false;
		if (! mw.hasPushAcknowledgement()) return false;
		PushAcknowledgement pushResp = mw.getPushAcknowledgement();
		
		return distributor.dispatchPushResponse(pushResp);
	}
	
	/**
	 * Pull requests are set via UDP.
	 * 
	 * @param uri
	 * @param mimeType
	 * @param data
	 * @return
	 */
	private AmmoMessages.MessageWrapper.Builder buildRetrievalRequest(String uuid, String mimeType, String query) 
	{
		AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType(AmmoMessages.MessageWrapper.MessageType.PULL_REQUEST);
		mw.setSessionUuid(sessionId);
		
		AmmoMessages.PullRequest.Builder pushReq = AmmoMessages.PullRequest.newBuilder();
		
		pushReq.setRequestUid(uuid)
		       .setDeviceId(deviceId)
		       .setMimeType(mimeType);
		
		if (query != null) pushReq.setQuery(query);
		
		// projection
		// max_results
		// start_from_count
		// live_query
		// expiration
		
		mw.setPullRequest(pushReq);
		return mw;
	}	
	
	/**
	 * Get response to RetrievalRequest, PullResponse, from the gateway.
	 * 
	 * @param mw
	 * @return
	 */
	private boolean receivePullResponse(AmmoMessages.MessageWrapper mw) {
		if (mw == null) return false;
		if (! mw.hasPullResponse()) return false;
		final AmmoMessages.PullResponse pullResp = mw.getPullResponse();
		
		return distributor.dispatchRetrievalResponse(pullResp);
	}
	
	private AmmoMessages.MessageWrapper.Builder buildSubscribeRequest(String mimeType, String query) 
	{
		AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType(AmmoMessages.MessageWrapper.MessageType.SUBSCRIBE_MESSAGE);
		mw.setSessionUuid(sessionId);
		
		AmmoMessages.SubscribeMessage.Builder subscribeReq = AmmoMessages.SubscribeMessage.newBuilder();
		
		subscribeReq.setMimeType(mimeType);
		
		if (subscribeReq != null) subscribeReq.setQuery(query);
		
		mw.setSubscribeMessage(subscribeReq);
		return mw;
	}	
	
	private boolean receiveSubscribeResponse(AmmoMessages.MessageWrapper mw) {
		if (mw == null) return false;
		if (! mw.hasDataMessage()) return false;
		final AmmoMessages.DataMessage subscribeResp = mw.getDataMessage();
		
		return distributor.dispatchSubscribeResponse(subscribeResp);
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
	 * @param checksum
	 * @param message
	 */
	private boolean sendGatewayRequest(Carrier carrier, int size, int checksum, byte[] message) 
	{
		switch (carrier) {
		case TCP:		
			return this.tcpSocket.sendGatewayRequest(size, checksum, message);
		//case UDP:		
		//	return this.udpSocket.sendGatewayRequest(size, checksum, message);
		// case JOURNAL: dos = new DataOutputStream(udpSocket.get);
		// break;
		// default: dos = new DataOutputStream(udpSocket); break;
		}
		return false;
	}
	
	// ===========================================================
	// Helper classes
	// ===========================================================
	
	/**
	 * Store the size and checksum of a data array into a map.
	 * The size and checksum are followed by the content which is a 
	 * protocol buffer of type MessageWrapper. 
	 * 
	 * @param data
	 * @param isLittleEndian
	 * @return
	 */
	static public class MsgHeader {
		public final int size;
		public final int checksum;
	
		private MsgHeader(int size, int crc32) {
			this.size = size;
			this.checksum = crc32;
		}
		
		static public MsgHeader getInstance(byte[] data, boolean isLittleEndian) {
			CRC32 crc32 = new CRC32();
			crc32.update(data);
			return new MsgHeader(data.length, (int)crc32.getValue());
		}
	}
	
	
	
	/**
	 *  Processes and delivers a message from the gateway.
	 *  
	 * @param instream
	 * @return was the message clean (true) or garbled (false).
	 */
	public boolean deliverGatewayResponse(byte[] message, CRC32 checksum) 
	{
		CRC32 crc32 = new CRC32();
		crc32.update(message);
		if (crc32.getValue() != checksum.getValue()) {
			String msg = "you have received a bad message, the checksums did not match)"+ 
			crc32.toString() +":"+checksum.toString();
			Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
			logger.warn(msg);
			return false;
		}

		AmmoMessages.MessageWrapper mw = null;
		try {
			mw = AmmoMessages.MessageWrapper.parseFrom(message);
		} catch (InvalidProtocolBufferException ex) {
			ex.printStackTrace();
		}
		if (mw == null) return false; // TBD SKN: this was true, why? if we can't parse it then its bad
		
		switch (mw.getType()) {
		
		case DATA_MESSAGE:
			receiveSubscribeResponse(mw);
			break;
			
		case AUTHENTICATION_RESULT:
			receiveAuthenticationResponse(mw);
			break;
	   
		case PUSH_ACKNOWLEDGEMENT:
			receivePushResponse(mw);
			break;
	   
		case PULL_RESPONSE:
			receivePullResponse(mw);
			break;
		}
		return true;
	}
	
	/**
	 * Write the size, checksum, and byte array to the journal.
	 * 
	 * @param MsgHeader
	 * @param messageByteArray
	 */
	public void writeMessageToJournal(MsgHeader MsgHeader, byte[] messageByteArray) {
		if (! isJournaling()) return;
		setupJournal();
		try {
			journal.write(MsgHeader.size);
			journal.write(MsgHeader.checksum);
			journal.write(messageByteArray);
			journal.flush();
		} catch (IOException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		} catch (NullPointerException ex) {
			ex.printStackTrace();
	    } 
	}
	
	
	// ===============================================================
	// BINDING CALLS (NetworkBinder)
	// 
	// These may be called internally but they are intended to be 
	// called by the distributor service.
	// ===============================================================
	
	/**
	 * This method is called just prior to onDestroy or when the
	 * service is being intentionally shut down.
	 */
	public void teardown() {

			try {
				journal.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			
			logger.debug("Tearing down NPS");
			disconnectChannels();
			
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
		if (gatewayConnectionStale) {
			return tcpSocket.isConnected();
		}
		if (tcpSocket == null) return false;
		return tcpSocket.isConnected();
	}
	
	public boolean authenticateGatewayConnection() {
		if (! isConnected()) return false;
		
		/** Message Building */
		AmmoMessages.MessageWrapper.Builder mwb = buildAuthenticationRequest();
		byte[] protocByteBuf = mwb.build().toByteArray();
		MsgHeader msgHeader = MsgHeader.getInstance(protocByteBuf, true);
		
		return sendGatewayRequest(Carrier.TCP, msgHeader.size, msgHeader.checksum, protocByteBuf);
	}
	
	public boolean dispatchPushRequestToGateway(String uri, String mimeType, byte []data) {
		if (! isConnected() && ! isJournaling()) return false;
		
		Long now = System.currentTimeMillis();
		logger.debug(String.format("Building MessageWrapper: data size %d @ time %d", data.length, now));
		AmmoMessages.MessageWrapper.Builder mwb = buildPushRequest(uri, mimeType, data);
		logger.debug(String.format("Finished wrap build @ time %d...difference of %d ms \n",System.currentTimeMillis(), System.currentTimeMillis()-now));
		byte[] protocByteBuf = mwb.build().toByteArray();

		MsgHeader msgHeader = MsgHeader.getInstance(protocByteBuf, true);
		this.writeMessageToJournal(msgHeader, protocByteBuf);

		return sendGatewayRequest(Carrier.TCP, msgHeader.size, msgHeader.checksum, protocByteBuf);
	}
	
	public boolean dispatchRetrievalRequestToGateway(String subscriptionId, String mimeType, String selection) {
		if (! isConnected()) return false; 
		
		/** Message Building */
		AmmoMessages.MessageWrapper.Builder mwb = buildRetrievalRequest(subscriptionId, mimeType, selection);
		byte[] protocByteBuf = mwb.build().toByteArray();
		MsgHeader msgHeader = MsgHeader.getInstance(protocByteBuf, true);

		sendGatewayRequest(Carrier.TCP, msgHeader.size, msgHeader.checksum, protocByteBuf);
		return true;
	}
	
	public boolean dispatchSubscribeRequestToGateway(String mimeType, String selection) {
		if (! isConnected()) return false; 
		
		/** Message Building */
		AmmoMessages.MessageWrapper.Builder mwb = buildSubscribeRequest(mimeType, selection);
		byte[] protocByteBuf = mwb.build().toByteArray();
		MsgHeader msgHeader = MsgHeader.getInstance(protocByteBuf, true);

		sendGatewayRequest(Carrier.TCP, msgHeader.size, msgHeader.checksum, protocByteBuf);
		return true;
	}
	
	public void setDistributorServiceCallback(IDistributorService callback) {
		distributor = callback;
		// there is now someplace to send the responses.
		// connectChannels(true);
	}
	
	private class MyBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(final Context aContext, final Intent aIntent) {

			final String action = aIntent.getAction();
			logger.info("onReceive: " + action);

			if (INetworkBinder.ACTION_RECONNECT.equals(action)) {
				NetworkService.this.connectChannels(true);
				return;
			}
			if (INetworkBinder.ACTION_DISCONNECT.equals(action)) {
				NetworkService.this.disconnectChannels();
				return;
			}
			return;
		}
	}
	
}

/**
 *
 */
package edu.vu.isis.ammo.core.network;

import java.util.ArrayList;
import java.util.List;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;


import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IPrefKeys;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.ApplicationEx;
import edu.vu.isis.ammo.core.distributor.IDistributorService;
import edu.vu.isis.ammo.core.ethertracker.EthTrackSvc;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.model.PhoneNetlink;
import edu.vu.isis.ammo.core.model.WifiNetlink;
import edu.vu.isis.ammo.core.model.WiredNetlink;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
import edu.vu.isis.ammo.core.ui.GatewayAdapter;
import edu.vu.isis.ammo.core.ui.NetlinkAdapter;
import edu.vu.isis.ammo.util.IRegisterReceiver;
import edu.vu.isis.ammo.util.UniqueIdentifiers;


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
implements OnSharedPreferenceChangeListener,
           INetworkService,
           INetworkService.OnSendMessageHandler,
           IChannelManager
{
    // ===========================================================
    // Constants
    // ===========================================================
    private static final Logger logger = LoggerFactory.getLogger( NetworkService.class );

    // Local constants
    public static final String DEFAULT_GATEWAY_HOST = "129.59.2.25";
    public static final int DEFAULT_GATEWAY_PORT = 32869;
    public static final int DEFAULT_FLAT_LINE_TIME = 20; // 20 minutes
    public static final int DEFAULT_SOCKET_TIMEOUT = 3; // 3 seconds

    @SuppressWarnings("unused")
    private static final String NULL_CHAR = "\0";
    @SuppressWarnings("unused")
    private static final int UDP_BUFFER_SIZE = 4096;

    public static enum NPSReturnCode {
        NO_CONNECTION, SOCKET_EXCEPTION, UNKNOWN, BAD_MESSAGE, OK
    };

    public static final String SIZE_KEY = "sizeByteArrayKey";
    public static final String CHECKSUM_KEY = "checksumByteArrayKey";

    public enum Carrier { UDP , TCP }

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


    //Determine if the connection is enabled
    private boolean gatewayEnabled = true;
    // for providing networking support
    // should this be using IPv6?
    private boolean networkingSwitch = true;
    public boolean isNetworking() { return networkingSwitch; }
    public void setNetworkingSwitch(boolean value) { networkingSwitch = value; }
    public boolean getNetworkingSwitch() { return networkingSwitch; }
    public boolean toggleNetworkingSwitch() { return networkingSwitch = networkingSwitch ? false : true; }

    private IDistributorService distributor;

    // Channels
    private INetChannel tcpChannel = TcpChannel.getInstance(this);
    private INetChannel journalChannel = JournalChannel.getInstance(this);

    private MyBroadcastReceiver myReceiver = null;
    private IRegisterReceiver mReceiverRegistrar = new IRegisterReceiver() {
        @Override
        public Intent registerReceiver(final BroadcastReceiver aReceiver, final IntentFilter aFilter) {
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
    private ApplicationEx getApplicationEx() {
        if (this.application == null)
            this.application = (ApplicationEx)this.getApplication();
        return this.application;
    }

    public class MyBinder extends Binder {
        public NetworkService getService() {
            logger.trace("MyBinder::getService");
            return NetworkService.this;
        }
    }

    /**
     * Class for clients to access.
     * This service always runs in the same process as its clients.
     * So no inter-*process* communication is needed.
     */
    @Override
    public IBinder onBind(Intent arg0) {
        logger.trace("MyBinder::onBind {}", Thread.currentThread().toString());
        return binder;
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
     * When the service is first created, we should grab
     * the IP and Port values from the SystemPreferences.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        logger.info("onCreate");

        mGateways.add( Gateway.getInstance( getBaseContext() ));

        mNetlinks.add( WifiNetlink.getInstance( getBaseContext() ));
        mNetlinks.add( WiredNetlink.getInstance( getBaseContext() ));
        mNetlinks.add( PhoneNetlink.getInstance( getBaseContext() ));

        // no point in enabling the socket until the preferences have been read
        this.tcpChannel.disable();  //
        this.acquirePreferences();
        if (this.networkingSwitch && this.gatewayEnabled)
            this.tcpChannel.enable();   //

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        this.myReceiver = new MyBroadcastReceiver();

        final IntentFilter networkFilter = new IntentFilter();
        networkFilter.addAction(INetworkService.ACTION_RECONNECT);
        networkFilter.addAction(INetworkService.ACTION_DISCONNECT);

        networkFilter.addAction(AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE);
        networkFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        networkFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        networkFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        networkFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        networkFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

        this.mReceiverRegistrar.registerReceiver(this.myReceiver, networkFilter);

        mListener = new PhoneStateListener()
        {
            public void onDataConnectionStateChanged( int state )
            {
                logger.info( "PhoneReceiver::onCallStateChanged()" );
                mNetlinks.get( 2 ).updateStatus();
                netlinkStatusChanged();
            }
        };
        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService( Context.TELEPHONY_SERVICE );
        tm.listen( mListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE );

        // get updates as they happen
        //this.phoneReceiver = new PhoneReceiver();
        //IntentFilter phoneFilter = new IntentFilter( TelephonyManager.ACTION_PHONE_STATE_CHANGED );
        //getBaseContext().registerReceiver(this.phoneReceiver, phoneFilter);
    }

    @Override
    public void onDestroy() {
        logger.warn("::onDestroy");
        this.tcpChannel.disable();
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        this.journalingSwitch = prefs.getBoolean(INetPrefKeys.CORE_IS_JOURNALED, this.journalingSwitch);

        this.gatewayEnabled = prefs.getBoolean(INetPrefKeys.GATEWAY_SHOULD_USE, true);
        this.networkingSwitch = prefs.getBoolean(INetPrefKeys.NET_CONN_PREF_SHOULD_USE, this.networkingSwitch);

        this.deviceId = prefs.getString(INetPrefKeys.CORE_DEVICE_ID, this.deviceId);
        this.operatorId = prefs.getString(INetPrefKeys.CORE_OPERATOR_ID, this.operatorId);
        this.operatorKey = prefs.getString(INetPrefKeys.CORE_OPERATOR_KEY, this.operatorKey);

        String gatewayHostname = prefs.getString(INetPrefKeys.CORE_IP_ADDR, DEFAULT_GATEWAY_HOST);
        this.tcpChannel.setHost(gatewayHostname);

        String gatewayPortStr =prefs.getString(INetPrefKeys.CORE_IP_PORT, String.valueOf(DEFAULT_GATEWAY_PORT));
        int gatewayPort = Integer.valueOf(gatewayPortStr);
        this.tcpChannel.setPort(gatewayPort);

        String flatLineTimeStr = prefs.getString(INetPrefKeys.NET_CONN_FLAT_LINE_TIME, String.valueOf(DEFAULT_FLAT_LINE_TIME));
        long flatLineTime = Integer.valueOf(flatLineTimeStr);
        this.tcpChannel.setFlatLineTime(flatLineTime * 60 * 1000); // convert minutes into milliseconds
    }

    /**
     * Reset the local copies of the shared preference.
     * Also indicate that the gateway connections are stale
     * will need to be refreshed.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        logger.info("::onSharedPreferenceChanged {}", key);

        if (key.equals(INetPrefKeys.CORE_IP_ADDR)) {
            String gatewayHostname = prefs.getString(INetPrefKeys.CORE_IP_ADDR, DEFAULT_GATEWAY_HOST);
            this.tcpChannel.setHost(gatewayHostname);
            return;
        }
        if (key.equals(INetPrefKeys.CORE_IP_PORT)) {
            int gatewayPort = Integer.valueOf(prefs.getString(INetPrefKeys.CORE_IP_PORT, String.valueOf(DEFAULT_GATEWAY_PORT)));
            this.tcpChannel.setPort(gatewayPort);
            return;
        }
        if (key.equals(INetPrefKeys.CORE_IS_JOURNALED)) {
            this.journalingSwitch = prefs.getBoolean(INetPrefKeys.CORE_IS_JOURNALED, this.journalingSwitch);
            if (this.journalingSwitch)
                 this.journalChannel.enable();
            else this.journalChannel.disable();
            return;
        }

        // handle network authentication group
        if (key.equals(INetPrefKeys.CORE_DEVICE_ID)) {
            deviceId = prefs.getString(INetPrefKeys.CORE_DEVICE_ID, deviceId);
            if (this.isConnected()) this.auth();
            return;
        }
        if (key.equals(IPrefKeys.CORE_OPERATOR_ID)) {
            operatorId = prefs.getString(IPrefKeys.CORE_OPERATOR_ID, operatorId);
            if (this.isConnected()) this.auth(); // TBD SKN: this should really do a setStale rather than just authenticate
            return;
        }
        if (key.equals(INetPrefKeys.CORE_OPERATOR_KEY)) {
            operatorKey = prefs.getString(INetPrefKeys.CORE_OPERATOR_KEY, operatorKey);
            if (this.isConnected()) this.auth();
            return;
        }

        if (key.equals(INetPrefKeys.CORE_SOCKET_TIMEOUT)) {
            Integer timeout = Integer.valueOf(prefs.getString(INetPrefKeys.CORE_SOCKET_TIMEOUT, String.valueOf(DEFAULT_SOCKET_TIMEOUT)));
            this.tcpChannel.setSocketTimeout(timeout.intValue() * 1000); // convert seconds into milliseconds
        }

        // handle network connectivity group
//      if (key.equals(INetPrefKeys.WIRED_PREF_SHOULD_USE)) {
//          shouldUse(prefs);
//      }
//      if (key.equals(INetPrefKeys.WIFI_PREF_SHOULD_USE)) {
//          shouldUse(prefs);
//      }
        if (key.equals(INetPrefKeys.NET_CONN_PREF_SHOULD_USE)) {
            logger.info("explicit opererator reset on channel");
            this.networkingSwitch = true;
            this.tcpChannel.reset();
        }

        if (key.equals(INetPrefKeys.NET_CONN_FLAT_LINE_TIME)) {
            long flatLineTime = Integer.valueOf(prefs.getString(INetPrefKeys.NET_CONN_FLAT_LINE_TIME, String.valueOf(DEFAULT_FLAT_LINE_TIME)));
            this.tcpChannel.setFlatLineTime(flatLineTime * 60 * 1000); // convert from minutes to milliseconds
        }

        if(key.equals(INetPrefKeys.GATEWAY_SHOULD_USE))
        {
            if(prefs.getBoolean(key, true))
            {

                this.tcpChannel.enable();
            }
            else
            {
                this.tcpChannel.disable();
            }
        }
        return;
    }

    // ===========================================================
    // Protocol Buffers Methods
    // ===========================================================

    /**
     * Authentication requests are sent via TCP.
     * They are primarily concerned with obtaining the sessionId.
     */
    private AmmoMessages.MessageWrapper.Builder buildAuthenticationRequest() {
        logger.info("::buildAuthenticationRequest");

        AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType(AmmoMessages.MessageWrapper.MessageType.AUTHENTICATION_MESSAGE);
        mw.setSessionUuid(sessionId);

        AmmoMessages.AuthenticationMessage.Builder authreq = AmmoMessages.AuthenticationMessage.newBuilder();
        authreq.setDeviceId(UniqueIdentifiers.device(this.getApplicationContext()))
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
        logger.info("::receiveAuthenticationResponse");

        if (mw == null) return false;
        if (! mw.hasAuthenticationResult()) return false;
        if (mw.getAuthenticationResult().getResult() != AmmoMessages.AuthenticationResult.Status.SUCCESS) {
            return false;
        }
        PreferenceManager
            .getDefaultSharedPreferences(this)
            .edit()
            .putBoolean(INetPrefKeys.NET_CONN_PREF_IS_ACTIVE, true)
            .commit();
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
        logger.info("::buildPushRequest");

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
        logger.info("::receivePushResponse");

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
        logger.info("::buildRetrievalRequest");

        AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType(AmmoMessages.MessageWrapper.MessageType.PULL_REQUEST);
        mw.setSessionUuid(sessionId);

        AmmoMessages.PullRequest.Builder pushReq = AmmoMessages.PullRequest.newBuilder();

        pushReq.setRequestUid(uuid)
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
        logger.info("::receivePullResponse");

        if (mw == null) return false;
        if (! mw.hasPullResponse()) return false;
        final AmmoMessages.PullResponse pullResp = mw.getPullResponse();

        return distributor.dispatchRetrievalResponse(pullResp);
    }

    private AmmoMessages.MessageWrapper.Builder buildSubscribeRequest(String mimeType, String query)
    {
        logger.info("::buildSubscribeRequest");

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
        logger.info("::receiveSubscribeResponse");

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
    private boolean sendRequest(int size, CRC32 checksum, byte[] message, OnSendMessageHandler handler)
    {
        logger.info("::sendGatewayRequest");
        return this.tcpChannel.sendRequest(size, checksum, message, handler);
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
        public final CRC32 checksum;

        private MsgHeader(int size, CRC32 crc32) {
            this.size = size;
            this.checksum = crc32;
        }

        static public MsgHeader getInstance(byte[] data, boolean isLittleEndian) {
            CRC32 crc32 = new CRC32();
            crc32.update(data);
            return new MsgHeader(data.length, crc32);
        }
    }

    /**
     *  Processes and delivers messages received from the gateway.
     *
     * @param instream
     * @return was the message clean (true) or garbled (false).
     */
    public boolean deliver(byte[] message, long checksum)
    {
        logger.info("::deliverGatewayResponse");

        CRC32 crc32 = new CRC32();
        crc32.update(message);
        if (crc32.getValue() != checksum) {
            String msg = "you have received a bad message, the checksums did not match)"+
            Long.toHexString(crc32.getValue()) +":"+ Long.toHexString(checksum);
            // Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            logger.warn(msg);
            return false;
        }

        AmmoMessages.MessageWrapper mw = null;
        try {
            mw = AmmoMessages.MessageWrapper.parseFrom(message);
        } catch (InvalidProtocolBufferException ex) {
            ex.printStackTrace();
        }
        if (mw == null)
        {
            logger.error( "mw was null!" );
            return false; // TBD SKN: this was true, why? if we can't parse it then its bad
        }

        switch (mw.getType()) {

        case DATA_MESSAGE:
            receiveSubscribeResponse(mw);
            break;

        case AUTHENTICATION_RESULT:
            boolean result = receiveAuthenticationResponse(mw);
            logger.error( "authentication result={}", result );
            break;

        case PUSH_ACKNOWLEDGEMENT:
            receivePushResponse(mw);
            break;

        case PULL_RESPONSE:
            receivePullResponse(mw);
            break;
        default:
            logger.error( "mw.getType() returned an unexpected type." );
        }
        return true;
    }

    // ===============================================================
    // BINDING CALLS (NetworkServiceBinder)
    //
    // These may be called internally but they are intended to be
    // called by the distributor service.
    // ===============================================================

    /**
     * This method is called just prior to onDestroy or when the
     * service is being intentionally shut down.
     */
    public void teardown() {
        logger.info("Tearing down NPS");
        this.tcpChannel.disable();

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
        logger.info("::isConnected");
        return tcpChannel.isConnected();
    }

    /**
     * For the following methods there is an expectation that
     * the connection has been pre-verified.
     */
    public boolean auth() {
        logger.info("::authenticate");

        /** Message Building */
        AmmoMessages.MessageWrapper.Builder mwb = buildAuthenticationRequest();
        byte[] protocByteBuf = mwb.build().toByteArray();
        MsgHeader msgHeader = MsgHeader.getInstance(protocByteBuf, true);

        sendRequest(msgHeader.size, msgHeader.checksum, protocByteBuf, this);
        return true;
    }

    public boolean dispatchPushRequest(String uri, String mimeType, byte []data, INetworkService.OnSendMessageHandler handler) {
        logger.info("::dispatchPushRequest");

        Long now = System.currentTimeMillis();
        logger.debug("Building MessageWrapper: data size {} @ time {}", data.length, now);
        AmmoMessages.MessageWrapper.Builder mwb = buildPushRequest(uri, mimeType, data);
        logger.debug("Finished wrap build @ time {}...difference of {} ms \n",System.currentTimeMillis(), System.currentTimeMillis()-now);
        byte[] protocByteBuf = mwb.build().toByteArray();

        MsgHeader msgHeader = MsgHeader.getInstance(protocByteBuf, true);
        // this.journalChannel.sendRequest(msgHeader.size, msgHeader.checksum, protocByteBuf, handler);

        boolean rc = sendRequest(msgHeader.size, msgHeader.checksum, protocByteBuf, handler);
        return rc;
    }

    public boolean dispatchRetrievalRequest(String subscriptionId, String mimeType, String selection, INetworkService.OnSendMessageHandler handler) {
        logger.info("::dispatchRetrievalRequest");

        /** Message Building */
        AmmoMessages.MessageWrapper.Builder mwb = buildRetrievalRequest(subscriptionId, mimeType, selection);
        byte[] protocByteBuf = mwb.build().toByteArray();
        MsgHeader msgHeader = MsgHeader.getInstance(protocByteBuf, true);

        return sendRequest(msgHeader.size, msgHeader.checksum, protocByteBuf, handler);
    }

    public boolean dispatchSubscribeRequest(String mimeType, String selection, INetworkService.OnSendMessageHandler handler) {
        logger.info("::dispatchSubscribeRequest");

        /** Message Building */
        AmmoMessages.MessageWrapper.Builder mwb = buildSubscribeRequest(mimeType, selection);
        byte[] protocByteBuf = mwb.build().toByteArray();
        MsgHeader msgHeader = MsgHeader.getInstance(protocByteBuf, true);

        return sendRequest(msgHeader.size, msgHeader.checksum, protocByteBuf, handler);
    }

    public void setDistributorServiceCallback(IDistributorService callback) {
        logger.info("::setDistributorServiceCallback");

        distributor = callback;
        // there is now someplace to send the received messages.
        //connectChannels(false); // was true - why should we reconnect if a distributor call back changes
    }

    /**
     * This should handle the link state behavior.
     * This is really the main job of the Network service;
     * matching up links with channels.
     *
     */
    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent aIntent) {
            final String action = aIntent.getAction();
            logger.debug("onReceive: {}", action);

            if (AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE.equals(action)){
                int state = aIntent.getIntExtra("state", 0);

                // Should we be doing this here? It's not parallel with the wifi and 3G below.
                if (state != 0) {
                    switch (state) {
                    case AmmoIntents.LINK_UP:
                        logger.info("onReceive: Link UP " + action);
                        tcpChannel.linkUp();
                        break;
                    case AmmoIntents.LINK_DOWN:
                        logger.info("onReceive: Link DOWN " + action);
                        tcpChannel.linkDown();
                        break;
                    }
                }

                // This intent comes in for both wired and wifi.
                mNetlinks.get( 0 ).updateStatus();
                mNetlinks.get( 1 ).updateStatus();
                netlinkStatusChanged();
                return;
            }
            else if ( WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)
                      || WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
                      || WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION.equals(action)
                      || WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action) )
            {
                logger.warn( "WIFI state changed" );
                mNetlinks.get( 0 ).updateStatus();
                mNetlinks.get( 1 ).updateStatus();
                netlinkStatusChanged();
                return;
            }
            else if ( TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action) )
            {
                logger.warn( "3G state changed" );
                mNetlinks.get( 2 ).updateStatus();
                netlinkStatusChanged();
                return;
            }

            //if (INetworkService.ACTION_RECONNECT.equals(action)) {
            //  //NetworkService.this.connectChannels(true);
            //  return;
            //}
            //if (INetworkService.ACTION_DISCONNECT.equals(action)) {
            //  //NetworkService.this.disconnectChannels();
            //  return;
            //}

            return;
        }
    }

    /**
     * A routine to let the distributor know that the 
     * *authentication* message was sent (or discarded).
     * The distributor is notified that a channel is available.
     */
    @Override
    public boolean ack(boolean status) {
        if (status) {   // authentication succeeded
            logger.trace("authentication complete, repost subscriptions and pending data : ");
            this.distributor.consumerReady();

            logger.info("authentication complete inform applications : ");
            // broadcast login event to apps ...
            Intent loginIntent = new Intent(INetPrefKeys.AMMO_LOGIN);
            loginIntent.putExtra("operatorId", operatorId);
            this.sendBroadcast(loginIntent);
        }
        return false;
    }

    /**
     * Deal with the status of the connection changing.
     * Report the status to the application who acts as a broker.
     */

    @Override
    public void statusChange(INetChannel channel, int connStatus, int sendStatus, int recvStatus) {
        // Once we have multiple gateways we'll have to fix this.
        mGateways.get( 0 ).setStatus( new int[]{connStatus, sendStatus, recvStatus} );

        Intent broadcastIntent = new Intent( AmmoIntents.AMMO_ACTION_GATEWAY_STATUS_CHANGE );
        this.sendBroadcast( broadcastIntent );
    }


    private void netlinkStatusChanged()
    {
        Intent broadcastIntent = new Intent( AmmoIntents.AMMO_ACTION_NETLINK_STATUS_CHANGE );
        sendBroadcast( broadcastIntent );
    }


    public boolean isWiredLinkUp()
    {
        return mNetlinks.get( 1 ).isLinkUp();
    }


    public boolean isWifiLinkUp()
    {
        return mNetlinks.get( 0 ).isLinkUp();
    }


    public boolean is3GLinkUp()
    {
        return mNetlinks.get( 2 ).isLinkUp();
    }


    public boolean isAnyLinkUp()
    {
        return isWiredLinkUp() || isWifiLinkUp() || is3GLinkUp();
    }


    private List<Gateway> mGateways = new ArrayList<Gateway>();
    private List<Netlink> mNetlinks = new ArrayList<Netlink>();

    public List<Gateway> getGatewayList()
    {
        return mGateways;
    }

    public List<Netlink> getNetlinkList()
    {
        return mNetlinks;
    }
}

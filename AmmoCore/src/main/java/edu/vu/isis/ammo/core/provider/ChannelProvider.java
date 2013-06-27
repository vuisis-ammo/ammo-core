
package edu.vu.isis.ammo.core.provider;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.IBinder;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.network.AddressedChannel;
import edu.vu.isis.ammo.core.network.IChannelObserver;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.MulticastChannel;
import edu.vu.isis.ammo.core.network.NetChannel;
import edu.vu.isis.ammo.core.network.ReliableMulticastChannel;
import edu.vu.isis.ammo.core.network.SerialChannel;
import edu.vu.isis.ammo.core.network.TcpChannel;

public class ChannelProvider extends ContentProvider implements ChannelSchema, IChannelObserver {

    private static final Logger logger = LoggerFactory.getLogger("provider.channel");
    private static final Intent AMMO_SERVICE_INTENT;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int MULTICAST_MATCH = 1;
    private static final int RELIABLE_MULTICAST_MATCH = 2;
    private static final int GATEWAY_MATCH = 3;
    private static final int GATEWAY_MEDIA_MATCH = 4;
    private static final int SERIAL_MATCH = 5;

    private static final int BPS_UPDATE_INTERVAL = 30; // seconds

    static {
        logger.trace("ChannelProvider class constructed");

        // Setup intent
        AMMO_SERVICE_INTENT = new Intent();
        final ComponentName serviceComponent =
                new ComponentName(AmmoService.class.getPackage().getName(),
                        AmmoService.class.getCanonicalName());
        AMMO_SERVICE_INTENT.setComponent(serviceComponent);

        // Setup matcher
        MATCHER.addURI(AUTHORITY, MULTICAST_PATH, MULTICAST_MATCH);
        MATCHER.addURI(AUTHORITY, RELIABLE_MULTICAST_PATH, RELIABLE_MULTICAST_MATCH);
        MATCHER.addURI(AUTHORITY, GATEWAY_PATH, GATEWAY_MATCH);
        MATCHER.addURI(AUTHORITY, GATEWAY_MEDIA_PATH, GATEWAY_MEDIA_MATCH);
        MATCHER.addURI(AUTHORITY, SERIAL_PATH, SERIAL_MATCH);
    }

    private INetworkService mNetService;
    private boolean mIsConnected = false;
    private ScheduledExecutorService mEx;

    // The network channels
    private MulticastChannel mMulticastChannel;
    private ReliableMulticastChannel mReliableMulticastChannel;
    private SerialChannel mSerialChannel;
    private TcpChannel mTcpChannel;
    private TcpChannel mTcpMediaChannel;

    private BpsCache mMulBpsCache, mRelMulBpsCache, mSerBpsCache, mTcpBpsCache,
            mTcpMediaBpsCache;

    private Runnable mUpdateBpsRunnable = new Runnable() {
        @Override
        public void run() {
            if (mMulBpsCache != null && mMulBpsCache.update()) {
                notifyUpdate(mMulticastChannel);
            }

            if (mRelMulBpsCache != null && mRelMulBpsCache.update()) {
                notifyUpdate(mReliableMulticastChannel);
            }

            if (mSerBpsCache != null && mSerBpsCache.update()) {
                notifyUpdate(mSerialChannel);
            }

            if (mTcpBpsCache != null && mTcpBpsCache.update()) {
                notifyUpdate(mTcpChannel);
            }

            if (mTcpMediaBpsCache != null && mTcpMediaBpsCache.update()) {
                notifyUpdate(mTcpMediaChannel);
            }
        }

    };

    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.debug("Service connected.");
            final AmmoService.DistributorServiceAidl binder = (AmmoService.DistributorServiceAidl) service;
            mNetService = binder.getService();

            // Get references to the channels
            mMulticastChannel = mNetService.getMulticastChannel();
            mReliableMulticastChannel = mNetService.getReliableMulticastChannel();
            mTcpChannel = mNetService.getTcpChannel();
            mTcpMediaChannel = mNetService.getTcpMedialChannel();

            // Register observer
            mMulticastChannel.registerChannelObserver(ChannelProvider.this);
            mReliableMulticastChannel.registerChannelObserver(ChannelProvider.this);
            mTcpChannel.registerChannelObserver(ChannelProvider.this);
            mTcpMediaChannel.registerChannelObserver(ChannelProvider.this);
            
            // Build caches
            mMulBpsCache = new BpsCache(mMulticastChannel);
            mRelMulBpsCache = new BpsCache(mReliableMulticastChannel);
            mTcpBpsCache = new BpsCache(mTcpChannel);
            mTcpMediaBpsCache = new BpsCache(mTcpMediaChannel);

            logger.trace("mNetService: {}", mNetService);
            mIsConnected = true;
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.debug("Service disconnected.");
            mNetService = null;
            mIsConnected = false;
        }
    };

    @Override
    public boolean onCreate() {
        boolean successful = this.getContext().bindService(AMMO_SERVICE_INTENT,
                networkServiceConnection,
                Context.BIND_AUTO_CREATE);
        logger.trace("Attempting to bind to service. Status = {}, Connected = {}",
                (successful ? "successfully bound"
                        : "failed to bind"), mIsConnected);

        mEx = Executors.newSingleThreadScheduledExecutor();
        mEx.scheduleWithFixedDelay(mUpdateBpsRunnable, BPS_UPDATE_INTERVAL, BPS_UPDATE_INTERVAL, TimeUnit.SECONDS);

        return successful;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (logger.isTraceEnabled()) {
            logger.trace("Got a query, Uri: {}", uri.toString());
        }
        if (!mIsConnected) {
            // Return a null cursor until the service connects
            return null;
        }

        /*
         * Get the serial channel at query time to ensure that NetworkManager
         * has enough time to initialize it before we get a reference to it
         */
        if (mSerialChannel == null) {
            mSerialChannel = mNetService.getSerialChannel();
            mSerialChannel.registerChannelObserver(this);
            mSerBpsCache = new BpsCache(mSerialChannel);
        }

        MatrixCursor c = new MatrixCursor(new String[] {
                ChannelColumns.NAME, ChannelColumns.FORMAL_IP,
                ChannelColumns.CONNECTION_STATE,
                ChannelColumns.SENDER_STATE,
                ChannelColumns.RECEIVER_STATE,
                ChannelColumns.SEND_RECEIVE_COUNTS,
                ChannelColumns.SEND_BIT_STATS,
                ChannelColumns.RECEIVE_BIT_STATS
        });

        switch (MATCHER.match(uri)) {
            case MULTICAST_MATCH:
                logger.trace("Query matched multicast");
                addAddressedChannelRow(c, "Multicast", mMulticastChannel);
                c.setNotificationUri(getContext().getContentResolver(), MULTICAST_URI);
                break;
            case RELIABLE_MULTICAST_MATCH:
                logger.trace("Query matched reliable multicast");
                addAddressedChannelRow(c, "ReliableMulticast", mReliableMulticastChannel);
                c.setNotificationUri(getContext().getContentResolver(), RELIABLE_MULTICAST_URI);
                break;
            case GATEWAY_MATCH:
                logger.trace("Query matched gateway");
                addAddressedChannelRow(c, "Gateway", mTcpChannel);
                c.setNotificationUri(getContext().getContentResolver(), GATEWAY_URI);
                break;
            case GATEWAY_MEDIA_MATCH:
                logger.trace("Query matched gateway media");
                addAddressedChannelRow(c, "Gateway Media", mTcpMediaChannel);
                c.setNotificationUri(getContext().getContentResolver(), GATEWAY_MEDIA_URI);
                break;
            case SERIAL_MATCH:
                logger.trace("Query matched serial");
                addSerialRow(c);
                c.setNotificationUri(getContext().getContentResolver(), SERIAL_URI);
                break;
            default:
                logger.warn("Received an invalid Uri");
                return null;
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Returning cursor {}", DatabaseUtils.dumpCursorToString(c));
        }
        return c;
    }

    private void addAddressedChannelRow(MatrixCursor c, String channelName, AddressedChannel channel) {
        c.addRow(new Object[] {
                channelName, channel.getAddress() + ":" + channel.getPort(),
                channel.getConnState(), channel.getSenderState(), channel.getReceiverState(),
                channel.getSendReceiveStats(), channel.getSendBitStats(), channel.getReceiveBitStats()
        });
    }

    private void addSerialRow(MatrixCursor c) {
        if (mSerialChannel == null) {
            logger.error("Serial channel is null");
            return;
        }

        // Since the Serial channel doesn't have a formal IP, we send
        // the error string in the formalIP field
        StringBuilder errorString = new StringBuilder();
        errorString.append("@:").append(mSerialChannel.getCorruptMessages()).append(" ");
        errorString.append(mSerialChannel.getReceiverSubstate());
        errorString.append(":").append(mSerialChannel.getBytesSinceMagic()).append(" ");
        errorString.append("N:").append(mSerialChannel.getSecondsSinceByteRead());

        c.addRow(new Object[] {
                "Serial", errorString.toString(), mSerialChannel.getConnState(),
                mSerialChannel.getSenderState(), mSerialChannel.getReceiverState(),
                mSerialChannel.getSendReceiveStats(), mSerialChannel.getSendBitStats(),
                mSerialChannel.getReceiveBitStats()
        });
    }

    @Override
    public String getType(Uri uri) {
        switch (MATCHER.match(uri)) {
            case MULTICAST_MATCH:
            case RELIABLE_MULTICAST_MATCH:
            case GATEWAY_MATCH:
            case GATEWAY_MEDIA_MATCH:
            case SERIAL_MATCH:
                return SINGLE_CHANNEL_TYPE;
            default:
                return null;
        }
    }

    /**
     * Not supported by this ContentProvider
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    /**
     * Not supported by this ContentProvider
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    /**
     * Not supported by this ContentProvider
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public void notifyUpdate(ReliableMulticastChannel channel) {
        logger.trace("Update for ReliableMulticast");
        getContext().getContentResolver().notifyChange(RELIABLE_MULTICAST_URI, null);
    }

    @Override
    public void notifyUpdate(MulticastChannel channel) {
        logger.trace("Update for Multicast");
        getContext().getContentResolver().notifyChange(MULTICAST_URI, null);
    }

    @Override
    public void notifyUpdate(TcpChannel channel) {
        if (channel == mTcpChannel) {
            logger.trace("Update for Gateway");
            getContext().getContentResolver().notifyChange(GATEWAY_URI, null);
        } else if (channel == mTcpMediaChannel) {
            logger.trace("Update for GatewayMedia");
            getContext().getContentResolver().notifyChange(GATEWAY_MEDIA_URI, null);
        } else {
            logger.warn("Unknown TCP channel {}", channel);
        }
    }

    @Override
    public void notifyUpdate(SerialChannel channel) {
        logger.trace("Update for Serial");
        getContext().getContentResolver().notifyChange(SERIAL_URI, null);
    }

    public class BpsCache {

        NetChannel mChannel;
        public String mLastSendBits, mLastReceiveBits;

        public BpsCache(NetChannel channel) {
            mChannel = channel;
            mLastSendBits = mChannel.getSendBitStats();
            mLastReceiveBits = mChannel.getReceiveBitStats();
        }

        /**
         * Get new data from the NetChannel and update the cache if necessary
         * 
         * @return true if the cache was updated
         */
        public boolean update() {
            boolean updated = false;
            String receive = mChannel.getReceiveBitStats();
            String send = mChannel.getSendBitStats();

            if (!receive.equals(mLastReceiveBits)) {
                mLastReceiveBits = receive;
                updated = true;
            }

            if (!send.equals(mLastSendBits)) {
                mLastSendBits = send;
                updated = true;
            }

            return updated;
        }

    }

}


package edu.vu.isis.ammo.core.provider;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.IBinder;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.JournalChannel;
import edu.vu.isis.ammo.core.network.MulticastChannel;
import edu.vu.isis.ammo.core.network.NetChannel;
import edu.vu.isis.ammo.core.network.ReliableMulticastChannel;
import edu.vu.isis.ammo.core.network.SerialChannel;
import edu.vu.isis.ammo.core.network.TcpChannel;

public class ChannelProvider extends ContentProvider {

    public static final Uri CONTENT_URI = Uri
            .parse("content://edu.vu.isis.ammo.core.provider.channel/Channel");

    private static final Logger logger = LoggerFactory.getLogger("provider.channel");
    private static final Intent AMMO_SERVICE_INTENT;
    static {
        logger.trace("ChannelProvider class constructed");
        AMMO_SERVICE_INTENT = new Intent();
        final ComponentName serviceComponent =
                new ComponentName(AmmoService.class.getPackage().getName(),
                        AmmoService.class.getCanonicalName());
        AMMO_SERVICE_INTENT.setComponent(serviceComponent);
    }

    private INetworkService mNetService;
    private boolean mIsConnected = false;
    
    // The network channels
    private MulticastChannel mMulticastChannel;
    private JournalChannel mJournalChannel;
    private ReliableMulticastChannel mReliableMulticastChannel;
    private SerialChannel mSerialChannel;
    private TcpChannel mTcpChannel;
    private TcpChannel mTcpMediaChannel;

    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        final private ChannelProvider parent = ChannelProvider.this;

        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.debug("Service connected.");
            final AmmoService.DistributorServiceAidl binder = (AmmoService.DistributorServiceAidl) service;
            parent.mNetService = binder.getService();
            mIsConnected = true;
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.debug("Service disconnected.");
            parent.mNetService = null;
            mIsConnected = false;
        }
    };

    @Override
    public boolean onCreate() {
        boolean successful = this.getContext().bindService(AMMO_SERVICE_INTENT,
                networkServiceConnection,
                Context.BIND_AUTO_CREATE);
        logger.trace("Attempting to bind to service. Status = {}", (successful ? "successfully bound"
                : "failed to bind"));
        
        if (successful) {
            // Get references to the channels
            mMulticastChannel = mNetService.getMulticastChannel();
            mJournalChannel = mNetService.getJournalChannel();
            mReliableMulticastChannel = mNetService.getReliableMulticastChannel();
            mTcpChannel = mNetService.getTcpChannel();
            mTcpMediaChannel = mNetService.getTcpMedialChannel();
        }
        
        return successful;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        logger.trace("Got a query");
        
        // Get the serial channel on the first query to ensure that NetworkManager
        // has enough time to initialize it before we get a reference to it
        if (mSerialChannel == null) {
            mSerialChannel = mNetService.getSerialChannel();
        }
        
        MatrixCursor c = new MatrixCursor(new String[] {
            "name", "formalIP", "state", "sendReceive"
        });
        
        c.addRow(new Object[] { "Multicast", mMulticastChannel.getAddress() + ":" + mMulticastChannel.getPort(), "Unknown", mMulticastChannel.getSendReceiveStats()});
        c.addRow(new String[] {
            "hello"
        });
        if (logger.isTraceEnabled()) {
            logger.trace("Returning cursor with contents\n\n{}",
                    DatabaseUtils.dumpCursorToString(c));
        }
        return c;
    }

    @Override
    public String getType(Uri uri) {
        // Not very relevant for our needs, so ignored for now
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // Not supported by this ContentProvider
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Not supported by this ContentProvider
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Not supported by this ContentProvider
        return 0;
    }

}

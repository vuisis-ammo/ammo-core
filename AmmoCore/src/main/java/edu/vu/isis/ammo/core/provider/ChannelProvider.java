
package edu.vu.isis.ammo.core.provider;

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

public class ChannelProvider extends ContentProvider {

    public static final Uri CONTENT_URI = Uri
            .parse("content://edu.vu.isis.ammo.core.provider.channel/Channel");

    private static final Logger logger = LoggerFactory.getLogger("provider.channel");
    private static final Intent AMMO_SERVICE;
    static {
        logger.trace("ChannelProvider class constructed");
        AMMO_SERVICE = new Intent();
        final ComponentName serviceComponent =
                new ComponentName(AmmoService.class.getPackage().getName(),
                        AmmoService.class.getCanonicalName());
        AMMO_SERVICE.setComponent(serviceComponent);
    }

    private INetworkService networkServiceBinder;
    private boolean mIsConnected = false;

    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        final private ChannelProvider parent = ChannelProvider.this;

        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.debug("Service connected.");
            final AmmoService.DistributorServiceAidl binder = (AmmoService.DistributorServiceAidl) service;
            mIsConnected = true;
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.debug("Service disconnected.");
            parent.networkServiceBinder = null;
            mIsConnected = false;
        }
    };

    @Override
    public boolean onCreate() {
        boolean status = this.getContext().bindService(AMMO_SERVICE, networkServiceConnection,
                Context.BIND_AUTO_CREATE);
        logger.trace("Attempting to bind to service. Status = {}", (status ? "successfully bound" : "failed to bind"));
        return status;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        logger.trace("Got a query");
        MatrixCursor c = new MatrixCursor(new String[] { "Col1" });
        c.addRow(new String[] {"hello" });
        if (logger.isTraceEnabled()) {
            logger.trace("Returning cursor with contents\n\n{}", DatabaseUtils.dumpCursorToString(c));
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

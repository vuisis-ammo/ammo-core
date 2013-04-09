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

package edu.vu.isis.ammo.core.provider;

import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicReference;

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
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.NetworkManager;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.store.RelationsHelper;
import edu.vu.isis.ammo.util.Genealogist;

public class DistributorProvider extends ContentProvider {
    // =================================
    // Constants
    // =================================
    private static final Logger logger = LoggerFactory.getLogger("provider.dist");

    public static final String DATABASE_NAME = DistributorDataStore.SQLITE_NAME;

    private static final UriMatcher uriMatcher;
    private static final UriMatcher garbageMatcher;
    /**
     * ordinal values are returned by the uri which may then be used to retrieve
     * the relation enum.
     */
    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        garbageMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        for (final Relations table : Relations.values()) {
            uriMatcher.addURI(DistributorSchema.AUTHORITY, table.n, table.ordinal());
            garbageMatcher.addURI(DistributorSchema.AUTHORITY, table.n + "/garbage",
                    table.ordinal());
        }
    }

    // =================================
    // setup
    // =================================

    private DistributorDataStore dds;
    private static final Intent AMMO_SERVICE;
    static {
        AMMO_SERVICE = new Intent();
        final ComponentName serviceComponent =
                new ComponentName(AmmoService.class.getPackage().getName(),
                        AmmoService.class.getCanonicalName());
        AMMO_SERVICE.setComponent(serviceComponent);
    }

    final AtomicReference<ServiceConnection> conn = new AtomicReference<ServiceConnection>(null);
    public volatile boolean isBound = false;

    /**
     * Make a local binding to the AmmoService and get the DDS.
     */
    @Override
    public boolean onCreate() {
        final ServiceConnection newConn = new ServiceConnection() {
            final DistributorProvider parent = DistributorProvider.this;

            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {

                if (!(binder instanceof AmmoService.DistributorServiceAidl)) {
                    logger.error("service failed connection type=[{}]", Genealogist.getAncestry(binder));
                    return;
                }
                logger.debug("service connected ");
                final AmmoService.DistributorServiceAidl proxy = (AmmoService.DistributorServiceAidl) binder;
                final NetworkManager service = proxy.getService();

                parent.dds = service.store();
                parent.isBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                logger.info("service disconnected {}", name);
                parent.isBound = false;
                return;
            }
        };
        
        if (!(this.conn.compareAndSet(null, newConn))) {
            logger.info("distributor provider is already connected {}", this.isBound);
            return true;
        }
        logger.info("create distributor provider");
        this.dds = null;
        final boolean isBindable = this.getContext().bindService(AMMO_SERVICE, conn.get(),
                Context.BIND_AUTO_CREATE);
        return isBindable;
    }

    @Override
    public String getType(Uri uri) {
        if (!this.isBound) {
            logger.warn("get type called before bound {}",uri);
            return null;
        }
        return null;
    }

    // =================================
    // Content Provider Overrides
    // =================================

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        logger.warn("delete from distributor provider {} {}", uri, selection);
        if (!this.isBound){
            logger.warn("delete called before service bound {}",uri, selection);
            return -1;
        }
        if (this.dds == null)
            return -1;
        logger.trace("delete on distributor provider {} {}", uri, selection);

        switch (RelationsHelper.getValue(uriMatcher.match(uri))) {
            case POSTAL:
                return dds.deletePostal(selection, selectionArgs);
            case RETRIEVAL:
                return dds.deleteRetrieval(selection, selectionArgs);
            case SUBSCRIBE:
                return dds.deleteSubscribe(selection, selectionArgs);
            case DISPOSAL:
            case CHANNEL:
                return -1;
            case PRESENCE:
                return this.dds.deletePresence();
            case CAPABILITY:
                return this.dds.deleteCapability();
            case POSTAL_DISPOSAL:
            case RECIPIENT:
            case REQUEST:
            case RETRIEVAL_DISPOSAL:
            case SUBSCRIBE_DISPOSAL:
            default:
                break;
        }

        switch (RelationsHelper.getValue(garbageMatcher.match(uri))) {
            case POSTAL:
                return dds.deletePostalGarbage();
            case RETRIEVAL:
                return dds.deleteRetrievalGarbage();
            case SUBSCRIBE:
                return dds.deleteSubscribeGarbage();
            case DISPOSAL:
            case CHANNEL:
                return -1;
            case CAPABILITY:
            case POSTAL_DISPOSAL:
            case PRESENCE:
            case RECIPIENT:
            case REQUEST:
            case RETRIEVAL_DISPOSAL:
            case SUBSCRIBE_DISPOSAL:
            default:
                break;
        }
        return -1;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        logger.warn("no inserts allowed on distributor provider {} {}", uri, values);
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (!this.isBound) {
            logger.warn("query on unbound distributor {} {}", uri, selection);
            return null;
        }
        if (this.dds == null)
            return null;

        final int uriMatch = uriMatcher.match(uri);
        if (uriMatch < 0) {
            logger.error("failed query on distributor provider {} {}", uri, selection);
            return null;
        }
        logger.trace("query on distributor provider {} {}", uri, selection);

        final Cursor cursor;
        switch (Relations.values()[uriMatch]) {
            case POSTAL:
                cursor = dds.queryPostal(projection, selection, selectionArgs, sortOrder);
                break;
            case RETRIEVAL:
                cursor = dds.queryRetrieval(projection, selection, selectionArgs, sortOrder);
                break;
            case SUBSCRIBE:
                cursor = dds.querySubscribe(projection, selection, selectionArgs, sortOrder);
                break;
            case DISPOSAL:
                cursor = dds.queryDisposal(projection, selection, selectionArgs, sortOrder);
                break;
            case CHANNEL:
                cursor = dds.queryChannel(projection, selection, selectionArgs, sortOrder);
                break;
            case PRESENCE:
                if (selection == null) {
                    cursor = this.dds.queryPresenceAll();
                } else if (PresenceSchema.WHERE_ALL.equals(selection)) {
                    cursor = this.dds.queryPresenceAll();
                } else if (PresenceSchema.WHERE_OPERATOR_IS.equals(selection)) {
                    cursor = this.dds.queryPresenceByOperator(selectionArgs[0]);
                } else if (PresenceSchema.WHERE_OPERATOR_IS_SQL.equals(selection)) {
                    cursor = this.dds.queryPresenceByOperator(selectionArgs[0]);
                } else {
                    logger.warn("unknown selection=[{}]", selection);
                    cursor = null;
                }
                break;
            case CAPABILITY:
                cursor = this.dds.queryCapabilityAll();
                break;
            default:
                // If we get here, it's a special uri and should be matched
                // differently.
                cursor = null;
        }
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        logger.warn("no updates allowed on distributor provider {} {}", uri, values);
        return 0;
    }

    /**
     * this to handle requests to open a file blob.
     */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!this.isBound) {
            logger.warn("unbound openFile from distributor provider {} {}", uri, mode);
            return null;
        }
        return super.openFile(uri, mode);
    }

}

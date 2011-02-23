package edu.vu.isis.ammo.core.distributor;


import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import android.app.PendingIntent;
import android.app.Service;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import edu.vu.isis.ammo.core.network.INetworkBinder;
import edu.vu.isis.ammo.core.pb.AmmoMessages.DataMessage;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PullResponse;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
import edu.vu.isis.ammo.core.provider.DistributorSchema.DeliveryMechanismTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;
import edu.vu.isis.ammo.core.receiver.CellPhoneListener;
import edu.vu.isis.ammo.core.receiver.WifiReceiver;
import edu.vu.isis.ammo.util.IRegisterReceiver;
import edu.vu.isis.ammo.util.InternetMediaType;


/**
 * The DistributorService is responsible for synchronization between the World
 * Server and the player's individual databases. The DistributorService will
 * issue calls to the network proxy service for updates and then writes the
 * results to the correct content provider.
 * 
 * Any activity or application wishing to receive updates when a content
 * provider has been modified can register via a custom ContentObserver
 * subclass.
 * 
 * @author Demetri Miller
 * @author Fred Eisele
 * 
 */
public class DistributorService extends Service implements IDistributorService {

    // ===========================================================
    // Constants
    // ===========================================================
    private static final Logger logger = LoggerFactory.getLogger(
            DistributorService.class);
    private static final boolean DEBUGMODE = true;

    @SuppressWarnings("unused")
    private static final int FILE_READ_SIZE = 1024;
    public static final String SERIALIZED_STRING_KEY = "serializedString";
    public static final String SERIALIZED_BYTE_ARRAY_KEY = "serializedByteArray";

    // ===========================================================
    // Fields
    // ===========================================================

    private IDistributorService callback;
    private ServiceConnection networkServiceConnection;
    private Intent networkServiceIntent = new Intent(INetworkBinder.ACTION);

    private INetworkBinder network;
    private boolean isBoundNPS = false;
    @SuppressWarnings("unused")
    private boolean onCreateCalled = false;

    private DeliveryMechanismObserver deliveryMechanismObserver;
    private PostalObserver postalObserver;
    private RetrievalObserver enrollmentObserver;
    private SubscriptionObserver subscriptionObserver;

    private TelephonyManager tm;
    private CellPhoneListener cellPhoneListener;
    private WifiReceiver wifiReceiver;

    private MyBroadcastReceiver mReadyResourceReceiver = null;
    private boolean mNetworkConnected = false;
    private boolean mSdCardAvailable = false;

    private long dispatchToastTimestamp = System.currentTimeMillis();

    // ===========================================================
    // LifeCycle
    // ===========================================================
    private IRegisterReceiver mReceiverRegistrar = null;

    // When the service is created, we should setup all services necessary to
    // maintain synchronization (updating player loop,
    @Override
    public void onCreate() {
        super.onCreate();
        this.onCreateCalled = true;
        logger.debug("service created...");

        // Set our callback.
        callback = this;

        // Set this service to observe certain Content Providers.
        // Initialize our content observer.

        postalObserver = new PostalObserver(new Handler(), callback);
        this.getContentResolver().registerContentObserver(
                PostalTableSchema.CONTENT_URI, false, postalObserver);

        deliveryMechanismObserver = new DeliveryMechanismObserver(new Handler(),
                callback);
        this.getContentResolver().registerContentObserver(
                DeliveryMechanismTableSchema.CONTENT_URI, false,
                deliveryMechanismObserver);

        enrollmentObserver = new RetrievalObserver(new Handler(), callback);
        this.getContentResolver().registerContentObserver(
                RetrievalTableSchema.CONTENT_URI, false, enrollmentObserver);

        subscriptionObserver = new SubscriptionObserver(new Handler(), callback);
        this.getContentResolver().registerContentObserver(
                SubscriptionTableSchema.CONTENT_URI, false, subscriptionObserver);

        // Initialize our receivers/listeners.
        /*
         wifiReceiver = new WifiReceiver();
         cellPhoneListener = new CellPhoneListener(this);
         tm = (TelephonyManager) this .getSystemService(Context.TELEPHONY_SERVICE);
         tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_DATA_ACTIVITY | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
         */
		
        // Listen for changes to resource availability
        this.mReadyResourceReceiver = new MyBroadcastReceiver();
        this.mReceiverRegistrar = new IRegisterReceiver() {
            @Override
            public Intent registerReceiver(final BroadcastReceiver aReceiver, final IntentFilter aFilter) {
                return DistributorService.this.registerReceiver(aReceiver,
                        aFilter);
            }

            @Override
            public void unregisterReceiver(final BroadcastReceiver aReceiver) {
                DistributorService.this.unregisterReceiver(aReceiver);
            }
        };
        final IntentFilter networkFilter = new IntentFilter();

        networkFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        networkFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mReceiverRegistrar.registerReceiver(mReadyResourceReceiver,
                networkFilter);

        final IntentFilter mediaFilter = new IntentFilter();

        mediaFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mediaFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mediaFilter.addDataScheme("file");
        mReceiverRegistrar.registerReceiver(mReadyResourceReceiver, mediaFilter);
		
        mReadyResourceReceiver.checkResourceStatus(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.debug("service started...");
        // If we get this intent, unbind from all services 
        // so the service can be stopped.
        if (intent == null) {
            callback = this;
            this.bindToNetworkService();
            return START_STICKY;
        }
        if (intent.getAction().equals(DistributorService.PREPARE_FOR_STOP)) {
            this.teardownService();
            return START_NOT_STICKY;
        }
        callback = this;
        this.bindToNetworkService();
        return START_STICKY;
    }

    public void teardownService() {
        logger.debug("service torn down...");
        if (!isBoundNPS) {
            return;
        }
		
        logger.debug("service unbinding from network proxy service");
        // Use our binding for notifying the NPS of teardown.
        if (network != null) {
            network.teardown();
        }
        this.unbindService(networkServiceConnection);
        isBoundNPS = false;
    }

    public void finishTeardown() {
        logger.debug("service teardown finished");
        this.stopSelf();
    }

    @Override
    public void onDestroy() {
        logger.debug("service destroyed...");
        if (isBoundNPS) {
            this.unbindService(networkServiceConnection);
            isBoundNPS = false;
        }
        this.stopService(networkServiceIntent);
        tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_NONE);
        wifiReceiver.setInitialized(false);
        this.getContentResolver().unregisterContentObserver(postalObserver);
        this.getContentResolver().unregisterContentObserver(
                deliveryMechanismObserver);
        this.getContentResolver().unregisterContentObserver(enrollmentObserver);
        this.getContentResolver().unregisterContentObserver(subscriptionObserver);
        this.mReceiverRegistrar.unregisterReceiver(this.mReadyResourceReceiver);
		
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean sendPendingIntent(byte[] notice) {
        if (notice == null) {
            return false;
        }
        Parcel noticeParcel = Parcel.obtain();

        noticeParcel.unmarshall(notice, 0, notice.length);
        PendingIntent pi = PendingIntent.readPendingIntentOrNullFromParcel(
                noticeParcel);

        try {
            pi.send();
        } catch (CanceledException e) {
            logger.error("could not process marshalled pending intent");
            return false;
        }
        return true;
    }

    // ===========================================================
    // IDistributorService implementation
    // ===========================================================

    /**
     * Make a specialized query on a specific content provider URI 
     * to get back that row in serialized form
     * 
     * @param uri
     * @return
     * @throws IOException
     */
    private byte[] queryUriForSerializedData(String uri) throws IOException {
        Uri rowUri = Uri.parse(uri);
        Uri serialUri = Uri.withAppendedPath(rowUri, "_serial");

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        BufferedInputStream bis = null;
        InputStream instream = null;

        try {
            try {
                // instream = this.getContentResolver().openInputStream(serialUri);
                AssetFileDescriptor afd = this.getContentResolver().openAssetFileDescriptor(
                        serialUri, "r");
                // afd.createInputStream();
        		
                ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();

                instream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            } catch (IOException e) {
                throw new FileNotFoundException("Unable to create stream");
            }
            // if (instream)
            bis = new BufferedInputStream(instream);
            
            for (int bytesRead = 0; (bytesRead = bis.read(buffer)) != -1;) {
                bout.write(buffer, 0, bytesRead);
            }
            bis.close();
            // String bs = bout.toString();
            // logger.info("length of serialized data: ["+bs.length()+"] \n"+bs.substring(0, 256));
            byte[] ba = bout.toByteArray();

            bout.close();
            return ba;

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (bout != null) {
            bout.close();
        }
        if (bis != null) {
            bis.close();
        }
        return null;
    }

    private String String(String string) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * This method is called when the connection to the gateway is reestablished.
     * Some requests must be resubmitted.
     * 
     * Subscription and retrival requests can be sent mutiple times.
     * The postal requests should only be sent once.
     *
     */
    public void repostToGateway() {
        if (network == null) {
            return;
        }
        if (!network.isConnected()) {
        	logger.warn("establishing network connection failed.");
            return;
        }
        callback.processSubscriptionChange(true);
        callback.processRetrivalChange(true);
        callback.processPostalChange(false);
    }

    /**
     * Every time the distributor provider is modified, find out what the
     * changes were and, if necessary, send the data to the server. Be careful
     * about the race condition; don't leave gaps in the time line. Originally
     * this method used time stamps to determine if the item had be sent. Now a
     * status indicator is used.
     * 
     * We can't loop the main while loop forever because there may be times that
     * a network connection is not available (causing infinite loop since the
     * number pending remains > 1). To escape this, we continue looping so long
     * as the current query does not contain the same number of items as the
     * previous query.
     * 
     * Though not impossible, the potential for race conditions is highly
     * unlikely since posts to the distributor provider should be serviced
     * before this method has finished a run loop and any posts from external
     * sources should be complete before the table is updated (i.e. The post
     * request will occur before the status update).
     * 
     * TODO Garbage collect items which are expired.
     */
    @Override
    public void processPostalChange(boolean repost) {
        logger.debug("::processSubscriptionChange()");
        if (!bindToNetworkService()) {
            return;
        }

        ContentResolver cr = this.getContentResolver();

        int prevPendingCount = 0;

        for (; true; repost = false) {
            final String selectPending = "\"" + PostalTableSchema.DISPOSITION
                    + "\" IN (" + " '" + PostalTableSchema.DISPOSITION_PENDING
                    + "'"
                    + (repost
                    ? (", '" + PostalTableSchema.DISPOSITION_FAIL + "'")
                    : "")
                    + ")";

            String[] selectionArgs = null;
            // Cursor cur = cr.query(PostalTableSchema.CONTENT_URI, null,
            // selectPending, selectionArgs,
            // PostalTableSchema.PRIORITY_SORT_ORDER);

            Cursor cur = cr.query(PostalTableSchema.CONTENT_URI, null,
                    selectPending, selectionArgs, PostalTableSchema._ID + " ASC");

            int curCount = cur.getCount();

            if (curCount == prevPendingCount) {
                cur.close();
                break; // no new items to send
            }

            prevPendingCount = curCount;
            // Iterate over each row serializing its data and sending it.
            for (boolean moreItems = cur.moveToFirst(); moreItems; moreItems = cur.moveToNext()) {
                String rowUri = cur.getString(
                        cur.getColumnIndex(PostalTableSchema.URI));
                String cpType = cur.getString(
                        cur.getColumnIndex(PostalTableSchema.CP_TYPE));

                logger.debug("serializing: " + rowUri);
                logger.debug("rowUriType: " + cpType);

                String mimeType = InternetMediaType.getInst(cpType).setType("application").toString();
                byte[] serialized;

                int serialType = cur.getInt(
                        cur.getColumnIndex(PostalTableSchema.SERIALIZE_TYPE));

                switch (serialType) {
                case PostalTableSchema.SERIALIZE_TYPE_DIRECT: 
                    int dataColumnIndex = cur.getColumnIndex(
                            PostalTableSchema.DATA);

                    if (!cur.isNull(dataColumnIndex)) {
                        String data = cur.getString(dataColumnIndex);

                        serialized = data.getBytes();
                    } else {
                        // TODO handle the case where data is null 
                        // that signifies there is a file containing the data
                        serialized = null;
                    }
                    break;

                case PostalTableSchema.SERIALIZE_TYPE_INDIRECT: 
                case PostalTableSchema.SERIALIZE_TYPE_DEFERRED:
                default:
                    try {
                        serialized = this.queryUriForSerializedData(rowUri);
                    } catch (IOException e1) {
                        logger.error("invalid row for serialization");
                        continue;
                    }
                }
                if (serialized == null) {
                    logger.error("no serialized data produced");
                    continue;
                }

                // Dispatch the message.
                boolean dispatchSuccessful = false;

                try {
                    dispatchSuccessful = network.dispatchPushRequestToGateway(
                            rowUri.toString(), mimeType, serialized);
                    if (dispatchSuccessful) {
                        byte[] notice = cur.getBlob(
                                cur.getColumnIndex(PostalTableSchema.NOTICE));

                        sendPendingIntent(notice);
                    }
                } catch (NullPointerException e) {
                    logger.debug(
                            "NullPointerException, sending to gateway failed");
                } 

                // Update distributor status if message dispatch successful.
                ContentValues values = new ContentValues();

                values.put(PostalTableSchema.DISPOSITION,
                        (dispatchSuccessful) 
                        ? PostalTableSchema.DISPOSITION_SENT
                        : PostalTableSchema.DISPOSITION_PENDING);
                int numUpdated = cr.update(PostalTableSchema.getUri(cur), values,
                        null, null);

                logger.debug(
                        String.valueOf(numUpdated)
                                + " rows updated to sent status");
            }
            cur.close();
        }
    }

    /**
     * Each time the enrollment provider is modified, find out what the changes
     * were and if necessary, send the data to the network server.
     * 
     * Be careful about the race condition; don't leave gaps in the time line.
     * Originally this method used time stamps to determine if the item had be
     * sent. Now a status indicator is used. TODO Garbage collect items which
     * are expired.
     */
    @Override
    public void processRetrievalChange(boolean repost) {
        logger.debug("::processRetrievalChange()");
        if (!bindToNetworkProxyService()) {
            return;
        }

        ContentResolver cr = this.getContentResolver();
        String order = RetrievalTableSchema.PRIORITY_SORT_ORDER;
        // Additional items may be added to the table while the current set are being processed
        for (; true; repost = false) {
            final String selectPending = "\"" + RetrievalTableSchema.DISPOSITION
                    + "\" IN (" + " '" + RetrievalTableSchema.DISPOSITION_PENDING
                    + "'"
                    + (repost
                    ? (", '" + RetrievalTableSchema.DISPOSITION_SENT + "'"
                    + ", '" + RetrievalTableSchema.DISPOSITION_FAIL + "'")
                    : "")
                    + ")";
		
            String[] selectionArgs = null;
            Cursor pendingCursor = cr.query(RetrievalTableSchema.CONTENT_URI,
                    null, selectPending, selectionArgs, order);

            if (pendingCursor.getCount() < 1) {
                pendingCursor.close();
                break; // no more items
            }
			
            int failedSendCount = 0;

            for (boolean areMoreItems = pendingCursor.moveToFirst(); areMoreItems; areMoreItems = pendingCursor.moveToNext()) {
                // For each item in the cursor, ask the content provider to
                // serialize it, then pass it off to the NPS.
				
                String uri = pendingCursor.getString(
                        pendingCursor.getColumnIndex(RetrievalTableSchema.URI));
                String mime = pendingCursor.getString(
                        pendingCursor.getColumnIndex(RetrievalTableSchema.MIME));
                // String disposition = pendingCursor.getString(pendingCursor.getColumnIndex(RetrievalTableSchema.DISPOSITION));
                String selection = pendingCursor.getString(
                        pendingCursor.getColumnIndex(
                                RetrievalTableSchema.SELECTION));
                // int expiration = pendingCursor.getInt(pendingCursor.getColumnIndex(RetrievalTableSchema.EXPIRATION));
                // long createdDate = pendingCursor.getLong(pendingCursor.getColumnIndex(RetrievalTableSchema.CREATED_DATE));
				
                // Make a query on the row we want to serialize. 
				
                // Passing the serial
                // field in the projection tells the content provider to serialize
                // the row queried and return a matrix cursor with the serialized data
                // information.
                Uri rowUri = Uri.parse(uri);
				
                // String mimeType = InternetMediaType.getInst(cr.getType(rowUri)).setType("application").toString();
				
                boolean sent = network.dispatchRetrievalRequestToGateway(
                        rowUri.toString(), mime, selection);
				
                if (!sent) {
                    ++failedSendCount;
                    // Toast.makeText(this, "Sending retrieval request to gateway failed.", Toast.LENGTH_SHORT).show();
                } else {// Toast.makeText(this, "Sending retrieval request to gateway succeeded.", Toast.LENGTH_LONG).show();
                }
				
                ContentValues values = new ContentValues();

                values.put(RetrievalTableSchema.DISPOSITION,
                        sent
                        ? RetrievalTableSchema.DISPOSITION_SENT 
                        : RetrievalTableSchema.DISPOSITION_FAIL);
					
                int numUpdated = cr.update(
                        RetrievalTableSchema.getUri(pendingCursor), values, null,
                        null);

                logger.debug(
                        String.valueOf(numUpdated) + " rows updated to "
                        + (sent ? "sent" : "pending") + " status");
            }
            pendingCursor.close();
        }
    }

    /**
     * Each time the subscription provider is modified, find out what the
     * changes were and if necessary, send the data to the network server.
     * 
     * Be careful about the race condition; don't leave gaps in the time line.
     * Originally this method used time stamps to determine if the item had be
     * sent. Now a status indicator is used. TODO Garbage collect items which
     * are expired.
     */
    @Override
    public void processSubscriptionChange(boolean repost) {
        logger.debug("::processSubscriptionChange()");
        if (!bindToNetworkService()) {
            return;
        }

        ContentResolver cr = this.getContentResolver();
        String order = SubscriptionTableSchema.PRIORITY_SORT_ORDER;

        // Additional items may be added to the table while the current set are
        // being processed
        for (; true; repost = false) {
            String[] selectionArgs = null;
            final String selectPending = "\""
                    + SubscriptionTableSchema.DISPOSITION + "\" IN (" + " '"
                    + SubscriptionTableSchema.DISPOSITION_PENDING + "'"
                    + (repost
                            ? (", '" + SubscriptionTableSchema.DISPOSITION_SENT
                            + "'" + ", '"
                            + SubscriptionTableSchema.DISPOSITION_FAIL + "'")
                            : "")
                            + ")";
		
            Cursor pendingCursor = cr.query(SubscriptionTableSchema.CONTENT_URI,
                    null, selectPending, selectionArgs, order);

            if (pendingCursor.getCount() < 1) {
                pendingCursor.close();
                break;
            }
			
            int failedSendCount = 0;

            for (boolean areMoreItems = pendingCursor.moveToFirst(); areMoreItems; areMoreItems = pendingCursor.moveToNext()) {
                // For each item in the cursor, ask the content provider to
                // serialize it, then pass it off to the NPS.
				
                String mime = pendingCursor.getString(
                        pendingCursor.getColumnIndex(
                                SubscriptionTableSchema.MIME));
                // String disposition = pendingCursor.getString(pendingCursor.getColumnIndex(SubscriptionTableSchema.DISPOSITION));
                String selection = pendingCursor.getString(
                        pendingCursor.getColumnIndex(
                                SubscriptionTableSchema.SELECTION));
                // int expiration = pendingCursor.getInt(pendingCursor.getColumnIndex(SubscriptionTableSchema.EXPIRATION));
                // long createdDate = pendingCursor.getLong(pendingCursor.getColumnIndex(SubscriptionTableSchema.CREATED_DATE));
				
                // Make a query on the row we want to serialize. 
				
                // Passing the serial
                // field in the projection tells the content provider to serialize
                // the row queried and return a matrix cursor with the serialized data
                // information.
                // Uri rowUri = Uri.parse(uri);
				
                // String mimeType = InternetMediaType.getInst(cr.getType(rowUri)).setType("application").toString();
				
                boolean sent = network.dispatchSubscribeRequestToGateway(mime,
                        selection);
				
                if (!sent) {
                    ++failedSendCount;
                    Toast.makeText(this, "subscription to " + mime + " failed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "subscription to " + mime + " sent", Toast.LENGTH_SHORT).show();
                }
				
                ContentValues values = new ContentValues();

                values.put(SubscriptionTableSchema.DISPOSITION,
                        (failedSendCount < 1) 
                        ? SubscriptionTableSchema.DISPOSITION_SENT 
                        : SubscriptionTableSchema.DISPOSITION_FAIL);
					
                int numUpdated = cr.update(
                        SubscriptionTableSchema.getUri(pendingCursor), values,
                        null, null);

                logger.debug(
                        String.valueOf(numUpdated)
                                + " rows updated to sent status");
            }
            pendingCursor.close();
        }
    }

    public byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        // Get the size of the file
        long length = file.length();
    
        if (length > Integer.MAX_VALUE) {// File is too large
        }

        // Create the byte array to hold the data
        byte[] bytes = new byte[(int) length];
    
        // Read in the bytes
        int offset = 0;
        int numRead = 0;

        while (offset < bytes.length
                && (numRead = is.read(bytes, offset, bytes.length - offset))
                        >= 0) {
            offset += numRead;
        }

        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException(
                    "Could not completely read file " + file.getName());
        }

        // Close the input stream and return bytes
        is.close();
        return bytes;
    }

    // ===========================================================
    // Network Service Calls
    // ===========================================================
    private boolean bindToNetworkService() {
        if (isBoundNPS) {
            return true;
        }
        if (network != null) {
            return isBoundNPS;
        }
        // Create a service connection to the Network Service.
        networkServiceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                logger.debug("Connected to NPS");
                isBoundNPS = true;
                network = (INetworkBinder) service;
                network.setDistributorServiceCallback(callback);
            }

            public void onServiceDisconnected(ComponentName name) {
                logger.debug("Disconnected NPS");
                isBoundNPS = false;
            }
        };

        networkServiceIntent = new Intent(INetworkBinder.ACTION);
        isBoundNPS = this.bindService(networkServiceIntent,
                networkServiceConnection, BIND_AUTO_CREATE);
        return isBoundNPS;
    }

    // ===========================================================
    // Content Observer Nested Classes
    // ===========================================================
    private class DeliveryMechanismObserver extends ContentObserver {

        /** Fields */
        @SuppressWarnings("unused")
        private IDistributorService callback;

        public DeliveryMechanismObserver(Handler handler, IDistributorService aCallback) {
            super(handler);
            callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.debug("DeliveryMechanismObserver::onChange");
        }
    }


    private class PostalObserver extends ContentObserver {

        /** Fields */
        private IDistributorService callback;

        public PostalObserver(Handler handler, IDistributorService aCallback) {
            super(handler);
            callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.debug(
                    "PostalObserver::onChange - selfChange = "
                            + String.valueOf(selfChange));
            callback.processPostalChange(false);
        }
    }

    private class RetrievalObserver extends ContentObserver {

        /** Fields */
        private IDistributorService callback;

        public RetrievalObserver(Handler handler, IDistributorService aCallback) {
            super(handler);
            callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.debug("RetrievalObserver::onChange");
            callback.processRetrievalChange(false);
        }
    }

    private class SubscriptionObserver extends ContentObserver {

        /** Fields */
        private IDistributorService callback;

        public SubscriptionObserver(Handler handler, IDistributorService aCallback) {
            super(handler);
            callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.debug("SubscriptionObserver::onChange");
            callback.processSubscriptionChange(false);
        }
    }


    /**
     * This broadcast receiver is responsible for determining the best channel
     * over which tiles may be acquired. In other words it sets status flags.
     * 
     */
    private class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context aContext, final Intent aIntent) {

            final String action = aIntent.getAction();

            logger.info("onReceive: " + action);
            checkResourceStatus(aContext);

            if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {// find serialized directory
            }
            // it may be that there were items which need to be delivered.
            DistributorService.this.repostToGateway();
        }
		
        public void checkResourceStatus(final Context aContext) { //
            { 
                final WifiManager wm = (WifiManager) aContext.getSystemService(
                        Context.WIFI_SERVICE);
                final int wifiState = wm.getWifiState(); // TODO check for permission or catch error

                if (DEBUGMODE) {
                    logger.debug("wifi state=" + wifiState);
                }

                final TelephonyManager tm = (TelephonyManager) aContext.getSystemService(
                        Context.TELEPHONY_SERVICE);
                final int dataState = tm.getDataState(); // TODO check for permission or catch error

                if (DEBUGMODE) {
                    logger.debug("telephone data state=" + dataState);
                }
			
                mNetworkConnected = wifiState == WifiManager.WIFI_STATE_ENABLED
                        || dataState == TelephonyManager.DATA_CONNECTED;
                if (DEBUGMODE) {
                    logger.debug("mConnected=" + mNetworkConnected);
                }
            } 
            {
                final String state = Environment.getExternalStorageState();

                logger.info("sdcard state: " + state);
                mSdCardAvailable = Environment.MEDIA_MOUNTED.equals(state);
                if (DEBUGMODE) {
                    logger.debug("mSdcardAvailable=" + mSdCardAvailable);
                }
            }
        }
    }

    // ================================================
    // Calls originating from NetworkService
    // ================================================

    /**
     * Typically just an acknowledgment.
     */
    @Override
    public boolean dispatchPushResponse(PushAcknowledgement resp) {		
        return true;
    }

    /**
     * Update the content providers as appropriate.
     * De-serialize into the proper content provider.
     * 
     */
    @Override
    public boolean dispatchRetrievalResponse(PullResponse resp) {
        logger.debug("dispatching retrieval response : {} : {}",
                resp.getRequestUid(), resp.getUri());
        String uriStr = resp.getRequestUid(); // resp.getUri(); --- why do we have uri in data message and retrieval response?
        Uri uri = Uri.parse(uriStr);
        ContentResolver cr = this.getContentResolver();

        try {
            Uri serialUri = Uri.withAppendedPath(uri, "_serial");
            OutputStream outstream = cr.openOutputStream(serialUri);

            if (outstream == null) {
                logger.error(
                        "could not open output stream to content provider: "
                                + serialUri);
                return false;
            }
            ByteString data = resp.getData();

            if (data != null) {
                outstream.write(data.toByteArray());
            }
            outstream.close();
		    
            // TODO: update the retrieval request table
            // This mess is intended to update/delete the retrieval request as it has been fullfulled.
            //
            // RetrievalTableSchema.URI
            // final String selectPending = 
            // "\""+RetrievalTableSchema.CONTENT_ITEM_TYPE + "\" = "+
            // " '" +  + "'" +
            // (repost ? (", '" + RetrievalTableSchema.DISPOSITION_SENT + "'" +
            // ", '" + RetrievalTableSchema.DISPOSITION_FAIL+"'") : "") +
            // ")";
            //
            // String[] selectionArgs = null;
            //
            // Cursor pendingCursor = cr.query(RetrievalTableSchema.CONTENT_URI, null, selectPending, selectionArgs, order);
            // if (pendingCursor.getCount() < 1) {
            // pendingCursor.close();
            // Cursor pendingCursor = cr.query(RetrievalTableSchema.CONTENT_URI, null, selectPending, selectionArgs, order);
            // Cursor cursor = cr.query(uri, null, null, null, null);
            // byte[] notice = cursor.getBlob(cursor.getColumnIndex(RetrievalTableSchema.NOTICE));
            //
            // catch (CanceledException e) {
            // // TODO Auto-generated catch block
            // e.printStackTrace();
            // }
            // // cr.delete(, where, selectionArgs)
        } catch (FileNotFoundException e) {
            String msg = "could not connect to content provider";

            logger.warn(msg);
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            String msg = "could not write to the content provider";

            logger.warn(msg);
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Update the content providers as appropriate. These are typically received
     * in response to subscriptions.
     * 
     * The subscribing uri isn't sent with the subscription to the gateway
     * therefore it needs to be recovered from the subscription table.
     */
    @Override
    public boolean dispatchSubscribeResponse(DataMessage resp) {

        String mime = resp.getMimeType();
        ContentResolver cr = this.getContentResolver();
        String tableUriStr = null;

        try {
            Cursor subCursor = cr.query(SubscriptionTableSchema.CONTENT_URI,
                    null,
                    "\"" + SubscriptionTableSchema.MIME + "\" = '" + mime + "'",
                    null, null);

            if (!subCursor.moveToFirst()) {
                logger.info("no matching subscription");
                subCursor.close();
                return false;
            }
            tableUriStr = subCursor.getString(
                    subCursor.getColumnIndex(SubscriptionTableSchema.URI));
            subCursor.close();

            Uri tableUri = Uri.withAppendedPath(Uri.parse(tableUriStr),
                    "_serial");
            OutputStream outstream = cr.openOutputStream(tableUri);

            if (outstream == null) {
                logger.error(
                        "the content provider " + tableUri.toString()
                        + " is not available");
                return false;
            }
            outstream.write(resp.getData().toByteArray());
            outstream.flush();
            outstream.close();
            return true;
        } catch (IllegalArgumentException ex) {
            String msg = "could not serialize to content provider: "
                    + tableUriStr;

            logger.warn(msg);
            return false;
        } catch (FileNotFoundException ex) {
            String msg = "could not connect to content provider";

            logger.warn(msg);
            return false;
        } catch (IOException e) {
            String msg = "could not write to the content provider";

            logger.warn(msg);
            return false;
        }
    }

    @Override
    public void processPublicationChange(boolean repost) {// TODO Auto-generated method stub
    }

}

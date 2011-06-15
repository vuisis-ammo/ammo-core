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

import android.app.Service;
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
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.google.protobuf.ByteString;

import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.pb.AmmoMessages.DataMessage;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PullResponse;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;
import edu.vu.isis.ammo.core.receiver.CellPhoneListener;
import edu.vu.isis.ammo.core.receiver.WifiReceiver;
import edu.vu.isis.ammo.util.IRegisterReceiver;
import edu.vu.isis.ammo.util.InternetMediaType;



/**
 * The DistributorService is responsible for synchronization between the Gateway
 * and individual application databases. The DistributorService will
 * issue calls to the NetworkService for updates and then writes the
 * results to the correct content provider using the deserialization mechanism
 * defined by each content provider.
 * 
 * Any activity or application wishing to send data via the DistributorService
 * should use one of the AmmoDispatcher API methods for communication between
 * said application and AmmoCore.
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
    private static final Logger logger = LoggerFactory.getLogger(DistributorService.class);

    public static final Intent LAUNCH = new Intent("edu.vu.isis.ammo.core.distributor.DistributorService.LAUNCH");

    @SuppressWarnings("unused")
    private static final int FILE_READ_SIZE = 1024;
    public static final String SERIALIZED_STRING_KEY = "serializedString";
    public static final String SERIALIZED_BYTE_ARRAY_KEY = "serializedByteArray";

    // ===========================================================
    // Fields
    // ===========================================================

    private IDistributorService callback;
    private Intent networkServiceIntent = new Intent(INetworkService.ACTION);

    private INetworkService networkServiceBinder;
    private boolean isNetworkServiceBound = false;
    private ProcessChangeTask pct;
    public void consumerReady() {
        pct.subscriptionChange();
        pct.retrievalChange();
        pct.postalChange();
    }
    
    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.info("::onServiceConnected - Network Service");
            isNetworkServiceBound = true;
            networkServiceBinder = ((NetworkService.MyBinder) service).getService();
            networkServiceBinder.setDistributorServiceCallback(callback);
            
            // Start processing the tables
            DistributorService.this.pct = new ProcessChangeTask();
            pct.execute(DistributorService.this);
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.info("::onServiceDisconnected - Network Service");
            isNetworkServiceBound = false;
            networkServiceBinder = null;
        }
    };

    private PostalObserver postalObserver;
    private RetrievalObserver retrievalObserver;
    private SubscriptionObserver subscriptionObserver;

    private TelephonyManager tm;
    private CellPhoneListener cellPhoneListener;
    private WifiReceiver wifiReceiver;

    private MyBroadcastReceiver mReadyResourceReceiver = null;
    private boolean mNetworkConnected = false;
    private boolean mSdCardAvailable = false;


    // ===========================================================
    // LifeCycle
    // ===========================================================
    private IRegisterReceiver mReceiverRegistrar = null;

    // When the service is created, we should setup all services necessary to
    // maintain synchronization (updating player loop,
    @Override
    public void onCreate() {
        super.onCreate();
        logger.info("::onCreate");

        // Set this service to observe certain Content Providers.
        // Initialize our content observer.

        postalObserver = new PostalObserver(new Handler(), this);
        this.getContentResolver().registerContentObserver(
                PostalTableSchema.CONTENT_URI, true, postalObserver);

        retrievalObserver = new RetrievalObserver(new Handler(), this);
        this.getContentResolver().registerContentObserver(
                RetrievalTableSchema.CONTENT_URI, true, retrievalObserver);

        subscriptionObserver = new SubscriptionObserver(new Handler(), this);
        this.getContentResolver().registerContentObserver(
                SubscriptionTableSchema.CONTENT_URI, true, subscriptionObserver);

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
                return DistributorService.this.registerReceiver(aReceiver, aFilter);
            }

            @Override
            public void unregisterReceiver(final BroadcastReceiver aReceiver) {
                DistributorService.this.unregisterReceiver(aReceiver);
            }
        };
        final IntentFilter networkFilter = new IntentFilter();

        networkFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        networkFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mReceiverRegistrar.registerReceiver(mReadyResourceReceiver, networkFilter);

        final IntentFilter mediaFilter = new IntentFilter();

        mediaFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mediaFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mediaFilter.addDataScheme("file");
        mReceiverRegistrar.registerReceiver(mReadyResourceReceiver, mediaFilter);

        mReadyResourceReceiver.checkResourceStatus(this);
    }

    /**
     * Prepare to handle network service calls
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.info("::onStartCommand");
        // If we get this intent, unbind from all services 
        // so the service can be stopped.
        callback = this;
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                if (action.equals(DistributorService.PREPARE_FOR_STOP)) {
                    this.teardownService();
                    return START_NOT_STICKY;
                }
            }
        }

        if (isNetworkServiceBound) return START_STICKY;
        if (networkServiceBinder != null) return START_STICKY;
        networkServiceIntent = new Intent(this, NetworkService.class); 
        DistributorService.this.bindService(networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE);
        return START_STICKY;
    }

    public void teardownService() {
        if (!isNetworkServiceBound) {
            return;
        }
        logger.info("service unbinding from networkServiceBinder proxy service");
        // Use our binding for notifying the NPS of teardown.
        if (networkServiceBinder != null) {
            networkServiceBinder.teardown();
        }
        this.unbindService(networkServiceConnection);
        isNetworkServiceBound = false;
    }

    public void finishTeardown() {
        logger.info("service teardown finished");
        this.stopSelf();
    }

    @Override
    public void onDestroy() {
        logger.warn("onDestroy");
        if (isNetworkServiceBound) {
            this.unbindService(networkServiceConnection);
            isNetworkServiceBound = false;
        }
        this.stopService(networkServiceIntent);
        tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_NONE);
        wifiReceiver.setInitialized(false);
        this.getContentResolver().unregisterContentObserver(postalObserver);
        this.getContentResolver().unregisterContentObserver(retrievalObserver);
        this.getContentResolver().unregisterContentObserver(subscriptionObserver);
        this.mReceiverRegistrar.unregisterReceiver(this.mReadyResourceReceiver);

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
    private synchronized byte[] queryUriForSerializedData(String uri) throws IOException {
        Uri rowUri = Uri.parse(uri);
        Uri serialUri = Uri.withAppendedPath(rowUri, "_serial");

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        BufferedInputStream bis = null;
        InputStream instream = null;

        try {
            try {
                // instream = this.getContentResolver().openInputStream(serialUri);
                AssetFileDescriptor afd = this.getContentResolver()
                    .openAssetFileDescriptor(serialUri, "r");
                if (afd == null) {
                    logger.warn("could not acquire file descriptor {}", serialUri);
                    throw new IOException("could not acquire file descriptor "+serialUri);
                }
                // afd.createInputStream();

                ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();

                instream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            } catch (IOException e) {
                logger.info("unable to create stream {} ",e.getMessage());
                throw new FileNotFoundException("Unable to create stream");
            }
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


    /**
     * The distributor service runs in the ui thread.
     * This establishes a new thread for distributing the requests.
     * 
     */
    public static class ProcessChangeTask extends
            AsyncTask<DistributorService, Integer, Void> 
    {
        private static final int BURP_TIME = 20 * 1000; // 20 seconds expressed in
                                                        // milliseconds

        private boolean subscriptionDelta;

        public synchronized void subscriptionChange() {
            subscriptionDelta = true;
            this.notifyAll();
        }

        private boolean retrievalDelta;

        public synchronized void retrievalChange() {
            retrievalDelta = true;
            this.notifyAll();
        }

        private boolean postalDelta;

        public synchronized void postalChange() {
            postalDelta = true;
            this.notifyAll();
        }

        private boolean isReady() {
            return this.subscriptionDelta || this.retrievalDelta || this.postalDelta;
        }

        @Override
        protected Void doInBackground(DistributorService... them) {
            logger.info("::post to network service");
          
            for (DistributorService that : them) {
                this.processSubscriptionChange(that, true);
                this.processRetrievalChange(that, true);
                this.processPostalChange(that, true);
            }
            // condition wait is there something to process?
            try {
                while (true) {
                    boolean subscriptionFlag = false;
                    boolean retrievalFlag = false;
                    boolean postalFlag = false;
                    
                    synchronized (this) {
                        while (!this.isReady())
                        {
                        	logger.info("!this.isReady()");
                            // this is IMPORTANT don't remove it.
                            this.wait(BURP_TIME);
                        }
                        subscriptionFlag = this.subscriptionDelta;
                        this.subscriptionDelta = false;
                        
                        retrievalFlag = this.retrievalDelta;
                        this.retrievalDelta = false;
                        
                        postalFlag = this.postalDelta;
                        this.postalDelta = false;
                    }
                    if (subscriptionFlag) {
                        for (DistributorService that : them) {
                            this.processSubscriptionChange(that, false);
                        }
                    }
                    if (retrievalFlag) {
                        for (DistributorService that : them) {
                            this.processRetrievalChange(that, false);
                        }
                    }
                    if (postalFlag) {
                        for (DistributorService that : them) {
                            this.processPostalChange(that, false);
                        }
                    }
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            // this.publishProgress(values);
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... them) {
            super.onProgressUpdate(them[0]);
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
        }

        private boolean collectGarbage = true;

        /**
         * Every time the distributor provider is modified, find out what the
         * changes were and, if necessary, send the data to the server. Be
         * careful about the race condition; don't leave gaps in the time line.
         * Originally this method used time stamps to determine if the item had
         * be sent. Now a status indicator is used.
         * 
         * We can't loop the main while loop forever because there may be times
         * that a networkServiceBinder connection is not available (causing
         * infinite loop since the number pending remains > 1). To escape this,
         * we continue looping so long as the current query does not contain the
         * same number of items as the previous query.
         * 
         * Though not impossible, the potential for race conditions is highly
         * unlikely since posts to the distributor provider should be serviced
         * before this method has finished a run loop and any posts from
         * external sources should be complete before the table is updated (i.e.
         * The post request will occur before the status update).
         * 
         */

        private static final String POSTAL_GARBAGE;
        private static final String POSTAL_RESEND;
        private static final String POSTAL_SEND;
        
        static {
            StringBuilder sb = new StringBuilder()
              .append('"').append(PostalTableSchema.DISPOSITION).append('"')
              .append(" IN (")
              .append("'").append(PostalTableSchema.DISPOSITION_SATISFIED).append("'")
              .append(",")
              .append("'").append(PostalTableSchema.DISPOSITION_EXPIRED).append("'")
            //  .append("'").append(RetrievalTableSchema.EXPIRATION).append("'")
            //  .append("<\"").append(Long.valueOf(System.currentTimeMillis())).append("\"");
              .append(")");
            POSTAL_GARBAGE = sb.toString();
            
            sb = new StringBuilder()
              .append('"').append(PostalTableSchema.DISPOSITION).append('"')
              .append(" IN (")
              .append("'").append(PostalTableSchema.DISPOSITION_PENDING).append("'")
              .append(")");
            POSTAL_SEND = sb.toString();

            sb = new StringBuilder()
              .append('"').append(PostalTableSchema.DISPOSITION).append('"')
              .append(" IN (")
              .append("'").append(PostalTableSchema.DISPOSITION_PENDING).append("'")
              .append(",")
              .append("'").append(PostalTableSchema.DISPOSITION_FAIL).append("'")
              .append(",")
              .append("'").append(PostalTableSchema.DISPOSITION_SENT).append("'")
              .append(")");
            POSTAL_RESEND = sb.toString();
        }
        public void processPostalChange(DistributorService that, boolean resend) {
            logger.info("::processPostalChange()");

            if (!that.isNetworkServiceBound)
                return;
            if (!that.networkServiceBinder.isConnected())
                return;

            final ContentResolver cr = that.getContentResolver();

            if (collectGarbage)
            cr.delete(PostalTableSchema.CONTENT_URI, POSTAL_GARBAGE, null);
            
            int prevPendingCount = 0;

            for (; true; resend = false) {
                String[] selectionArgs = null;

                Cursor cur = cr.query(PostalTableSchema.CONTENT_URI, null, 
                        (resend ? POSTAL_RESEND : POSTAL_SEND), 
                        selectionArgs, PostalTableSchema._ID + " ASC");

                int curCount = cur.getCount();

                if (curCount == prevPendingCount) {
                    cur.close();
                    break; // no new items to send
                }

                prevPendingCount = curCount;
                // Iterate over each row serializing its data and sending it.
                for (boolean moreItems = cur.moveToFirst(); moreItems; 
                     moreItems = cur.moveToNext()) 
                {
                    String rowUri = cur.getString(
                        cur.getColumnIndex(PostalTableSchema.URI));
                    String cpType = cur.getString(
                        cur.getColumnIndex(PostalTableSchema.CP_TYPE));

                    logger.debug("serializing: " + rowUri);
                    logger.debug("rowUriType: " + cpType);

                    String mimeType = InternetMediaType.getInst(cpType)
                            .setType("application").toString();
                    byte[] serialized;

                    int serialType = cur.getInt(
                        cur.getColumnIndex(PostalTableSchema.SERIALIZE_TYPE));

                    switch (serialType) {
                    case PostalTableSchema.SERIALIZE_TYPE_DIRECT:
                        int dataColumnIndex = cur.getColumnIndex(PostalTableSchema.DATA);

                        if (!cur.isNull(dataColumnIndex)) {
                            String data = cur.getString(dataColumnIndex);
                            serialized = data.getBytes();
                        } else {
                            // TODO handle the case where data is null
                            // that signifies there is a file containing the
                            // data
                            serialized = null;
                        }
                        break;

                    case PostalTableSchema.SERIALIZE_TYPE_INDIRECT:
                    case PostalTableSchema.SERIALIZE_TYPE_DEFERRED:
                    default:
                        try {
                            serialized = that.queryUriForSerializedData(rowUri);
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
                    try {
                        if (!that.networkServiceBinder.isConnected()) {
                            logger.info("no network connection");
                        } else {
                            final Uri postalUri = PostalTableSchema.getUri(cur);
                            ContentValues values = new ContentValues();

                            values.put(PostalTableSchema.DISPOSITION,
                                    PostalTableSchema.DISPOSITION_QUEUED);
                            @SuppressWarnings("unused")
                            int numUpdated = cr.update(postalUri, values, null, null);

                            boolean dispatchSuccessful = that.networkServiceBinder
                                    .dispatchPushRequest(
                                            rowUri.toString(),
                                            mimeType,
                                            serialized,
                                            new INetworkService.OnSendMessageHandler() {

                                                @Override
                                                public boolean ack(boolean status) {

                                                    // Update distributor status
                                                    // if message dispatch
                                                    // successful.
                                                    ContentValues values = new ContentValues();

                                                    values.put(PostalTableSchema.DISPOSITION,
                                                        (status) ? PostalTableSchema.DISPOSITION_SENT
                                                                : PostalTableSchema.DISPOSITION_FAIL);
                                                    int numUpdated = cr.update(
                                                            postalUri, values,
                                                            null, null);

                                                    logger.info("Postal: {} rows updated to {}",
                                                        numUpdated, (status ? "sent" : "failed"));

                                                    // if (status) {
                                                    // byte[] notice =
                                                    // cur.getBlob(cur.getColumnIndex(PostalTableSchema.NOTICE));
                                                    // sendPendingIntent(notice);
                                                    // }
                                                    return false;
                                                }
                                            });
                            if (!dispatchSuccessful) {
                                values.put(PostalTableSchema.DISPOSITION,
                                        PostalTableSchema.DISPOSITION_PENDING);
                                cr.update(postalUri, values, null, null);
                            }

                        }
                    } catch (NullPointerException ex) {
                        logger.warn("NullPointerException, sending to gateway failed");
                    }
                }
                cur.close();
            }
        }

        /**
         * Each time the enrollment provider is modified, find out what the
         * changes were and if necessary, send the data to the
         * networkServiceBinder server.
         * 
         * Be careful about the race condition; don't leave gaps in the time
         * line. Originally this method used time stamps to determine if the
         * item had be sent. Now a status indicator is used.
         * 
         * Garbage collect items which are expired.
         */

        private static final String RETRIEVAL_GARBAGE;
        private static final String RETRIEVAL_SEND;
        private static final String RETRIEVAL_RESEND;
        
        static {
            StringBuilder sb = new StringBuilder()
              .append('"').append(RetrievalTableSchema.DISPOSITION).append('"')
              .append(" IN (")
              .append("'").append(RetrievalTableSchema.DISPOSITION_SATISFIED).append("'")
              .append(",")
              .append("'").append(RetrievalTableSchema.DISPOSITION_EXPIRED).append("'")
              .append(")");
            RETRIEVAL_GARBAGE = sb.toString();
            
            sb = new StringBuilder()
              .append('"').append(RetrievalTableSchema.DISPOSITION).append('"')
              .append(" IN (")
              .append("'").append(RetrievalTableSchema.DISPOSITION_PENDING).append("'")
              .append(",")
              .append("'").append(RetrievalTableSchema.DISPOSITION_FAIL).append("'")
              .append(")");
            RETRIEVAL_SEND = sb.toString();

            sb = new StringBuilder()
              .append('"').append(RetrievalTableSchema.DISPOSITION).append('"')
              .append(" IN (")
              .append("'").append(RetrievalTableSchema.DISPOSITION_PENDING).append("'")
              .append(",")
              .append("'").append(RetrievalTableSchema.DISPOSITION_FAIL).append("'")
              .append(",")
              .append("'").append(RetrievalTableSchema.DISPOSITION_SENT).append("'")
              .append(")");
            RETRIEVAL_RESEND = sb.toString();
        }
        
        public void processRetrievalChange(DistributorService that, boolean resend) {
            logger.info("::processRetrievalChange()");

            if (!that.isNetworkServiceBound)
                return;
            if (!that.networkServiceBinder.isConnected())
                return;

            final ContentResolver cr = that.getContentResolver();
            String order = RetrievalTableSchema.PRIORITY_SORT_ORDER;

            if (collectGarbage)
            cr.delete(RetrievalTableSchema.CONTENT_URI, RETRIEVAL_GARBAGE, null);
            
            // Additional items may be added to the table while the current set
            // are being processed

            for (; true; resend = false) {
                String[] selectionArgs = null;
                Cursor pendingCursor = cr.query(
                        RetrievalTableSchema.CONTENT_URI, null, 
                        (resend ? RETRIEVAL_RESEND : RETRIEVAL_SEND),
                        selectionArgs, order);

                if (pendingCursor.getCount() < 1) {
                    pendingCursor.close();
                    break; // no more items
                }

                for (boolean areMoreItems = pendingCursor.moveToFirst(); areMoreItems;
                     areMoreItems = pendingCursor.moveToNext()) 
                {
                    // For each item in the cursor, ask the content provider to
                    // serialize it, then pass it off to the NPS.

                    String uri = pendingCursor.getString(pendingCursor
                            .getColumnIndex(RetrievalTableSchema.URI));
                    String mime = pendingCursor.getString(pendingCursor
                            .getColumnIndex(RetrievalTableSchema.MIME));
                    // String disposition =
                    // pendingCursor.getString(pendingCursor.getColumnIndex(RetrievalTableSchema.DISPOSITION));
                    String selection = pendingCursor.getString(pendingCursor
                            .getColumnIndex(RetrievalTableSchema.SELECTION));
                    // int expiration =
                    // pendingCursor.getInt(pendingCursor.getColumnIndex(RetrievalTableSchema.EXPIRATION));
                    // long createdDate =
                    // pendingCursor.getLong(pendingCursor.getColumnIndex(RetrievalTableSchema.CREATED_DATE));

                    Uri rowUri = Uri.parse(uri);

                    if (!that.networkServiceBinder.isConnected()) {
                        continue;
                    }
                    final Uri retrieveUri = RetrievalTableSchema.getUri(pendingCursor);
                    ContentValues values = new ContentValues();
                    values.put(RetrievalTableSchema.DISPOSITION,
                            RetrievalTableSchema.DISPOSITION_QUEUED);

                    @SuppressWarnings("unused")
                    int numUpdated = cr.update(retrieveUri, values, null, null);

                    boolean sent = that.networkServiceBinder
                            .dispatchRetrievalRequest(rowUri.toString(), mime,
                                    selection,
                                    new INetworkService.OnSendMessageHandler() {
                                        @Override
                                        public boolean ack(boolean status) {
                                            // Update distributor status if
                                            // message dispatch successful.
                                            ContentValues values = new ContentValues();

                                            values.put(RetrievalTableSchema.DISPOSITION,
                                                    status ? RetrievalTableSchema.DISPOSITION_SENT
                                                        : RetrievalTableSchema.DISPOSITION_FAIL);

                                            int numUpdated = cr.update(
                                                    retrieveUri, values, null,
                                                    null);

                                            logger.info("{} rows updated to {} status",
                                                    numUpdated, (status ? "sent" : "pending"));
                                            return false;
                                        }
                                    });
                    if (!sent) {
                        values.put(RetrievalTableSchema.DISPOSITION,
                                RetrievalTableSchema.DISPOSITION_PENDING);
                        cr.update(retrieveUri, values, null, null);
                        // break; // no point in trying any more
                    }
                }
                pendingCursor.close();
            }
        }

        /**
         * Each time the subscription provider is modified, find out what the
         * changes were and if necessary, send the data to the
         * networkServiceBinder server.
         * 
         * Be careful about the race condition; don't leave gaps in the time
         * line. Originally this method used time stamps to determine if the
         * item had be sent. Now a status indicator is used.
         * 
         * Garbage collect items which are expired.
         */
        private static final String SUBSCRIPTION_GARBAGE;
        private static final String SUBSCRIPTION_RESEND;
        private static final String SUBSCRIPTION_SEND;
        
        static {
            StringBuilder sb = new StringBuilder();
            
            sb = new StringBuilder()
            .append('"').append(SubscriptionTableSchema.DISPOSITION).append('"')
            .append(" IN (")
            .append("'").append(SubscriptionTableSchema.DISPOSITION_EXPIRED).append("'")
            .append(")");      
            SUBSCRIPTION_GARBAGE = sb.toString();

            sb = new StringBuilder()
              .append('"').append(SubscriptionTableSchema.DISPOSITION).append('"')
              .append(" IN (")
              .append("'").append(SubscriptionTableSchema.DISPOSITION_PENDING).append("'")
              .append(",")
              .append("'").append(SubscriptionTableSchema.DISPOSITION_FAIL).append("'")
              .append(")");             
            SUBSCRIPTION_SEND = sb.toString();
            
            sb = new StringBuilder()
              .append('"').append(SubscriptionTableSchema.DISPOSITION).append('"')
              .append(" IN (")
                          .append("'").append(SubscriptionTableSchema.DISPOSITION_PENDING).append("'")
              .append(",")
              .append("'").append(SubscriptionTableSchema.DISPOSITION_FAIL).append("'")
              .append(",")
              .append("'").append(SubscriptionTableSchema.DISPOSITION_SENT).append("'")
              .append(")");
            SUBSCRIPTION_RESEND = sb.toString();
        }
        public void processSubscriptionChange(DistributorService that, boolean resend) {
            logger.info("::processSubscriptionChange()");

            if (!that.isNetworkServiceBound)
                return;
            if (!that.networkServiceBinder.isConnected())
                return;

            final ContentResolver cr = that.getContentResolver();
            if (collectGarbage)
            cr.delete(SubscriptionTableSchema.CONTENT_URI, SUBSCRIPTION_GARBAGE, null);

            String order = SubscriptionTableSchema.PRIORITY_SORT_ORDER;

            // Additional items may be added to the table while the current set
            // are being processed

            for (; true; resend = false) {
                String[] selectionArgs = null;

                Cursor pendingCursor = cr.query(
                        SubscriptionTableSchema.CONTENT_URI, null, 
                        (resend ? SUBSCRIPTION_RESEND : SUBSCRIPTION_SEND), 
                        selectionArgs, order);

                if (pendingCursor.getCount() < 1) {
                    pendingCursor.close();
                    break;
                }

                for (boolean areMoreItems = pendingCursor.moveToFirst(); areMoreItems; 
                    areMoreItems = pendingCursor.moveToNext()) 
                {
                    // For each item in the cursor, ask the content provider to
                    // serialize it, then pass it off to the NPS.

                    String mime = pendingCursor.getString(pendingCursor
                            .getColumnIndex(SubscriptionTableSchema.MIME));
                    // String disposition =
                    // pendingCursor.getString(pendingCursor.getColumnIndex(SubscriptionTableSchema.DISPOSITION));
                    String selection = pendingCursor.getString(pendingCursor
                            .getColumnIndex(SubscriptionTableSchema.SELECTION));
                    int expiration = pendingCursor.getInt(pendingCursor
                            .getColumnIndex(SubscriptionTableSchema.EXPIRATION));

                    // skip subscriptions with expiration of 0 -- they have been
                    // unsubscribed
                    if (expiration == 0)
                        continue;

                    // long createdDate =
                    // pendingCursor.getLong(pendingCursor.getColumnIndex(SubscriptionTableSchema.CREATED_DATE));

                    logger.info("Subscribe request with mime: {} and selection: {}",
                            mime, selection);

                    final Uri subUri = SubscriptionTableSchema
                            .getUri(pendingCursor);

                    ContentValues values = new ContentValues();
                    values.put(SubscriptionTableSchema.DISPOSITION,
                            SubscriptionTableSchema.DISPOSITION_QUEUED);

                    @SuppressWarnings("unused")
                    int numUpdated = cr.update(subUri, values, null, null);

                    boolean sent = that.networkServiceBinder
                            .dispatchSubscribeRequest(mime, selection,
                                    new INetworkService.OnSendMessageHandler() {
                                        @Override
                                        public boolean ack(boolean status) {
                                            // Update distributor status if
                                            // message dispatch successful.
                                            ContentValues values = new ContentValues();
                                            values.put( SubscriptionTableSchema.DISPOSITION,
                                                            (status) ? SubscriptionTableSchema.DISPOSITION_SENT
                                                                    : SubscriptionTableSchema.DISPOSITION_FAIL);

                                            int numUpdated = cr.update(subUri, values, null, null);

                                            logger.info("Subscription: {} rows updated to {} status ",
                                                    numUpdated, (status ? "sent" : "pending"));
                                            return true;
                                        }
                                    });
                    if (!sent) {
                        values.put(SubscriptionTableSchema.DISPOSITION,
                                SubscriptionTableSchema.DISPOSITION_PENDING);
                        cr.update(subUri, values, null, null);
                        // break; // no point in trying any more
                    }
                }
                pendingCursor.close();
            }
        }

        public void processPublicationChange(DistributorService that,
                boolean resend) {
            logger.error("::processPublicationChange : {} : not implemented",
                    resend);
        }
    }

    // ===========================================================
    // Content Observer Nested Classes
    // ===========================================================

    private class PostalObserver extends ContentObserver 
    {
        /** Fields */
        private DistributorService callback;

        public PostalObserver(Handler handler, DistributorService aCallback) {
            super(handler);
            logger.info("PostalObserver::");
            this.callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.info("PostalObserver::onChange : {}", selfChange);
            this.callback.pct.postalChange();
        }
    }

    private class RetrievalObserver extends ContentObserver {

        /** Fields */
        private DistributorService callback;

        public RetrievalObserver(Handler handler, DistributorService aCallback) {
            super(handler);
            logger.info("RetrievalObserver::");
            this.callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.info("RetrievalObserver::onChange : {}", selfChange );
            this.callback.pct.retrievalChange();
        }
    }

    private class SubscriptionObserver extends ContentObserver {

        /** Fields */
        private DistributorService callback;

        public SubscriptionObserver(Handler handler, DistributorService aCallback) {
            super(handler);
            logger.info("SubscriptionObserver::");
            this.callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.info("SubscriptionObserver::onChange : {}", selfChange );
            this.callback.pct.subscriptionChange();
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

    // ================================================
    // Calls originating from NetworkService
    // ================================================

    /**
     * Typically just an acknowledgment.
     */
    @Override
    public boolean dispatchPushResponse(PushAcknowledgement resp) {    
        logger.info("::dispatchPushResponse");
        return true;
    }

    /**
     * Update the content providers as appropriate.
     * De-serialize into the proper content provider.
     * 
     */
    @Override
    public boolean dispatchRetrievalResponse(PullResponse resp) {
        logger.info("::dispatchRetrievalResponse : {} : {}", resp.getRequestUid(), resp.getUri());
        String uriStr = resp.getRequestUid(); // resp.getUri(); --- why do we have uri in data message and retrieval response?
        Uri uri = Uri.parse(uriStr);
        ContentResolver cr = this.getContentResolver();

        try {
            Uri serialUri = Uri.withAppendedPath(uri, "_serial");
            OutputStream outstream = cr.openOutputStream(serialUri);

            if (outstream == null) {
                logger.error( "could not open output stream to content provider: {} ",serialUri);
                return false;
            }
            ByteString data = resp.getData();

            if (data != null) {
                outstream.write(data.toByteArray());
            }
            outstream.close();

            // This update/delete the retrieval request as it has been fulfilled.
            
            String selection = "\"" + RetrievalTableSchema.URI +"\" = '" + uri +"'";
            Cursor cursor = cr.query(RetrievalTableSchema.CONTENT_URI, null, selection, null, null);
            if (!cursor.moveToFirst()) {
                logger.info("no matching retrieval: {}", selection);
                cursor.close();
                return false;
            }
            final Uri retrieveUri = RetrievalTableSchema.getUri(cursor);
            cursor.close ();
            ContentValues values = new ContentValues();
            values.put(RetrievalTableSchema.DISPOSITION, RetrievalTableSchema.DISPOSITION_SATISFIED);

            @SuppressWarnings("unused")
            int numUpdated = cr.update(retrieveUri, values,null, null);
            
        } catch (FileNotFoundException e) {
            logger.warn("could not connect to content provider");
            return false;
        } catch (IOException e) {
            logger.warn("could not write to the content provider");
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
        logger.info("::dispatchSubscribeResponse : {} : {}", resp.getMimeType(), resp.getUri());
        String mime = resp.getMimeType();
        ContentResolver cr = this.getContentResolver();
        String tableUriStr = null;

        try {
            Cursor subCursor = cr.query(
                    SubscriptionTableSchema.CONTENT_URI,
                    null,
                    "\"" + SubscriptionTableSchema.MIME + "\" = '" + mime + "'",
                    null, null);

            if (!subCursor.moveToFirst()) {
                logger.info("no matching subscription");
                subCursor.close();
                return false;
            }
            tableUriStr = subCursor.getString(subCursor.getColumnIndex(SubscriptionTableSchema.URI));
            subCursor.close();

            Uri tableUri = Uri.withAppendedPath(Uri.parse(tableUriStr),"_serial");
            OutputStream outstream = cr.openOutputStream(tableUri);

            if (outstream == null) {
                logger.error("the content provider {} is not available", tableUri);
                return false;
            }
            outstream.write(resp.getData().toByteArray());
            outstream.flush();
            outstream.close();
            return true;
        } catch (IllegalArgumentException ex) {
            logger.warn("could not serialize to content provider: {} : {}", tableUriStr, ex.getLocalizedMessage());
            return false;
        } catch (FileNotFoundException ex) {
            logger.warn("could not connect to content provider using openFile: {}", ex.getLocalizedMessage());
            return false;
        } catch (IOException ex) {
            logger.warn("could not write to the content provider {}", ex.getLocalizedMessage());
            return false;
        }
    }

}

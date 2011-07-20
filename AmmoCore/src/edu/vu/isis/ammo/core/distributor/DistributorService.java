package edu.vu.isis.ammo.core.distributor;


import java.io.FileNotFoundException;
import java.io.IOException;
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
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
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
    public INetworkService getNetworkServiceBinder() { 
    	return this.networkServiceBinder; 
    }
    private boolean isNetworkServiceBound = false;
    public boolean isNetworkServiceBound() { 
    	return this.isNetworkServiceBound; 
    }
    
    private DistributorThread pct;

    public void consumerReady() {
        logger.info("::consumer ready : resend old requests");
        pct.resend();
    }
    
    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.info("::onServiceConnected - Network Service");
            isNetworkServiceBound = true;
            networkServiceBinder = ((NetworkService.MyBinder) service).getService();
            networkServiceBinder.setDistributorServiceCallback(callback);
            
            // Start processing the tables
            DistributorService.this.pct = new DistributorThread();
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

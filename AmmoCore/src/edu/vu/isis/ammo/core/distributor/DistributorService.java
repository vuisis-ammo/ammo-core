package edu.vu.isis.ammo.core.distributor;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;
import edu.vu.isis.ammo.core.receiver.CellPhoneListener;
import edu.vu.isis.ammo.core.receiver.WifiReceiver;
import edu.vu.isis.ammo.util.IRegisterReceiver;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.IDistributorService;

/**
 * The DistributorService is responsible for prioritizing and serializing
 * requests for data communications between distributed application databases. 
 * The DistributorService issues calls to the NetworkService for updates and then writes the
 * results to the correct content provider using the deserialization mechanism
 * defined by each content provider.
 * 
 * Any activity or application wishing to send data via the DistributorService
 * should use one of the AmmoRequest API methods for communication between
 * said application and AmmoCore.
 * 
 * Any activity or application wishing to receive updates when a content
 * provider has been modified can register via a custom ContentObserver
 * subclass.
 * 
 * The real work is delegated to the Distributor Thread, which maintains a queue.
 * 
 */
public class DistributorService extends Service {

    // ===========================================================
    // Constants
    // ===========================================================
    private static final Logger logger = LoggerFactory.getLogger(DistributorService.class);

    public static final Intent LAUNCH = new Intent("edu.vu.isis.ammo.core.distributor.DistributorService.LAUNCH");
    public static final String BIND = "edu.vu.isis.ammo.core.distributor.DistributorService.BIND";
    public static final String PREPARE_FOR_STOP = "edu.vu.isis.ammo.core.distributor.DistributorService.PREPARE_FOR_STOP";
    public static final String SEND_SERIALIZED = "edu.vu.isis.ammo.core.distributor.DistributorService.SEND_SERIALIZED";
    

    @SuppressWarnings("unused")
    private static final int FILE_READ_SIZE = 1024;
    public static final String SERIALIZED_STRING_KEY = "serializedString";
    public static final String SERIALIZED_BYTE_ARRAY_KEY = "serializedByteArray";

    // ===========================================================
    // Fields
    // ===========================================================

    private Intent networkServiceIntent = new Intent(INetworkService.ACTION);

    private INetworkService networkServiceBinder;
    public INetworkService getNetworkServiceBinder() { 
        return this.networkServiceBinder; 
    }
    private boolean isNetworkServiceBound = false;
    public boolean isNetworkServiceBound() { 
        return this.isNetworkServiceBound; 
    }
    
    private DistributorThread distThread;

    public void consumerReady() {
        logger.info("::consumer ready : resend old requests");
        this.distThread.resend();
    }
    
    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.info("::onServiceConnected - Network Service");
            isNetworkServiceBound = true;
            networkServiceBinder = ((NetworkService.MyBinder) service).getService();
            networkServiceBinder.setDistributorServiceCallback(DistributorService.this);
            
            DistributorService.this.distThread.execute(DistributorService.this);
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.info("::onServiceDisconnected - Network Service");
            DistributorService.this.distThread.cancel(true);
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
    // AIDL Implementation
    // ===========================================================

    public class DistributorServiceAidl extends IDistributorService.Stub {
        @Override
        public String makeRequest(AmmoRequest request) throws RemoteException {
            logger.trace("make request {}", request.action.toString());
            return DistributorService.this.distThread.distributeRequest(request);
        }

        @Override
        public AmmoRequest recoverRequest(String uuid) throws RemoteException {
            logger.trace("recover data request {}", uuid);
            return null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        logger.trace("client binding...");
        return new DistributorServiceAidl();
    }

    // ===========================================================
    // LifeCycle
    // ===========================================================
    private IRegisterReceiver mReceiverRegistrar = null;

    private DistributorPolicy policy;
    public DistributorPolicy policy() { return this.policy; }

    // When the service is created, we should setup all services necessary to
    // maintain synchronization (updating player loop,
    @Override
    public void onCreate() {
        super.onCreate();
        logger.info("::onCreate");

        // set up the worker thread
        this.distThread = new DistributorThread();
        
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
        
        this.policy = DistributorPolicy.newInstance(this.getBaseContext());
    }

    /**
     * Prepare to handle network service calls
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.info("::onStartCommand");
        // If we get this intent, unbind from all services 
        // so the service can be stopped.
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
        if (this.isNetworkServiceBound) {
            this.unbindService(this.networkServiceConnection);
            this.isNetworkServiceBound = false;
        }
        if (this.networkServiceIntent != null)
            this.stopService(this.networkServiceIntent);
        if (this.tm != null) 
            this.tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_NONE);
        if (this.wifiReceiver != null) 
            this.wifiReceiver.setInitialized(false);
        if (this.postalObserver != null)
            this.getContentResolver().unregisterContentObserver(this.postalObserver);
        if (this.retrievalObserver != null)
            this.getContentResolver().unregisterContentObserver(this.retrievalObserver);
        if (this.subscriptionObserver != null)
            this.getContentResolver().unregisterContentObserver(this.subscriptionObserver);
        if (this.mReceiverRegistrar != null)
            this.mReceiverRegistrar.unregisterReceiver(this.mReadyResourceReceiver);

        super.onDestroy();
    }

    // ===========================================================
    // Content Observer Nested Classes
    // ===========================================================

    private class PostalObserver extends ContentObserver 
    {
        /** Fields */
        private final DistributorService callback;

        public PostalObserver(Handler handler, DistributorService aCallback) {
            super(handler);
            logger.info("PostalObserver::");
            this.callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.info("PostalObserver::onChange : {}", selfChange);
            this.callback.distThread.postalChange();
        }
    }

    private class RetrievalObserver extends ContentObserver {

        /** Fields */
        private final DistributorService callback;

        public RetrievalObserver(Handler handler, DistributorService aCallback) {
            super(handler);
            logger.info("RetrievalObserver::");
            this.callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.info("RetrievalObserver::onChange : {}", selfChange );
            this.callback.distThread.retrievalChange();
        }
    }

    private class SubscriptionObserver extends ContentObserver {

        /** Fields */
        private final DistributorService callback;

        public SubscriptionObserver(Handler handler, DistributorService aCallback) {
            super(handler);
            logger.info("SubscriptionObserver::");
            this.callback = aCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            logger.info("SubscriptionObserver::onChange : {}", selfChange );
            this.callback.distThread.subscriptionChange();
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


    public boolean deliver(AmmoGatewayMessage agm) {
        return distThread.distributeResponse(agm);
    }
   
}

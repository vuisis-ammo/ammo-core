package edu.vu.isis.ammo.core.distributor;


import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.PriorityBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;
import edu.vu.isis.ammo.core.receiver.CellPhoneListener;
import edu.vu.isis.ammo.core.receiver.WifiReceiver;
import edu.vu.isis.ammo.util.IRegisterReceiver;
import edu.vu.isis.ammo.core.FLogger;


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

    private Intent networkServiceIntent = new Intent(INetworkService.ACTION);

    public INetworkService networkServiceBinder;
    public boolean isNetworkServiceBound = false;
    private DistributorSenderThread senderThread;
    private DistributorReceiverThread receiverThread;
    
    public void consumerReady() {
        senderThread.subscriptionChange();
        senderThread.retrievalChange();
        senderThread.postalChange();
    }
    
    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.info("::onServiceConnected - Network Service");
            isNetworkServiceBound = true;
            networkServiceBinder = ((NetworkService.MyBinder) service).getService();
                    
            // Start processing the requests
            final PriorityBlockingQueue<NetworkService.Request> outboundQueue
        	     = new PriorityBlockingQueue<NetworkService.Request>();      
            networkServiceBinder.setRequestQueue(outboundQueue);
            DistributorService.this.senderThread = new DistributorSenderThread(outboundQueue);
            DistributorService.this.senderThread.execute(DistributorService.this);
                      
            // Start processing the receives
            final PriorityBlockingQueue<NetworkService.Response> inboundQueue
            	= networkServiceBinder.getResponseQueue();
            DistributorService.this.receiverThread = new DistributorReceiverThread(inboundQueue);
            DistributorService.this.receiverThread.execute(DistributorService.this);
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
    public synchronized byte[] queryUriForSerializedData(String uri) throws FileNotFoundException, IOException {
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
            } catch (IOException ex) {
                logger.info("unable to create stream {} {}",serialUri, ex.getMessage());
                bout.close();
                throw new FileNotFoundException("Unable to create stream");
            }
            bis = new BufferedInputStream(instream);

            for (int bytesRead = 0; (bytesRead = bis.read(buffer)) != -1;) {
                bout.write(buffer, 0, bytesRead);
            }
            bis.close();
            instream.close();
            // String bs = bout.toString();
            // logger.info("length of serialized data: ["+bs.length()+"] \n"+bs.substring(0, 256));
            byte[] ba = bout.toByteArray();
            logger.info("length of serialized data: ["+ba.length+"]");
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
    

    /**
     * Get the contents of a file as a byte array.
     * 
     * @param file
     * @return
     * @throws IOException
     */
    public byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        // Get the size of the file
        long length = file.length();

        if (length > Integer.MAX_VALUE) {
        	// File is too large
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
            this.callback.senderThread.postalChange();
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
            this.callback.senderThread.retrievalChange();
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
            this.callback.senderThread.subscriptionChange();
        }
    }

    /**
     * This broadcast receiver is responsible for determining 
     * that an interface is available for use.
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
   
}

package edu.vu.isis.ammo.core.distributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import edu.vu.isis.ammo.core.network.INetworkBinder;
import edu.vu.isis.ammo.core.pb.AmmoMessages.DataMessage;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PullResponse;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
import edu.vu.isis.ammo.core.provider.DistributorSchema.DeliveryMechanismTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SerializedTableSchema;
import edu.vu.isis.ammo.core.provider.IAmmoSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrivalTableSchema;
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
    private static final Logger logger = LoggerFactory.getLogger(DistributorService.class);
    private static final boolean DEBUGMODE = true;
	
    @SuppressWarnings("unused")
	private static final int FILE_READ_SIZE = 1024;
    public static final String SERIALIZED_STRING_KEY = "serializedString";
    public static final String SERIALIZED_BYTE_ARRAY_KEY = "serializedByteArray";

    // ===========================================================
    // Fields
    // ===========================================================

    private IDistributorService callback;
    private ServiceConnection networkProxyServiceConnection;
    private Intent networkProxyServiceIntent = new Intent(INetworkBinder.ACTION);

    private INetworkBinder network;
    private boolean isBoundNPS = false;
    @SuppressWarnings("unused")
	private boolean onCreateCalled = false;

    private DeliveryMechanismObserver deliveryMechanismObserver;
    private PostalObserver postalObserver;
    private RetrivalObserver enrollmentObserver;
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

	deliveryMechanismObserver = new DeliveryMechanismObserver(new Handler(), callback);
	this.getContentResolver().registerContentObserver(
							  DeliveryMechanismTableSchema.CONTENT_URI, false, deliveryMechanismObserver);
		
	enrollmentObserver = new RetrivalObserver(new Handler(), callback);
	this.getContentResolver().registerContentObserver(
							  RetrivalTableSchema.CONTENT_URI, false, enrollmentObserver);

	subscriptionObserver = new SubscriptionObserver(new Handler(), callback);
	this.getContentResolver().registerContentObserver(
							  SubscriptionTableSchema.CONTENT_URI, false, subscriptionObserver);

	// Initialize our receivers/listeners.
	/*
	  wifiReceiver = new WifiReceiver();
	  cellPhoneListener = new CellPhoneListener(this);
	  tm = (TelephonyManager) this
	  .getSystemService(Context.TELEPHONY_SERVICE);
	  tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_DATA_ACTIVITY
	  | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
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

    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	logger.debug("service started...");
	// If we get this intent, unbind from all services so the service can be stopped.
	if (intent == null ){
	    callback = this;
	    this.bindToNetworkProxyService();
	    return START_STICKY;
	}
        if (intent.getAction().equals(DistributorService.PREPARE_FOR_STOP)) {
	    this.teardownService();
	    return START_NOT_STICKY;
	} 		
	callback = this;
	this.bindToNetworkProxyService();
	return START_STICKY;
    }

    public void teardownService() {
	logger.debug("service torn down...");
	if (! isBoundNPS) return;
		
	logger.debug("service unbinding from network proxy service");
	// Use our binding for notifying the NPS of teardown.
	if (network != null) network.teardown();
	this.unbindService(networkProxyServiceConnection);
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
	    this.unbindService(networkProxyServiceConnection);
	    isBoundNPS = false;
	}
	this.stopService(networkProxyServiceIntent);
	tm.listen(cellPhoneListener, PhoneStateListener.LISTEN_NONE);
	wifiReceiver.setInitialized(false);
	this.getContentResolver().unregisterContentObserver(postalObserver);
	this.getContentResolver().unregisterContentObserver(deliveryMechanismObserver);
	this.getContentResolver().unregisterContentObserver(enrollmentObserver);
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
     *  Iterate over each row of the matrix cursor and send the serialized data.
     *  A dispatch is only successful if all pieces of the incident 
     *  report are transmitted successfully.
     *   
     * @param cursorWithSerializedData
     * @param rowUri
     * @param mimeType
     * @return true or false depending on the return code.
     * @throws NullPointerException
     * @throws IOException
     */
    private boolean dispatchSerializedDataFromCursor(Cursor cursorWithSerializedData, String rowUri, String mimeType) throws NullPointerException, IOException {
	boolean dispatchSuccessful = true;
	for (boolean moreChildren = cursorWithSerializedData.moveToFirst(); 
	     moreChildren;
	     moreChildren = cursorWithSerializedData.moveToNext()) 
	    {
		String file = cursorWithSerializedData.getString(cursorWithSerializedData.getColumnIndex(SerializedTableSchema.FILE));
		if (file.equals("")) {
		    cursorWithSerializedData.close();
		    continue;
		}
		byte[] data = this.getBytesFromFile(new File(file));
		
		// Send the message.
			
	    }
		
	return dispatchSuccessful;
    }
	
	
    /**
     *  Make a specialized query on a specific URI to get back that row in serialized form
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
        		//instream = this.getContentResolver().openInputStream(serialUri);
        		AssetFileDescriptor afd = this.getContentResolver().openAssetFileDescriptor(serialUri, "r");
        		//afd.createInputStream();
        		
        		 ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();
        		 instream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        	     
        	} catch (IOException e) {
        	     throw new FileNotFoundException("Unable to create stream");
        	}
        	// if (instream)
            bis = new BufferedInputStream(instream);
            
            for (int bytesRead = 0; (bytesRead = bis.read(buffer)) != -1; ) {
            	bout.write(buffer, 0, bytesRead);
            }
            bis.close();
            //String bs = bout.toString();
            //logger.info("length of serialized data: ["+bs.length()+"] \n"+bs.substring(0, 256));
            byte[] ba = bout.toByteArray();
            bout.close();
            return ba;
            
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (bout != null) bout.close();
        if (bis != null) bis.close();
        return null;
    }

	
    private String String(String string) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
     * When the connection to the gateway is established the connection
     * must be re-authenticated and the request for data must be reposted.
     */
    public void repostToGateway() {
	if (network == null) return;
	if (!network.isConnected()) {
	    Toast.makeText(this, "establishing network connection failed.", Toast.LENGTH_SHORT).show();
	    return;
        }
	callback.processSubscriptionChange(true);
	/**
	 * @todo : Fred, please check this - it was true, sandeep changed to false
	 * we don't want to send reports that were already submitted and processed by Gateway
	 * similarly pull query must also be made explicitly by applications based on what they want,
	 * and we should not automatically reissue pull requests
	 */
	callback.processRetrievalChange(false);
	callback.processPostalChange(false);
    }
	
    /**
     * Every time the distributor provider is modified, find out what the
     * changes were and, if necessary, send the data to the server.
     * Be careful about the race condition; don't leave gaps in the time line.
     * Originally this method used time stamps to determine if the item had be sent.
     * Now a status indicator is used.
     * 
     * We can't loop the main while loop forever because there may be times that
     * a network connection is not available (causing infinite loop since 
     * the number pending remains > 1). To escape this, we continue looping
     * so long as the current query does not contain the same number of items as the previous
     * query. 
     * 
     * Though not impossible, the potential for race conditions is highly unlikely since 
     * posts to the distributor provider should be serviced before this method
     * has finished a run loop and any posts from external sources should be 
     * complete before the table is updated (i.e. The post request will occur
     * before the status update).
     * 
     * TODO 
     * Garbage collect items which are expired.
     */
    @Override
	public void processPostalChange(boolean repost) {
	logger.debug("::processSubscriptionChange()");
	if (!bindToNetworkProxyService()) return;

	ContentResolver cr = this.getContentResolver();

	int prevPendingCount = 0;
	for (; true; repost = false) {
	    final String selectPending = "\""
		+ PostalTableSchema.DISPOSITION
		+ "\" IN ("
		+ " '"+ PostalTableSchema.DISPOSITION_PENDING+ "'"
		+ (repost 
		   ? (", '"+ PostalTableSchema.DISPOSITION_FAIL + "'")
		   : "") + ")";

	    String[] selectionArgs = null;
	    //			Cursor cur = cr.query(PostalTableSchema.CONTENT_URI, null,
	    //					selectPending, selectionArgs, PostalTableSchema.PRIORITY_SORT_ORDER);
			
	    Cursor cur = cr.query(PostalTableSchema.CONTENT_URI, null,
				  selectPending, selectionArgs, PostalTableSchema._ID + " ASC");

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
		    String rowUri = cur.getString(cur.getColumnIndex(PostalTableSchema.URI));
		    String cpType = cur.getString(cur.getColumnIndex(PostalTableSchema.CP_TYPE));
				
		    Log.d("DistributorService", "serializing: " + rowUri);
		    Log.d("DistributorService", "rowUriType: " + cpType );
				
		    String mimeType = InternetMediaType.getInst(cpType).setType("application").toString();
		    byte[] serialized;
		    try {
		    	serialized = this.queryUriForSerializedData(rowUri);
		    } catch (IOException e1) {
				logger.error("invalid row for serialization");
				continue;
		    }
		    if (serialized == null) {
		    	logger.error("no serialized data produced");
		    	continue;
		    }

		    // Dispatch the message.
		    boolean dispatchSuccessful = false;
		    try {
		    	dispatchSuccessful = network.dispatchPushRequestToGateway(rowUri.toString(), mimeType, serialized);					
		    } catch (NullPointerException e) {
		    	logger.debug("NullPointerException, sending to gateway failed");
		    } 

		    // Update distributor status if message dispatch successful.
		    ContentValues values = new ContentValues();
		    values.put(PostalTableSchema.DISPOSITION,
			       (dispatchSuccessful) 
			       ? PostalTableSchema.DISPOSITION_SENT
			       : PostalTableSchema.DISPOSITION_PENDING);
		    int numUpdated = cr.update(PostalTableSchema.getUri(cur),
					       values, null, null);
		    logger.debug(String.valueOf(numUpdated) + " rows updated to sent status");
		}
	    cur.close();
	}
    }

    /**
     * Each time the enrollment provider is modified, find out what the
     * changes were and if necessary, send the data to the network server.
     * 
     * Be careful about the race condition; don't leave gaps in the time line.
     * Originally this method used time stamps to determine if the item had be sent.
     * Now a status indicator is used.
     * TODO 
     * Garbage collect items which are expired.
     */
    @Override
	public void processRetrievalChange(boolean repost) {
	logger.debug("::processRetrivalChange()");
	if (! bindToNetworkProxyService()) return;
		
	ContentResolver cr = this.getContentResolver();
	String order = RetrivalTableSchema.PRIORITY_SORT_ORDER;
		
	// Additional items may be added to the table while the current set are being processed
	for (; true ; repost = false ) {
	    final String selectPending = 
		"\""+RetrivalTableSchema.DISPOSITION + "\" IN ("+
		" '" + RetrivalTableSchema.DISPOSITION_PENDING + "'" +
		(repost ? (", '" + RetrivalTableSchema.DISPOSITION_SENT + "'" +
			   ", '" + RetrivalTableSchema.DISPOSITION_FAIL+"'") : "") +
		")";
		
	    String[] selectionArgs = null;
			
	    Cursor pendingCursor = cr.query(RetrivalTableSchema.CONTENT_URI, null, selectPending, selectionArgs, order);
	    if (pendingCursor.getCount() < 1) {
		pendingCursor.close();
		break; // no more items
	    }
			
	    int failedSendCount = 0;
	    for (boolean areMoreItems = pendingCursor.moveToFirst(); 
		 areMoreItems ; 
		 areMoreItems = pendingCursor.moveToNext())
		{
		    // For each item in the cursor, ask the content provider to
		    // serialize it, then pass it off to the NPS.
				
		    String uri = pendingCursor.getString(pendingCursor.getColumnIndex(RetrivalTableSchema.URI));
		    String mime = pendingCursor.getString(pendingCursor.getColumnIndex(RetrivalTableSchema.MIME));
		    //String disposition = pendingCursor.getString(pendingCursor.getColumnIndex(RetrivalTableSchema.DISPOSITION));
		    String selection = pendingCursor.getString(pendingCursor.getColumnIndex(RetrivalTableSchema.SELECTION));
		    // int expiration = pendingCursor.getInt(pendingCursor.getColumnIndex(RetrivalTableSchema.EXPIRATION));
		    // long createdDate = pendingCursor.getLong(pendingCursor.getColumnIndex(RetrivalTableSchema.CREATED_DATE));
				
		    // Make a query on the row we want to serialize. 
				
		    // Passing the serial
		    // field in the projection tells the content provider to serialize
		    // the row queried and return a matrix cursor with the serialized data
		    // information.
		    Uri rowUri = Uri.parse(uri);
				
		    //String mimeType = InternetMediaType.getInst(cr.getType(rowUri)).setType("application").toString();
				
		    boolean sent = network.dispatchPullRequestToGateway(rowUri.toString(), mime, selection );
				
		    if (!sent) {
			++failedSendCount;
			Toast.makeText(this, "Sending pull request to gateway failed.", Toast.LENGTH_SHORT).show();
		    } else {
			Toast.makeText(this, "Sending pull request to gateway succeeded.", Toast.LENGTH_LONG).show();
		    }
				
		    ContentValues values = new ContentValues();
		    values.put(RetrivalTableSchema.DISPOSITION,  sent
			       ? RetrivalTableSchema.DISPOSITION_SENT 
			       : RetrivalTableSchema.DISPOSITION_FAIL);
					
		    int numUpdated = cr.update(RetrivalTableSchema.getUri(pendingCursor), values, null, null);
		    logger.debug(String.valueOf(numUpdated) + " rows updated to " + (sent ? "sent" : "pending")  + " status");
		}
	    pendingCursor.close();
	}
    }
	
    /**
     * Each time the subscription provider is modified, find out what the
     * changes were and if necessary, send the data to the network server.
     * 
     * Be careful about the race condition; don't leave gaps in the time line.
     * Originally this method used time stamps to determine if the item had be sent.
     * Now a status indicator is used.
     * TODO 
     * Garbage collect items which are expired.
     */
    @Override
	public void processSubscriptionChange(boolean repost) {
	logger.debug("::processSubscriptionChange()");
	if (! bindToNetworkProxyService()) return;
		
	ContentResolver cr = this.getContentResolver();
	String order = SubscriptionTableSchema.PRIORITY_SORT_ORDER;
		
	// Additional items may be added to the table while the current set are being processed
		
	for (; true ; repost = false ) {
	    String[] selectionArgs = null;
	    final String selectPending = 
		"\""+SubscriptionTableSchema.DISPOSITION + "\" IN ("+
		" '" + SubscriptionTableSchema.DISPOSITION_PENDING + "'" +
		(repost ? (", '" + SubscriptionTableSchema.DISPOSITION_SENT + "'" +
			   ", '" + SubscriptionTableSchema.DISPOSITION_FAIL+"'") : "")  +
		")";
		
	    Cursor pendingCursor = cr.query(SubscriptionTableSchema.CONTENT_URI, null, selectPending, selectionArgs, order);
	    if (pendingCursor.getCount() < 1) {
		pendingCursor.close();
		break;
	    }
			
	    int failedSendCount = 0;
	    for (boolean areMoreItems = pendingCursor.moveToFirst(); 
		 areMoreItems ; 
		 areMoreItems = pendingCursor.moveToNext())
		{
		    // For each item in the cursor, ask the content provider to
		    // serialize it, then pass it off to the NPS.
				
		    String mime = pendingCursor.getString(pendingCursor.getColumnIndex(SubscriptionTableSchema.MIME));
		    //String disposition = pendingCursor.getString(pendingCursor.getColumnIndex(SubscriptionTableSchema.DISPOSITION));
		    String selection = pendingCursor.getString(pendingCursor.getColumnIndex(SubscriptionTableSchema.SELECTION));
		    // int expiration = pendingCursor.getInt(pendingCursor.getColumnIndex(SubscriptionTableSchema.EXPIRATION));
		    // long createdDate = pendingCursor.getLong(pendingCursor.getColumnIndex(SubscriptionTableSchema.CREATED_DATE));
				
		    // Make a query on the row we want to serialize. 
				
		    // Passing the serial
		    // field in the projection tells the content provider to serialize
		    // the row queried and return a matrix cursor with the serialized data
		    // information.
		    // Uri rowUri = Uri.parse(uri);
				
		    //String mimeType = InternetMediaType.getInst(cr.getType(rowUri)).setType("application").toString();
				
		    boolean sent = network.dispatchSubscribeRequestToGateway(mime, selection );
				
		    if (!sent) {
				++failedSendCount;
				Toast.makeText(this, "subscription to "+mime+" failed", Toast.LENGTH_SHORT).show();
		    } else {
		    	Toast.makeText(this, "subscription to "+mime+" sent", Toast.LENGTH_SHORT).show();
		    }
				
		    ContentValues values = new ContentValues();
		    values.put(SubscriptionTableSchema.DISPOSITION,  (failedSendCount < 1) 
			       ? SubscriptionTableSchema.DISPOSITION_SENT 
			       : SubscriptionTableSchema.DISPOSITION_FAIL);
					
		    int numUpdated = cr.update(SubscriptionTableSchema.getUri(pendingCursor), values, null, null);
		    logger.debug(String.valueOf(numUpdated) + " rows updated to sent status");
		}
	    pendingCursor.close();
	}
    }
	
    public byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);
    
        // Get the size of the file
        long length = file.length();
    
        if (length > Integer.MAX_VALUE) {
            // File is too large
        }
    
        // Create the byte array to hold the data
        byte[] bytes = new byte[(int)length];
    
        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
               && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
            offset += numRead;
        }
    
        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file "+file.getName());
        }
    
        // Close the input stream and return bytes
        is.close();
        return bytes;
    }

    // ===========================================================
    // Network Proxy Service Calls
    // ===========================================================
    private boolean bindToNetworkProxyService() {
	if (isBoundNPS) return true;
	if (network != null) return isBoundNPS;
		
	// Create a service connection to the Network Proxy Service.
	networkProxyServiceConnection = new ServiceConnection() {
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

	networkProxyServiceIntent = new Intent(INetworkBinder.ACTION);
	isBoundNPS = this.bindService(networkProxyServiceIntent,
				      networkProxyServiceConnection, BIND_AUTO_CREATE);
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
	    logger.debug("PostalObserver::onChange - selfChange = " + String.valueOf(selfChange));
	    callback.processPostalChange(false);
	}
    }
	
    private class RetrivalObserver extends ContentObserver {
	/** Fields */
	private IDistributorService callback;

	public RetrivalObserver(Handler handler, IDistributorService aCallback) {
	    super(handler);
	    callback = aCallback;
	}

	@Override
	    public void onChange(boolean selfChange) {
	    logger.debug("RetrivalObserver::onChange");
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
     * This broadcast receiver is responsible for determining the best
     * channel over which tiles may be acquired.
     * In other words it sets status flags.
     *
     */
    private class MyBroadcastReceiver extends BroadcastReceiver {

	@Override
	    public void onReceive(final Context aContext, final Intent aIntent) {

	    final String action = aIntent.getAction();
	    logger.info("onReceive: " + action);
	    checkResourceStatus(aContext);

	    if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
		// find serialized directory
	    }
	    // it may be that there were items which need to be delivered.
	    DistributorService.this.repostToGateway();
	}
		
	public void checkResourceStatus(final Context aContext) {
	    // 
	    { 
		final WifiManager wm = (WifiManager) aContext.getSystemService(Context.WIFI_SERVICE);
		final int wifiState = wm.getWifiState(); // TODO check for permission or catch error
		if (DEBUGMODE)
		    logger.debug("wifi state=" + wifiState);

		final TelephonyManager tm = (TelephonyManager) aContext.getSystemService(Context.TELEPHONY_SERVICE);
		final int dataState = tm.getDataState(); // TODO check for permission or catch error
		if (DEBUGMODE)
		    logger.debug("telephone data state=" + dataState);
			
		mNetworkConnected = 
		    wifiState == WifiManager.WIFI_STATE_ENABLED
		    || dataState == TelephonyManager.DATA_CONNECTED;
		if (DEBUGMODE)
		    logger.debug("mConnected=" + mNetworkConnected);
	    }

	    {
			final String state = Environment.getExternalStorageState();
			logger.info("sdcard state: " + state);
			mSdCardAvailable = Environment.MEDIA_MOUNTED.equals(state);
			if (DEBUGMODE)
			    logger.debug("mSdcardAvailable=" + mSdCardAvailable);
		    }
		}
    }
	
    // ================================================
    // Calls originating from NetworkProxyService
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
     * 
     * De-serialize into the proper content provider.
     */
    @Override
	public boolean dispatchPullResponse(PullResponse resp) {
	    logger.debug("dispatching pull response : {} : {}",resp.getRequestUid(), resp.getUri());
		String uriStr = resp.getRequestUid(); //resp.getUri(); --- why do we have uri in data message and pull response?
		Uri uri = Uri.parse(uriStr);
		ContentResolver cr = this.getContentResolver();
		try {
		    uri = Uri.withAppendedPath(uri, "_serial");
		    OutputStream outstream = cr.openOutputStream(uri);
		    if (outstream == null) {
		    	logger.error("could not open output stream to content provider: "+uri);
		    	return false;
		    }
		    ByteString data = resp.getData();
		    if (data != null) outstream.write(data.toByteArray());
		    outstream.close();
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
     * Update the content providers as appropriate.
     * These are typically received in response to subscriptions.
     * 
     * The subscribing uri isn't sent with the subscription to
     * the gateway therefore it needs to be recovered from the
     * subscription table.
     */
    @Override
	public boolean dispatchSubscribeResponse(DataMessage resp) {

	String mime = resp.getMimeType();
	ContentResolver cr = this.getContentResolver();
	String tableUriStr = null;
	try {
	    Cursor subCursor = cr.query(SubscriptionTableSchema.CONTENT_URI,null,
					"\""+SubscriptionTableSchema.MIME+"\" = '"+mime+"'",
					null, null);
	    if (!subCursor.moveToFirst()) {
		logger.info("no matching subscription");
		subCursor.close();
		return false;
	    }
	    tableUriStr = subCursor.getString(subCursor.getColumnIndex(SubscriptionTableSchema.URI));	
	    subCursor.close();
			
	    Uri tableUri = Uri.withAppendedPath(Uri.parse(tableUriStr), "_serial");
	    OutputStream outstream = cr.openOutputStream(tableUri);
	    if (outstream == null) {
		logger.error("the content provider "+tableUri.toString()+" is not available");
		return false;
	    }
	    outstream.write(resp.getData().toByteArray());
	    outstream.flush();
	    outstream.close();
	    return true;
			
	} catch (IllegalArgumentException ex) {
	    String msg = "could not serialize to content provider: "+ tableUriStr;
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
	public void processPublicationChange(boolean repost) {
	// TODO Auto-generated method stub
		
    }

}

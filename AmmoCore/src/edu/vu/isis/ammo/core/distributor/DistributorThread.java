package edu.vu.isis.ammo.core.distributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import net.jcip.annotations.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.INetChannel;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.TcpChannel;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.PostalTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.PublicationTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.RetrievalTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.SubscriptionTableSchemaBase;
import edu.vu.isis.ammo.util.InternetMediaType;


/**
 * The distributor service runs in the ui thread.
 * This establishes a new thread for distributing the requests.
 * 
 */
@ThreadSafe
public class DistributorThread 
extends AsyncTask<DistributorService, Integer, Void> 
{
    // ===========================================================
    // Constants
    // ===========================================================
    private static final Logger logger = LoggerFactory.getLogger("ammo:dst");

    // 20 seconds expressed in milliseconds
    private static final int BURP_TIME = 20 * 1000;
  
    public DistributorThread() {
        super();
        this.requestQueue = new LinkedBlockingQueue<AmmoRequest>(20);
        this.responseQueue = 
            new PriorityBlockingQueue<AmmoGatewayMessage>(20, 
                                         new AmmoGatewayMessage.PriorityOrder());
    }
    
    private AtomicBoolean subscriptionDelta = new AtomicBoolean(true);

    public void subscriptionChange() {
    	logger.trace("::subscription change");
        this.signal(this.subscriptionDelta);
    }

    private AtomicBoolean retrievalDelta = new AtomicBoolean(true);

    public void retrievalChange() {
    	logger.trace("::subscription change");
        this.signal(this.retrievalDelta);
    }

    private AtomicBoolean postalDelta = new AtomicBoolean(true);

    public void postalChange() {
    	logger.trace("::subscription change");
        this.signal(this.postalDelta);
    }
    
    /**
     * for handling client application requests
     */
    private AtomicBoolean requestDelta = new AtomicBoolean(true);
    private final BlockingQueue<AmmoRequest> requestQueue;
  
    public String distributeRequest(AmmoRequest request) {
        try {
            logger.trace("received request of type {}", 
                    request.toString());
            
            // FIXME should we generate the uuid here or earlier?
            this.requestQueue.put(request);
            this.signal(this.requestDelta);
            return request.uuid();
            
        } catch (InterruptedException ex) {
            logger.warn("distribute request {}", ex.getStackTrace());
        }
        return null;
    }
    
    /**
     * for handling gateway responses
     */
    private AtomicBoolean responseDelta = new AtomicBoolean(false);
    private final PriorityBlockingQueue<AmmoGatewayMessage> responseQueue;
    
    public boolean distributeResponse(AmmoGatewayMessage agm)
    {
        this.responseQueue.put(agm);
        this.signal(this.responseDelta);
        return true;
    }
    
    private void signal(AtomicBoolean atom) {
        if (!atom.compareAndSet(false, true)) return;
        synchronized(this) { 
             this.notifyAll(); 
        }
    }

    private boolean isReady(DistributorService[] them) {
    	CONNECTED: {
	    	for (DistributorService that : them) {
	    		if (that.getNetworkServiceBinder().isConnected()) {
	    			break CONNECTED;
	    		}
	    	}
	    	return false;
	    }
	    if (this.subscriptionDelta.get()) return true;
	    if (this.retrievalDelta.get()) return true;
	    if (this.postalDelta.get()) return true;
	
	    if (this.responseDelta.get()) return true;
	    if (this.requestDelta.get()) return true;
	    return false;
    }

    private AtomicBoolean retry = new AtomicBoolean(true);
    public void retry() {
    	logger.trace("::resend");
        this.retry.set(true);
        this.subscriptionDelta.set(true);
        this.retrievalDelta.set(true);
        this.postalDelta.set(true);
        
        this.responseDelta.set(true);
        this.requestDelta.set(true);
        synchronized(this) { this.notifyAll(); }
    }

 
    /**
     * The following condition wait holds until
     * there is some work for the distributor.
     */
    @Override
    protected Void doInBackground(DistributorService... them) {
        
        logger.info("::post to network service");
        if (this.retry.getAndSet(false)) {
            for (DistributorService that : them) {
            	if (!that.getNetworkServiceBinder().isConnected()) 
            		continue;
                this.processSubscriptionChange(that, true);
                this.processRetrievalChange(that, true);
                this.processPostalChange(that, true);
            }
        }

        try {
            while (true) {
                // condition wait, is there something to process?
                synchronized (this) { while (!this.isReady(them)) this.wait(BURP_TIME); }
                
                boolean resend = this.retry.getAndSet(false);
                logger.info("process requests, resend? {}", resend);
                    
                if (this.subscriptionDelta.getAndSet(false)) {
                    for (DistributorService that : them) {
                        this.processSubscriptionChange(that, resend);
                    }
                }
                if (this.retrievalDelta.getAndSet(false)) {
                    for (DistributorService that : them) {
                        this.processRetrievalChange(that, resend);
                    }
                }
                if (this.postalDelta.getAndSet(false)) {
                    for (DistributorService that : them) {
                        this.processPostalChange(that, resend);
                    }
                }
                if (this.responseDelta.getAndSet(false)) {
                    while (!this.responseQueue.isEmpty()) {
                        try {
                            AmmoGatewayMessage agm = this.responseQueue.take();
                            for (DistributorService that : them) {
                                this.processResponse(that, agm);
                            }
                        } catch (ClassCastException ex) {
                            logger.error("response queue contains illegal item of class {}", 
                                    ex.getLocalizedMessage());
                        }
                    }
                }
                if (this.requestDelta.getAndSet(false)) {
                    while (!this.requestQueue.isEmpty()) {
                        try {
                            AmmoRequest agm = this.requestQueue.take();
                            for (DistributorService that : them) {
                                this.processRequest(that, agm);
                            }
                        } catch (ClassCastException ex) {
                            logger.error("request queue contains illegal item of class {}", 
                                    ex.getLocalizedMessage());
                        }
                    }
                }
            }
        } catch (InterruptedException ex) {
            logger.warn("task interrupted {}", ex.getStackTrace());
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
    

    // ================= DRIVER METHODS ==================== //
    
    /**
     *  Processes and delivers messages received from the gateway.
     *  - Verify the check sum for the payload is correct
     *  - Parse the payload into a message
     *  - Receive the message
     *
     * @param instream
     * @return was the message clean (true) or garbled (false).
     */
    private boolean processRequest(DistributorService that, AmmoRequest agm) {
        logger.info("::processRequest {}",agm);
        switch (agm.action){
        case POSTAL: processPostalRequest(that, agm, 1); break;
        case DIRECTED_POSTAL: processPostalRequest(that, agm, 2); break;
        case PUBLISH: break;
        case RETRIEVAL: processRetrievalRequest(that, agm); break;
        case SUBSCRIBE: processSubscribeRequest(that, agm, 1); break;
        case DIRECTED_SUBSCRIBE: processSubscribeRequest(that, agm, 2); break;
        }
        return true;
    }
    
    /**
     *  Processes and delivers messages received from the gateway.
     *  - Verify the check sum for the payload is correct
     *  - Parse the payload into a message
     *  - Receive the message
     *
     * @param instream
     * @return was the message clean (true) or garbled (false).
     */
    private boolean processResponse(Context context, AmmoGatewayMessage agm) {
        logger.info("::processResponse {}", agm);

        CRC32 crc32 = new CRC32();
        crc32.update(agm.payload);
        if (crc32.getValue() != agm.payload_checksum) {
            logger.warn("you have received a bad message, the checksums [{}:{}] did not match",
                    Long.toHexString(crc32.getValue()), Long.toHexString(agm.payload_checksum));
            return false;
        }

        AmmoMessages.MessageWrapper mw = null;
        try {
            mw = AmmoMessages.MessageWrapper.parseFrom(agm.payload);
        } catch (InvalidProtocolBufferException ex) {
            logger.error("parsing gateway message {}", ex.getStackTrace());
        }
        if (mw == null) {
            logger.error( "mw was null!" );
            return false; // TBD SKN: this was true, why? if we can't parse it then its bad
        }

        switch (mw.getType()) {

        case DATA_MESSAGE:
            logger.info("data interest");
            receiveSubscriptionResponse(context, mw);
            break;

        case AUTHENTICATION_RESULT:
            boolean result = receiveAuthenticationResponse(context, mw);
            logger.error( "authentication result={}", result );
            break;

        case PUSH_ACKNOWLEDGEMENT:
            logger.info("push ack");
            receivePostalResponse(context, mw);
            break;

        case PULL_RESPONSE:
            logger.info("pull response");
            receiveRetrievalResponse(context, mw);
            break;
            
        case HEARTBEAT:
            logger.info("heartbeat");
            break;
        case AUTHENTICATION_MESSAGE:
        case SUBSCRIBE_MESSAGE:
        case PULL_REQUEST:
        case UNSUBSCRIBE_MESSAGE:
            logger.warn( "received an outbound message type {}", mw.getType());
            break;
        default:
            logger.error( "mw.getType() returned an unexpected type. {}", mw.getType());
        }
        return true;
    }


    /**
     * Give to the network service for verification.
     * 
     * Get the session id set by the gateway.
     *
     * @param mw
     * @return
     */
    private boolean receiveAuthenticationResponse(Context context, AmmoMessages.MessageWrapper mw) {
        logger.info("::receiveAuthenticationResponse");

        if (mw == null) return false;
        if (! mw.hasAuthenticationResult()) return false;
        if (mw.getAuthenticationResult().getResult() != AmmoMessages.AuthenticationResult.Status.SUCCESS) {
            return false;
        }
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(INetPrefKeys.NET_CONN_PREF_IS_ACTIVE, true)
            .commit();
        // sessionId = mw.getSessionUuid();

        // the distributor doesn't need to know about authentication results.
        return true;
    }


    private boolean collectGarbage = true;

    // =========== POSTAL ====================
    /**
     * Every time the distributor provider is modified, find out what the
     * changes were and, if necessary, send the data to the server. Be
     * careful about the race condition; don't leave gaps in the time line.
     * Originally this method used time stamps to determine if the item had
     * be sent. Now a status indicator is used.
     * 
     * We can't loop the main while loop forever because there may be times
     * that a getNetworkServiceBinder() connection is not available (causing
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
          .append("'").append(PostalTableSchema.DISPOSITION_QUEUED).append("'")
          .append(",")
          .append("'").append(PostalTableSchema.DISPOSITION_FAIL).append("'")
          .append(")");
        POSTAL_RESEND = sb.toString();
    }
    
    private static final String POSTAL_EXPIRATION_CONDITION; 
    private static final ContentValues POSTAL_EXPIRATION_UPDATE;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append('"').append(PostalTableSchema.EXPIRATION).append('"')
          .append('<').append('?');
        POSTAL_EXPIRATION_CONDITION = sb.toString();
        
        POSTAL_EXPIRATION_UPDATE= new ContentValues();
        POSTAL_EXPIRATION_UPDATE.put(PostalTableSchema.DISPOSITION,
                PostalTableSchema.DISPOSITION_EXPIRED);
    }
    
    private void processPostalChange(DistributorService that, boolean resend) {
        logger.info("::processPostalChange()");

        if (!that.getNetworkServiceBinder().isConnected()) 
            return;

        final ContentResolver resolver = that.getContentResolver();

        if (collectGarbage) {
            resolver.update(PostalTableSchema.CONTENT_URI, 
                    POSTAL_EXPIRATION_UPDATE,
                    POSTAL_EXPIRATION_CONDITION, new String[]{Long.toString(System.currentTimeMillis())} );
            resolver.delete(PostalTableSchema.CONTENT_URI, POSTAL_GARBAGE, null);
       }
        
        int prevPendingCount = 0;

        for (; true; resend = false) {
            String[] selectionArgs = null;

            final Cursor cursor = resolver.query(PostalTableSchema.CONTENT_URI, null, 
                    (resend ? POSTAL_RESEND : POSTAL_SEND), selectionArgs, 
                    PostalTableSchema.PRIORITY_SORT_ORDER);

            final int curCount = cursor.getCount();

            if (curCount == prevPendingCount) {
                cursor.close();
                break; // no new items to send
            }

            prevPendingCount = curCount;
            // Iterate over each row serializing its data and sending it.
            for (boolean moreItems = cursor.moveToFirst(); moreItems; 
                 moreItems = cursor.moveToNext()) 
            {
                String rowUri = cursor.getString(
                    cursor.getColumnIndex(PostalTableSchema.URI));
                String cpType = cursor.getString(
                    cursor.getColumnIndex(PostalTableSchema.CP_TYPE));

                logger.debug("serializing: " + rowUri);
                logger.debug("rowUriType: " + cpType);

                String mimeType = InternetMediaType.getInst(cpType)
                        .setType("application").toString();
                byte[] serialized;

                int serialType = cursor.getInt(
                    cursor.getColumnIndex(PostalTableSchema.SERIALIZE_TYPE));

                switch (serialType) {
                case PostalTableSchema.SERIALIZE_TYPE_DIRECT:
                    int dataColumnIndex = cursor.getColumnIndex(PostalTableSchema.DATA);

                    if (!cursor.isNull(dataColumnIndex)) {
                        String data = cursor.getString(dataColumnIndex);
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
                        serialized = queryUriForSerializedData(that, Uri.parse(rowUri));
                    } catch (IOException e1) {
                        logger.error("invalid row for serialization");
                        continue;
                    }
                }
                if (serialized == null) {
                    logger.error("no serialized data produced");
                    continue;
                }

                // Dispatch the request.
                try {
                    if (!that.getNetworkServiceBinder().isConnected()) {
                        logger.info("no network connection");
                    } else {
                        final Uri postalUri = PostalTableSchema.getUri(cursor);
                        final ContentValues values = new ContentValues();

                        values.put(PostalTableSchema.DISPOSITION,
                                PostalTableSchema.DISPOSITION_QUEUED);
                        @SuppressWarnings("unused")
                        int numUpdated = resolver.update(postalUri, values, null, null);

                        Map<Class<? extends INetChannel>,Boolean> dispatchResult = 
                            this.dispatchPostalRequest(that,
                                rowUri.toString(),
                                mimeType,
                                serialized,
                                new INetworkService.OnSendMessageHandler() {
    
                                    @Override
                                    public boolean ack(Class<? extends INetChannel> clazz, boolean status) {
    
                                        // Update distributor status
                                        // if message dispatch
                                        // successful.
                                        ContentValues values = new ContentValues();
    
                                        values.put(PostalTableSchema.DISPOSITION,
                                            (status) ? PostalTableSchema.DISPOSITION_SENT
                                                    : PostalTableSchema.DISPOSITION_FAIL);
                                        int numUpdated = resolver.update(
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
                        if (!dispatchResult.get(TcpChannel.class)) {
                            values.put(PostalTableSchema.DISPOSITION,
                                    PostalTableSchema.DISPOSITION_PENDING);
                            resolver.update(postalUri, values, null, null);
                        }
                    }
                } catch (NullPointerException ex) {
                    logger.warn("NullPointerException, sending to gateway failed");
                }
            }
            cursor.close();
        }
    }
    
    /**
     * Used when a new postal request arrives.
     * It first tries to dispatch the request to the network service.
     * Regardless of whether that works, the request is recorded for later use.
     * 
     * @param that
     * @param uri
     * @param mimeType
     * @param data
     * @param handler
     * @return
     */
    private void processPostalRequest(DistributorService that, AmmoRequest agm, int st) {
        logger.info("::processPostalRequest()");

        final ContentResolver resolver = that.getContentResolver();

        String mimeType = agm.topic.asString();
        final byte[] serialized;
        
        switch (st) {
        case PostalTableSchema.SERIALIZE_TYPE_DIRECT:
            serialized = agm.payload.asBytes();
            break;

        case PostalTableSchema.SERIALIZE_TYPE_INDIRECT:
        case PostalTableSchema.SERIALIZE_TYPE_DEFERRED:
        default:
            try {
                serialized = queryUriForSerializedData(that, agm.provider.asUri());
            } catch (IOException e1) {
                logger.error("invalid row for serialization");
                return;
            }
        }
        if (serialized == null) {
            logger.error("no serialized data produced");
            return;
        }

        // Dispatch the message.
        try {
            final ContentValues values = new ContentValues();
            values.put(PostalTableSchemaBase.CP_TYPE, mimeType);
            values.put(PostalTableSchemaBase.URI, agm.provider.toString());
            values.put(PostalTableSchemaBase.SERIALIZE_TYPE, PostalTableSchemaBase.SERIALIZE_TYPE_INDIRECT);
            values.put(PostalTableSchemaBase.EXPIRATION, agm.durability);
            values.put(PostalTableSchemaBase.UNIT, 50);
            values.put(PostalTableSchemaBase.PRIORITY, agm.priority);
            //if (notice != null) 
                //values.put(PostalTableSchemaBase.NOTICE, serializePendingIntent(notice));
            values.put(PostalTableSchemaBase.CREATED_DATE, System.currentTimeMillis());
            
            
            if (!that.getNetworkServiceBinder().isConnected()) {
                values.put(PostalTableSchemaBase.DISPOSITION, 
                        PostalTableSchemaBase.DISPOSITION_PENDING);
                
                resolver.insert(PostalTableSchemaBase.CONTENT_URI, values);
                logger.info("no network connection");
                return;
            }
            
            values.put(PostalTableSchema.DISPOSITION,
                    PostalTableSchema.DISPOSITION_QUEUED);
            final Uri postalUri = resolver.insert(PostalTableSchemaBase.CONTENT_URI, values);
            
            Map<Class<? extends INetChannel>,Boolean> dispatchResult = 
                this.dispatchPostalRequest(that,
                    agm.provider.toString(),
                    mimeType,
                    serialized,
                    new INetworkService.OnSendMessageHandler() {
    
                        @Override
                        public boolean ack(Class<? extends INetChannel> clazz, boolean status) {
    
                            // Update distributor status
                            // if message dispatch
                            // successful.
                            final ContentValues values = new ContentValues();
    
                            values.put(PostalTableSchema.DISPOSITION,
                                (status) ? PostalTableSchema.DISPOSITION_SENT
                                        : PostalTableSchema.DISPOSITION_FAIL);
                            final int numUpdated = resolver.update(
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
            if (!dispatchResult.get(TcpChannel.class)) {
                values.put(PostalTableSchema.DISPOSITION,
                        PostalTableSchema.DISPOSITION_PENDING);
                resolver.update(postalUri, values, null, null);
            }
        } catch (NullPointerException ex) {
            logger.warn("NullPointerException, sending to gateway failed");
        }
    }
    

    /**
     * dispatch the request to the network service.
     * It is presumed that the connection to the network service
     * exists before this method is called.
     * 
     * @param that
     * @param uri
     * @param mimeType
     * @param data
     * @param handler
     * @return
     */
    private Map<Class<? extends INetChannel>,Boolean> 
    dispatchPostalRequest(DistributorService that, String uri, String mimeType, 
            byte []data, INetworkService.OnSendMessageHandler handler) 
    {
        logger.info("::dispatchPostalRequest");

        final Long now = System.currentTimeMillis();
        logger.debug("Building MessageWrapper: data size {} @ time {}", data.length, now);
        
        final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType(AmmoMessages.MessageWrapper.MessageType.DATA_MESSAGE);
 
        final AmmoMessages.DataMessage.Builder pushReq = AmmoMessages.DataMessage.newBuilder();
        pushReq.setUri(uri)
               .setMimeType(mimeType)
               .setData(ByteString.copyFrom(data));

        mw.setDataMessage(pushReq);

        logger.debug("Finished wrap build @ time {}...difference of {} ms \n",System.currentTimeMillis(), System.currentTimeMillis()-now);
        final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder( mw, handler);
        
        final DistributorPolicy.Topic topic = that.policy().match(mimeType);
       
        return that.getNetworkServiceBinder().sendRequest(agmb.build(), topic);
    }
    
    
    /**
     * Get response to PushRequest from the gateway.
     * This should be seen in response to a post passing
     * through transition for which a notice has been requested.
     *
     * @param mw
     * @return
     */
    private boolean receivePostalResponse(Context context, AmmoMessages.MessageWrapper mw) {
        logger.info("::receivePushResponse");

        if (mw == null) return false;
        if (! mw.hasPushAcknowledgement()) return false;
        // PushAcknowledgement pushResp = mw.getPushAcknowledgement();
        return true;
    }


    // =========== PUBLICATION ====================
    
    @SuppressWarnings("unused")
    static final private String selectPublicationUri = "\""+PublicationTableSchemaBase.URI+"\"=?";
    
    @SuppressWarnings("unused")
    private void processPublicationChange(DistributorService that, boolean resend) {
        logger.error("::processPublicationChange : {} : not implemented", resend);
    }

    /**
     * Used when a new postal request arrives.
     * It first tries to dispatch the request to the network service.
     * Regardless of whether that works, the request is recorded for later use.
     * 
     * @param that
     * @param uri
     * @param mimeType
     * @param data
     * @param handler
     * @return
     */
    @SuppressWarnings("unused")
    private void processPublicationRequest(DistributorService that, AmmoRequest agm, int st) {
        logger.info("::processPublicationRequest()");
    }
    
    /**
     * dispatch the request to the network service.
     * It is presumed that the connection to the network service
     * exists before this method is called.
     * 
     * @param that
     * @param uri
     * @param mimeType
     * @param data
     * @param handler
     * @return
     */
    @SuppressWarnings("unused")
    private Map<Class<? extends INetChannel>,Boolean> 
    dispatchPublicationRequest(DistributorService that, String uri, String mimeType, 
            byte []data, INetworkService.OnSendMessageHandler handler) 
    {
        logger.info("::dispatchPublicationRequest");
        return null;
    }
    /**
     * Get response to PushRequest from the gateway.
     * This should be seen in response to a post passing
     * through transition for which a notice has been requested.
     *
     * @param mw
     * @return
     */
    @SuppressWarnings("unused")
    private boolean receivePublicationResponse(Context context, AmmoMessages.MessageWrapper mw) {
        logger.info("::receivePublicationResponse");

        if (mw == null) return false;
        if (! mw.hasPushAcknowledgement()) return false;
        // PushAcknowledgement pushResp = mw.getPushAcknowledgement();
        return true;
    }
    


     // =========== RETRIEVAL ====================
    /**
     * Each time the enrollment provider is modified, find out what the
     * changes were and if necessary, send the data to the
     * getNetworkServiceBinder() server.
     * 
     * Be careful about the race condition; don't leave gaps in the time
     * line. Originally this method used time stamps to determine if the
     * item had be sent. Now a status indicator is used.
     * 
     * Garbage collect items which are expired.
     */
    
    static final private String SELECT_RETRIEVAL_URI;
    static {
         SELECT_RETRIEVAL_URI = "\""+RetrievalTableSchemaBase.URI+"\"=?";
    }

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
          .append("'").append(RetrievalTableSchema.DISPOSITION_QUEUED).append("'")
          .append(",")
          .append("'").append(RetrievalTableSchema.DISPOSITION_FAIL).append("'")
          .append(",")
          .append("'").append(RetrievalTableSchema.DISPOSITION_SENT).append("'")
          .append(")");
        RETRIEVAL_RESEND = sb.toString();
    }
    
    private static final String RETRIEVAL_EXPIRATION_CONDITION; 
    private static final ContentValues RETRIEVAL_EXPIRATION_UPDATE;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append('"').append(RetrievalTableSchemaBase.EXPIRATION).append('"')
          .append('<').append('?');
        RETRIEVAL_EXPIRATION_CONDITION = sb.toString();
        
        RETRIEVAL_EXPIRATION_UPDATE= new ContentValues();
        RETRIEVAL_EXPIRATION_UPDATE.put(RetrievalTableSchemaBase.DISPOSITION,
                RetrievalTableSchemaBase.DISPOSITION_EXPIRED);
    }
    
    private void processRetrievalChange(DistributorService that, boolean resend) {
        logger.info("::processRetrievalChange()");

        final ContentResolver resolver = that.getContentResolver();
        
        if (collectGarbage) {
            resolver.update(RetrievalTableSchema.CONTENT_URI, 
                    RETRIEVAL_EXPIRATION_UPDATE,
                    RETRIEVAL_EXPIRATION_CONDITION, new String[]{Long.toString(System.currentTimeMillis())} );
            resolver.delete(RetrievalTableSchema.CONTENT_URI, RETRIEVAL_GARBAGE, null);
        }
        
        // Additional items may be added to the table while the current set
        // are being processed

        for (; true; resend = false) {
            String[] selectionArgs = null;
            final Cursor pendingCursor = resolver.query(
                    RetrievalTableSchema.CONTENT_URI, null, 
                    (resend ? RETRIEVAL_RESEND : RETRIEVAL_SEND), selectionArgs, 
                    RetrievalTableSchema.PRIORITY_SORT_ORDER);

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

                final Uri retrieveUri = RetrievalTableSchema.getUri(pendingCursor);
                final ContentValues values = new ContentValues();
                values.put(RetrievalTableSchema.DISPOSITION,
                        RetrievalTableSchema.DISPOSITION_QUEUED);

                @SuppressWarnings("unused")
                final int numUpdated = resolver.update(retrieveUri, values, null, null);

                Map<Class<? extends INetChannel>,Boolean> dispatchResult = 
                    this.dispatchRetrievalRequest(that, 
                        rowUri.toString(), mime,
                        selection,
                        new INetworkService.OnSendMessageHandler() {
                            @Override
                            public boolean ack(Class<? extends INetChannel> clazz, boolean status) {
                                // Update distributor status if
                                // message dispatch successful.
                                ContentValues values = new ContentValues();

                                values.put(RetrievalTableSchema.DISPOSITION,
                                        status ? RetrievalTableSchema.DISPOSITION_SENT
                                            : RetrievalTableSchema.DISPOSITION_FAIL);

                                int numUpdated = resolver.update(
                                        retrieveUri, values, null,
                                        null);

                                logger.info("{} rows updated to {} status",
                                        numUpdated, (status ? "sent" : "pending"));
                                return false;
                            }
                        });
                if (!dispatchResult.get(TcpChannel.class)) {
                    values.put(RetrievalTableSchema.DISPOSITION,
                            RetrievalTableSchema.DISPOSITION_PENDING);
                    resolver.update(retrieveUri, values, null, null);
                }
            }
            pendingCursor.close();
        }
    }
   
    
    /**
     * Process the subscription request.
     * There are two parts:
     * 1) checking to see if the network service is accepting requests 
     *    and sending the request if it is
     * 2) placing the request in the table.
     * 
     * The second step must be done first, so as to avoid a race to update
     * the status of the request.
     * The handling of insert v. update is also handled here.
     * 
     * @param that
     * @param agm
     * @param st
     */
    private void processRetrievalRequest(DistributorService that, AmmoRequest agm) {
        logger.info("::processRetrievalRequest()");

        final ContentResolver resolver = that.getContentResolver();
        final String mimeType = agm.topic.asString();

        // Dispatch the message.
        try {
            
            final ContentValues values = new ContentValues();
            values.put(RetrievalTableSchemaBase.MIME, mimeType);
            values.put(RetrievalTableSchemaBase.URI, agm.payload.asString());
            values.put(RetrievalTableSchemaBase.DISPOSITION, RetrievalTableSchemaBase.DISPOSITION_PENDING);
            //values.put(RetrievalTableSchemaBase.EXPIRATION, expiration.getTimeInMillis());
            
            // values.put(RetrievalTableSchemaBase.SELECTION, agm.select_query);
            values.put(RetrievalTableSchemaBase.PROJECTION, "");
            values.put(RetrievalTableSchemaBase.CREATED_DATE, System.currentTimeMillis());
            // if (notice != null) 
            //     values.put(RetrievalTableSchemaBase.NOTICE, serializePendingIntent(notice));
            
            final boolean queuable = that.getNetworkServiceBinder().isConnected();
            
            values.put(RetrievalTableSchemaBase.DISPOSITION, 
                    queuable
                       ? RetrievalTableSchemaBase.DISPOSITION_QUEUED
                       : RetrievalTableSchemaBase.DISPOSITION_PENDING);
            
            final Cursor queryCursor = resolver.query(
                    RetrievalTableSchemaBase.CONTENT_URI, // content provider uri
                    new String[]{BaseColumns._ID},        // projection
                    SELECT_RETRIEVAL_URI, new String[]{agm.provider.toString()}, // selection
                    RetrievalTableSchema.PRIORITY_SORT_ORDER);
            
            final Uri refUri;
            if (queryCursor.getCount() == 1) {
                logger.debug("found an existing pull request in the retrieval table ... updating ...");
                queryCursor.moveToFirst(); // there is only one
                long refId = queryCursor.getLong(queryCursor.getColumnIndex(BaseColumns._ID));
                refUri = ContentUris.withAppendedId(RetrievalTableSchemaBase.CONTENT_URI, refId);
                resolver.update(refUri, values, null, null);
            } else if  (queryCursor.getCount() > 1) {
                logger.warn("corrupted subscriber content provider; removing offending tuples");
                resolver.delete(RetrievalTableSchemaBase.CONTENT_URI, 
                        SELECT_RETRIEVAL_URI, new String[]{agm.provider.toString()});
                refUri = resolver.insert(RetrievalTableSchemaBase.CONTENT_URI, values);
            } else {
                logger.debug("creating a pull request in retrieval table ... inserting ...");
                refUri = resolver.insert(RetrievalTableSchemaBase.CONTENT_URI, values);
            }
            if (! queuable) return;  // cannot send now, maybe later
            
            Map<Class<? extends INetChannel>,Boolean> dispatchResult = 
                this.dispatchRetrievalRequest(that, 
                     agm.provider.toString(), mimeType,
                     agm.select.query.select(),
                     new INetworkService.OnSendMessageHandler() {
                         @Override
                         public boolean ack(Class<? extends INetChannel> clazz, boolean status) {
                             // Update distributor status if
                             // message dispatch successful.
                             ContentValues values = new ContentValues();

                             values.put(RetrievalTableSchema.DISPOSITION,
                                     status ? RetrievalTableSchema.DISPOSITION_SENT
                                         : RetrievalTableSchema.DISPOSITION_FAIL);

                             int numUpdated = resolver.update(
                                     refUri, values, null,
                                     null);

                             logger.info("{} rows updated to {} status",
                                     numUpdated, (status ? "sent" : "pending"));
                             return false;
                         }
                     });
            if (!dispatchResult.get(TcpChannel.class)) {
                 values.put(RetrievalTableSchema.DISPOSITION,
                         RetrievalTableSchema.DISPOSITION_PENDING);
                 resolver.update(refUri, values, null, null);
                 // break; // no point in trying any more
             }
        } catch (NullPointerException ex) {
            logger.warn("NullPointerException, sending to gateway failed");
        }
    }

    /**
     * The retrieval request is sent to 
     * @param that
     * @param subscriptionId
     * @param mimeType
     * @param selection
     * @param handler
     * @return
     */

    private Map<Class<? extends INetChannel>,Boolean> 
    dispatchRetrievalRequest(DistributorService that, String subscriptionId, String mimeType, 
    		String selection, INetworkService.OnSendMessageHandler handler) {
        logger.info("::dispatchRetrievalRequest");

        /** Message Building */

        AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType(AmmoMessages.MessageWrapper.MessageType.PULL_REQUEST);
        //mw.setSessionUuid(sessionId);

        AmmoMessages.PullRequest.Builder pushReq = AmmoMessages.PullRequest.newBuilder();

        pushReq.setRequestUid(subscriptionId)
               .setMimeType(mimeType);

        if (selection != null) pushReq.setQuery(selection);

        // projection
        // max_results
        // start_from_count
        // live_query
        // expiration

        mw.setPullRequest(pushReq);
       
        AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder( mw, handler);
        agmb.isGateway(true);
        return that.getNetworkServiceBinder().sendRequest(agmb.build(), null);
    }
    

    /**
     * Get response to RetrievalRequest, PullResponse, from the gateway.
     *
     * @param mw
     * @return
     */
    private boolean receiveRetrievalResponse(Context context, AmmoMessages.MessageWrapper mw) {
        logger.info("::receiveRetrievalResponse");

        if (mw == null) return false;
        if (! mw.hasPullResponse()) return false;
        final AmmoMessages.PullResponse resp = mw.getPullResponse();

        String uriStr = resp.getRequestUid(); 
        // FIXME --- why do we have uri in data message and retrieval response?
        final Uri uri = Uri.parse(uriStr);
        final ContentResolver resolver = context.getContentResolver();

        try {
            final Uri serialUri = Uri.withAppendedPath(uri, "_serial");
            final OutputStream outstream = resolver.openOutputStream(serialUri);

            if (outstream == null) {
                logger.error( "could not open output stream to content provider: {} ",serialUri);
                return false;
            }
            final ByteString data = resp.getData();

            if (data != null) {
                outstream.write(data.toByteArray());
            }
            outstream.close();

            // This update/delete the retrieval request, it is fulfilled.
            
            final Cursor cursor = resolver.query(RetrievalTableSchema.CONTENT_URI, null, 
                    SELECT_RETRIEVAL_URI, new String[]{uri.toString()}, 
                    RetrievalTableSchema.PRIORITY_SORT_ORDER);
            
            if (!cursor.moveToFirst()) {
                logger.info("no matching retrieval: {}", uri);
                cursor.close();
                return false;
            }
            final Uri retrieveUri = RetrievalTableSchema.getUri(cursor);
            cursor.close ();
            ContentValues values = new ContentValues();
            values.put(RetrievalTableSchema.DISPOSITION, RetrievalTableSchema.DISPOSITION_SATISFIED);

            @SuppressWarnings("unused")
            int numUpdated = resolver.update(retrieveUri, values,null, null);
            
        } catch (FileNotFoundException e) {
            logger.warn("could not connect to content provider");
            return false;
        } catch (IOException e) {
            logger.warn("could not write to the content provider");
        }
        return true;
    }


    // =========== SUBSCRIBE ====================
    /**
     * Each time the subscription provider is modified, find out what the
     * changes were and if necessary, send the data to the
     * getNetworkServiceBinder() server.
     * 
     * Be careful about the race condition; don't leave gaps in the time
     * line. Originally this method used time stamps to determine if the
     * item had be sent. Now a status indicator is used.
     * 
     * Garbage collect items which are expired.
     */
    static final private String SELECT_SUBSCRIPTION_URI;
    static final private String SELECT_SUBSCRIPTION_TYPE;
    static final private String SELECT_SUBSCRIPTION_URI_TYPE;
    static {
        SELECT_SUBSCRIPTION_URI = "\""+SubscriptionTableSchemaBase.URI+"\"=?";
        SELECT_SUBSCRIPTION_TYPE = "\"" + SubscriptionTableSchema.MIME + "\"=?";
        SELECT_SUBSCRIPTION_URI_TYPE = SELECT_SUBSCRIPTION_URI + " AND " + SELECT_SUBSCRIPTION_TYPE;
    }
    
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
          .append("'").append(SubscriptionTableSchema.DISPOSITION_QUEUED).append("'")
          .append(",")
          .append("'").append(SubscriptionTableSchema.DISPOSITION_FAIL).append("'")
          .append(",")
          .append("'").append(SubscriptionTableSchema.DISPOSITION_SENT).append("'")
          .append(")");
        SUBSCRIPTION_RESEND = sb.toString();
    }
    
    private static final String SUBSCRIPTION_EXPIRATION_CONDITION; 
    private static final ContentValues SUBSCRIPTION_EXPIRIATION_UPDATE;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append('"').append(SubscriptionTableSchemaBase.EXPIRATION).append('"')
          .append('<').append('?');
        SUBSCRIPTION_EXPIRATION_CONDITION = sb.toString();
        
        SUBSCRIPTION_EXPIRIATION_UPDATE= new ContentValues();
        SUBSCRIPTION_EXPIRIATION_UPDATE.put(SubscriptionTableSchema.DISPOSITION,
                    SubscriptionTableSchema.DISPOSITION_EXPIRED);
    }
    
    private void processSubscriptionChange(DistributorService that, boolean resend) {
        logger.info("::processSubscriptionChange()");

        if (!that.getNetworkServiceBinder().isConnected())
            return;

        final ContentResolver resolver = that.getContentResolver();
        if (collectGarbage) {
             resolver.update(SubscriptionTableSchema.CONTENT_URI, 
                     SUBSCRIPTION_EXPIRIATION_UPDATE,
                     SUBSCRIPTION_EXPIRATION_CONDITION, 
                     new String[]{Long.toString(System.currentTimeMillis())} );
             resolver.delete(SubscriptionTableSchema.CONTENT_URI, SUBSCRIPTION_GARBAGE, null);
        }

        // Additional items may be added to the table while the current set
        // are being processed

        for (; true; resend = false) {
            String[] selectionArgs = null;

            final Cursor pendingCursor = resolver.query(
                    SubscriptionTableSchema.CONTENT_URI, null, 
                    (resend ? SUBSCRIPTION_RESEND : SUBSCRIPTION_SEND), selectionArgs, 
                    SubscriptionTableSchema.PRIORITY_SORT_ORDER);

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

                final ContentValues values = new ContentValues();
                values.put(SubscriptionTableSchema.DISPOSITION,
                        SubscriptionTableSchema.DISPOSITION_QUEUED);

                @SuppressWarnings("unused")
                int numUpdated = resolver.update(subUri, values, null, null);

                Map<Class<? extends INetChannel>,Boolean> dispatchResult = 
                    this.dispatchSubscribeRequest(that, 
                        mime, selection,
                        new INetworkService.OnSendMessageHandler() {
                            @Override
                            public boolean ack(Class<? extends INetChannel> clazz, boolean status) {
                                // Update distributor status if
                                // message dispatch successful.
                                ContentValues values = new ContentValues();
                                values.put( SubscriptionTableSchema.DISPOSITION,
                                                (status) ? SubscriptionTableSchema.DISPOSITION_SENT
                                                         : SubscriptionTableSchema.DISPOSITION_FAIL);

                                int numUpdated = resolver.update(subUri, values, null, null);

                                logger.info("Subscription: {} rows updated to {} status ",
                                        numUpdated, (status ? "sent" : "pending"));
                                return true;
                            }
                        });
                if (!dispatchResult.get(TcpChannel.class)) {
                    values.put(SubscriptionTableSchema.DISPOSITION,
                            SubscriptionTableSchema.DISPOSITION_PENDING);
                    resolver.update(subUri, values, null, null);
                    // break; // no point in trying any more
                }
            }
            pendingCursor.close();
        }
    }
    
    
    /**
     * Process the subscription request.
     * There are two parts:
     * 1) checking to see if the network service is accepting requests 
     *    and sending the request if it is
     * 2) placing the request in the table.
     * 
     * The second step must be done first, so as to avoid a race to update
     * the status of the request.
     * The handling of insert v. update is also handled here.
     * 
     * @param that
     * @param agm
     * @param st
     */
    private void processSubscribeRequest(DistributorService that, AmmoRequest agm, int st) {
        logger.info("::processSubscribeRequest()");

        final ContentResolver resolver = that.getContentResolver();
        final String mimeType = agm.topic.asString();

        // Dispatch the message.
        try {
            final ContentValues values = new ContentValues();
            values.put(SubscriptionTableSchemaBase.MIME, mimeType);
            values.put(SubscriptionTableSchemaBase.URI, agm.payload.asString());
            values.put(SubscriptionTableSchemaBase.DISPOSITION, SubscriptionTableSchemaBase.DISPOSITION_PENDING);
            // values.put(SubscriptionTableSchemaBase.EXPIRATION, agm.e.getTimeInMillis());
            
            // values.put(SubscriptionTableSchemaBase.SELECTION, agm.select_query);
            values.put(SubscriptionTableSchemaBase.CREATED_DATE, System.currentTimeMillis());
            // if (notice != null) 
            //     values.put(SubscriptionTableSchemaBase.NOTICE, serializePendingIntent(notice));
           
            final Cursor queryCursor = resolver.query(SubscriptionTableSchema.CONTENT_URI, 
                    new String[] {BaseColumns._ID,SubscriptionTableSchemaBase.EXPIRATION}, 
                    SELECT_SUBSCRIPTION_URI_TYPE, new String[] {agm.provider.toString(), mimeType},
                    null);
            if (queryCursor == null) {
               logger.warn("missing subscriber content provider");
               return;
            }
            final Uri requestUri;
            if (queryCursor.getCount() == 1) {
                if (! queryCursor.moveToFirst())  {
                    logger.error("the cursor claimed to have exactly one tuple");
                    return;
                }
                long queryId = queryCursor.getLong(queryCursor.getColumnIndex(SubscriptionTableSchema._ID));
                long queryExpiration = queryCursor.getLong(queryCursor.getColumnIndex(SubscriptionTableSchema.EXPIRATION));

                if (queryExpiration == 0) // if this was an expired subscription then set its DISPOSITION to PENDING
                    values.put(SubscriptionTableSchema.DISPOSITION, SubscriptionTableSchema.DISPOSITION_PENDING);

                requestUri = ContentUris.withAppendedId(SubscriptionTableSchema.CONTENT_URI, queryId);
                resolver.update(requestUri, values, null, null);
                    
            } else if  (queryCursor.getCount() > 1) {
                logger.warn("corrupted subscription relation in  content provider; removing offending tuples");
                resolver.delete(SubscriptionTableSchema.CONTENT_URI, 
                        SELECT_SUBSCRIPTION_URI, new String[] {agm.provider.toString()});
                requestUri = resolver.insert(SubscriptionTableSchema.CONTENT_URI, values);
            } else {
                // if its a new entry set the DISPOSITION to pending - else leave it as is ...
                values.put(SubscriptionTableSchema.DISPOSITION, SubscriptionTableSchema.DISPOSITION_PENDING);
                requestUri = resolver.insert(SubscriptionTableSchema.CONTENT_URI, values);
            }
            if (!that.getNetworkServiceBinder().isConnected()) {
                values.put(SubscriptionTableSchemaBase.DISPOSITION, 
                        SubscriptionTableSchemaBase.DISPOSITION_PENDING);
                
                resolver.insert(SubscriptionTableSchemaBase.CONTENT_URI, values);
                logger.info("no network connection");
                return;
            }
            
            values.put(SubscriptionTableSchema.DISPOSITION,
                    SubscriptionTableSchema.DISPOSITION_QUEUED);
             final Uri retrievalUri = resolver.insert(SubscriptionTableSchemaBase.CONTENT_URI, values);
            
             Map<Class<? extends INetChannel>,Boolean> dispatchResult = 
                 this.dispatchSubscribeRequest(that, 
                     agm.provider.toString(), mimeType,
                     new INetworkService.OnSendMessageHandler() {
                         @Override
                         public boolean ack(Class<? extends INetChannel> clazz, boolean status) {
                             // Update distributor status if
                             // message dispatch successful.
                             ContentValues values = new ContentValues();

                             values.put(SubscriptionTableSchema.DISPOSITION,
                                     status ? SubscriptionTableSchema.DISPOSITION_SENT
                                         : SubscriptionTableSchema.DISPOSITION_FAIL);

                             int numUpdated = resolver.update(
                                     retrievalUri, values, null,
                                     null);

                             logger.info("{} rows updated to {} status",
                                     numUpdated, (status ? "sent" : "pending"));
                             return false;
                         }
                     });
             if (!dispatchResult.get(TcpChannel.class)) {
                 values.put(SubscriptionTableSchema.DISPOSITION,
                         SubscriptionTableSchema.DISPOSITION_PENDING);
                 resolver.update(retrievalUri, values, null, null);
                 // break; // no point in trying any more
             }
        } catch (NullPointerException ex) {
            logger.warn("NullPointerException, sending to gateway failed");
        }
    }

    /**
     * Deliver the subscription request to the network service for processing.
     */
    private Map<Class<? extends INetChannel>,Boolean> 
    dispatchSubscribeRequest(DistributorService that, String mimeType, 
            String selection, INetworkService.OnSendMessageHandler handler) {
        logger.info("::dispatchSubscribeRequest");

        /** Message Building */
        AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType(AmmoMessages.MessageWrapper.MessageType.SUBSCRIBE_MESSAGE);
        //mw.setSessionUuid(sessionId);

        AmmoMessages.SubscribeMessage.Builder subscribeReq = AmmoMessages.SubscribeMessage.newBuilder();

        subscribeReq.setMimeType(mimeType);

        if (subscribeReq != null) subscribeReq.setQuery(selection);

        mw.setSubscribeMessage(subscribeReq);
       
        AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder( mw, handler);
        agmb.isGateway(true);
        return that.getNetworkServiceBinder().sendRequest(agmb.build(), null);
    }
    

    /**
     * Update the content providers as appropriate. These are typically received
     * in response to subscriptions.
     * 
     * The subscribing uri isn't sent with the subscription to the gateway
     * therefore it needs to be recovered from the subscription table.
     */
    private boolean receiveSubscriptionResponse(Context context, AmmoMessages.MessageWrapper mw) {
        if (mw == null) {
            logger.warn("no message");
            return false;
        }
        if (! mw.hasDataMessage()) {
            logger.warn("no data in message");
            return false;
        }
            
        final AmmoMessages.DataMessage resp = mw.getDataMessage();

        logger.info("::dispatchSubscribeResponse : {} : {}", resp.getMimeType(), resp.getUri());
        String mime = resp.getMimeType();
        final ContentResolver resolver = context.getContentResolver();
        String tableUriStr = null;

        try {
            final Cursor subCursor = resolver.query(
                    SubscriptionTableSchema.CONTENT_URI,
                    null,
                    SELECT_SUBSCRIPTION_TYPE, new String[]{mime},
                    null);

            if (!subCursor.moveToFirst()) {
                logger.info("no matching subscription");
                subCursor.close();
                return false;
            }
            tableUriStr = subCursor.getString(subCursor.getColumnIndex(SubscriptionTableSchema.URI));
            subCursor.close();

            final Uri tableUri = Uri.withAppendedPath(Uri.parse(tableUriStr),"_serial");
            final OutputStream outstream = resolver.openOutputStream(tableUri);

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
    

    // =============== UTILITY METHODS ======================== //
    
    /**
     * Make a specialized query on a specific content provider URI 
     * to get back that row in serialized form
     * 
     * @param uri
     * @return
     * @throws IOException
     */

    private synchronized byte[] queryUriForSerializedData(Context context, Uri tuple) 
    throws FileNotFoundException, IOException {
        final Uri serialUri = Uri.withAppendedPath(tuple, "_serial");
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        
        try {
            final BufferedInputStream bis;
            AssetFileDescriptor afd = null;
            try {
                afd = context.getContentResolver()
                    .openAssetFileDescriptor(serialUri, "r");
                if (afd == null) {
                    logger.warn("could not acquire file descriptor {}", serialUri);
                    throw new IOException("could not acquire file descriptor "+serialUri);
                }
                final ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();

                final InputStream instream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                bis = new BufferedInputStream(instream);
            } catch (IOException ex) {
                logger.info("unable to create stream {} {}",serialUri, ex.getMessage());
                bout.close();
                throw new FileNotFoundException("Unable to create stream");
            }

            for (int bytesRead = 0; (bytesRead = bis.read(buffer)) != -1;) {
                bout.write(buffer, 0, bytesRead);
            }
            bis.close();
            // String bs = bout.toString();
            // logger.info("length of serialized data: ["+bs.length()+"] \n"+bs.substring(0, 256));
            byte[] ba = bout.toByteArray();
            logger.info("length of serialized data: ["+ba.length+"]");
            bout.close();
            return ba;

        } catch (FileNotFoundException ex) {
            logger.warn("query URI for serialized data {}", ex.getStackTrace());
        } catch (IOException ex) {
            logger.error("query URI for serialized data {}", ex.getStackTrace());
        }
        if (bout != null) bout.close();
        
        return null;
    }
    

}

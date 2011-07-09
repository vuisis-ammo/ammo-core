package edu.vu.isis.ammo.core.distributor;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import edu.vu.isis.ammo.core.FLogger;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.network.NetworkService.Message;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Disposition;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTable;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SerializeType;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTable;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.util.InternetMediaType;

/**
 * The distributor service runs in the ui thread.
 * This establishes a new thread for distributing the requests.
 * 
 * It consumes messages from a priority queue and, depending on the message type,
 * <ul>
 * <li>attempts to send requests to the gateway, saving the request in the data store </li>
 * <li>updates the data store with responses from the gateway, also updates content providers </li>
 * </ul>
 * 
 */
public class DistributorThread extends
        AsyncTask<DistributorService, Integer, Void> 
{
	private static final Logger logger = LoggerFactory.getLogger(DistributorThread.class);
	
    private static final int BURP_TIME = 20 * 1000; 
    // 20 seconds expressed in milliseconds
    
    /**
     * The queues from which requests and responses are processed.
     */
    private final BlockingQueue<NetworkService.Message> queue;
    private final Context context;
    private final DistributorDataStore ds;
    
    public DistributorThread(Context context, BlockingQueue<NetworkService.Message> queue) {
    	super();
    	this.queue = queue;
    	this.ds = new DistributorDataStore(this.context);
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
        	NetworkService.Message = message;
            
			while (null != (message = this.queue.take())) {
				if (message.Type == NetworkService.Message.Type.REQUEST) {
					NetworkService.Request request = (NetworkService.Request) message;

					switch(request.type) {
					case POSTAL:
						for (DistributorService that : them) {
	                        this.processPostalChange(that, false);
	                    }
						break;
					case PUBLISH:
						for (DistributorService that : them) {
	                        this.processPublishChange(that, false);
	                    }
						break;
					case RETRIEVE:
	                    for (DistributorService that : them) {
	                        this.processPostalChange(that, false);
	                    }
	                    break;
					case SUBSCRIBE:
	                    for (DistributorService that : them) {
	                        this.processSubscriptionChange(that, false);
	                    }
	                    break;
					}
				} else if (message.Type == NetworkService.Message.Type.RESPONSE){
					NetworkService.Response response = (NetworkService.Response) message;
					AmmoMessages.MessageWrapper mw = response.msg;
        			switch (mw.getType()) {

        			    case DATA_MESSAGE:
        			    	for (DistributorService that : them) 
        			    		dispatchSubscribeResponse(mw, that);
        			        break;

        			    case PUSH_ACKNOWLEDGEMENT:
        			    	for (DistributorService that : them) 
        			    		dispatchPushResponse(mw, that);
        			        break;

        			    case PULL_RESPONSE:
        			    	for (DistributorService that : them) 
        			    		dispatchRetrievalResponse(mw, that);
        			        break;
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
     * Every time the postal provider is modified, find out what the
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
          .append(PostalTableSchema.DISPOSITION.col())
          .append(" IN (")
          .append(Disposition.SATISFIED.val()).append(",")
          .append(Disposition.EXPIRED.val()).append(")");
        POSTAL_GARBAGE = sb.toString();
        
        sb = new StringBuilder()
          .append(PostalTableSchema.DISPOSITION.col())
          .append(" IN (")
          .append(Disposition.PENDING.val()).append(")");
        POSTAL_SEND = sb.toString();

        sb = new StringBuilder()
          .append(PostalTableSchema.DISPOSITION.col())
          .append(" IN (")
          .append(Disposition.PENDING.val()).append(",")
          .append(Disposition.FAIL.val()).append(")");
        POSTAL_RESEND = sb.toString();
    }
    
    /**
     * Called when the postal table changes.
     * Places the postal requests into the input queue.
     * 
     * @param that
     * @param resend
     */
    public void processPostalChange(DistributorService that, boolean resend) {
        logger.info("::processPostalChange()");

        if (!that.isNetworkServiceBound)
            return;
        if (!that.networkServiceBinder.isConnected())
            return;

        if (collectGarbage)
        ds.deletePostal(POSTAL_GARBAGE, null);
        
        int prevPendingCount = 0;

        for (; true; resend = false) {
            String[] selectionArgs = null;

            Cursor cur = ds.queryPostal(null, 
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
                    cur.getColumnIndex(PostalTableSchema.PROVIDER.n));
                String cpType = cur.getString(
                    cur.getColumnIndex(PostalTableSchema.CP_TYPE.n));

                logger.debug("serializing: " + rowUri);
                logger.debug("rowUriType: " + cpType);

                String mimeType = InternetMediaType.getInst(cpType)
                        .setType("application").toString();
                byte[] serialized;

                int serialType = cur.getInt(
                    cur.getColumnIndex(PostalTableSchema.SERIALIZE_TYPE.n));

                switch (serialType) {
                case SerializeType.DIRECT.o:
                    int dataColumnIndex = cur.getColumnIndex(PostalTableSchema.DATA.n);

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

                case SerializeType.INDIRECT.o:
                case SerializeType.DEFERRED.o:
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

                        values.put(PostalTableSchema.DISPOSITION, Disposition.QUEUED.o);
                        ds.updatePostal(values, null, null);

                        logger.info("::dispatchPushRequest");

                        AmmoMessages.DataMessage.Builder dmb = 
                        	AmmoMessages.DataMessage.newBuilder()
                               .setUri(rowUri.toString())
                               .setMimeType(mimeType)
                               .setData(ByteString.copyFrom(serialized));
                        
                        AmmoMessages.MessageWrapper.Builder mwb = 
                        	AmmoMessages.MessageWrapper.newBuilder()
                        		.setType(AmmoMessages.MessageWrapper.MessageType.DATA_MESSAGE)
                        		.setDataMessage(dmb);
                        // mw.setSessionUuid(sessionId); // the session should be sent by the Network Service

                        // TODO or call the network service directly.
                        // this.deliver(NetworkService.Request.getInstance(0, mwb,
                        this.queue.put(NetworkService.Request.getInstance(0, mwb, 
                        	new INetworkService.OnSendHandler() {
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
                                    return false;
                                }
                            }));

                    }
                } catch (NullPointerException ex) {
                    logger.warn("NullPointerException, sending to gateway failed");
                }
            }
            cur.close();
        }
    }

    /**
     * Each time the retrieval provider is modified, find out what the
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
          .append(RetrievalTableSchema.DISPOSITION.col())
          .append(" IN (")
          .append(Disposition.SATISFIED.val()).append(",")
          .append(Disposition.EXPIRED.val()).append(")");
        RETRIEVAL_GARBAGE = sb.toString();
        
        sb = new StringBuilder()
          .append('"').append(RetrievalTableSchema.DISPOSITION).append('"')
          .append(" IN (")
          .append(Disposition.PENDING.val()).append(",")
          .append(Disposition.FAIL.val()).append(")");
        RETRIEVAL_SEND = sb.toString();

        sb = new StringBuilder()
          .append('"').append(RetrievalTableSchema.DISPOSITION).append('"')
          .append(" IN (")
          .append(Disposition.PENDING.val()).append(",")
          .append(Disposition.FAIL.val()).append(",")
          .append(Disposition.SENT.val()).append(")");
        RETRIEVAL_RESEND = sb.toString();
    }
    
    public void processRetrievalChange(DistributorService that, boolean resend) {
        logger.info("::processRetrievalChange()");

        if (!that.isNetworkServiceBound)
            return;
        if (!that.networkServiceBinder.isConnected())
            return;

        String order = RetrievalTable.PRIORITY_SORT_ORDER;

        if (collectGarbage)
        ds.deleteRetrieval(RETRIEVAL_GARBAGE, null);
        
        // Additional items may be added to the table while the current set
        // are being processed

        for (; true; resend = false) {
            String[] selectionArgs = null;
            Cursor pendingCursor = ds.queryRetrieval(null, 
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
                        .getColumnIndex(RetrievalTableSchema.PROVIDER.n));
                String mime = pendingCursor.getString(pendingCursor
                        .getColumnIndex(RetrievalTableSchema.DATA_TYPE.n));
                // String disposition =
                // pendingCursor.getString(pendingCursor.getColumnIndex(RetrievalTableSchema.DISPOSITION));
                String selection = pendingCursor.getString(pendingCursor
                        .getColumnIndex(RetrievalTableSchema.SELECTION.n));
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
                        Disposition.QUEUED.o);

                ds.updateRetrieval(values, null, null);

                AmmoMessages.PullRequest.Builder prb = 
                	AmmoMessages.PullRequest.newBuilder()
                       .setRequestUid(rowUri.toString())
                       .setMimeType(mime);
                if (selection != null) prb
                       .setQuery(selection);
                
                AmmoMessages.MessageWrapper.Builder mwb = 
                	AmmoMessages.MessageWrapper.newBuilder()
                		.setType(AmmoMessages.MessageWrapper.MessageType.PULL_REQUEST)
                		.setPullRequest(prb);
                // mw.setSessionUuid(sessionId); // the session should be sent by the Network Service
               
                this.queue.put(NetworkService.Request.getInstance(0, mwb, 
                	new INetworkService.OnSendHandler() {
                        @Override
                        public boolean ack(boolean status) {
                            // Update distributor status if
                            // message dispatch successful.
                            ContentValues values = new ContentValues();

                            values.put(RetrievalTableSchema.DISPOSITION,
                                    status ? Disposition.SENT
                                           : Disposition.FAIL);

                            int numUpdated = ds.updateRetrieval(values, null, null);

                            logger.info("{} rows updated to {} status",
                                    numUpdated, (status ? "sent" : "pending"));
                            return false;
                        }
                    }));
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
           .append(SubscribeTableSchema.DISPOSITION.col())
           .append(" IN (")
           .append(Disposition.EXPIRED.val()).append(")");      
        SUBSCRIPTION_GARBAGE = sb.toString();

        sb = new StringBuilder()
          .append(SubscribeTableSchema.DISPOSITION).append('"')
          .append(" IN (")
          .append(Disposition.PENDING.val()).append(",")
          .append(Disposition.FAIL.val()).append(")");             
        SUBSCRIPTION_SEND = sb.toString();
        
        sb = new StringBuilder()
          .append(SubscribeTableSchema.DISPOSITION).append('"')
          .append(" IN (")
          .append(Disposition.PENDING.val()).append(",")
          .append(Disposition.FAIL.val()).append(",")
          .append(Disposition.SENT.val()).append(")");
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
        ds.deleteSubscribe(SUBSCRIPTION_GARBAGE, null);

        String order = SubscribeTable.PRIORITY_SORT_ORDER;

        // Additional items may be added to the table while the current set
        // are being processed

        for (; true; resend = false) {
            String[] selectionArgs = null;

            Cursor pendingCursor = ds.querySubscribe(null, 
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
                        .getColumnIndex(SubscribeTableSchema.DATA_TYPE.n));
                // String disposition =
                // pendingCursor.getString(pendingCursor.getColumnIndex(SubscribeTableSchema.DISPOSITION));
                String selection = pendingCursor.getString(pendingCursor
                        .getColumnIndex(SubscribeTableSchema.SELECTION.n));
                int expiration = pendingCursor.getInt(pendingCursor
                        .getColumnIndex(SubscribeTableSchema.EXPIRATION.n));

                // skip subscriptions with expiration of 0 -- they have been
                // unsubscribed
                if (expiration == 0)
                    continue;

                // long createdDate =
                // pendingCursor.getLong(pendingCursor.getColumnIndex(SubscribeTableSchema.CREATED_DATE));

                FLogger.request.trace("subscribe type[{}] select[{}]",
                        mime, selection);

                final Uri subUri = SubscribeTableSchema
                        .getUri(pendingCursor);

                ContentValues values = new ContentValues();
                values.put(SubscribeTableSchema.DISPOSITION,
                		Disposition.QUEUED.o);

                @SuppressWarnings("unused")
                int numUpdated = ds.update(subUri, values, null, null);
                

                AmmoMessages.SubscribeMessage.Builder smb = 
                	AmmoMessages.SubscribeMessage.newBuilder()
                       .setMimeType(mime);
                if (selection != null) smb
                       .setQuery(selection);
                
                AmmoMessages.MessageWrapper.Builder mwb = 
                	AmmoMessages.MessageWrapper.newBuilder()
                		.setType(AmmoMessages.MessageWrapper.MessageType.SUBSCRIBE_MESSAGE)
                		.setSubscribeMessage(smb);
                // mw.setSessionUuid(sessionId); // the session should be sent by the Network Service
               

                this.queue.put(NetworkService.Request.getInstance(0, mwb, 
                	new INetworkService.OnSendHandler() {
                        @Override
                        public boolean ack(boolean status) {
                                // Update distributor status if
                                // message dispatch successful.
                                ContentValues values = new ContentValues();
                                values.put( SubscribeTableSchema.DISPOSITION.n,
                                                (status) ? Disposition.SENT.o()
                                                        : Disposition.FAIL.o());

                                int numUpdated = cr.update(subUri, values, null, null);
                                FLogger.request.trace("subscribe rows[{}] status[{}]",
                                        numUpdated, (status ? "sent" : "pending"));
                                return true;
                            }
                        }));
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
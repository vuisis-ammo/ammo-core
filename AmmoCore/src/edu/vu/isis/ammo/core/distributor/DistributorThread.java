package edu.vu.isis.ammo.core.distributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import net.jcip.annotations.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;
import edu.vu.isis.ammo.util.InternetMediaType;


/**
 * The distributor service runs in the ui thread.
 * This establishes a new thread for distributing the requests.
 * 
 */
@ThreadSafe
public class DistributorThread extends
        AsyncTask<DistributorService, Integer, Void> 
{
    // ===========================================================
    // Constants
    // ===========================================================
    private static final Logger logger = LoggerFactory.getLogger(DistributorThread.class);

    // 20 seconds expressed in milliseconds
    private static final int BURP_TIME = 20 * 1000;
  
    public DistributorThread() {
        super();
        this.requestQueue = new LinkedBlockingQueue<AmmoRequest>(20);
        this.responseQueue = new PriorityBlockingQueue<AmmoGatewayMessage>(20, 
                        new AmmoGatewayMessage.PriorityOrder());
    }
    
    private AtomicBoolean subscriptionDelta = new AtomicBoolean(true);

    public void subscriptionChange() {
        this.signal(this.subscriptionDelta);
    }

    private AtomicBoolean retrievalDelta = new AtomicBoolean(true);

    public void retrievalChange() {
        this.signal(this.retrievalDelta);
    }

    private AtomicBoolean postalDelta = new AtomicBoolean(true);

    public void postalChange() {
        this.signal(this.postalDelta);
    }
    
    /**
     * for handling client application requests
     */
    private AtomicBoolean requestDelta = new AtomicBoolean(true);
    private final BlockingQueue<AmmoRequest> requestQueue;
  
    public String distributeRequest(AmmoRequest request)
    {
        try {
            this.requestQueue.put(request);
            this.signal(this.requestDelta);
            return "1234567890";  // FIXME what is a good string to return?
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
        synchronized(this) { this.notifyAll(); }
    }

    private boolean isReady() {
        if (this.subscriptionDelta.get()) return true;
        if (this.retrievalDelta.get()) return true;
        if (this.postalDelta.get()) return true;
        
        if (this.responseDelta.get()) return true;
        if (this.requestDelta.get()) return true;
        return false;
    }

    private AtomicBoolean resend = new AtomicBoolean(true);
    public void resend() {
        this.resend.set(true);
        this.subscriptionDelta.set(true);
        this.retrievalDelta.set(true);
        this.postalDelta.set(true);
        
        this.responseDelta.set(true);
        this.requestDelta.set(true);
        synchronized(this) { this.notifyAll(); }
    }

    @Override
    protected Void doInBackground(DistributorService... them) {
        logger.info("::post to network service");
        if (this.resend.getAndSet(false)) {
            for (DistributorService that : them) {
                this.processSubscriptionChange(that, true);
                this.processRetrievalChange(that, true);
                this.processPostalChange(that, true);
            }
        }
        // condition wait is there something to process?
        try {
            while (true) {
                synchronized (this) {
                    while (!this.isReady())
                    {
                        logger.info("!this.isReady()");
                        // this is IMPORTANT don't remove it.
                        this.wait(BURP_TIME);
                    }
                }
                boolean resend = this.resend.getAndSet(false);
                    
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

    private boolean collectGarbage = true;

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
          .append(")");
        POSTAL_RESEND = sb.toString();
    }
    public void processPostalChange(DistributorService that, boolean resend) {
        logger.info("::processPostalChange()");

        if (!that.isNetworkServiceBound())
            return;
        if (!that.getNetworkServiceBinder().isConnected())
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
                        serialized = queryUriForSerializedData(that, rowUri);
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
                    if (!that.getNetworkServiceBinder().isConnected()) {
                        logger.info("no network connection");
                    } else {
                        final Uri postalUri = PostalTableSchema.getUri(cur);
                        ContentValues values = new ContentValues();

                        values.put(PostalTableSchema.DISPOSITION,
                                PostalTableSchema.DISPOSITION_QUEUED);
                        @SuppressWarnings("unused")
                        int numUpdated = cr.update(postalUri, values, null, null);

                        boolean dispatchSuccessful = that.getNetworkServiceBinder()
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
     * getNetworkServiceBinder() server.
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

        if (!that.isNetworkServiceBound())
            return;
        if (!that.getNetworkServiceBinder().isConnected())
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

                if (!that.getNetworkServiceBinder().isConnected()) {
                    continue;
                }
                final Uri retrieveUri = RetrievalTableSchema.getUri(pendingCursor);
                ContentValues values = new ContentValues();
                values.put(RetrievalTableSchema.DISPOSITION,
                        RetrievalTableSchema.DISPOSITION_QUEUED);

                @SuppressWarnings("unused")
                int numUpdated = cr.update(retrieveUri, values, null, null);

                boolean sent = that.getNetworkServiceBinder()
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
     * getNetworkServiceBinder() server.
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

        if (!that.isNetworkServiceBound())
            return;
        if (!that.getNetworkServiceBinder().isConnected())
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

                boolean sent = that.getNetworkServiceBinder()
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

    public void processPublicationChange(DistributorService that, boolean resend) {
        logger.error("::processPublicationChange : {} : not implemented", resend);
    }
    
    /**
     * Make a specialized query on a specific content provider URI 
     * to get back that row in serialized form
     * 
     * @param uri
     * @return
     * @throws IOException
     */

    private synchronized byte[] queryUriForSerializedData(Context context, String uri) throws FileNotFoundException, IOException {
        Uri rowUri = Uri.parse(uri);
        Uri serialUri = Uri.withAppendedPath(rowUri, "_serial");

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        BufferedInputStream bis = null;
        InputStream instream = null;

        try {
            try {
                // instream = this.getContentResolver().openInputStream(serialUri);
                AssetFileDescriptor afd = context.getContentResolver()
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
            logger.warn("query URI for serialized data {}", ex.getStackTrace());
        } catch (IOException ex) {
            logger.error("query URI for serialized data {}", ex.getStackTrace());
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
     *  Processes and delivers messages received from the gateway.
     *  - Verify the check sum for the payload is correct
     *  - Parse the payload into a message
     *  - Receive the message
     *
     * @param instream
     * @return was the message clean (true) or garbled (false).
     */
    private boolean processRequest(Context context, AmmoRequest agm) {
        logger.info("::processRequest");
        // FIXME bunch of code here
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
        logger.info("::processResponse");

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
            receiveSubscribeResponse(context, mw);
            break;

        case AUTHENTICATION_RESULT:
            boolean result = receiveAuthenticationResponse(context, mw);
            logger.error( "authentication result={}", result );
            break;

        case PUSH_ACKNOWLEDGEMENT:
            receivePushResponse(context, mw);
            break;

        case PULL_RESPONSE:
            receivePullResponse(context, mw);
            break;
            
        case HEARTBEAT:
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
    
    /**
     * Get response to PushRequest from the gateway.
     * (PushResponse := PushAcknowledgement)
     *
     * @param mw
     * @return
     */
    private boolean receivePushResponse(Context context, AmmoMessages.MessageWrapper mw) {
        logger.info("::receivePushResponse");

        if (mw == null) return false;
        if (! mw.hasPushAcknowledgement()) return false;
        // PushAcknowledgement pushResp = mw.getPushAcknowledgement();
        return true;
    }


    /**
     * Get response to RetrievalRequest, PullResponse, from the gateway.
     *
     * @param mw
     * @return
     */
    private boolean receivePullResponse(Context context, AmmoMessages.MessageWrapper mw) {
        logger.info("::receivePullResponse");

        if (mw == null) return false;
        if (! mw.hasPullResponse()) return false;
        final AmmoMessages.PullResponse resp = mw.getPullResponse();

        String uriStr = resp.getRequestUid(); 
        // FIXME --- why do we have uri in data message and retrieval response?
        Uri uri = Uri.parse(uriStr);
        ContentResolver cr = context.getContentResolver();

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


    private boolean receiveSubscribeResponse(Context context, AmmoMessages.MessageWrapper mw) {
        if (mw == null) return false;
        if (! mw.hasDataMessage()) return false;
        final AmmoMessages.DataMessage resp = mw.getDataMessage();

        logger.info("::dispatchSubscribeResponse : {} : {}", resp.getMimeType(), resp.getUri());
        String mime = resp.getMimeType();
        ContentResolver cr = context.getContentResolver();
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

package edu.vu.isis.ammo.core.distributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

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
import edu.vu.isis.ammo.core.network.INetworkService;
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
    private static final Logger logger = LoggerFactory.getLogger(DistributorService.class);

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

    private boolean resend;
    public synchronized void resend() {
        this.resend = true;
        subscriptionDelta = true;
        retrievalDelta = true;
        postalDelta = true;
        this.notifyAll();
    }

    @Override
    protected Void doInBackground(DistributorService... them) {
        logger.info("::post to network service");
        for (DistributorService that : them) {
            this.processSubscriptionChange(that, true);
            this.processRetrievalChange(that, true);
            this.processPostalChange(that, true);
        }
        this.resend = false; 
        // condition wait is there something to process?
        try {
            while (true) {
                boolean subscriptionFlag = false;
                boolean retrievalFlag = false;
                boolean postalFlag = false;
                boolean resend = false;
                
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

                    resend = this.resend;
                    this.resend = false;
                }
                if (subscriptionFlag) {
                    for (DistributorService that : them) {
                        this.processSubscriptionChange(that, resend);
                    }
                }
                if (retrievalFlag) {
                    for (DistributorService that : them) {
                        this.processRetrievalChange(that, resend);
                    }
                }
                if (postalFlag) {
                    for (DistributorService that : them) {
                        this.processPostalChange(that, resend);
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
    

}

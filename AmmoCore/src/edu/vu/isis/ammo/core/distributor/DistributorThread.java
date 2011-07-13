package edu.vu.isis.ammo.core.distributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.zip.CRC32;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.core.FLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Disposition;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PublishTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTable;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SerializeType;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTable;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.network.NetworkService.Response;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
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
public class DistributorThread
    extends AsyncTask<DistributorService, Integer, Void>
    implements INetworkService.DeliveryHandler {
    private static final Logger logger = LoggerFactory.getLogger(DistributorThread.class);

    @SuppressWarnings("unused")
    private static final int BURP_TIME = 20 * 1000;
    // 20 seconds expressed in milliseconds

    /**
     * The queues from which requests and responses are processed.
     */
    private final BlockingQueue<NetworkService.DistributorMessage> queue;
    private final Context context;
    private final DistributorDataStore ds;
    private final INetworkService gateway;

    public DistributorThread(Context context, BlockingQueue<NetworkService.DistributorMessage> queue, INetworkService gateway) {
        super();
        this.context = context;
        this.queue = queue;
        this.ds = new DistributorDataStore(this.context);
        this.gateway = gateway;
    }

    @Override
    protected Void doInBackground(DistributorService... them) {
        logger.info("::post to network service");

        for (DistributorService that : them) {
            this.processSubscribeTable(that, true);
            this.processRetrievalTable(that, true);
            this.processPostalTable(that, true);
        }
        try {
            NetworkService.DistributorMessage distributorMessage;

            while (null != (distributorMessage = this.queue.take())) {

                switch (distributorMessage.type) {
                case REQUEST:
                    logger.trace("process out-bound request ");
                    NetworkService.RawRequest request = (NetworkService.RawRequest) distributorMessage;

                    switch(request.payload.action) {
                    case POSTAL:
                        for (DistributorService that : them) {
                            this.processPostalRequest(that, request);
                        }
                        break;
                    case PUBLISH:
                        for (DistributorService that : them) {
                            this.processPublishRequest(that, request);
                        }
                        break;
                    case RETRIEVAL:
                        for (DistributorService that : them) {
                            this.processRetrievalRequest(that, request);
                        }
                        break;
                    case SUBSCRIBE:
                        for (DistributorService that : them) {
                            this.processSubscribeRequest(that, request);
                        }
                        break;
                    }
                    break;
                case RESPONSE:
                    logger.trace("process in-bound response ");
                    NetworkService.Response response = (NetworkService.Response) distributorMessage;
                    AmmoMessages.MessageWrapper mw = response.msg;
                    switch (mw.getType()) {

                    case DATA_MESSAGE:
                        for (DistributorService that : them)
                            dispatchSubscribeResponse(mw, that);
                        break;

                    case PUSH_ACKNOWLEDGEMENT:
                        for (DistributorService that : them)
                            dispatchPostalResponse(mw, that);
                        break;

                    case PULL_RESPONSE:
                        for (DistributorService that : them)
                            dispatchRetrievalResponse(mw, that);
                        break;
                    }
                    break;
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


// ================================================
// Handle work originating from Client Application
// ================================================
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
    public void processPostalRequest(DistributorService that, NetworkService.RawRequest request) {
        logger.info("::processPostalRequest()");
    }

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
     * Called when the postal table.
     * Places the postal requests into the input queue.
     *
     * @param that
     * @param resend
     */
    public void processPostalTable(DistributorService that, boolean resend) {
        logger.info("::processPostalTable()");

        if (!that.isNetworkServiceBound)
            return;
        if (!that.networkServiceBinder.isConnected())
            return;

        if (collectGarbage)
            ds.deletePostal(POSTAL_GARBAGE, null);

        int prevPendingCount = 0;
        final ContentResolver cr = that.getContentResolver();

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
                    moreItems = cur.moveToNext()) {
                String rowUri = cur.getString(
                                    cur.getColumnIndex(PostalTableSchema.PROVIDER.n));
                String cpType = cur.getString(
                                    cur.getColumnIndex(PostalTableSchema.CP_TYPE.n));

                logger.debug("serializing: " + rowUri);
                logger.debug("rowUriType: " + cpType);

                String mimeType = InternetMediaType.getInst(cpType)
                                  .setType("application").toString();
                byte[] serialized;

                int serialType = cur.getInt(cur.getColumnIndex(PostalTableSchema.SERIALIZE_TYPE.n));

                switch (SerializeType.byOrdinal(serialType)) {
                case DIRECT:
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

                case INDIRECT:
                case DEFERRED:
                default:
                    try {
                        serialized = queryUriForSerializedData(that.getBaseContext(), rowUri);
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
                        ContentValues values = new ContentValues();

                        values.put(PostalTableSchema.DISPOSITION.n, Disposition.QUEUED.o);
                        ds.updatePostal(values);

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

                        final Uri postalUri = Uri.parse("content://the/thing/123");

                        this.gateway.sendRequest(NetworkService.Request.getInstance(0,
                                                 NetworkService.Request.Action.POSTAL, mwb,
                        new INetworkService.OnSendHandler() {
                            @Override
                            public boolean ack(boolean status) {
                                ContentValues values = new ContentValues();

                                values.put(PostalTableSchema.DISPOSITION.n,
                                           (status) ? Disposition.SENT.o
                                           : Disposition.FAIL.o);

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
    public void processPublishRequest(DistributorService that, NetworkService.RawRequest request) {
        logger.info("::processPublishRequest()");
    }

    private static final String PUBLISH_GARBAGE;
    private static final String PUBLISH_RESEND;
    private static final String PUBLISH_SEND;

    static {
        StringBuilder sb = new StringBuilder()
        .append(PublishTableSchema.DISPOSITION.col())
        .append(" IN (")
        .append(Disposition.SATISFIED.val()).append(",")
        .append(Disposition.EXPIRED.val()).append(")");
        PUBLISH_GARBAGE = sb.toString();

        sb = new StringBuilder()
        .append(PublishTableSchema.DISPOSITION.col())
        .append(" IN (")
        .append(Disposition.PENDING.val()).append(")");
        PUBLISH_SEND = sb.toString();

        sb = new StringBuilder()
        .append(PublishTableSchema.DISPOSITION.col())
        .append(" IN (")
        .append(Disposition.PENDING.val()).append(",")
        .append(Disposition.FAIL.val()).append(")");
        PUBLISH_RESEND = sb.toString();
    }

    /**
     * Called when the publish table changes.
     * Places the publish requests into the input queue.
     *
     * @param that
     * @param resend
     */
    public void processPublishTable(DistributorService that, boolean resend) {
        logger.info("::processPublishTable()");

        if (!that.isNetworkServiceBound)
            return;
        if (!that.networkServiceBinder.isConnected())
            return;

        if (collectGarbage)
            ds.deletePublish(PUBLISH_GARBAGE, null);

        int prevPendingCount = 0;
        final Context context = that.getApplicationContext();
        final ContentResolver cr = that.getContentResolver();

        for (; true; resend = false) {
            String[] selectionArgs = null;

            Cursor cur = ds.queryPublish(null,
                                         (resend ? PUBLISH_RESEND : PUBLISH_SEND),
                                         selectionArgs, PublishTableSchema._ID + " ASC");

            int curCount = cur.getCount();

            if (curCount == prevPendingCount) {
                cur.close();
                break; // no new items to send
            }

            prevPendingCount = curCount;
            // Iterate over each row serializing its data and sending it.
            for (boolean moreItems = cur.moveToFirst(); moreItems;
                    moreItems = cur.moveToNext()) {
                String rowUri = cur.getString(cur.getColumnIndex(PublishTableSchema.PROVIDER.n));
                String cpType = cur.getString(cur.getColumnIndex(PublishTableSchema.DATA_TYPE.n));

                logger.debug("serializing: " + rowUri);
                logger.debug("rowUriType: " + cpType);

                String mimeType = InternetMediaType.getInst(cpType)
                                  .setType("application").toString();
                byte[] serialized;

                try {
                    serialized = queryUriForSerializedData(context, rowUri);
                } catch (IOException e1) {
                    logger.error("invalid row for serialization");
                    continue;
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
                        final Uri publishUri = Uri.parse("content://the/thing/123"); // PublishTableSchema.getProvider(cur);
                        ContentValues values = new ContentValues();

                        values.put(PublishTableSchema.DISPOSITION.n, Disposition.QUEUED.o);
                        ds.updatePublish(values);

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

                        this.gateway.sendRequest(NetworkService.Request.getInstance(0,
                                                 NetworkService.Request.Action.PUBLISH, mwb,
                        new INetworkService.OnSendHandler() {
                            @Override
                            public boolean ack(boolean status) {
                                // Update distributor status
                                // if message dispatch
                                // successful.
                                ContentValues values = new ContentValues();

                                values.put(PublishTableSchema.DISPOSITION.n,
                                           (status) ? Disposition.SENT.o
                                           : Disposition.FAIL.o);
                                int numUpdated = cr.update(
                                                     publishUri, values,
                                                     null, null);

                                logger.info("Publish: {} rows updated to {}",
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
    public void processRetrievalRequest(DistributorService that, NetworkService.RawRequest request) {
        logger.info("::processRetrievalRequest()");
    }

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

    public void processRetrievalTable(DistributorService that, boolean resend) {
        logger.info("::processRetrievalTable()");

        if (!that.isNetworkServiceBound)
            return;
        if (!that.networkServiceBinder.isConnected())
            return;

        String order = RetrievalTable.PRIORITY_SORT_ORDER;
        @SuppressWarnings("unused")
        final Context context = that.getApplicationContext();
        final ContentResolver cr = that.getContentResolver();

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
                    areMoreItems = pendingCursor.moveToNext()) {
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
                try {
                    final Uri retrieveUri = Uri.parse("content://the/thing/123"); //RetrievalTableSchema.getUri(pendingCursor);
                    ContentValues values = new ContentValues();
                    values.put(RetrievalTableSchema.DISPOSITION.n, Disposition.QUEUED.o);

                    ds.updateRetrieval(values);

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

                    this.gateway.sendRequest(NetworkService.Request.getInstance(0,
                                             NetworkService.Request.Action.RETRIEVAL, mwb,
                    new INetworkService.OnSendHandler() {
                        @Override
                        public boolean ack(boolean status) {
                            // Update distributor status if
                            // message dispatch successful.
                            ContentValues values = new ContentValues();

                            values.put(RetrievalTableSchema.DISPOSITION.n,
                                       status ? Disposition.SENT.o
                                       : Disposition.FAIL.o);

                            int numUpdated = cr.update(retrieveUri, values, null, null);

                            logger.info("{} rows updated to {} status",
                                        numUpdated, (status ? "sent" : "pending"));
                            return false;
                        }
                    }));
                } catch (NullPointerException ex) {
                    logger.warn("NullPointerException, sending to gateway failed");
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
    public void processSubscribeRequest(DistributorService that, NetworkService.RawRequest request) {
        logger.info("::processSubscribeRequest()");
    }

    private static final String SUBSCRIBE_GARBAGE;
    private static final String SUBSCRIBE_RESEND;
    private static final String SUBSCRIBE_SEND;

    static {
        StringBuilder sb = new StringBuilder();

        sb = new StringBuilder()
        .append(SubscribeTableSchema.DISPOSITION.col())
        .append(" IN (")
        .append(Disposition.EXPIRED.val()).append(")");
        SUBSCRIBE_GARBAGE = sb.toString();

        sb = new StringBuilder()
        .append(SubscribeTableSchema.DISPOSITION).append('"')
        .append(" IN (")
        .append(Disposition.PENDING.val()).append(",")
        .append(Disposition.FAIL.val()).append(")");
        SUBSCRIBE_SEND = sb.toString();

        sb = new StringBuilder()
        .append(SubscribeTableSchema.DISPOSITION).append('"')
        .append(" IN (")
        .append(Disposition.PENDING.val()).append(",")
        .append(Disposition.FAIL.val()).append(",")
        .append(Disposition.SENT.val()).append(")");
        SUBSCRIBE_RESEND = sb.toString();
    }
    public void processSubscribeTable(DistributorService that, boolean resend) {
        logger.info("::processSubscribeTable()");

        if (!that.isNetworkServiceBound)
            return;
        if (!that.networkServiceBinder.isConnected())
            return;

        final ContentResolver cr = that.getContentResolver();
        if (collectGarbage)
            ds.deleteSubscribe(SUBSCRIBE_GARBAGE, null);

        String order = SubscribeTable.PRIORITY_SORT_ORDER;

        // Additional items may be added to the table while the current set
        // are being processed

        for (; true; resend = false) {
            String[] selectionArgs = null;

            Cursor pendingCursor = ds.querySubscribe(null,
                                   (resend ? SUBSCRIBE_RESEND : SUBSCRIBE_SEND),
                                   selectionArgs, order);

            if (pendingCursor.getCount() < 1) {
                pendingCursor.close();
                break;
            }

            for (boolean areMoreItems = pendingCursor.moveToFirst(); areMoreItems;
                    areMoreItems = pendingCursor.moveToNext()) {
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

                try {
                    final Uri subUri = Uri.parse("content://the/thing/123"); //SubscribeTableSchema.getUri(pendingCursor);

                    ContentValues values = new ContentValues();
                    values.put(SubscribeTableSchema.DISPOSITION.n, Disposition.QUEUED.o);

                    @SuppressWarnings("unused")
                    long numUpdated = ds.updateSubscribe(values);


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


                    this.gateway.sendRequest(NetworkService.Request.getInstance(0,
                                             NetworkService.Request.Action.SUBSCRIBE, mwb,
                    new INetworkService.OnSendHandler() {
                        @Override
                        public boolean ack(boolean status) {
                            // Update distributor status if
                            // message dispatch successful.
                            ContentValues values = new ContentValues();
                            values.put( SubscribeTableSchema.DISPOSITION.n,
                                        (status) ? Disposition.SENT.o
                                        : Disposition.FAIL.o);

                            int numUpdated = cr.update(subUri, values, null, null);
                            FLogger.request.trace("subscribe rows[{}] status[{}]",
                                                  numUpdated, (status ? "sent" : "pending"));
                            return true;
                        }
                    }));
                } catch (NullPointerException ex) {
                    logger.warn("NullPointerException, sending to gateway failed");
                }
            }
            pendingCursor.close();
        }
    }


    /**
     * Put the message in the queue.
     *
     * @param instream
     * @return was the message clean (true) or garbled (false).
     *    a null message is considered garbled indicating it won't be processed.
     */
    @Override
    public boolean deliver(byte[] message, long checksum) {
        logger.info("::deliverGatewayResponse");

        CRC32 crc32 = new CRC32();
        crc32.update(message);
        if (crc32.getValue() != checksum) {
            String msg = "you have received a bad message, the checksums did not match)"+
                         Long.toHexString(crc32.getValue()) +":"+ Long.toHexString(checksum);
            // Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            logger.warn(msg);
            return false;
        }

        AmmoMessages.MessageWrapper mw = null;
        try {
            mw = AmmoMessages.MessageWrapper.parseFrom(message);
        } catch (InvalidProtocolBufferException ex) {
            ex.printStackTrace();
        }
        if (mw == null) return false;

        try {
            this.queue.put(Response.getInstance(0, mw));
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        return true;
    }

// ================================================
// Handle work originating from NetworkService
// ================================================

    /**
     * Typically just an acknowledgment.
     * Get response to PushRequest from the gateway.
     * (PushResponse := PushAcknowledgement)
     *
     * @param mw
     * @return
     */
    @SuppressWarnings("unused")
    final private static String POSTAL_SELECT = new StringBuilder()
    .append('"').append(PostalTableSchema.PROVIDER).append("\"=?")
    .toString();

    private boolean dispatchPostalResponse(AmmoMessages.MessageWrapper mw, DistributorService that) {
        logger.info("::receivePushResponse");

        if (mw == null) return false;
        if (! mw.hasPushAcknowledgement()) return false;
        @SuppressWarnings("unused")
        PushAcknowledgement resp = mw.getPushAcknowledgement();

        return true;
    }

    /**
     * Get response to RetrievalRequest, PullResponse, from the gateway.
     *
     * @param mw
     * @return
     */
    final private static String RETRIEVE_SELECT = new StringBuilder()
    .append('"').append(RetrievalTableSchema.PROVIDER).append("\"=?")
    .toString();

    private boolean dispatchRetrievalResponse(AmmoMessages.MessageWrapper mw, DistributorService that) {
        logger.info("::receivePullResponse");

        if (mw == null) return false;
        if (! mw.hasPullResponse()) return false;
        final AmmoMessages.PullResponse resp = mw.getPullResponse();

        String uriStr = resp.getRequestUid(); // resp.getUri(); --- why do we have uri in data message and retrieval response?
        final Uri uri = Uri.parse(uriStr);
        final ContentResolver cr = that.getContentResolver();

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
            Cursor cursor = cr.query(serialUri, null, RETRIEVE_SELECT, new String[] {uri.toString()}, null);
            if (!cursor.moveToFirst()) {
                logger.info("no matching retrieval: {} {}", RETRIEVE_SELECT, uri);
                cursor.close();
                return false;
            }
            final Uri retrieveUri = serialUri; // RetrievalTableSchema.getUri(cursor);
            cursor.close ();
            ContentValues values = new ContentValues();
            values.put(RetrievalTableSchema.DISPOSITION.n, Disposition.SATISFIED.o);

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
    final private static String SUBSCRIBE_SELECT = new StringBuilder()
    .append('"').append(SubscribeTableSchema.DATA_TYPE).append("\"=?")
    .toString();

    private boolean dispatchSubscribeResponse(AmmoMessages.MessageWrapper mw, DistributorService that) {
        logger.info("::receiveSubscribeResponse");

        if (mw == null) return false;
        if (! mw.hasDataMessage()) return false;
        final AmmoMessages.DataMessage resp = mw.getDataMessage();

        String mime = resp.getMimeType();
        final ContentResolver cr = that.getContentResolver();
        String tableUriStr = null;

        try {
            Cursor subCursor = ds.querySubscribe(
                                   null,
                                   SUBSCRIBE_SELECT,
                                   new String[] {mime}, null);

            if (!subCursor.moveToFirst()) {
                logger.info("no matching subscription");
                subCursor.close();
                return false;
            }
            tableUriStr = subCursor.getString(subCursor.getColumnIndex(SubscribeTableSchema.PROVIDER.n));
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



    /**
     * Make a specialized query on a specific content provider URI
     * to get back that row in serialized form
     *
     * @param uri
     * @return
     * @throws IOException
     */
    static public byte[] queryUriForSerializedData(Context context, String uri) throws FileNotFoundException, IOException {
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


    /**
     * Get the contents of a file as a byte array.
     *
     * @param file
     * @return
     * @throws IOException
     */
    @SuppressWarnings("unused")
    static private byte[] getBytesFromFile(File file) throws IOException {
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
}

package edu.vu.isis.ammo.core.distributor;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.InvalidProtocolBufferException;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.api.type.Notice;
import edu.vu.isis.ammo.api.type.Notice.Via;
import edu.vu.isis.ammo.core.AmmoMimeTypes;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.AmmoService.ChannelChange;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.store.Capability;
import edu.vu.isis.ammo.core.distributor.store.Presence;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetChannel;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.AcknowledgementThresholds;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
import edu.vu.isis.ammo.core.pb.AmmoMessages.MessageWrapper.MessageType;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement.PushStatus;
import edu.vu.isis.ammo.util.AsyncQueryHelper;
import edu.vu.isis.ammo.util.FullTopic;

public class ResponseDistributor implements Runnable {
    private static final Logger resLogger = LoggerFactory.getLogger("test.queue.response");
    private static final Logger logger = LoggerFactory.getLogger("dist.resp");
    private static final Logger tlogger = LoggerFactory.getLogger("test.queue.insert");

    private AtomicInteger masterSequence;

    private final Context context;
    private final AmmoService parent;

    private final AtomicInteger total_recv;

    /**
     * The backing store for the distributor
     */
    final private DistributorDataStore store;

    public class Item {
        final public int priority;
        final public int sequence;

        final public Context context;
        final public String channelName;
        final public Uri provider;
        final public Encoding encoding;
        final public byte[] data;

        public Item(final int priority, final Context context, final String channelName,
                final Uri provider, final Encoding encoding, final byte[] data)
        {
            this.priority = priority;
            this.sequence = masterSequence.getAndIncrement();

            this.context = context;
            this.channelName = channelName;
            this.provider = provider;
            this.encoding = encoding;
            this.data = data;
        }
    }

    public ResponseDistributor(final Context context, AmmoService parent,
            DistributorDataStore store, AtomicInteger total) {
        this.context = context;
        this.parent = parent;
        this.store = store;
        this.total_recv = total;

        this.masterSequence = new AtomicInteger(0);
    }

    /**
     * A functor to be used in cases such as PriorityQueue. This gives a partial
     * ordering, rather than total ordering of the natural order. This overrides
     * the default comparison of the AmmoGatewayMessage. The ordering is as
     * follows: priority : larger sequence : smaller
     */
    public static class PriorityOrder implements Comparator<Item> {
        @Override
        public int compare(Item o1, Item o2) {
            logger.debug("compare msgs: priority=[{}:{}] sequence=[{}:{}]",
                    new Object[] {
                            o1.priority, o2.priority,
                            o1.sequence, o2.sequence
                    });
            if (o1.priority > o2.priority)
                return -1;
            if (o1.priority < o2.priority)
                return 1;

            // if priority is same then process in the insertion order
            if (o1.sequence > o2.sequence)
                return 1;
            if (o1.sequence < o2.sequence)
                return -1;
            return 0;
        }
    }

    /**
     * proxy for the RequestSerializer deserializeToProvider method.
     * 
     * @param context
     * @param provider
     * @param encoding
     * @param data
     * @return
     */
    public boolean toProvider(int priority, Context context, final String channelName,
            Uri provider, Encoding encoding, byte[] data)
    {
        final Message msg = Message.obtain();
        msg.obj = new Item(priority, context, channelName, provider, encoding, data);
        this.upsertHandler.dispatchMessage(msg);
        return true;
    }

    /**
     * This is the method which places responses in to the message queue.
     * 
     * @param agm
     * @return
     */
    public boolean distributeResponse(AmmoGatewayMessage agm) {
        PLogger.QUEUE_RESP_ENTER.trace("\"action\":\"offer\" \"response\":\"{}\"", agm);
        final Message msg = Message.obtain();
        msg.obj = agm;
        this.responseHandler.dispatchMessage(msg);
        return true;
    }

    public void onChannelChange(final Context context, final String channelName,
            final ChannelChange change) {

    }

    /**
     * Get response to RetrievalRequest, PullResponse, from the gateway.
     * 
     * @param mw
     * @return
     */
    private boolean receiveRetrievalResponse(Context context, AmmoMessages.MessageWrapper mw,
            NetChannel channel, int priority) {
        if (mw == null)
            return false;
        if (!mw.hasPullResponse())
            return false;
        logger.trace("receive response RETRIEVAL");

        final AmmoMessages.PullResponse resp = mw.getPullResponse();

        // find the provider to use
        final String uuidString = resp.getRequestUid();
        final String topic = resp.getMimeType();
        final Cursor cursor = this.store
                .queryRetrievalByKey(new String[] {
                        RetrievalTableSchema.PROVIDER.n
                }, uuidString, topic, null);
        if (cursor.getCount() < 1) {
            logger.error("received a message for which there is no retrieval {} {}", topic,
                    uuidString);
            cursor.close();
            return false;
        }
        cursor.moveToFirst();
        final String uriString = cursor.getString(0); // only asked for one so
        // it better be it.
        cursor.close();
        final Uri provider = Uri.parse(uriString);

        // update the actual provider

        final Encoding encoding = Encoding.getInstanceByName(resp.getEncoding());
        final boolean queued = this.toProvider(priority, context, channel.name,
                provider, encoding, resp.getData().toByteArray());
        logger.debug("tuple upserted {}", queued);

        return true;
    }

    /**
     * Update the content providers as appropriate. These are typically received
     * in response to subscriptions. In other words these replies are postal
     * messages which have been redirected. The subscribing uri isn't sent with
     * the subscription to the gateway therefore it needs to be recovered from
     * the subscription table.
     */
    private boolean receiveSubscribeResponse(Context context, AmmoMessages.MessageWrapper mw,
            NetChannel channel, int priority) {
        if (mw == null) {
            logger.warn("no message");
            return false;
        }
        if (!mw.hasDataMessage() && !mw.hasTerseMessage()) {
            logger.warn("no data in message");
            return false;
        }

        String mime = null;
        String encode = null;
        String originUser = null;
        String originDevice = null;
        String originUid = null;
        com.google.protobuf.ByteString data = null;
        AmmoMessages.AcknowledgementThresholds at = null;

        final String selfDevice = this.parent.getDeviceId();
        if (mw.hasDataMessage()) {
            final AmmoMessages.DataMessage resp = mw.getDataMessage();
            mime = resp.getMimeType();
            data = resp.getData();
            encode = resp.getEncoding();
            at = resp.getThresholds();
            originUser = resp.getUserId();
            originDevice = resp.getOriginDevice();
            originUid = resp.getUri(); // SKN: URI is really UID

            if (originDevice.equals(selfDevice)) {
                logger.error("received own device message [{}:{}]",
                        originDevice, selfDevice);
                // FIXME return false;
                // Apparently the unique device identifies are not unique.
            }
        } else {
            final AmmoMessages.TerseMessage resp = mw.getTerseMessage();
            mime = AmmoMimeTypes.mimeTypes.get(resp.getMimeType());
            data = resp.getData();
            originUser = resp.getUserId(); // SERIAL does not have this -- do we
                                           // have a use for it?
            encode = "TERSE";
        }

        final String selfOperator = this.parent.getOperatorId();
        if (originUser.equals(selfOperator)) {
            logger.error("received own user message [{}:{}]",
                    originUser, selfOperator);
        }

        // final ContentResolver resolver = context.getContentResolver();

        final String topic = mime;
        final Cursor cursor = this.store.querySubscribeByKey(new String[] {
                SubscribeTableSchema.PROVIDER.n
        }, topic, null);
        if (cursor.getCount() < 1) {
            logger.warn("received a message for which there is no subscription {}", topic);
            cursor.close();
            return false;
        }
        cursor.moveToFirst();
        final String uriString = cursor.getString(0); // only asked for one so
        // it better be it.
        cursor.close();
        final Uri provider = Uri.parse(uriString);

        // we were subscribed to this - does the sender need an ack (let's be
        // careful...), do this before deserializing
        logger.debug("data message notice=[{}]", at);
        if (at != null && at.getDeviceDelivered()) {
            final AcknowledgementThresholds bt = AcknowledgementThresholds.newBuilder()
                    .setDeviceDelivered(true)
                    .build();

            final AmmoMessages.PushAcknowledgement.Builder pushAck =
                    AmmoMessages.PushAcknowledgement.newBuilder()
                            .setUri(originUid)
                            .setThreshold(bt)
                            .setDestinationDevice(originDevice)
                            .setDestinationUser(originUser)
                            .setAcknowledgingDevice(this.parent.getDeviceId())
                            .setAcknowledgingUser(this.parent.getOperatorId())
                            .setStatus(PushStatus.RECEIVED);

            final AmmoMessages.MessageWrapper.Builder mwb = AmmoMessages.MessageWrapper
                    .newBuilder()
                    .setType(AmmoMessages.MessageWrapper.MessageType.PUSH_ACKNOWLEDGEMENT)
                    .setPushAcknowledgement(pushAck);

            final AmmoGatewayMessage.Builder oagmb = AmmoGatewayMessage.newBuilder(mwb,
                    new INetworkService.OnSendMessageHandler() {
                        // final AmmoMessages.PushAcknowledgement.Builder ack_ =
                        // pushAck;
                        @Override
                        public boolean ack(String channel, DisposalState status) {
                            return true;
                        }
                    });

            logger.debug("sending ack=[{}]", pushAck);
            if (channel != null) {
                channel.sendRequest(oagmb.build());
            }
        }

        final Encoding encoding = Encoding.getInstanceByName(encode);
        this.toProvider(priority, context, channel.name, provider, encoding,
                data.toByteArray());

        logger.info("Ammo received message on topic: {} for provider: {}", mime, uriString);

        return true;
    }

    /**
     * Processes and delivers messages received from a channel.
     * <ol>
     * <li>Verify the check sum for the payload is correct String
     * <li>Parse the payload into a message
     * <li>Receive the message
     * </ol>
     * 
     * @param instream
     * @return was the message clean (true) or garbled (false).
     */
    private boolean doResponse(Context context, AmmoGatewayMessage agm) {
        logger.trace("process response");

        if (!agm.hasValidChecksum()) {
            // If this message came from the serial channel, let it know that
            // a corrupt message occurred, so it can update its statistics.
            // Make this a more general mechanism later on.
            if (agm.isSerialChannel)
                this.parent.receivedCorruptPacketOnSerialChannel();

            return false;
        }

        this.total_recv.incrementAndGet();

        final AmmoMessages.MessageWrapper mw;
        try {
            mw = AmmoMessages.MessageWrapper.parseFrom(agm.payload);
        } catch (InvalidProtocolBufferException ex) {
            logger.error("parsing gateway message", ex);
            return false;
        }
        if (mw == null) {
            logger.error("mw was null!");
            return false; // TBD SKN: this was true, why? if we can't parse it
            // then its bad
        }
        final MessageType mtype = mw.getType();
        if (mtype == MessageType.HEARTBEAT) {
            logger.trace("heartbeat");
            return true;
        }

        switch (mw.getType()) {
            case DATA_MESSAGE:
            case TERSE_MESSAGE:
                final boolean subscribeResult = receiveSubscribeResponse(context, mw, agm.channel,
                        agm.priority);
                logger.debug("subscribe reply {}", subscribeResult);
                if (mw.hasDataMessage()) {
                    final AmmoMessages.DataMessage dm = mw.getDataMessage();
                    if (dm.hasOriginDevice()) {
                        Presence.getWorker()
                                .device(dm.getOriginDevice())
                                .operator(dm.getUserId())
                                .upsert();
                    }
                }
                break;

            case AUTHENTICATION_RESULT:
                final boolean result = receiveAuthenticateResponse(context, mw);
                logger.debug("authentication result={}", result);
                break;

            case PUSH_ACKNOWLEDGEMENT:
                final boolean postalResult = receivePostalResponse(context, mw, agm.channel);
                logger.debug("post acknowledgement {}", postalResult);
                break;

            case PULL_RESPONSE:
                final boolean retrieveResult = receiveRetrievalResponse(context, mw, agm.channel,
                        agm.priority);
                logger.debug("retrieve response {}", retrieveResult);
                break;

            case SUBSCRIBE_MESSAGE:
                if (mw.hasSubscribeMessage()) {
                    final AmmoMessages.SubscribeMessage sm = mw.getSubscribeMessage();
                    final FullTopic fulltopic = FullTopic.fromType(sm.getMimeType());

                    if (sm.hasOriginDevice()) {
                        final String deviceId = sm.getOriginDevice();
                        final String operator = sm.getOriginUser();

                        Capability.getWorker()
                                .origin(deviceId)
                                .operator(operator)
                                .topic(fulltopic.topic)
                                .subtopic(fulltopic.subtopic)
                                .upsert();

                        Presence.getWorker()
                                .device(deviceId)
                                .operator(operator)
                                .upsert();
                    }
                }
                break;

            case AUTHENTICATION_MESSAGE:
            case PULL_REQUEST:
            case UNSUBSCRIBE_MESSAGE:
                logger.debug("{} message, no processing", mw.getType());
                break;
            default:
                logger.error("unexpected reply type. {}", mw.getType());
        }

        return true;
    }

    /**
     * Give to the network service for verification. Get the session id set by
     * the gateway.
     * 
     * @param mw
     * @return
     */
    private boolean receiveAuthenticateResponse(Context context, AmmoMessages.MessageWrapper mw) {
        logger.trace("::receiveAuthenticateResponse");

        if (mw == null)
            return false;
        if (!mw.hasAuthenticationResult())
            return false;
        if (mw.getAuthenticationResult().getResult() != AmmoMessages.AuthenticationResult.Status.SUCCESS) {
            return false;
        }
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(INetDerivedKeys.NET_CONN_PREF_IS_ACTIVE, true)
                .commit();
        // sessionId = mw.getSessionUuid();

        // the distributor doesn't need to know about authentication results.
        return true;
    }

    /**
     * Get response to PushRequest from the gateway. This should be seen in
     * response to a post passing through transition for which a notice has been
     * requested.
     * 
     * @param mw
     * @return
     */
    private boolean receivePostalResponse(Context context, AmmoMessages.MessageWrapper mw,
            NetChannel channel) {
        logger.trace("receive response POSTAL");

        if (mw == null)
            return false;
        if (!mw.hasPushAcknowledgement())
            return false;
        final PushAcknowledgement pushResp = mw.getPushAcknowledgement();
        // generate an intent if it was requested

        final Cursor postalReq = this.store.queryPostal(null, new StringBuilder().
                append(DistributorDataStore.PostalTableSchema.UUID.cv()).append("=?").toString(),
                new String[] {
                    pushResp.getUri()
                }, null);

        if (postalReq == null) {
            logger.error(
                    "Got an ack for which not able to find the orignal request, dropping ack {}",
                    pushResp.getUri());
            return false;
        }
        boolean hasMore = postalReq.moveToFirst();
        if (!hasMore) {
            logger.error(
                    "Got an ack for which not able to find the orignal request, dropping ack {}",
                    pushResp.getUri());
            postalReq.close();
            return false;
        }

        if (pushResp.hasThreshold()) {
            final AcknowledgementThresholds thresholds = pushResp.getThreshold();

            byte[] noticeBytes = postalReq.getBlob(postalReq
                    .getColumnIndex(DistributorDataStore.PostalTableSchema.NOTICE.cv()));
            logger.debug("notice bytes {}", noticeBytes);
            final Notice notice = Notice.unpickle(noticeBytes);

            final String topic = postalReq.getString(postalReq
                    .getColumnIndex(DistributorDataStore.PostalTableSchema.TOPIC.cv()));
            final String auid = postalReq.getString(postalReq
                    .getColumnIndex(DistributorDataStore.PostalTableSchema.AUID.cv()));

            postalReq.close();

            // check if it is for us , and how we should notify
            final Notice.IntentBuilder noteBuilder = Notice.getIntentBuilder(notice)
                    .topic(topic)
                    .auid(auid)
                    .channel(channel.name)
                    .device(pushResp.getAcknowledgingDevice())
                    .operator(pushResp.getAcknowledgingUser());

            final Notice.Item note;
            final Intent noticed;

            if (thresholds.getDeviceDelivered()) {
                note = notice.atDeviceDelivered;
                noticed = noteBuilder.buildDeviceDelivered(context);
                ResponseDistributor.sendIntent(note.getVia().v, noticed, context);
            } else if (thresholds.getAndroidPluginReceived()) {
                note = notice.atGatewayDelivered;
                noticed = noteBuilder.buildGatewayDelivered(context);
                ResponseDistributor.sendIntent(note.getVia().v, noticed, context);
            } else if (thresholds.getPluginDelivered()) {
                note = notice.atPluginDelivered;
                noticed = noteBuilder.buildPluginDelivered(context);
                ResponseDistributor.sendIntent(note.getVia().v, noticed, context);
            } else {
                note = null;
                noticed = null;
            }
            PLogger.API_INTENT.debug(
                    "ack note=[{}] intent=[{}]",
                    note, noticed);
            if (PLogger.API_INTENT.isTraceEnabled()) {
                PLogger.API_INTENT.trace("extras=[{}]",
                        PLogger.expandBundle(noticed.getExtras(), '\n'));
            }
        }
        return true;
    }

    /**
     * In those cases where it is requested generate an intent.
     * 
     * @param aggregate
     * @param noticed
     * @param context
     */
    static private void sendIntent(int aggregate, Intent noticed, Context context) {
        if (0 < (aggregate & Via.Type.ACTIVITY.v)) {
            noticed.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(noticed);
        }
        if (0 < (aggregate & Via.Type.BROADCAST.v)) {
            context.sendBroadcast(noticed);
        }
        if (0 < (aggregate & Via.Type.STICKY_BROADCAST.v)) {
            context.sendStickyBroadcast(noticed);
        }
        if (0 < (aggregate & Via.Type.SERVICE.v)) {
            context.startService(noticed);
        }
    }

    public Handler responseHandler = null;
    public Handler upsertHandler = null;

    @Override
    public void run()
    {
        Looper.prepare();

        /**
         * Process.THREAD_PRIORITY_FOREGROUND(-2) and THREAD_PRIORITY_DEFAULT(0)
         */
        Process.setThreadPriority(-7);
        logger.info("deserializer thread start @prio: {}",
                Process.getThreadPriority(Process.myTid()));

        this.responseHandler = new Handler() {
            final ResponseDistributor master = ResponseDistributor.this;

            @Override
            public void handleMessage(Message msg) {
                if (!(msg.obj instanceof AmmoGatewayMessage)) {
                    logger.error("not the proper message type");
                    return;
                }
                final AmmoGatewayMessage agm = (AmmoGatewayMessage) msg.obj;
                master.doResponse(master.context, agm);
            }
        };

        this.upsertHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (!(msg.obj instanceof Item)) {
                    logger.error("not the proper message type");
                    return;
                }
                final Item item = (Item) msg.obj;

                try {
                    final AsyncQueryHelper.InsertResultHandler insertHandler = new
                            AsyncQueryHelper.InsertResultHandler() {

                                final ResponseDistributor master = ResponseDistributor.this;

                                @Override
                                public void run(Uri resultTuple) {
                                    // use master to update the database as
                                    // needed
                                    logger.info(
                                            "Ammo inserted received message in remote content provider=[{}] inserted in [{}]",
                                            item.provider, resultTuple);
                                }
                            };

                    RequestSerializer.deserializeToProvider(insertHandler, item.context,
                            item.context.getContentResolver(),
                            item.channelName, item.provider, item.encoding, item.data);
                    /*
                     * tlogger.info(PLogger.TEST_QUEUE_FORMAT, new Object[] {
                     * System.currentTimeMillis(), "insert_queue",
                     * this.queue.size() });
                     */
                } catch (Exception ex) {
                    /* logger.error( */
                }
            }

        };
        Looper.loop();
    }

}

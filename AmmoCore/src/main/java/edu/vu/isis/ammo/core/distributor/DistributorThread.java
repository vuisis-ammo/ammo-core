/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
 */

package edu.vu.isis.ammo.core.distributor;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.jcip.annotations.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Debug;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Notice;
import edu.vu.isis.ammo.api.type.Notice.Via;
import edu.vu.isis.ammo.api.type.Order;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.AmmoMimeTypes;
import edu.vu.isis.ammo.core.ChannelChange;
import edu.vu.isis.ammo.core.NetworkManager;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTotalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SerializeMode;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.serializer.ContentProviderContentItem;
import edu.vu.isis.ammo.core.distributor.serializer.CustomAdaptorCache;
import edu.vu.isis.ammo.core.distributor.serializer.ISerializer;
import edu.vu.isis.ammo.core.distributor.serializer.JsonSerializer;
import edu.vu.isis.ammo.core.distributor.serializer.TerseSerializer;
import edu.vu.isis.ammo.core.distributor.store.Capability;
import edu.vu.isis.ammo.core.distributor.store.Presence;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetChannel;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.AcknowledgementThresholds;
import edu.vu.isis.ammo.core.pb.AmmoMessages.MessageWrapper.MessageType;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement.PushStatus;
import edu.vu.isis.ammo.core.provider.Relations;
import edu.vu.isis.ammo.core.ui.AmmoCore;
import edu.vu.isis.ammo.util.ByteBufferAdapter;
import edu.vu.isis.ammo.util.FullTopic;

/**
 * The distributor service runs in the ui thread. This establishes a new thread
 * for distributing the requests.
 */
@ThreadSafe
public class DistributorThread extends Thread {
    // ===========================================================
    // Constants
    // ===========================================================
    private static final Logger logger = LoggerFactory.getLogger("dist.thread");
    private static final Logger resLogger = LoggerFactory.getLogger("test.queue.response");
    private static final Logger reqLogger = LoggerFactory.getLogger("test.queue.request");
    private static final boolean RUN_TRACE = false;

    private static final Marker MARK_POSTAL = MarkerFactory.getMarker("postal");
    private static final Marker MARK_RETRIEVAL = MarkerFactory.getMarker("retrieval");
    private static final Marker MARK_SUBSCRIBE = MarkerFactory.getMarker("subscribe");

    private static final String ACTION_BASE = "edu.vu.isis.ammo.";
    public static final String ACTION_MSG_SENT = ACTION_BASE + "ACTION_MESSAGE_SENT";
    public static final String ACTION_MSG_RCVD = ACTION_BASE + "ACTION_MESSAGE_RECEIVED";
    public static final String EXTRA_TOPIC = "topic";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_CHANNEL = "channel";
    public static final String EXTRA_STATUS = "status";

    // 20 seconds expressed in milliseconds
    private static final int BURP_TIME = 20 * 1000;

    private final Context context;
    public Context getContext() { return this.context; }
    private final NetworkManager networkManager;
    private final CustomAdaptorCache ammoAdaptorCache;
    /**
     * The backing store for the distributor
     */
    final private DistributorDataStore store;
    
    final public ContractStore contractStore;

    @SuppressWarnings("unused")
    private static final int SERIAL_NOTIFY_ID = 1;
    @SuppressWarnings("unused")
    private static final int IP_NOTIFY_ID = 2;

    private int current_icon_id = 1;

    private AtomicInteger total_sent = new AtomicInteger(0);
    private AtomicInteger total_recv = new AtomicInteger(0);

    private NotifyMsgNumber notify = null;
    static private final AtomicInteger gThreadOrdinal = new AtomicInteger(1);
    
    private ResponseExecutor deserializeExecutor;


    public DistributorThread(final Context context, final NetworkManager parent) {
        super(new StringBuilder("Distribute-").
                append(DistributorThread.gThreadOrdinal.getAndIncrement()).toString());
        this.context = context;
        this.networkManager = parent;
        this.requestQueue = new LinkedBlockingQueue<AmmoRequest>(200);
        this.responseQueue = new PriorityBlockingQueue<AmmoGatewayMessage>(200,
                new AmmoGatewayMessage.PriorityOrder());
        this.deserializeExecutor = ResponseExecutor.newInstance(this);
        
        this.store = new DistributorDataStore(context);
        this.contractStore = ContractStore.newInstance(context);

        this.channelStatus = new ConcurrentHashMap<String, ChannelStatus>();
        this.channelDelta = new AtomicBoolean(true);

        this.channelAck = new LinkedBlockingQueue<ChannelAck>(200);
        this.ammoAdaptorCache = new CustomAdaptorCache(context);
        logger.debug("thread constructed");
    }

    private class NotifyMsgNumber implements Runnable {

        private DistributorThread parent = null;

        private int last_sent_count = 0;
        private int last_recv_count = 0;

        public NotifyMsgNumber(DistributorThread parent) {
            this.parent = parent;
        }

        private AtomicBoolean terminate = new AtomicBoolean(false);
        
        private AtomicBoolean channelStatus = new AtomicBoolean(false);
        
        void setChannelStatus (boolean status) {
        	channelStatus.set(status);
        }

        public void terminate() {
            terminate.set(true);
        }

        public void run() {
                updateNotification();
        }

        private void updateNotification() {

        	logger.trace("Update Sent and Receive Counts");
        	
            // check for variable update ...
            int total_sent = parent.total_sent.get();
            int total_recv = parent.total_recv.get();

            int sent = total_sent - last_sent_count;
            int recv = total_recv - last_recv_count;

            int icon = 0;
            // figure out the icon ...
            if (sent == 0 && recv == 0) {
                logger.trace("No data in the last interval");
                icon = R.drawable.nodata;                
            }
            else if (sent > 0 && recv == 0) {
            	logger.trace("Only sending in the last interval");
                icon = R.drawable.sending;
            }
            else if (sent == 0 && recv > 0) {
            	logger.trace("Only receiving in the last interval");
                icon = R.drawable.receiving;
            }
            else if (sent > 0 && recv > 0) {
            	logger.trace("Sending and Receiving in the last interval");
                icon = R.drawable.alldata;
            }

            String contentText = "Sent " + total_sent + " Received " + total_recv;
            
            if (channelStatus.get() == false) {
                int icon1 = R.drawable.channel_down;
//                current_icon_id = IP_NOTIFY_ID;
                logger.info("Ammo Channel Disconnected");
                notifyIcon("Omma Channel Down", "Data Channel", "Offline", icon1);
                return;
            }
            
            parent.notifyIcon("", "Data Channel", contentText, icon);

            // save the last sent and recv ...
            last_sent_count = total_sent;
            last_recv_count = total_recv;

            parent.networkManager.notifyMsg.postDelayed(this, 30000);
        }

		public boolean getChannelStatus() {
			return channelStatus.get();
		}
    }

    public DistributorDataStore store() {
        return this.store;
    }

    /**
     * This method is *not* called from the distributor thread so it should not
     * update the store directly. Rather it updates a map which records the same
     * information found in the store's channel table.
     * 
     * @param context
     * @param channelName
     * @param change
     */
    public void onChannelChange(final Context context, final String channelName,
            final ChannelChange change) {
        final ChannelStatus priorStatus = this.channelStatus.get(channelName);
        if (priorStatus != null) {
            final ChannelChange priorChange = priorStatus.change;
            if (change.equals(priorChange))
                return; // already present
        }
        logger.debug("On Channel Change: {} :: {}", channelName, change);
        this.channelStatus.put(channelName, new ChannelStatus(change)); // change
        // channel
        if (!channelDelta.compareAndSet(false, true))
            return; // mark as needing processing
        this.signal(); // signal to perform update

        setupNotificationIcon(channelName, change);
    }

    private void setupNotificationIcon(String channelName, ChannelChange change)
    {
        if (change == ChannelChange.DEACTIVATE)
        {
        	logger.debug("Channel: {} deactivated", channelName);
        	for (Entry<String, ChannelStatus> entry : channelStatus.entrySet()) {
                if (entry.getValue().change == ChannelChange.ACTIVATE) {
                    return; // leave, since at least one channel is still active
                }
            }

            // none of the channels are active ...
        	logger.debug("All Ammo Channels Deactivated");
            if (notify != null) {
            	notify.setChannelStatus(false);
                notify.terminate();
            }
            
            return;
        }

        synchronized (this) {
        	logger.trace("Ammo Channel {} Activated", channelName);
        	
        	if (notify == null) {
        		logger.trace("Creating NotifyMsgNumber");
        		notify = new NotifyMsgNumber(this);
        	}        	
        	
        	if (notify.getChannelStatus() != true) {
        		logger.trace("Post Delayed Registration");
        		notify.setChannelStatus(true);
        		this.networkManager.notifyMsg.postDelayed(notify, 5000);
        	}
        }
    }

    private void notifyIcon(String tickerTxt,
            String contentTitle,
            String contentText,
            int icon)
    {
    	logger.trace("Creating a notification, tickerTxt: {}", tickerTxt);
    	
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(ns);

        CharSequence tickerText = tickerTxt;
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        Intent notificationIntent = new Intent(context, AmmoCore.class);

        PendingIntent contentIntent = PendingIntent
                .getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        notification.setLatestEventInfo(context, contentTitle, contentText,
                contentIntent);

        mNotificationManager.notify(current_icon_id, notification);
    }

    /**
     * When a channel comes on-line the disposition table should be checked to
     * see if there are any waiting messages for that channel. Channels going
     * off-line are uninteresting so no signal.
     */
    private final ConcurrentMap<String, ChannelStatus> channelStatus;
    private final AtomicBoolean channelDelta;

    /**
     * The status field indicates the table does not yet reflect this state
     */
    private class ChannelStatus {
        final ChannelChange change;
        final AtomicBoolean status;

        public ChannelStatus(final ChannelChange change) {
            this.change = change;
            this.status = new AtomicBoolean(false);
        }
    }

    private final LinkedBlockingQueue<ChannelAck> channelAck;

    private class ChannelAck {
        public final Relations type;
        public final long id;

        public final String topic;
        public final UUID uuid;
        public final String auid;
        public final Notice notice;

        public final String channel;
        public final DisposalState status;

        public ChannelAck(Relations type, long id, UUID uuid,
                String topic, String auid, Notice notice,
                String channel, DisposalState status)
        {
            this.type = type;
            this.id = id;
            this.uuid = uuid;
            this.topic = topic;
            this.auid = auid;
            this.notice = notice;

            this.channel = channel;
            this.status = status;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append(" type ").append(type)
                    .append(" id ").append(id)
                    .append(" uuid ").append(uuid)
                    .append(" topic ").append(topic)
                    .append(" aid ").append(auid)
                    .append(" channel ").append(channel)
                    .append(" status ").append(status)
                    .toString();
        }
    }

    /**
     * Called by the channel acknowledgment once the channel has attempted to
     * send the message.
     * 
     * @param ack
     * @return
     */
    private boolean announceChannelAck(ChannelAck ack) {
        logger.trace("send ACK {}", ack);
        try {
            PLogger.QUEUE_ACK_ENTER.trace("offer ack: {}", ack);
            if (!this.channelAck.offer(ack, 2, TimeUnit.SECONDS)) {
                logger.warn("announcing channel ack queue is full");
                return false;
            }
        } catch (InterruptedException ex) {
            logger.warn("announcing channel ack was interrupted");
            return false;
        }
        this.signal();

        if (ack.status == DisposalState.SENT) // update recv count and send
                                              // notify
        {
            total_sent.incrementAndGet();
        }

        return true;
    }

    /**
     * Once the channel is done with a request it generates a ChannelAck object.
     * 
     * @param ack
     */
    private void doChannelAck(final Context context, final ChannelAck ack) {
        logger.trace("channel ACK {}", ack);
        final long numUpdated;
        switch (ack.type) {
            case POSTAL:
                numUpdated = this.store.updatePostalByKey(ack.id, ack.channel, ack.status);
                break;
            case RETRIEVAL:
                numUpdated = this.store.updateRetrievalByKey(ack.id, ack.channel, ack.status);
                break;
            case SUBSCRIBE:
                numUpdated = this.store.updateSubscribeByKey(ack.id, ack.channel, ack.status);
                break;
            default:
                logger.warn("invalid ack type {}", ack);
                return;
        }
        // generate broadcast intent for everyone who cares about this
        if (ack.notice != null) {
            final Notice.Item note = ack.notice.atSend;
            final Notice.Via via = note.getVia();
            if (via.isActive()) {

                final Notice.IntentBuilder noteBuilder = Notice.getIntentBuilder(ack.notice)
                        .topic(ack.topic)
                        .auid(ack.auid)
                        .channel(ack.channel);

                if (ack.status != null)
                    noteBuilder.status(ack.status.toString());

                final Intent noticed = noteBuilder.buildSent(context);
                final int aggregate = via.v;

                PLogger.API_INTENT.debug(
                        "ack note=[{}] intent=[{}]",
                        note, noticed);

                if (0 < (aggregate & Via.Type.ACTIVITY.v)) {
                    try {
                        noticed.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(noticed);
                    } catch (ActivityNotFoundException ex) {
                        logger.warn("no activity for intent=[{}]", noticed);
                    }
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

                if (PLogger.API_INTENT.isTraceEnabled()) {
                    PLogger.API_INTENT.trace("extras=[{}]",
                            PLogger.expandBundle(noticed.getExtras(), '\n'));
                }
            }
        }

        logger.debug("count {}: intent {}", numUpdated, ack);

    }

    private void announceChannelActive(final Context context, final String name) {
        logger.trace("inform applications to retrieve more data");

        // TBD SKN --- the channel activates repeatedly due to status change
        // --- do not use this to generate ammo_connected intent
        // --- generate ammo_connected after gateway authenticated
        // // broadcast login event to apps ...
        // final Intent loginIntent = new Intent(INetPrefKeys.AMMO_CONNECTED);
        // loginIntent.putExtra("channel", name);
        // context.sendBroadcast(loginIntent);
    }

    /**
     * Contains client application requests
     */
    private final BlockingQueue<AmmoRequest> requestQueue;

    public String distributeRequest(AmmoRequest request) {
        try {
            logger.info("From AIDL into AMMO type:{} uuid:{}", request.topic, request.uuid);

            PLogger.QUEUE_REQ_ENTER.trace("\"action\":\"offer\" \"request\":\"{}\"", request);
            if (!this.requestQueue.offer(request, 1, TimeUnit.SECONDS)) {
                logger.error("could not process request={} size={}", request, this.requestQueue.size());
                this.signal();
                return null;
            }
            this.signal();
            return request.uuid;

        } catch (InterruptedException ex) {
            logger.error("Exception while distributing request", ex);
        }
        return null;
    }

    /**
     * Contains gateway responses
     */
    private final PriorityBlockingQueue<AmmoGatewayMessage> responseQueue;

    public boolean distributeResponse(AmmoGatewayMessage agm) {
        PLogger.QUEUE_RESP_ENTER.trace("\"action\":\"offer\" \"response\":\"{}\"", agm);
        if (!this.responseQueue.offer(agm, 1, TimeUnit.SECONDS)) {
            logger.error("could not process response {}", agm);
            this.signal();
            return false;
        }
        this.signal();
        return true;
    }

    private void signal() {
        synchronized (this) {
            this.notifyAll();
        }
    }

    /**
     * Check to see if there is any work for the thread to do. If there are no
     * network connections then nothing can be distributed, so no work. Either
     * incoming requests, responses, or a channel has been activated.
     */
    private boolean isReady() {
        if (this.channelDelta.get())
            return true;

        if (!this.channelAck.isEmpty())
            return true;
        if (!this.responseQueue.isEmpty())
            return true;
        if (!this.requestQueue.isEmpty())
            return true;
        return false;
    }

    /**
     * The following condition wait holds until there is some work for the
     * distributor. The method tries to be fair processing the requests in
     */
    @Override
    public void run()
    {
        Process.setThreadPriority(-6); // Process.THREAD_PRIORITY_FOREGROUND(-2)
                                       // and THREAD_PRIORITY_DEFAULT(0)
        logger.info("distributor thread start @prio: {}",
                Process.getThreadPriority(Process.myTid()));

        final AtomicLong sanitationSchedule = new AtomicLong(System.currentTimeMillis()
                + (1 * 60 * 60 * 1000));
        // initial sanitation work happens after 1 hour (in milliseconds)

        if (networkManager.isConnected()) {

            for (final Map.Entry<String, ChannelStatus> entry : channelStatus.entrySet()) {
                final String name = entry.getKey();
                this.store.deactivateDisposalStateByChannel(name);
            }

            this.doSubscribeCache(networkManager);
            this.doRetrievalCache(networkManager);
            this.doPostalCache(networkManager);
        }

        try {
            while (true) {
                // condition wait, is there something to process?
                synchronized (this) {
                    while (!this.isReady()) {
                        logger.info("No data to process");
                        this.wait(BURP_TIME);

                        final long currentTime = System.currentTimeMillis();
                        if (sanitationSchedule.get() > currentTime) {
                            continue;
                        }
                        sanitationSchedule.getAndSet(currentTime + (10L * 60 * 1000));
                        // next alarm in 10 minutes, specified in milliseconds
                        this.takeOutGarbage();
                    }
                }
                while (this.isReady()) {

                    final long currentTime = System.currentTimeMillis();
                    
                    if (this.channelDelta.getAndSet(false)) {
                        logger.trace("channel change");
                        this.doChannelChange(networkManager);
                    }

                    if (!this.channelAck.isEmpty()) {
                        logger.info("processing channel acks, remaining {}",
                                this.channelAck.size());
                        try {
                            final ChannelAck ack = this.channelAck.take();
                            PLogger.QUEUE_ACK_EXIT.trace(PLogger.QUEUE_FORMAT,
                                    new Object[] {
                                            this.channelAck.size(), ack.uuid, 0, ack
                                    });

                            this.doChannelAck(this.context, ack);
                        } catch (ClassCastException ex) {
                            logger.error("channel ack queue contains illegal item of class", ex);
                        }
                    }

                    if (!this.responseQueue.isEmpty()) {
                        try {
                            final AmmoGatewayMessage agm = this.responseQueue.take();
                            PLogger.QUEUE_RESP_EXIT.trace(PLogger.QUEUE_FORMAT,
                                            this.responseQueue.size(), agm.payload_checksum,
                                            agm.size, agm
                                    );
                            
                            logger.info(
                                    "processing response {}, recvd @{}, remaining {}",
                                            agm.payload_checksum, agm.buildTime,
                                            this.responseQueue.size()
                                    );
                            resLogger.info(PLogger.TEST_QUEUE_FORMAT,
                                    currentTime, 
                                    "response_queue",
                                    this.responseQueue.size(),
                                    currentTime - agm.buildTime
                            );
                            this.doResponse(this.context, agm);
                        } catch (ClassCastException ex) {
                            logger.error("response queue contains illegal item of class", ex);
                        }
                    }

                    if (!this.requestQueue.isEmpty()) {
                        try {
                            final AmmoRequest ar = this.requestQueue.take();
                            logger.info("processing request uuid {}, remaining {}", ar.uuid,
                                    this.requestQueue.size());
                            reqLogger.info(PLogger.TEST_QUEUE_FORMAT,
                                            System.currentTimeMillis(), 
                                            "request_queue",
                                            this.requestQueue.size(),
                                            currentTime - ar.buildTime
                                    );
                            PLogger.QUEUE_REQ_EXIT.trace(PLogger.QUEUE_FORMAT,
                                            this.requestQueue.size(), ar.uuid, "n/a", ar
                                    );

                            this.doRequest(networkManager, ar);
                        } catch (ClassCastException ex) {
                            logger.error("request queue contains illegal item of class", ex);
                        }
                    }
                }
                logger.trace("work processed");

            }
        } catch (InterruptedException ex) {
            logger.warn("task interrupted", ex);
        }
        return;
    }

    // ================= DRIVER METHODS ==================== //

    /**
     * Processes and delivers messages received from the gateway.
     * <ol>
     * <li>Verify the check sum for the payload is correct
     * <li>Parse the payload into a message
     * <li>Receive the message
     * </ol>
     * 
     * @param instream
     * @return was the message clean (true) or garbled (false).
     */
    private boolean doRequest(NetworkManager that, AmmoRequest agm) {
        logger.trace("process request {}", agm);
        switch (agm.action) {
            case POSTAL:
                if (RUN_TRACE) {
                    Debug.startMethodTracing("doPostalRequest");
                    doPostalRequest(that, agm);
                    Debug.stopMethodTracing();
                } else {
                    doPostalRequest(that, agm);
                }

                break;
            case DIRECTED_POSTAL:
                doPostalRequest(that, agm);
                break;
            case UNPOSTAL:
                cancelPostalRequest(that, agm);
                break;
            case RETRIEVAL:
                doRetrievalRequest(that, agm);
                break;
            case UNRETRIEVAL:
                cancelRetrievalRequest(that, agm);
                break;
            case SUBSCRIBE:
                doSubscribeRequest(that, agm, 1);
                break;
            case DIRECTED_SUBSCRIBE:
                doSubscribeRequest(that, agm, 2);
                break;
            case UNSUBSCRIBE:
                cancelSubscribeRequest(that, agm);
                break;
            case NONE:
            case PUBLISH:
            default:
                break;
        }
        return true;
    }

    /**
     * Check to see if the active channels can be used to send a request. This
     * updates the channel status table. There is a slight race here, it is
     * possible that while the list was being processed the channel status
     * changed. This is all right it just means that this method may be called
     * with no work to do.
     */
    private void doChannelChange(NetworkManager that) {
        logger.trace("::doChannelChange()");

        for (final Map.Entry<String, ChannelStatus> entry : channelStatus.entrySet()) {
            final String name = entry.getKey();
            final ChannelStatus status = entry.getValue();
            if (status.status.getAndSet(true))
                continue;

            logger.trace("::doChannelChange() : {} , {}", name, status.change);
            final ChannelChange change = status.change;
            switch (change) {
                case DEACTIVATE:
                    this.store.upsertChannelByName(name, ChannelState.INACTIVE);
                    this.store.deactivateDisposalStateByChannel(name);

                    logger.trace("::channel deactivated");
                    break;

                case ACTIVATE:
                    this.store.upsertChannelByName(name, ChannelState.ACTIVE);
                    this.store.deactivateDisposalStateByChannel(name);

                    this.store.activateDisposalStateByChannel(name);
                    this.announceChannelActive(that.getContext(), name);
                    logger.trace("::channel activated");
                    break;

                case REPAIR:
                    this.store.upsertChannelByName(name, ChannelState.ACTIVE);
                    this.store.activateDisposalStateByChannel(name);

                    this.store.repairDisposalStateByChannel(name);
                    this.announceChannelActive(that.getContext(), name);
                    logger.trace("::channel repaired");
                    break;

                default:
                    logger.trace("::channel unknown change {}", change);
            }
        }

        // we could do a priming query to determine if there are any candidates

        this.doPostalCache(that);
        this.doRetrievalCache(that);
        this.doSubscribeCache(that);
    }

    private void takeOutGarbage() {
        this.store.deletePostalGarbage();
        this.store.deleteRetrievalGarbage();
        this.store.deleteSubscribeGarbage();
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
                networkManager.receivedCorruptPacketOnSerialChannel();

            agm.releasePayload();
            return false;
        }

        total_recv.incrementAndGet();
        
        final AmmoMessages.MessageWrapper mw;
        try {
            mw = AmmoMessageSerializer.deserialize(agm.payload);
        } catch (Exception ex) {
/* FIXME this catch is overly aggressive */
            logger.error("parsing gateway message", ex);
            agm.releasePayload();
            return false;
        }
        if (mw == null) {
            logger.error("mw was null!");
            agm.releasePayload();
            return false; // TBD SKN: this was true, why? if we can't parse it
            // then its bad
        }
        final MessageType mtype = mw.getType();
        if (mtype == MessageType.HEARTBEAT) {
            logger.trace("heartbeat");
            agm.releasePayload();
            return true;
        }

        switch (mw.getType()) {
            case DATA_MESSAGE:
            case TERSE_MESSAGE:
                final boolean subscribeResult = receiveSubscribeResponse(context, mw, agm.channel,
                        agm.priority, agm.payload);
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
                if( subscribeResult ) {
                	agm.payload = null;
                } else {
                	agm.releasePayload();
                }
                break;

            case AUTHENTICATION_RESULT:
                final boolean result = receiveAuthenticateResponse(context, mw);
                logger.debug("authentication result={}", result);
                agm.releasePayload();
                break;

            case PUSH_ACKNOWLEDGEMENT:
                final boolean postalResult = receivePostalResponse(context, mw, agm.channel);
                logger.debug("post acknowledgement {}", postalResult);
                agm.releasePayload();
                break;

            case PULL_RESPONSE:
                final boolean retrieveResult = receiveRetrievalResponse(context, mw, agm.channel,
                        agm.priority, agm.payload);
                logger.debug("retrieve response {}", retrieveResult);
                if( retrieveResult ) {
                	agm.payload = null;
                } else {
                	agm.releasePayload();
                }
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
                agm.releasePayload();
                break;

            case AUTHENTICATION_MESSAGE:
            case PULL_REQUEST:
            case UNSUBSCRIBE_MESSAGE:
                logger.debug("{} message, no processing", mw.getType());
                agm.releasePayload();
                break;
            default:
                logger.error("unexpected reply type. {}", mw.getType());
                agm.releasePayload();
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

    @SuppressWarnings("unused")
    private boolean collectGarbage = true;
    private RequestDeserializer.Builder requestDeserializerBuilder;

    // =========== POSTAL ====================

    /**
     * Used when a new postal request arrives. It first tries to dispatch the
     * request to the network service. Regardless of whether that works, the
     * request is recorded for later use.
     * 
     * @param that
     * @param uri
     * @param mimeType
     * @param data
     * @param handler
     * @return
     */
    private void doPostalRequest(final NetworkManager that, final AmmoRequest ar) {

        // Dispatch the message.
        try {
            final UUID uuid = UUID.fromString(ar.uuid); // UUID.randomUUID();
            final String auid = ar.uid;
            final String topic = ar.topic.asString();
            final DistributorPolicy.Topic policy = that.policy().matchPostal(topic);
            final String channel = (ar.channelFilter == null) ? null : ar.channelFilter.cv();

            logger.debug("process request topic {}, uuid {}", ar.topic, uuid);
            logger.trace(" channel {}, policy {}", channel, policy);

            final ContentValues values = new ContentValues();
            values.put(PostalTableSchema.UUID.cv(), uuid.toString());
            values.put(PostalTableSchema.AUID.cv(), auid);
            values.put(PostalTableSchema.TOPIC.cv(), topic);
            if(ar.provider != null) {
                values.put(PostalTableSchema.PROVIDER.cv(), ar.provider.cv());
            }
            values.put(PostalTableSchema.CHANNEL.cv(), channel);
            if (ar.payload != null) {
                final byte[] payloadBytes = ar.payload.pickle();
                values.put(PostalTableSchema.PAYLOAD.cv(), payloadBytes);
            }
            values.put(PostalTableSchema.PRIORITY.cv(), policy.routing.getPriority(ar.priority));
            values.put(PostalTableSchema.EXPIRATION.cv(),
                    policy.routing.getExpiration(ar.expire.cv()));

            values.put(PostalTableSchema.CREATED.cv(), System.currentTimeMillis());

            values.put(PostalTableSchema.ORDER.cv(), ar.order.cv());
            if (ar.notice != null)
                values.put(PostalTableSchema.NOTICE.cv(), ar.notice.pickle());

            final Dispersal dispersal = policy.makeRouteMap(channel);
            if (!that.isConnected()) {
                values.put(PostalTableSchema.DISPOSITION.cv(), DisposalTotalState.NEW.cv());
                long key = this.store.upsertPostal(values, policy.makeRouteMap(channel));
                logger.debug("no channel connected, added postal [{}] [{}]", key, values);
                return;
            }

            final RequestSerializer serializer = RequestSerializer.newInstance(ar.provider, ar.payload);
            serializer.setSerializeActor(new RequestSerializer.OnSerialize() {

                final RequestSerializer serializer_ = serializer;
                final NetworkManager that_ = that;
                final DistributorThread parent = DistributorThread.this;
                
                byte[] bytes = null;
                @Override

                public byte[] getBytes() {
                    return this.bytes;
                }
             
                @Override
                public void run(Encoding encode) {
                    // TODO FPE handle payload with data types
                    if (serializer_.payload.whatContent() != Payload.Type.NONE) {
                        if (this.bytes != null) {
                            logger.warn("bytes already bound {}", this.bytes);
                            return;
                        }
                        final ByteBufferAdapter result =
                                RequestSerializer.serializeFromContentValues(
                                        serializer_.payload.getCV(),
                                        encode, ar.topic.asString(), contractStore);

                        if (result == null) {
                            logger.error(
                                    "Null result from serialize content value, encoding into {}",
                                    encode);
                        }
                        
                        return;
                        
                    } else {
                        try {
                            final ByteBufferAdapter result = RequestSerializer.serializeFromProvider(
                                    that_.getContext().getContentResolver(), serializer_.provider.asUri(),
                                    encode);

                            if (result == null) {
                                logger.error("Null result from serialize {} {} ",
                                        serializer_.provider, encode);
                            }
                            return result;
                        } catch (IOException ex) {
                            logger.error("invalid row for serialization", ex);
                            return null;
                        } catch (TupleNotFoundException e) {
                            logger.error("tuple not found when processing postal table");
                            parent.store().deletePostal(new StringBuilder()
                                    .append(PostalTableSchema.PROVIDER.q()).append("=?")
                                    .append(" AND ")
                                    .append(PostalTableSchema.TOPIC.q()).append("=?")
                                    .toString(),
                                    new String[] {
                                            e.missingTupleUri.getPath(), topic
                                    });
                            return null;
                        } catch (NonConformingAmmoContentProvider ex) {
                            logger.error("non-conforming content provider", ex);
                            return null;
                        }
                    }
                }
            });

            values.put(PostalTableSchema.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
            // We synchronize on the store to avoid a race between dispatch and
            // queuing
            synchronized (this.store) {
                final long id = this.store.upsertPostal(values, policy.makeRouteMap(channel));

                final Dispersal dispatchResult = this.dispatchPostalRequest(that, ar.notice,
                        uuid, topic, dispersal, serializer,
                        new INetworkService.OnSendMessageHandler() {
                            final DistributorThread parent = DistributorThread.this;
                            final long id_ = id;
                            final UUID uuid_ = uuid;
                            final String topic_ = topic;
                            final String auid_ = ar.uid;
                            final Notice notice_ = ar.notice;

                            @Override
                            public boolean ack(String channel, DisposalState status) {
                                final ChannelAck chack = new ChannelAck(Relations.POSTAL,
                                        id_, uuid_,
                                        topic_, auid_, notice_,
                                        channel, status);
                                return parent.announceChannelAck(chack);
                            }
                        });

                this.store.updatePostalByKey(id, null, dispatchResult);
            }

        } catch (NullPointerException ex) {
            logger.warn("sending postal request failed", ex);
        }
    }

    private void cancelPostalRequest(final NetworkManager that, final AmmoRequest ar) {

        // Dispatch the message.
        try {
            // final UUID uuid = UUID.fromString(ar.uuid); //UUID.randomUUID();
            // final String auid = ar.uid;
            final String topic = ar.topic.asString();
            final String provider = ar.provider.cv();

            this.store().deletePostal(new StringBuilder()
                    .append(PostalTableSchema.PROVIDER.q()).append("=?")
                    .append(" AND ")
                    .append(PostalTableSchema.TOPIC.q()).append("=?").toString(),
                    new String[] {
                            provider, topic
                    });
        } finally {

        }

    }

    /**
     * Check for requests whose delivery policy has not been fully satisfied and
     * for which there is, now, an available channel.
     */
    private void doPostalCache(final NetworkManager that) {
        logger.debug(MARK_POSTAL, "process table POSTAL");

        if (!that.isConnected())
            return;

        final Cursor pending = this.store.queryPostalReady();
        if (pending == null) {
            logger.warn("no requests pending");
            return;
        }

        logger.info("pending postal requests=[{}]", pending.getCount());
        // Iterate over each row serializing its data and sending it.
        for (boolean moreItems = pending.moveToFirst(); moreItems; moreItems = pending.moveToNext())
        {
            final int id = pending.getInt(pending.getColumnIndex(PostalTableSchema._ID.n));
            final String auid = pending.getString(pending.getColumnIndex(PostalTableSchema.AUID.n));
            final String uuidString = pending.getString(pending
                    .getColumnIndex(PostalTableSchema.UUID.n));
            final UUID uuid = UUID.fromString(uuidString);

            final Provider provider = new Provider(pending.getString(pending
                    .getColumnIndex(PostalTableSchema.PROVIDER.n)));
            final int payloadIx = pending.getColumnIndex(PostalTableSchema.PAYLOAD.n);
            final Payload payload;
            if (!pending.isNull(payloadIx)) {
                final byte[] payloadBytes = pending.getBlob(payloadIx);
                logger.trace("get payload bytes=[{}]", payloadBytes);
                payload = Payload.unpickle(payloadBytes);
            } else {
                payload = null;
            }
            logger.trace("payload=[{}]", payload);
            final String topic = pending.getString(pending
                    .getColumnIndex(PostalTableSchema.TOPIC.n));
            final String channelFilter = pending.getString(pending
                    .getColumnIndex(PostalTableSchema.CHANNEL.n));

            final int noticeIx = pending.getColumnIndex(PostalTableSchema.NOTICE.n);
            final Notice notice;
            if (!pending.isNull(noticeIx)) {
                final byte[] noticeBytes = pending.getBlob(noticeIx);
                logger.trace("get notice bytes=[{}]", noticeBytes);
                notice = Notice.unpickle(noticeBytes);
            } else {
                notice = Notice.RESET;
            }

            logger.debug("serializing: {} as {}", provider, topic);

            final RequestSerializer serializer = RequestSerializer.newInstance(provider, payload);
            final String orderingMethodId = pending
                    .getString(pending.getColumnIndex(PostalTableSchema.ORDER.n));
            @SuppressWarnings("unused")
            final Order orderMethod = new Order(orderingMethodId);

            final SerializeMode serialType;
            int dataColumnIndex = pending.getColumnIndex(PostalTableSchema.DATA.n);

            final String data;
            {
                if (pending.isNull(dataColumnIndex)) {
                    data = null;
                    serialType = SerializeMode.DEFERRED;

                } else {
                    data = pending.getString(dataColumnIndex);
                    serialType = SerializeMode.DIRECT;
                }
            }

            serializer.setSerializeActor(new RequestSerializer.OnSerialize() {
                final DistributorThread parent = DistributorThread.this;
                final RequestSerializer serializer_ = serializer;
                final NetworkManager that_ = that;
                final SerializeMode serialType_ = serialType;
                final String data_ = data;
                
                byte[] bytes = null;
                @Override
                public byte[] getBytes() {
                    return this.bytes;
                }

                @Override
                public ByteBufferAdapter run(Encoding encode) {
                    switch (serialType_) {
                        case DIRECT:
                            return (data.length() > 0) ? ByteBufferAdapter.obtain(data.getBytes()) : null;
                        case INDIRECT:
                        case DEFERRED:
                        default:
                            try {
                                if (payload != null && payload.isSet()) {
                                    return RequestSerializer.serializeFromContentValues(
                                            payload.getCV(), encode);
                                } else {

                                    return RequestSerializer.serializeFromProvider(
                                            that_.getContext().getContentResolver(),
                                            serializer_.provider.asUri(), encode);
                                }
                            } catch (IOException e1) {
                                logger.error("invalid row for serialization");
                            } catch (TupleNotFoundException ex) {
                                logger.error("no tuple for postal request serializer [{}]",
                                        serializer_);
                                parent.store().deletePostal(
                                        new StringBuilder()
                                                .append(PostalTableSchema.PROVIDER.q())
                                                .append("=?").append(" AND ")
                                                .append(PostalTableSchema.TOPIC.q()).append("=?")
                                                .toString(),
                                        new String[] {
                                                ex.missingTupleUri.getPath(), topic
                                        });
                            } catch (NonConformingAmmoContentProvider ex) {
                                ex.printStackTrace();
                            }
                    }
                    logger.error("no serialized data produced");
                    return null;
                }
            });

            final DistributorPolicy.Topic policy = that.policy().matchPostal(topic);
            final Dispersal dispersal = policy.makeRouteMap(channelFilter);
            {
                final Cursor channelCursor = this.store.queryDisposalByParent(
                        Relations.POSTAL.nominal, id);
                for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; moreChannels = channelCursor
                        .moveToNext())
                {
                    final String channel = channelCursor.getString(channelCursor
                            .getColumnIndex(DisposalTableSchema.CHANNEL.n));
                    final short channelState = channelCursor.getShort(channelCursor
                            .getColumnIndex(DisposalTableSchema.STATE.n));
                    dispersal.put(channel, DisposalState.getInstanceById(channelState));
                }
                logger.trace("prior channel states {}", dispersal);
                channelCursor.close();
            }
            // Dispatch the request.
            try {
                if (!that.isConnected()) {
                    logger.debug("no channel on postal");
                    continue;
                }
                synchronized (this.store) {
                    final ContentValues values = new ContentValues();

                    values.put(PostalTableSchema.DISPOSITION.cv(),
                            DisposalTotalState.DISTRIBUTE.cv());
                    long numUpdated = this.store.updatePostalByKey(id, values, null);
                    logger.debug("updated {} postal items", numUpdated);

                    final Dispersal dispatchResult =
                            this.dispatchPostalRequest(that, notice,
                                    uuid, topic,
                                    dispersal, serializer,
                                    new INetworkService.OnSendMessageHandler() {
                                        final DistributorThread parent = DistributorThread.this;
                                        final int id_ = id;
                                        final UUID uuid_ = uuid;
                                        final String auid_ = auid;
                                        final String topic_ = topic;
                                        final Notice notice_ = notice;

                                        @Override
                                        public boolean ack(String channel, DisposalState status) {
                                            final ChannelAck chack = new ChannelAck(
                                                    Relations.POSTAL, id_, uuid_,
                                                    topic_, auid_, notice_,
                                                    channel, status);
                                            return parent.announceChannelAck(chack);
                                        }
                                    });
                    this.store.updatePostalByKey(id, null, dispatchResult);
                }
            } catch (NullPointerException ex) {
                logger.warn("error posting message", ex);
            }
        }
        pending.close();
        logger.debug(MARK_POSTAL, "processed table POSTAL");
    }

    /**
     * dispatch the request to the network service. It is presumed that the
     * connection to the network service exists before this method is called.
     * 
     * @param that
     * @param provider
     * @param msgType
     * @param data
     * @param handler
     * @return
     */
    private Dispersal dispatchPostalRequest(final NetworkManager that,
            final Notice notice, final UUID uuid, final String msgType,
            final Dispersal dispersal, final RequestSerializer serializer,
            final INetworkService.OnSendMessageHandler handler)
    {
        logger.trace("::dispatchPostalRequest");

        final Long now = System.currentTimeMillis();
        logger.debug("Building MessageWrapper @ time {}", now);

        serializer.setAction(new RequestSerializer.OnReady() {
            @Override
            public AmmoGatewayMessage run(Encoding encode, ByteBufferAdapter serialized) {

                if (serialized == null) {
                    logger.error("No Payload");
                    return null;
                }

                final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper
                        .newBuilder();
                AmmoGatewayMessage.Builder agmb = null; 
                if (encode.getType() != Encoding.Type.TERSE) {
                    final AmmoMessages.DataMessage.Builder pushReq = AmmoMessages.DataMessage
                            .newBuilder()
                            .setUri(uuid.toString())
                            .setMimeType(msgType)
                            .setEncoding(encode.getType().name())
                            .setUserId(networkManager.getOperatorId())
                            .setOriginDevice(networkManager.getDeviceId());

                    if (notice != null) {
                        final AcknowledgementThresholds.Builder noticeBuilder = AcknowledgementThresholds
                                .newBuilder()
                                .setDeviceDelivered(notice.atDeviceDelivered.getVia().isActive())
                                .setAndroidPluginReceived(
                                        notice.atGatewayDelivered.getVia().isActive())
                                .setPluginDelivered(notice.atPluginDelivered.getVia().isActive());

                        pushReq.setThresholds(noticeBuilder);
                    }

                    mw.setType(AmmoMessages.MessageWrapper.MessageType.DATA_MESSAGE);
                    mw.setDataMessage(pushReq);
                    try {
                    	agmb = AmmoGatewayMessage.newBuilder(
                    			AmmoMessageSerializer.serialize(mw, serialized), handler);
                    } catch ( Exception e ) {
                    	throw new RuntimeException(e.getMessage(), e);
                    }
                } else {
                    final Integer mimeId = AmmoMimeTypes.mimeIds.get(msgType);
                    if (mimeId == null) {
                        logger.error("no integer mapping for this mime type {}", msgType);
                        return null;
                    }
                    final AmmoMessages.TerseMessage.Builder pushReq = AmmoMessages.TerseMessage
                            .newBuilder()
                            .setMimeType(mimeId)
                            .setData(ByteString.copyFrom(serialized.array()));
                    mw.setType(AmmoMessages.MessageWrapper.MessageType.TERSE_MESSAGE);
                    mw.setTerseMessage(pushReq);
                    agmb = AmmoGatewayMessage.newBuilder(mw.build().toByteArray(), handler);
                }

                logger.debug("Finished wrap build @ timeTaken {} ms, serialized-size={} \n",
                        System.currentTimeMillis() - now, serialized.limit());
                agmb.needAck( notice.atDeviceDelivered.getVia().isActive() ||
			      notice.atGatewayDelivered.getVia().isActive() ||
			      notice.atPluginDelivered.getVia().isActive() ) // does App need an Ack
                    .uuid( uuid );
                return agmb.build();
            }
        });
        return dispersal.multiplexRequest(that, serializer);
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
                DistributorThread.sendIntent(note.getVia().v, noticed, context);
            } else if (thresholds.getAndroidPluginReceived()) {
                note = notice.atGatewayDelivered;
                noticed = noteBuilder.buildGatewayDelivered(context);
                DistributorThread.sendIntent(note.getVia().v, noticed, context);
            } else if (thresholds.getPluginDelivered()) {
                note = notice.atPluginDelivered;
                noticed = noteBuilder.buildPluginDelivered(context);
                DistributorThread.sendIntent(note.getVia().v, noticed, context);
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

    // =========== RETRIEVAL ====================

    /**
     * Process the retrieval request. There are two parts: 1) checking to see if
     * the network service is accepting requests and sending the request if it
     * is 2) placing the request in the table. The second step must be done
     * first, so as to avoid a race to update the status of the request. The
     * handling of insert v. update is also handled here.
     * 
     * @param that
     * @param ar
     * @param st
     */
    private void doRetrievalRequest(NetworkManager that, AmmoRequest ar) {
        logger.trace("process request RETRIEVAL {} {}", ar.topic.toString(), ar.provider.toString());

        // Dispatch the message.
        try {
            final UUID uuid = UUID.randomUUID();
            final String auid = ar.uid;
            final String topic = ar.topic.asString();
            final String select = ar.select.toString();
            final Integer limit = (ar.limit == null) ? null : ar.limit.asInteger();
            final DistributorPolicy.Topic policy = that.policy().matchRetrieval(topic);

            final ContentValues values = new ContentValues();
            values.put(RetrievalTableSchema.UUID.cv(), uuid.toString());
            values.put(RetrievalTableSchema.AUID.cv(), auid);
            values.put(RetrievalTableSchema.TOPIC.cv(), topic);

            values.put(RetrievalTableSchema.SELECTION.cv(), select);
            if (limit != null)
                values.put(RetrievalTableSchema.LIMIT.cv(), limit);

            values.put(RetrievalTableSchema.PROVIDER.cv(), ar.provider.cv());
            values.put(RetrievalTableSchema.PRIORITY.cv(), policy.routing.getPriority(ar.priority));
            values.put(RetrievalTableSchema.EXPIRATION.cv(),
                    policy.routing.getExpiration(ar.expire.cv()));

            values.put(RetrievalTableSchema.UNIT.cv(), 50);
            values.put(RetrievalTableSchema.PRIORITY.cv(), ar.priority);
            values.put(RetrievalTableSchema.CREATED.cv(), System.currentTimeMillis());

            final Dispersal dispersal = policy.makeRouteMap(null);
            if (!that.isConnected()) {
                values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalTotalState.NEW.cv());
                final long key = this.store.upsertRetrieval(values, dispersal);
                logger.debug("no channel available, added retrieval [{}] [{}]", key, values);
                return;
            }

            values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
            // We synchronize on the store to avoid a race between dispatch and
            // queuing
            synchronized (this.store) {
                final long id = this.store.upsertRetrieval(values, policy.makeRouteMap(null));

                final Dispersal dispatchResult = this.dispatchRetrievalRequest(that,
                        uuid, topic, select, limit, dispersal,
                        new INetworkService.OnSendMessageHandler() {
                            final DistributorThread parent = DistributorThread.this;
                            final long id_ = id;
                            final UUID uuid_ = uuid;
                            final String auid_ = auid;
                            final String topic_ = topic;
                            final Notice notice_ = new Notice();

                            @Override
                            public boolean ack(String channel, DisposalState status) {
                                final ChannelAck chack = new ChannelAck(Relations.RETRIEVAL,
                                        id_, uuid_,
                                        topic_, auid_, notice_,
                                        channel, status);
                                return parent.announceChannelAck(chack);
                            }
                        });
                this.store.updateRetrievalByKey(id, null, dispatchResult);
            }

        } catch (NullPointerException ex) {
            logger.warn("sending to gateway failed", ex);
        }
    }

    private void cancelRetrievalRequest(final NetworkManager that, final AmmoRequest ar) {

        // Dispatch the message.
        try {
            // final UUID uuid = UUID.fromString(ar.uuid); //UUID.randomUUID();
            // final String auid = ar.uid;
            final String topic = ar.topic.asString();
            final String provider = ar.provider.cv();
            final String selection = new StringBuilder()
                    .append(RetrievalTableSchema.PROVIDER.q()).append("=?")
                    .append(" AND ")
                    .append(RetrievalTableSchema.TOPIC.q()).append("=?")
                    .toString();

            final String[] selectionArgs = new String[] {
                    provider, topic
            };

            this.store().deleteRetrieval(selection, selectionArgs);
        } finally {
        }
    }

    /**
     * Each time the enrollment provider is modified, find out what the changes
     * were and if necessary, update the server. Be careful about the race
     * condition; don't leave gaps in the time line. Originally this method used
     * time stamps to determine if the item had be sent. Now a status indicator
     * is used. Garbage collect items which are expired.
     */
    private void doRetrievalCache(NetworkManager that) {
        logger.debug(MARK_RETRIEVAL, "process table RETRIEVAL");

        final Cursor pending = this.store.queryRetrievalReady();
        if (pending == null)
            return;

        for (boolean areMoreItems = pending.moveToFirst(); areMoreItems; areMoreItems = pending
                .moveToNext()) {
            // For each item in the cursor, ask the content provider to
            // serialize it, then pass it off to the NPS.
            final int id = pending.getInt(pending.getColumnIndex(RetrievalTableSchema._ID.n));
            final String topic = pending.getString(pending
                    .getColumnIndex(RetrievalTableSchema.TOPIC.cv()));
            final DistributorPolicy.Topic policy = that.policy().matchRetrieval(topic);
            final Dispersal dispersal = policy.makeRouteMap(null);
            {
                final Cursor channelCursor = this.store.queryDisposalByParent(
                        Relations.RETRIEVAL.nominal, id);
                for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; moreChannels = channelCursor
                        .moveToNext()) {
                    final String channel = channelCursor.getString(channelCursor
                            .getColumnIndex(DisposalTableSchema.CHANNEL.n));
                    final short channelState = channelCursor.getShort(channelCursor
                            .getColumnIndex(DisposalTableSchema.STATE.n));
                    dispersal.put(channel, DisposalState.getInstanceById(channelState));
                }
                channelCursor.close();
            }

            final UUID uuid = UUID.fromString(pending.getString(pending
                    .getColumnIndex(RetrievalTableSchema.UUID.cv())));
            final String auid = pending.getString(pending.getColumnIndex(RetrievalTableSchema.AUID
                    .cv()));
            final String selection = pending.getString(pending
                    .getColumnIndex(RetrievalTableSchema.SELECTION.n));

            final int columnIx = pending.getColumnIndex(RetrievalTableSchema.LIMIT.n);
            final Integer limit = pending.isNull(columnIx) ? null : pending.getInt(columnIx);

            try {
                if (!that.isConnected()) {
                    logger.debug("no channel on retrieval");
                    continue;
                }
                synchronized (this.store) {
                    final ContentValues values = new ContentValues();

                    values.put(RetrievalTableSchema.DISPOSITION.cv(),
                            DisposalTotalState.DISTRIBUTE.cv());
                    @SuppressWarnings("unused")
                    final long numUpdated = this.store.updateRetrievalByKey(id, values, null);

                    final Dispersal dispatchResult = this.dispatchRetrievalRequest(that,
                            uuid, topic, selection, limit, dispersal,
                            new INetworkService.OnSendMessageHandler() {
                                final DistributorThread parent = DistributorThread.this;
                                final String auid_ = auid;
                                final UUID uuid_ = uuid;
                                final String topic_ = topic;
                                final Notice notice_ = new Notice();

                                @Override
                                public boolean ack(String channel, DisposalState status) {
                                    final ChannelAck chack = new ChannelAck(Relations.RETRIEVAL,
                                            id, uuid_,
                                            topic_, auid_, notice_,
                                            channel, status);
                                    return parent.announceChannelAck(chack);
                                }
                            });
                    this.store.updateRetrievalByKey(id, null, dispatchResult);
                }
            } catch (NullPointerException ex) {
                logger.warn("sending to gateway failed", ex);
            }
        }
        pending.close();
    }

    /**
     * The retrieval request is sent to
     * 
     * @param that
     * @param retrievalId
     * @param topic
     * @param selection
     * @param handler
     * @return
     */

    private Dispersal dispatchRetrievalRequest(final NetworkManager that,
            final UUID retrievalId, final String topic,
            final String selection, final Integer limit, final Dispersal dispersal,
            final INetworkService.OnSendMessageHandler handler)
    {
        logger.trace("dispatch request RETRIEVAL {}", topic);

        /** Message Building */

        // mw.setSessionUuid(sessionId);

        final AmmoMessages.PullRequest.Builder retrieveReq = AmmoMessages.PullRequest
                .newBuilder()
                .setRequestUid(retrievalId.toString())
                .setMimeType(topic);

        if (selection != null)
            retrieveReq.setQuery(selection);
        if (limit != null)
            retrieveReq.setMaxResults(limit);

        // projection
        // start_from_count
        // live_query
        // expiration
        try {
            final RequestSerializer serializer = RequestSerializer.newInstance();
            serializer.setReadyActor(new RequestSerializer.OnReady() {

                private AmmoMessages.PullRequest.Builder retrieveReq_ = retrieveReq;

                @Override
                public AmmoGatewayMessage run(Encoding encode, ByteBufferAdapter adapter) {
                    final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper
                            .newBuilder();
                    mw.setType(AmmoMessages.MessageWrapper.MessageType.PULL_REQUEST);
                    mw.setPullRequest(retrieveReq_);
                    final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(
                    		mw.build().toByteArray(), handler);
                    return agmb.build();
                }
            });
            return dispersal.multiplexRequest(that, serializer);
        } catch (com.google.protobuf.UninitializedMessageException ex) {
            logger.warn("Failed to marshal the message", ex);
        }
        return dispersal;

    }

    /**
     * Get response to RetrievalRequest, PullResponse, from the gateway.
     * 
     * @param mw
     * @return
     */
    private boolean receiveRetrievalResponse(Context context, AmmoMessages.MessageWrapper mw,
            NetChannel channel, int priority, ByteBufferAdapter payload) {
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
        final boolean queued = this.deserialThread.toProvider(priority, context, channel.name,
                provider, encoding, AmmoMessageSerializer.getDataBuffer(payload, resp.getData()));
        logger.debug("tuple upserted {}", queued);

        return true;
    }

    // =========== SUBSCRIBE ====================

    /**
     * Process the subscription request. There are two parts:
     * <ul>
     * <li>checking to see if the network service is accepting requests and
     * <li>sending the request if it is 2) placing the request in the table.
     * </ul>
     * The second step must be done first, so as to avoid a race to update the
     * status of the request. The handling of insert v. update is also handled
     * here.
     * 
     * @param that
     * @param agm
     * @param st
     */
    private void doSubscribeRequest(final NetworkManager that, final AmmoRequest ar, int st) {
        logger.trace("process request SUBSCRIBE {}", ar.topic.toString());

        // Dispatch the message.
        try {
            final UUID uuid = UUID.randomUUID();
            final String auid = ar.uid;
            final String topic = ar.topic.asString();
            final DistributorPolicy.Topic policy = that.policy().matchSubscribe(topic);

            final ContentValues values = new ContentValues();
            values.put(RetrievalTableSchema.UUID.cv(), uuid.toString());
            values.put(RetrievalTableSchema.AUID.cv(), auid);
            values.put(SubscribeTableSchema.TOPIC.cv(), topic);

            values.put(SubscribeTableSchema.PROVIDER.cv(), ar.provider.cv());
            values.put(SubscribeTableSchema.SELECTION.cv(), ar.select.toString());

            values.put(SubscribeTableSchema.PRIORITY.cv(), policy.routing.getPriority(ar.priority));
            values.put(SubscribeTableSchema.EXPIRATION.cv(),
                    policy.routing.getExpiration(ar.expire.cv()));
            values.put(SubscribeTableSchema.CREATED.cv(), System.currentTimeMillis());

            final Dispersal dispersal = policy.makeRouteMap(null);
            if (!that.isConnected()) {
                values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalTotalState.NEW.cv());
                long key = this.store.upsertSubscribe(values, dispersal);
                logger.debug("no channel available, added subscribe [{}] [{}]", key, values);
                return;
            }

            values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
            // We synchronize on the store to avoid a race between dispatch and
            // queuing
            synchronized (this.store) {
                final long id = this.store.upsertSubscribe(values, dispersal);
                final Dispersal dispatchResult = this.dispatchSubscribeRequest(that,
                        topic, ar.select.toString(), dispersal,
                        new INetworkService.OnSendMessageHandler() {
                            final DistributorThread parent = DistributorThread.this;
                            final long id_ = id;
                            final UUID uuid_ = uuid;
                            final String auid_ = auid;
                            final String topic_ = topic;
                            final Notice notice_ = new Notice();

                            @Override
                            public boolean ack(String channel, DisposalState status) {
                                final ChannelAck chack = new ChannelAck(Relations.SUBSCRIBE,
                                        id_, uuid_,
                                        topic_, auid_, notice_,
                                        channel, status);
                                return parent.announceChannelAck(chack);
                            }
                        });
                this.store.updateSubscribeByKey(id, null, dispatchResult);
            }

        } catch (NullPointerException ex) {
            logger.warn("sending to gateway failed", ex);
        }
    }

    private void cancelSubscribeRequest(final NetworkManager that, final AmmoRequest ar) {

        // Dispatch the message.
        try {
            // final UUID uuid = UUID.fromString(ar.uuid); //UUID.randomUUID();
            // final String auid = ar.uid;
            final String topic = ar.topic.asString();
            final String provider = ar.provider.cv();
            final String selection = new StringBuilder()
                    .append(SubscribeTableSchema.PROVIDER.q()).append("=?")
                    .append(" AND ")
                    .append(SubscribeTableSchema.TOPIC.q()).append("=?")
                    .toString();
            final String[] selectionArgs = new String[] {
                    provider, topic
            };

            this.store().deleteSubscribe(selection, selectionArgs);
        } finally {

        }

    }

    /**
     * Each time the subscription provider is modified, find out what the
     * changes were and if necessary, send the data to the NetworkManager. Be
     * careful about the race condition; don't leave gaps in the time line.
     * Originally this method used time stamps to determine if the item had be
     * sent. Now a status indicator is used. Garbage collect items which are
     * expired.
     */

    private void doSubscribeCache(NetworkManager that) {
        logger.debug(MARK_SUBSCRIBE, "process table SUBSCRIBE");

        final Cursor pending = this.store.querySubscribeReady();
        if (pending == null)
            return;

        for (boolean areMoreItems = pending.moveToFirst(); areMoreItems; areMoreItems = pending
                .moveToNext()) {
            // For each item in the cursor, ask the content provider to
            // serialize it, then pass it off to the NPS.
            final int id = pending.getInt(pending.getColumnIndex(SubscribeTableSchema._ID.n));
            final String uuidString = pending.getString(pending
                    .getColumnIndex(PostalTableSchema.UUID.n));
            final UUID uuid = UUID.fromString(uuidString);

            final String topic = pending.getString(pending
                    .getColumnIndex(SubscribeTableSchema.TOPIC.cv()));
            final String auid = pending.getString(pending.getColumnIndex(SubscribeTableSchema.AUID
                    .cv()));

            final String selection = pending.getString(pending
                    .getColumnIndex(SubscribeTableSchema.SELECTION.n));

            logger.trace(MARK_SUBSCRIBE, "process row SUBSCRIBE {} {} {}", new Object[] {
                    id, topic, selection
            });

            final DistributorPolicy.Topic policy = that.policy().matchSubscribe(topic);
            final Dispersal dispersal = policy.makeRouteMap(null);
            {
                final Cursor channelCursor = this.store.queryDisposalByParent(
                        Relations.SUBSCRIBE.nominal, id);
                for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; moreChannels = channelCursor
                        .moveToNext()) {
                    final String channel = channelCursor.getString(channelCursor
                            .getColumnIndex(DisposalTableSchema.CHANNEL.n));
                    final short channelState = channelCursor.getShort(channelCursor
                            .getColumnIndex(DisposalTableSchema.STATE.n));
                    dispersal.put(channel, DisposalState.getInstanceById(channelState));
                }
                channelCursor.close();
            }

            try {
                if (!that.isConnected()) {
                    logger.debug("no channel on subscribe");
                    continue;
                }
                synchronized (this.store) {
                    final ContentValues values = new ContentValues();

                    values.put(SubscribeTableSchema.DISPOSITION.cv(),
                            DisposalTotalState.DISTRIBUTE.cv());
                    @SuppressWarnings("unused")
                    long numUpdated = this.store.updateSubscribeByKey(id, values, null);

                    final Dispersal dispatchResult = this.dispatchSubscribeRequest(that,
                            topic, selection, dispersal,
                            new INetworkService.OnSendMessageHandler() {
                                final DistributorThread parent = DistributorThread.this;
                                final int id_ = id;
                                final UUID uuid_ = uuid;
                                final String auid_ = auid;
                                final String topic_ = topic;
                                final Notice notice_ = new Notice();

                                @Override
                                public boolean ack(String channel, DisposalState status) {
                                    final ChannelAck chack = new ChannelAck(Relations.SUBSCRIBE,
                                            id_, uuid_,
                                            topic_, auid_, notice_,
                                            channel, status);
                                    return parent.announceChannelAck(chack);
                                }
                            });
                    this.store.updateSubscribeByKey(id, null, dispatchResult);
                }
            } catch (NullPointerException ex) {
                logger.warn("sending to gateway failed", ex);
            }
        }
        pending.close();
    }

    /**
     * Deliver the subscription request to the network service for processing.
     */
    private Dispersal dispatchSubscribeRequest(final NetworkManager that,
            final String topic, final String selection, final Dispersal dispersal,
            final INetworkService.OnSendMessageHandler handler)
    {
        logger.trace("::dispatchSubscribeRequest {}", topic);

        /** Message Building */

        final AmmoMessages.SubscribeMessage.Builder subscribeReq = AmmoMessages.SubscribeMessage
                .newBuilder();
        subscribeReq.setMimeType(topic);
        subscribeReq
                .setOriginDevice(this.networkManager.getDeviceId())
                .setOriginUser(this.networkManager.getOperatorId());

        if (subscribeReq != null)
            subscribeReq.setQuery(selection);

        final RequestSerializer serializer = RequestSerializer.newInstance();
        serializer.setAction(new RequestSerializer.OnReady() {

            final AmmoMessages.SubscribeMessage.Builder subscribeReq_ = subscribeReq;

            @Override
            public AmmoGatewayMessage run(Encoding encode, ByteBufferAdapter serialized) {
                final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper
                        .newBuilder();
                mw.setType(AmmoMessages.MessageWrapper.MessageType.SUBSCRIBE_MESSAGE);
                mw.setSubscribeMessage(subscribeReq_);
                final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(
                		mw.build().toByteArray(), handler);
                return agmb.build();
            }
        });
        return dispersal.multiplexRequest(that, serializer);
    }

    /**
     * Update the content providers as appropriate. These are typically received
     * in response to subscriptions. In other words these replies are postal
     * messages which have been redirected. The subscribing uri isn't sent with
     * the subscription to the gateway therefore it needs to be recovered from
     * the subscription table.
     */
    private boolean receiveSubscribeResponse(Context context, AmmoMessages.MessageWrapper mw,
            NetChannel channel, int priority, ByteBufferAdapter payload) {
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

        final String selfDevice = networkManager.getDeviceId();
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

        final String selfOperator = networkManager.getOperatorId();
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
                            .setAcknowledgingDevice(networkManager.getDeviceId())
                            .setAcknowledgingUser(networkManager.getOperatorId())
                            .setStatus(PushStatus.RECEIVED);

            final AmmoMessages.MessageWrapper.Builder mwb = AmmoMessages.MessageWrapper
                    .newBuilder()
                    .setType(AmmoMessages.MessageWrapper.MessageType.PUSH_ACKNOWLEDGEMENT)
                    .setPushAcknowledgement(pushAck);

            final AmmoGatewayMessage.Builder oagmb = AmmoGatewayMessage.newBuilder(
            		mwb.build().toByteArray(),
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

/*
        final Encoding encoding = Encoding.getInstanceByName(encode);        
        this.deserialThread.toProvider(priority, context, channel.name, provider, encoding,
        		AmmoMessageSerializer.getDataBuffer(payload, data));
*/
        
        final DistributorPolicy policy = this.networkManager.policy();
        final DistributorPolicy.Topic topicPolicy = policy.matchPostal(topic);
        
        final Encoding encoding = Encoding.getInstanceByName(encode);
      
       this.requestDeserializerBuilder = RequestDeserializer.newBuilder(this, this.ammoAdaptorCache);
       
       final RequestDeserializer deserializeCommand = 
               this.requestDeserializerBuilder.toProvider(priority, context, channel.name,
               provider, encoding, data.toByteArray());
       this.deserializeExecutor.execute(deserializeCommand);
     
        if(topicPolicy.getRouted() == true) {
            this.deserializeExecutor.execute(deserializeCommand.toReroute());
        }
        
        logger.info("Ammo received message on topic: {} for provider: {}", mime, uriString);

        return true;
    }

    /**
     * Clear the contents of tables in preparation for reloading them. This is
     * *not* for postal which should persist.
     */
    public void clearTables() {
        this.store.purgeRetrieval();
        this.store.purgeSubscribe();
    }

    // =============== UTILITY METHODS ======================== //
    
}

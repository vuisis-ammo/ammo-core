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

import android.os.Debug;

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

import net.jcip.annotations.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.preference.PreferenceManager;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.AmmoMimeTypes;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.AmmoService.ChannelChange;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTotalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SerializeType;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.ethertracker.EthTrackSvc;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.MessageWrapper.MessageType;
import edu.vu.isis.ammo.core.ui.AmmoCore;

/**
 * The distributor service runs in the ui thread. This establishes a new thread
 * for distributing the requests.
 * 
 */
@ThreadSafe
    public class DistributorThread extends Thread {
	// ===========================================================
	// Constants
	// ===========================================================
	private static final Logger logger = LoggerFactory.getLogger("dist.thread");
	private static final boolean RUN_TRACE = false;

	private static final Marker MARK_POSTAL = MarkerFactory.getMarker("postal");
	private static final Marker MARK_RETRIEVAL = MarkerFactory.getMarker("retrieval");
	private static final Marker MARK_SUBSCRIBE = MarkerFactory.getMarker("subscribe");
	
	private static final String ACTION_BASE = "edu.vu.isis.ammo.";
	public static final String ACTION_MSG_SENT = ACTION_BASE+"ACTION_MESSAGE_SENT";
	public static final String ACTION_MSG_RCVD = ACTION_BASE+"ACTION_MESSAGE_RECEIVED";
	public static final String EXTRA_TOPIC = "topic";
	public static final String EXTRA_UID = "uid";
	public static final String EXTRA_CHANNEL = "channel";
	public static final String EXTRA_STATUS = "status";

	// 20 seconds expressed in milliseconds
	private static final int BURP_TIME = 20 * 1000;

	private final Context context;
    private final AmmoService ammoService;
	/**
	 * The backing store for the distributor
	 */
	final private DistributorDataStore store;
	
	private static final int SERIAL_NOTIFY_ID = 1;
    private static final int IP_NOTIFY_ID = 2;
    
    private int current_icon_id = 1;
    private int current_icon = 0;
    
    private AtomicInteger total_sent = new AtomicInteger (0);
    private AtomicInteger total_recv = new AtomicInteger (0);
    
    private NotifyMsgNumber notify = null;

    public DistributorThread(final Context context, AmmoService parent) {
		super();
		this.context = context;
		this.ammoService = parent;
		this.requestQueue = new LinkedBlockingQueue<AmmoRequest>(200);
		this.responseQueue = new PriorityBlockingQueue<AmmoGatewayMessage>(200, new AmmoGatewayMessage.PriorityOrder());
		this.store = new DistributorDataStore(context);
		
		this.channelStatus = new ConcurrentHashMap<String, ChannelStatus>();
		this.channelDelta = new AtomicBoolean(true);
		
		this.channelAck = new LinkedBlockingQueue<ChannelAck>(200);
		logger.debug("constructed");
	}
    
    private class NotifyMsgNumber implements Runnable {
        
        private DistributorThread parent = null;
        
        private int last_sent_count = 0;
        private int last_recv_count = 0;
        
        public NotifyMsgNumber (DistributorThread parent) {
            this.parent = parent;
        }
        
        private AtomicBoolean terminate = new AtomicBoolean (false);
        
        public void terminate () {
            terminate.set(true);
        }
        
        public void run () {
            if (terminate.get() != true)
                updateNotification ();
        }

        private void updateNotification () {

            //check for variable update ... 
            int total_sent = parent.total_sent.get();
            int total_recv = parent.total_recv.get();
            
            int sent = total_sent - last_sent_count;
            int recv = total_recv - last_recv_count;
            
            int icon = 0;
            //figure out the icon ... 
            if (sent == 0 && recv == 0)
                icon = R.drawable.nodata;
            else if (sent > 0 && recv ==0)
                icon = R.drawable.up;
            else if (sent == 0 && recv > 0)
                icon = R.drawable.down;
            else if (sent > 0 && recv > 0)
                icon = R.drawable.alldata;

            String contentText = "Sent " + total_sent + " Received " + total_recv;
            
            parent.notifyIcon("", "Data Channel", contentText, icon);
            
            //save the last sent and recv ...
            last_sent_count = total_sent;
            last_recv_count = total_recv;
            
            parent.ammoService.notifyMsg.postDelayed(this, 30000);
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
	public void onChannelChange(final Context context, final String channelName, final ChannelChange change) {
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
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = 
                (NotificationManager) context.getSystemService(ns);

        if (change == ChannelChange.DEACTIVATE)
        {
            for (Entry<String, ChannelStatus> entry : channelStatus.entrySet()) {
                if (entry.getValue().change == ChannelChange.ACTIVATE) {
                    return; // leave, since at least one channel is still active 
                }
            }
            
            // none of the channels are active ...
            if (notify != null) {
                this.ammoService.notifyMsg.removeCallbacks(notify);
                notify.terminate ();
                notify = null;                
            }
            mNotificationManager.cancel(current_icon_id);
            return;
        }
        
        int icon;
        
        if (channelName.equals("serial"))
        {
//            current_icon = R.drawable.notify_icon_152_small;
            
            // right now using the same icon ... once we get new icons, replace this .. 
            icon = R.drawable.alldata;
            current_icon_id = SERIAL_NOTIFY_ID;
        }
        else
        {
//            current_icon = R.drawable.notify_icon_wr_small;
            icon = R.drawable.alldata;
            current_icon_id = IP_NOTIFY_ID;
        }
        
        notifyIcon(channelName + " Channel Up", "Data Channel", "Online", icon);
       
        
        if (notify == null) {
            notify = new NotifyMsgNumber (this);
            this.ammoService.notifyMsg.postDelayed(notify, 15000);
        }
    }

	
	private void notifyIcon (String tickerTxt,
	        String contentTitle, 
	        String contentText,
	        int icon) 
	{    
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
		public final Tables type;
		public final long id;
		
		public final String topic;
		public final String auid;
		
		public final String channel;
		public final DisposalState status;

		public ChannelAck(Tables type, long id, 
				          String topic, String auid, 
				          String channel, DisposalState status) 
		{
			this.type = type;
			this.id = id;
			
			this.topic = topic;
			this.auid = auid;
			
			this.channel = channel;
			this.status = status;
		}

		@Override
		public String toString() {
			return new StringBuilder()
			                .append(" type ").append(type)
                            .append(" id ").append(id)
                            .append(" topic ").append(topic)
                            .append(" aid ").append(auid)
                            .append(" channel ").append(channel)
                            .append(" status ").append(status)
                            .toString();
		}
	}

	/**
	 * Called by the channel acknowledgement once the channel has
	 * attempted to send the message.
	 * 
	 * @param ack
	 * @return
	 */
	private boolean announceChannelAck(ChannelAck ack) {
		logger.trace("RECV ACK {}", ack);
		try {
			if (!this.channelAck.offer(ack, 2, TimeUnit.SECONDS)) {
				logger.warn("announcing channel ack queue is full");
				return false;
			}
		} catch (InterruptedException ex) {
			logger.warn("announcing channel ack was interrupted");
			return false;
		}
		this.signal();
		
        if (ack.status == DisposalState.SENT)//update recv count and send notify
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
		final Intent notice = new Intent()
		      .setAction(ACTION_MSG_SENT)
		      /*
		      .setType(ack.topic)
		       ... or ...
		      .setData(Uri.Builder()
		    		  .scheme("ammo")
		    		  .authority(ack.topic)
		    		  //.path(ack.target)
		    		  .build())
		      */
		      .putExtra(EXTRA_TOPIC, ack.topic.toString())
		      .putExtra(EXTRA_UID, ack.auid.toString())
		      .putExtra(EXTRA_CHANNEL, ack.channel.toString())
		      .putExtra(EXTRA_STATUS, ack.status.toString());
		      
		context.sendBroadcast(notice);
		context.startService(notice); // TBD SKN - WHY DO WE DO THIS???
	
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

			if (! this.requestQueue.offer(request, 1, TimeUnit.SECONDS)) {
				logger.error("could not process request {}", request);
				this.signal();
				return null;
			}
			this.signal();
			return request.uuid;

		} catch (InterruptedException ex) {
			logger.error("Exception while distributing request {}", ex.getStackTrace());
		}
		return null;
	}

	/**
	 * Contains gateway responses
	 */
	private final PriorityBlockingQueue<AmmoGatewayMessage> responseQueue;

	public boolean distributeResponse(AmmoGatewayMessage agm) {
		if (! this.responseQueue.offer(agm, 1, TimeUnit.SECONDS)) {
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
	 * distributor.
	 * 
	 * The method tries to be fair processing the requests in
	 */
	@Override
	    public void run()
    {
	Process.setThreadPriority( -6 ); // Process.THREAD_PRIORITY_FOREGROUND(-2) and THREAD_PRIORITY_DEFAULT(0) 
	logger.info("distributor thread start @prio: {}", Process.getThreadPriority( Process.myTid() ) );

	if (ammoService.isConnected()) {

	    for (final Map.Entry<String, ChannelStatus> entry : channelStatus.entrySet()) {
		final String name = entry.getKey();
		this.store.deactivateDisposalStateByChannel(name);
	    }

	    this.doSubscribeCache(ammoService);
	    this.doRetrievalCache(ammoService);
	    this.doPostalCache(ammoService);
	}


	try {
	    while (true) {
		// condition wait, is there something to process?
		synchronized (this) {
		    while (!this.isReady())
			this.wait(BURP_TIME);
		}
		while (this.isReady()) {
		    if (this.channelDelta.getAndSet(false)) {
			logger.trace("channel change");
			this.doChannelChange(ammoService);
		    }

		    if (!this.channelAck.isEmpty()) {
			logger.trace("processing channel acks, remaining {}", this.channelAck.size());
			try {
			    final ChannelAck ack = this.channelAck.take();
			    this.doChannelAck(this.context, ack);
			} catch (ClassCastException ex) {
			    logger.error("channel ack queue contains illegal item of class {}", ex.getLocalizedMessage());
			}
		    }

		    if (!this.responseQueue.isEmpty()) {
			try {
			    final AmmoGatewayMessage agm = this.responseQueue.take();
			    logger.info("processing response {}, recvd @{}, remaining {}", new Object[]{agm.payload_checksum, agm.buildTime, this.responseQueue.size()} );
			    this.doResponse(ammoService, agm);
			} catch (ClassCastException ex) {
			    logger.error("response queue contains illegal item of class {}", ex.getLocalizedMessage());
			}
		    }

		    if (!this.requestQueue.isEmpty()) {
			try {
			    final AmmoRequest agm = this.requestQueue.take();
			    logger.info("processing request uuid {}, remaining {}", agm.uuid, this.requestQueue.size());
			    this.doRequest(ammoService, agm);
			} catch (ClassCastException ex) {
			    logger.error("request queue contains illegal item of class {}", ex.getLocalizedMessage());
			}
		    }
		}
		logger.trace("work processed");
	    }
	} catch (InterruptedException ex) {
	    logger.warn("task interrupted {}", ex.getStackTrace());
	}
	return;
    }


	// ================= DRIVER METHODS ==================== //

	/**
	 * Processes and delivers messages received from the gateway. - Verify the
	 * check sum for the payload is correct - Parse the payload into a message -
	 * Receive the message
	 * 
	 * @param instream
	 * @return was the message clean (true) or garbled (false).
	 */
	private boolean doRequest(AmmoService that, AmmoRequest agm) {
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
		case RETRIEVAL:
			doRetrievalRequest(that, agm);
			break;
		case SUBSCRIBE:
			doSubscribeRequest(that, agm, 1);
			break;
		case DIRECTED_SUBSCRIBE:
			doSubscribeRequest(that, agm, 2);
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
	private void doChannelChange(AmmoService that) {
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
				this.announceChannelActive(that.getBaseContext(), name);
				logger.trace("::channel activated");
				break;

			case REPAIR:
				this.store.upsertChannelByName(name, ChannelState.ACTIVE);
				this.store.activateDisposalStateByChannel(name);

				this.store.repairDisposalStateByChannel(name);
				this.announceChannelActive(that.getBaseContext(), name);
				logger.trace("::channel repaired");
				break;

			default:
				logger.trace("::channel unknown change {}", change);
			}
		}

		// we could do a priming query to determine if there are any candidates

		this.store.deletePostalGarbage();
		this.store.deleteRetrievalGarbage();
		this.store.deleteSubscribeGarbage();

		this.doPostalCache(that);
		this.doRetrievalCache(that);
		this.doSubscribeCache(that);
	}

	/**
	 * Processes and delivers messages received from the gateway. - Verify the
	 * check sum for the payload is correct - Parse the payload into a message -
	 * Receive the message
	 * 
	 * @param instream
	 * @return was the message clean (true) or garbled (false).
	 */
	private boolean doResponse(Context context, AmmoGatewayMessage agm) {
	    logger.trace("process response");

        if ( !agm.hasValidChecksum() ) {
            // If this message came from the serial channel, let it know that
            // a corrupt message occured, so it can update its stats.
            // Make this a more general mechanism later on.
            if ( agm.isSerialChannel )
                ammoService.receivedCorruptPacketOnSerialChannel();

            return false;
        }
        
        total_recv.incrementAndGet();

		final AmmoMessages.MessageWrapper mw;
		try {
			mw = AmmoMessages.MessageWrapper.parseFrom(agm.payload);
		} catch (InvalidProtocolBufferException ex) {
			logger.error("parsing gateway message {}", ex.getStackTrace());
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
			final boolean subscribeResult = receiveSubscribeResponse(context, mw);
			logger.debug("subscribe reply {}", subscribeResult);
			break;

		case AUTHENTICATION_RESULT:
			final boolean result = receiveAuthenticateResponse(context, mw);
			logger.debug("authentication result={}", result);
			break;

		case PUSH_ACKNOWLEDGEMENT:
			final boolean postalResult = receivePostalResponse(context, mw);
			logger.debug("post acknowledgement {}", postalResult);
			break;

		case PULL_RESPONSE:
			final boolean retrieveResult = receiveRetrievalResponse(context, mw);
			logger.debug("retrieve response {}", retrieveResult);
			break;

		case AUTHENTICATION_MESSAGE:
		case SUBSCRIBE_MESSAGE:
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
	 * Give to the network service for verification.
	 * 
	 * Get the session id set by the gateway.
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
	private void doPostalRequest(final AmmoService that, final AmmoRequest ar) {

		// Dispatch the message.
		try {
		    final UUID uuid = UUID.fromString(ar.uuid); //UUID.randomUUID();
			final String auid = ar.uid;
			final String topic = ar.topic.asString();
			final DistributorPolicy.Topic policy = that.policy().matchPostal(topic);

			logger.trace("process request topic {}, uuid {}", ar.topic, uuid);

			final ContentValues values = new ContentValues();
			values.put(PostalTableSchema.UUID.cv(), uuid.toString());
			values.put(PostalTableSchema.AUID.cv(), auid);
			values.put(PostalTableSchema.TOPIC.cv(), topic);
			values.put(PostalTableSchema.PROVIDER.cv(), ar.provider.cv());
			
			values.put(PostalTableSchema.PRIORITY.cv(), policy.routing.priority+ar.priority);
			values.put(PostalTableSchema.EXPIRATION.cv(), ar.expire.cv());
			values.put(PostalTableSchema.CREATED.cv(), System.currentTimeMillis());
			
			values.put(PostalTableSchema.ORDER.cv(), ar.order.cv());

			// values.put(PostalTableSchema.UNIT.cv(), 50);

			final DistributorState dispersal = policy.makeRouteMap();
			if (!that.isConnected()) {
				values.put(PostalTableSchema.DISPOSITION.cv(), DisposalTotalState.NEW.cv());
				long key = this.store.upsertPostal(values, policy.makeRouteMap());
				logger.debug("no network connection, added {}", key);
				return;
			}

			final RequestSerializer serializer = RequestSerializer.newInstance(ar.provider, ar.payload);
			serializer.setSerializer(new RequestSerializer.OnSerialize() {

				final RequestSerializer serializer_ = serializer;
				final AmmoService that_ = that;
				final DistributorThread parent = DistributorThread.this;
				
				@Override
				public byte[] run(Encoding encode) {
					if (serializer_.payload.hasContent()) {
						return serializer_.payload.asBytes();
					} else {
						try {
							final byte[] result = RequestSerializer.serializeFromProvider(that_.getContentResolver(), serializer_.provider.asUri(), encode);

							if (result == null) {
								logger.error("Null result from serialize {} {} ", serializer_.provider, encode);
							}
							return result;
						} catch (IOException e1) {
							logger.error("invalid row for serialization {}", e1.getLocalizedMessage());
							return null;
						} catch (TupleNotFoundException e) {
							logger.error("tuple not found when processing postal table");
							parent.store().deletePostal(new StringBuilder().append(PostalTableSchema.PROVIDER.q()).append("=?").append(" AND ")
									.append(PostalTableSchema.TOPIC.q()).append("=?").toString(), new String[] {e.missingTupleUri.getPath(), topic});
							return null;
						} catch (NonConformingAmmoContentProvider e) {
							e.printStackTrace();
							return null;
						}
					}
				}
			});

			values.put(PostalTableSchema.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
			// We synchronize on the store to avoid a race between dispatch and
			// queuing
			synchronized (this.store) {
			    final long id = this.store.upsertPostal(values, policy.makeRouteMap());

			    final DistributorState dispatchResult = this.dispatchPostalRequest(that, 
						ar.provider.toString(), topic, dispersal, serializer, 
						new INetworkService.OnSendMessageHandler() {
							final DistributorThread parent = DistributorThread.this;
							final long id_ = id;
							final String topic_ = topic;
							final String auid_ = ar.uid;
		
							@Override
							public boolean ack(String channel, DisposalState status) {
								return parent.announceChannelAck(new ChannelAck(Tables.POSTAL, id_, 
										                                        topic_, auid_,  
										                                        channel, status));
							}
						});

			    this.store.updatePostalByKey(id, null, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("NullPointerException, sending to gateway failed {}", ex.getStackTrace());
		}
	}

	/**
	 * Check for requests whose delivery policy has not been fully satisfied and
	 * for which there is, now, an available channel.
	 */
	private void doPostalCache(final AmmoService that) {
		logger.debug(MARK_POSTAL, "process table POSTAL");

		if (!that.isConnected()) 
			return;

		final Cursor pending = this.store.queryPostalReady();
		if (pending == null) return;

		// Iterate over each row serializing its data and sending it.
		for (boolean moreItems = pending.moveToFirst(); moreItems; 
				moreItems = pending.moveToNext()) 
		{
			final int id = pending.getInt(pending.getColumnIndex(PostalTableSchema._ID.n));
			final String auid = pending.getString(pending.getColumnIndex(PostalTableSchema.AUID.n));
			final Provider provider = new Provider(pending.getString(pending.getColumnIndex(PostalTableSchema.PROVIDER.n)));
			final Payload payload = new Payload(pending.getString(pending.getColumnIndex(PostalTableSchema.PAYLOAD.n)));
			final String topic = pending.getString(pending.getColumnIndex(PostalTableSchema.TOPIC.n));

			logger.debug("serializing: {} as {}", provider,topic);

			final RequestSerializer serializer = RequestSerializer.newInstance(provider, payload);
			final int serialType = pending.getInt(pending.getColumnIndex(PostalTableSchema.ORDER.n));
			int dataColumnIndex = pending.getColumnIndex(PostalTableSchema.DATA.n);

			final String data = (pending.isNull(dataColumnIndex)) ? null : pending.getString(dataColumnIndex);
			
			serializer.setSerializer( new RequestSerializer.OnSerialize() {
				final DistributorThread parent = DistributorThread.this;
				final RequestSerializer serializer_ = serializer;
				final AmmoService that_ = that;
				final int serialType_ = serialType;
				final String data_ = data;

				@Override
				public byte[] run(Encoding encode) {
					switch (SerializeType.getInstance(serialType_)) {
					case DIRECT:
                        return (data_.length() > 0) ? data_.getBytes() : null;

					case INDIRECT:
					case DEFERRED:
					default:
						try {
							return RequestSerializer.serializeFromProvider(that_.getContentResolver(), 
									serializer_.provider.asUri(), encode);
						} catch (IOException e1) {
							logger.error("invalid row for serialization");
						} catch (TupleNotFoundException ex) {
							logger.error("tuple not found when processing postal table");
							parent.store().deletePostal(new StringBuilder()
							        .append(PostalTableSchema.PROVIDER.q()).append("=?").append(" AND ")
									.append(PostalTableSchema.TOPIC.q()).append("=?").toString(), 
									new String[] {ex.missingTupleUri.getPath(), topic});
						} catch (NonConformingAmmoContentProvider ex) {
							ex.printStackTrace();
						}
					}
					logger.error("no serialized data produced");
					return null;
				}
			});

			final DistributorPolicy.Topic policy = that.policy().matchPostal(topic);
			final DistributorState dispersal = policy.makeRouteMap();
			{
				final Cursor channelCursor = this.store.queryDisposalByParent(Tables.POSTAL.o, id);
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; 
						moreChannels = channelCursor.moveToNext()) 
				{
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short channelState = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					dispersal.put(channel, DisposalState.getInstanceById(channelState));
				}
				logger.trace("prior channel states {}", dispersal);
				channelCursor.close();
			}
			// Dispatch the request.
			try {
				if (!that.isConnected()) {
					logger.debug("no network connection while processing table");
					continue;
				} 
				synchronized (this.store) {
					final ContentValues values = new ContentValues();

					values.put(PostalTableSchema.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
					long numUpdated = this.store.updatePostalByKey(id, values, null);
					logger.debug("updated {} postal items", numUpdated);

					final DistributorState dispatchResult = 
							this.dispatchPostalRequest(that,
									provider.toString(), topic, 
									dispersal, serializer,
									new INetworkService.OnSendMessageHandler() {
										final DistributorThread parent = DistributorThread.this;
		                                final int id_ = id;
		                                final String auid_ = auid;
		                                final String topic_ = topic;
		                                
										@Override
										public boolean ack(String channel, DisposalState status) {
											return parent.announceChannelAck( new ChannelAck(Tables.POSTAL, id_, 
													                                         topic_, auid_, 
													                                         channel, status) );
										}
									});
					this.store.updatePostalByKey(id, null, dispatchResult);
				}
			} catch (NullPointerException ex) {
				logger.warn("error posting message {}", ex.getStackTrace());
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
	private DistributorState dispatchPostalRequest(final AmmoService that, 
			final String provider, final String msgType, 
			final DistributorState dispersal, final RequestSerializer serializer, 
			final INetworkService.OnSendMessageHandler handler) 
	{
		logger.trace("::dispatchPostalRequest");

		final Long now = System.currentTimeMillis();
		logger.debug("Building MessageWrapper @ time {}", now);

		serializer.setAction(new RequestSerializer.OnReady() {
			@Override
			public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {

				if (serialized == null) {
					logger.error("No Payload");
					return null;
				}
				
				final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
				if (encode.getType() != Encoding.Type.TERSE) {
					final AmmoMessages.DataMessage.Builder pushReq = AmmoMessages.DataMessage
						.newBuilder()
						.setUri(provider)
						.setMimeType(msgType)
						.setEncoding(encode.getType().name())
					        .setUserId(ammoService.getOperatorId())
						.setData(ByteString.copyFrom(serialized));
					mw.setType(AmmoMessages.MessageWrapper.MessageType.DATA_MESSAGE);
					mw.setDataMessage(pushReq);

				} else {
					final Integer mimeId = AmmoMimeTypes.mimeIds.get(msgType);
					if (mimeId == null) {
						logger.error("no integer mapping for this mime type {}", msgType);
						return null;
					}
					final AmmoMessages.TerseMessage.Builder pushReq = AmmoMessages.TerseMessage
							.newBuilder()
							.setMimeType(mimeId)
					                .setUserId(ammoService.getOperatorId())
							.setData(ByteString.copyFrom(serialized));
						mw.setType(AmmoMessages.MessageWrapper.MessageType.TERSE_MESSAGE);
						mw.setTerseMessage(pushReq);
				}

				logger.debug("Finished wrap build @ timeTaken {} ms, serialized-size={} \n", System.currentTimeMillis() - now, serialized.length);
				final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(mw, handler);
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
	private boolean receivePostalResponse(Context context, AmmoMessages.MessageWrapper mw) {
		logger.trace("receive response POSTAL");

		if (mw == null)
			return false;
		if (!mw.hasPushAcknowledgement())
			return false;
		// PushAcknowledgement pushResp = mw.getPushAcknowledgement();
		return true;
	}

	// =========== RETRIEVAL ====================

	/**
	 * Process the retrieval request. There are two parts: 1) checking to see if
	 * the network service is accepting requests and sending the request if it
	 * is 2) placing the request in the table.
	 * 
	 * The second step must be done first, so as to avoid a race to update the
	 * status of the request. The handling of insert v. update is also handled
	 * here.
	 * 
	 * @param that
	 * @param ar
	 * @param st
	 */
	private void doRetrievalRequest(AmmoService that, AmmoRequest ar) {
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
			values.put(RetrievalTableSchema.PRIORITY.cv(), ar.priority);
			values.put(RetrievalTableSchema.EXPIRATION.cv(), ar.expire.cv());
			values.put(RetrievalTableSchema.UNIT.cv(), 50);
			values.put(RetrievalTableSchema.PRIORITY.cv(), ar.priority);
			values.put(RetrievalTableSchema.CREATED.cv(), System.currentTimeMillis());

			final DistributorState dispersal = policy.makeRouteMap();
			if (!that.isConnected()) {
				values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalTotalState.NEW.cv());
				this.store.upsertRetrieval(values, dispersal);
				logger.debug("no network connection");
				return;
			}

			values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
			// We synchronize on the store to avoid a race between dispatch and
			// queuing
			synchronized (this.store) {
				final long id = this.store.upsertRetrieval(values, policy.makeRouteMap());

				final DistributorState dispatchResult = this.dispatchRetrievalRequest(that, 
						uuid, topic, select, limit, dispersal, 
						new INetworkService.OnSendMessageHandler() {
							final DistributorThread parent = DistributorThread.this;
							final long id_ = id;
							final String auid_ = auid;
							final String topic_ = topic;
		
							@Override
							public boolean ack(String channel, DisposalState status) {
								return parent.announceChannelAck(new ChannelAck(Tables.RETRIEVAL, id_, 
										                                        topic_, auid_,  
										                                        channel, status));
							}
						});
				this.store.updateRetrievalByKey(id, null, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("NullPointerException, sending to gateway failed {}", ex.getStackTrace());
		}
	}

	/**
	 * Each time the enrollment provider is modified, find out what the changes
	 * were and if necessary, update the server.
	 * 
	 * Be careful about the race condition; don't leave gaps in the time line.
	 * Originally this method used time stamps to determine if the item had be
	 * sent. Now a status indicator is used.
	 * 
	 * Garbage collect items which are expired.
	 */
	private void doRetrievalCache(AmmoService that) {
		logger.debug(MARK_RETRIEVAL, "process table RETRIEVAL");

		final Cursor pending = this.store.queryRetrievalReady();
        if (pending == null) return;
        
		for (boolean areMoreItems = pending.moveToFirst(); areMoreItems; areMoreItems = pending.moveToNext()) {
			// For each item in the cursor, ask the content provider to
			// serialize it, then pass it off to the NPS.
			final int id = pending.getInt(pending.getColumnIndex(RetrievalTableSchema._ID.n));
			final String topic = pending.getString(pending.getColumnIndex(RetrievalTableSchema.TOPIC.cv()));
			final DistributorPolicy.Topic policy = that.policy().matchRetrieval(topic);
			final DistributorState dispersal = policy.makeRouteMap();
			{
				final Cursor channelCursor = this.store.queryDisposalByParent(Tables.RETRIEVAL.o, id);
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; moreChannels = channelCursor.moveToNext()) {
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short channelState = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					dispersal.put(channel, DisposalState.getInstanceById(channelState));
				}
				channelCursor.close();
			}

			final UUID uuid = UUID.fromString(pending.getString(pending.getColumnIndex(RetrievalTableSchema.UUID.cv())));
			final String auid = pending.getString(pending.getColumnIndex(RetrievalTableSchema.AUID.cv()));
			final String selection = pending.getString(pending.getColumnIndex(RetrievalTableSchema.SELECTION.n));

			final int columnIx = pending.getColumnIndex(RetrievalTableSchema.LIMIT.n);
			final Integer limit = pending.isNull(columnIx) ? null : pending.getInt(columnIx);

			try {
				if (!that.isConnected()) {
					logger.debug("no network connection");
					continue;
				}
				synchronized (this.store) {
					final ContentValues values = new ContentValues();

					values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
					@SuppressWarnings("unused")
					final long numUpdated = this.store.updateRetrievalByKey(id, values, null);

					final DistributorState dispatchResult = this.dispatchRetrievalRequest(that, 
							uuid, topic, selection, limit, dispersal, 
							new INetworkService.OnSendMessageHandler() {
								final DistributorThread parent = DistributorThread.this;
								final String auid_ = auid;
								final String topic_ = topic;
		
								@Override
								public boolean ack(String channel, DisposalState status) {
									return parent.announceChannelAck(new ChannelAck(Tables.RETRIEVAL, id, 
											                                        topic_, auid_, 
											                                        channel, status));
								}
							});
					this.store.updateRetrievalByKey(id, null, dispatchResult);
				}
			} catch (NullPointerException ex) {
				logger.warn("NullPointerException, sending to gateway failed {}", ex.getStackTrace());
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

	private DistributorState dispatchRetrievalRequest(final AmmoService that, 
			final UUID retrievalId, final String topic, 
			final String selection, final Integer limit, final DistributorState dispersal, 
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
			serializer.setAction(new RequestSerializer.OnReady() {

				private AmmoMessages.PullRequest.Builder retrieveReq_ = retrieveReq;

				@Override
				public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {
					final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
					mw.setType(AmmoMessages.MessageWrapper.MessageType.PULL_REQUEST);
					mw.setPullRequest(retrieveReq_);
					final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(mw, handler);
					return agmb.build();
				}
			});
			return dispersal.multiplexRequest(that, serializer);
		} catch (com.google.protobuf.UninitializedMessageException ex) {
			logger.warn("Failed to marshal the message: {}", ex.getStackTrace());
		}
		return dispersal;

	}

	/**
	 * Get response to RetrievalRequest, PullResponse, from the gateway.
	 * 
	 * @param mw
	 * @return
	 */
	private boolean receiveRetrievalResponse(Context context, AmmoMessages.MessageWrapper mw) {
		if (mw == null)
			return false;
		if (!mw.hasPullResponse())
			return false;
		logger.trace("receive response RETRIEVAL");

		final AmmoMessages.PullResponse resp = mw.getPullResponse();

		// find the provider to use
		final String uuid = resp.getRequestUid();
		final String topic = resp.getMimeType();
		final Cursor cursor = this.store
				.queryRetrievalByKey(new String[] { RetrievalTableSchema.PROVIDER.n }, uuid, topic, null);
		if (cursor.getCount() < 1) {
			logger.error("received a message for which there is no retrieval {} {}", topic, uuid);
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
		final Uri tuple = RequestSerializer.deserializeToProvider(context, provider, encoding, resp.getData().toByteArray());
		logger.debug("tuple upserted {}", tuple);

		return true;
	}

	// =========== SUBSCRIBE ====================

	/**
	 * Process the subscription request. There are two parts: 1) checking to see
	 * if the network service is accepting requests and sending the request if
	 * it is 2) placing the request in the table.
	 * 
	 * The second step must be done first, so as to avoid a race to update the
	 * status of the request. The handling of insert v. update is also handled
	 * here.
	 * 
	 * @param that
	 * @param agm
	 * @param st
	 */
	private void doSubscribeRequest(final AmmoService that, final AmmoRequest ar, int st) {
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
			values.put(SubscribeTableSchema.EXPIRATION.cv(), ar.expire.cv());
			values.put(SubscribeTableSchema.PRIORITY.cv(), policy.routing.priority);
			values.put(SubscribeTableSchema.CREATED.cv(), System.currentTimeMillis());

			final DistributorState dispersal = policy.makeRouteMap();
			if (!that.isConnected()) {
				values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalTotalState.NEW.cv());
				long key = this.store.upsertSubscribe(values, dispersal);
				logger.debug("no network connection, added {}", key);
				return;
			}

			values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
			// We synchronize on the store to avoid a race between dispatch and
			// queuing
			synchronized (this.store) {
				final long id = this.store.upsertSubscribe(values, dispersal);
				final DistributorState dispatchResult = this.dispatchSubscribeRequest(that, 
						topic, ar.select.toString(), dispersal, 
						new INetworkService.OnSendMessageHandler() {
							final DistributorThread parent = DistributorThread.this;
							final long id_ = id;
							final String auid_ = auid;
							final String topic_ = topic;
		
							@Override
							public boolean ack(String channel, DisposalState status) {
								return parent.announceChannelAck(new ChannelAck(Tables.SUBSCRIBE, id_, 
										                                        topic_, auid_, 
										                                        channel, status));
							}
						});
				this.store.updateSubscribeByKey(id, null, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("NullPointerException, sending to gateway failed {}", ex.getStackTrace());
		}
	}

	/**
	 * Each time the subscription provider is modified, find out what the
	 * changes were and if necessary, send the data to the AmmoService.
	 * 
	 * Be careful about the race condition; don't leave gaps in the time line.
	 * Originally this method used time stamps to determine if the item had be
	 * sent. Now a status indicator is used.
	 * 
	 * Garbage collect items which are expired.
	 */

	private void doSubscribeCache(AmmoService that) {
		logger.debug(MARK_SUBSCRIBE, "process table SUBSCRIBE");

		final Cursor pending = this.store.querySubscribeReady();
		if (pending == null) return;

		for (boolean areMoreItems = pending.moveToFirst(); areMoreItems; areMoreItems = pending.moveToNext()) {
			// For each item in the cursor, ask the content provider to
			// serialize it, then pass it off to the NPS.
			final int id = pending.getInt(pending.getColumnIndex(SubscribeTableSchema._ID.n));
			final String topic = pending.getString(pending.getColumnIndex(SubscribeTableSchema.TOPIC.cv()));
			final String auid = pending.getString(pending.getColumnIndex(SubscribeTableSchema.AUID.cv()));

			final String selection = pending.getString(pending.getColumnIndex(SubscribeTableSchema.SELECTION.n));

			logger.trace(MARK_SUBSCRIBE, "process row SUBSCRIBE {} {} {}", new Object[] { id, topic, selection });

			final DistributorPolicy.Topic policy = that.policy().matchSubscribe(topic);
			final DistributorState dispersal = policy.makeRouteMap();
			{
				final Cursor channelCursor = this.store.queryDisposalByParent(Tables.SUBSCRIBE.o, id);
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; moreChannels = channelCursor.moveToNext()) {
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short channelState = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					dispersal.put(channel, DisposalState.getInstanceById(channelState));
				}
				channelCursor.close();
			}

			try {
				if (!that.isConnected()) {
					logger.debug("no network connection");
					continue;
				}
				synchronized (this.store) {
					final ContentValues values = new ContentValues();

					values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
					@SuppressWarnings("unused")
					long numUpdated = this.store.updateSubscribeByKey(id, values, null);

					final DistributorState dispatchResult = this.dispatchSubscribeRequest(that, 
							topic, selection, dispersal, 
							new INetworkService.OnSendMessageHandler() {
								final DistributorThread parent = DistributorThread.this;
								final int id_ = id;
								final String auid_ = auid;
								final String topic_ = topic;
		
								@Override
								public boolean ack(String channel, DisposalState status) {
									return parent.announceChannelAck(new ChannelAck(Tables.SUBSCRIBE, id_, 
											                                        topic_, auid_,  
											                                        channel, status));
								}
							});
					this.store.updateSubscribeByKey(id, null, dispatchResult);
				}
			} catch (NullPointerException ex) {
				logger.warn("NullPointerException, sending to gateway failed {}", ex.getStackTrace());
			}
		}
		pending.close();
	}

	/**
	 * Deliver the subscription request to the network service for processing.
	 */
	private DistributorState dispatchSubscribeRequest(final AmmoService that, 
			final String topic, final String selection, final DistributorState dispersal, 
			final INetworkService.OnSendMessageHandler handler) 
	{
		logger.trace("::dispatchSubscribeRequest {}", topic);

		/** Message Building */

		final AmmoMessages.SubscribeMessage.Builder subscribeReq = AmmoMessages.SubscribeMessage.newBuilder();
		subscribeReq.setMimeType(topic);

		if (subscribeReq != null)
			subscribeReq.setQuery(selection);

		final RequestSerializer serializer = RequestSerializer.newInstance();
		serializer.setAction(new RequestSerializer.OnReady() {

			final AmmoMessages.SubscribeMessage.Builder subscribeReq_ = subscribeReq;

			@Override
			public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {
				final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
				mw.setType(AmmoMessages.MessageWrapper.MessageType.SUBSCRIBE_MESSAGE);
				// mw.setSessionUuid(sessionId);
				mw.setSubscribeMessage(subscribeReq_);
				final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(mw, handler);
				return agmb.build();
			}
		});
		return dispersal.multiplexRequest(that, serializer);
	}

	/**
	 * Update the content providers as appropriate. These are typically received
	 * in response to subscriptions. In other words these replies are postal
	 * messages which have been redirected.
	 * 
	 * The subscribing uri isn't sent with the subscription to the gateway
	 * therefore it needs to be recovered from the subscription table.
	 */
	private boolean receiveSubscribeResponse(Context context, AmmoMessages.MessageWrapper mw) {
		if (mw == null) {
			logger.warn("no message");
			return false;
		}
		if (!mw.hasDataMessage() && !mw.hasTerseMessage() ) {
			logger.warn("no data in message");
			return false;
		}
		
		String mime = null;
		String encode = null;
		com.google.protobuf.ByteString data = null;
		if ( mw.hasDataMessage()) {
			final AmmoMessages.DataMessage resp = mw.getDataMessage();
			mime = resp.getMimeType();
			data = resp.getData();
			encode = resp.getEncoding();
		} else {
			final AmmoMessages.TerseMessage resp = mw.getTerseMessage();
			mime = AmmoMimeTypes.mimeTypes.get( resp.getMimeType());
			data = resp.getData();	
			encode = "TERSE";
		}
		
		// final ContentResolver resolver = context.getContentResolver();

		final String topic = mime;
		final Cursor cursor = this.store.querySubscribeByKey(new String[] { SubscribeTableSchema.PROVIDER.n }, topic, null);
		if (cursor.getCount() < 1) {
			logger.error("received a message for which there is no subscription {}", topic);
			cursor.close();
			return false;
		}
		cursor.moveToFirst();
		final String uriString = cursor.getString(0); // only asked for one so
														// it better be it.
		cursor.close();
		final Uri provider = Uri.parse(uriString);

		final Encoding encoding = Encoding.getInstanceByName( encode );
		final Uri tuple = RequestSerializer.deserializeToProvider(context, provider, encoding, data.toByteArray());

		logger.info("Ammo received message on topic: {} for provider: {}, inserted in {}", new Object[]{mime, uriString, tuple} );

		return true;
	}

	/**
	 * Clear the contents of tables in preparation for reloading them. This is
	 * predominantly *not* for postal which should persist.
	 */
	public void clearTables() {
		this.store.purgeRetrieval();
		this.store.purgeSubscribe();
	}

	// =============== UTILITY METHODS ======================== //

}

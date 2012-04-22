/*
 * Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
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
import android.os.Debug;
import android.os.Process;
import android.preference.PreferenceManager;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Notice;
import edu.vu.isis.ammo.api.type.Notice.Via;
import edu.vu.isis.ammo.api.type.Selection;
import edu.vu.isis.ammo.api.type.SerialMoment;
import edu.vu.isis.ammo.api.type.Topic;
import edu.vu.isis.ammo.core.AmmoMimeTypes;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.AmmoService.ChannelChange;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetChannel;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.AcknowledgementThresholds;
import edu.vu.isis.ammo.core.pb.AmmoMessages.MessageWrapper.MessageType;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement.PushStatus;
import edu.vu.isis.ammo.core.store.DistributorDataStore;
import edu.vu.isis.ammo.core.store.DistributorDataStore.CapabilityWorker;
import edu.vu.isis.ammo.core.store.DistributorDataStore.ChannelState;
import edu.vu.isis.ammo.core.store.DistributorDataStore.DisposalField;
import edu.vu.isis.ammo.core.store.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.store.DistributorDataStore.DisposalTotalState;
import edu.vu.isis.ammo.core.store.DistributorDataStore.InterestWorker;
import edu.vu.isis.ammo.core.store.DistributorDataStore.PostalField;
import edu.vu.isis.ammo.core.store.DistributorDataStore.PostalWorker;
import edu.vu.isis.ammo.core.store.DistributorDataStore.RequestField;
import edu.vu.isis.ammo.core.store.DistributorDataStore.RetrievalField;
import edu.vu.isis.ammo.core.store.Tables;
import edu.vu.isis.ammo.core.ui.AmmoCore;
import edu.vu.isis.ammo.util.FullTopic;

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
	private static final Logger logger = LoggerFactory.getLogger("class.dist");
	private static final boolean RUN_TRACE = false;

	private static final Marker MARK_POSTAL = MarkerFactory.getMarker("postal");
	private static final Marker MARK_RETRIEVAL = MarkerFactory.getMarker("retrieval");
	private static final Marker MARK_INTEREST = MarkerFactory.getMarker("interest");

	private static final String ACTION_BASE = "edu.vu.isis.ammo.";
	public static final String ACTION_MSG_SENT = ACTION_BASE+"ACTION_MESSAGE_SENT";
	public static final String ACTION_MSG_RCVD = ACTION_BASE+"ACTION_MESSAGE_RECEIVED";
	public static final String EXTRA_TOPIC = "topic";
	public static final String EXTRA_SUBTOPIC = "subtopic";
	public static final String EXTRA_UID = "uid";
	public static final String EXTRA_CHANNEL = "channel";
	public static final String EXTRA_STATUS = "status";
	public static final String EXTRA_DEVICE = "device";

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
	@SuppressWarnings("unused")
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

	public class ChannelAck {
		public final Tables type;
		public final long request;

		public final String topic;
		public final String subtopic;
		public final String auid;
		public final Notice notice;

		public final String channel;
		public final DisposalState status;

		public ChannelAck(Tables type, long request, 
				String topic, String subtopic, 
				String auid, Notice notice,
				String channel, DisposalState status) 
		{
			this.type = type;
			this.request = request;

			this.topic = topic;
			this.subtopic = topic;
			this.auid = auid;

			if (notice != null) { 
				this.notice = notice;
			} else {
				this.notice = new Notice();
			}

			this.channel = channel;
			this.status = status;
		}

		@Override
		public String toString() {
			return new StringBuilder()
			.append(" type=[").append(type).append(']')
			.append(" request=[").append(request).append(']')
			.append(" topic=[").append(topic).append(':').append(subtopic).append(']')
			.append(" aid=[").append(auid).append(']')
			.append(" channel=[").append(channel).append(']')
			.append(" status=[").append(status).append(']')
			.toString();
		}
	}

	/**
	 * Called by the channel acknowledgment once the channel has
	 * attempted to send the message.
	 * It will indicate whether the attempt succeeded or not.
	 * 
	 * @param ack
	 * @return
	 */
	private boolean announceChannelAck(ChannelAck ack) {
		logger.trace("RECV ACK {}", ack);
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

		if (ack.status == DisposalState.SENT)//update recv count and send notify
		{
			total_sent.incrementAndGet();
		}

		return true;
	}

	/**
	 * Once the channel is done with a request it generates a ChannelAck object.
	 * The corresponding disposal is updated with the 
	 * status of the channel's effort.
	 * If requested an intent is generated to notify others.
	 * 
	 * @param context 
	 * @param ack
	 */
	private void doChannelAck(final Context context, final ChannelAck ack) {
		logger.trace("channel ACK {}", ack);
		final long disposalKey;
		switch (ack.type) {
		case POSTAL:
			disposalKey = this.store.updatePostalByKey(ack.request, ack.channel, ack.status);
			PLogger.STORE_POSTAL_DQL.debug("update postal disposal=[{}]: ack=[{}]", disposalKey, ack);
			break;
		case RETRIEVAL:
			disposalKey = this.store.updateRetrievalByKey(ack.request, ack.channel, ack.status);
			PLogger.STORE_RETRIEVE_DQL.debug("update retrieval disposal=[{}]: ack=[{}]", disposalKey, ack);
			break;
		case INTEREST:
			disposalKey = this.store.updateInterestByKey(ack.request, ack.channel, ack.status);
			PLogger.STORE_INTEREST_DQL.debug("update interest disposal=[{}]: ack=[{}]", disposalKey, ack);
			break;
		default:
			disposalKey = -1;
			logger.warn("invalid ack type {}", ack);
			return;
		}

		final Notice.Item note = ack.notice.atSend;
		if (note.via.isActive()) {

			final Uri.Builder uriBuilder = new Uri.Builder()
			.scheme("ammo")
			.authority(ack.topic)
			.path(ack.subtopic);

			final Intent noticed = new Intent()
			.setAction(ACTION_MSG_SENT)
			.setData(uriBuilder.build())
			.putExtra(EXTRA_TOPIC, ack.topic.toString())
			.putExtra(EXTRA_SUBTOPIC, ack.subtopic.toString())
			.putExtra(EXTRA_UID, ack.auid.toString())
			.putExtra(EXTRA_CHANNEL, ack.channel.toString())
			.putExtra(EXTRA_STATUS, ack.status.toString());


			final int aggregate = note.via.v;
			PLogger.API_INTENT.debug("gen notice: via=[{}] intent=[{}]", 
					note.via, noticed);

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
			PLogger.QUEUE_REQ_ENTER.trace("offer request: {}", request);
			if (! this.requestQueue.offer(request, 1, TimeUnit.SECONDS)) {
				logger.error("queue full [{}], could not process request: {}", 
						this.requestQueue.size(), request);
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
		PLogger.QUEUE_RESP_ENTER.trace("offer response: {}", agm);
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

			this.doInterestCache(ammoService);
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
						logger.trace("do channel change");
						this.doChannelChange(ammoService);
						logger.trace("did channel change");
					}

					if (!this.channelAck.isEmpty()) {						
						try {
							final ChannelAck ack = this.channelAck.take();
							PLogger.QUEUE_ACK_EXIT.trace("take remaining=[{}] ack=[{}]", this.channelAck.size(), ack);
							this.doChannelAck(this.context, ack);
							logger.trace("did ack=[{}]", ack);
						} catch (ClassCastException ex) {
							logger.error("channel ack queue contains illegal item of class {}", ex.getLocalizedMessage());
						}
					}
					if (!this.responseQueue.isEmpty()) {
						try {
							final AmmoGatewayMessage agm = this.responseQueue.take();
							PLogger.QUEUE_RESP_EXIT.trace("take remaining=[{}] ack=[{}]", this.responseQueue.size(), agm);
							this.doResponse(ammoService, agm);
							logger.trace("did response=[{}]", agm);
						} catch (ClassCastException ex) {
							logger.error("response queue contains illegal item of class {}", ex.getLocalizedMessage());
						}
					}

					if (!this.requestQueue.isEmpty()) {
						try {					
							final AmmoRequest ar = this.requestQueue.take();
							PLogger.QUEUE_REQ_EXIT.trace("take remaining=[{}] ack=[{}]", this.requestQueue.size(), ar);
							this.doRequest(ammoService, ar);
							logger.trace("did request=[{}]", ar);
						} catch (ClassCastException ex) {
							logger.error("request queue contains illegal item of class {}", ex.getLocalizedMessage());
						}
					}
				}

			}
		} catch (InterruptedException ex) {
			logger.warn("task interrupted {}", ex.getStackTrace());
		}
		return;
	}


	// ================= DRIVER METHODS ==================== //

	/**
	 * Processes and delivers messages received from the gateway. 
	 * <ol>
	 * <li>Verify the check sum for the payload is correct
	 * <li>Parse the payload into a message
	 * <li> Receive the message
	 * </ol>
	 * 
	 * @param instream
	 * @return was the message clean (true) or garbled (false).
	 */
	private boolean doRequest(AmmoService that, AmmoRequest ar) {
		logger.trace("process request {}", ar);
		switch (ar.action) {
		case POSTAL:
			if (RUN_TRACE) {
				Debug.startMethodTracing("doPostalRequest");
				doPostalRequest(that, ar);
				Debug.stopMethodTracing();
			} else {
				doPostalRequest(that, ar);
			}
			break;
		case RETRIEVAL:
			doRetrievalRequest(that, ar);
			break;
		case INTEREST:
			doInterestRequest(that, ar, 1);
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
		this.store.deleteInterestGarbage();

		this.doPostalCache(that);
		this.doRetrievalCache(that);
		this.doInterestCache(that);
	}

	/**
	 * Processes and delivers messages received from a channel. 
	 * <ol>
	 * <li> Verify the check sum for the payload is correct 
	 * <li> Parse the payload into a message 
	 * <li> Receive the message
	 * </ol>
	 * 
	 * @param instream
	 * @return was the message clean (true) or garbled (false).
	 */
	private boolean doResponse(final Context context, 
			final AmmoGatewayMessage agm) 
	{
		logger.trace("do response=[{}]", agm);

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

		final String deviceId;
		switch (mw.getType()) {
		case DATA_MESSAGE:
		case TERSE_MESSAGE:
			final boolean interestResult = receiveInterestResponse(context, mw, agm.channel);
			logger.debug("interest reply {}", interestResult);
			if (mw.hasDataMessage()) {
				AmmoMessages.DataMessage dm = mw.getDataMessage();
				if (dm.hasOriginDevice()) { 
					deviceId = dm.getOriginDevice(); 
				} else {
					deviceId = null;
				}
			} else {
				deviceId = null;
			}
			break;

		case AUTHENTICATION_RESULT:
			final boolean result = receiveAuthenticateResponse(context, mw);
			logger.debug("authentication result={}", result);
			deviceId = null;
			break;

		case PUSH_ACKNOWLEDGEMENT:
			final boolean postalResult = receivePostalResponse(context, mw);
			logger.debug("post acknowledgement {}", postalResult);
			deviceId = null;
			break;

		case PULL_RESPONSE:
			final boolean retrieveResult = receiveRetrievalResponse(context, mw);
			logger.debug("retrieve response {}", retrieveResult);
			deviceId = null;
			break;

		case SUBSCRIBE_MESSAGE:
			if (mw.hasSubscribeMessage()) {
				final AmmoMessages.SubscribeMessage sm = mw.getSubscribeMessage();

				final FullTopic fulltopic = FullTopic.fromType(sm.getMimeType());
				final AmmoRequest.Builder ab = AmmoRequest.newBuilder(this.context)
						.topic(fulltopic.topic)
						.subtopic(fulltopic.subtopic);

				if (sm.hasOriginDevice()) { 
					deviceId = sm.getOriginDevice();
					final CapabilityWorker worker = 
							this.store.getCapabilityWorker(ab.base(), this.ammoService);
					worker.upsert();
				} else {
					deviceId = null;
				}
			} else {
				deviceId = null;
			}
			break;
		case AUTHENTICATION_MESSAGE:
		case PULL_REQUEST:
		case UNSUBSCRIBE_MESSAGE:
			logger.debug("{} message, no processing", mw.getType());
			deviceId = null;
			break;
		default:
			logger.error("unexpected reply type. {}", mw.getType());
			deviceId = null;
		}
		if (deviceId == null) {
			logger.trace("[{}] did not carry a device", mw.getType());
		} else {
			ammoService.store()
			.getPresenceWorker(deviceId)
			.upsert();
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
		logger.trace("process request POSTAL");

		// Dispatch the message.
		try {
			final PostalWorker worker = that.store().getPostalWorker(ar, that);
			logger.trace("process request topic {}, uuid {}", worker.topic, worker.uuid);

			final Dispersal dispersal = worker.policy.makeRouteMap();

			final byte[] payload;			
			switch (worker.serialMoment.type()) {
			case APRIORI:
				if (worker.payload == null) {
					logger.error("apriori serialization requires payload");
					payload = new byte[0];
				} else {
					payload = worker.payload.asBytes();
				}
				break;
			case EAGER:				
				try {
					final RequestSerializer serializer = RequestSerializer.newInstance(worker.provider, worker.payload);
					payload = RequestSerializer.serializeFromProvider(that.getContentResolver(), 
							serializer.provider.asUri(), Encoding.getDefault());

				} catch (IOException e1) {
					logger.error("invalid row for serialization");
					return;
				} catch (TupleNotFoundException e) {
					logger.error("tuple not found when processing postal table");
					worker.delete(e.missingTupleUri.getPath());
					return;
				} catch (NonConformingAmmoContentProvider e) {
					e.printStackTrace();
					return;
				}
				break;
			case LAZY:
			default:
				payload = null;
			}

			if (!that.isConnected()) {
				long key = worker.upsert(DisposalTotalState.DISTRIBUTE, payload);

				logger.debug("no network connection, added {}", key);
				return;
			}

			final RequestSerializer serializer = RequestSerializer.newInstance(worker.provider, worker.payload);
			serializer.setSerializer(new RequestSerializer.OnSerialize() {

				final RequestSerializer serializer_ = serializer;
				final AmmoService that_ = that;
				final PostalWorker postal_ = worker;

				@Override
				public byte[] run(Encoding encode) {
					if (serializer_.payload != null) {
						switch (serializer_.payload.whatContent()) {
						case NONE: break;

						case CV:
							final byte[] result = 
							RequestSerializer.serializeFromContentValues(
									serializer_.payload.getCV(),
									encode);

							if (result == null) {
								logger.error("Null result from serialize content value, encoding into {}", encode);
							}
							return result;

						case BYTE:
						case STR:
							return serializer_.payload.asBytes();
						}
					} 
					try {
						final byte[] result = 
								RequestSerializer.serializeFromProvider(that_.getContentResolver(), 
										serializer_.provider.asUri(), encode);

						if (result == null) {
							logger.error("Null result from serialize {} {} ", serializer_.provider, encode);
						}
						return result;
					} catch (IOException e1) {
						logger.error("invalid row for serialization {}", e1.getLocalizedMessage());
						return null;
					} catch (TupleNotFoundException e) {
						logger.error("tuple not found when processing postal table");
						postal_.delete(e.missingTupleUri.getPath());
						return null;
					} catch (NonConformingAmmoContentProvider e) {
						e.printStackTrace();
						return null;
					}
				}
			});

			// We synchronize on the store to avoid a race between dispatch and
			// queuing
			synchronized (this.store) {
				final long id = worker.upsert(DisposalTotalState.DISTRIBUTE, payload);
				final INetworkService.OnSendMessageHandler msgHandler = 
						new INetworkService.OnSendMessageHandler() {
					final DistributorThread parent = DistributorThread.this;
					final long id_ = id;
					final String topic_ = worker.topic;
					final String subtopic_ = worker.subtopic;
					final String auid_ = worker.auid;
					final Notice notice_ = worker.notice;

					@Override
					public boolean ack(String channel, DisposalState status) {
						final ChannelAck ack = new ChannelAck(
								Tables.POSTAL, id_, topic_, subtopic_, auid_, notice_, channel, status);
						return parent.announceChannelAck(ack);
					}
				};
				final Dispersal dispatchResult = this.dispatchPostalRequest(that, 
						worker, dispersal, serializer, 
						msgHandler);

				this.store.updatePostalByKey(id, null, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("processing postal request failed {} {}", 
					ex, ex.getStackTrace());
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

		Cursor pending = null;
		try {
			pending = this.store.queryPostalReady();
			if (pending == null) return;

			// Iterate over each row serializing its data and sending it.
			for (boolean moreItems = pending.moveToFirst(); moreItems; 
					moreItems = pending.moveToNext()) 
			{
				PLogger.STORE_POSTAL_DQL.trace("postal cursor: {}", pending);
				final int id = pending.getInt(pending.getColumnIndex(RequestField._ID.n()));
				final PostalWorker postal = this.store().getPostalWorker(pending, that);

				logger.debug("serializing: {} as {}", postal.provider, postal.topic);

				final RequestSerializer serializer = RequestSerializer.newInstance(postal.provider, postal.payload);

				int dataColumnIndex = pending.getColumnIndex(PostalField.DATA.n());

				final String data;
				if (!pending.isNull(dataColumnIndex)) {
					data = pending.getString(dataColumnIndex);
				} else {
					data = null;
				}

				switch (postal.serialMoment.type()) {
				case APRIORI:
				case EAGER:
					serializer.setSerializer( new RequestSerializer.OnSerialize() {
						final SerialMoment serialMoment_ = postal.serialMoment;
						final String data_ = data;

						@Override
						public byte[] run(Encoding encode) {
							if (data_ == null || data_.length() < 1) {
								logger.warn("your {} payload has no content: {}", 
										serialMoment_, data_);
								return new byte[0];
							}
							return data_.getBytes();
						}
					});
					break;

				case LAZY:
				default:
					serializer.setSerializer( new RequestSerializer.OnSerialize() {
						final RequestSerializer serializer_ = serializer;
						final AmmoService that_ = that;
						final PostalWorker postal_ = postal;

						@Override
						public byte[] run(Encoding encode) {
							try {
								return RequestSerializer.serializeFromProvider(that_.getContentResolver(), 
										serializer_.provider.asUri(), encode);
							} catch (IOException e1) {
								logger.error("invalid row for serialization");
							} catch (TupleNotFoundException ex) {
								logger.error("tuple not found when processing postal table");
								postal_.delete(ex.missingTupleUri.getPath());
							} catch (NonConformingAmmoContentProvider e) {
								e.printStackTrace();
							}
							logger.error("no serialized data produced");
							return null;
						}
					});
				}

				final DistributorPolicy.Topic policy = that.policy().matchPostal(postal.topic);
				final Dispersal dispersal = policy.makeRouteMap();	
				Cursor channelCursor = null;
				try {
					channelCursor = this.store.getPostalDisposalWorker().queryByParent(id);
					for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; 
							moreChannels = channelCursor.moveToNext()) 
					{
						final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalField.CHANNEL.n()));
						final short channelState = channelCursor.getShort(channelCursor.getColumnIndex(DisposalField.STATE.n()));
						dispersal.put(channel, DisposalState.getInstanceById(channelState));
					}
					logger.trace("prior channel states {}", dispersal);
				} finally {
					if (channelCursor != null) channelCursor.close();
				}

				// Dispatch the request.
				try {
					if (!that.isConnected()) {
						logger.debug("no network connection while processing table");
						continue;
					} 
					synchronized (this.store) {
						final ContentValues values = new ContentValues();

						values.put(RequestField.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
						long numUpdated = this.store.updatePostalByKey(id, values, null);
						logger.debug("updated {} postal items", numUpdated);

						final INetworkService.OnSendMessageHandler msgHandler = null;
						new INetworkService.OnSendMessageHandler() {
							final DistributorThread parent = DistributorThread.this;
							final int id_ = id;
							final String auid_ = postal.auid;
							final String topic_ = postal.topic;
							final String subtopic_ = postal.subtopic;
							final Notice notice_ = postal.notice;

							@Override
							public boolean ack(String channel, DisposalState status) {
								final ChannelAck ca = new ChannelAck(Tables.POSTAL, id_, 
										topic_, subtopic_, auid_, notice_, channel, status);
								return parent.announceChannelAck(ca);
							}
						};

						final Dispersal dispatchResult = this.dispatchPostalRequest(
								that, postal, dispersal, serializer, msgHandler);

						this.store.updatePostalByKey(id, null, dispatchResult);
					}
				} catch (NullPointerException ex) {
					logger.warn("processing postal request from cache failed {} {}", 
							ex, ex.getStackTrace());
				}
			}
			pending.close();
		} finally {
			if (pending != null) pending.close();
		}
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
	private Dispersal dispatchPostalRequest(
			final AmmoService that,
			final PostalWorker worker, 
			final Dispersal dispersal, 
			final RequestSerializer serializer, 
			final INetworkService.OnSendMessageHandler handler) 
	{
		logger.trace("::dispatchPostalRequest");
		final String provider = worker.provider.toString();
		final String msgType = worker.getType();

		final Long now = System.currentTimeMillis();
		logger.debug("Building MessageWrapper @ time {}", now);

		serializer.setAction(new RequestSerializer.OnReady() {
			private final PostalWorker postal_ = worker;
			@Override
			public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {

				if (serialized == null) {
					logger.error("No Payload");
					return null;
				}

				final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();

				if (encode.getType() == Encoding.Type.TERSE)  {
					mw.setType(AmmoMessages.MessageWrapper.MessageType.TERSE_MESSAGE);

					final Integer mimeId = AmmoMimeTypes.mimeIds.get(msgType);
					if (mimeId == null) {
						logger.error("no integer mapping for this mime type {}", msgType);
						return null;
					}
					final AmmoMessages.TerseMessage.Builder postReq = AmmoMessages.TerseMessage
							.newBuilder()
							.setMimeType(mimeId)
							.setData(ByteString.copyFrom(serialized));

					mw.setTerseMessage(postReq);
				} 
				else {
					final Notice notice = postal_.notice;
					final AcknowledgementThresholds.Builder noticeBuilder = AcknowledgementThresholds.newBuilder()
							.setDeviceDelivered(notice.atDelivery.via.isActive())
							.setAndroidPluginReceived(notice.atGateIn.via.isActive())
							.setPluginDelivered(notice.atGateOut.via.isActive());

					mw.setType(AmmoMessages.MessageWrapper.MessageType.DATA_MESSAGE);

					final AmmoMessages.DataMessage.Builder postReq = AmmoMessages.DataMessage
							.newBuilder()
							.setUid(provider)
							.setMimeType(msgType)
							.setEncoding(encode.getType().name())
							.setData(ByteString.copyFrom(serialized))
							.setThresholds(noticeBuilder);
					if (notice.isRemoteActive()) {
						postReq.setOriginDevice(ammoService.getDeviceId());
					}

					mw.setDataMessage(postReq);
				} 
				logger.debug("Finished wrap build @ timeTaken {} ms, serialized-size={} \n", System.currentTimeMillis() - now, serialized.length);
				final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(mw, handler);
				return agmb.build();
			}
		});
		return dispersal.multiplexRequest(that, serializer);
	}

	/**
	 * Get response to PostRequest from the gateway. This should be seen in
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

		final PushAcknowledgement pushResp = mw.getPushAcknowledgement();
		// generate an intent if it was requested

		final PostalWorker worker = this.store().getPostalWorkerByKey(pushResp.getUid());

		if (worker.notice.atDelivery.via.hasHeartbeat()) {
			// TODO update CAPABILITY or RECIPIENT table
		}
		final Notice.Item note = worker.notice.atDelivery;
		if (note.via.isActive()) {

			final Uri.Builder uriBuilder = new Uri.Builder()
			.scheme("ammo")
			.authority(worker.topic)
			.path(worker.subtopic);

			final Intent noticed = new Intent()
			.setAction(ACTION_MSG_SENT)
			.setData(uriBuilder.build())
			.putExtra(EXTRA_TOPIC, worker.topic.toString())
			.putExtra(EXTRA_SUBTOPIC, worker.topic.toString())
			.putExtra(EXTRA_UID, worker.auid.toString())
			.putExtra(EXTRA_STATUS, worker.dispersal.toString())
			.putExtra(EXTRA_DEVICE, pushResp.getAcknowledgingDevice().toString());

			final int aggregate = note.via.v;	
			PLogger.API_INTENT.debug("gen intent: [{}]", note);

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
			final String subtopic = (ar.subtopic == null) ? Topic.DEFAULT : ar.subtopic.asString();

			final String select = ar.select.toString();
			final Integer limit = (ar.limit == null) ? null : ar.limit.asInteger();
			final DistributorPolicy.Topic policy = that.policy().matchRetrieval(topic);

			final ContentValues values = new ContentValues();
			values.put(RequestField.UUID.cv(), uuid.toString());
			values.put(RequestField.AUID.cv(), auid);
			values.put(RequestField.TOPIC.cv(), topic);

			values.put(RetrievalField.SELECTION.cv(), select);
			if (limit != null)
				values.put(RetrievalField.LIMIT.cv(), limit);

			values.put(RequestField.PROVIDER.cv(), ar.provider.cv());
			values.put(RequestField.PRIORITY.cv(), ar.priority);
			values.put(RequestField.EXPIRATION.cv(), ar.expire.cv());
			values.put(RequestField.PRIORITY.cv(), ar.priority);
			values.put(RequestField.CREATED.cv(), System.currentTimeMillis());

			final Dispersal dispersal = policy.makeRouteMap();
			if (!that.isConnected()) {
				values.put(RequestField.DISPOSITION.cv(), DisposalTotalState.NEW.cv());
				// FIXME				this.store.upsertRetrieval(values, dispersal);
				logger.debug("no network connection");
				return;
			}

			values.put(RequestField.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
			// We synchronize on the store to avoid a race between dispatch and
			// queuing
			synchronized (this.store) {
				// FIXME				final long id = this.store.upsertRetrieval(values, policy.makeRouteMap());
				final long id = 1;
				final Dispersal dispatchResult = this.dispatchRetrievalRequest(that, 
						uuid, topic, subtopic, select, limit, dispersal, 
						new INetworkService.OnSendMessageHandler() {
					final DistributorThread parent = DistributorThread.this;
					final long id_ = id;
					final String auid_ = auid;
					final String topic_ = topic;
					final String subtopic_ = null;
					final Notice notice_ = null;

					@Override
					public boolean ack(String channel, DisposalState status) {
						return parent.announceChannelAck(new ChannelAck(Tables.RETRIEVAL, id_, 
								topic_, subtopic_, auid_, notice_, 
								channel, status));
					}
				});
				this.store.updateRetrievalByKey(id, null, dispatchResult);
			}


		} catch (NullPointerException ex) {
			logger.warn("processing retrieval request failed {} {}", 
					ex, ex.getStackTrace());
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

		Cursor pending = null;
		try {
			pending = this.store.queryRetrievalReady();

			if (pending == null) return;

			for (boolean areMoreItems = pending.moveToFirst(); areMoreItems; areMoreItems = pending.moveToNext()) {
				PLogger.STORE_RETRIEVE_DQL.trace("retrieval cursor: {}", pending);

				// For each item in the cursor, ask the content provider to
				// serialize it, then pass it off to the NPS.
				final int id = pending.getInt(pending.getColumnIndex(RequestField._ID.n()));
				final String topic = pending.getString(pending.getColumnIndex(RequestField.TOPIC.cv()));
				final String subtopic = pending.getString(pending.getColumnIndex(RequestField.SUBTOPIC.cv()));
				final DistributorPolicy.Topic policy = that.policy().matchRetrieval(topic);
				final Dispersal dispersal = policy.makeRouteMap();
				Cursor channelCursor = null;
				try {
					channelCursor = this.store.getRetrievalDisposalWorker().queryByParent(id);
					for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; moreChannels = channelCursor.moveToNext()) {
						final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalField.CHANNEL.n()));
						final short channelState = channelCursor.getShort(channelCursor.getColumnIndex(DisposalField.STATE.n()));
						dispersal.put(channel, DisposalState.getInstanceById(channelState));
					}
				} finally {
					channelCursor.close();
				}

				final UUID uuid = UUID.fromString(pending.getString(pending.getColumnIndex(RequestField.UUID.cv())));
				final String auid = pending.getString(pending.getColumnIndex(RequestField.AUID.cv()));
				final String selection = pending.getString(pending.getColumnIndex(RetrievalField.SELECTION.cv()));

				final int columnIx = pending.getColumnIndex(RetrievalField.LIMIT.n());
				final Integer limit = pending.isNull(columnIx) ? null : pending.getInt(columnIx);

				try {
					if (!that.isConnected()) {
						logger.debug("no network connection");
						continue;
					}
					synchronized (this.store) {
						final ContentValues values = new ContentValues();

						values.put(RequestField.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
						@SuppressWarnings("unused")
						final long numUpdated = this.store.updateRetrievalByKey(id, values, null);

						final Dispersal dispatchResult = this.dispatchRetrievalRequest(that, 
								uuid, topic, subtopic, selection, limit, dispersal, 
								new INetworkService.OnSendMessageHandler() {
							final DistributorThread parent = DistributorThread.this;
							final String auid_ = auid;
							final String topic_ = topic;
							final String subtopic_ = null;
							final Notice notice_ = null;

							@Override
							public boolean ack(String channel, DisposalState status) {
								return parent.announceChannelAck(new ChannelAck(Tables.RETRIEVAL, id, 
										topic_, subtopic_, auid_, notice_,
										channel, status));
							}
						});
						this.store.updateRetrievalByKey(id, null, dispatchResult);
					}

				} catch (NullPointerException ex) {
					logger.warn("processing retrieval request from cache failed {} {}", 
							ex, ex.getStackTrace());
				}
			}
			pending.close();
		} finally {
			if (pending != null) pending.close();
		}
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

	private Dispersal dispatchRetrievalRequest(final AmmoService that, 
			final UUID retrievalId, 
			final String topic, final String subtopic,
			final String selection, final Integer limit, final Dispersal dispersal, 
			final INetworkService.OnSendMessageHandler handler) 
	{
		final FullTopic fulltopic = FullTopic.fromTopic(topic, subtopic);
		logger.trace("dispatch request RETRIEVAL {}", topic);

		/** Message Building */

		// mw.setSessionUuid(sessionId);

		final AmmoMessages.PullRequest.Builder retrieveReq = AmmoMessages.PullRequest
				.newBuilder()
				.setRequestUid(retrievalId.toString())
				.setMimeType(fulltopic.aggregate);

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
		final FullTopic fulltopic = FullTopic.fromType(resp.getMimeType());

		Cursor cursor = null;
		try {
			cursor = this.store
					.queryRetrievalByKey(
							new String[] { RequestField.PROVIDER.n() }, 
							uuid, null);
			if (cursor.getCount() < 1) {
				logger.error("received a message for which there is no retrieval {} {}", fulltopic, uuid);
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
		} finally {
			if (cursor != null) cursor.close();
		}

		return true;
	}

	// =========== INTEREST ====================

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
	private void doInterestRequest(final AmmoService that, final AmmoRequest ar, int st) {
		logger.trace("process request INTEREST {}", ar);

		// Dispatch the message.
		try {
			final InterestWorker worker = this.store().getInterestWorker(ar, this.ammoService);
			PLogger.STORE_INTEREST_DQL.trace("do interest request: {}", worker);

			if (!that.isConnected()) {
				long key = worker.upsert(DisposalTotalState.NEW);
				logger.debug("no network connection, added {}", key);
				return;
			}

			// We synchronize on the store to avoid a race between dispatch and
			// queuingupsertInterest
			synchronized (this.store) {
				final long id = worker.upsert(DisposalTotalState.DISTRIBUTE);

				final Dispersal dispatchResult = this.dispatchInterestRequest(that, 
						worker.topic, worker.subtopic, worker.select, worker.dispersal, 
						new INetworkService.OnSendMessageHandler() {
					final DistributorThread parent = DistributorThread.this;
					final long id_ = id;
					final String auid_ = worker.auid;
					final String topic_ = worker.topic;
					final String subtopic_ = worker.subtopic;
					final Notice notice_ = null;

					@Override
					public boolean ack(String channel, DisposalState status) {
						return parent.announceChannelAck(new ChannelAck(Tables.INTEREST, id_, 
								topic_, subtopic_, auid_, notice_,
								channel, status));
					}
				});
				this.store.updateInterestByKey(id, null, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("processing interest request failed {} {}", 
					ex, ex.getStackTrace());
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

	private void doInterestCache(AmmoService that) {
		logger.debug(MARK_INTEREST, "process table INTEREST");

		Cursor pending = null;
		try {
			pending = this.store.queryInterestReady();

			if (pending == null) return;

			for (boolean areMoreItems = pending.moveToFirst(); areMoreItems; areMoreItems = pending.moveToNext()) {

				final InterestWorker worker = this.store().getInterestWorker(pending, this.ammoService);
				PLogger.STORE_INTEREST_DQL.trace("interest cursor: {}", worker);

				try {
					if (!that.isConnected()) {
						logger.debug("no network connection");
						continue;
					}
					synchronized (this.store) {
						final ContentValues values = new ContentValues();

						values.put(RequestField.DISPOSITION.cv(), DisposalTotalState.DISTRIBUTE.cv());
						@SuppressWarnings("unused")
						long numUpdated = this.store.updateInterestByKey(worker.id, values, null);

						final Dispersal dispatchResult = this.dispatchInterestRequest(that, 
								worker.topic, worker.subtopic, worker.select, worker.dispersal, 
								new INetworkService.OnSendMessageHandler() {
							final DistributorThread parent = DistributorThread.this;
							final int id_ = worker.id;
							final String auid_ = worker.auid;
							final String topic_ = worker.topic;
							final String subtopic_ = worker.subtopic;

							final Notice notice_ = Notice.RESET;

							@Override
							public boolean ack(String channel, DisposalState status) {
								return parent.announceChannelAck(new ChannelAck(Tables.INTEREST, id_, 
										topic_, subtopic_, auid_,  notice_,
										channel, status));
							}
						});
						this.store.updateInterestByKey(worker.id, null, dispatchResult);
					}

				} catch (NullPointerException ex) {
					logger.warn("processing interest request from cache failed {} {}", 
							ex, ex.getStackTrace());
				}
			}
			pending.close();
		} finally {
			if (pending != null) pending.close();
		}
	}

	/**
	 * Deliver the subscription request to the network service for processing.
	 */
	private Dispersal dispatchInterestRequest(final AmmoService that, 
			final String topic, final String subtopic, 
			final Selection selection, final Dispersal dispersal, 
			final INetworkService.OnSendMessageHandler handler) 
	{
		final FullTopic fulltopic = FullTopic.fromTopic(topic, subtopic);
		logger.trace("::dispatchInterestRequest {}", fulltopic);

		/** Message Building */

		final AmmoMessages.SubscribeMessage.Builder interestReq = AmmoMessages.SubscribeMessage.newBuilder()
				.setMimeType(fulltopic.aggregate)
				.setOriginDevice(ammoService.getDeviceId());

		if (selection != null)
			interestReq.setQuery(selection.cv());

		final RequestSerializer serializer = RequestSerializer.newInstance();
		serializer.setAction(new RequestSerializer.OnReady() {

			final AmmoMessages.SubscribeMessage.Builder interestReq_ = interestReq;

			@Override
			public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {
				final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
				mw.setType(AmmoMessages.MessageWrapper.MessageType.SUBSCRIBE_MESSAGE);
				// mw.setSessionUuid(sessionId);
				mw.setSubscribeMessage(interestReq_);
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
	private boolean receiveInterestResponse(Context context, AmmoMessages.MessageWrapper mw, NetChannel channel) {
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

			// Send acknowledgment, if requested by sender
			final AmmoMessages.AcknowledgementThresholds at = resp.getThresholds();
			if (at.getDeviceDelivered()) {
				final AmmoMessages.MessageWrapper.Builder mwb = 
						AmmoMessages.MessageWrapper.newBuilder();
				mwb.setType(AmmoMessages.MessageWrapper.MessageType.PUSH_ACKNOWLEDGEMENT);

				final AmmoMessages.PushAcknowledgement.Builder pushAck = 
						AmmoMessages.PushAcknowledgement
						.newBuilder()
						.setUid(resp.getUid())
						.setDestinationDevice(resp.getOriginDevice())
						.setAcknowledgingDevice(ammoService.getDeviceId())
						.setStatus(PushStatus.UNKNOWN);

				mwb.setPushAcknowledgement(pushAck);
				// TODO place in the appropriate channel's queue
				final AmmoGatewayMessage.Builder oagmb = AmmoGatewayMessage.newBuilder()
						.payload(mwb.build().toByteArray());

				if (channel != null) {
					channel.sendRequest(oagmb.build());
				}
			}
		} else {
			final AmmoMessages.TerseMessage resp = mw.getTerseMessage();
			mime = AmmoMimeTypes.mimeTypes.get( resp.getMimeType());
			data = resp.getData();	
			encode = "TERSE";
		}
		final FullTopic fulltopic = FullTopic.fromType(mime);
		logger.trace("receive response INTEREST : [{}]", fulltopic );

		Cursor cursor = null;
		try {
			cursor = this.store.queryInterestByKey(
					new String[] { RequestField.PROVIDER.n() }, fulltopic.topic, fulltopic.subtopic, null);
			if (cursor.getCount() < 1) {
				logger.error("received a message for which there is no interest {}", fulltopic);
				cursor.close();
				return false;
			}
			cursor.moveToFirst();
			final String uriString = cursor.getString(0); 
			// only asked for one so it better be it.
			cursor.close();
			final Uri provider = Uri.parse(uriString);

			final Encoding encoding = Encoding.getInstanceByName( encode );
			final Uri tuple = RequestSerializer.deserializeToProvider(context, provider, encoding, data.toByteArray());

			logger.info("Ammo received message on topic: {} for provider: {}, inserted in {}", 
					new Object[]{mime, uriString, tuple} );

		} finally {
			if (cursor != null) cursor.close();
		}
		return true;
	}

	/**
	 * Clear the contents of tables in preparation for reloading them. This is
	 * predominantly *not* for postal which should persist.
	 */
	public void clearTables() {
		this.store.purgeRetrieval();
		this.store.purgeInterest();
	}

}

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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.jcip.annotations.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.AmmoMimeTypes;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.AmmoService.ChannelChange;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelDisposal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RequestDisposal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SerializeType;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.MessageWrapper.MessageType;

/**
 * The distributor service runs in the ui thread. This establishes a new thread
 * for distributing the requests.
 * 
 */
@ThreadSafe
public class DistributorThread extends AsyncTask<AmmoService, Integer, Void> {
	// ===========================================================
	// Constants
	// ===========================================================
	private static final Logger logger = LoggerFactory.getLogger("ammo-dst");
	private static final Marker MARK_POSTAL = MarkerFactory.getMarker("postal");
	private static final Marker MARK_RETRIEVAL = MarkerFactory.getMarker("retrieval");
	private static final Marker MARK_SUBSCRIBE = MarkerFactory.getMarker("subscribe");

	// 20 seconds expressed in milliseconds
	private static final int BURP_TIME = 20 * 1000;

	/**
	 * The backing store for the distributor
	 */
	final private DistributorDataStore store;

	public DistributorThread(Context context) {
		super();
		this.requestQueue = new LinkedBlockingQueue<AmmoRequest>(200);
		this.responseQueue = new PriorityBlockingQueue<AmmoGatewayMessage>(200, new AmmoGatewayMessage.PriorityOrder());
		this.store = new DistributorDataStore(context);
		
		this.channelStatus = new ConcurrentHashMap<String, ChannelStatus>();
		this.channelDelta = new AtomicBoolean(true);
		
		this.channelAck = new LinkedBlockingQueue<ChannelAck>(200);
		logger.debug("constructed");
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
		public final long id;
		public final Tables type;
		public final String channel;
		public final ChannelDisposal status;

		public ChannelAck(long id, Tables type, String channel, ChannelDisposal status) {
			this.id = id;
			this.type = type;
			this.channel = channel;
			this.status = status;
		}

		@Override
		public String toString() {
			return new StringBuilder().append(" id ").append(id).append(" type ").append(type).append(" channel ").append(channel).append(" status ").append(status).toString();
		}
	}

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
		return true;
	}

	/**
	 * Once the channel is done with a request it generates a ChannelAck object.
	 * 
	 * @param ack
	 */
	private void processChannelAck(ChannelAck ack) {
		final long numUpdated;
		switch (ack.type) {
		case POSTAL:
			numUpdated = this.store.updatePostalByKey(ack.id, ack.channel, ack.status);
			break;
		case PUBLISH:
			numUpdated = this.store.updatePublishByKey(ack.id, ack.channel, ack.status);
			break;
		case RETRIEVAL:
			numUpdated = this.store.updateRetrievalByKey(ack.id, ack.channel, ack.status);
			break;
		case SUBSCRIBE:
			numUpdated = this.store.updateSubscribeByKey(ack.id, ack.channel, ack.status);
			break;
		default:
			logger.trace("invalid ack type {}", ack);
			return;
		}
		logger.trace("ACK {}: request {} : over {} row {} updated to {}", new Object[] { ack.type, ack.id, ack.channel, numUpdated, ack.status });
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
			logger.trace("received request of type {}", request.toString());

			if (! this.requestQueue.offer(request, 1, TimeUnit.SECONDS)) {
				logger.error("could not process request {}", request);
				this.signal();
				return null;
			}
			this.signal();
			return request.uuid();

		} catch (InterruptedException ex) {
			logger.warn("distribute request {}", ex.getStackTrace());
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
	private boolean isReady(AmmoService[] them) {
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
	protected Void doInBackground(AmmoService... them) {

		logger.info("started");

		for (final AmmoService that : them) {
			if (!that.isConnected())
				continue;

			for (final Map.Entry<String, ChannelStatus> entry : channelStatus.entrySet()) {
				final String name = entry.getKey();
				this.store.deactivateDisposalStateByChannel(name);
			}

			this.processSubscribeTable(that);
			this.processRetrievalTable(that);
			this.processPostalTable(that);
		}

		try {
			while (true) {
				// condition wait, is there something to process?
				synchronized (this) {
					while (!this.isReady(them))
						this.wait(BURP_TIME);
				}
				while (this.isReady(them)) {
					if (this.channelDelta.getAndSet(false)) {
						logger.trace("channel change");
						for (AmmoService that : them) {
							this.processChannelChange(that);
						}
					}

					if (!this.channelAck.isEmpty()) {
						logger.trace("processing channel acks, remaining {}", this.channelAck.size());
						try {
							final ChannelAck ack = this.channelAck.take();
							this.processChannelAck(ack);
						} catch (ClassCastException ex) {
							logger.error("channel ack queue contains illegal item of class {}", ex.getLocalizedMessage());
						}
					}

					if (!this.responseQueue.isEmpty()) {
						logger.trace("processing response, remaining {}", this.responseQueue.size());
						try {
							final AmmoGatewayMessage agm = this.responseQueue.take();
							for (AmmoService that : them) {
								this.processResponse(that, agm);
							}
						} catch (ClassCastException ex) {
							logger.error("response queue contains illegal item of class {}", ex.getLocalizedMessage());
						}
					}

					if (!this.requestQueue.isEmpty()) {
						logger.trace("processing request, remaining {}", this.requestQueue.size());
						try {
							final AmmoRequest agm = this.requestQueue.take();
							for (AmmoService that : them) {
								this.processRequest(that, agm);
							}
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
		logger.error("distribution thread finishing {}", result);
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
	private boolean processRequest(AmmoService that, AmmoRequest agm) {
		logger.trace("process request {}", agm);
		switch (agm.action) {
		case POSTAL:
			processPostalRequest(that, agm);
			break;
		case DIRECTED_POSTAL:
			processPostalRequest(that, agm);
			break;
		case PUBLISH:
			break;
		case RETRIEVAL:
			processRetrievalRequest(that, agm);
			break;
		case SUBSCRIBE:
			processSubscribeRequest(that, agm, 1);
			break;
		case DIRECTED_SUBSCRIBE:
			processSubscribeRequest(that, agm, 2);
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
	private void processChannelChange(AmmoService that) {
		logger.trace("::processChannelChange()");

		for (final Map.Entry<String, ChannelStatus> entry : channelStatus.entrySet()) {
			final String name = entry.getKey();
			final ChannelStatus status = entry.getValue();
			if (status.status.getAndSet(true))
				continue;

			logger.trace("::processChannelChange() : {} , {}", name, status.change);
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
		this.store.deletePublishGarbage();
		this.store.deleteRetrievalGarbage();
		this.store.deleteSubscribeGarbage();

		this.processPostalTable(that);
		this.processPublishTable(that);
		this.processRetrievalTable(that);
		this.processSubscribeTable(that);
	}

	/**
	 * Processes and delivers messages received from the gateway. - Verify the
	 * check sum for the payload is correct - Parse the payload into a message -
	 * Receive the message
	 * 
	 * @param instream
	 * @return was the message clean (true) or garbled (false).
	 */
	private boolean processResponse(Context context, AmmoGatewayMessage agm) {
		logger.trace("process response");

        if ( !agm.hasValidChecksum() ) {
            return false;
        }

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
		PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(INetPrefKeys.NET_CONN_PREF_IS_ACTIVE, true).commit();
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
	private void processPostalRequest(final AmmoService that, AmmoRequest ar) {
		logger.trace("process request POSTAL");

		// Dispatch the message.
		try {
			final String topic = ar.topic.asString();
			final DistributorPolicy.Topic policy = that.policy().matchPostal(topic);

			final ContentValues values = new ContentValues();
			values.put(PostalTableSchema.TOPIC.cv(), topic);
			values.put(PostalTableSchema.PROVIDER.cv(), ar.provider.cv());
			values.put(PostalTableSchema.PRIORITY.cv(), policy.routing.priority);
			values.put(PostalTableSchema.ORDER.cv(), ar.order.cv());
			values.put(PostalTableSchema.EXPIRATION.cv(), ar.expire.cv());

			values.put(PostalTableSchema.PRIORITY.cv(), ar.priority);
			values.put(PostalTableSchema.CREATED.cv(), System.currentTimeMillis());
			// values.put(PostalTableSchema.UNIT.cv(), 50);

			final DistributorState dispersal = policy.makeRouteMap();
			if (!that.isConnected()) {
				values.put(PostalTableSchema.DISPOSITION.cv(), RequestDisposal.NEW.cv());
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

			values.put(PostalTableSchema.DISPOSITION.cv(), RequestDisposal.DISTRIBUTE.cv());
			// We synchronize on the store to avoid a race between dispatch and
			// queuing
			synchronized (this.store) {
				final long id = this.store.upsertPostal(values, policy.makeRouteMap());
				final DistributorState dispatchResult = this.dispatchPostalRequest(that, 
						ar.provider.toString(), topic, dispersal, serializer, 
						new INetworkService.OnSendMessageHandler() {
							final DistributorThread parent = DistributorThread.this;
							final long id_ = id;
		
							@Override
							public boolean ack(String channel, ChannelDisposal status) {
								return parent.announceChannelAck(new ChannelAck(id_, Tables.POSTAL, channel, status));
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
	private void processPostalTable(final AmmoService that) {
		logger.debug(MARK_POSTAL, "process table POSTAL");

		if (!that.isConnected()) 
			return;

		final Cursor pending = this.store.queryPostalReady();

		// Iterate over each row serializing its data and sending it.
		for (boolean moreItems = pending.moveToFirst(); moreItems; 
				moreItems = pending.moveToNext()) 
		{
			final int id = pending.getInt(pending.getColumnIndex(PostalTableSchema._ID.n));
			final Provider provider = new Provider(pending.getString(pending.getColumnIndex(PostalTableSchema.PROVIDER.n)));
			final Payload payload = new Payload(pending.getString(pending.getColumnIndex(PostalTableSchema.PAYLOAD.n)));
			final String topic = pending.getString(pending.getColumnIndex(PostalTableSchema.TOPIC.n));

			logger.debug("serializing: {} as {}", provider,topic);

			final RequestSerializer serializer = RequestSerializer.newInstance(provider, payload);
			final int serialType = pending.getInt(pending.getColumnIndex(PostalTableSchema.ORDER.n));
			int dataColumnIndex = pending.getColumnIndex(PostalTableSchema.DATA.n);

			final String data;
			if (!pending.isNull(dataColumnIndex)) {
				data = pending.getString(dataColumnIndex);
			} else {
                                data = null;
                        }
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
                                              if (data_.length() > 0) {
						return data_.getBytes();
 					      } else {
 					        return null;
 					      }

					case INDIRECT:
					case DEFERRED:
					default:
						try {
							return RequestSerializer.serializeFromProvider(that_.getContentResolver(), 
									serializer_.provider.asUri(), encode);
						} catch (IOException e1) {
							logger.error("invalid row for serialization");
						} catch (TupleNotFoundException e) {
							logger.error("tuple not found when processing postal table");
							parent.store().deletePostal(new StringBuilder()
							        .append(PostalTableSchema.PROVIDER.q()).append("=?").append(" AND ")
									.append(PostalTableSchema.TOPIC.q()).append("=?").toString(), 
									new String[] {e.missingTupleUri.getPath(), topic});
						} catch (NonConformingAmmoContentProvider e) {
							e.printStackTrace();
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
					dispersal.put(channel, ChannelDisposal.getInstanceById(channelState));
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

					values.put(PostalTableSchema.DISPOSITION.cv(), RequestDisposal.DISTRIBUTE.cv());
					long numUpdated = this.store.updatePostalByKey(id, values, null);
					logger.debug("updated {} postal items", numUpdated);

					final DistributorState dispatchResult = 
							this.dispatchPostalRequest(that,
									provider.toString(), topic, 
									dispersal, serializer,
									new INetworkService.OnSendMessageHandler() {
										final DistributorThread parent = DistributorThread.this;
		                                final int id_ = id;
										@Override
										public boolean ack(String channel, ChannelDisposal status) {
											return parent.announceChannelAck( new ChannelAck(id_, Tables.POSTAL, channel, status) );
										}
									});
					this.store.updatePostalByKey(id, null, dispatchResult);
				}
			} catch (NullPointerException ex) {
				logger.warn("error sending to gateway {}", ex.getStackTrace());
			}
		}
		pending.close();
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
	private DistributorState dispatchPostalRequest(final AmmoService that, final String provider, final String msgType, final DistributorState dispersal, final RequestSerializer serializer, final INetworkService.OnSendMessageHandler handler) {
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

	// =========== PUBLICATION ====================

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
	@SuppressWarnings("unused")
	private void processPublishRequest(AmmoService that, AmmoRequest agm, int st) {
		logger.trace("process request PUBLISH : not implemented");
	}

	private void processPublishTable(AmmoService that) {
		logger.error("process table PUBLISH : not implemented");
	}

	/**
	 * dispatch the request to the network service. It is presumed that the
	 * connection to the network service exists before this method is called.
	 * 
	 * @param that
	 * @param uri
	 * @param mimeType
	 * @param data
	 * @param handler
	 * @return
	 */
	@SuppressWarnings("unused")
	private DistributorState dispatchPublishRequest(final AmmoService that, final String provider, final String msgType, final DistributorState dispersal, byte[] data, final INetworkService.OnSendMessageHandler handler) {
		logger.trace("::dispatchPublishRequest");
		return null;
	}

	/**
	 * Get response to PushRequest from the gateway. This should be seen in
	 * response to a post passing through transition for which a notice has been
	 * requested.
	 * 
	 * @param mw
	 * @return
	 */
	@SuppressWarnings("unused")
	private boolean receivePublishResponse(Context context, AmmoMessages.MessageWrapper mw) {
		logger.trace("receive response PUBLISH");

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
	private void processRetrievalRequest(AmmoService that, AmmoRequest ar) {
		logger.trace("process request RETRIEVAL {} {}", ar.topic.toString(), ar.provider.toString());

		// Dispatch the message.
		try {
			final String uuid = ar.uuid();
			final String topic = ar.topic.asString();
			final String select = ar.select.toString();
			final Integer limit = (ar.limit == null) ? null : ar.limit.asInteger();
			final DistributorPolicy.Topic policy = that.policy().matchRetrieval(topic);

			final ContentValues values = new ContentValues();
			values.put(RetrievalTableSchema.UUID.cv(), uuid);
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
				values.put(RetrievalTableSchema.DISPOSITION.cv(), RequestDisposal.NEW.cv());
				this.store.upsertRetrieval(values, dispersal);
				logger.debug("no network connection");
				return;
			}

			values.put(RetrievalTableSchema.DISPOSITION.cv(), RequestDisposal.DISTRIBUTE.cv());
			// We synchronize on the store to avoid a race between dispatch and
			// queuing
			synchronized (this.store) {
				final long id = this.store.upsertRetrieval(values, policy.makeRouteMap());

				final DistributorState dispatchResult = this.dispatchRetrievalRequest(that, 
						uuid, topic, select, limit, dispersal, 
						new INetworkService.OnSendMessageHandler() {
							final DistributorThread parent = DistributorThread.this;
							final long id_ = id;
		
							@Override
							public boolean ack(String channel, ChannelDisposal status) {
								return parent.announceChannelAck(new ChannelAck(id_, Tables.RETRIEVAL, channel, status));
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
	 * were and if necessary, send the data to the getNetworkServiceBinder()
	 * server.
	 * 
	 * Be careful about the race condition; don't leave gaps in the time line.
	 * Originally this method used time stamps to determine if the item had be
	 * sent. Now a status indicator is used.
	 * 
	 * Garbage collect items which are expired.
	 */
	private void processRetrievalTable(AmmoService that) {
		logger.debug(MARK_RETRIEVAL, "process table RETRIEVAL");

		final Cursor pending = this.store.queryRetrievalReady();

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
					dispersal.put(channel, ChannelDisposal.getInstanceById(channelState));
				}
				channelCursor.close();
			}

			final String uuid = pending.getString(pending.getColumnIndex(RetrievalTableSchema.UUID.cv()));
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

					values.put(RetrievalTableSchema.DISPOSITION.cv(), RequestDisposal.DISTRIBUTE.cv());
					@SuppressWarnings("unused")
					final long numUpdated = this.store.updateRetrievalByKey(id, values, null);

					final DistributorState dispatchResult = this.dispatchRetrievalRequest(that, 
							uuid, topic, selection, limit, dispersal, 
							new INetworkService.OnSendMessageHandler() {
								final DistributorThread parent = DistributorThread.this;
		
								@Override
								public boolean ack(String channel, ChannelDisposal status) {
									return parent.announceChannelAck(new ChannelAck(id, Tables.RETRIEVAL, channel, status));
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
			final String retrievalId, final String topic, 
			final String selection, final Integer limit, final DistributorState dispersal, 
			final INetworkService.OnSendMessageHandler handler) 
	{
		logger.trace("dispatch request RETRIEVAL {}", topic);

		/** Message Building */

		// mw.setSessionUuid(sessionId);

		final AmmoMessages.PullRequest.Builder retrieveReq = AmmoMessages.PullRequest
				.newBuilder()
				.setRequestUid(retrievalId)
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
	private void processSubscribeRequest(AmmoService that, AmmoRequest agm, int st) {
		logger.trace("process request SUBSCRIBE {}", agm.topic.toString());

		// Dispatch the message.
		try {
			final String topic = agm.topic.asString();
			final DistributorPolicy.Topic policy = that.policy().matchSubscribe(topic);

			final ContentValues values = new ContentValues();
			values.put(SubscribeTableSchema.TOPIC.cv(), topic);
			values.put(SubscribeTableSchema.PROVIDER.cv(), agm.provider.cv());
			values.put(SubscribeTableSchema.SELECTION.cv(), agm.select.toString());
			values.put(SubscribeTableSchema.EXPIRATION.cv(), agm.expire.cv());
			values.put(SubscribeTableSchema.PRIORITY.cv(), policy.routing.priority);
			values.put(SubscribeTableSchema.CREATED.cv(), System.currentTimeMillis());

			final DistributorState dispersal = policy.makeRouteMap();
			if (!that.isConnected()) {
				values.put(SubscribeTableSchema.DISPOSITION.cv(), RequestDisposal.NEW.cv());
				long key = this.store.upsertSubscribe(values, dispersal);
				logger.debug("no network connection, added {}", key);
				return;
			}

			values.put(SubscribeTableSchema.DISPOSITION.cv(), RequestDisposal.DISTRIBUTE.cv());
			// We synchronize on the store to avoid a race between dispatch and
			// queuing
			synchronized (this.store) {
				final long id = this.store.upsertSubscribe(values, dispersal);
				final DistributorState dispatchResult = this.dispatchSubscribeRequest(that, 
						topic, agm.select.toString(), dispersal, 
						new INetworkService.OnSendMessageHandler() {
							final DistributorThread parent = DistributorThread.this;
							final long id_ = id;
		
							@Override
							public boolean ack(String channel, ChannelDisposal status) {
								return parent.announceChannelAck(new ChannelAck(id_, Tables.SUBSCRIBE, channel, status));
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
	 * changes were and if necessary, send the data to the
	 * getNetworkServiceBinder() server.
	 * 
	 * Be careful about the race condition; don't leave gaps in the time line.
	 * Originally this method used time stamps to determine if the item had be
	 * sent. Now a status indicator is used.
	 * 
	 * Garbage collect items which are expired.
	 */

	private void processSubscribeTable(AmmoService that) {
		logger.debug(MARK_SUBSCRIBE, "process table SUBSCRIBE");

		final Cursor pending = this.store.querySubscribeReady();

		for (boolean areMoreItems = pending.moveToFirst(); areMoreItems; areMoreItems = pending.moveToNext()) {
			// For each item in the cursor, ask the content provider to
			// serialize it, then pass it off to the NPS.
			final int id = pending.getInt(pending.getColumnIndex(SubscribeTableSchema._ID.n));
			final String topic = pending.getString(pending.getColumnIndex(SubscribeTableSchema.TOPIC.cv()));

			final String selection = pending.getString(pending.getColumnIndex(SubscribeTableSchema.SELECTION.n));

			logger.trace(MARK_SUBSCRIBE, "process row SUBSCRIBE {} {} {}", new Object[] { id, topic, selection });

			final DistributorPolicy.Topic policy = that.policy().matchSubscribe(topic);
			final DistributorState dispersal = policy.makeRouteMap();
			{
				final Cursor channelCursor = this.store.queryDisposalByParent(Tables.SUBSCRIBE.o, id);
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; moreChannels = channelCursor.moveToNext()) {
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short channelState = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					dispersal.put(channel, ChannelDisposal.getInstanceById(channelState));
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

					values.put(SubscribeTableSchema.DISPOSITION.cv(), RequestDisposal.DISTRIBUTE.cv());
					@SuppressWarnings("unused")
					long numUpdated = this.store.updateSubscribeByKey(id, values, null);

					final DistributorState dispatchResult = this.dispatchSubscribeRequest(that, 
							topic, selection, dispersal, 
							new INetworkService.OnSendMessageHandler() {
								final DistributorThread parent = DistributorThread.this;
								final int id_ = id;
		
								@Override
								public boolean ack(String channel, ChannelDisposal status) {
									return parent.announceChannelAck(new ChannelAck(id_, Tables.SUBSCRIBE, channel, status));
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
	private DistributorState dispatchSubscribeRequest(final AmmoService that, final String topic, final String selection, final DistributorState dispersal, final INetworkService.OnSendMessageHandler handler) {
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

		logger.trace("receive response SUBSCRIBE : {}", mime );
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
		RequestSerializer.deserializeToProvider(context, provider, encoding, data.toByteArray());

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

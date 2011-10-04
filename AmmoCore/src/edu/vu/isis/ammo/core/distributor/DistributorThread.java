package edu.vu.isis.ammo.core.distributor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import net.jcip.annotations.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.AmmoService.ChannelChange;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
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
 * The distributor service runs in the ui thread.
 * This establishes a new thread for distributing the requests.
 * 
 */
@ThreadSafe
public class DistributorThread 
extends AsyncTask<AmmoService, Integer, Void> 
{
	// ===========================================================
	// Constants
	// ===========================================================
	private static final Logger logger = LoggerFactory.getLogger("ammo-dst");

	// 20 seconds expressed in milliseconds
	private static final int BURP_TIME = 20 * 1000;

	/**
	 * The backing store for the distributor
	 */
	final private DistributorDataStore store;

	public DistributorThread(Context context) {
		super();
		this.requestQueue = new LinkedBlockingQueue<AmmoRequest>(20);
		this.responseQueue = 
				new PriorityBlockingQueue<AmmoGatewayMessage>(20, 
						new AmmoGatewayMessage.PriorityOrder());
		this.store = new DistributorDataStore(context);
		this.channelStatus = new ConcurrentHashMap<String, ChannelStatus>();
		logger.debug("constructed");
	}


	/**
	 * This method is *not* called from the distributor thread so it
	 * should not update the store directly.
	 * Rather it updates a map which records the same information
	 * found in the store's channel table.
	 * 
	 * @param context
	 * @param name
	 * @param change
	 */
	public void onChannelChange(final Context context, final String name, final ChannelChange change) {
		if (change.equals(this.channelStatus.get(name))) return; // already present
		this.channelStatus.put(name, new ChannelStatus(change)); // change channel
		if (!channelDelta.compareAndSet(false, true)) return; // mark as needing processing
		this.signal(); // signal to perform update
	}	
	/**
	 * When a channel comes on-line the disposition table should
	 * be checked to see if there are any waiting messages for 
	 * that channel.
	 * Channels going off-line are uninteresting so no signal.
	 */
	private final ConcurrentMap<String, ChannelStatus> channelStatus;
	
	private AtomicBoolean channelDelta = new AtomicBoolean(true);

	// indicates the table does not yet reflect this state
	private class ChannelStatus {
		final ChannelChange change;
		final AtomicBoolean status;
		public ChannelStatus(final ChannelChange change) {
			this.change = change;
			this.status = new AtomicBoolean(false); 			
		}
	}

	private void announceChannelActive(final Context context, final String name) {
		logger.trace("inform applications to retrieve more data");

		// TBD SKN --- the channel activates repeatedly due to status change
		//         --- do not use this to generate ammo_connected intent
		//         --- generate ammo_connected after gateway authenticated
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
			logger.trace("received request of type {}", 
					request.toString());

			// TODO should we generate the uuid here or earlier?
			this.requestQueue.put(request);
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

	public boolean distributeResponse(AmmoGatewayMessage agm)
	{
		this.responseQueue.put(agm);
		this.signal();
		return true;
	}

	private void signal() {
		synchronized(this) { 
			this.notifyAll(); 
		}
	}

	/**
	 * Check to see if there is any work for the thread to do.
	 * If there are no network connections then nothing can be distributed, so no work.
	 * Either incoming requests, responses, or a channel has been activated.
	 */
	private boolean isReady(AmmoService[] them) {
		if (this.channelDelta.get()) return true;

		if (! this.responseQueue.isEmpty()) return true;
		if (! this.requestQueue.isEmpty()) return true;
		return false;
	}

	/**
	 * The following condition wait holds until
	 * there is some work for the distributor.
	 * 
	 * The method tries to be fair processing the requests in 
	 */
	@Override
	protected Void doInBackground(AmmoService... them) {

		logger.info("started");

		for (final AmmoService that : them) {
			if (!that.isConnected()) 
				continue;

			// TBD SKN: cleanup all channel entries first
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

					if (!this.responseQueue.isEmpty()) {
						logger.trace("processing response, remaining {}", this.responseQueue.size());
						try {
							final AmmoGatewayMessage agm = this.responseQueue.take();
							for (AmmoService that : them) {
								this.processResponse(that, agm);
							}
						} catch (ClassCastException ex) {
							logger.error("response queue contains illegal item of class {}", 
									ex.getLocalizedMessage());
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
							logger.error("request queue contains illegal item of class {}", 
									ex.getLocalizedMessage());
						}
					}
				}
				logger.info("work processed"); 
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
	 *  Processes and delivers messages received from the gateway.
	 *  - Verify the check sum for the payload is correct
	 *  - Parse the payload into a message
	 *  - Receive the message
	 *
	 * @param instream
	 * @return was the message clean (true) or garbled (false).
	 */
	private boolean processRequest(AmmoService that, AmmoRequest agm) {
		logger.info("::processRequest {}",agm);
		switch (agm.action){
		case POSTAL: processPostalRequest(that, agm); break;
		case DIRECTED_POSTAL: processPostalRequest(that, agm); break;
		case PUBLISH: break;
		case RETRIEVAL: processRetrievalRequest(that, agm); break;
		case SUBSCRIBE: processSubscribeRequest(that, agm, 1); break;
		case DIRECTED_SUBSCRIBE: processSubscribeRequest(that, agm, 2); break;
		}
		return true;
	}

	/**
	 * Check to see if the active channels can be used to send a request.
	 * This updates the channel status table.
	 */
	private void processChannelChange(AmmoService that) {
	    logger.info("::processChannelChange()");

	    if (!that.isConnected()) 
		return;
		
	    for (final Map.Entry<String, ChannelStatus> entry : channelStatus.entrySet()) {
		final String name = entry.getKey();		
		final ChannelStatus status = entry.getValue();
		logger.trace("processChannelChange: {} {}", name, status);
		if (status.status.getAndSet(true)) continue; // is it this one?

			
		final ChannelChange change = status.change;
		switch (change) {
		case DEACTIVATE:
		    this.store.upsertChannelByName(name, ChannelState.INACTIVE);
		    this.store.deactivateDisposalStateByChannel(name);
		    logger.trace("::channel deactivated");
		    return;

		case ACTIVATE:
		    this.store.upsertChannelByName(name, ChannelState.ACTIVE);
		    this.store.activateDisposalStateByChannel(name);
		    this.announceChannelActive(that.getBaseContext(), name);
		    logger.trace("::channel activated");
		    this.store.deletePostalGarbage();
		    this.store.deletePublishGarbage();
		    this.store.deleteRetrievalGarbage();
		    this.store.deleteSubscribeGarbage();	

		    this.processPostalTable(that);
		    this.processPublishTable(that);
		    this.processRetrievalTable(that);
		    this.processSubscribeTable(that);

		    return;

		case REPAIR: 
		    this.store.upsertChannelByName(name, ChannelState.ACTIVE);
		    this.store.repairDisposalStateByChannel(name);
		    this.announceChannelActive(that.getBaseContext(), name);
		    logger.trace("::channel repaired");
		    return;
		} 
	    }

	    // we could do a priming query to determine if there are any candidates
	}

	/**
	 * Attempt to satisfy the distribution policy for this message's topic.
	 * 
	 * The policies are processed in "short circuit" disjunctive normal form.
	 * Each clause is evaluated there results are 'anded' together, hence disjunction. 
	 * Within a clause each literal is handled in order until one matches
	 * its prescribed condition, effecting a short circuit conjunction.
	 * (It is short circuit as only the first literal to be true is evaluated.)
	 * 
	 * In order for a topic to evaluate to success all of its clauses must evaluate true.
	 * In order for a clause to be true at least (exactly) one of its literals must succeed.
	 * A literal is true if the condition of the term matches the 
	 * 'condition' attribute for the term.   
	 * 
	 * @see scripts/tests/distribution_policy.xml for an example.
	 */
	private DispersalVector
	dispatchRequest(AmmoService that, 
			RequestSerializer serializer, DistributorPolicy.Topic topic, DispersalVector status) {

		logger.info("::sendGatewayRequest");
		if (status == null) status = DispersalVector.newInstance();

		if (topic == null) {
			logger.error("no matching routing topic");
			final AmmoGatewayMessage agmb = serializer.act(Encoding.getDefault());
			final DisposalState actualCondition =
					that.sendRequest(agmb, DistributorPolicy.DEFAULT, topic);
			status.put(DistributorPolicy.DEFAULT, actualCondition);
			return status;
		} 
		// evaluate rule
		for (DistributorPolicy.Clause clause : topic.routing.clauses) {
			boolean clauseSuccess = false;
			// evaluate clause
			for (DistributorPolicy.Literal literal : clause.literals) {
				final String term = literal.term;
				final boolean goalCondition = literal.condition;
				final DisposalState actualCondition;
				if (status.containsKey(term)) {
					actualCondition = status.get(term);
				} else {
					final AmmoGatewayMessage agmb = serializer.act(literal.encoding);
					actualCondition = that.sendRequest(agmb, term, topic);
					status.put(term, actualCondition);
				} 
				if (actualCondition.goalReached(goalCondition)) {
					clauseSuccess = true;
					break;
				}
			}
			status.and(clauseSuccess);
		}
		return status;
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

		final CRC32 crc32 = new CRC32();
		crc32.update(agm.payload);
		if (crc32.getValue() != agm.payload_checksum) {
			logger.warn("you have received a bad message, the checksums [{}:{}] did not match",
					Long.toHexString(crc32.getValue()), Long.toHexString(agm.payload_checksum));
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
			logger.error( "mw was null!" );
			return false; // TBD SKN: this was true, why? if we can't parse it then its bad
		}
		final MessageType mtype = mw.getType();
		if (mtype == MessageType.HEARTBEAT) {
			logger.trace("heartbeat");
			return true;
		}

		switch (mw.getType()) {
		case DATA_MESSAGE:
			final boolean subscribeResult = receiveSubscribeResponse(context, mw);
			logger.debug("subscribe reply {}", subscribeResult);
			break;

		case AUTHENTICATION_RESULT:
			final boolean result = receiveAuthenticateResponse(context, mw);
			logger.debug( "authentication result={}", result );
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
			logger.debug( "{} message, no processing", mw.getType());
			break;
		default:
			logger.error( "unexpected resply type. {}", mw.getType());
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
		logger.info("::receiveAuthenticateResponse");

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


	@SuppressWarnings("unused")
	private boolean collectGarbage = true;

	// =========== POSTAL ====================

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
	private void processPostalRequest(final AmmoService that, AmmoRequest ar) {
		logger.info("::processPostalRequest()");

		// Dispatch the message.
		try {
			final String topic = ar.topic.asString();
			final DistributorPolicy.Topic policy = that.policy().match(topic);

			final ContentValues values = new ContentValues();
			values.put(PostalTableSchema.TOPIC.cv(), topic);
			values.put(PostalTableSchema.PROVIDER.cv(), ar.provider.cv());
			values.put(PostalTableSchema.ORDER.cv(), ar.order.cv());
			values.put(PostalTableSchema.EXPIRATION.cv(), ar.expire.cv());

			values.put(PostalTableSchema.PRIORITY.cv(), ar.priority);
			values.put(PostalTableSchema.CREATED.cv(), System.currentTimeMillis());
			//values.put(PostalTableSchema.UNIT.cv(), 50);

			if (!that.isConnected()) {
				values.put(PostalTableSchema.DISPOSITION.cv(), DisposalState.PENDING.cv());
				long key = this.store.upsertPostal(values, policy.makeRouteMap());
				logger.info("no network connection, added {}", key);
				return;
			}

			final RequestSerializer serializer = RequestSerializer.newInstance(ar.provider, ar.payload);
			serializer.setSerializer( new RequestSerializer.OnSerialize() {
				@Override
				public byte[] run(Encoding encode) {
					if (serializer.payload.hasContent()) {
						return serializer.payload.asBytes();
					} else {
						try {
							return RequestSerializer.serializeFromProvider(that.getContentResolver(), 
									serializer.provider.asUri(), encode);
						} catch (IOException e1) {
							logger.error("invalid row for serialization {}",
									e1.getLocalizedMessage());
							return null;
						}
					}			
				}
			});

			values.put(PostalTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
			// We synchronize on the store to avoid a race between dispatch and queuing
			synchronized (this.store) {
				final long id = this.store.upsertPostal(values, policy.makeRouteMap());
				final DispersalVector dispatchResult = 
						this.dispatchPostalRequest(that,
								ar.provider.toString(),
								topic, policy, null, serializer,
								new INetworkService.OnSendMessageHandler() {

							@Override
							public boolean ack(String channel, DisposalState status) {
								synchronized (DistributorThread.this.store) {
									DistributorThread.this.store.upsertDisposalByParent(id, Tables.POSTAL, channel, status);
								}
								return true;
							}
						});
				this.store.upsertDisposalByParent(id, Tables.POSTAL, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("NullPointerException, sending to gateway failed {}",
					ex.getStackTrace() );
		}
	}


	/**
	 * Check for requests whose delivery policy has not been fully satisfied
	 * and for which there is, now, an available channel.
	 */
	private void processPostalTable(final AmmoService that) {
		logger.info("::processPostalTable()");

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

			logger.debug("serializing: {} as {}",provider,topic);

			final RequestSerializer serializer = RequestSerializer.newInstance(provider, payload);
			final int serialType = pending.getInt(pending.getColumnIndex(PostalTableSchema.ORDER.n));
			serializer.setSerializer( new RequestSerializer.OnSerialize() {
				@Override
				public byte[] run(Encoding encode) {

					switch (SerializeType.getInstance(serialType)) {
					case DIRECT:
						int dataColumnIndex = pending.getColumnIndex(PostalTableSchema.DATA.n);

						if (!pending.isNull(dataColumnIndex)) {
							String data = pending.getString(dataColumnIndex);
							return data.getBytes();
						} else {
							// TODO handle the case where data is null
							// that signifies there is a file containing the data
							;
						}
						break;

					case INDIRECT:
					case DEFERRED:
					default:
						try {
							return RequestSerializer.serializeFromProvider(that.getContentResolver(), 
									serializer.provider.asUri(), encode);
						} catch (IOException e1) {
							logger.error("invalid row for serialization");
						}
					}
					logger.error("no serialized data produced");
					return null;
				}
			});

			final DistributorPolicy.Topic policy = that.policy().match(topic);
			final DispersalVector status = DispersalVector.newInstance();
			{
				final Cursor channelCursor = this.store.queryDisposalReady(id,"postal");
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; 
						moreChannels = channelCursor.moveToNext()) 
				{
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short state = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					status.put(channel, DisposalState.values()[state]);
				}
				channelCursor.close();
			}
			// Dispatch the request.
			try {
				if (!that.isConnected()) {
					logger.info("no network connection");
					continue;
				} 
				synchronized (this.store) {
					final ContentValues values = new ContentValues();

					values.put(PostalTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
					long numUpdated = this.store.updatePostalByKey(id, values);
					logger.debug("updated {} postal items", numUpdated);

					final DispersalVector dispatchResult = 
							this.dispatchPostalRequest(that,
									provider.toString(),
									topic, policy, status, serializer,
									new INetworkService.OnSendMessageHandler() {

								@Override
								public boolean ack(String clazz, DisposalState status) {

									ContentValues values = new ContentValues();

									values.put(PostalTableSchema.DISPOSITION.cv(), status.cv());
									long numUpdated = DistributorThread.this.store.updatePostalByKey(id, values);

									logger.info("Postal: {} rows updated to {}",
											numUpdated, status);

									return false;
								}
							});
					this.store.upsertDisposalByParent(id, Tables.POSTAL, dispatchResult);
				}
			} catch (NullPointerException ex) {
				logger.warn("error sending to gateway {}", ex.getStackTrace());
			}
		}
		pending.close();
	}

	/**
	 * dispatch the request to the network service.
	 * It is presumed that the connection to the network service
	 * exists before this method is called.
	 * 
	 * @param that
	 * @param uri
	 * @param msgType
	 * @param data
	 * @param handler
	 * @return
	 */
	private DispersalVector 
	dispatchPostalRequest(final AmmoService that, final String uri, 
			final String msgType,
			final DistributorPolicy.Topic policy, final DispersalVector status,
			final RequestSerializer serializer, final INetworkService.OnSendMessageHandler handler)  {
		logger.info("::dispatchPostalRequest");

		final Long now = System.currentTimeMillis();
		logger.debug("Building MessageWrapper @ time {}", now);

		final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType(AmmoMessages.MessageWrapper.MessageType.DATA_MESSAGE);

		serializer.setAction(new RequestSerializer.OnReady() {
			@Override
			public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {
				final AmmoMessages.DataMessage.Builder pushReq = AmmoMessages.DataMessage.newBuilder()
						.setUri(uri)
						.setMimeType(msgType)
						.setEncoding(encode.getPayload().name())
						.setData(ByteString.copyFrom(serialized));
				mw.setDataMessage(pushReq);

				logger.debug("Finished wrap build @ timeTaken {} ms, serialized-size={} \n",
						System.currentTimeMillis()-now, serialized.length);
				final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder( mw, handler);
				return agmb.build();
			}
		});	
		return this.dispatchRequest(that, serializer, policy, status);
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
		logger.info("::receivePostalResponse");

		if (mw == null) return false;
		if (! mw.hasPushAcknowledgement()) return false;
		// PushAcknowledgement pushResp = mw.getPushAcknowledgement();
		return true;
	}


	// =========== PUBLICATION ====================

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
	private void processPublishRequest(AmmoService that, AmmoRequest agm, int st) {
		logger.info("::processPublicationRequest()");
	}

	private void processPublishTable(AmmoService that) {
		logger.error("::processPublishTable : not implemented");
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
	private Map<String,Boolean> 
	dispatchPublishRequest(AmmoService that, String uri, String mimeType, 
			Map<String,Boolean> status,
			byte []data, INetworkService.OnSendMessageHandler handler) {
		logger.info("::dispatchPublishRequest");
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
	private boolean receivePublishResponse(Context context, AmmoMessages.MessageWrapper mw) {
		logger.info("::receivePublishResponse");

		if (mw == null) return false;
		if (! mw.hasPushAcknowledgement()) return false;
		// PushAcknowledgement pushResp = mw.getPushAcknowledgement();
		return true;
	}


	// =========== RETRIEVAL ====================


	/**
	 * Process the retrieval request.
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
	private void processRetrievalRequest(AmmoService that, AmmoRequest agm) {
		logger.info("::processRetrievalRequest() {} {}", agm.topic.toString(), agm.provider.toString() );

		// Dispatch the message.
		try {
			final String uuid = agm.uuid();
			final String topic = agm.topic.asString();
			final String select = agm.select.toString();
			final DistributorPolicy.Topic policy = that.policy().match(topic);

			final ContentValues values = new ContentValues();
			values.put(RetrievalTableSchema.UUID.cv(), uuid);
			values.put(RetrievalTableSchema.TOPIC.cv(), topic);
			values.put(RetrievalTableSchema.SELECTION.cv(), select);

			values.put(RetrievalTableSchema.PROVIDER.cv(), agm.provider.cv());		
			values.put(RetrievalTableSchema.EXPIRATION.cv(), agm.expire.cv());
			values.put(RetrievalTableSchema.UNIT.cv(), 50);
			values.put(RetrievalTableSchema.PRIORITY.cv(), agm.priority);
			values.put(RetrievalTableSchema.CREATED.cv(), System.currentTimeMillis());

			if (!that.isConnected()) {
				values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalState.PENDING.cv());
				this.store.upsertRetrieval(values, policy.makeRouteMap());
				logger.info("no network connection");
				return;
			}

			values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
			// We synchronize on the store to avoid a race between dispatch and queuing
			synchronized (this.store) {
				final long id = this.store.upsertRetrieval(values, policy.makeRouteMap());

				final DispersalVector dispatchResult = 
						this.dispatchRetrievalRequest(that,
								uuid, topic, select, policy, DispersalVector.newInstance(),
								new INetworkService.OnSendMessageHandler() {

							@Override
							public boolean ack(String channel, DisposalState status) {
								synchronized (DistributorThread.this.store) {
									DistributorThread.this.store.upsertDisposalByParent(id, Tables.RETRIEVAL, channel, status);
								}
								return false;
							}
						});
				this.store.upsertDisposalByParent(id, Tables.RETRIEVAL, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("NullPointerException, sending to gateway failed {}", ex.getStackTrace());
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
	private void processRetrievalTable(AmmoService that) {
		logger.info("::processRetrievalTable()");

		final Cursor pending = this.store.queryRetrievalReady();

		for (boolean areMoreItems = pending.moveToFirst(); areMoreItems;
				areMoreItems = pending.moveToNext()) 
		{
			// For each item in the cursor, ask the content provider to
			// serialize it, then pass it off to the NPS.
			final int id = pending.getInt(pending.getColumnIndex(RetrievalTableSchema._ID.n));
			final DispersalVector status = DispersalVector.newInstance();
			{
				final Cursor channelCursor = this.store.queryDisposalReady(id,"retrieval");
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; 
						moreChannels = channelCursor.moveToNext()) 
				{
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short state = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					status.put(channel, DisposalState.values()[state]);
				}
				channelCursor.close();
			}

			final String uuid = pending.getString(pending.getColumnIndex(RetrievalTableSchema.UUID.cv()));
			final String topic = pending.getString(pending.getColumnIndex(RetrievalTableSchema.TOPIC.cv()));		
			final String selection = pending.getString(pending.getColumnIndex(RetrievalTableSchema.SELECTION.n));
			try {
				if (!that.isConnected()) {
					logger.info("no network connection");
					continue;
				} 
				synchronized (this.store) {
					final ContentValues values = new ContentValues();
					final DistributorPolicy.Topic policy = that.policy().match(topic);

					values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
					@SuppressWarnings("unused")
					final long numUpdated = this.store.updateRetrievalByKey(id, values);

					final DispersalVector dispatchResult = 
							this.dispatchRetrievalRequest(that,
									uuid, topic, selection,
									policy, status,
									new INetworkService.OnSendMessageHandler() {

								@Override
								public boolean ack(String clazz, DisposalState status) {

									ContentValues values = new ContentValues();

									values.put(RetrievalTableSchema.DISPOSITION.cv(), status.cv());
									long numUpdated = DistributorThread.this.store.updateRetrievalByKey(id, values);

									logger.info("Retrieval: {} rows updated to {}",
											numUpdated, status);

									return false;
								}
							});
					this.store.upsertDisposalByParent(id, Tables.RETRIEVAL, dispatchResult);
				}
			} catch (NullPointerException ex) {
				logger.warn("NullPointerException, sending to gateway failed {}", ex.getStackTrace());
			}
		}
		pending.close();
	}


	/**
	 * The retrieval request is sent to 
	 * @param that
	 * @param retrievalId
	 * @param topic
	 * @param selection
	 * @param handler
	 * @return
	 */

	private DispersalVector 
	dispatchRetrievalRequest(final AmmoService that, String retrievalId, String topic, String selection,  
			DistributorPolicy.Topic policy, DispersalVector status,
			final INetworkService.OnSendMessageHandler handler) {
		logger.info("::dispatchRetrievalRequest {}", topic);

		/** Message Building */

		final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType(AmmoMessages.MessageWrapper.MessageType.PULL_REQUEST);
		//mw.setSessionUuid(sessionId);

		final AmmoMessages.PullRequest.Builder pushReq = AmmoMessages.PullRequest.newBuilder()
				.setRequestUid(retrievalId)
				.setMimeType(topic);

		if (selection != null) pushReq.setQuery(selection);

		// projection
		// max_results
		// start_from_count
		// live_query
		// expiration
		try {
			mw.setPullRequest(pushReq);
			final RequestSerializer serializer = RequestSerializer.newInstance();
			serializer.setAction(new RequestSerializer.OnReady() {
				@Override
				public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {
					final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder( mw, handler);
					return agmb.build();
				}
			});	
			return this.dispatchRequest(that, serializer, policy, status);
		} catch (com.google.protobuf.UninitializedMessageException ex) {
			logger.warn("Failed to marshal the message: {}", ex.getStackTrace() );
		}
		return status;

	}


	/**
	 * Get response to RetrievalRequest, PullResponse, from the gateway.
	 *
	 * @param mw
	 * @return
	 */
	private boolean receiveRetrievalResponse(Context context, AmmoMessages.MessageWrapper mw) {	
		if (mw == null) return false;
		if (! mw.hasPullResponse()) return false;
		logger.info("::receiveRetrievalResponse");

		final AmmoMessages.PullResponse resp = mw.getPullResponse();

		// find the provider to use
		final String uuid = resp.getRequestUid(); 
		final String topic = resp.getMimeType();
		final Cursor cursor = this.store.queryRetrievalByKey(
				new String[]{RetrievalTableSchema.PROVIDER.n}, 
				uuid, topic, null);
		if (cursor.getCount() < 1) {
			logger.error("received a message for which there is no retrieval {} {}", topic, uuid);
			cursor.close();
			return false;
		}
		cursor.moveToFirst();
		final String uriString = cursor.getString(0);  // only asked for one so it better be it.
		cursor.close();
		final Uri provider = Uri.parse(uriString);

		// update the actual provider

		final Encoding encoding = Encoding.getInstanceByName(resp.getEncoding());
		final Uri tuple = RequestSerializer.deserializeToProvider(context.getContentResolver(), provider, encoding, resp.getData().toByteArray());
		logger.debug("tuple upserted {}", tuple);

		return true;
	}

	// =========== SUBSCRIBE ====================


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
	private void processSubscribeRequest(AmmoService that, AmmoRequest agm, int st) {
		logger.info("::processSubscribeRequest() {}", agm.topic.toString() );

		// Dispatch the message.
		try {
			final String topic = agm.topic.asString();
			final DistributorPolicy.Topic policy = that.policy().match(topic);

			final ContentValues values = new ContentValues();
			values.put(SubscribeTableSchema.TOPIC.cv(), topic);
			values.put(SubscribeTableSchema.PROVIDER.cv(), agm.provider.cv());
			values.put(SubscribeTableSchema.SELECTION.cv(), agm.select.toString());
			values.put(SubscribeTableSchema.EXPIRATION.cv(), agm.expire.cv());
			values.put(SubscribeTableSchema.PRIORITY.cv(), agm.priority);
			values.put(SubscribeTableSchema.CREATED.cv(), System.currentTimeMillis());

			if (!that.isConnected()) {
				values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalState.PENDING.cv());
				long key = this.store.upsertSubscribe(values, policy.makeRouteMap());
				logger.info("no network connection, added {}", key);
				return;
			}

			values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
			// We synchronize on the store to avoid a race between dispatch and queuing
			synchronized (this.store) {
				final long id = this.store.upsertSubscribe(values, policy.makeRouteMap());
				final DispersalVector dispatchResult = 
						this.dispatchSubscribeRequest(that,
								topic, agm.select.toString(),
								policy, null,
								new INetworkService.OnSendMessageHandler() {

							@Override
							public boolean ack(String channel, DisposalState status) {
								synchronized (DistributorThread.this.store) {
									DistributorThread.this.store.upsertDisposalByParent(id, Tables.SUBSCRIBE, channel, status);
								}
								return true;
							}
						});
				this.store.upsertDisposalByParent(id, Tables.SUBSCRIBE, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("NullPointerException, sending to gateway failed {}",
					ex.getStackTrace());
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

	private void processSubscribeTable(AmmoService that) {
		logger.info("::processSubscribeTable()");

		final Cursor pending = this.store.querySubscribeReady();

		for (boolean areMoreItems = pending.moveToFirst(); areMoreItems;
				areMoreItems = pending.moveToNext()) 
		{
			logger.info("Inside the loop of processSubscribeResponse");
			// For each item in the cursor, ask the content provider to
			// serialize it, then pass it off to the NPS.
			final int id = pending.getInt(pending.getColumnIndex(SubscribeTableSchema._ID.n));
			final String topic = pending.getString(pending.getColumnIndex(SubscribeTableSchema.TOPIC.cv()));

			final String selection = pending.getString(pending.getColumnIndex(SubscribeTableSchema.SELECTION.n));

			logger.info("Inside the loop of processSubscribeResponse {} {} {}", new Object[]{id, topic, selection});

			final DispersalVector status = DispersalVector.newInstance();
			{
				final Cursor channelCursor = this.store.queryDisposalReady(id,"subscribe");
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; 
						moreChannels = channelCursor.moveToNext()) 
				{
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short state = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					status.put(channel, DisposalState.values()[state]);
				}
				channelCursor.close();
			}

			try {
				if (!that.isConnected()) {
					logger.info("no network connection");
					continue;
				} 
				synchronized (this.store) {
					final ContentValues values = new ContentValues();
					final DistributorPolicy.Topic policy = that.policy().match(topic);

					values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
					@SuppressWarnings("unused")
					long numUpdated = this.store.updateSubscribeByKey(id, values);

					final DispersalVector dispatchResult = 
							this.dispatchSubscribeRequest(that,
									topic, selection,
									policy, status, 
									new INetworkService.OnSendMessageHandler() {

								@Override
								public boolean ack(String clazz, DisposalState status) {

									final ContentValues values = new ContentValues();

									values.put(SubscribeTableSchema.DISPOSITION.cv(), status.cv());
									long numUpdated = DistributorThread.this.store.updateSubscribeByKey(id, values);

									logger.info("Subscribe: {} rows updated to {}",
											numUpdated, status);

									return true;
								}
							});
					this.store.upsertDisposalByParent(id, Tables.SUBSCRIBE, dispatchResult);
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
	private DispersalVector 
	dispatchSubscribeRequest(final AmmoService that, String topic, 
			String selection, DistributorPolicy.Topic policy, DispersalVector status,
			final INetworkService.OnSendMessageHandler handler) {
		logger.info("::dispatchSubscribeRequest {}", topic);

		/** Message Building */
		final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType(AmmoMessages.MessageWrapper.MessageType.SUBSCRIBE_MESSAGE);
		//mw.setSessionUuid(sessionId);

		final AmmoMessages.SubscribeMessage.Builder subscribeReq = AmmoMessages.SubscribeMessage.newBuilder();
		subscribeReq.setMimeType(topic);

		if (subscribeReq != null) subscribeReq.setQuery(selection);

		mw.setSubscribeMessage(subscribeReq);

		final RequestSerializer serializer = RequestSerializer.newInstance();
		serializer.setAction(new RequestSerializer.OnReady() {
			@Override
			public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {
				final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder( mw, handler);
				return agmb.build();
			}
		});	
		return this.dispatchRequest(that, serializer, policy, status);
	}


	/**
	 * Update the content providers as appropriate. 
	 * These are typically received in response to subscriptions.
	 * In other words these replies are postal messages which have been redirected.
	 * 
	 * The subscribing uri isn't sent with the subscription to the gateway
	 * therefore it needs to be recovered from the subscription table.
	 */
	private boolean receiveSubscribeResponse(Context context, AmmoMessages.MessageWrapper mw) {
		if (mw == null) {
			logger.warn("no message");
			return false;
		}
		if (! mw.hasDataMessage()) {
			logger.warn("no data in message");
			return false;
		}
		final AmmoMessages.DataMessage resp = mw.getDataMessage();
		// final ContentResolver resolver = context.getContentResolver();

		logger.info("::dispatchSubscribeResponse : {} : {}",
				resp.getMimeType(), resp.getUri());
		final String topic = resp.getMimeType();
		final Cursor cursor = this.store.querySubscribeByKey(
				new String[]{SubscribeTableSchema.PROVIDER.n}, 
				topic, null);
		if (cursor.getCount() < 1) {
			logger.error("received a message for which there is no subscription {}", topic);
			cursor.close();
			return false;
		}
		cursor.moveToFirst();
		final String uriString = cursor.getString(0);  // only asked for one so it better be it.
		cursor.close();
		final Uri provider = Uri.parse(uriString);

		final Encoding encoding = Encoding.getInstanceByName(resp.getEncoding());
		RequestSerializer.deserializeToProvider(context.getContentResolver(), provider, encoding, resp.getData().toByteArray());

		return true;
	}


	/**
	 * Clear the contents of tables in preparation for reloading them.
	 * This is predominantly *not* for postal which should persist.
	 */
	public void clearTables() {
		this.store.purgeRetrieval();
		this.store.purgeSubscribe();
	}


	// =============== UTILITY METHODS ======================== //

}

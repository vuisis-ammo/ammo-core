package edu.vu.isis.ammo.core.distributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import net.jcip.annotations.ThreadSafe;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SerializeType;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.DistributorService.ChannelChange;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.MessageWrapper.MessageType;
import edu.vu.isis.ammo.util.InternetMediaType;


/**
 * The distributor service runs in the ui thread.
 * This establishes a new thread for distributing the requests.
 * 
 */
@ThreadSafe
public class DistributorThread 
extends AsyncTask<DistributorService, Integer, Void> 
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
		logger.debug("constructed");
	}

	/**
	 * When a channel comes on-line the disposition table should
	 * be checked to see if there are any waiting messages for 
	 * that channel.
	 * Channels going off-line are uninteresting so no signal.
	 */
	private AtomicBoolean channelDelta = new AtomicBoolean(true);
	private ConcurrentMap<String, ChannelChange> channelStatus = 
			new ConcurrentHashMap<String, ChannelChange>();

	public void onChannelChange(String name, ChannelChange change) {
		if (change.equals(this.channelStatus.get(name))) return;
		this.channelStatus.put(name, change);

		switch (change) {
		case DEACTIVATE:
			this.store.upsertChannelByName(name, ChannelState.INACTIVE);
			logger.trace("::channel deactivated");
			return;

		case ACTIVATE:
			this.store.upsertChannelByName(name, ChannelState.ACTIVE);
			this.store.upsertDisposalStateByChannel(name, DisposalState.PENDING);
			if (!channelDelta.compareAndSet(false, true)) return;
			this.signal();
			logger.trace("::channel activated");
			return;

		case REPAIR: 
			this.store.upsertChannelByName(name, ChannelState.ACTIVE);
			this.store.upsertDisposalStateByChannel(name, DisposalState.PENDING);
			if (!channelDelta.compareAndSet(false, true)) return;
			this.signal();
			logger.trace("::channel repaired");
			return;
		} 
	}

	/**
	 * Contains client application requests
	 */
	private final BlockingQueue<AmmoRequest> requestQueue;

	public String distributeRequest(AmmoRequest request) {
		try {
			logger.trace("received request of type {}", 
					request.toString());

			// FIXME should we generate the uuid here or earlier?
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
	 * If any of the distributor services are connected then true.
	 * There will in all likelyhood be only one such service so this
	 * is overkill but...
	 */
	private boolean isNetworkServiceConnected(DistributorService... them) {
		for (final DistributorService that : them) {
			if (that.getNetworkServiceBinder().isConnected()) {
				return true;
			}
		}
		return false;
	}
	/**
	 * Check to see if there is any work for the thread to do.
	 * If there are no network connections then nothing can be distributed, so no work.
	 * Either incoming requests, responses, or a channel has been activated.
	 */
	private boolean isReady(DistributorService[] them) {
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
	protected Void doInBackground(DistributorService... them) {

		logger.info("started");

		for (final DistributorService that : them) {
			if (!that.getNetworkServiceBinder().isConnected()) 
				continue;
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
						for (DistributorService that : them) {
							this.processChannelChange(that);						
						}
					}

					if (!this.responseQueue.isEmpty()) {
						logger.trace("processing response {}", this.responseQueue.size());
						try {
							final AmmoGatewayMessage agm = this.responseQueue.take();
							for (DistributorService that : them) {
								this.processResponse(that, agm);
							}
						} catch (ClassCastException ex) {
							logger.error("response queue contains illegal item of class {}", 
									ex.getLocalizedMessage());
						}
					}

					if (!this.requestQueue.isEmpty()) {
						logger.trace("processing request {}", this.requestQueue.size());
						try {
							final AmmoRequest agm = this.requestQueue.take();
							for (DistributorService that : them) {
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
	private boolean processRequest(DistributorService that, AmmoRequest agm) {
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
	 * This makes use of the disposition table and the channel status map.
	 * 
	 */
	private void processChannelChange(DistributorService that) {
		logger.info("::processPostalChange()");

		if (!that.getNetworkServiceBinder().isConnected()) 
			return;
		/*
		if (collectGarbage) {
			this.store.updatePostal( 
					POSTAL_EXPIRATION_UPDATE,
					POSTAL_EXPIRATION_CONDITION, new String[]{Long.toString(System.currentTimeMillis())} );
			this.store.deletePostal(POSTAL_GARBAGE, (String[]) null);

			this.store.updateRetrieval( 
					RETRIEVAL_EXPIRATION_UPDATE,
					RETRIEVAL_EXPIRATION_CONDITION, new String[]{Long.toString(System.currentTimeMillis())} );
			this.store.deletePostal(RETRIEVAL_GARBAGE, (String[]) null);
		}
		 */
		// we could do a priming query to determine if there are any candidates
		this.processPostalTable(that);
		this.processPublishTable(that);
		this.processRetrievalTable(that);
		this.processSubscribeTable(that);
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
	private Map<String, Boolean> 
	dispatchRequest(DistributorService that, 
			RequestSerializer serializer, DistributorPolicy.Topic topic, Map<String, Boolean> status) {

		logger.info("::sendGatewayRequest");
		if (status == null) status = new HashMap<String, Boolean>();

		Boolean ruleSuccess = status.get(DistributorPolicy.TOTAL);
		if (ruleSuccess == null) ruleSuccess = Boolean.FALSE;

		if (topic == null) {
			logger.error("no matching routing topic");
			final AmmoGatewayMessage agmb = serializer.act(Encoding.getDefault());
			Boolean actualCondition =
					that.getNetworkServiceBinder().sendRequest(agmb, DistributorPolicy.DEFAULT, topic);
			status.put(DistributorPolicy.DEFAULT, actualCondition);
			status.put(DistributorPolicy.TOTAL, actualCondition);
			return status;
		} 
		// evaluate rule
		for (DistributorPolicy.Clause clause : topic.routing.clauses) {
			boolean clauseSuccess = false;
			// evaluate clause
			for (DistributorPolicy.Literal literal : clause.literals) {
				final String term = literal.term;
				final boolean goalCondition = literal.condition;
				final Boolean actualCondition;
				if (status.containsKey(term)) {
					actualCondition = status.get(term);
				} else {
					final AmmoGatewayMessage agmb = serializer.act(literal.encoding);
					actualCondition = that.getNetworkServiceBinder().sendRequest(agmb, term, topic);
					status.put(term, actualCondition);
				} 
				if (goalCondition == actualCondition) {
					clauseSuccess = true;
					break;
				}
			}
			ruleSuccess &= clauseSuccess;
		}
		status.put(DistributorPolicy.TOTAL, ruleSuccess);
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
		logger.info("::processResponse {}", agm);

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
			logger.info("heartbeat");
			return true;
		}

		switch (mw.getType()) {
		case DATA_MESSAGE:
			logger.info("data interest");
			receiveSubscribeResponse(context, mw);
			break;

		case AUTHENTICATION_RESULT:
			boolean result = receiveAuthenticateResponse(context, mw);
			logger.error( "authentication result={}", result );
			break;

		case PUSH_ACKNOWLEDGEMENT:
			logger.info("push ack");
			receivePostalResponse(context, mw);
			break;

		case PULL_RESPONSE:
			logger.info("pull response");
			receiveRetrievalResponse(context, mw);
			break;

		case AUTHENTICATION_MESSAGE:
		case SUBSCRIBE_MESSAGE:
		case PULL_REQUEST:
		case UNSUBSCRIBE_MESSAGE:
			logger.warn( "received message type {}", mw.getType());
			break;
		default:
			logger.error( "mw.getType() returned an unexpected type. {}", mw.getType());
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
	private void processPostalRequest(final DistributorService that, AmmoRequest ar) {
		logger.info("::processPostalRequest()");

		// Dispatch the message.
		try {
			final String topic = ar.topic.asString();
			final DistributorPolicy.Topic policy = that.policy().match(topic);

			final ContentValues values = new ContentValues();
			values.put(PostalTableSchema.TOPIC.cv(), topic);
			values.put(PostalTableSchema.PROVIDER.cv(), ar.provider.cv());
			values.put(PostalTableSchema.ORDER.cv(), ar.order.cv());
			values.put(PostalTableSchema.EXPIRATION.cv(), ar.durability);
			values.put(PostalTableSchema.UNIT.cv(), 50);
			values.put(PostalTableSchema.PRIORITY.cv(), ar.priority);
			values.put(PostalTableSchema.CREATED.cv(), System.currentTimeMillis());

			if (!that.getNetworkServiceBinder().isConnected()) {
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
							return DistributorThread.serializeFromProvider(that.getContentResolver(), 
									serializer.provider.asUri(), encode, logger);
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
				final Map<String,Boolean> dispatchResult = 
						this.dispatchPostalRequest(that,
								ar.provider.toString(),
								topic, policy, null, serializer,
								new INetworkService.OnSendMessageHandler() {

							@Override
							public boolean ack(String channel, boolean status) {
								synchronized (DistributorThread.this.store) {
									DistributorThread.this.store.upsertDisposalByParent(id, Tables.POSTAL, channel, status);
								}
								return false;
							}
						});
				this.store.upsertDisposalByParent(id, Tables.POSTAL, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("NullPointerException, sending to gateway failed {}",
					ex.getStackTrace());
		}
	}


	/**
	 * Check for requests whose delivery policy has not been fully satisfied
	 * and for which there is, now, an available channel.
	 */
	private void processPostalTable(final DistributorService that) {
		logger.info("::processPostalTable()");

		if (!that.getNetworkServiceBinder().isConnected()) 
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

			logger.debug("serializing: " + provider);
			logger.debug("rowUriType: " + topic);

			final String mimeType = InternetMediaType.getInst(topic).setType("application").toString();

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
							return serializeFromProvider(that.getContentResolver(), 
									serializer.provider.asUri(), encode, logger);
						} catch (IOException e1) {
							logger.error("invalid row for serialization");
						}
					}
					logger.error("no serialized data produced");
					return null;
				}
			});

			final DistributorPolicy.Topic policy = that.policy().match(topic);
			final Map<String,Boolean> status = new HashMap<String,Boolean>();
			{
				final Cursor channelCursor = this.store.queryDisposalReady(id,"postal");
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; 
						moreChannels = channelCursor.moveToNext()) 
				{
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short state = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					status.put(channel, (state > 0));
				}
			}
			// Dispatch the request.
			try {
				if (!that.getNetworkServiceBinder().isConnected()) {
					logger.info("no network connection");
					continue;
				} 
				synchronized (this.store) {
					final ContentValues values = new ContentValues();

					values.put(PostalTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
					@SuppressWarnings("unused")
					long numUpdated = this.store.updatePostalByKey(id, values);

					final Map<String,Boolean> dispatchResult = 
							this.dispatchPostalRequest(that,
									provider.toString(),
									mimeType, policy, status, serializer,
									new INetworkService.OnSendMessageHandler() {

								@Override
								public boolean ack(String clazz, boolean status) {

									ContentValues values = new ContentValues();

									values.put(PostalTableSchema.DISPOSITION.cv(),
											(status) ? DisposalState.SENT.cv()
													: DisposalState.FAIL.cv());
									long numUpdated = DistributorThread.this.store.updatePostalByKey(id, values);

									logger.info("Postal: {} rows updated to {}",
											numUpdated, (status ? "sent" : "failed"));

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
	private Map<String,Boolean> 
	dispatchPostalRequest(final DistributorService that, final String uri, 
			final String msgType,
			final DistributorPolicy.Topic policy, final Map<String,Boolean> status,
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
						.setData(ByteString.copyFrom(serialized));
				mw.setDataMessage(pushReq);

				logger.debug("Finished wrap build @ time {}...difference of {} ms \n",
						System.currentTimeMillis(), System.currentTimeMillis()-now);
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
	private void processPublishRequest(DistributorService that, AmmoRequest agm, int st) {
		logger.info("::processPublicationRequest()");
	}

	private void processPublishTable(DistributorService that) {
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
	dispatchPublishRequest(DistributorService that, String uri, String mimeType, 
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
	private void processRetrievalRequest(DistributorService that, AmmoRequest agm) {
		logger.info("::processRetrievalRequest()");

		// Dispatch the message.
		try {
			final String topic = agm.topic.asString();
			final DistributorPolicy.Topic policy = that.policy().match(topic);

			final ContentValues values = new ContentValues();
			values.put(RetrievalTableSchema.TOPIC.cv(), topic);
			values.put(RetrievalTableSchema.PROVIDER.cv(), agm.provider.cv());
			values.put(RetrievalTableSchema.EXPIRATION.cv(), agm.durability);
			values.put(RetrievalTableSchema.UNIT.cv(), 50);
			values.put(RetrievalTableSchema.PRIORITY.cv(), agm.priority);
			values.put(RetrievalTableSchema.CREATED.cv(), System.currentTimeMillis());

			if (!that.getNetworkServiceBinder().isConnected()) {
				values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalState.PENDING.cv());
				this.store.upsertRetrieval(values, policy.makeRouteMap());
				logger.info("no network connection");
				return;
			}

			values.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
			// We synchronize on the store to avoid a race between dispatch and queuing
			synchronized (this.store) {
				final long id = this.store.upsertRetrieval(values, policy.makeRouteMap());
				final Map<String,Boolean> dispatchResult = 
						this.dispatchRetrievalRequest(that,
								agm.provider.toString(),
								agm.select.toString(),
								topic, policy, null,
								new INetworkService.OnSendMessageHandler() {

							@Override
							public boolean ack(String channel, boolean status) {
								synchronized (DistributorThread.this.store) {
									DistributorThread.this.store.upsertDisposalByParent(id, Tables.POSTAL, channel, status);
								}
								return false;
							}
						});
				this.store.upsertDisposalByParent(id, Tables.POSTAL, dispatchResult);
			}

		} catch (NullPointerException ex) {
			logger.warn("NullPointerException, sending to gateway failed");
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
	private void processRetrievalTable(DistributorService that) {
		logger.info("::processRetrievalTable()");

		final Cursor pending = this.store.queryRetrievalReady();

		for (boolean areMoreItems = pending.moveToFirst(); areMoreItems;
				areMoreItems = pending.moveToNext()) 
		{
			// For each item in the cursor, ask the content provider to
			// serialize it, then pass it off to the NPS.
			final int id = pending.getInt(pending.getColumnIndex(PostalTableSchema._ID.n));
			final String provider = pending.getString(pending.getColumnIndex(RetrievalTableSchema.PROVIDER.cv()));
			final String topic = pending.getString(pending.getColumnIndex(RetrievalTableSchema.TOPIC.cv()));
			// String disposition =
			// pendingCursor.getString(pendingCursor.getColumnIndex(RetrievalTableSchema.DISPOSITION));
			@SuppressWarnings("unused")
			final String selection = pending.getString(pending.getColumnIndex(RetrievalTableSchema.SELECTION.n));
			// int expiration =
			// pendingCursor.getInt(pendingCursor.getColumnIndex(RetrievalTableSchema.EXPIRATION));
			// long createdDate =
			// pendingCursor.getLong(pendingCursor.getColumnIndex(RetrievalTableSchema.CREATED_DATE));

			@SuppressWarnings("unused")
			final Uri rowUri = Uri.parse(provider);

			final Map<String,Boolean> status = new HashMap<String,Boolean>();
			{
				final Cursor channelCursor = this.store.queryDisposalReady(id,"postal");
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; 
						moreChannels = channelCursor.moveToNext()) 
				{
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short state = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					status.put(channel, (state > 0));
				}
			}

			try {
				if (!that.getNetworkServiceBinder().isConnected()) {
					logger.info("no network connection");
					continue;
				} 
				synchronized (this.store) {
					final ContentValues values = new ContentValues();
					final DistributorPolicy.Topic policy = that.policy().match(topic);

					values.put(PostalTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
					@SuppressWarnings("unused")
					final long numUpdated = this.store.updatePostalByKey(id, values);

					final Map<String,Boolean> dispatchResult = 
							this.dispatchPostalRequest(that,
									provider.toString(),
									topic, policy, status, null,
									new INetworkService.OnSendMessageHandler() {

								@Override
								public boolean ack(String clazz, boolean status) {

									ContentValues values = new ContentValues();

									values.put(PostalTableSchema.DISPOSITION.cv(),
											(status) ? DisposalState.SENT.cv()
													: DisposalState.FAIL.cv());
									long numUpdated = DistributorThread.this.store.updatePostalByKey(id, values);

									logger.info("Postal: {} rows updated to {}",
											numUpdated, (status ? "sent" : "failed"));

									return false;
								}
							});
					this.store.upsertDisposalByParent(id, Tables.RETRIEVAL, dispatchResult);
				}
			} catch (NullPointerException ex) {
				logger.warn("NullPointerException, sending to gateway failed");
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

	private Map<String,Boolean> 
	dispatchRetrievalRequest(final DistributorService that, String retrievalId, String selection,  
			String topic, DistributorPolicy.Topic policy, Map<String,Boolean> status,
			final INetworkService.OnSendMessageHandler handler) {
		logger.info("::dispatchRetrievalRequest");

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
	}


	/**
	 * Get response to RetrievalRequest, PullResponse, from the gateway.
	 *
	 * @param mw
	 * @return
	 */
	private boolean receiveRetrievalResponse(Context context, AmmoMessages.MessageWrapper mw) {
		logger.info("::receiveRetrievalResponse");

		if (mw == null) return false;
		if (! mw.hasPullResponse()) return false;
		final AmmoMessages.PullResponse resp = mw.getPullResponse();
		final String uriStr = resp.getRequestUid(); 
		final ContentResolver resolver = context.getContentResolver();
		final Uri provider = Uri.parse(uriStr);

		// FIXME how to control de-serializing
		DistributorThread.deserializeToProvider(resolver, provider, resp.getData().toByteArray(), logger);

		// This update/delete the retrieval request, it is fulfilled.

		// this.store.upsertDisposalByParent(id, type, channel, status);

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
	private void processSubscribeRequest(DistributorService that, AmmoRequest agm, int st) {
		logger.info("::processSubscribeRequest()");

		// Dispatch the message.
		try {
			final String topic = agm.topic.asString();
			final DistributorPolicy.Topic policy = that.policy().match(topic);

			final ContentValues values = new ContentValues();
			values.put(SubscribeTableSchema.TOPIC.cv(), topic);
			values.put(SubscribeTableSchema.PROVIDER.cv(), agm.provider.cv());
			values.put(SubscribeTableSchema.EXPIRATION.cv(), agm.durability);
			values.put(SubscribeTableSchema.PRIORITY.cv(), agm.priority);
			values.put(SubscribeTableSchema.CREATED.cv(), System.currentTimeMillis());

			if (!that.getNetworkServiceBinder().isConnected()) {
				values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalState.PENDING.cv());
				long key = this.store.upsertSubscribe(values, policy.makeRouteMap());
				logger.info("no network connection, added {}", key);
				return;
			}

			values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
			// We synchronize on the store to avoid a race between dispatch and queuing
			synchronized (this.store) {
				final long id = this.store.upsertSubscribe(values, policy.makeRouteMap());
				final Map<String,Boolean> dispatchResult = 
						this.dispatchSubscribeRequest(that,
								topic, agm.select.toString(),
								policy, null,
								new INetworkService.OnSendMessageHandler() {

							@Override
							public boolean ack(String channel, boolean status) {
								synchronized (DistributorThread.this.store) {
									DistributorThread.this.store.upsertDisposalByParent(id, Tables.POSTAL, channel, status);
								}
								return false;
							}
						});
				this.store.upsertDisposalByParent(id, Tables.POSTAL, dispatchResult);
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

	private void processSubscribeTable(DistributorService that) {
		logger.info("::processSubscribeTable()");

		final Cursor pending = this.store.querySubscribeReady();

		for (boolean areMoreItems = pending.moveToFirst(); areMoreItems;
				areMoreItems = pending.moveToNext()) 
		{
			// For each item in the cursor, ask the content provider to
			// serialize it, then pass it off to the NPS.
			final int id = pending.getInt(pending.getColumnIndex(PostalTableSchema._ID.n));
			final String provider = pending.getString(pending.getColumnIndex(SubscribeTableSchema.PROVIDER.cv()));
			final String topic = pending.getString(pending.getColumnIndex(SubscribeTableSchema.TOPIC.cv()));
			// String disposition =
			// pendingCursor.getString(pendingCursor.getColumnIndex(SubscribeTableSchema.DISPOSITION));
			@SuppressWarnings("unused")
			final String selection = pending.getString(pending.getColumnIndex(SubscribeTableSchema.SELECTION.n));
			// int expiration =
			// pendingCursor.getInt(pendingCursor.getColumnIndex(SubscribeTableSchema.EXPIRATION));
			// long createdDate =
			// pendingCursor.getLong(pendingCursor.getColumnIndex(SubscribeTableSchema.CREATED_DATE));

			@SuppressWarnings("unused")
			final Uri rowUri = Uri.parse(provider);

			final Map<String,Boolean> status = new HashMap<String,Boolean>();
			{
				final Cursor channelCursor = this.store.queryDisposalReady(id,"postal");
				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; 
						moreChannels = channelCursor.moveToNext()) 
				{
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
					final short state = channelCursor.getShort(channelCursor.getColumnIndex(DisposalTableSchema.STATE.n));
					status.put(channel, (state > 0));
				}
			}

			try {
				if (!that.getNetworkServiceBinder().isConnected()) {
					logger.info("no network connection");
					continue;
				} 
				synchronized (this.store) {
					final ContentValues values = new ContentValues();
					final DistributorPolicy.Topic policy = that.policy().match(topic);

					values.put(PostalTableSchema.DISPOSITION.cv(), DisposalState.QUEUED.cv());
					@SuppressWarnings("unused")
					long numUpdated = this.store.updatePostalByKey(id, values);

					final Map<String,Boolean> dispatchResult = 
							this.dispatchPostalRequest(that,
									provider.toString(),
									topic, policy, status, null,
									new INetworkService.OnSendMessageHandler() {

								@Override
								public boolean ack(String clazz, boolean status) {

									ContentValues values = new ContentValues();

									values.put(PostalTableSchema.DISPOSITION.cv(),
											(status) ? DisposalState.SENT.cv()
													: DisposalState.FAIL.cv());
									long numUpdated = DistributorThread.this.store.updatePostalByKey(id, values);

									logger.info("Postal: {} rows updated to {}",
											numUpdated, (status ? "sent" : "failed"));

									return false;
								}
							});
					this.store.upsertDisposalByParent(id, Tables.RETRIEVAL, dispatchResult);
				}
			} catch (NullPointerException ex) {
				logger.warn("NullPointerException, sending to gateway failed");
			}
		}
		pending.close();
	}

	/**
	 * Deliver the subscription request to the network service for processing.
	 */
	private Map<String,Boolean> 
	dispatchSubscribeRequest(final DistributorService that, String topic, 
			String selection, DistributorPolicy.Topic policy, Map<String,Boolean> status,
			final INetworkService.OnSendMessageHandler handler) {
		logger.info("::dispatchSubscribeRequest");

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

		logger.info("::dispatchSubscribeResponse : {} : {}", resp.getMimeType(), resp.getUri());
		@SuppressWarnings("unused")
		final String mime = resp.getMimeType();
		@SuppressWarnings("unused")
		final String tableUriStr = null;
		return true;
	}


	// =============== UTILITY METHODS ======================== //

	/**
	 * The data is serialized in the following form...
	 * serialized tuple : A list of non-null bytes which serialize the tuple, 
	 *   this is provided/supplied to the ammo enabled content provider via insert/query.
	 *   The serialized tuple may be null terminated or the byte array may simply end.
	 * field blobs : A list of name:value pairs where name is the field name and value is 
	 *   the field's data blob associated with that field.
	 *   There may be more than one field blob.
	 *   
	 *   field name : A null terminated name, 
	 *   field data length : A 4 byte big-endian length, indicating the number of bytes in the data blob.
	 *   field data blob : A set of bytes whose size is that of the field data length
	 *   
	 * Note the deserializeToUri and serializeFromUri are symmetric, any change to one 
	 * will necessitate a corresponding change to the other.
	 */  
	private static synchronized boolean deserializeToProvider(final ContentResolver resolver, Uri uri, byte[] data, Logger logger) {
		final Uri tupleUri = Uri.withAppendedPath(uri, "_deserial");
		final ByteBuffer dataBuff = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

		// FIXME encoding should be obtained from the input somehow
		final Encoding encoding = Encoding.getDefault();

		int position = 0;
		for (; position < data.length; position++) {
			if (position == (data.length-1)) { // last byte
				final byte[] payload = new byte[position];
				System.arraycopy(data, 0, payload, 0, position);
				DistributorThread.deserializeToProviderByEncoding(resolver, uri, encoding, payload, logger);
				return true;
			}
			if (data[position] == 0x0) {
				final ContentValues cv = new ContentValues();
				final byte[] name = new byte[position];
				System.arraycopy(data, 0, name, 0, position);
				cv.put("data", new String(data));
				resolver.insert(tupleUri, cv);
				break;
			}
		}

		int start = position; 
		int length = 0;
		while (position < data.length) {
			if (data[position] != 0x0) { position++; length++; continue; }
			final String fieldName = new String(data, start, length);

			dataBuff.position(position);
			final int dataLength = dataBuff.getInt();
			start = dataBuff.position();
			final byte[] blob = new byte[dataLength];
			System.arraycopy(data, start, blob, 0, dataLength);
			final Uri fieldUri = Uri.withAppendedPath(tupleUri, fieldName);
			OutputStream outstream;
			try {
				outstream = resolver.openOutputStream(fieldUri);
				if (outstream == null) {
					logger.error( "could not open output stream to content provider: {} ",fieldUri);
					return false;
				}
				outstream.write(blob);
			} catch (FileNotFoundException ex) {
				logger.error( "blob file not found: {} {}",fieldUri, ex.getStackTrace());
			} catch (IOException ex) {
				logger.error( "error writing blob file: {} {}",fieldUri, ex.getStackTrace());
			}
		}	
		return true;
	}

	private static Uri deserializeToProviderByEncoding(final ContentResolver resolver, Uri provider, 
			Encoding encoding, byte[] data, Logger logger) {
		final String payload = new String(data);

		switch (encoding.getPayload()) {
		case JSON: 
		case TERSE:
			try {
				final JSONObject input = (JSONObject) new JSONTokener(payload).nextValue();
				final ContentValues cv = new ContentValues();
				for (@SuppressWarnings("unchecked")
				Iterator<String> iter = input.keys(); iter.hasNext();) {
					final String key = iter.next();
					cv.put(key, input.getString(key));
				}
				return resolver.insert(provider, cv);
			} catch (JSONException ex) {
				logger.warn("invalid JSON content {}", ex.getLocalizedMessage());
			} catch (SQLiteException ex) {
				logger.warn("invalid sql insert {}", ex.getLocalizedMessage());
			}
			return null;
		case CUSTOM:
		default:
		{
			// FIXME write to the custom provider address
			final Uri customProvider = encoding.extendProvider(provider);
			final ContentValues cv = new ContentValues();
			cv.put("data", payload);
			return resolver.insert(customProvider, cv);
		}
		}
	}


	/**
	 * @see deserializeToUri with which this method is symmetric.
	 */
	private static synchronized byte[] serializeFromProvider(final ContentResolver resolver, 
			final Uri tupleUri, final DistributorPolicy.Encoding encoding, final Logger logger) 
					throws FileNotFoundException, IOException {

		// ========= Serialize the non-blob data ===============

		final Uri serialUri = Uri.withAppendedPath(tupleUri, encoding.getPayloadSuffix());
		final Cursor tupleCursor;
		try {
			tupleCursor = resolver.query(serialUri, null, null, null, null);
		} catch(IllegalArgumentException ex) {
			logger.warn("unknown content provider {}", ex.getLocalizedMessage());
			return null;
		}
		if (tupleCursor == null) return null;

		if (! tupleCursor.moveToFirst()) return null;
		if (tupleCursor.getColumnCount() < 1) return null;

		final byte[] tuple;

		switch (encoding.getPayload()) {
		case JSON: 
		case TERSE:
		{
			final JSONObject json = new JSONObject();
			tupleCursor.moveToFirst();

			final List<String> fieldNameList = new ArrayList<String>();
			fieldNameList.add("_serial");
			for (final String name : tupleCursor.getColumnNames()) {
				final String value = tupleCursor.getString(tupleCursor.getColumnIndex(name));
				if (value == null || value.length() < 1) continue;
				try {
					json.put(name, value);
				} catch (JSONException ex) {
					logger.warn("invalid content provider {}", ex.getStackTrace());
				}
			}
			tuple = json.toString().getBytes();
		}
		break;
		case CUSTOM:
		default:
		{
			final String tupleString = tupleCursor.getString(0);
			tuple = tupleString.getBytes();
		}
		}
		tupleCursor.close(); 

		// ========= Serialize the blob data (if any) ===============

		final Uri blobUri = Uri.withAppendedPath(tupleUri, "_blob");
		final Cursor blobCursor;
		try {
			blobCursor = resolver.query(blobUri, null, null, null, null);
		} catch(IllegalArgumentException ex) {
			logger.warn("unknown content provider {}", ex.getLocalizedMessage());
			return null;
		}
		if (blobCursor == null) return tuple;
		if (! blobCursor.moveToFirst()) return tuple;
		if (blobCursor.getColumnCount() < 1) return tuple;

		final ByteArrayOutputStream bigTuple = new ByteArrayOutputStream();
		bigTuple.write(tuple); // copy over tuple there
		bigTuple.write(0x0);

		final int blobCount = blobCursor.getColumnCount();
		final List<String> fieldNameList = new ArrayList<String>(blobCount);
		final List<ByteArrayOutputStream> fieldBlobList = new ArrayList<ByteArrayOutputStream>(blobCount);
		final byte[] buffer = new byte[1024]; 
		for (int ix=0; ix < blobCursor.getColumnCount(); ix++) {
			final String fieldName = blobCursor.getColumnName(ix);
			fieldNameList.add(fieldName);

			final Uri fieldUri = Uri.parse(blobCursor.getString(ix));    
			try {
				final AssetFileDescriptor afd = resolver.openAssetFileDescriptor(fieldUri, "r");
				if (afd == null) {
					logger.warn("could not acquire file descriptor {}", serialUri);
					throw new IOException("could not acquire file descriptor "+fieldUri);
				}
				final ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();

				final InputStream instream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
				final BufferedInputStream bis = new BufferedInputStream(instream);
				final ByteArrayOutputStream fieldBlob = new ByteArrayOutputStream();
				for (int bytesRead = 0; (bytesRead = bis.read(buffer)) != -1;) {
					fieldBlob.write(buffer, 0, bytesRead);
				}
				bis.close();
				fieldBlobList.add(fieldBlob);

			} catch (IOException ex) {
				logger.info("unable to create stream {} {}",serialUri, ex.getMessage());
				bigTuple.close();
				throw new FileNotFoundException("Unable to create stream");
			}
		}
		for (int ix=0; ix < blobCount; ix++) {
			final String fieldName = fieldNameList.get(ix);
			bigTuple.write(fieldName.getBytes());
			bigTuple.write(0x0);

			final ByteArrayOutputStream fieldBlob = fieldBlobList.get(ix);
			final ByteBuffer bb = ByteBuffer.allocate(4);
			bb.order(ByteOrder.BIG_ENDIAN); 
			bb.putInt(fieldBlob.size());
			bigTuple.write(bb.array());
			bigTuple.write(fieldBlob.toByteArray());
		}
		blobCursor.close();
		return bigTuple.toByteArray();
	}

}

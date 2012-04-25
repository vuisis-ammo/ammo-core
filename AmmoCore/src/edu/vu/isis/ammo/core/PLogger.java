package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * These are the loggers for measuring performance.
 * 
 * In particular they look at Threads/Processes/Queues/Intents/API
 * 
 * The view for these loggers is that of functional decomposition.
 * These loggers should be named according to the patterns indicated in-line.
 * These loggers are used for monitoring communication between components.
 * 
 */
public interface PLogger {

	// api external communications
	/**
	 * Incoming requests via the api from applications.
	 * - request - gross messages
	 * - parcel - detailed content of request
	 */
	public static final Logger API_REQ_RECV = LoggerFactory.getLogger( "api.request.recv" );
	public static final Logger API_PARCEL_RECV = LoggerFactory.getLogger( "api.parcel.recv" );
	
	/**
	 * Serialization to/from application content providers 
	 */
	public static final Logger API_STORE_SEND = LoggerFactory.getLogger( "api.store.send" );
	public static final Logger API_STORE_RECV = LoggerFactory.getLogger( "api.store.recv" );

	/**
	 * Notify external applications vi intents.
	 */
	public static final Logger API_INTENT = LoggerFactory.getLogger( "api.intent" );
	public static final Logger API_INTENT_ACTIVITY = LoggerFactory.getLogger( "api.intent.activity" );
	public static final Logger API_INTENT_BROAD = LoggerFactory.getLogger( "api.intent.broadcast" );
	public static final Logger API_INTENT_SERVICE = LoggerFactory.getLogger( "api.intent.service" );
	
	/**
	 * Producer/consumer queues
	 * For each queue there is a pair of in/out loggers.
	 * 
	 * <ul>
	 * <li>request - the requests received from the application before processing.
	 * <li>response - the responses received by a channel waiting to be used to update application's content providers
	 * <li>ack - a channel reporting back to the distributor about it disposal of a request 
	 * <li>channel - channels are consumers of requests for delivery these are their sources
	 * </ul>
	 * 
	 * These messages for the queue loggers are of the form QUEUE_FORMAT
	 *
	 * <ul>
	 * <li>remainder - the number of items remaining in the queue (before/after the item is added/taken)
	 * <li>id - may be uuid, auid, etc. depending on the queue type
	 * <li>size - how big is the item being added/taken to/from the queue
	 * <li>msg - some representative part of the item being added/taken
	 * </ul>
	 * 
	 */
	public static final String QUEUE_FORMAT =  "remainder=[{}] id=[{}] size=[{}] item=[{}]";
	public static final Logger QUEUE_REQ_ENTER = LoggerFactory.getLogger( "queue.request.in" );
	public static final Logger QUEUE_REQ_EXIT = LoggerFactory.getLogger( "queue.request.out" );
	
	public static final Logger QUEUE_RESP_ENTER = LoggerFactory.getLogger( "queue.response.in" );
	public static final Logger QUEUE_RESP_EXIT = LoggerFactory.getLogger( "queue.response.out" );
	
	public static final Logger QUEUE_ACK_ENTER = LoggerFactory.getLogger( "queue.ack.in" );
	public static final Logger QUEUE_ACK_EXIT = LoggerFactory.getLogger( "queue.ack.out" );
	
	public static final Logger QUEUE_CHANNEL_GW_ENTER = LoggerFactory.getLogger( "queue.channel.gw.in" );
	public static final Logger QUEUE_CHANNEL_GW_EXIT = LoggerFactory.getLogger( "queue.channel.gw.out" );

	public static final Logger QUEUE_CHANNEL_MC_ENTER = LoggerFactory.getLogger( "queue.channel.mc.in" );
	public static final Logger QUEUE_CHANNEL_MC_EXIT = LoggerFactory.getLogger( "queue.channel.mc.out" );

	public static final Logger QUEUE_CHANNEL_RMC_ENTER = LoggerFactory.getLogger( "queue.channel.rmc.in" );
	public static final Logger QUEUE_CHANNEL_RMC_EXIT = LoggerFactory.getLogger( "queue.channel.rmc.out" );

	public static final Logger QUEUE_CHANNEL_SERIAL_ENTER = LoggerFactory.getLogger( "queue.channel.serial.in" );
	public static final Logger QUEUE_CHANNEL_SERIAL_EXIT = LoggerFactory.getLogger( "queue.channel.serial.out" );

	/**
	 * Each channel has some communication media.
	 * When the channel connects, sends or receives.
	 */
	public static final Logger COMM_GW_CONN = LoggerFactory.getLogger( "comm.gw.conn" );
	public static final Logger COMM_GW_SEND = LoggerFactory.getLogger( "comm.gw.send" );
	public static final Logger COMM_GW_RECV = LoggerFactory.getLogger( "comm.gw.recv" );
	
	/**
	 * settings which change the ammo behavior
	 */

	public static final Logger SET_PANTHR = LoggerFactory.getLogger( "pref.panthr" );
	public static final Logger SET_PANTHR_GW = LoggerFactory.getLogger( "pref.panthr.gateway" );
	public static final Logger SET_PANTHR_MC = LoggerFactory.getLogger( "pref.panthr.multicast" );
	public static final Logger SET_PANTHR_RMC = LoggerFactory.getLogger( "pref.panthr.reliable" );
	public static final Logger SET_PANTHR_SERIAL = LoggerFactory.getLogger( "pref.panthr.serial" );
	public static final Logger SET_PANTHR_JOURNAL = LoggerFactory.getLogger( "pref.panthr.journal" );

	/**
	 *  omma data store
	 */
	public static final Logger STORE = LoggerFactory.getLogger( "store" );
	public static final Logger STORE_DDL = LoggerFactory.getLogger( "store.ddl" );

	public static final Logger STORE_POSTAL_DML = LoggerFactory.getLogger( "store.postal.dml" );
	public static final Logger STORE_INTEREST_DML = LoggerFactory.getLogger( "store.interest.dml" );
	public static final Logger STORE_RETRIEVE_DML = LoggerFactory.getLogger( "store.retrieve.dml" );
	public static final Logger STORE_CHANNEL_DML = LoggerFactory.getLogger( "store.channel.dml" );
	public static final Logger STORE_CAPABILITY_DML = LoggerFactory.getLogger( "store.capability.dml" );
	public static final Logger STORE_PRESENCE_DML = LoggerFactory.getLogger( "store.presence.dml" );
	public static final Logger STORE_DISPOSAL_DML = LoggerFactory.getLogger( "store.disposal.dml" );

	public static final Logger STORE_POSTAL_DQL = LoggerFactory.getLogger( "store.postal.dql" );
	public static final Logger STORE_INTEREST_DQL = LoggerFactory.getLogger( "store.interest.dql" );
	public static final Logger STORE_RETRIEVE_DQL = LoggerFactory.getLogger( "store.retrieve.dql" );
	public static final Logger STORE_CHANNEL_DQL = LoggerFactory.getLogger( "store.channel.dql" );
	public static final Logger STORE_CAPABILITY_DQL = LoggerFactory.getLogger( "store.capability.dql" );
	public static final Logger STORE_PRESENCE_DQL = LoggerFactory.getLogger( "store.presence.dql" );
	public static final Logger STORE_DISPOSAL_DQL = LoggerFactory.getLogger( "store.disposal.dql" );
}

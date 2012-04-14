package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * These are the loggers for Threads/Processes/Queues/Intents
 * 
 * The view for these loggers is that of functional decomposition.
 * These loggers should be named according to the pattern...
 * <component>.proc.<thread/process>.<class>.<property>
 * <component>.ipc.<queue/intent/shared-pref>.<class>.<property>
 * 
 * All of these loggers belong to the "omma" component.
 * These loggers are used for monitoring communication between components.
 * 
 */
public interface PLogger {
	// omma threads/processes
	public static final Logger TOP = LoggerFactory.getLogger( "proc.top" );
	public static final Logger DIST = LoggerFactory.getLogger( "proc.dist" );
	public static final Logger POLICY = LoggerFactory.getLogger( "proc.policy" );
	public static final Logger CHANNEL = LoggerFactory.getLogger( "proc.serve.channel" );
	
	// omma queues
	public static final Logger IPC_REQ_IN = LoggerFactory.getLogger( "ipc.request.inbound" );
	public static final Logger IPC_REQ_CACHE = LoggerFactory.getLogger( "ipc.request.cached" );
	
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
		
	// omma network channel
	public static final Logger IPC_CONN = LoggerFactory.getLogger( "ipc.channel.conn" );
	public static final Logger IPC_SEND = LoggerFactory.getLogger( "ipc.channel.send" );
	public static final Logger IPC_RECV = LoggerFactory.getLogger( "ipc.channel.recv" );
	
	// other IPC
	
	public static final Logger IPC_LOCAL = LoggerFactory.getLogger( "ipc.local" );
	
	public static final Logger IPC_PANTHR = LoggerFactory.getLogger( "ipc.panthr" );
	public static final Logger IPC_PANTHR_GW = LoggerFactory.getLogger( "ipc.panthr.gateway" );
	public static final Logger IPC_PANTHR_MC = LoggerFactory.getLogger( "ipc.panthr.multicast" );
	public static final Logger IPC_PANTHR_RMC = LoggerFactory.getLogger( "ipc.panthr.reliable" );
	public static final Logger IPC_PANTHR_SERIAL = LoggerFactory.getLogger( "ipc.panthr.serial" );
	public static final Logger IPC_PANTHR_JOURNAL = LoggerFactory.getLogger( "ipc.panthr.journal" );
	
	// omma data store
	public static final Logger STORE = LoggerFactory.getLogger( "store" );
	public static final Logger STORE_DDL = LoggerFactory.getLogger( "store.ddl" );
	public static final Logger STORE_DML = LoggerFactory.getLogger( "store.dml" );
	public static final Logger STORE_DQL = LoggerFactory.getLogger( "store.dql" );
	public static final Logger STORE_POSTAL = LoggerFactory.getLogger( "store.postal" );
	
	// omma intents
	public static final Logger IPC_INTENT = LoggerFactory.getLogger( "ipc.intent.service" );
	public static final Logger IPC_BCAST = LoggerFactory.getLogger( "ipc.broadcast" );
	public static final Logger IPC_ACTIVITY = LoggerFactory.getLogger( "ipc.activity" );
}

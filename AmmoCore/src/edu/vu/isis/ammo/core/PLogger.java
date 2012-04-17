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
	public static final Logger top_log = LoggerFactory.getLogger( "proc.top" );
	public static final Logger dist_log = LoggerFactory.getLogger( "proc.dist" );
	public static final Logger policy_log = LoggerFactory.getLogger( "proc.policy" );
	public static final Logger channel_log = LoggerFactory.getLogger( "proc.serve.channel" );
	
	// omma queues
	public static final Logger ipc_conn_log = LoggerFactory.getLogger( "ipc.channel.conn" );
	public static final Logger ipc_send_log = LoggerFactory.getLogger( "ipc.channel.send" );
	public static final Logger ipc_recv_log = LoggerFactory.getLogger( "ipc.channel.recv" );
	
	public static final Logger ipc_local_log = LoggerFactory.getLogger( "ipc.local" );
	
	public static final Logger ipc_panthr_log = LoggerFactory.getLogger( "ipc.panthr" );
	public static final Logger ipc_panthr_gw_log = LoggerFactory.getLogger( "ipc.panthr.gateway" );
	public static final Logger ipc_panthr_mc_log = LoggerFactory.getLogger( "ipc.panthr.multicast" );
	public static final Logger ipc_panthr_rmc_log = LoggerFactory.getLogger( "ipc.panthr.reliable" );
	public static final Logger ipc_panthr_serial_log = LoggerFactory.getLogger( "ipc.panthr.serial" );
	public static final Logger ipc_panthr_journal_log = LoggerFactory.getLogger( "ipc.panthr.journal" );
	
	// omma intents
	public static final Logger ipc_intent_log = LoggerFactory.getLogger( "ipc.intent.service" );
	public static final Logger ipc_bst_log = LoggerFactory.getLogger( "ipc.broadcast" );
	public static final Logger ipc_act_log = LoggerFactory.getLogger( "ipc.activity" );
}

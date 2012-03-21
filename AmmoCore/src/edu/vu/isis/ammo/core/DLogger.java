package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * These are the loggers for design breakdown.
 * A design consists of a set of classes forming a pattern.
 * 
 * The view for these loggers is that of material composition.
 * Designs are roughly the same a components.
 *
 */
public interface DLogger {
	// the main omma component
	public static final Logger top_log = LoggerFactory.getLogger( "omma" );
	public static final Logger network_log = LoggerFactory.getLogger( "omma.net" );
	public static final Logger api_log = LoggerFactory.getLogger( "omma.api" );
	public static final Logger policy_log = LoggerFactory.getLogger( "omma.api.policy" );
	
	public static final Logger channel_log = LoggerFactory.getLogger( "omma.net.channel" );
	public static final Logger multicast_log = LoggerFactory.getLogger( "omma.net.channel.multicast" );
	public static final Logger reliable_log = LoggerFactory.getLogger( "omma.net.channel.reliable" );
}

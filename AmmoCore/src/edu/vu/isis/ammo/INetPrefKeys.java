/*
Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo;

/**
 * Collection of all preference values used by Ammo.
 *
 */
public interface INetPrefKeys {
	
	/**
	 * General
	 * 
	 * External Keys
	 * 
	 * CORE_DEVICE_ID : A unique identifier for the device
	 *     (see UniqueIdentifier.java)
	 * CORE_OPERATOR_KEY : A passkey for the operator
	 * CORE_OPERATOR_ID : The panthr operator id
	 */
	public static final String CORE_DEVICE_ID = "CORE_DEVICE_ID";
	public static final String CORE_OPERATOR_KEY = "CORE_OPERATOR_KEY";
	public static final String CORE_OPERATOR_ID = "CORE_OPERATOR_ID";
	
	// VALUES
	public static final String DEFAULT_CORE_DEVICE_ID = null;
	public static final String DEFAULT_CORE_OPERATOR_KEY = "37";
	public static final String DEFAULT_CORE_OPERATOR_ID = "transappuser";
	

	/**
	 * Journaling 
	 * 
	 * External Keys
	 * 
	 * JOURNAL_SHOULD_USE : Indicates that the user wishes to make use if possible.
	 */
	public static final String JOURNAL_SHOULD_USE = "CORE_IS_JOURNALED";
	
	// VALUES
	public static final boolean DEFAULT_JOURNAL_SHOULD_USE = false;
	
	/**
	 * Gateway Channel and TCP Link Settings
	 *
	 * External Keys
	 * 
	 * GATEWAY_SHOULD_USE : Indicates that the user wishes to make use if possible.
	 * GATEWAY_HOST : The IP address of the gateway host
	 * GATEWAY_PORT : The listening port
	 * 
	 */
	public static final String GATEWAY_SHOULD_USE = "GATEWAY_SHOULD_USE";
	public static final String GATEWAY_HOST = "CORE_IP_ADDRESS";
	public static final String GATEWAY_PORT = "CORE_IP_PORT";
	public static final String GATEWAY_FLAT_LINE_TIME = "FLAT_LINE_TIME";
	public static final String GATEWAY_TIMEOUT = "CORE_SOCKET_TIMEOUT";
	
	// VALUES
	public static final boolean DEFAULT_GATEWAY_SHOULD_USE = true;
	public static final String DEFAULT_GATEWAY_HOST        = "192.168.1.100";
	public static final int DEFAULT_GATEWAY_PORT           = 33289;
	public static final int DEFAULT_GW_FLAT_LINE_TIME      = 20; // 20 minutes
	public static final int DEFAULT_GW_TIMEOUT             = 3; // 3 seconds
	
	/**
	 * Multicast Channel and UDP multicast Settings
	 * 
	 * External Keys
	 * 
	 * MULTICAST_SHOULD_USE : Indicates that the user wishes to make use if possible.
	 * MULTICAST_HOST : The IP address of the gateway host
	 * MULTICAST_PORT : The listening port
	 * 
	 */
	public static final String MULTICAST_SHOULD_USE = "MULTICAST_SHOULD_USE";
	public static final String MULTICAST_HOST = "MULTICAST_IP_ADDRESS";
	public static final String MULTICAST_PORT = "MULTICAST_PORT";
	public static final String MULTICAST_NET_CONN_TIMEOUT = "MULTICAST_NET_CONN_TIMEOUT";
	public static final String MULTICAST_CONN_IDLE_TIMEOUT = "MULTICAST_CONN_IDLE_TIMEOUT";
	public static final String MULTICAST_TTL = "MULTICAST_TTL";
	
	// VALUES
	public static final boolean DEFAULT_MULTICAST_SHOULD_USE = true;
	public static final String DEFAULT_MULTICAST_HOST        = "228.10.10.90";
	public static final String DEFAULT_MULTICAST_PORT        = "9982";
	public static final String DEFAULT_MULTICAST_NET_CONN    = "20";
	public static final String DEFAULT_MULTICAST_IDLE_TIME   = "3";
	public static final String DEFAULT_MULTICAST_TTL         = "1";

	
	/**
	 * ReliableMulticast Channel Settings
	 * 
	 * External Keys
	 * 
	 * SERIAL_SHOULD_USE : Indicates that the user wishes to make use if possible.
	 */
	public static final String RELIABLE_MULTICAST_SHOULD_USE = "RELIABLE_MULTICAST_SHOULD_USE";

	/** Internal KEYS
	 * The remaining keys are still in flux.
	 */
	public static final String RELIABLE_MULTICAST_HOST = "RELIABLE_MULTICAST_IP_ADDRESS";
	public static final String RELIABLE_MULTICAST_PORT = "RELIABLE_MULTICAST_PORT";
	public static final String RELIABLE_MULTICAST_NET_CONN_TIMEOUT = "RELIABLE_MULTICAST_NET_CONN_TIMEOUT";
	public static final String RELIABLE_MULTICAST_CONN_IDLE_TIMEOUT = "RELIABLE_MULTICAST_CONN_IDLE_TIMEOUT";
	public static final String RELIABLE_MULTICAST_TTL = "RELIABLE_MULTICAST_TTL";
	
	// VALUES
	public static final boolean DEFAULT_RELIABLE_MULTICAST_SHOULD_USE = false;
	public static final String DEFAULT_RELIABLE_MULTICAST_HOST        = "228.10.10.91";
	public static final String DEFAULT_RELIABLE_MULTICAST_PORT        = "9982";
	public static final String DEFAULT_RELIABLE_MULTICAST_NET_CONN    = "20";
	public static final String DEFAULT_RELIABLE_MULTICAST_IDLE_TIME   = "3";
	public static final String DEFAULT_RELIABLE_MULTICAST_TTL         = "1";

	/**
	 * Serial Channel and USB Link Settings
	 * Serial Channel/Link :
	 * 
	 * External Keys
	 * 
	 * SERIAL_SHOULD_USE : Indicates that the user wishes to make use if possible.
	 * SERIAL_BAUD_RATE : Valid values are {4800, 9600}
	 * SERIAL_RADIOS_IN_GROUP : the number of slots
	 * SERIAL_SLOT_NUMBER : The slot assigned to the link.
	 * SERIAL_SLOT_DURATION : time allocated to each slot.
	 */
	public static final String SERIAL_SHOULD_USE        = "SERIAL_SHOULD_USE";
	public static final String SERIAL_BAUD_RATE         = "SERIAL_BAUD_RATE";
	public static final String SERIAL_SLOT_NUMBER       = "SERIAL_SLOT_NUMBER";
	public static final String SERIAL_RADIOS_IN_GROUP   = "SERIAL_RADIOS_IN_GROUP";
	public static final String SERIAL_SLOT_DURATION     = "SERIAL_SLOT_DURATION";
	
	/** Internal KEYS
	 * SERIAL_SEND_ENABLED & SERIAL_RECEIVE_ENABLED :
	 * For debugging, indicates partial activation.
	 * 
	 * SERIAL_DEVICE : Probably auto-detectable.
	 * SERIAL_TRANSMIT_DURATION : Probably derived from the SERIAL_SLOT_DURATION
	 */
	public static final String SERIAL_DEVICE            = "SERIAL_DEVICE";
	public static final String SERIAL_SEND_ENABLED      = "SERIAL_SEND_ENABLED";
	public static final String SERIAL_RECEIVE_ENABLED   = "SERIAL_RECEIVE_ENABLED";
	public static final String SERIAL_TRANSMIT_DURATION = "SERIAL_TRANSMIT_DURATION";
	
	// VALUES
	public static final boolean DEFAULT_SERIAL_SHOULD_USE       = false;
	public static final String DEFAULT_SERIAL_DEVICE            = "/dev/ttyUSB0";
	public static final String DEFAULT_SERIAL_BAUD_RATE         = "9600";
	public static final String DEFAULT_SERIAL_SLOT_NUMBER       = "8";
	public static final String DEFAULT_SERIAL_RADIOS_IN_GROUP   = "16";
	public static final String DEFAULT_SERIAL_SLOT_DURATION     = "125";
	public static final String DEFAULT_SERIAL_TRANSMIT_DURATION = "50";
	public static final boolean DEFAULT_SERIAL_SEND_ENABLED     = true;
	public static final boolean DEFAULT_SERIAL_RECEIVE_ENABLED  = true;

}

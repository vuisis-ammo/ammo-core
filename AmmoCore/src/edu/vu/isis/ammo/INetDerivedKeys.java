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
package edu.vu.isis.ammo;

/**
 *  keys for managing network connections
 *  
 *  These are not intended to be modified by the user.
 *  These indicate the derived state of the network.
 *  These should be maintained in a separate shared preference file.
 */
public interface INetDerivedKeys {
	
	public static final String ARE_PREFS_CREATED = "prefsCreated";
	
	public static final String NET_IS_ACTIVE = "IS_ACTIVE";
	public static final String NET_SHOULD_USE = "SHOULD_USE";
	public static final String NET_IS_AVAILABLE = "IS_AVAILABLE";  
	public static final String NET_IS_STALE = "IS_STALE";
	
	public static final String WIRED_PREF = "AMMO_PHYSICAL_LINK_";
	public static final String WIFI_PREF = "AMMO_WIFI_LINK_";
	public static final String PHONE_PREF = "AMMO_PHONE_LINK_";
	public static final String NET_CONN_PREF = "AMMO_NET_CONN_";
	
	public static final String PHYSICAL_LINK_PREF_IS_ACTIVE = WIRED_PREF + NET_IS_ACTIVE;
	public static final String WIRED_PREF_SHOULD_USE = WIRED_PREF + NET_SHOULD_USE;
	public static final String PHYSICAL_LINK_PREF_IS_AVAILABLE = WIRED_PREF + NET_IS_AVAILABLE;	
	  
	public static final String WIFI_PREF_IS_ACTIVE = WIFI_PREF + NET_IS_ACTIVE;
	public static final String WIFI_PREF_SHOULD_USE = WIFI_PREF + NET_SHOULD_USE;
	public static final String WIFI_PREF_IS_AVAILABLE = WIFI_PREF + NET_IS_AVAILABLE;
	
	public static final String PHONE_PREF_IS_ACTIVE = PHONE_PREF + NET_IS_ACTIVE;
	public static final String PHONE_PREF_SHOULD_USE = PHONE_PREF + NET_SHOULD_USE;
	public static final String PHONE_PREF_IS_AVAILABLE = PHONE_PREF + NET_IS_AVAILABLE;
	
	public static final String NET_CONN_PREF_IS_STALE = NET_CONN_PREF + NET_IS_STALE;
	public static final String NET_CONN_PREF_SHOULD_USE = NET_CONN_PREF + NET_SHOULD_USE;
	public static final String NET_CONN_PREF_IS_ACTIVE = NET_CONN_PREF + NET_IS_ACTIVE;

}

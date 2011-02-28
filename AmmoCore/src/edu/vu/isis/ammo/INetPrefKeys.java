package edu.vu.isis.ammo;

/**
 * Collection of all preference values used by Ammo.
 * @author Demetri Miller
 *
 */
public interface INetPrefKeys extends edu.vu.isis.ammo.IPrefKeys {
	// ====================================
	// Ammo Core
	// ====================================	
	
// keys for network configuration
	
	public static final String PREF_IP_ADDR = "CORE_IP_ADDRESS";
	public static final String PREF_IP_PORT = "CORE_IP_PORT";
	public static final String PREF_SOCKET_TIMEOUT = "CORE_SOCKET_TIMEOUT";
	public static final String PREF_TRANSMISSION_TIMEOUT = "CORE_TRANSMISSION_TIMEOUT";
	public static final String PREF_IS_JOURNAL = "CORE_IS_JOURNALED";
	
	public static final String PREF_DEVICE_ID = "CORE_DEVICE_ID";
	public static final String PREF_OPERATOR_KEY = "CORE_OPERATOR_KEY";
	
// keys for managing network connections
	public static final String NET_IS_ACTIVE = "IS_ACTIVE";
	public static final String NET_SHOULD_USE = "SHOULD_USE";
	public static final String NET_IS_AVAILABLE = "IS_AVAILABLE";  
	public static final String NET_IS_STALE = "IS_STALE";
	
	public static final String PHYSICAL_LINK_PREF = "edu.vu.isis.ammo.core.physical_link.";
	public static final String WIFI_PREF = "edu.vu.isis.ammo.core.wifi_link.";
	public static final String NET_CONN_PREF = "edu.vu.isis.ammo.core.connect.";
	
	public static final String PHYSICAL_LINK_PREF_IS_ACTIVE = PHYSICAL_LINK_PREF + NET_IS_ACTIVE;
	public static final String PHYSICAL_LINK_PREF_SHOULD_USE = PHYSICAL_LINK_PREF + NET_SHOULD_USE;
	public static final String PHYSICAL_LINK_PREF_IS_AVAILABLE = PHYSICAL_LINK_PREF + NET_IS_AVAILABLE;	
	  
	public static final String WIFI_PREF_IS_ACTIVE = WIFI_PREF + NET_IS_ACTIVE;
	public static final String WIFI_PREF_SHOULD_USE = WIFI_PREF + NET_SHOULD_USE;
	public static final String WIFI_PREF_IS_AVAILABLE = WIFI_PREF + NET_IS_AVAILABLE;
	
	public static final String NET_CONN_PREF_IS_STALE = NET_CONN_PREF + NET_IS_STALE;
	public static final String NET_CONN_PREF_SHOULD_USE = NET_CONN_PREF + NET_SHOULD_USE;
	public static final String NET_CONN_PREF_IS_ACTIVE = NET_CONN_PREF + NET_IS_ACTIVE;

}

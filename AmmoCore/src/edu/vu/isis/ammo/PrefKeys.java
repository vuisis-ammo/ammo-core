package edu.vu.isis.ammo;

/**
 * Collection of all preference values used by Ammo.
 * @author Demetri Miller
 *
 */
public final class PrefKeys {
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
	public static final String PREF_OPERATOR_ID = "CORE_OPERATOR_ID";
	public static final String PREF_OPERATOR_KEY = "CORE_OPERATOR_KEY";
	
// keys for managing network connections
	
	public static final String PHYSICAL_LINK_PREF_STATUS_KEY = "edu.vu.isis.ammo.core.physical_link.STATUS.KEY";
	public static final String PHYSICAL_LINK_PREF_SHOULD_USE = "edu.vu.isis.ammo.core.physical_link.SHOULD_USE.KEY";
	public static final String PHYSICAL_LINK_PREF_IS_AVAILABLE = "edu.vu.isis.ammo.core.physical_link.IS_AVAILABLE";
	public static final String PHYSICAL_LINK_PREF_IS_STALE = "edu.vu.isis.ammo.core.physical_link.IS_STALE";
	  
	public static final String WIFI_PREF_STATUS_KEY = "edu.vu.isis.ammo.core.wifi_link.STATUS.KEY";
	public static final String WIFI_PREF_SHOULD_USE = "edu.vu.isis.ammo.core.wifi_link.SHOULD_USE.KEY";
	public static final String WIFI_PREF_IS_AVAILABLE = "edu.vu.isis.ammo.core.wifi_link.IS_AVAILABLE";  
	public static final String WIFI_PREF_IS_STALE = "edu.vu.isis.ammo.core.wifi_link.IS_STALE";
	
// The connection status defines those values stored with the *_PREF_STATUS_KEY above
	public static enum ConnectionStatus {
		NO_CONNECTION, CONNECTED, AVAILABLE_NOT_CONNECTED, NOT_AVAILABLE, PENDING
	};

	
}

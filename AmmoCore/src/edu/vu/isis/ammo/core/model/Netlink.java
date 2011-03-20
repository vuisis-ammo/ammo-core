package edu.vu.isis.ammo.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.view.View;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.OnStatusChangeListener;
import edu.vu.isis.ammo.core.network.NetworkService;

public class Netlink implements OnSharedPreferenceChangeListener {
	public static final Logger logger = LoggerFactory.getLogger(Netlink.class);
	// does the operator wish to use this gateway?
	private boolean election; 
	
	public void enable() { this.election = true; }
	public void disable() { this.election = false; }
	public void toggle() { this.election = !(this.election); }
	public boolean isEnabled() { return this.election; }
	
	// the user selected familiar name 
	private String type; 
	public String getType() { return this.type; }
	
	// the formal type for this gateway, 
	// in the case of a socket it is the "ip:<host ip>:<port>"
	
	private String host;
	private int port;
	public String getFormal() { 
		StringBuilder sb = new StringBuilder();
		sb.append("ip:").append(this.host).append(":").append(this.port);
		return sb.toString();
	}
	
	public static final int ACTIVE = 1;
	public static final int INACTIVE = 2; // means not available
	public static final int DISABLED = 3; // means the election is false
	private int status;
	
	// determines if any of the gateway's designated links
	// are functioning.
	public boolean hasLink() { return (this.status == ACTIVE); }
	
	// determines if any of the gateway is connected
	public boolean isConnected() { return (this.status == ACTIVE); }
	
	public int getStatus() { return this.status; }
	
	private final SharedPreferences prefs;
	protected final Context context;
	
	protected Netlink(Context context, String type) {
		this.context = context;
		this.type = type;
		this.host = NetworkService.DEFAULT_GATEWAY_HOST;
		this.port = NetworkService.DEFAULT_GATEWAY_PORT;
		this.election = true;
		this.status = INACTIVE;

		this.prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
	    prefs.registerOnSharedPreferenceChangeListener(this);
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.type).append(" = ").append(this.getFormal()).append(" ").append(this.election);
		return sb.toString();
	}

	private OnStatusChangeListener statusListener;
	private View statusView;
	
	public void setOnStatusChangeListener(OnStatusChangeListener listener, View view) {
		this.statusListener = listener;
		this.statusView = view;
	}
	
	/** 
	 * When the status changes update the local variable and any user interface.
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		
//		if (key.equals(INetPrefKeys.CORE_IS_JOURNALED)) {
//			this.journalingSwitch = prefs.getBoolean(INetPrefKeys.CORE_IS_JOURNALED, this.journalingSwitch);
//			if (this.journalingSwitch)
//				this.journalChannel.enable();
//			else this.journalChannel.disable();
//			return;
//		}

		// handle network connectivity group
		if (key.equals(INetPrefKeys.WIRED_PREF_SHOULD_USE)) {
		      //shouldUse(prefs);
		}       
		if (key.equals(INetPrefKeys.WIFI_PREF_SHOULD_USE)) {
		      //shouldUse(prefs);
		}
		if (key.equals(INetPrefKeys.NET_CONN_PREF_SHOULD_USE)) {
			logger.warn("explicit opererator reset on channel");
			this.statusListener.onStatusChange(this.statusView, this.status);
			//this.networkingSwitch = true;
			//this.tcpChannel.reset();
		}

		if (key.equals(INetPrefKeys.NET_CONN_FLAT_LINE_TIME)) {
			long flatLineTime = Integer.valueOf(prefs.getString(INetPrefKeys.NET_CONN_FLAT_LINE_TIME, 
					String.valueOf(NetworkService.DEFAULT_FLAT_LINE_TIME)));
			//this.tcpChannel.setFlatLineTime(flatLineTime * 60 * 1000); // convert from minutes to milliseconds
		}


		
	}
	
}

package edu.vu.isis.ammo.core;

import android.view.View;

/**
 * The Ammo core is responsible for distributing 
 * typed messages to gateways over multiple links.
 * The message may be sent to a single gateway
 * from an ordered set or to multiple gateways.
 * Each gateway may be reachable over several links.
 * The core makes these choices based on policy.
 * 
 * In a typical case the policy for message types
 * to gateways and gateways to links is 
 * established during provisioning.
 * In a future version this policy may be updated
 * by an existing trusted gateway publishing
 * a new policy.
 * 
 * The relationship of applications to message
 * types is established by the application.
 * It will certainly register its subscriptions
 * and publications.
 * From these it should be possible to infer and 
 * register the data types which it may post or pull.
 * 
 * With this information in hand the core should
 * be able to determine how effectively the core
 * can satiate the application's data hunger.
 * 
 * @author phreed
 */
public class Gateway {
	// does the operator wish to use this gateway?
	private boolean election; 
	
	public void enable() { this.election = true; }
	public void disable() { this.election = false; }
	public void toggle() { this.election = !(this.election); }
	public boolean isEnabled() { return this.election; }
	
	// the user selected familiar name 
	private String name; 
	public void setName(String name) { this.name = name; }
	public String getName() { return this.name; }
	
	// the formal name for this gateway, 
	// in the case of a socket it is the "ip:<host ip>:<port>"
	private String formal;
	public void setFormal(String formal) { this.formal = formal; }
	public String getFormal() { return this.formal; }
	
	public static final int ACTIVE = 1;
	public static final int INACTIVE = 2; // means not available
	public static final int DISABLED = 3; // means the election is false
	
	// determines if any of the gateway's designated links
	// are functioning.
	public int hasLink() { return ACTIVE; }
	
	// determines if any of the gateway is connected
	public int getConnected() { return INACTIVE; }
	
	public int getStatus() { return INACTIVE; }
	
	private Gateway(String name, String formal) {
		this.name = name;
		this.formal = formal;
		this.election = true;
	}
	
	public static Gateway getInstance() {
		// initialize the gateway from the shared preferences
		return new Gateway("default", "ip:10.0.2.2:32869");
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.name).append(" = ").append(this.formal).append(" ").append(this.election);
		return sb.toString();
	}
	
	public void setOnStatusChangeListener(OnStatusChangeListener listener, View view) {
		// set up observers of shared preferences
		listener.onStatusChange(view, this.getStatus());
	}
	
}

package edu.vu.isis.ammo.core;


/** 
 * An application can implement this interface to observe when certain statuses 
 * of the Gateway have changed.
 * @author phreed
 *
 */
public interface OnStatusChangeListenerByName {
	public boolean onNetlinkStatusChange(String itemName, int[] status);
	public boolean onGatewayStatusChange(String itemName, int[] status);
}

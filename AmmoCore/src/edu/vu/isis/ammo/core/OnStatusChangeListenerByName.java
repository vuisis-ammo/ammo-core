package edu.vu.isis.ammo.core;

import android.view.View;

/** 
 * An application 
 * @author phreed
 *
 */
public interface OnStatusChangeListenerByName {
	public boolean onNetlinkStatusChange(String itemName, int[] status);
	public boolean onGatewayStatusChange(String itemName, int[] status);
}

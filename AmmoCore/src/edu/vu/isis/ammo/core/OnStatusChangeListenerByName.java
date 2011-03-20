package edu.vu.isis.ammo.core;

import android.view.View;

/** 
 * An application 
 * @author phreed
 *
 */
public interface OnStatusChangeListenerByName {
	public boolean onStatusChange(String itemName, int[] status);
}

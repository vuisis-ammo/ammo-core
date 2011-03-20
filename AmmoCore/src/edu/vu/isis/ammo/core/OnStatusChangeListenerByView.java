package edu.vu.isis.ammo.core;

import android.view.View;

/** 
 * An application 
 * @author phreed
 *
 */
public interface OnStatusChangeListenerByView {
	public boolean onStatusChange(View view, int[] status);
}

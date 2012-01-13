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
package edu.vu.isis.ammo.core;

import android.view.View;

/**
 * Interface used by the Gateway class to notify interested parties certain
 * parts of the shared preferences have changed.
 * @author demetri
 *
 */
public interface OnNameChangeListener {
	public boolean onNameChange(View view, String name);
	public boolean onFormalChange(View view, String formal);
}

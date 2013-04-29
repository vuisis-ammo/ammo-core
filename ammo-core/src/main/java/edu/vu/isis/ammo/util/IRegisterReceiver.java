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
package edu.vu.isis.ammo.util;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * This is just a simple class which may be used to pass context around.
 * This can easily be done as a closure.
 * 
 * For example:
 * final IRegisterReceiver registerReceiver = new IRegisterReceiver() {
 *   @Override
 *   public Intent registerReceiver(final BroadcastReceiver aReceiver, final IntentFilter aFilter) {
 *     return applicationContext.registerReceiver(aReceiver, aFilter);
 *   }
 *   @Override
 *   public void unregisterReceiver(final BroadcastReceiver aReceiver) {
 *     applicationContext.unregisterReceiver(aReceiver);
 *   }
 * };
 * 
 * @author phreed
 *
 */
public interface IRegisterReceiver {
		public Intent registerReceiver(final BroadcastReceiver aReceiver, final IntentFilter aFilter);
		public void unregisterReceiver(final BroadcastReceiver aReceiver);
}

/**
 * 
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

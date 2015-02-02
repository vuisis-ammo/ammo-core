/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.vu.isis.ammo.core.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import edu.vu.isis.ammo.IntentNames;

public class PackageInstalledReceiver extends BroadcastReceiver {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger("receiver.install");

	// ===========================================================
	// Broadcast Receiver
	// ===========================================================
	/**
	 * Catch when packages are installed. If the package is part of the ammo family, send a notification
	 * letting the app know Ammo is ready to be used.
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		logger.debug("onReceive{}", intent.getAction());
		final String action = intent.getAction();
		final String packageName = intent.getDataString();

		// If this is an ammo package, broadcast an intent that the Ammo is ready.  
		if (action.equalsIgnoreCase(Intent.ACTION_PACKAGE_ADDED) 
				|| action.equalsIgnoreCase(Intent.ACTION_PACKAGE_REPLACED)) {
			if (packageName.contains("ammo")) {

				final Intent readyIntent = new Intent(IntentNames.AMMO_READY);
				//readyIntent.addCategory(packageName);
				context.sendBroadcast(readyIntent);
			}
		}
	}
}

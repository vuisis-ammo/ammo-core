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


package edu.vu.isis.ammo.core;

import transapps.settings.SettingsAvailableReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Rethrow the broadcast intent so the the AmmoService can see it.
 *
 */
public class AmmoSettingsAvailabiltyReceiver extends SettingsAvailableReceiver {

	final static String ACTION_AVAILABLE = "edu.vu.isis.ammo.settings.action.AVAILABLE";	
	final static String ACTION_UNAVAILABLE = "edu.vu.isis.ammo.settings.action.UNAVAILABLE";
	
	@Override
	protected void onSettingsAvailable(Context context) {
		PLogger.SET_PANTHR.debug("panthr settings available");
        final ComponentName targetService = new ComponentName(context, AmmoService.class);
		
		final Intent service = new Intent()
		     .setAction(ACTION_AVAILABLE)
		     .setComponent(targetService);
		
		context.startService(service);
	}

	@Override
	protected void onSettingsUnavailable(Context context) {
		PLogger.SET_PANTHR.debug("panthr settings *not* available");
		final ComponentName targetService = new ComponentName(context, AmmoService.class);
			
		final Intent service = new Intent()
		     .setAction(ACTION_UNAVAILABLE)
		     .setComponent(targetService);
		
		context.startService(service);
	}

}

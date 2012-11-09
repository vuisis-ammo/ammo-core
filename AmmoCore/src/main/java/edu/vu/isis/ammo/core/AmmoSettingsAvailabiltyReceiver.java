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

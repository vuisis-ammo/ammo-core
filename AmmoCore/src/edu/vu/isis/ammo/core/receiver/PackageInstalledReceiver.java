package edu.vu.isis.ammo.core.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.IntentNames;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PackageInstalledReceiver extends BroadcastReceiver {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger("ammo-PIR");

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

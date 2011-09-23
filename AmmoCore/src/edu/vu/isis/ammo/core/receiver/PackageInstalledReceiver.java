package edu.vu.isis.ammo.core.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.IPrefKeys;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PackageInstalledReceiver extends BroadcastReceiver {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger("ammo-PIR");
	
//	public static final String PACKAGE_INSTALLED_INTENT = "android.intent.action.PACKAGE_ADDED";
	
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
		String action = intent.getAction();
		String data = intent.getDataString();
		
		// If this is an ammo package, broadcast an intent that the Ammo is ready.  
		if (action.equalsIgnoreCase("android.intent.action.PACKAGE_ADDED") && data.contains("ammo")) {
			Intent i = new Intent(IPrefKeys.AMMO_READY);
			context.sendBroadcast(i);
		}
	}

}

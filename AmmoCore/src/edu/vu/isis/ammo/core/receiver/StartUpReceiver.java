package edu.vu.isis.ammo.core.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import edu.vu.isis.ammo.core.distributor.DistributorService;

public class StartUpReceiver extends BroadcastReceiver {

	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger(StartUpReceiver.class);
	
	public static final String RESET = "edu.vu.isis.ammo.action.RESET";
	
	// ===========================================================
	// Fields
	// ===========================================================

	// ===========================================================
	// Broadcast Receiver
	// ===========================================================

	@Override
	public void onReceive(Context context, Intent intent) {
		logger.debug("::onReceive");
		context.startService(DistributorService.LAUNCH);
		// context.startService(NetworkService.LAUNCH);
	}
}
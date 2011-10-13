package edu.vu.isis.ammo.core.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.ethertracker.EthTrackSvc;

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
		context.startService(AmmoService.LAUNCH);
		// context.startService(NetworkService.LAUNCH);
		
		Intent svc = new Intent();
		svc.setClass(context, EthTrackSvc.class);  // explicit start
        context.startService(svc);
	}
}

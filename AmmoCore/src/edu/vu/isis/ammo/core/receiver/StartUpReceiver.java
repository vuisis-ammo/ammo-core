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
	public static final Logger logger = LoggerFactory.getLogger("ammo.class.StartUpReceiver");
	
	public static final String RESET = "edu.vu.isis.ammo.action.RESET";
	
	// ===========================================================
	// Fields
	// ===========================================================

	// ===========================================================
	// Broadcast Receiver
	// ===========================================================

	@Override
	public void onReceive(Context context, Intent intent) {
		logger.info("::onReceive {}", intent.getAction());
		
		logger.info("launching AmmoService");
		context.startService(AmmoService.LAUNCH);
		
		logger.info("launching Ether Track Service");
		final Intent svc = new Intent();
		svc.setClass(context, EthTrackSvc.class);  // explicit start
        context.startService(svc);
	}
}

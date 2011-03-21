package edu.vu.isis.ammo.core.ethertracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class EthrTrckrStartupReceiver extends BroadcastReceiver {

	@Override
	public void onReceive (Context context, Intent intent) {
		Intent svc = new Intent();
		// svc.setAction("edu.vu.isis.ammo.startatboot.EthTrackSvc");
		svc.setClass(context, EthTrackSvc.class);  // explicit start
        context.startService(svc);
	}
}

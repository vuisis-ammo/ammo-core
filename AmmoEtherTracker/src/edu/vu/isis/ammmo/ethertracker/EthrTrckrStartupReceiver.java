package edu.vu.isis.ammmo.ethertracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class EthrTrckrStartupReceiver extends BroadcastReceiver {

	@Override
	public void onReceive (Context context, Intent intent) {
		Intent svc = new Intent();
		svc.setAction("edu.vu.isis.ammmo.startatboot.EthTrackSvc");
        context.startService(svc);
	}
	
	static {
        System.loadLibrary("ethrmon");
    }
}

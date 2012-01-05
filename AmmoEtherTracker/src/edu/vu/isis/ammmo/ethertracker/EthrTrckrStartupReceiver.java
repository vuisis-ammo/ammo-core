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

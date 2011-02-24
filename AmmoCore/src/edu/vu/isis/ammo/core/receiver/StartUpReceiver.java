package edu.vu.isis.ammo.core.receiver;

import edu.vu.isis.ammo.core.ICoreService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StartUpReceiver extends BroadcastReceiver {

	// ===========================================================
	// Constants
	// ===========================================================
	private static final String TAG = "AmmoLoginReceiver";
	
	// ===========================================================
	// Fields
	// ===========================================================

	// ===========================================================
	// Broadcast Receiver
	// ===========================================================

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "::onReceive");
		context.startService(ICoreService.CORE_APPLICATION_LAUNCH_SERVICE_INTENT);
	}
}

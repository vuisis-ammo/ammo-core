package edu.vu.isis.ammo.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AmmoLoginReceiver extends BroadcastReceiver {

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
	}
}

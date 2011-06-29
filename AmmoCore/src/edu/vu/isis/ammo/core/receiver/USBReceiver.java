package edu.vu.isis.ammo.core.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * USBReceiver is a broadcast receiver which handles system intents broadcast
 * when a device is connected via USB to some other device.
 * 
 * NOTE: It turns out that Google doesn't really want you to connect an 
 * android application to another device via USB. For this reason, all usb
 * synchronization will be handled from the external device side.
 *   
 * @author Demetri Miller
 *
 */
public class USBReceiver extends BroadcastReceiver {

	// ===========================================================
	// Constants
	// ===========================================================
private static final Logger logger = LoggerFactory.getLogger(USBReceiver.class);
	
	// ===========================================================
	// Fields
	// ===========================================================

	// ===========================================================
	// Broadcast Receiver
	// ===========================================================

	@Override
	public void onReceive(Context context, Intent intent) {
		logger.debug("::onReceive");
	}
}

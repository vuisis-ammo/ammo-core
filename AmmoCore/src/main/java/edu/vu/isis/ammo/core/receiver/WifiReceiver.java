/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.vu.isis.ammo.core.receiver;

import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.widget.Toast;

/**
 * WifiReceiver is a broadcast receiver which receives intents from the system
 * when network state changes occur.
 * 
 * One of this receiver's main responsibilities is scanning and connecting
 * to access points on the fly. Since users of this application will be highly
 * mobile (and we also desire high speed connections for lowest-delay data sync),
 * we want to automate network connections.
 * 
 * Additionally, this receiver is responsible for updating the DeliveryMechanism
 * table in the Distribution Content Provider as state changes occur. This is 
 * an important piece of the data sync mechanism because updates to that table
 * will trigger synchronization processes to begin or end.
 * 
 */

public class WifiReceiver extends BroadcastReceiver {
private static final Logger logger = LoggerFactory.getLogger("receiver.wifi");
	// ===========================================================
	// Constants
	// ===========================================================

	@SuppressWarnings("unused")
	private static final int RSSI_TIMER_DELAY = 30*1000; // in milliseconds.
	@SuppressWarnings("unused")
	private static final int SUPPLICATION_TIMER_DELAY = 30*1000; // in milliseconds
	private static HashMap<String, Integer> intentSwitch = new HashMap<String, Integer>();
	
	// ===========================================================
	// Fields
	// ===========================================================
	
	/** Convenience fields reset each time we receive a broadcast. */
	private Context recvContext;
	private Intent recvIntent;
	private WifiManager wifiManager;
	
	// Only handle broadcasted actions after initialized.
	boolean initialized = false;
	
	// ===========================================================
	// Lifecycle
	// ===========================================================
	public WifiReceiver() {
		intentSwitch.put("android.net.wifi.supplicant.CONNECTION_CHANGE", 0);
		intentSwitch.put("android.net.wifi.RSSI_CHANGED", 1);
		intentSwitch.put("android.net.wifi.SCAN_RESULTS", 2);
		initialized = true;
	}
	
	// ===========================================================
	// Broadcast Receiver
	// ===========================================================

	// Switch on the different intents we have registered to receive.
	@Override
	public void onReceive(Context context, Intent intent) {
		if (initialized == false) {
			return;
		}
		
		logger.debug("::onReceive with intent {}", intent.getAction());
		
		recvContext = context;
		recvIntent = intent;
		wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		Integer switchVal = intentSwitch.get(intent.getAction());
		if (switchVal == null) {
			switchVal = -1;
		}
		
		switch(switchVal) {
		case 0:	// Supplicant connection change
			this.wifiSupplicationConnectionChanged();
			break;
		case 1: // RSSI changed 
			this.wifiRSSIChanged();
			break;
		
		case 2:	// Wifi scan results available
			this.wifiScanResultsAvailable();
			break;
		
		default:
			logger.debug("::onReceive: intent not found");
			return;
		}
	}
	
	// ===========================================================
	// Intent handling
	// ===========================================================
	
	

	// Check whether we have connected or disconnected from an access point.
	// If we have disconnected, begin scanning for new access points.
	private void wifiSupplicationConnectionChanged() {
//		logger.debug("::wifiSupplicationConnectionChanged");
		Toast.makeText(recvContext, "wifiSupplicationConnectionChanged", Toast.LENGTH_LONG);

		boolean connected = recvIntent.getExtras().getBoolean(WifiManager.EXTRA_SUPPLICANT_CONNECTED);
		
		
		if (!connected) {
			// Start scanning for access points.
			wifiManager.startScan();
			this.updateWifiRowInTable("down", "byte", 0, 0);
			
		} else {
			WifiInfo info = wifiManager.getConnectionInfo();
			int linkSpeed = info.getLinkSpeed();
			// TODO: Calculate costUp/costDown
			this.updateWifiRowInTable("up", WifiInfo.LINK_SPEED_UNITS, linkSpeed, linkSpeed);
			
			// Schedule a scan for new access points some time in the future.
			//this.scheduleWifiScan(SUPPLICATION_TIMER_DELAY);
		}
	}
	
	// Update the DeliveryMechanism table.
	private void wifiRSSIChanged() {
		//logger.debug("::wifiRSSIChanged");
		WifiInfo info = wifiManager.getConnectionInfo();
		int linkSpeed = info.getLinkSpeed();
		// TODO: Calculate costUp/costDown
		this.updateWifiRowInTable("up", WifiInfo.LINK_SPEED_UNITS, linkSpeed, linkSpeed);
		
		// Schedule another wifi scan.
		//this.scheduleWifiScan(RSSI_TIMER_DELAY);
	}
	
	// Determine if we are connected to the most desirable access point, if not,
	// change connection.
	private void wifiScanResultsAvailable() {
//		logger.debug("::wifiScanResultsAvailable");
		List<ScanResult> scanResults = wifiManager.getScanResults();
		
		if (!scanResults.isEmpty()) {
			// Sort the list in order of signal strength.
			ScanResult bestResult = scanResults.get(0);
			for (ScanResult result : scanResults) {
				// True if bestResult is weaker than result.
				if (WifiManager.compareSignalLevel(bestResult.level, result.level) < 0) {
					bestResult = result;
				}
			}
			
			// TODO: If the bestResult is not the active network connection, add it
			// to the wireless configurations and enable it.
			if (!bestResult.BSSID.equals(wifiManager.getConnectionInfo().getBSSID())) {
				Intent i = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
				i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				//recvContext.startActivity(i);
				//wifiManager.enableNetwork(wifiManager.get, true);
			}
		}
	}
	
	// Update the delivery mechanism table's wifi row.
	private void updateWifiRowInTable(String status, String unit, int costUp, int costDown) {
//		ContentResolver cr = recvContext.getContentResolver();
//		ContentValues values = new ContentValues();
//		values.put(DeliveryMechanismTableSchema.STATUS, status);
//		values.put(DeliveryMechanismTableSchema.UNIT, unit);
//		values.put(DeliveryMechanismTableSchema.COST_UP, costUp);
//		values.put(DeliveryMechanismTableSchema.COST_DOWN, costDown);
//		cr.update(DeliveryMechanismTableSchema.CONTENT_URI, values, DeliveryMechanismTableSchema.CONN_TYPE + " == " + "\"" + DeliveryMechanismTableSchema.CONN_TYPE_WIFI + "\"", null);
	}
	
	@SuppressWarnings("unused")
	private void scheduleWifiScan(int delay) {
		logger.debug("::scheduleWifiScan");
		Timer t = new Timer();
		t.schedule(new TimerTask() {
			@Override
			public void run() {
				wifiManager.startScan();
			}
		}, delay);
	}
	
	
	// ===========================================================
	// Getter & Setter
	// ===========================================================
	public boolean isInitialized() {
		return initialized;
	}
	public WifiReceiver setInitialized(boolean initialized) {
		this.initialized = initialized;
		return this;
	}
	
}


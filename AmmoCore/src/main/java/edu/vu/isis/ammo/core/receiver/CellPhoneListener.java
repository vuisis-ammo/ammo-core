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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

/**
 * CellPhoneListener is a PhoneStateListener which manages the 3G radio on the
 * device.
 * 
 * It is also responsible for updating the DeliveryMechanism table in the
 * Distribution Content Provider as the signal state changes.
 * 
 * NOTE: Assuming these devices will be running over CDMA rather than GSM.
 * 
 * @author Demetri Miller
 *
 */
public class CellPhoneListener extends PhoneStateListener {
	// ===========================================================
	// Constants
	// ===========================================================
        private static final Logger logger = LoggerFactory.getLogger("receiver.3G");
	
	// ===========================================================
	// Fields
	// ===========================================================
	@SuppressWarnings("unused")
	private Context context;
	
	// ===========================================================
	// Lifecycle
	// ===========================================================
	public CellPhoneListener(Context aContext) {
		context = aContext;
	}
	
	
	// ===========================================================
	// PhoneStateListener
	// ===========================================================

	@Override
	public void onDataConnectionStateChanged(int state) {
		logger.debug("::onDataConnectionStateChanged()");
		switch (state) {
		case TelephonyManager.DATA_DISCONNECTED:
			this.updateCellularRowInTable("down", "byte", 0, 0);
			break;
			
		case TelephonyManager.DATA_CONNECTED:
			this.updateCellularRowInTable("up", "byte", 0, 0);
			break;
			
		default: 
			return;
		}
	}
	
	// Assuming device operates over CDMA.
	@Override 
	public void onSignalStrengthsChanged (SignalStrength signalStrength) {
		logger.debug("::onSignalStrengthsChanged()");
		int dBmRSSI = signalStrength.getCdmaDbm();
		this.updateCellularRowInTable("up", "byte", dBmRSSI, dBmRSSI);
	}
	
	// Update the delivery mechanism table's 3g row.
	private void updateCellularRowInTable(String status, String unit, int costUp, int costDown) {
//		ContentResolver cr = context.getContentResolver();
//		ContentValues values = new ContentValues();
//		values.put(DeliveryMechanismTableSchema.STATUS, status);
//		values.put(DeliveryMechanismTableSchema.UNIT, unit);
//		values.put(DeliveryMechanismTableSchema.COST_UP, costUp);
//		values.put(DeliveryMechanismTableSchema.COST_DOWN, costDown);
//		cr.update(DeliveryMechanismTableSchema.CONTENT_URI, values, DeliveryMechanismTableSchema.CONN_TYPE + " == " + "\"" + DeliveryMechanismTableSchema.CONN_TYPE_WIFI + "\"", null);
	}
}

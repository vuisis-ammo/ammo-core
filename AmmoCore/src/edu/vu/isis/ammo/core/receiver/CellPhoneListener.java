package edu.vu.isis.ammo.core.receiver;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import edu.vu.isis.ammo.core.provider.DistributorSchema.DeliveryMechanismTableSchema;

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
	private static final String TAG = "CellPhoneListener";
	
	// ===========================================================
	// Fields
	// ===========================================================
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
		Log.d(TAG, "::onDataConnectionStateChanged()");
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
		Log.d(TAG, "::onSignalStrengthsChanged()");
		int dBmRSSI = signalStrength.getCdmaDbm();
		this.updateCellularRowInTable("up", "byte", dBmRSSI, dBmRSSI);
	}
	
	// Update the delivery mechanism table's 3g row.
	private void updateCellularRowInTable(String status, String unit, int costUp, int costDown) {
		ContentResolver cr = context.getContentResolver();
		ContentValues values = new ContentValues();
		values.put(DeliveryMechanismTableSchema.STATUS, status);
		values.put(DeliveryMechanismTableSchema.UNIT, unit);
		values.put(DeliveryMechanismTableSchema.COST_UP, costUp);
		values.put(DeliveryMechanismTableSchema.COST_DOWN, costDown);
		cr.update(DeliveryMechanismTableSchema.CONTENT_URI, values, DeliveryMechanismTableSchema.CONN_TYPE + " == " + "\"" + DeliveryMechanismTableSchema.CONN_TYPE_WIFI + "\"", null);
	}
}

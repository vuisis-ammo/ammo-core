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
package edu.vu.isis.ammo.core.ui;

import android.content.res.Resources;
import android.os.Bundle;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ui.util.PreferenceActivityEx;

/**
 * View and change the ammo core preferences.
 * These are primarily concerned with the management
 * of the logging framework.
 * 
 * @author phreed
 *
 */
public class GeneralPreferences extends PreferenceActivityEx {
	// ===========================================================
	// Constants
	// ===========================================================
    public static final String LAUNCH = "edu.vu.isis.ammo.core.LoggingPreferences.LAUNCH";

	public static final String PREF_LOG_LEVEL = "CORE_LOG_LEVEL";
	
	// ===========================================================
	// Fields
	// ===========================================================
	private MyEditTextPreference level;
	private MyEditTextPreference name;

	// Gateway
	private MyCheckBoxPreference gwOpEnablePref;
	
	private MyEditTextPreference gwIpPref;
	private MyEditIntegerPreference gwPortPref;
	
	private MyEditIntegerPreference gwConnIdlePref;
	private MyEditIntegerPreference gwNetConnPref;

	// Multicast
	private MyCheckBoxPreference mcOpEnablePref;
	
	private MyEditTextPreference mcIpPref;
	private MyEditIntegerPreference mcPortPref;
	
	private MyEditIntegerPreference mcConnIdlePref;
	private MyEditIntegerPreference mcNetConnPref;
	private MyEditIntegerPreference mcTTLPref;

	// Reliable Multicast
	private MyCheckBoxPreference rmcOpEnablePref;
	
	private MyEditTextPreference rmcIpPref;
	private MyEditIntegerPreference rmcPortPref;
	
	private MyEditIntegerPreference rmcConnIdlePref;
	private MyEditIntegerPreference rmcNetConnPref;
	private MyEditIntegerPreference rmcTtlPref;

    // Serial port
	private MyCheckBoxPreference serialOpEnablePref;
	
	private MyEditTextPreference serialDevicePref;
	private MyEditIntegerPreference serialBaudPref;
	private MyEditIntegerPreference serialSlotPref;
	private MyEditIntegerPreference serialRadiosInGroupPref;
	private MyEditIntegerPreference serialSlotDurationPref;
	private MyEditIntegerPreference serialTransmitDurationPref;
	private MyCheckBoxPreference serialOpEnableSendingPref;
	private MyCheckBoxPreference serialOpEnableReceivingPref;


	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.general_preferences);
		
		final Resources res = this.getResources();
	
		level = (MyEditTextPreference) findPreference(PREF_LOG_LEVEL);
		level.setSummaryPrefix(res.getString(R.string.log_level_label));
		level.setType(MyEditTextPreference.Type.LOG_LEVEL);
		
		name = (MyEditTextPreference) this.findPreference(INetPrefKeys.CORE_OPERATOR_ID);
		name.setSummaryPrefix(res.getString(R.string.operator_id_label));
		name.setType(MyEditTextPreference.Type.OPERATOR_ID);
		name.refreshSummaryField();
		
		/*
		 * Gateway Setup
		 */
		
		// IP Preference Setup
		this.gwIpPref = (MyEditTextPreference) this.findPreference(INetPrefKeys.GATEWAY_HOST);
		this.gwIpPref.setType(MyEditTextPreference.Type.IP);
		this.gwIpPref.refreshSummaryField();
		
		// Port Preference Setup
		this.gwPortPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.GATEWAY_PORT);
		this.gwPortPref.setType(Type.PORT);
		this.gwPortPref.refreshSummaryField();
		
		// Enabled Preference Setup
		this.gwOpEnablePref = (MyCheckBoxPreference) this.findPreference(INetPrefKeys.GATEWAY_DISABLED);
		this.gwOpEnablePref.refreshSummaryField();
		
		// Connection Idle Timeout
		this.gwConnIdlePref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.GATEWAY_TIMEOUT);
		this.gwConnIdlePref.setType(Type.TIMEOUT);
		this.gwConnIdlePref.refreshSummaryField();
		
		// Network Connection Timeout
		this.gwNetConnPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.GATEWAY_FLAT_LINE_TIME);
		this.gwNetConnPref.setType(Type.TIMEOUT);
		this.gwNetConnPref.refreshSummaryField();


		/*
		 * Multicast Setup
		 */
		this.mcIpPref = (MyEditTextPreference) this.findPreference(INetPrefKeys.MULTICAST_HOST);
		this.mcIpPref.setType(MyEditTextPreference.Type.IP);
		this.mcIpPref.refreshSummaryField();
		
		// Port Preference Setup
		this.mcPortPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_PORT);
		this.mcPortPref.setType(Type.PORT);
		this.mcPortPref.refreshSummaryField();
		
		// Enabled Preference Setup
		this.mcOpEnablePref = (MyCheckBoxPreference) this.findPreference(INetPrefKeys.MULTICAST_DISABLED);
		this.mcOpEnablePref.refreshSummaryField();
		
		// Connection Idle Timeout
		this.mcConnIdlePref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT);
		this.mcConnIdlePref.setType(Type.TIMEOUT);
		this.mcConnIdlePref.refreshSummaryField();
		
		// Network Connection Timeout
		this.mcNetConnPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT);
		this.mcNetConnPref.setType(Type.TIMEOUT);
		this.mcNetConnPref.refreshSummaryField();

		// Network Connection Timeout
		this.mcTTLPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_TTL);
		this.mcTTLPref.setType(Type.TTL);
		this.mcTTLPref.refreshSummaryField();
		
		/*
		 * Reliable Multicast Setup
		 */
		this.rmcIpPref = (MyEditTextPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_HOST);
		this.rmcIpPref.setType(MyEditTextPreference.Type.IP);
		this.rmcIpPref.refreshSummaryField();
		
		// Port Preference Setup
		this.rmcPortPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_PORT);
		this.rmcPortPref.setType(Type.PORT);
		this.rmcPortPref.refreshSummaryField();
		
		// Enabled Preference Setup
		this.rmcOpEnablePref = (MyCheckBoxPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_DISABLED);
		this.rmcOpEnablePref.refreshSummaryField();
		
		// Connection Idle Timeout
		this.rmcConnIdlePref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_CONN_IDLE_TIMEOUT);
		this.rmcConnIdlePref.setType(Type.TIMEOUT);
		this.rmcConnIdlePref.refreshSummaryField();
		
		// Network Connection Timeout
		this.rmcNetConnPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_NET_CONN_TIMEOUT);
		this.rmcNetConnPref.setType(Type.TIMEOUT);
		this.rmcNetConnPref.refreshSummaryField();

		// Network Connection Timeout
		this.rmcTtlPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_TTL);
		this.rmcTtlPref.setType(Type.TTL);
		this.rmcTtlPref.refreshSummaryField();
		
		
		/*
		 * Serial Setup
		 */

		this.serialOpEnablePref = (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_DISABLED);
		this.serialOpEnablePref.refreshSummaryField();

		this.serialDevicePref = (MyEditTextPreference)this.findPreference(INetPrefKeys.SERIAL_DEVICE);
		//this.devicePref.setType(MyEditTextPreference.Type.DEVICE_ID);
		this.serialDevicePref.refreshSummaryField();

		this.serialBaudPref = (MyEditIntegerPreference)this.findPreference(INetPrefKeys.SERIAL_BAUD_RATE);
		this.serialBaudPref.setType(Type.BAUDRATE);
		this.serialBaudPref.refreshSummaryField();

		this.serialSlotPref = (MyEditIntegerPreference)this.findPreference(INetPrefKeys.SERIAL_SLOT_NUMBER);
		this.serialSlotPref.setType(Type.SLOT_NUMBER);
		this.serialSlotPref.refreshSummaryField();

		this.serialRadiosInGroupPref = (MyEditIntegerPreference) this.findPreference( INetPrefKeys.SERIAL_RADIOS_IN_GROUP );
		this.serialRadiosInGroupPref.setType(Type.RADIOS_IN_GROUP);
		this.serialRadiosInGroupPref.refreshSummaryField();

		this.serialSlotDurationPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.SERIAL_SLOT_DURATION);
		this.serialSlotDurationPref.setType(Type.SLOT_DURATION);
		this.serialSlotDurationPref.refreshSummaryField();

		this.serialTransmitDurationPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.SERIAL_TRANSMIT_DURATION);
		this.serialTransmitDurationPref.setType(Type.TRANSMIT_DURATION);
		this.serialTransmitDurationPref.refreshSummaryField();

		this.serialOpEnableSendingPref = (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_SEND_ENABLED);
		this.serialOpEnableSendingPref.refreshSummaryField();

		this.serialOpEnableReceivingPref = (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_RECEIVE_ENABLED);
		this.serialOpEnableReceivingPref.refreshSummaryField();


		// System.setProperty(prop, value);
		// export ANDROID_LOG_TAGS="ActivityManager:I MyApp:D *:S"

		this.setupViews();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	// ===========================================================
	// UI Management
	// ===========================================================
	public void setupViews() {
		// Set the summary of each edit text to the current value
		// of its EditText field.
		if (level != null) level.refreshSummaryField();
	}
	
	// ===========================================================
	// Methods
	// ===========================================================
	
	
	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

}

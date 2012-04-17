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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final Logger logger = LoggerFactory.getLogger("GeneralPreferences");
	
    public static final String LAUNCH = "edu.vu.isis.ammo.core.LoggingPreferences.LAUNCH";

	public static final String PREF_LOG_LEVEL = "CORE_LOG_LEVEL";
	
	// ===========================================================
	// Fields
	// ===========================================================
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
		logger.error("on create");
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.general_preferences);
		
		final Resources res = this.getResources();
		
		name = (MyEditTextPreference) this.findPreference(INetPrefKeys.CORE_OPERATOR_ID);
		name.setSummaryPrefix(res.getString(R.string.operator_id_label));
		name.setType(MyEditTextPreference.Type.OPERATOR_ID);
		
		/*
		 * Gateway Setup
		 */
		
		// IP Preference Setup
		this.gwIpPref = (MyEditTextPreference) this.findPreference(INetPrefKeys.GATEWAY_HOST);
		this.gwIpPref.setType(MyEditTextPreference.Type.IP);
		
		// Port Preference Setup
		this.gwPortPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.GATEWAY_PORT);
		this.gwPortPref.setType(Type.PORT);
		
		// Enabled Preference Setup
		this.gwOpEnablePref = (MyCheckBoxPreference) this.findPreference(INetPrefKeys.GATEWAY_DISABLED);
		
		// Connection Idle Timeout
		this.gwConnIdlePref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.GATEWAY_TIMEOUT);
		this.gwConnIdlePref.setType(Type.TIMEOUT);
		
		// Network Connection Timeout
		this.gwNetConnPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.GATEWAY_FLAT_LINE_TIME);
		this.gwNetConnPref.setType(Type.TIMEOUT);


		/*
		 * Multicast Setup
		 */
		this.mcIpPref = (MyEditTextPreference) this.findPreference(INetPrefKeys.MULTICAST_HOST);
		this.mcIpPref.setType(MyEditTextPreference.Type.IP);
		
		// Port Preference Setup
		this.mcPortPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_PORT);
		this.mcPortPref.setType(Type.PORT);
		
		// Enabled Preference Setup
		this.mcOpEnablePref = (MyCheckBoxPreference) this.findPreference(INetPrefKeys.MULTICAST_DISABLED);
		
		// Connection Idle Timeout
		this.mcConnIdlePref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT);
		this.mcConnIdlePref.setType(Type.TIMEOUT);
		
		// Network Connection Timeout
		this.mcNetConnPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT);
		this.mcNetConnPref.setType(Type.TIMEOUT);

		// Network Connection Timeout
		this.mcTTLPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_TTL);
		this.mcTTLPref.setType(Type.TTL);
		
		/*
		 * Reliable Multicast Setup
		 */
		this.rmcIpPref = (MyEditTextPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_HOST);
		this.rmcIpPref.setType(MyEditTextPreference.Type.IP);
		
		// Port Preference Setup
		this.rmcPortPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_PORT);
		this.rmcPortPref.setType(Type.PORT);
		
		// Enabled Preference Setup
		this.rmcOpEnablePref = (MyCheckBoxPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_DISABLED);
		
		// Connection Idle Timeout
		this.rmcConnIdlePref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_CONN_IDLE_TIMEOUT);
		this.rmcConnIdlePref.setType(Type.TIMEOUT);
		
		// Network Connection Timeout
		this.rmcNetConnPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_NET_CONN_TIMEOUT);
		this.rmcNetConnPref.setType(Type.TIMEOUT);

		// Network Connection Timeout
		this.rmcTtlPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.RELIABLE_MULTICAST_TTL);
		this.rmcTtlPref.setType(Type.TTL);	
		
		/*
		 * Serial Setup
		 */
		this.serialOpEnablePref = (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_DISABLED);

		this.serialDevicePref = (MyEditTextPreference)this.findPreference(INetPrefKeys.SERIAL_DEVICE);
		//this.devicePref.setType(MyEditTextPreference.Type.DEVICE_ID);

		this.serialBaudPref = (MyEditIntegerPreference)this.findPreference(INetPrefKeys.SERIAL_BAUD_RATE);
		this.serialBaudPref.setType(Type.BAUDRATE);

		this.serialSlotPref = (MyEditIntegerPreference)this.findPreference(INetPrefKeys.SERIAL_SLOT_NUMBER);
		this.serialSlotPref.setType(Type.SLOT_NUMBER);

		this.serialRadiosInGroupPref = (MyEditIntegerPreference) this.findPreference( INetPrefKeys.SERIAL_RADIOS_IN_GROUP );
		this.serialRadiosInGroupPref.setType(Type.RADIOS_IN_GROUP);

		this.serialSlotDurationPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.SERIAL_SLOT_DURATION);
		this.serialSlotDurationPref.setType(Type.SLOT_DURATION);

		this.serialTransmitDurationPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.SERIAL_TRANSMIT_DURATION);
		this.serialTransmitDurationPref.setType(Type.TRANSMIT_DURATION);

		this.serialOpEnableSendingPref = (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_SEND_ENABLED);

		this.serialOpEnableReceivingPref = (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_RECEIVE_ENABLED);

		// System.setProperty(prop, value);
		// export ANDROID_LOG_TAGS="ActivityManager:I MyApp:D *:S"

		this.setupViews();
	}
	
	@Override
	protected void onResume() {
		logger.error("on resume");
		super.onResume();
		
		this.name.refresh();
		
		/*
		 * Gateway Setup
		 */
		this.gwIpPref.refresh();
		this.gwPortPref.refresh();
		this.gwOpEnablePref.refresh();
		this.gwConnIdlePref.refresh();
		this.gwNetConnPref.refresh();

		/*
		 * Multicast Setup
		 */
		this.mcIpPref.refresh();
		this.mcPortPref.refresh();
		this.mcOpEnablePref.refresh();
		this.mcConnIdlePref.refresh();
		this.mcNetConnPref.refresh();
		this.mcTTLPref.refresh();
		
		/*
		 * Reliable Multicast Setup
		 */
		this.rmcIpPref.refresh();
		this.rmcPortPref.refresh();
		this.rmcOpEnablePref.refresh();
		this.rmcConnIdlePref.refresh();
		this.rmcNetConnPref.refresh();
		this.rmcTtlPref.refresh();
		
		/*
		 * Serial Setup
		 */
		this.serialOpEnablePref.refresh();
		this.serialDevicePref.refresh();
		this.serialBaudPref.refresh();
		this.serialSlotPref.refresh();
		this.serialRadiosInGroupPref.refresh();
		this.serialSlotDurationPref.refresh();
		this.serialTransmitDurationPref.refresh();
		this.serialOpEnableSendingPref.refresh();
		this.serialOpEnableReceivingPref.refresh();

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
	}
	
	// ===========================================================
	// Methods
	// ===========================================================
	
	
	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

}

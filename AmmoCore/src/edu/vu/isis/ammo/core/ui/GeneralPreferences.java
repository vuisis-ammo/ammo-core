package edu.vu.isis.ammo.core.ui;

import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;
import edu.vu.isis.ammo.core.ui.util.PreferenceActivityEx;

/**
 * View and change the core application preferences.
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

	// Multicast
	private MyEditTextPreference mcIpPref;
	private MyEditIntegerPreference mcPortPref;
	private MyCheckBoxPreference mcEnabledPref;
	private MyEditIntegerPreference mcConnIdlePref;
	private MyEditIntegerPreference mcNetConnPref;

	private MyEditTextPreference ipPref;

	private MyEditIntegerPreference portPref;

	private MyCheckBoxPreference enabledPref;

	private MyEditIntegerPreference connIdlePref;

	private MyEditIntegerPreference netConnPref;

	private MyEditTextPreference devicePref;

	private MyCheckBoxPreference serialUsePref;

	private MyCheckBoxPreference sendingPref;

	private MyCheckBoxPreference receivingPref;

	private MyEditIntegerPreference baudPref;

	private MyEditIntegerPreference slotPref;

	private MyEditIntegerPreference debugPref;
	
	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.general_preferences);
		
		Resources res = this.getResources();
	
		level = (MyEditTextPreference) findPreference(PREF_LOG_LEVEL);
		level.setSummaryPrefix(res.getString(R.string.log_level_label));
		level.setType(MyEditTextPreference.Type.LOG_LEVEL);
		
		name = (MyEditTextPreference) this.findPreference(INetPrefKeys.CORE_OPERATOR_ID);
		name.setSummaryPrefix(res.getString(R.string.operator_id_label));
		name.setType(MyEditTextPreference.Type.OPERATOR_ID);
		
		/*
		 * Multicast Setup
		 */
		this.mcIpPref = (MyEditTextPreference) this.findPreference(INetPrefKeys.MULTICAST_IP_ADDRESS);
		this.mcIpPref.setType(MyEditTextPreference.Type.IP);
		this.mcIpPref.refreshSummaryField();
		
		// Port Preference Setup
		this.mcPortPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_PORT);
		this.mcPortPref.setType(Type.PORT);
		this.mcPortPref.refreshSummaryField();
		
		// Enabled Preference Setup
		this.mcEnabledPref = (MyCheckBoxPreference) this.findPreference(INetPrefKeys.MULTICAST_SHOULD_USE);
		this.mcEnabledPref.refreshSummaryField();
		
		// Connection Idle Timeout
		this.mcConnIdlePref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT);
		this.mcConnIdlePref.setType(Type.TIMEOUT);
		this.mcConnIdlePref.refreshSummaryField();
		
		// Network Connection Timeout
		this.mcNetConnPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT);
		this.mcNetConnPref.setType(Type.TIMEOUT);
		this.mcNetConnPref.refreshSummaryField();
		
		
		/*
		 * Gateway Setup
		 */
		
		// IP Preference Setup
		this.ipPref = (MyEditTextPreference) this.findPreference(INetPrefKeys.CORE_IP_ADDR);
		this.ipPref.setType(MyEditTextPreference.Type.IP);
		this.ipPref.refreshSummaryField();
		
		// Port Preference Setup
		this.portPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.CORE_IP_PORT);
		this.portPref.setType(Type.PORT);
		this.portPref.refreshSummaryField();
		
		// Enabled Preference Setup
		this.enabledPref = (MyCheckBoxPreference) this.findPreference(INetPrefKeys.GATEWAY_SHOULD_USE);
		this.enabledPref.refreshSummaryField();
		
		// Connection Idle Timeout
		this.connIdlePref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.CORE_SOCKET_TIMEOUT);
		this.connIdlePref.setType(Type.TIMEOUT);
		this.connIdlePref.refreshSummaryField();
		
		// Network Connection Timeout
		this.netConnPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.NET_CONN_FLAT_LINE_TIME);
		this.netConnPref.setType(Type.TIMEOUT);
		this.netConnPref.refreshSummaryField();
		
		
		/*
		 * Serial Setup
		 */
		
		this.devicePref = (MyEditTextPreference)this.findPreference(INetPrefKeys.SERIAL_DEVICE);
		//this.devicePref.setType(MyEditTextPreference.Type.DEVICE_ID);
		this.devicePref.refreshSummaryField();
		
		this.serialUsePref = (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_SHOULD_USE);
		this.serialUsePref.refreshSummaryField();
		
		this.sendingPref = (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_SEND_ENABLED);
		this.sendingPref.refreshSummaryField();
		
		this.receivingPref = (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_RECEIVE_ENABLED);
		this.receivingPref.refreshSummaryField();
		
		this.baudPref = (MyEditIntegerPreference)this.findPreference(INetPrefKeys.SERIAL_BAUD_RATE);
		this.baudPref.setType(Type.BAUDRATE);
		this.baudPref.refreshSummaryField();
		
		this.slotPref = (MyEditIntegerPreference)this.findPreference(INetPrefKeys.SERIAL_SLOT_NUMBER);
		this.slotPref.setType(Type.SLOT_NUMBER);
		this.slotPref.refreshSummaryField();
		
		this.debugPref = (MyEditIntegerPreference)this.findPreference(INetPrefKeys.SERIAL_DEBUG_PERIOD);
		this.debugPref.setType(Type.DEBUG_PERIOD);
		this.debugPref.refreshSummaryField();
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

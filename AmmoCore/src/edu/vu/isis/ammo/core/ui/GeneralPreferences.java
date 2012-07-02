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

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.view.KeyEvent;
import android.widget.Adapter;
import android.widget.Toast;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ui.util.PreferenceActivityEx;

/**
 * View and change the ammo core preferences. These are primarily concerned with
 * the management of the logging framework.
 * 
 * @author phreed
 * 
 */
public class GeneralPreferences extends PreferenceActivityEx {
	// ===========================================================
	// Constants
	// ===========================================================
	private static final Logger logger = LoggerFactory.getLogger("ui.gprefs");

	public static final String LAUNCH = "edu.vu.isis.ammo.core.LoggingPreferences.LAUNCH";

	public static final String PREF_LOG_LEVEL = "CORE_LOG_LEVEL";

	private static final String TRUE_TITLE_SUFFIX = "is enabled.";
	private static final String FALSE_TITLE_SUFFIX = "is disabled.";

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

	private MyEditIntegerPreference serialSlotPref;
	private MyEditIntegerPreference serialRadiosInGroupPref;
	private MyEditIntegerPreference serialSlotDurationPref;
	private MyEditIntegerPreference serialTransmitDurationPref;

	// Unused
	// private MyEditIntegerPreference serialBaudPref;
	// private MyCheckBoxPreference serialOpEnableSendingPref;
	// private MyCheckBoxPreference serialOpEnableReceivingPref;

	private boolean isListClick = false;

	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		logger.error("on create");
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.general_preferences);

		final Resources res = this.getResources();

		this.name = (MyEditTextPreference) this
				.findPreference(INetPrefKeys.CORE_OPERATOR_ID);
		this.name.setSummaryPrefix(res.getString(R.string.operator_id_label));
		this.name.setType(MyEditTextPreference.Type.OPERATOR_ID);

		/*
		 * Gateway Setup
		 */

		// IP Preference Setup
		this.gwIpPref = (MyEditTextPreference) this
				.findPreference(INetPrefKeys.GATEWAY_HOST);
		this.gwIpPref.setType(MyEditTextPreference.Type.IP);
		this.gwIpPref.setSummarySuffix(" (Set in Panther Prefs)");
		this.gwIpPref.setOnPreferenceClickListener(sendToPantherPrefsListener);

		// Port Preference Setup
		this.gwPortPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.GATEWAY_PORT);
		this.gwPortPref.setType(Type.PORT);

		// Enabled Preference Setup
		this.gwOpEnablePref = (MyCheckBoxPreference) this
				.findPreference(INetPrefKeys.GATEWAY_DISABLED);

		this.gwOpEnablePref.setTrueTitle("Gateway " + TRUE_TITLE_SUFFIX);
		this.gwOpEnablePref.setFalseTitle("Gateway " + FALSE_TITLE_SUFFIX);
		this.gwOpEnablePref.setOnPreferenceClickListener(sendToPantherPrefsListener);

		// Connection Idle Timeout
		this.gwConnIdlePref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.GATEWAY_TIMEOUT);
		this.gwConnIdlePref.setType(Type.TIMEOUT);

		// Network Connection Timeout
		this.gwNetConnPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.GATEWAY_FLAT_LINE_TIME);
		this.gwNetConnPref.setType(Type.TIMEOUT);

		/*
		 * Multicast Setup
		 */
		this.mcIpPref = (MyEditTextPreference) this
				.findPreference(INetPrefKeys.MULTICAST_HOST);
		this.mcIpPref.setType(MyEditTextPreference.Type.IP);

		// Port Preference Setup
		this.mcPortPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.MULTICAST_PORT);
		this.mcPortPref.setType(Type.PORT);

		// Enabled Preference Setup
		this.mcOpEnablePref = (MyCheckBoxPreference) this
				.findPreference(INetPrefKeys.MULTICAST_DISABLED);
		mcOpEnablePref.setTrueTitle("Multicast " + TRUE_TITLE_SUFFIX);
		mcOpEnablePref.setFalseTitle("Multicast " + FALSE_TITLE_SUFFIX);
		mcOpEnablePref.setOnPreferenceClickListener(sendToPantherPrefsListener);

		// Connection Idle Timeout
		this.mcConnIdlePref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT);
		this.mcConnIdlePref.setType(Type.TIMEOUT);

		// Network Connection Timeout
		this.mcNetConnPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT);
		this.mcNetConnPref.setType(Type.TIMEOUT);

		// Network Connection Timeout
		this.mcTTLPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.MULTICAST_TTL);
		this.mcTTLPref.setType(Type.TTL);

		/*
		 * Reliable Multicast Setup
		 */
		this.rmcIpPref = (MyEditTextPreference) this
				.findPreference(INetPrefKeys.RELIABLE_MULTICAST_HOST);
		this.rmcIpPref.setType(MyEditTextPreference.Type.IP);

		// Port Preference Setup
		this.rmcPortPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.RELIABLE_MULTICAST_PORT);
		this.rmcPortPref.setType(Type.PORT);

		// Enabled Preference Setup
		this.rmcOpEnablePref = (MyCheckBoxPreference) this
				.findPreference(INetPrefKeys.RELIABLE_MULTICAST_DISABLED);
		rmcOpEnablePref.setTrueTitle("Reliable Multicast " + TRUE_TITLE_SUFFIX);
		rmcOpEnablePref.setFalseTitle("Reliable Multicast "
				+ FALSE_TITLE_SUFFIX);
		rmcOpEnablePref
				.setOnPreferenceClickListener(sendToPantherPrefsListener);

		// Connection Idle Timeout
		this.rmcConnIdlePref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.RELIABLE_MULTICAST_CONN_IDLE_TIMEOUT);
		this.rmcConnIdlePref.setType(Type.TIMEOUT);

		// Network Connection Timeout
		this.rmcNetConnPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.RELIABLE_MULTICAST_NET_CONN_TIMEOUT);
		this.rmcNetConnPref.setType(Type.TIMEOUT);

		// Network Connection Timeout
		this.rmcTtlPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.RELIABLE_MULTICAST_TTL);
		this.rmcTtlPref.setType(Type.TTL);

		/*
		 * Serial Setup
		 */
		this.serialOpEnablePref = (MyCheckBoxPreference) this
				.findPreference(INetPrefKeys.SERIAL_DISABLED);
		this.serialOpEnablePref.setTrueTitle("Serial " + TRUE_TITLE_SUFFIX);
		this.serialOpEnablePref.setFalseTitle("Serial " + FALSE_TITLE_SUFFIX);
		this.serialOpEnablePref.setSummary("Touch me to toggle");
		this.serialOpEnablePref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference preference) {
						serialOpEnablePref.toggle();
						return true;
					}
				});

		this.serialDevicePref = (MyEditTextPreference) this
				.findPreference(INetPrefKeys.SERIAL_DEVICE);

		// Baud rate was removed from prefs
		// this.serialBaudPref =
		// (MyEditIntegerPreference)this.findPreference(INetPrefKeys.SERIAL_BAUD_RATE);
		// this.serialBaudPref.setType(Type.BAUDRATE);

		this.serialSlotPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.SERIAL_SLOT_NUMBER);
		this.serialSlotPref.setType(Type.SLOT_NUMBER);
		this.serialSlotPref.setSummarySuffix(" (Set in Panther Prefs)");
		this.serialSlotPref.setOnPreferenceClickListener(sendToPantherPrefsListener);

		this.serialRadiosInGroupPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.SERIAL_RADIOS_IN_GROUP);
		this.serialRadiosInGroupPref.setType(Type.RADIOS_IN_GROUP);
		this.serialRadiosInGroupPref.setSummarySuffix(" (Set in Panther Prefs)");
		this.serialRadiosInGroupPref.setOnPreferenceClickListener(sendToPantherPrefsListener);

		this.serialSlotDurationPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.SERIAL_SLOT_DURATION);
		this.serialSlotDurationPref.setType(Type.SLOT_DURATION);

		this.serialTransmitDurationPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.SERIAL_TRANSMIT_DURATION);
		this.serialTransmitDurationPref.setType(Type.TRANSMIT_DURATION);

		// These were removed from prefs
		// this.serialOpEnableSendingPref =
		// (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_SEND_ENABLED);
		// this.serialOpEnableReceivingPref =
		// (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_RECEIVE_ENABLED);

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

		// Baud rate pref was removed
		// this.serialBaudPref.refresh();

		this.serialSlotPref.refresh();
		this.serialRadiosInGroupPref.refresh();
		this.serialSlotDurationPref.refresh();
		this.serialTransmitDurationPref.refresh();

		// These prefs were removed
		// this.serialOpEnableSendingPref.refresh();
		// this.serialOpEnableReceivingPref.refresh();

		this.setupViews();

		Intent receivedIntent = getIntent();
		int which = receivedIntent.getIntExtra(AmmoCore.PREF_KEY, -1);
		if (which != -1) {
			isListClick = true;
			autoSelect(which);
		}

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			logger.error("back button pressed");
		}
		return super.onKeyDown(keyCode, event);
	}

	// ===========================================================
	// UI Management
	// ===========================================================
	public void setupViews() {
		// Set the summary of each edit text to the current value
		// of its EditText field.
	}

	/**
	 * Automatically clicks on a certain preference so that we can give the
	 * illusion of going directly to a nested preference screen
	 * 
	 * @param which
	 *            the preference to click on
	 */
	private void autoSelect(int which) {
		final PreferenceScreen screen = getPreferenceScreen();
		final Preference pref;

		switch (which) {
		case AmmoCore.GATEWAY:
			pref = screen.findPreference(getResources().getString(
					R.string.gateway_pref_screen));
			break;
		case AmmoCore.MULTICAST:
			pref = screen.findPreference(getResources().getString(
					R.string.multicast_pref_screen));
			break;
		case AmmoCore.RELIABLE_MULTICAST:
			pref = screen.findPreference(getResources().getString(
					R.string.reliable_multicast_pref_screen));
			break;
		case AmmoCore.SERIAL:
			pref = screen.findPreference(getResources().getString(
					R.string.serial_pref_screen));
			break;
		default:
			logger.error("Got invalid preference number in intent");
			Toast.makeText(this, "Preference not valid", Toast.LENGTH_LONG);
			return;
		}

		int pos = searchForPreference(pref, screen.getRootAdapter());

		if (pos == -1) {
			logger.error("Did not find preference " + pref);
			Toast.makeText(this, "Preference screen does not exist",
					Toast.LENGTH_LONG);
			return;
		}

		screen.onItemClick(null, null, pos, 0);

		if (pref instanceof PreferenceScreen) {
			((PreferenceScreen) pref).getDialog().setOnDismissListener(
					new OnDismissListener() {

						GeneralPreferences parent = GeneralPreferences.this;

						@Override
						public void onDismiss(DialogInterface dialog) {
							parent.logger.error("Dismiss called");
							((PreferenceScreen) pref).onDismiss(dialog);
							parent.onBackPressed();
						}

					});
		}

	}

	/**
	 * Linearly searches for a given preference inside an adapter. Returns the
	 * index of the preference in the adapter or -1 if it is not found.
	 * 
	 * @param pref
	 *            the preference to search for
	 * @param adapter
	 *            the adapter for the preferences
	 * @return the index of the preference in the adapter or -1 if not found
	 */
	private int searchForPreference(Preference pref, Adapter adapter) {

		for (int i = 0; i < adapter.getCount(); i++) {
			Object o = adapter.getItem(i);
			if (o == pref)
				return i;
		}

		return -1;

	}

	// ===========================================================
	// Methods
	// ===========================================================

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	final OnPreferenceClickListener sendToPantherPrefsListener = new OnPreferenceClickListener() {
		@Override
		public boolean onPreferenceClick(Preference preference) {
			startActivity(new Intent()
					.setComponent(new ComponentName("transapps.settings",
							"transapps.settings.SettingsActivity")));
			return true;
		}
	};

}

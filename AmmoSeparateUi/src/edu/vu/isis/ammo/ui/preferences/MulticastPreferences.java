package edu.vu.isis.ammo.ui.preferences;

import android.os.Bundle;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vanderbilt.isis.ammo.ui.R;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;

public class MulticastPreferences extends AbstractAmmoPreference{

	private MyCheckBoxPreference mcOpEnablePref;

	private MyEditTextPreference mcIpPref;
	private MyEditIntegerPreference mcPortPref;

	private MyEditIntegerPreference mcConnIdlePref;
	private MyEditIntegerPreference mcNetConnPref;
	private MyEditIntegerPreference mcTTLPref;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		logger.error("on create");
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.multicast_preferences);
	
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
	}
	
	@Override
	protected void onResume() {
		logger.error("on resume");
		super.onResume();
		
		this.mcIpPref.refresh();
		this.mcPortPref.refresh();
		this.mcOpEnablePref.refresh();
		this.mcConnIdlePref.refresh();
		this.mcNetConnPref.refresh();
		this.mcTTLPref.refresh();
	}
}

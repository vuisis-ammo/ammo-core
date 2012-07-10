package edu.vu.isis.ammo.core.ui;

import android.os.Bundle;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;

public class ReliableMulticastPreferences extends AbstractAmmoPreference {

	// Reliable Multicast
	private MyCheckBoxPreference rmcOpEnablePref;

	private MyEditTextPreference rmcIpPref;
	private MyEditIntegerPreference rmcPortPref;

	private MyEditIntegerPreference rmcConnIdlePref;
	private MyEditIntegerPreference rmcNetConnPref;
	private MyEditIntegerPreference rmcTtlPref;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		logger.error("on create");
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.reliable_multicast_preferences);
		
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
	}
	
	@Override
	protected void onResume() {
		logger.error("on resume");
		super.onResume();
	
		this.rmcIpPref.refresh();
		this.rmcPortPref.refresh();
		this.rmcOpEnablePref.refresh();
		this.rmcConnIdlePref.refresh();
		this.rmcNetConnPref.refresh();
		this.rmcTtlPref.refresh();
	}
	
}

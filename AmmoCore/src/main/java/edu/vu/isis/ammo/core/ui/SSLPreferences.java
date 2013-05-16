package edu.vu.isis.ammo.core.ui;

import android.content.res.Resources;
import android.os.Bundle;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;

public class SSLPreferences extends AbstractAmmoPreference {

	private MyCheckBoxPreference gwOpEnablePref;

	private MyEditTextPreference gwIpPref;
	private MyEditIntegerPreference gwPortPref;

	private MyEditIntegerPreference gwConnIdlePref;
	private MyEditIntegerPreference gwNetConnPref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		logger.error("on create");
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.ssl_preferences);

		final Resources res = this.getResources();

		// IP Preference Setup
		this.gwIpPref = (MyEditTextPreference) this
				.findPreference(INetPrefKeys.SSL_HOST);
		this.gwIpPref.setType(MyEditTextPreference.Type.IP);
		this.gwIpPref.setSummarySuffix(" (Set in Panthr Prefs)");
		this.gwIpPref.setOnPreferenceClickListener(sendToPantherPrefsListener);

		// Port Preference Setup
		this.gwPortPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.SSL_PORT);
		this.gwPortPref.setType(Type.PORT);

		// Enabled Preference Setup
		this.gwOpEnablePref = (MyCheckBoxPreference) this
				.findPreference(INetPrefKeys.SSL_DISABLED);

		this.gwOpEnablePref.setTrueTitle("SSL " + TRUE_TITLE_SUFFIX);
		this.gwOpEnablePref.setFalseTitle("SSL " + FALSE_TITLE_SUFFIX);
		this.gwOpEnablePref
				.setOnPreferenceClickListener(sendToPantherPrefsListener);

		// Connection Idle Timeout
		this.gwConnIdlePref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.SSL_TIMEOUT);
		this.gwConnIdlePref.setType(Type.TIMEOUT);

		// Network Connection Timeout
		this.gwNetConnPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.SSL_FLAT_LINE_TIME);
		this.gwNetConnPref.setType(Type.TIMEOUT);

	}
	
	@Override
	protected void onResume() {
		logger.error("on resume");
		super.onResume();
		
		this.gwIpPref.refresh();
		this.gwPortPref.refresh();
		this.gwOpEnablePref.refresh();
		this.gwConnIdlePref.refresh();
		this.gwNetConnPref.refresh();
	}
	
	

}

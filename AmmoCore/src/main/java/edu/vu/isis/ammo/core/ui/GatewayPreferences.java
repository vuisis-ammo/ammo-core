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


package edu.vu.isis.ammo.core.ui;

import android.content.res.Resources;
import android.os.Bundle;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;

public class GatewayPreferences extends AbstractAmmoPreference {

	private MyCheckBoxPreference gwOpEnablePref;

	private MyEditTextPreference gwIpPref;
	private MyEditIntegerPreference gwPortPref;

	private MyEditIntegerPreference gwConnIdlePref;
	private MyEditIntegerPreference gwNetConnPref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		logger.error("on create");
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.gateway_preferences);

		final Resources res = this.getResources();

		// IP Preference Setup
		this.gwIpPref = (MyEditTextPreference) this
				.findPreference(INetPrefKeys.GATEWAY_HOST);
		this.gwIpPref.setType(MyEditTextPreference.Type.IP);
		this.gwIpPref.setSummarySuffix(" (Set in Panthr Prefs)");
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
		this.gwOpEnablePref
				.setOnPreferenceClickListener(sendToPantherPrefsListener);

		// Connection Idle Timeout
		this.gwConnIdlePref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.GATEWAY_TIMEOUT);
		this.gwConnIdlePref.setType(Type.TIMEOUT);

		// Network Connection Timeout
		this.gwNetConnPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.GATEWAY_FLAT_LINE_TIME);
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

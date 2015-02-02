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

import android.os.Bundle;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;

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

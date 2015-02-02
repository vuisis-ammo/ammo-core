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

public class ReliableMulticastPreferences extends AbstractAmmoPreference {

	// Reliable Multicast
	private MyCheckBoxPreference rmcOpEnablePref;

	private MyEditTextPreference rmcIpPref;
	private MyEditIntegerPreference rmcPortPref;
	private MyEditIntegerPreference rmcMediaPortPref;

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

		// Port Preference Setup
		this.rmcMediaPortPref = (MyEditIntegerPreference) this
				.findPreference(INetPrefKeys.RELIABLE_MULTICAST_MEDIA_PORT);
		this.rmcMediaPortPref.setType(Type.PORT);
		
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

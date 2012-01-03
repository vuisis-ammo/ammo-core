package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.SharedPreferences;
import android.os.Bundle;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ui.util.PreferenceActivityEx;

/**
 * Show details about the gateway.
 * Mostly explanatory information about the status.
 * 
 * @author phreed
 *
 */
public class ChannelDetailActivity extends PreferenceActivityEx
{
	public static final Logger logger = LoggerFactory.getLogger(ChannelDetailActivity.class);
	
	public static final String PREF_TYPE = "preference_type";
	public static final String IP = "ipKey";
	public static final String PORT = "portKey";
	public static final String NET_CONN = "netConnKey";
	public static final String CONN_IDLE = "connIdleKey";
	public static final String ENABLED = "enabledKey";
	
	public static final int GATEWAY_PREF = 0;
	public static final int MULTICAST_PREF = 1;
	// ===========================================================
	// Fields
	// ===========================================================
	
	// ===========================================================
	// Views
	// ===========================================================
	
	MyEditTextPreference ipPref = null;
	MyEditIntegerPreference portPref = null;
	MyCheckBoxPreference enabledPref = null;
	MyEditIntegerPreference connIdlePref = null;
	MyEditIntegerPreference netConnPref = null;
	SharedPreferences prefs = null;
	/**
	 * @Category Lifecycle
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.trace("::onCreate");
		
		switch(this.getIntent().getIntExtra(PREF_TYPE, GATEWAY_PREF))
		{
		case GATEWAY_PREF:
			this.addPreferencesFromResource(R.xml.gateway_prefs);
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
			break;
		case MULTICAST_PREF:
			this.addPreferencesFromResource(R.xml.multicast_prefs);
			// IP Preference Setup
			this.ipPref = (MyEditTextPreference) this.findPreference(INetPrefKeys.MULTICAST_IP_ADDRESS);
			this.ipPref.setType(MyEditTextPreference.Type.IP);
			this.ipPref.refreshSummaryField();
			
			// Port Preference Setup
			this.portPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_PORT);
			this.portPref.setType(Type.PORT);
			this.portPref.refreshSummaryField();
			
			// Enabled Preference Setup
			this.enabledPref = (MyCheckBoxPreference) this.findPreference(INetPrefKeys.MULTICAST_SHOULD_USE);
			this.enabledPref.refreshSummaryField();
			
			// Connection Idle Timeout
			this.connIdlePref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT);
			this.connIdlePref.setType(Type.TIMEOUT);
			this.connIdlePref.refreshSummaryField();
			
			// Network Connection Timeout
			this.netConnPref = (MyEditIntegerPreference) this.findPreference(INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT);
			this.netConnPref.setType(Type.TIMEOUT);
			this.netConnPref.refreshSummaryField();
			break;
		}
		

	
				

		
	}
	
	@Override
	public void onStart() {
		super.onStart();
		logger.trace("::onStart");
	}
	
	@Override
	public void onStop() {
		super.onStop();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		logger.trace("::onDestroy");
	}
	
}

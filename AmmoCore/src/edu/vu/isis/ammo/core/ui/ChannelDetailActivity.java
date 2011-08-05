package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
	public static final String IP = "ipKey";
	public static final String PORT = "portKey";
	public static final String NET_CONN = "netConnKey";
	public static final String CONN_IDLE = "connIdleKey";
	public static final String ENABLED = "enabledKey";
	// ===========================================================
	// Fields
	// ===========================================================
	
	private String ipKey = "";
	private String portKey = "";
	private String netConnKey = "";
	private String connIdleKey = "";
	private String enabledKey = "";
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
		this.addPreferencesFromResource(R.layout.gateway_detail_activity);
		this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		Intent i = this.getIntent();
		this.ipKey = i.getStringExtra(ChannelDetailActivity.IP);
		this.portKey = i.getStringExtra(ChannelDetailActivity.PORT);
		this.netConnKey = i.getStringExtra(ChannelDetailActivity.NET_CONN);
		this.connIdleKey = i.getStringExtra(ChannelDetailActivity.CONN_IDLE);
		this.enabledKey = i.getStringExtra(ChannelDetailActivity.ENABLED);
		
		// IP Preference Setup
		this.ipPref = (MyEditTextPreference) this.findPreference("ippref");
		this.ipPref.setType(MyEditTextPreference.Type.IP);
		this.ipPref.setKey(this.ipKey);
		this.ipPref.setText(this.prefs.getString(this.ipKey, "default"));
		this.ipPref.refreshSummaryField();
		
		
		// Port Preference Setup
		this.portPref = (MyEditIntegerPreference) this.findPreference("portpref");
		this.portPref.setType(Type.PORT);
		this.portPref.setKey(this.portKey);
		this.portPref.setText(this.prefs.getString(this.portKey, "default"));
		this.portPref.refreshSummaryField();
		
		// Enabled Preference Setup
		this.enabledPref = (MyCheckBoxPreference) this.findPreference("enabledpref");
		this.enabledPref.setKey(this.enabledKey);
		this.enabledPref.setChecked(this.prefs.getBoolean(this.enabledKey, true));
		this.enabledPref.refreshSummaryField();
		
		// Connection Idle Timeout
		this.connIdlePref = (MyEditIntegerPreference) this.findPreference("idle_timepref");
		this.connIdlePref.setType(Type.TIMEOUT);
		this.connIdlePref.setKey(this.connIdleKey);
		this.connIdlePref.setText(this.prefs.getString(this.connIdleKey, "10"));
		this.connIdlePref.refreshSummaryField();
		
		// Network Connection Timeout
		this.netConnPref = (MyEditIntegerPreference) this.findPreference("net_connpref");
		this.netConnPref.setType(Type.TIMEOUT);
		this.netConnPref.setKey(this.netConnKey);
		this.netConnPref.setText(this.prefs.getString(this.netConnKey, "10"));
		this.netConnPref.refreshSummaryField();
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

package edu.vu.isis.ammo.core.ui;

import android.content.res.Resources;
import android.os.Bundle;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ui.util.PreferenceActivityEx;

/**
 * View and change the core application preferences.
 * These are primarily concerned with the management of the 
 * communication channel.
 * 
 * @author phreed
 *
 */
public class CorePreferenceActivity extends PreferenceActivityEx {
	// ===========================================================
	// Constants
	// ===========================================================
    public static final String LAUNCH = "edu.vu.isis.ammo.core.Preference.LAUNCH";

	// ===========================================================
	// Fields
	// ===========================================================
	private MyEditTextPreference mIPAddr;
	
	private MyEditIntegerPreference mPort;
	private MyEditIntegerPreference mSocketTimeout;
	
	private MyCheckBoxPreference prefChannelJournal;
	
	private MyEditTextPreference mDeviceId;
	private MyEditTextPreference mOperatorId;
	private MyEditTextPreference mOperatorKey;
	
	private MyEditIntegerPreference flatLineTime;
	    
	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.layout.core_preferences);
		
		Resources res = this.getResources();
		/*
		mIPAddr = (MyEditTextPreference) findPreference(INetPrefKeys.CORE_IP_ADDR);
		mIPAddr.setSummaryPrefix(res.getString(R.string.ipaddr_label));
		mIPAddr.setType(MyEditTextPreference.Type.IP);
		
		mPort = (MyEditIntegerPreference) findPreference(INetPrefKeys.CORE_IP_PORT);
		mPort.setSummaryPrefix(res.getString(R.string.port_label));
		mPort.setType(MyEditIntegerPreference.Type.PORT);
		
		mSocketTimeout = (MyEditIntegerPreference)findPreference(INetPrefKeys.CORE_SOCKET_TIMEOUT);
		mSocketTimeout.setSummaryPrefix(res.getString(R.string.socket_timeout_label));
		mSocketTimeout.setType(MyEditIntegerPreference.Type.TIMEOUT);
		
		mDeviceId = (MyEditTextPreference) findPreference(INetPrefKeys.CORE_DEVICE_ID);
		String deviceId = UniqueIdentifiers.device(this);
		mDeviceId.setDefaultValue(deviceId);
		mDeviceId.setText(deviceId);
		mDeviceId.setSummaryPrefix(res.getString(R.string.device_id_label));
		mDeviceId.setType(MyEditTextPreference.Type.DEVICE_ID);
		*/
		mOperatorId = (MyEditTextPreference) findPreference(IPrefKeys.CORE_OPERATOR_ID);
		mOperatorId.setSummaryPrefix(res.getString(R.string.operator_id_label));
		mOperatorId.setType(MyEditTextPreference.Type.OPERATOR_ID);
		
		/*
		mOperatorKey = (MyEditTextPreference) findPreference(INetPrefKeys.CORE_OPERATOR_KEY);
		mOperatorKey.setSummaryPrefix(res.getString(R.string.operator_key_label));
		mOperatorKey.setType(MyEditTextPreference.Type.OPERATOR_KEY);
		

		flatLineTime = (MyEditIntegerPreference) findPreference(INetPrefKeys.NET_CONN_FLAT_LINE_TIME);
		flatLineTime.setSummaryPrefix(res.getString(R.string.net_conn_flat_line_label));
		flatLineTime.setType(MyEditIntegerPreference.Type.TIMEOUT);
		*/
		this.setupViews();
	}
	
	@Override
	public void onStop() {
		super.onStop();
	}
	
	// ===========================================================
	// UI Management
	// ===========================================================
	public void setupViews() {
		// Set the summary of each edit text to the current value
		// of its EditText field.
		if (mIPAddr != null) mIPAddr.refreshSummaryField();
		if (mPort != null) mPort.refreshSummaryField();
		if (mSocketTimeout != null) mSocketTimeout.refreshSummaryField();
		if (prefChannelJournal != null) prefChannelJournal.refreshSummaryField();
		if (mDeviceId != null) mDeviceId.refreshSummaryField();
		if (mOperatorId != null) mOperatorId.refreshSummaryField();
		if (mOperatorKey != null) mOperatorKey.refreshSummaryField();
		if (flatLineTime != null) flatLineTime.refreshSummaryField();
	}
	
	// ===========================================================
	// Methods
	// ===========================================================
	
	
	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

}

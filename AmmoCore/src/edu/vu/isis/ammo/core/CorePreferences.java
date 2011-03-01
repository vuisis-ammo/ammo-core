package edu.vu.isis.ammo.core;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IPrefKeys;

/**
 * View and change the core application preferences.
 * These are primarily concerned with the management of the 
 * communication channel.
 * 
 * @author phreed
 *
 */
public class CorePreferences extends PreferenceActivity {
	// ===========================================================
	// Constants
	// ===========================================================
    public static final String LAUNCH = "edu.vu.isis.ammo.core.CorePreferences.LAUNCH";

	// ===========================================================
	// Fields
	// ===========================================================
	private CustomEditTextPreference mIPAddr;
	private CustomEditTextPreference mPort;
	private CustomEditTextPreference mSocketTimeout;
	private CustomCheckBoxPreference prefChannelJournal;
	
	private CustomEditTextPreference mDeviceId;
	private CustomEditTextPreference mOperatorId;
	private CustomEditTextPreference mOperatorKey;
	
	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.layout.core_preferences);
		
		Resources res = this.getResources();
	
		mIPAddr = (CustomEditTextPreference) findPreference(INetPrefKeys.PREF_IP_ADDR);
		mIPAddr.setSummaryPrefix(res.getString(R.string.ipaddr_label));
		mIPAddr.setType(CustomEditTextPreference.Type.IP);
		
		mPort = (CustomEditTextPreference) findPreference(INetPrefKeys.PREF_IP_PORT);
		mPort.setSummaryPrefix(res.getString(R.string.port_label));
		mPort.setType(CustomEditTextPreference.Type.PORT);
		
		mSocketTimeout = (CustomEditTextPreference)findPreference(INetPrefKeys.PREF_SOCKET_TIMEOUT);
		mSocketTimeout.setSummaryPrefix(res.getString(R.string.socket_timeout_label));
		mSocketTimeout.setType(CustomEditTextPreference.Type.SOCKET_TIMEOUT);
		
		prefChannelJournal = (CustomCheckBoxPreference) findPreference(INetPrefKeys.PREF_IS_JOURNAL);
		prefChannelJournal.setSummaryPrefix(res.getString(R.string.channel_journal_label));
		prefChannelJournal.setType(CustomCheckBoxPreference.Type.JOURNAL);
		
		mDeviceId = (CustomEditTextPreference) findPreference(INetPrefKeys.PREF_DEVICE_ID);
		mDeviceId.setSummaryPrefix(res.getString(R.string.device_id_label));
		mDeviceId.setType(CustomEditTextPreference.Type.DEVICE_ID);
		
		mOperatorId = (CustomEditTextPreference) findPreference(IPrefKeys.PREF_OPERATOR_ID);
		mOperatorId.setSummaryPrefix(res.getString(R.string.operator_id_label));
		mOperatorId.setType(CustomEditTextPreference.Type.OPERATOR_ID);
		
		mOperatorKey = (CustomEditTextPreference) findPreference(INetPrefKeys.PREF_OPERATOR_KEY);
		mOperatorKey.setSummaryPrefix(res.getString(R.string.operator_key_label));
		mOperatorKey.setType(CustomEditTextPreference.Type.OPERATOR_KEY);
		
		this.setupViews();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		Intent i = new Intent(IPrefKeys.AMMO_PREF_UPDATE);
		this.sendBroadcast(i);
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
	}
	
	// ===========================================================
	// Methods
	// ===========================================================
	
	
	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

}

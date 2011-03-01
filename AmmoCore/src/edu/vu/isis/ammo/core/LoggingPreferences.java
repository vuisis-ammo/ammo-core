package edu.vu.isis.ammo.core;

import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * View and change the core application preferences.
 * These are primarily concerned with the management
 * of the logging framework.
 * 
 * @author phreed
 *
 */
public class LoggingPreferences extends PreferenceActivity {
	// ===========================================================
	// Constants
	// ===========================================================
    public static final String LAUNCH = "edu.vu.isis.ammo.core.LoggingPreferences.LAUNCH";

	public static final String PREF_LOG_LEVEL = "CORE_LOG_LEVEL";
	
	// ===========================================================
	// Fields
	// ===========================================================
	private CustomEditTextPreference level;
	
	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.layout.logging_preferences);
		
		Resources res = this.getResources();
	
		level = (CustomEditTextPreference) findPreference(PREF_LOG_LEVEL);
		level.setSummaryPrefix(res.getString(R.string.log_level_label));
		level.setType(CustomEditTextPreference.Type.LOG_LEVEL);
		
		// System.setProperty(prop, value);
		// export ANDROID_LOG_TAGS="ActivityManager:I MyApp:D *:S"
			
		this.setupViews();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		// this.startService(new Intent("edu.vu.isis.ammo.core.CorePreferenceService.LAUNCH"));
	}
	
	// ===========================================================
	// UI Management
	// ===========================================================
	public void setupViews() {
		// Set the summary of each edit text to the current value
		// of its EditText field.
		if (level != null) level.refreshSummaryField();
	}
	
	// ===========================================================
	// Methods
	// ===========================================================
	
	
	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

}

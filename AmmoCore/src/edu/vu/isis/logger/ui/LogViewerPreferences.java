package edu.vu.isis.logger.ui;

import edu.vu.isis.ammo.core.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class LogViewerPreferences extends PreferenceActivity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {	
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.log_viewer_preferences);
	}
	
}

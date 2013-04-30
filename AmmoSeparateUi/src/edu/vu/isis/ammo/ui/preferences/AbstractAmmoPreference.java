
package edu.vu.isis.ammo.ui.preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.Intent;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import edu.vu.isis.ammo.core.ui.util.PreferenceActivityEx;

public abstract class AbstractAmmoPreference extends PreferenceActivityEx {

    protected static final String TRUE_TITLE_SUFFIX = "is enabled.";
    protected static final String FALSE_TITLE_SUFFIX = "is disabled.";

    protected static final Logger logger = LoggerFactory.getLogger("ui.ammoPref");

    protected final OnPreferenceClickListener sendToPantherPrefsListener = new OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            startActivity(new Intent()
                    .setComponent(new ComponentName("transapps.settings",
                            "transapps.settings.SettingsActivity")));
            return true;
        }
    };

}

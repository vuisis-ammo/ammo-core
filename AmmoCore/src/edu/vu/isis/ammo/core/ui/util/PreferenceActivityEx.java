package edu.vu.isis.ammo.core.ui.util;

import android.content.Context;
import android.preference.PreferenceActivity;

public abstract class PreferenceActivityEx extends PreferenceActivity implements IActivityEx {
    public Context getAppContext(){
        return getApplicationContext();
    }
}

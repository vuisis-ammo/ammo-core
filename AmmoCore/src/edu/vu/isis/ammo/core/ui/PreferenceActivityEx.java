package edu.vu.isis.ammo.core.ui;

import edu.vu.isis.ammo.core.ApplicationEx;
import android.content.Context;
import android.preference.PreferenceActivity;

abstract class PreferenceActivityEx extends PreferenceActivity implements IActivityEx {
    public Context getAppContext(){
        return getApplicationContext();
    }

    public void onResume() {
        super.onResume();
        
        ((ApplicationEx)this.getApplication()).setCurrentActivity(this);
    }
}
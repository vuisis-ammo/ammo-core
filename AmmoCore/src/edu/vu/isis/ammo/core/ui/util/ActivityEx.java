package edu.vu.isis.ammo.core.ui.util;

import edu.vu.isis.ammo.core.ApplicationEx;
import android.app.Activity;
import android.content.Context;

public abstract class ActivityEx extends Activity implements IActivityEx {
    public Context getAppContext(){
        return getApplicationContext();
    }

    public void onResume() {
        super.onResume();
        
        ((ApplicationEx)this.getApplication()).setCurrentActivity(this);
    }
}
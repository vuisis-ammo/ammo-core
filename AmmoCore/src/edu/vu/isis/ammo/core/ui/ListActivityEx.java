package edu.vu.isis.ammo.core.ui;

import android.app.ListActivity;
import android.content.Context;
import edu.vu.isis.ammo.core.ApplicationEx;

abstract class ListActivityEx extends ListActivity implements IActivityEx {
    public Context getAppContext(){
        return getApplicationContext();
    }

    public void onResume() {
        super.onResume();
        
        ((ApplicationEx)this.getApplication()).setCurrentActivity(this);
    }
}
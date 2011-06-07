package edu.vu.isis.ammo.core.ui.util;

import android.app.ListActivity;
import android.content.Context;
import edu.vu.isis.ammo.core.ApplicationEx;

public abstract class ListActivityEx extends ListActivity implements IActivityEx {
    public Context getAppContext(){
        return getApplicationContext();
    }
}

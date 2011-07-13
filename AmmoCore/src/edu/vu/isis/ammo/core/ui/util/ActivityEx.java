package edu.vu.isis.ammo.core.ui.util;

import android.app.Activity;
import android.content.Context;

public abstract class ActivityEx extends Activity implements IActivityEx {
    public Context getAppContext() {
        return getApplicationContext();
    }
}
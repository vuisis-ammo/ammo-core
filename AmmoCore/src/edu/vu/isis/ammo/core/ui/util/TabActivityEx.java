package edu.vu.isis.ammo.core.ui.util;

import android.app.TabActivity;
import android.content.Context;

public class TabActivityEx extends TabActivity implements IActivityEx {
	   public Context getAppContext(){
	        return getApplicationContext();
	    }
}

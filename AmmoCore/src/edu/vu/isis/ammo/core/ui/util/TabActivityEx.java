package edu.vu.isis.ammo.core.ui.util;

import android.app.TabActivity;
import android.content.Context;
import edu.vu.isis.ammo.core.ApplicationEx;

public class TabActivityEx extends TabActivity implements IActivityEx {
	   public Context getAppContext(){
	        return getApplicationContext();
	    }

	    public void onResume() {
	        super.onResume();
	        
	        ((ApplicationEx)this.getApplication()).setCurrentActivity(this);
	    }
}

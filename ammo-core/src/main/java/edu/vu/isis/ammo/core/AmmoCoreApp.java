/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.core;
/**
 * This is the place for application global information.
 *
 * Using global variables should follow the following pattern.
 * example:
 *   File dir = AppEx.getInstance().getPublicDirectory("images");
 *
 *
 */
import java.io.File;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;

public class AmmoCoreApp  extends Application {
    // private static final Logger logger = LoggerFactory.getLogger("app");

    private static AmmoCoreApp singleton;

    public AmmoCoreApp() {
        super();
    }

    public static AmmoCoreApp getInstance() {
        return singleton;
    }

    @Override
    public final void onCreate() {
        super.onCreate();
        singleton = this;
        
   
        /**
         * keep the service running
         */
        final Intent intent = new Intent(this, AmmoService.class);
        final PendingIntent service = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(service);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000, service);
    }

    /**
     * This function returns a directory suitable for storing data external to the application.
     * This has been largely obviated by Environment.getExternalStoragePublicDirectory (String type)
     * But that isn't available until APIv8
     */
    public File getPublicDirectory (String type) {
        File base = new File(Environment.getExternalStorageDirectory(), AmmoCoreApp.class.toString());
        return new File(base, type);
    }
}

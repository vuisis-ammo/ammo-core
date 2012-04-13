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
 * @author phreed
 *
 */
import java.io.File;

import android.app.Application;
import android.content.Intent;
import android.os.Environment;
import edu.vu.isis.ammo.core.ethertracker.EthTrackSvc;

public class AmmoCoreApp  extends Application {
    // private static final Logger logger = LoggerFactory.getLogger("ammo.class.AmmoCoreApp");

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
        
        final Intent svc = new Intent();

        svc.setClass(this, AmmoService.class);
        this.startService(svc);
        // context.startService(AmmoService.LAUNCH);

        svc.setClass(this, EthTrackSvc.class);
        this.startService(svc);
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

/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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

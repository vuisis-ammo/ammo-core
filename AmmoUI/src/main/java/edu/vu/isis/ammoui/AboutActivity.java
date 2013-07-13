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
package edu.vu.isis.ammoui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammoui.R;

/**
 * Show details about the app, such as version number.
 *
 * @author hackd
 *
 */
public class AboutActivity extends Activity
{
    public static final Logger logger = LoggerFactory.getLogger("ui.about");


    /**
     * @Category Lifecycle
     */
    @Override
        public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.trace("::onCreate");
        this.setContentView(R.layout.about_activity);
        final TextView aboutVersion = (TextView) this.findViewById(R.id.about_version);
        try {
            final String versionName = this.getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            aboutVersion.setText(versionName);
        } catch (NameNotFoundException ex) {
            logger.warn("no version name", ex);
        }

    }
    
}

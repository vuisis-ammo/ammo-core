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
package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ui.util.ActivityEx;

/**
 * Show details about the app, such as version number.
 *
 * @author hackd
 *
 */
public class AboutActivity extends ActivityEx
{
    public static final Logger logger = LoggerFactory.getLogger("AboutActivity");


    /**
     * @Cateogry Lifecycle
     */
    @Override
        public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.trace("::onCreate");
        this.setContentView(R.layout.about_activity);
    }

    @Override
        public void onStart() {
        super.onStart();
        logger.trace("::onStart");
	
	this.displayVersion();
    }

    @Override
        public void onStop() {
        super.onStop();
    }

    @Override
        public void onDestroy() {
        super.onDestroy();
        logger.trace("::onDestroy");
    }
    
    private void displayVersion() {
        logger.trace("::displayVersion");
	//String message = "version = " + getResources().getString(R.string.ammo_version);
	//Toast.makeText(AboutActivity.this, message, Toast.LENGTH_LONG).show();
    }

}

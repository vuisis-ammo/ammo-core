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

package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.widget.TextView;
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
    public static final Logger logger = LoggerFactory.getLogger("ui.about");


    /**
     * @Cateogry Lifecycle
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

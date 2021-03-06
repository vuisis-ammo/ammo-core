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


package edu.vu.isis.logger.ui;

import java.net.MalformedURLException;
import java.net.URL;

import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import ch.qos.logback.classic.LoggerContext;
import edu.vu.isis.ammo.core.R;

/**
 * This is a special Activity for testing purposes. The purpose of this Activity
 * is to cause Logback to reconfigure itself. It should only be possible to get
 * to this Activity from issuing an intent through the Android shell. The Intent
 * used to start this Activity should include a String extra with the key
 * "fileurl" and a value that is a String formatted to be a URL pointing to the
 * logback configuration file (ex. "file:/sdcard/logback/logback-test.xml") One
 * possible command to access this Activity would be
 * "adb shell am start --es fileurl file:/sdcard/logback/logback-test.xml -n edu.vu.isis.ammo.core/edu.vu.isis.logger.ui.LogbackReconfigureActivity"
 * Note that the logback config file does not have to have a special or
 * conventional name. It's valid to call the file whatever you like as long as
 * your URL string points to it properly.
 * 
 * @author nick
 * 
 */
public class LogbackReconfigureActivity extends Activity {

	private URL mUrl;
	private LoggerContext lc;

	private static final String LOG_TAG = "LogbackReconfigureActivity";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logback_reconfigure);
		String fileurl = getIntent().getStringExtra("fileurl");

		if (fileurl == null) {
			Log.wtf(LOG_TAG,
					"The Intent that was given had no String extra for the file url!");
			return;
		}

		try {
			mUrl = new URL(fileurl);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			Log.wtf(LOG_TAG, "Malformed url! String extra was: " + fileurl);
		}

		lc = (LoggerContext) LoggerFactory.getILoggerFactory();

	}

	@Override
	public void onStart() {
		super.onStart();
		force(null);
		finish();
	}

	public void force(View v) {
		Log.w("LogbackReconfigure", "Reconfiguring LoggerContext " + lc.toString() + " with file at url " + mUrl.toString());
		LogbackReconfigureForcer.forceReconfigure(mUrl, lc);
	}

}

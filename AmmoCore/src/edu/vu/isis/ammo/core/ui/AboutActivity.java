package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.widget.Toast;

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

    public static final Logger logger = LoggerFactory.getLogger(AboutActivity.class);

    private static final String TAG = "AmmoCore/about";


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
	String message = "version = " + getResources().getString(R.string.ammo_version);
	//Toast.makeText(AboutActivity.this, message, Toast.LENGTH_LONG).show();
    }

}

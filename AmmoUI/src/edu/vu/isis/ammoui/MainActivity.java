
package edu.vu.isis.ammoui;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;

import edu.vu.isis.ammoui.R;

public class MainActivity extends Activity {

    private static final Uri CHANNEL_CP_URI = Uri.parse("content://edu.vu.isis.ammo.core.provider.channel/Channel");
    private static final Logger logger = LoggerFactory.getLogger("ui.MainActivity");
    
    private ScheduledExecutorService mSex;
    
    private Runnable mQueryRunnable = new Runnable() {
        @Override
        public void run() {
            logger.trace("Running query...");
            Cursor c = getContentResolver().query(CHANNEL_CP_URI, null, null, null, null);
            if (c != null) {
                logger.trace("Received cursor with contents:\n\n{}", DatabaseUtils.dumpCursorToString(c));
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSex = Executors.newSingleThreadScheduledExecutor();
        mSex.scheduleAtFixedRate(mQueryRunnable, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

}

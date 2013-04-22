package edu.vu.isis.ammo.test.ics;

import edu.vu.isis.ammo.test.ics.provider.AmmotestdriverSchema.BaselineTableSchema;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class TestService extends IntentService {

	private static final String TAG = "TestService";
	
	// Intents this service can handle
	private static final String START_TEST = "edu.vu.isis.ammo.test.ics.TestService.START_TEST";
	private static final String STOP_RUNNING_TEST = "edu.vu.isis.ammo.test.ics.TestService.STOP_RUNNING_TEST";
	private static final String TEST_STATUS = "edu.vu.isis.ammo.test.ics.TestService.TEST_STATUS";
	private static final String QUICK_TEST = "edu.vu.isis.ammo.test.ics.TestService.QUICK_TEST";
	
	// Intent for future use
	private static final String ADD_TEST = "edu.vu.isis.ammo.test.ics.TestService.ADD_TEST";

	// Intent extras (describe test characteristics)
	private static final String INT_EXTRA_TEST_NAME = "name";
	private static final String INT_EXTRA_TEST_DURATION = "duration";  //units: minute
	private static final String INT_EXTRA_MSG_DELAY = "delay";  //units: second
	
	public TestService() {
		super("TestService");
	}
	
	public TestService(String name) {
		super(name);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// TODO Auto-generated method stub

		// Print some info about this intent, including extras
		/*
		Log.d(TAG, "received intent,  tostr = " + intent.toString());
		Log.d(TAG, "            action = " + intent.getAction());
		Log.d(TAG, "            package = " + intent.getPackage());
		Log.d(TAG, "            type = " + intent.getType());
		Bundle intentExtras = intent.getExtras();
        if (intentExtras != null) {
            Log.d(TAG, "     got intent extras");
            if (!intentExtras.isEmpty()) {
                for (String s : intentExtras.keySet()) {
                    Log.d(TAG, "         intent extra = '" + s + "'");
                }
            }
        }
		*/
		
        // Parse the intent and take action: start/stop test, etc.
        final String action = intent.getAction();
        if (action.equals(START_TEST)) {
        	handleStartCmd(intent);
        }
        if (action.equals(QUICK_TEST)) {
        	handleQuickTestCmd(intent);
        }
        if (action.equals(STOP_RUNNING_TEST)) {
        	handleStopCmd(intent);
        }
        if (action.equals(TEST_STATUS)) {
        	handleTestStatusCmd(intent);
        }
	}
	
	private void handleQuickTestCmd(Intent intent) {
		Log.d(TAG, "quick test");
		
		// Populate message content values
        ContentValues cv = new ContentValues();
        cv.put(BaselineTableSchema.TEST_ITEM, 1);
        cv.put(BaselineTableSchema.TEST_NAME, "Quick message send");
        cv.put(BaselineTableSchema.FOREIGN_KEY_REF, 1);
        cv.put(BaselineTableSchema.INTEGER, TestUtils.TEST_INTEGER ); 
        cv.put(BaselineTableSchema.LONG_INTEGER, TestUtils.TEST_LONG_INTEGER);
        cv.put(BaselineTableSchema.SHORT_INTEGER, TestUtils.TEST_SHORT_INTEGER);
        cv.put(BaselineTableSchema.BOOLEAN, TestUtils.TEST_BOOLEAN);
        cv.put(BaselineTableSchema.FLOATING_POINT_NUMBER, TestUtils.TEST_FLOAT);
        cv.put(BaselineTableSchema.GLOBALLY_UNIQUE_ID, TestUtils.randomGuidAsString()); //TestUtils.TEST_GUID_STR);
        cv.put(BaselineTableSchema.TEXT_VALUE, TestUtils.TEST_FIXED_STRING);  //TestUtils.randomText(20));
        cv.put(BaselineTableSchema.TIMESTAMP_VALUE, System.currentTimeMillis());
        cv.put(BaselineTableSchema.EXCLUSIVE_ENUMERATION, BaselineTableSchema.EXCLUSIVE_ENUMERATION_HIGH);
        cv.put(BaselineTableSchema.INCLUSIVE_ENUMERATION, BaselineTableSchema.INCLUSIVE_ENUMERATION_APPLE);
        cv.put(BaselineTableSchema.CONTENT_CHECKSUM, new Long("9223372036854775806").longValue()); 

        // TMP: file
        //cv.put(BaselineTableSchema.A_FILE, "/sdcard/wolverine.png");
        
	}
	
	private void handleTestStatusCmd(Intent intent) {
		Log.d(TAG, "test status");
	}
	
	private void handleStopCmd(Intent intent) {
		// stop a running test (by name?)
		
		Log.d(TAG, "stopping test");
	}
	
	private void handleStartCmd(Intent intent) {
		
		IndividualTest test = new IndividualTest(this);

		if (intent.hasExtra(INT_EXTRA_TEST_NAME)) {
			String name = intent.getStringExtra(INT_EXTRA_TEST_NAME);
			if (name.length() > 0) {
				test.setTestName(name);				
			}
		}
		if (intent.hasExtra(INT_EXTRA_TEST_DURATION)) {
			long duration = Long.valueOf(intent.getStringExtra(INT_EXTRA_TEST_DURATION) ).longValue();
			if (duration > 0) { 
				test.setTestDurationMinutes(duration);
			}
		}
		if (intent.hasExtra(INT_EXTRA_MSG_DELAY)) {
			long delay = Long.valueOf(intent.getStringExtra(INT_EXTRA_MSG_DELAY) ).longValue();
			if (delay > 1) { 
				test.setMessageDelayMillis(delay);
			}
		}
		
		
        if (test.set()) {
        	Thread t = new Thread(test);
        	t.start();
        	//Intent intent = new Intent(this, TestProgressActivity.class);
            //startActivity(intent);
        } else {
        	Log.e(TAG, "problem starting test");
        	Toast.makeText(this, "problem starting test", Toast.LENGTH_SHORT).show();
        	return;
        }
	}

}

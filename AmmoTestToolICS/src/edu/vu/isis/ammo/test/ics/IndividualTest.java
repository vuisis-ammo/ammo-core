package edu.vu.isis.ammo.test.ics;

import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.test.ics.provider.AmmotestdriverProviderBase;
import edu.vu.isis.ammo.test.ics.provider.AmmotestdriverProvider;
import edu.vu.isis.ammo.test.ics.provider.AmmotestdriverSchema.BaselineTableSchema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.Double;
import java.lang.Float;
import java.lang.Boolean;
import java.lang.Integer;
import java.lang.Short;
import java.lang.Long;
import java.lang.ClassCastException;

import java.util.zip.CRC32;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class IndividualTest implements Runnable {

	private Context mContext;
	public AmmoRequest.Builder ab;
    private static final String TAG = "AmmoTestInstance";
    private static final long secondsPerMinute = 60;
    private static final long millisPerSecond = 1000;
    private static final int mRandomStringMaxSize = 30;

    // Test name
    private String mTestName = "";
    private boolean mNameIsSet = false;
	
	// Contract name
    private String mTestContract;
	
	// User-specified test duration (in minutes)
	private long mTestDurationMinutes = 1;
	private boolean mDurationIsSet = false;
	
	// Delay between successive messages (msec)
	private long mMessageDelayMillis = 1000;
	private boolean mDelayIsSet = false;
	
	// Send randomize message content? (yes if true, constant otherwise)
	private boolean mRandomMsgContent;
	
	private boolean mIncludeText;
	private boolean mIncludeInt;
	private boolean mIncludeShort;
	private boolean mIncludeLong;
	private boolean mIncludeFloat;
	private boolean mIncludeBoolean;
	private boolean mIncludeTimestamp;
	private boolean mIncludeUUID;
	private boolean mIncludeEnumExc;
	private boolean mIncludeEnumInc;
	private boolean mIncludeBlob;
	private boolean mIncludeFile;
	
	
	
	private long mTargetEndTime = 0;
	private long mTestDurationSeconds = 1;
	
	// Number of messages in test
	private int mTestMessageCount;
	
	
	public String getTestName() {
		return mTestName;
	}

	public void setTestName(String mTestName) {
		this.mTestName = mTestName;
		mNameIsSet = true;
	}

	public long getTestDurationMinutes() {
		return mTestDurationMinutes;
	}

	public void setTestDurationMinutes(long mTestDurationMinutes) {
		this.mTestDurationMinutes = mTestDurationMinutes;
		mDurationIsSet = true;
	}

	public long getMessageDelayMillis() {
		return mMessageDelayMillis;
	}

	public void setMessageDelayMillis(long mMessageDelayMillis) {
		this.mMessageDelayMillis = mMessageDelayMillis;
		mDelayIsSet = true;
	}

	public IndividualTest(Context context) {
		mContext = context;
		
		// Default values
		mMessageDelayMillis = 1000;
		mRandomMsgContent = true;
		mTestName = "Unnamed Ammo test";
		mTestDurationMinutes = 1;
		
		mIncludeText = true;
		mIncludeInt = true;
		mIncludeShort = true;
		mIncludeLong = true;
		mIncludeFloat = true;
		mIncludeBoolean = true;
		mIncludeTimestamp = true;
		mIncludeUUID = true;
		mIncludeEnumExc = true;
		mIncludeEnumInc = true;
		mIncludeBlob = false;
		mIncludeFile = false;
	}
	
	public boolean set() {
		
		// initialize Ammo builder object
		try {
		    this.ab = AmmoRequest.newBuilder(mContext);
		} catch (Throwable e) {
		    Log.e(TAG, "no ammo today: " + e.toString());
		    Toast.makeText(mContext, "Ammo initialization failed", Toast.LENGTH_SHORT).show();
		    return false;
		}
		
		// Read test parameters from stored pref values (set by user)
		//SharedPreferences prefs = mContext.getSharedPreferences("test_preferences", 0);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		if (prefs == null) {
		    Log.e(TAG, "Error reading preferences: null preferences object");
		    return false;
		}
		
		try {
			mTestContract = prefs.getString("contract_list", "1");

			if (!mNameIsSet) {
				mTestName = prefs.getString("test_name_memo", "Unnamed test");
				mNameIsSet = true;
			}			
			if (!mDurationIsSet) {
				mTestDurationMinutes = Long.valueOf(prefs.getString("test_duration", "1") ).longValue();

				mDurationIsSet = true;
			}
			if (!mDelayIsSet) {
				mMessageDelayMillis = Long.valueOf(prefs.getString("msg_rate", "1000")).longValue();
				mDelayIsSet = true;
			}
			
			mRandomMsgContent = prefs.getBoolean("random_msg_content", true);
		
			mIncludeText = prefs.getBoolean("include_text", true);
			mIncludeInt = prefs.getBoolean("include_int", true);
			mIncludeShort = prefs.getBoolean("include_short", true);
			mIncludeLong = prefs.getBoolean("include_long", true);
			mIncludeFloat = prefs.getBoolean("include_float", true);
			mIncludeBoolean = prefs.getBoolean("include_bool", true);
			mIncludeTimestamp = prefs.getBoolean("include_timestamp", true);
			mIncludeUUID = prefs.getBoolean("include_uuid", true);
			mIncludeEnumExc = prefs.getBoolean("include_enum_exc", true);
			mIncludeEnumInc = prefs.getBoolean("include_enum_inc", true);
			mIncludeBlob = prefs.getBoolean("include_blob", false);
			mIncludeFile = prefs.getBoolean("include_file", false);
		
			Log.d(TAG, "{ name='" + mTestName + "'  idx=" + "  mRandomMsgContent=  "
				+ "mIncludeText=   mIncludeInt=  mIncludeShort=  mIncludeLong=  mIncludeFloat=  mIncludeBoolean=  mIncludeTimestamp=" 
				+ "mIncludeUUID=  mIncludeEnumExc=  mIncludeEnumInc=  mIncludeBlob=  mIncludeFile=  }");
		
		} catch (ClassCastException e) {
			Log.e(TAG, "Error reading preferences: " + e.toString());
			e.printStackTrace();
		    return false;
		} catch (NumberFormatException e) {
			Log.e(TAG, "Error reading preferences: " + e.toString());
			e.printStackTrace();
			return false;
		}
		
		// Derived quantities
		mTestDurationSeconds = mTestDurationMinutes * secondsPerMinute;
		
		Log.d(TAG, "test duration min = " + mTestDurationMinutes);
		Log.d(TAG, "test duration sec = " + mTestDurationSeconds);
		Log.d(TAG, "msg delay msec = " + mMessageDelayMillis);
		
		
		return true;
	}
	
	public void finish() {
		if (this.ab != null) {
		    this.ab.releaseInstance();
		}
	}
	
	@Override
	public void run() {
		
		// compute projected test end time (Now + N seconds)
		mTargetEndTime = System.currentTimeMillis() + (mTestDurationSeconds * millisPerSecond);
				
		// make sure it's been set correctly (at least make sure it's "now" or later)
		if ( (mTargetEndTime <= 0) || (mTargetEndTime < System.currentTimeMillis() ) ) {
			Log.e(TAG, "invalid test end time: " + String.valueOf(mTargetEndTime));
			return;
		}
		
		// Main test loop
		int i=0;
		while (System.currentTimeMillis() < mTargetEndTime ) {
			i++;
			// Populate message content values
			ContentValues cv = mRandomMsgContent ? makeRandomValuedMessage() : makeStaticValuedMessage();
	        cv.put(BaselineTableSchema.TEST_NAME, mTestName);
			cv.put(BaselineTableSchema.TEST_ITEM, i);
			cv.put(BaselineTableSchema.FOREIGN_KEY_REF, BaselineSchemaMessageUtils.countMessageBytes(cv));
			Long cksum = BaselineSchemaMessageUtils.computeChecksum(cv);
			Log.d(TAG, "checksum: " + String.valueOf(cksum));
			if (cksum > 1L) {
				cv.put(BaselineTableSchema.CONTENT_CHECKSUM, cksum);
			}
	        
			// Insert values to provider and post to Ammo
			final Uri thisUri = mContext.getContentResolver().insert(BaselineTableSchema.CONTENT_URI, cv);
			try {
				this.ab.provider(thisUri).topicFromProvider().post();
				Log.d(TAG, "posted to Ammo with Uri: " + thisUri.toString());
			} catch (RemoteException ex) {
				Log.e(TAG, "post incident failed: " + ex.toString());
			}
			
			mTestMessageCount = i;
			// Wait for the prescribed time before sending another
			try{
				Thread.currentThread().sleep(mMessageDelayMillis);                        
			} catch(InterruptedException ie){
				//If this thread was interrupted by another thread                                
			}

		}

	    // cleanup and return when done
	    finish();
	    return;
	}

	public ContentValues makeRandomValuedMessage() {
		ContentValues cv = new ContentValues();
		
		//if (true) {
		//	cv.put(BaselineTableSchema.FOREIGN_KEY_REF, TestUtils.randomInt(100000));
		//}
		if (mIncludeInt) {
			cv.put(BaselineTableSchema.INTEGER, TestUtils.randomInt());
		}
		if (mIncludeLong) {
			cv.put(BaselineTableSchema.LONG_INTEGER, TestUtils.randomLong());
		}
		if (mIncludeShort) {
			cv.put(BaselineTableSchema.SHORT_INTEGER, TestUtils.randomShort());
		}
		if (mIncludeBoolean) {
			cv.put(BaselineTableSchema.BOOLEAN, TestUtils.randomBoolean());
		}
		if (mIncludeFloat) {
			cv.put(BaselineTableSchema.FLOATING_POINT_NUMBER, TestUtils.randomFloat());
		}
		if (mIncludeUUID) {
			cv.put(BaselineTableSchema.GLOBALLY_UNIQUE_ID, TestUtils.randomGuidAsString()); 
		}
		if (mIncludeText) {
			//cv.put(BaselineTableSchema.TEXT_VALUE, TestUtils.randomText(TestUtils.randomInt(mRandomStringMaxSize + 1)) );
			cv.put(BaselineTableSchema.TEXT_VALUE, TestUtils.randomText(mRandomStringMaxSize) );
		}
		
		if (mIncludeTimestamp) {
			cv.put(BaselineTableSchema.TIMESTAMP_VALUE, System.currentTimeMillis());
		}
		
		if (mIncludeEnumExc) {
			cv.put(BaselineTableSchema.EXCLUSIVE_ENUMERATION, BaselineTableSchema.EXCLUSIVE_ENUMERATION_HIGH);
		}
		if (mIncludeEnumInc) {
			cv.put(BaselineTableSchema.INCLUSIVE_ENUMERATION, BaselineTableSchema.INCLUSIVE_ENUMERATION_APPLE);
		}
		
		if (mIncludeFile) {
			cv.put(BaselineTableSchema.A_FILE, "/sdcard/wolverine.png");
			cv.put(BaselineTableSchema.A_FILE_TYPE, "image/png");
			
			//cv.put(BaselineTableSchema.A_FILE, "/sdcard/galaxy.jpg");
			//cv.put(BaselineTableSchema.A_FILE_TYPE, "image/jpg");
		}
			
		return cv;
	}
	
	public ContentValues makeStaticValuedMessage() {
		ContentValues cv = new ContentValues();
		
		//if (true) {
		//	cv.put(BaselineTableSchema.FOREIGN_KEY_REF, 1);
		//}
		if (mIncludeInt) {
			cv.put(BaselineTableSchema.INTEGER, TestUtils.TEST_INTEGER);
		}
		if (mIncludeLong) {
			cv.put(BaselineTableSchema.LONG_INTEGER, TestUtils.TEST_LONG_INTEGER);
		}
		if (mIncludeShort) {
			cv.put(BaselineTableSchema.SHORT_INTEGER, TestUtils.TEST_SHORT_INTEGER);
		}
		if (mIncludeBoolean) {
			cv.put(BaselineTableSchema.BOOLEAN, TestUtils.TEST_BOOLEAN);
		}
		if (mIncludeFloat) {
			cv.put(BaselineTableSchema.FLOATING_POINT_NUMBER, TestUtils.TEST_FLOAT);
		}
		if (mIncludeUUID) {
			cv.put(BaselineTableSchema.GLOBALLY_UNIQUE_ID, TestUtils.randomGuidAsString()); 
		}
		if (mIncludeText) {
			cv.put(BaselineTableSchema.TEXT_VALUE, TestUtils.TEST_FIXED_STRING );
		}
		
		if (mIncludeTimestamp) {
			cv.put(BaselineTableSchema.TIMESTAMP_VALUE, System.currentTimeMillis());
		}
		
		if (mIncludeEnumExc) {
			cv.put(BaselineTableSchema.EXCLUSIVE_ENUMERATION, BaselineTableSchema.EXCLUSIVE_ENUMERATION_HIGH);
		}
		if (mIncludeEnumInc) {
			cv.put(BaselineTableSchema.INCLUSIVE_ENUMERATION, BaselineTableSchema.INCLUSIVE_ENUMERATION_APPLE);
		}
		
		return cv;
	}
	
	/*
	public static Long computeChecksum(ContentValues cv) {
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
				
		appendValueBytes(cv, BaselineTableSchema.FOREIGN_KEY_REF, outputStream);
		//if (mIncludeInt) {
			appendValueBytes(cv, BaselineTableSchema.INTEGER, outputStream);
		//}
		//if (mIncludeShort) {
			appendValueBytes(cv, BaselineTableSchema.SHORT_INTEGER, outputStream);
		//}
		//if (mIncludeLong) {
			appendValueBytes(cv, BaselineTableSchema.LONG_INTEGER, outputStream);
		//}
		//if (mIncludeBoolean) {
			appendValueBytes(cv, BaselineTableSchema.BOOLEAN, outputStream);
		//}
		//if (mIncludeTimestamp) {
			appendValueBytes(cv, BaselineTableSchema.TIMESTAMP_VALUE, outputStream);
		//}
		//if (mIncludeFloat) {
			appendValueBytes(cv, BaselineTableSchema.FLOATING_POINT_NUMBER, outputStream);
		//}
		//if (mIncludeUUID) {
			appendValueBytes(cv, BaselineTableSchema.GLOBALLY_UNIQUE_ID, outputStream);
		//}
		//if (mIncludeText) {
			appendValueBytes(cv, BaselineTableSchema.TEXT_VALUE, outputStream);
		//}
		//if (mIncludeEnumInc) {
			appendValueBytes(cv, BaselineTableSchema.EXCLUSIVE_ENUMERATION, outputStream);
		//}
		//if (mIncludeEnumInc) {
			appendValueBytes(cv, BaselineTableSchema.INCLUSIVE_ENUMERATION, outputStream);
		//}
		//if (mIncludeBlob) {
			appendValueBytes(cv, BaselineTableSchema.A_BLOB, outputStream);
		//}
		//if (mIncludeFile) {
			appendValueBytes(cv, BaselineTableSchema.A_FILE, outputStream);
		//}
		
		byte[] buf = outputStream.toByteArray();
		CRC32 cksum = new CRC32();
		cksum.update(buf);
		try {
			outputStream.close();
		} catch (IOException e) {
			return -1L;
		}
		//cv.put(BaselineTableSchema.CONTENT_CHECKSUM, cksum.getValue());
		return cksum.getValue();
	}
	
	private static boolean appendValueBytes(ContentValues cv, String key, ByteArrayOutputStream outputStream) {
		if (cv.containsKey(key)) {
			Log.d(TAG, "appendValueBytes: " + key);
			byte[] b = cv.getAsString(key).getBytes();
		
			try {
				outputStream.write(b, 0, b.length);
			} catch (NullPointerException e) {
				Log.e(TAG, "NullPointerException");
				//e.printStackTrace();
				return false;
			} catch (IndexOutOfBoundsException e) {
				Log.e(TAG, "IndexOutOfBoundsException");
				//e.printStackTrace();
				return false;
			}
			
		}
		Log.d(TAG, "   " + String.valueOf(outputStream.size()) + " bytes");
		return true;
	}
	*/
}

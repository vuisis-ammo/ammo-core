package edu.vu.isis.ammo.test.ics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

import android.content.ContentValues;
import android.util.Log;
import edu.vu.isis.ammo.test.ics.provider.AmmotestdriverSchema.BaselineTableSchema;

public class MessageUtils {

    private static final String TAG = "MessageUtils";

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

		return cksum.getValue();
	}
	
	private static boolean appendValueBytes(ContentValues cv, String key, ByteArrayOutputStream outputStream) {
		if (cv.containsKey(key)) {
			Log.d(TAG, "appendValueBytes: " + key);
			
			// Wait, this isn't right! Don't get the thing as a string and count those bytes... dummy.
			// Add the correct number of bytes for the datatype:
			// Long.SIZE (bits)
			// Integer.SIZE (bits)
			// Float.SIZE (bits)
			// length of the string
			// ...and so forth.
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
		Log.d(TAG, "   " + key + "  " + String.valueOf(outputStream.size()) + " bytes");
		return true;
	}
	
	public static int countMessageBytes(ContentValues cv) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
		
		for (String key : cv.keySet()) {
			Log.d(TAG, "countMessageBytes: " + key);
			byte[] b = cv.getAsString(key).getBytes();
		
			try {
				outputStream.write(b, 0, b.length);
			} catch (NullPointerException e) {
				Log.e(TAG, "NullPointerException");
				//e.printStackTrace();
				return -1;
			} catch (IndexOutOfBoundsException e) {
				Log.e(TAG, "IndexOutOfBoundsException");
				//e.printStackTrace();
				return -1;
			}
			Log.d(TAG, "   " + key + "  " + String.valueOf(outputStream.size()) + " bytes");
			
		}
		return outputStream.size();
	}
	*/
}

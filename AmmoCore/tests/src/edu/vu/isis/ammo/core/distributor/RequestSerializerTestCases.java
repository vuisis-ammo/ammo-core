package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Random;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.AmmoTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.QuickTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.StartTableSchema;

public class RequestSerializerTestCases {

	// 3 test sets
	static ArrayList<ContentValues> CVs;
	static ArrayList<String> mimeTypes;
	static ArrayList<String> description;

	public static ArrayList<ContentValues> getCVsForRoundTripTests() {
		return CVs;
	}

	public static ArrayList<String> getMimeTypesForRoundTripTests() {
		return mimeTypes;
	}

	public static ArrayList<String> getDescriptionsForRoundTripTests() {
		return description;
	}

	// putting the tests into the 3 test sets
	static {
		CVs = new ArrayList<ContentValues>();
		mimeTypes = new ArrayList<String>();
		description = new ArrayList<String>();

		/*
		 * test 3 mimeTypes with default values
		 */
		{
			CVs.add(createDefaultAmmoCV());
			mimeTypes.add("edu.vu.isis.ammo");
			description.add("edu.vu.isis.ammo defualt -defualt- test");
		}
		{
			CVs.add(createDefaultQuickCV());
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.ammo quick -defualt- test");
		}
		{
			CVs.add(createDefaultStartCV());
			mimeTypes.add("edu.vu.isis.start");
			description.add("edu.vu.isis.start defualt -defualt- test");
		}

		/*
		 * test custom 'ammo' CVs
		 */

		{
			ContentValues cv1 = createDefaultAmmoCV();
			cv1.put(AmmoTableSchema.A_FOREIGN_KEY_REF, 1000);
			CVs.add(cv1);
			mimeTypes.add("edu.vu.isis.ammo");
			description.add("edu.vu.isis.ammo test a FK of 1000");
		}
		{
			ContentValues cv1 = createDefaultAmmoCV();
			cv1.put(AmmoTableSchema.A_FOREIGN_KEY_REF, 0);
			CVs.add(cv1);
			mimeTypes.add("edu.vu.isis.ammo");
			description.add("edu.vu.isis.ammo test a FK of 0");
		}
		{
			ContentValues cv1 = createDefaultAmmoCV();
			cv1.put(AmmoTableSchema.A_FOREIGN_KEY_REF, 1);
			CVs.add(cv1);
			mimeTypes.add("edu.vu.isis.ammo");
			description.add("edu.vu.isis.ammo test a FK of 1");
		}
		{
			ContentValues cv = createDefaultAmmoCV();
			cv.remove(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION);
			cv.put(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);

			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.ammo");
			description.add("edu.vu.isis.ammo test high exclusive enum");
		}
		{
			ContentValues cv = createDefaultAmmoCV();
			cv.remove(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION);
			cv.put(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_LOW);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.ammo");
			description.add("edu.vu.isis.ammo test low exclusive enum");
		}
		{
			ContentValues cv3 = createDefaultAmmoCV();
			cv3.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_ORANGE);
			CVs.add(cv3);
			mimeTypes.add("edu.vu.isis.ammo");
			description.add("edu.vu.isis.ammo test add orange inclusive enum");
		}
		{
			ContentValues cv3 = createDefaultAmmoCV();
			cv3.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_PEAR);
			CVs.add(cv3);
			mimeTypes.add("edu.vu.isis.ammo");
			description.add("edu.vu.isis.ammo test add pear inclusive enum");
		}
		{
			ContentValues cv3 = createDefaultAmmoCV();
			cv3.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_ORANGE);
			CVs.add(cv3);
			mimeTypes.add("edu.vu.isis.ammo");
			description.add("edu.vu.isis.ammo test add orange inclusive enum");
		}
		{
			ContentValues cv3 = createDefaultAmmoCV();
			// TODO: not sure if this will work, don't think it will,need to
			// test later how to include multiple 'inclusive enumeration'
			cv3.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);
			cv3.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);
			cv3.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);
			CVs.add(cv3);
			mimeTypes.add("edu.vu.isis.ammo");
			description
					.add("edu.vu.isis.ammo test add 3x apple inclusive enum");
		}
		{
			ContentValues cv3 = createDefaultAmmoCV();
			// TODO: not sure if this will work, don't think it will,need to
			// test later how to include multiple 'inclusive enumeration'
			cv3.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_ORANGE);
			cv3.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
					AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_PEAR);
			CVs.add(cv3);
			mimeTypes.add("edu.vu.isis.ammo");
			description
					.add("edu.vu.isis.ammo test add orange and pear inclusive enum");
		}

		/*
		 * test custom 'quick' CVs
		 */
		// boolean test
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_BOOLEAN);
			cv.put(QuickTableSchema.A_BOOLEAN, "true");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_BOOLEAN = true");
		}
		// short int tests
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_SHORT_INTEGER);
			cv.put(QuickTableSchema.A_SHORT_INTEGER, -1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_SHORT_INTEGER = -1");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_SHORT_INTEGER);
			cv.put(QuickTableSchema.A_SHORT_INTEGER, -100);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_SHORT_INTEGER = -100");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_SHORT_INTEGER);
			cv.put(QuickTableSchema.A_SHORT_INTEGER, 1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_SHORT_INTEGER = 1");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_SHORT_INTEGER);
			cv.put(QuickTableSchema.A_SHORT_INTEGER, 1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_SHORT_INTEGER = 1");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_SHORT_INTEGER);
			cv.put(QuickTableSchema.A_SHORT_INTEGER, 100);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_SHORT_INTEGER = 100");
		}
		// long int tests
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_LONG_INTEGER);
			cv.put(QuickTableSchema.A_LONG_INTEGER, -1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_LONG_INTEGER = -1");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_LONG_INTEGER);
			cv.put(QuickTableSchema.A_LONG_INTEGER, 1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_LONG_INTEGER = 1");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_LONG_INTEGER);
			cv.put(QuickTableSchema.A_LONG_INTEGER, -1000000);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_LONG_INTEGER = -1000000");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.A_LONG_INTEGER);
			cv.put(QuickTableSchema.A_LONG_INTEGER, 1000000);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick A_LONG_INTEGER = 1000000");
		}
		// int tests
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.AN_INTEGER);
			cv.put(QuickTableSchema.AN_INTEGER, 10000);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick AN_INTEGER = 10000");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.AN_INTEGER);
			cv.put(QuickTableSchema.AN_INTEGER, -10000);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick AN_INTEGER = -10000");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.AN_INTEGER);
			cv.put(QuickTableSchema.AN_INTEGER, 1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick AN_INTEGER = 1");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.AN_INTEGER);
			cv.put(QuickTableSchema.AN_INTEGER, -1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick AN_INTEGER = -1");
		}
		{
			ContentValues cv = createDefaultQuickCV();
			cv.remove(QuickTableSchema.AN_INTEGER);
			cv.put(QuickTableSchema.AN_INTEGER, -10000);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.quick");
			description.add("edu.vu.isis.quick AN_INTEGER = -10000");
		}

		/*
		 * test custom 'start' CVs
		 */
		// test reals
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_REAL);
			cv.put(StartTableSchema.A_REAL, -10000);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description.add("edu.vu.isis.start A_REAL = -10000");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_REAL);
			cv.put(StartTableSchema.A_REAL, 10000);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description.add("edu.vu.isis.start A_REAL = 10000");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_REAL);
			cv.put(StartTableSchema.A_REAL, -1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description.add("edu.vu.isis.start A_REAL = -1");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_REAL);
			cv.put(StartTableSchema.A_REAL, 1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description.add("edu.vu.isis.start A_REAL = 1");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_REAL);
			cv.put(StartTableSchema.A_REAL, -1.1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description.add("edu.vu.isis.start A_REAL = -1.1");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_REAL);
			cv.put(StartTableSchema.A_REAL, 1.1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description.add("edu.vu.isis.start A_REAL = 1.1");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_REAL);
			cv.put(StartTableSchema.A_REAL, -10000.1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description.add("edu.vu.isis.start A_REAL = -10000.1");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_REAL);
			cv.put(StartTableSchema.A_REAL, 10000.1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description.add("edu.vu.isis.start A_REAL = 10000.1");
		}
		// test GUID
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER);
			cv.put(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER, 10000);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start A_GLOBALLY_UNIQUE_IDENTIFIER = 10000");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER);
			cv.put(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER, -10000);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start A_GLOBALLY_UNIQUE_IDENTIFIER = -10000");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER);
			cv.put(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER, 1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start A_GLOBALLY_UNIQUE_IDENTIFIER = 1");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER);
			cv.put(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER, -1);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start A_GLOBALLY_UNIQUE_IDENTIFIER = -1");
		}
		// test text
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "singleword");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = singleword");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "singleWord");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = singleWord");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "two words");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = two words");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words.");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words!");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words!");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words@");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words@");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words#");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words#");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words$");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words$");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words%");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words%");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words^");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words^");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words&");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words&");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words*");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words*");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words(");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words(");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words)");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words)");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words\0");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words\0");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words\n");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words\n");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words||");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words||");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words+_");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words+_");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words{}");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words{}");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words{} ");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words{} ");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words{}   .");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words{}   .");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words{}\\");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words{}\\");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words{}");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words{}");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "Two Words{}</>");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = Two Words{}</>");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "   leading space");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = [   leading space]");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "trailing space    ");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = [trailing space    ]");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.SOME_ARBITRARY_TEXT);
			cv.put(StartTableSchema.SOME_ARBITRARY_TEXT,
					"   dual ended spaces    ");
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start SOME_ARBITRARY_TEXT = [   dual ended spaces    ]");
		}
		// blob test(s)
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_BLOB);
			byte[] byteArray = new byte[1024];
			new Random().nextBytes(byteArray);
			cv.put(StartTableSchema.A_BLOB, byteArray);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start A_BLOB = random byte[] of 1024 ");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_BLOB);
			byte[] byteArray = new byte[1];
			new Random().nextBytes(byteArray);
			cv.put(StartTableSchema.A_BLOB, byteArray);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start A_BLOB = random byte[] of 1024 ");
		}
		{
			ContentValues cv = createDefaultStartCV();
			cv.remove(StartTableSchema.A_BLOB);
			byte[] byteArray = new byte[2];
			new Random().nextBytes(byteArray);
			cv.put(StartTableSchema.A_BLOB, byteArray);
			CVs.add(cv);
			mimeTypes.add("edu.vu.isis.start");
			description
					.add("edu.vu.isis.start A_BLOB = random byte[] of 1024 ");
		}
	}

	// "edu.vu.isis.ammo"
	public static ContentValues createDefaultAmmoCV() {
		final ContentValues cv = new ContentValues();
		final int sampleForeignKey = -1;
		cv.put(AmmoTableSchema.A_FOREIGN_KEY_REF, sampleForeignKey);
		cv.put(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION,
				AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);
		cv.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
				AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);
		return cv;
	}

	// "edu.vu.isis.quick"
	public static ContentValues createDefaultQuickCV() {
		final ContentValues cv = new ContentValues();

		cv.put(QuickTableSchema.A_SHORT_INTEGER, 0);
		cv.put(QuickTableSchema.AN_INTEGER, 0);
		cv.put(QuickTableSchema.A_BOOLEAN, false);
		cv.put(QuickTableSchema.A_LONG_INTEGER, 0);
		cv.put(QuickTableSchema.A_ABSOLUTE_TIME,
				"" + SystemClock.currentThreadTimeMillis());

		return cv;
	}

	// "edu.vu.isis.start"
	public static ContentValues createDefaultStartCV() {
		final ContentValues cv = new ContentValues();

		Uri uri = null;

		File file = new File(Environment.getExternalStorageDirectory()
				+ File.separator + "test.txt");

		// write the bytes in file
		if (file.exists() == false) {

			try {
				file.createNewFile();

				OutputStream os = new FileOutputStream(file);

				byte[] byteArray = "test data here".getBytes();

				os.write(byteArray);

				os.close();
				// TODO fix logger not showing up
				// logger.info("created new test input file for RequestSerializerTest of CVs");

				System.out.println("file created: " + file);

				uri = Uri.fromFile(file);

			} catch (Exception ex) {
				// logger.info("exception caught when trying to create the tes file. "
				// + ex.getMessage());
			}
		}

		cv.put(StartTableSchema.A_REAL, 0.0);
		cv.put(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER, "");
		cv.put(StartTableSchema.SOME_ARBITRARY_TEXT, "");
		cv.put(StartTableSchema.A_BLOB, "");

		// TODO: This isn't defined yet, so commented out for now.
		// handle if file was there or created

		if (uri == null) {
			cv.put(StartTableSchema.A_FILE, "");
		} else {
			cv.put(StartTableSchema.A_FILE, uri.toString());
		}

		cv.put(StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER, "");

		return cv;
	}
}

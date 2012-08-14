
package edu.vu.isis.ammo.core.distributor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import android.test.mock.MockContentResolver;
import android.test.mock.MockContentProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;
import ch.qos.logback.classic.Level;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.provider.AmmoMockProvider01;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import edu.vu.isis.ammo.core.distributor.RequestSerializer;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.NonConformingAmmoContentProvider;
import edu.vu.isis.ammo.core.distributor.TupleNotFoundException;
import edu.vu.isis.ammo.provider.AmmoMockProviderBase;
import edu.vu.isis.ammo.provider.AmmoMockSchema01;
import edu.vu.isis.ammo.provider.AmmoMockProviderBase.Tables;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.AmmoTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.QuickTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.StartTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchemaBase.AmmoTableSchemaBase;

import edu.vu.isis.ammo.testutils.TestUtils;

import android.net.Uri;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.content.ContentProvider;
import android.content.Context;
import android.os.Parcel;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class edu.vu.isis.ammo.core.ui.RequestSerializerTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 */


public class RequestSerializerTest extends AndroidTestCase {

    private static final Logger logger = LoggerFactory.getLogger("test.request.serial");

    private Context mContext;
    private Uri mBaseUri;

    public RequestSerializerTest() {
        //super("edu.vu.isis.ammo.core.distributor", RequestSerializer.class);
    }

    public RequestSerializerTest( String testName )
    {
        //super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
	public static Test suite() {
        return new TestSuite( RequestSerializerTest.class );
    }

	protected void setUp() throws Exception {
        mContext = getContext();
        mBaseUri = AmmoTableSchema.CONTENT_URI;
        //cr = new MockContentResolver();
    }

	protected void tearDown() throws Exception {
        mContext = null;
        mBaseUri = null;
        //cr = null;
    }


    /**====================================================================
     *  ---D
     *  ---E
     *  ---E
     *  ---P
     *  
     *  --TESTS-
     *  These tests focus on the Request Serializer methods.
     *  Namely intricacies of how a specific part behaves with differing content.
     *=====================================================================
     */

    // =========================================================
    // newInstance() with no parameters
    // =========================================================
    public void testNewInstanceNoArgs()
    {
        RequestSerializer rs = RequestSerializer.newInstance();
        assertNotNull(rs);
    }

    // =========================================================
    // newInstance() with parameters
    // =========================================================
    public void testNewInstanceArgs()
    {
        /*
        Uri uri = null;
        Provider p1 = new Provider(uri);

        Parcel par = utilCreatePayloadParcel();
        Payload  p2 = new Payload(par);

        // Provider.Type.URI, Payload.Type.CV
        RequestSerializer rs = RequestSerializer.newInstance(p1,p2);
        assertNotNull(rs);
         */
    }

    // =========================================================
    // serialize from ContentValues (JSON encoding)
    // =========================================================
    public void testSerializeFromContentValuesJSON()
    {
        /*
        ContentValues cv = utilCreateContentValues();

        RequestSerializer rs = RequestSerializer.newInstance();
        assertNotNull(rs);

        // JSON encoding
        Encoding encJson = Encoding.newInstance(Encoding.Type.JSON);
        byte[] rval = RequestSerializer.serializeFromContentValues(cv, encJson);
         */
        assertTrue(true);
    }

    // =========================================================
    // serialize from ContentValues (terse encoding)
    // =========================================================
    public void testSerializeFromContentValuesTerse()
    {
        /*
        ContentValues cv = utilCreateContentValues();

        RequestSerializer rs = RequestSerializer.newInstance();
        assertNotNull(rs);

        // Terse encoding
        Encoding encTerse = Encoding.newInstance(Encoding.Type.TERSE);
        byte[] rval = RequestSerializer.serializeFromContentValues(cv, encTerse);
         */
        assertTrue(true);
    }

    /**
     * Serialize from ContentProvider (JSON encoding)
     * 
     * Basic use case of serializing from provider.
     * This test 
     * <ol>
     * <li>constructs a mock content provider,
     * <li>loads some data into the content provider,(imitating the application)
     * <li>serializes that data into a json string
     * <li>checks the json string to verify it's correct
     * </ol>
     */
    public void testSerializeFromProviderJson()
    {
        // Test content provider (belongs to the application side)
        AmmoMockProvider01 provider = null;
        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            //if (provider == null) { fail("could not get mock content provider"); }

            final MockContentResolver cr = new MockContentResolver();
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            // Populate content provider with "application-provided data"
            // (this step simulates what the application would already have done
            // before RequestSerializer is called)
            final ContentValues cv = new ContentValues();
            final int sampleForeignKey = -1;
            cv.put(AmmoTableSchema.A_FOREIGN_KEY_REF, sampleForeignKey);
            cv.put(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION, AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);
            cv.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION, AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);

            final Uri tupleUri = utilPopulateTestDbWithCV01(provider, cv);

            // Choose JSON encoding for this test
            final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

            // Serialize the provider content into JSON bytes
            final byte[] jsonBlob; 
            try 
            {
                jsonBlob = RequestSerializer.serializeFromProvider(cr, tupleUri, enc);
            }
            catch (NonConformingAmmoContentProvider ex)
            {
                fail("Should not have thrown NonConformingAmmoContentProvider in this case");
                return;
            }
            catch (TupleNotFoundException ex)
            {
                fail("Should not have thrown TupleNotFoundException in this case");
                return;
            }
            catch (IOException ex) 
            {
                fail("failure of the test itself");
                return;
            }

            // Create a string from the JSON bytes
            final String jsonString;
            try {
                jsonString = new String(jsonBlob, "US-ASCII");
            } catch (UnsupportedEncodingException ex) {
                fail(new StringBuilder().
                        append("could not convert json blob to string").append(jsonBlob).
                        append(" with exception ").append(ex.getLocalizedMessage()).
                        toString());
                return;
            }

            // Examine the JSON string in detail, making sure it contains what we expect
            // (i.e. the contents of the original ContentValues)
            logger.info("encoded json=[{}]", jsonString);
            final JSONObject json;
            try {
                json = new JSONObject(jsonString);

                // First check that all the original keys are present
                assertTrue(json.has(AmmoTableSchema.A_FOREIGN_KEY_REF));
                assertTrue(json.has(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION));
                assertTrue(json.has(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION));

                // Next check that the corresponding values are the same
                assertEquals(json.getInt(AmmoTableSchema.A_FOREIGN_KEY_REF), sampleForeignKey);
                assertEquals(json.getInt(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION), 
                        AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);
                assertEquals(json.getInt(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION), 
                        AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);
            } catch (JSONException ex) {
                fail(new StringBuilder().
                        append("Unexpected JSONException :: JSON string =   ").append(jsonString).
                        append(" :: ERROR = ").append(ex.getLocalizedMessage()).
                        toString());
            } 
        } finally {
            if (provider != null) provider.release();
        }

    }

    /**
     * Serialize to ContentProvider (JSON encoding)
     * Basic use case of serializing a JSON-encoded message into a provider table.
     */
    public void testDeserializeToProviderJson()
    {
        // Test content provider (belongs to the application side)
        AmmoMockProvider01 provider = null;
        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);

            // Content resolver
            final MockContentResolver resolver = new MockContentResolver();
            resolver.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            // Choose JSON encoding for this test
            final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

            // Create JSON to deserialize into provider
            final ContentValues cv = new ContentValues();
            final int sampleForeignKey = -1;
            cv.put(AmmoTableSchema.A_FOREIGN_KEY_REF, sampleForeignKey);
            cv.put(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION, AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);
            cv.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION, AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);
            byte[] jsonBytes = TestUtils.createJsonAsBytes(cv);


            @SuppressWarnings("unused")
            final Uri tupleIn;
            tupleIn = RequestSerializer.deserializeToProvider(mContext, resolver, "dummy", mBaseUri, enc, jsonBytes);

            // We ought to know that the URI... should be row 1, right? 
            //assertEquals(ContentUris.withAppendedId(mBaseUri, 1), tupleIn);

            // Now query the provider and examine its contents, checking that they're
            // the same as the original JSON.
            final SQLiteDatabase db = provider.getDatabase();
            final String table = Tables.AMMO_TBL;
            final String[] projection = null;
            final String selection = null;
            final String[] selectArgs = null;
            final String groupBy = null;
            final String having = null;
            final String orderBy = null;
            final String limit = null;
            final Cursor cursor = db.query(table, projection, selection, selectArgs,
                    groupBy, having, orderBy, limit);

            // The query should have succeeded
            assertNotNull("Query into provider failed", cursor);

            // There should be only one entry
            assertEquals("Unexpected number of rows in cursor", 1, cursor.getCount());

            // Row should be accessible with a cursor
            assertTrue("Row not accessible with cursor", (cursor.moveToFirst()));

            // Examine the provider content in detail, making sure it contains what we expect
            // (i.e. the contents of the original JSON)
            assertEquals(sampleForeignKey, cursor.getInt(cursor.getColumnIndex(AmmoTableSchema.A_FOREIGN_KEY_REF)));
            assertEquals(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH, 
                    cursor.getInt(cursor.getColumnIndex(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION)));
            assertEquals(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE, 
                    cursor.getInt(cursor.getColumnIndex(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION)));
        } finally {
            if (provider != null) provider.release();
        }

    }

    // =========================================================
    // 
    // utility methods to assist testing
    // 
    // =========================================================

    @SuppressWarnings("unused")
    private Parcel utilCreatePayloadParcel()
    {
        return null;
    }

    @SuppressWarnings("unused")
    private ContentValues utilCreateContentValues()
    {
        final ContentValues cv = new ContentValues();
        final int sampleForeignKey = 1;
        cv.put(AmmoTableSchema.A_FOREIGN_KEY_REF, sampleForeignKey);
        cv.put(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION, AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);
        cv.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION, AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);
        return cv;
    }


    @SuppressWarnings("unused")
    private MockContentResolver utilGetContentResolver()
    {
        final MockContentResolver mcr = new MockContentResolver();
        mcr.addProvider(AmmoMockSchema01.AUTHORITY, 
                AmmoMockProvider01.getInstance(getContext()));

        return mcr;
    }

    private AmmoMockProvider01 utilMakeTestProvider01(Context context) 
    {
        return AmmoMockProvider01.getInstance(context);
    }

    private Uri utilPopulateTestDbWithCV01(AmmoMockProvider01 provider, ContentValues cv)
    {
        SQLiteDatabase db = provider.getDatabase();
        long rowid = db.insert(Tables.AMMO_TBL, AmmoTableSchemaBase.A_FOREIGN_KEY_REF, cv);
        Uri tupleUri = ContentUris.withAppendedId(mBaseUri, rowid);
        return tupleUri;
    }

    /**====================================================================
     *  ---W---I---D---E------T---E---S---T---S---
     *  These tests focus on the Request Serializer objects as components.
     *  Namely how the parts interact with the differing content.
     *=====================================================================
     */
    private interface SerialChecker {
        public void check(final byte[] bytes);
    }
    
    public void testRoundTripJson()
    {
        final ContentValues cv = new ContentValues();
        final int sampleForeignKey = -1;
        cv.put(AmmoTableSchema.A_FOREIGN_KEY_REF, sampleForeignKey);
        cv.put(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION, AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);
        cv.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION, AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);

        this.roundTripTrial(Encoding.newInstance(Encoding.Type.JSON), cv, Tables.AMMO_TBL,
                new SerialChecker() {
            @Override public void check(final byte[] bytes) {
                String jsonStr = null;
                try {
                    jsonStr = new String(bytes, "UTF-8");
                    final JSONObject jsonObj = new JSONObject(jsonStr);
                    Assert.assertEquals("quick check json", 
                            String.valueOf(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH),
                            jsonObj.get(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION));

                } catch (UnsupportedEncodingException ex) {
                    Assert.fail("unsupported encoding");
                    return;
                } catch (JSONException ex) {
                    Assert.fail("invalid json "+ jsonStr);
                    return;
                }
            }
        });
    }

    /**
	 * for use in testing 'edu.vu.isis.ammo.dash.media'
	 * 
	 * @return
	 */

	private ContentValues getDashMediaDefaut() {
		ContentValues rValue = new ContentValues();
		// create default values
		String event_id = "event id";
		String data = ""; // uri to file?
		long created_date = 11111111;
		long modified_date = 22222222;
		// put default values into CV
		rValue.put("event id", event_id);
		rValue.put("data", data);
		rValue.put("created date", created_date);
		rValue.put("modified date", modified_date);
		// return filled out CV
		return rValue;
	}

	/**
	 * for use in testing 'edu.vu.isis.ammo.dash.event'
	 * 
	 * @return
	 */
	private ContentValues getDashEventDefaut() {
		ContentValues rValue = new ContentValues();
		// create default values
		String uuid = "";
		int media_count = 0;
		String originator = "";
		String display_name = "";
		String category_id = "";
		String title = "&lt;no title&gt;";
		String description = "";
		double longitude = 0.0;
		double latitude = 0.0;
		long created_date = 111111111;
		long modified_date = 22222222;
		String cid = null;
		String category = null;
		String unit = null;
		long size = 0;
		String dest_group_type = "";
		String dest_group_name = "";
		int STATUS = 1;
		// put default values into CV
		rValue.put("uuid", uuid);
		rValue.put("media count", media_count);
		rValue.put("originator", originator);
		rValue.put("display name", display_name);
		rValue.put("category id", category_id);
		rValue.put("title", title);
		rValue.put("description", description);
		rValue.put("longitude", longitude);
		rValue.put("latitude", latitude);
		rValue.put("created  date", created_date);
		rValue.put("modified date", modified_date);
		rValue.put("cid", cid);
		rValue.put("category", category);
		rValue.put("unit", unit);
		rValue.put("size", size);
		rValue.put("dest group type", dest_group_type);
		rValue.put("dest group name", dest_group_name);
		rValue.put("STATUS", STATUS);
		// return filled out CV
		return rValue;
	}

	/**
	 * for use in testing 'edu.vu.isis.ammo.dash.status'
	 * 
	 * @return
	 */
	private ContentValues getDashStatusDefaut() {
		ContentValues rValue = new ContentValues();
		// create default values
		String event_uuid = "";
		String status = "";
		String actor = "";
		long modified_date = 33333333;
		// put default values into CV
		rValue.put("event uuid", event_uuid);
		rValue.put("status", status);
		rValue.put("actor", actor);
		rValue.put("modified date", modified_date);
		// return filled out CV
		return rValue;
	}

	/**
	 * for use in testing 'edu.vu.isis.ammo.dash.category'
	 * 
	 * @return
	 */
	private ContentValues getDashCatagoryDefault() {
		ContentValues rValue = new ContentValues();
		// create default values
		String main_category = "";
		String sub_category = "";
		String tigr_id = "";
		String icon = ""; // uri to file?
		// put default values into CV
		rValue.put("main category", main_category);
		rValue.put("sub category", sub_category);
		rValue.put("tigr id", tigr_id);
		rValue.put("icon", icon);
		// return filled out CV
		return rValue;
	}

	private boolean compareCVs(ContentValues cv1, ContentValues cv2) {
		// what to do if both are null
		if ((cv1 == null) && (cv2 == null)) {
			return true;
		}
		// check to see if only 1 is null
		if ((cv1 == null) || (cv2 == null)) {
			return false;
		}
		// make sure both same size
		if (cv1.size() != cv2.size()) {
			return false;
		}
		// get both sets of key/value pairs
		Set<Entry<String, Object>> cv1Values = cv1.valueSet();
		Set<Entry<String, Object>> cv2Values = cv2.valueSet();
		// check to make sure both have the same values
		nextEntry: // label for quick return
		for (Entry<String, Object> entry : cv1Values) {

			if ((cv2.get(entry.getKey()) == null) && (entry.getValue() == null)) {
				continue nextEntry;// go to next entry
			}
			if ((cv2.get(entry.getKey()) == null) || (entry.getValue() == null)) {
				return false; // mismatch
			} else if (cv2.get(entry.getKey()).equals(entry.getValue()) == false) {
				return false;
			}

		}
		// neither null, both same size, both share same data, return true
		return true;
	}

	public void testSelfTestVerifycompareCVsMethod() {
		ContentValues cv1 = getDashEventDefaut();
		ContentValues cv2 = getDashEventDefaut();

		Assert.assertEquals("compareCVs() isn't working. ", true,
				compareCVs(cv1, cv2));

		cv1.put("new value to test with", "some randome string");

		Assert.assertEquals("compareCVs() isn't working. ", false,
				compareCVs(cv1, cv2));
	}

	public void testSerializeFromContentValues() {

		Encoding terseEncoding = Encoding.newInstance(Encoding.Type.TERSE);
		Encoding jsonEncoding = Encoding.newInstance(Encoding.Type.JSON);

		String mimeType1 = "edu.vu.isis.ammo.dash.media";

		ArrayList<ContentValues> CVs = new ArrayList<ContentValues>();
		ArrayList<String> mimeTypes = new ArrayList<String>();

		// add in the default possible dash types
		CVs.add(getDashMediaDefaut());
		mimeTypes.add("edu.vu.isis.ammo.dash.media");

		CVs.add(getDashEventDefaut());
		mimeTypes.add("edu.vu.isis.ammo.dash.event");

		CVs.add(getDashStatusDefaut());
		mimeTypes.add("edu.vu.isis.ammo.dash.status");

		CVs.add(getDashCatagoryDefault());
		mimeTypes.add("edu.vu.isis.ammo.dash.category");

		String jsonStr = null;
		try {
			// check all CVs loaded into 'CVs' for proper serial/de-serial
			// with appropriate mimetypes and encoding
			for (int i = 0; i < CVs.size(); ++i) {
				// setup shortTestVariables
				ContentValues testCV = CVs.get(i);
				String mimeType = mimeTypes.get(i);

				// test JSON Encoding
				byte[] encodedBytes = RequestSerializer
						.serializeFromContentValues(testCV, jsonEncoding,
								mimeType);

				ContentValues resultCV = RequestSerializer
						.deserializeToContentValues(encodedBytes, jsonEncoding,
								mimeType);

				assertEquals("Serialize/Deserialze to/from CV of type "
						+ mimeType + " do not match for JSON", true,
						compareCVs(testCV, resultCV));

				// see if encoded bytes can properly be made into JSON object
				jsonStr = new String(encodedBytes, "UTF-8");
				final JSONObject jsonObj = new JSONObject(jsonStr);

				assertEquals("Encoded JSON object for '" + mimeType
						+ "' doesn't contain as many objects as input CV",
						true, (testCV.size() == jsonObj.length()));

				// test Terse Encoding
				byte[] encodedBytesTerse = RequestSerializer
						.serializeFromContentValues(testCV, terseEncoding,
								mimeType);

				ContentValues resultCvTerse = RequestSerializer
						.deserializeToContentValues(encodedBytesTerse,
								terseEncoding, mimeType);

				assertEquals("Serialize/Deserialze to/from CV of type "
						+ mimeType + " do not match for Terse", true,
						compareCVs(testCV, resultCvTerse));

			}

		} catch (UnsupportedEncodingException ex) {
			Assert.fail("unsupported encoding");
			return;
		} catch (JSONException ex) {
			Assert.fail("invalid json " + ex.getMessage());
			return;
		} catch (Exception ex) {
			Assert.fail("\n\nException Caught: " + ex.getMessage()+"\n\n");
		}

	}

	/**
	 * This is round trip test what is taken from the database is identical to
	 * what the database ends up with.
     * 
     * <ol>
     * <li>constructs a mock content provider,
     * <li>loads some data into the content provider,(imitating the application)
     * <li>serializes that data into a json string
     * <li>clear the content provider (imitating the network)
     * <li>deserialize into the content provider
     * <li>check the content of the content provider,(imitating the application)
     * </ol>
     * @param serialChecker 
     * @param ammoTbl 
     * @param cv 
     * @param encoding 
     */
    private void roundTripTrial(Encoding encoding, ContentValues cv, String table, SerialChecker checker) {

        ((ch.qos.logback.classic.Logger) RequestSerializerTest.logger).setLevel(Level.TRACE);
        ((ch.qos.logback.classic.Logger) AmmoMockProviderBase.clogger).setLevel(Level.TRACE);
        ((ch.qos.logback.classic.Logger) AmmoMockProviderBase.hlogger).setLevel(Level.TRACE);
        ((ch.qos.logback.classic.Logger) RequestSerializer.logger).setLevel(Level.TRACE);

        AmmoMockProvider01 provider = null;
        try {
            provider = AmmoMockProvider01.getInstance(mContext);
            Assert.assertNotNull(provider);
            Assert.assertNotNull(provider);
            final MockContentResolver resolver = new MockContentResolver();
            resolver.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            final byte[] encodedBytes = encodeTripTrial(provider, resolver, encoding, cv, table);
            decodeTripTrial(provider, resolver, encoding, cv, table, encodedBytes);

        } finally {
            if (provider != null) provider.release();
            
            ((ch.qos.logback.classic.Logger) RequestSerializerTest.logger).setLevel(Level.OFF);
            ((ch.qos.logback.classic.Logger) AmmoMockProviderBase.clogger).setLevel(Level.OFF);
            ((ch.qos.logback.classic.Logger) AmmoMockProviderBase.hlogger).setLevel(Level.OFF);
            ((ch.qos.logback.classic.Logger) RequestSerializer.logger).setLevel(Level.WARN);
        }


    }
    private byte[] encodeTripTrial(final AmmoMockProvider01 provider,
            final ContentResolver resolver,
            final Encoding enc, final ContentValues cv, final String table) {

        final SQLiteDatabase db = provider.getDatabase();

        long rowid = db.insert(table, AmmoTableSchemaBase.A_FOREIGN_KEY_REF, cv);
        final Uri tupleUri = ContentUris.withAppendedId(mBaseUri, rowid);


        // Serialize the provider content into JSON bytes
        final byte[] encodedBytes; 
        try 
        {
            encodedBytes = RequestSerializer.serializeFromProvider(resolver, tupleUri, enc);
        }
        catch (NonConformingAmmoContentProvider ex)
        {
            Assert.fail("Should not have thrown NonConformingAmmoContentProvider in this case");
            return null;
        }
        catch (TupleNotFoundException ex)
        {
            Assert.fail("Should not have thrown TupleNotFoundException in this case");
            return null;
        }
        catch (IOException ex) 
        {
            Assert.fail("failure of the test itself");
            return null;
        }
        return encodedBytes;
    }


    private void decodeTripTrial(final AmmoMockProvider01 provider,
            final ContentResolver resolver,
            final Encoding enc, final ContentValues cv, final String table,
            final byte[] encodedBytes) {

        final SQLiteDatabase db = provider.getDatabase();
        final int deletedCount = db.delete(table, null, null);
        Assert.assertEquals("check deleted tuple count", 1, deletedCount);
        final Uri tupleIn = RequestSerializer.deserializeToProvider(mContext, resolver,
                "dummy channel", mBaseUri, enc, encodedBytes);

        // We ought to know that the URI... we deleted row 1 so it should be row 2 
        Assert.assertEquals(ContentUris.withAppendedId(mBaseUri, 2), tupleIn);

        // Now query the provider and examine its contents, 
        // checking that they're the same as the original.

        final String[] projection = null;
        final String selection = null;
        final String[] selectArgs = null;
        final String groupBy = null;
        final String having = null;
        final String orderBy = null;
        final String limit = null;
        final Cursor cursor = db.query(table, projection, selection, selectArgs,
                groupBy, having, orderBy, limit);

        // The query should have succeeded
        Assert.assertFalse("Query into provider failed", (cursor == null));

        // There should be only one entry
        Assert.assertEquals("Unexpected number of rows in cursor", 1, cursor.getCount());

        // Row should be accessible with a cursor
        Assert.assertTrue("Row not accessible with cursor", (cursor.moveToFirst()));

        // Examine the provider content in detail, making sure it contains what we expect
        // (i.e. the contents of the original JSON)
        for (final Map.Entry<String,Object> entry : cv.valueSet()) {
            final Object valueObj = entry.getValue();
            if (valueObj instanceof Integer) {
                Assert.assertEquals("foreign key changed/not verified",
                        entry.getValue(), 
                        cursor.getInt(cursor.getColumnIndex(entry.getKey())));
            } else {
                Assert.fail("unhandled data type"+valueObj.getClass().getCanonicalName());
                return;
            }
        }
    }
}

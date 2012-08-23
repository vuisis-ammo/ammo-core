package edu.vu.isis.ammo.core.distributor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import java.lang.Double;
import java.lang.Float;
import java.lang.Boolean;
import java.lang.Integer;
import java.lang.Short;
import java.lang.Long;


import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.util.Log;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;
import ch.qos.logback.classic.Level;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.provider.AmmoMockProvider01;
import edu.vu.isis.ammo.provider.AmmoMockProviderBase;
import edu.vu.isis.ammo.provider.AmmoMockProviderBase.Tables;
import edu.vu.isis.ammo.provider.AmmoMockSchema01;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.AmmoTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.QuickTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.StartTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchemaBase.AmmoTableSchemaBase;
import edu.vu.isis.ammo.testutils.TestUtils;

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
    private static final String TAG = "RequestSerializerTest";

    private static final Logger logger = LoggerFactory.getLogger("test.request.serial");

    private Context mContext;

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
    public static Test suite()
    {
        return new TestSuite( RequestSerializerTest.class );
    }

    protected void setUp() throws Exception
    {
        mContext = getContext();
    }

    protected void tearDown() throws Exception
    {
        mContext = null;
    }


    // =========================================================
    // utility: given a URI, serialize what it contains into JSON
    // and return a JSONObject
    // =========================================================
    private JSONObject utilSerializeJsonFromProvider(MockContentResolver cr, Uri uri)
    {
        final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

        // Serialize the provider content into JSON bytes
        final byte[] jsonBlob;
        try {
            jsonBlob = RequestSerializer.serializeFromProvider(cr, uri, enc);
        } catch (NonConformingAmmoContentProvider ex) {
            fail("Should not have thrown NonConformingAmmoContentProvider in this case");
            return null;
        } catch (TupleNotFoundException ex) {
            fail("Should not have thrown TupleNotFoundException in this case");
            return null;
        } catch (IOException ex) {
            fail("failure of the test itself");
            return null;
        }

        // Create a string from the JSON bytes
        final String jsonString;
        try {
            jsonString = new String(jsonBlob, "US-ASCII");
        } catch (UnsupportedEncodingException ex) {
            fail("Unexpected error -- could not convert json blob to string");
            return null;
        }

        // Create a JSONObject to return
        Log.d(TAG, "encoded json=[ " + jsonString + " ]");
        JSONObject json = null;
        try {
            json = new JSONObject(jsonString);
        } catch (JSONException ex) {
            fail("Unexpected JSONException -- JSON string =   " + jsonString);
        }
	//Log.d(TAG, "jsonobject as string = [" + json.toString() + "]");
	
        return json;
    }

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
    /*
      public void testNewInstanceArgs()
      {
      Uri uri = null;
      Provider p1 = new Provider(uri);

      Parcel par = utilCreatePayloadParcel();
      Payload  p2 = new Payload(par);

      // Provider.Type.URI, Payload.Type.CV
      RequestSerializer rs = RequestSerializer.newInstance(p1,p2);
      assertNotNull(rs);
      }
    */


    /**
     * Serialize from ContentProvider (JSON encoding) :
     * Simple case of known constant values on Table 1 ("Ammo") in schema.
     *
     * This test
     * <ol>
     * <li>constructs a mock content provider,
     * <li>loads some data into the content provider,(imitating the application)
     * <li>serializes that data into a json string
     * <li>checks the json string to verify it's correct
     */
    public void testSerializeFromProviderJson_table1_basic() {
        AmmoMockProvider01 provider = null;
        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            final MockContentResolver cr = new MockContentResolver();
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            SchemaTable1Data d = new SchemaTable1Data();

            // Serialize values from the db
            ContentValues cv = d.createContentValues();
            Uri uri = d.populateProviderWithData(provider, cv);
            JSONObject json = utilSerializeJsonFromProvider(cr, uri);
            if (json == null) {
                fail("unexpected JSON error");
            }
            d.compareJsonToCv(json, cv);
        } finally {
            if (provider != null) provider.release();
        }
    }

    /**
     * Serialize from ContentProvider (JSON encoding) :
     * iterated random trials on Table 1 ("Ammo") in schema.
     *
     */
    public void testSerializeFromProviderJson_table1_random() {
        final int NUM_ITERATIONS = 10;
        AmmoMockProvider01 provider = null;
        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            final MockContentResolver cr = new MockContentResolver();
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            SchemaTable1Data d = new SchemaTable1Data();

            // Repeatedly serialize random values from the db
            for (int i=0; i < NUM_ITERATIONS; i++) {
                ContentValues cv = d.createContentValuesRandom();
                Uri uri = d.populateProviderWithData(provider, cv);
                JSONObject json = utilSerializeJsonFromProvider(cr, uri);
                if (json == null) {
                    fail("unexpected JSON error");
                }

                d.compareJsonToCv(json, cv);
            }
        } finally {
            if (provider != null) provider.release();
        }
    }

    /**
     * Serialize from ContentProvider (JSON encoding) :
     * Simple case of known constant values on Table 2 ("Quick") in schema.
     *
     * This test
     * <ol>
     * <li>constructs a mock content provider,
     * <li>loads some data into the content provider,(imitating the application)
     * <li>serializes that data into a json string
     * <li>checks the json string to verify it's correct
     */
    public void testSerializeFromProviderJson_table2_basic() {
        AmmoMockProvider01 provider = null;
        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            final MockContentResolver cr = new MockContentResolver();
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            SchemaTable2Data d = new SchemaTable2Data();

            // Repeatedly serialize random values from the db
            ContentValues cv = d.createContentValues();
            Uri uri = d.populateProviderWithData(provider, cv);
            JSONObject json = utilSerializeJsonFromProvider(cr, uri);
            if (json == null) {
                fail("unexpected JSON error");
            }

            d.compareJsonToCv(json, cv);
        } finally {
            if (provider != null) provider.release();
        }
    }


    /**
     * Serialize from ContentProvider (JSON encoding) :
     * iterated random trials on Table 2 ("Quick") in schema.
     *
     */
    public void testSerializeFromProviderJson_table2_random() {
        final int NUM_ITERATIONS = 10;
        AmmoMockProvider01 provider = null;
        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            final MockContentResolver cr = new MockContentResolver();
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            SchemaTable2Data d = new SchemaTable2Data();

            // Repeatedly serialize random values from the db
            for (int i=0; i < NUM_ITERATIONS; i++) {
                ContentValues cv = d.createContentValuesRandom();
                Uri uri = d.populateProviderWithData(provider, cv);
                JSONObject json = utilSerializeJsonFromProvider(cr, uri);
                if (json == null) {
                    fail("unexpected JSON error");
                }

                d.compareJsonToCv(json, cv);
            }
        } finally {
            if (provider != null) provider.release();
        }
    }

    /**
     * Serialize from ContentProvider (JSON encoding)
     * Simple case of known constant values on Table 3 ("Start") in schema.
     *
     * This test
     * <ol>
     * <li>constructs a mock content provider,
     * <li>loads some data into the content provider,(imitating the application)
     * <li>serializes that data into a json string
     * <li>checks the json string to verify it's correct
     */
    public void testSerializeFromProviderJson_table3_basic() {
        AmmoMockProvider01 provider = null;
        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            final MockContentResolver cr = new MockContentResolver();
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            SchemaTable3Data d = new SchemaTable3Data();

            // Repeatedly serialize random values from the db
            ContentValues cv = d.createContentValues();
            Uri uri = d.populateProviderWithData(provider, cv);
            JSONObject json = utilSerializeJsonFromProvider(cr, uri);
            if (json == null) {
                fail("unexpected JSON error");
            }

            d.compareJsonToCv(json, cv);
        } finally {
            if (provider != null) provider.release();
        }
    }

    /**
     * Serialize from ContentProvider (JSON encoding)
     * iterated random trials on Table 3 ("Start") in schema.
     *
     */
    public void testSerializeFromProviderJson_table3_random() {
        final int NUM_ITERATIONS = 10;
        AmmoMockProvider01 provider = null;
        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            final MockContentResolver cr = new MockContentResolver();
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            SchemaTable3Data d = new SchemaTable3Data();

            // Repeatedly serialize random values from the db
            for (int i=0; i < NUM_ITERATIONS; i++) {
                ContentValues cv = d.createContentValuesRandom();
                Uri uri = d.populateProviderWithData(provider, cv);
                JSONObject json = utilSerializeJsonFromProvider(cr, uri);
                if (json == null) {
                    fail("unexpected JSON error");
                }

                d.compareJsonToCv(json, cv);
            }
        } finally {
            if (provider != null) provider.release();
        }
    }


    /**
     * Serialize to ContentProvider (JSON encoding) for Table 1 ("Ammo").
     * Basic use case of serializing a JSON-encoded message into a provider table.
     */
    public void testDeserializeToProviderJson_table1_basic()
    {
        // Mock provider and resolver
        AmmoMockProvider01 provider = null;
        final MockContentResolver cr = new MockContentResolver();

        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            // Choose JSON encoding for this test
            final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

            // Object with "Table 1" knowledge
            SchemaTable1Data d = new SchemaTable1Data();

            ContentValues cv = d.createContentValues();
            String jsonStr = TestUtils.createJsonAsString(cv);
            byte[] jsonBytes = jsonStr.getBytes();
            Uri uriIn = RequestSerializer.deserializeToProvider(mContext,
                                                                cr,
                                                                "dummy",
                                                                d.mBaseUri,
                                                                enc,
                                                                jsonBytes);
            d.compareJsonToUri(jsonStr, provider, uriIn);
        } finally {
            if (provider != null) provider.release();
        }
    }

    /**
     * Serialize to ContentProvider (JSON encoding) for Table 2 ("Quick").
     * Basic use case of serializing a JSON-encoded message into a provider table.
     */
    public void testDeserializeToProviderJson_table2_basic()
    {
        // Mock provider and resolver
        AmmoMockProvider01 provider = null;
        final MockContentResolver cr = new MockContentResolver();

        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            // Choose JSON encoding for this test
            final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

            // Object with "Table 2" knowledge
            SchemaTable2Data d = new SchemaTable2Data();

            ContentValues cv = d.createContentValues();
            String jsonStr = TestUtils.createJsonAsString(cv);
            byte[] jsonBytes = jsonStr.getBytes();
            Uri uriIn = RequestSerializer.deserializeToProvider(mContext,
                                                                cr,
                                                                "dummy",
                                                                d.mBaseUri,
                                                                enc,
                                                                jsonBytes);
            d.compareJsonToUri(jsonStr, provider, uriIn);
        } finally {
            if (provider != null) provider.release();
        }
    }

    /**
     * Serialize to ContentProvider (JSON encoding) for Table 3 ("Start").
     * Basic use case of serializing a JSON-encoded message into a provider table.
     */
    public void testDeserializeToProviderJson_table3_basic()
    {
        // Mock provider and resolver
        AmmoMockProvider01 provider = null;
        final MockContentResolver cr = new MockContentResolver();

        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            // Choose JSON encoding for this test
            final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

            // Object with "Table 2" knowledge
            SchemaTable3Data d = new SchemaTable3Data();

            ContentValues cv = d.createContentValues();
            String jsonStr = TestUtils.createJsonAsString(cv);
            byte[] jsonBytes = jsonStr.getBytes();
            Uri uriIn = RequestSerializer.deserializeToProvider(mContext,
                                                                cr,
                                                                "dummy",
                                                                d.mBaseUri,
                                                                enc,
                                                                jsonBytes);
            d.compareJsonToUri(jsonStr, provider, uriIn);
        } finally {
            if (provider != null) provider.release();
        }
    }


    /**
     * Serialize to ContentProvider (JSON encoding) for Table 1 ("Ammo").
     * Iterated with random data.
     */
    public void testDeserializeToProviderJson_table1_random()
    {
        final int NUM_ITERATIONS = 10;

        // Mock provider and resolver
        AmmoMockProvider01 provider = null;
        final MockContentResolver cr = new MockContentResolver();

        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            // Choose JSON encoding for this test
            final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

            // Object with "Table 1" knowledge
            SchemaTable1Data d = new SchemaTable1Data();

            // Repeatedly deserialize random values to the db
            for (int i=0; i < NUM_ITERATIONS; i++) {
                ContentValues cv = d.createContentValuesRandom();
                String jsonStr = TestUtils.createJsonAsString(cv);
                byte[] jsonBytes = jsonStr.getBytes();
                Uri uriIn = RequestSerializer.deserializeToProvider(mContext,
                                                                    cr,
                                                                    "dummy",
                                                                    d.mBaseUri,
                                                                    enc,
                                                                    jsonBytes);
                d.compareJsonToUri(jsonStr, provider, uriIn);
            }
        } finally {
            if (provider != null) provider.release();
        }
    }


    /**
     * Serialize to ContentProvider (JSON encoding) for Table 2 ("Quick").
     * Iterated with random data.
     */
    public void testDeserializeToProviderJson_table2_random()
    {
        final int NUM_ITERATIONS = 10;

        // Mock provider and resolver
        AmmoMockProvider01 provider = null;
        final MockContentResolver cr = new MockContentResolver();

        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            // Choose JSON encoding for this test
            final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

            // Object with "Table 1" knowledge
            SchemaTable2Data d = new SchemaTable2Data();

            // Repeatedly deserialize random values to the db
            for (int i=0; i < NUM_ITERATIONS; i++) {
                ContentValues cv = d.createContentValuesRandom();
                String jsonStr = TestUtils.createJsonAsString(cv);
                byte[] jsonBytes = jsonStr.getBytes();
                Uri uriIn = RequestSerializer.deserializeToProvider(mContext,
                                                                    cr,
                                                                    "dummy",
                                                                    d.mBaseUri,
                                                                    enc,
                                                                    jsonBytes);
                d.compareJsonToUri(jsonStr, provider, uriIn);
            }
        } finally {
            if (provider != null) provider.release();
        }
    }

    /**
     * Serialize to ContentProvider (JSON encoding) for Table 3 ("Start").
     * Iterated with random data.
     */
    public void testDeserializeToProviderJson_table3_random()
    {
        final int NUM_ITERATIONS = 10;

        // Mock provider and resolver
        AmmoMockProvider01 provider = null;
        final MockContentResolver cr = new MockContentResolver();

        try {
            provider = utilMakeTestProvider01(mContext);
            assertNotNull(provider);
            cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

            // Choose JSON encoding for this test
            final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

            // Object with "Table 1" knowledge
            SchemaTable3Data d = new SchemaTable3Data();

            // Repeatedly deserialize random values to the db
            for (int i=0; i < NUM_ITERATIONS; i++) {
                ContentValues cv = d.createContentValuesRandom();
                String jsonStr = TestUtils.createJsonAsString(cv);
                byte[] jsonBytes = jsonStr.getBytes();
                Uri uriIn = RequestSerializer.deserializeToProvider(mContext,
                                                                    cr,
                                                                    "dummy",
                                                                    d.mBaseUri,
                                                                    enc,
                                                                    jsonBytes);
                d.compareJsonToUri(jsonStr, provider, uriIn);
            }
        } finally {
            if (provider != null) provider.release();
        }
    }


    // -- below this line --
    // Private classes for containing knowledge about schema. These are intended
    // to keep the schema-specific knowledge localized so that if the schema
    // changes, we can change only these classes and shouldn't need to re-write
    // the tests themselves (or only minimally).

    private interface SchemaTable {
        public ContentValues createContentValues();
        public ContentValues createContentValuesRandom();
        public Uri populateProviderWithData(AmmoMockProvider01 provider, ContentValues cv);
        public void compareJsonToCv(JSONObject json, ContentValues cv);
        public void compareJsonToUri(String jsonStr, AmmoMockProvider01 provider, Uri uri);
    }

    // =========================================================
    // Encapsulate knowledge of Table 1 ("Ammo") in the schema
    // =========================================================
    private class SchemaTable1Data implements SchemaTable {
        public SchemaTable1Data() {}

        public Uri mBaseUri = AmmoTableSchema.CONTENT_URI;
        public String mTable =  Tables.AMMO_TBL;

        private final String schemaForeignKey = AmmoTableSchema.A_FOREIGN_KEY_REF;
        private final String schemaExEnum = AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION;
        private final String schemaInEnum = AmmoTableSchema.AN_INCLUSIVE_ENUMERATION;

        public ContentValues createContentValues()
        {
            final ContentValues cv = new ContentValues();
            final int sampleForeignKey = 1;
            cv.put(schemaForeignKey, sampleForeignKey);
            cv.put(schemaExEnum, AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);
            cv.put(schemaInEnum, AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);
            Log.d(TAG, "generated ContentValues: cv=[" + cv.toString() + "]");
            return cv;
        }

        public ContentValues createContentValuesRandom()
        {
            final ContentValues cv = new ContentValues();
	    final int keyUpperBound = 100;
	    int[] ExEnum = new int[] {AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH, 
				      AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_LOW,
				      AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_MEDIUM};
	    int[] InEnum = new int[] {AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE, 
				      AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_ORANGE,
				      AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_PEAR};
            cv.put(schemaForeignKey, TestUtils.randomInt(keyUpperBound));
            cv.put(schemaExEnum, ExEnum[TestUtils.randomInt(ExEnum.length)]);
            cv.put(schemaInEnum, InEnum[TestUtils.randomInt(InEnum.length)]);
            Log.d(TAG, "generated ContentValues: cv=[" + cv.toString() + "]");
            return cv;
        }

        public Uri populateProviderWithData(AmmoMockProvider01 provider, ContentValues cv)
        {
            SQLiteDatabase db = provider.getDatabase();
            long rowid = -1;
            Uri tupleUri = null;

            rowid = db.insert(Tables.AMMO_TBL, AmmoTableSchemaBase.A_FOREIGN_KEY_REF, cv);
            tupleUri = ContentUris.withAppendedId(AmmoTableSchema.CONTENT_URI, rowid);

            //Log.d(TAG, "rowId = " + String.valueOf(rowid));
            Log.d(TAG, "inserted uri = " + tupleUri.toString());
            return tupleUri;
        }

        // Compare json serialization to the cv which was written to the db originally
        public void compareJsonToCv(JSONObject json, ContentValues cv)
        {
            try {

                assertTrue(json.has(schemaForeignKey));
                assertTrue(json.has(schemaExEnum));
                assertTrue(json.has(schemaInEnum));

                JSONArray names = json.names();
                JSONArray values = json.toJSONArray(names);
                for(int i = 0 ; i < values.length(); i++) {
                    if(names.getString(i).equals(schemaForeignKey)) {
                        int actual = values.getInt(i);
                        int expected = cv.getAsInteger(schemaForeignKey).intValue();
                        Log.d(TAG, "   json value='" + String.valueOf(actual)
                              + "'     cv value='"+ String.valueOf(expected)  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaExEnum)) {
                        long actual = values.getLong(i);
                        long expected = cv.getAsLong(schemaExEnum).longValue();
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaInEnum)) {
                        long actual = values.getLong(i);
                        long expected = cv.getAsLong(schemaInEnum).longValue();
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                }

            } catch (JSONException ex) {
                fail("unexpected JSONException");
                return;
            }

        }

        // Compare json serialization to the provider content written from it
        public void compareJsonToUri(String jsonStr, AmmoMockProvider01 provider, Uri uri)
        {
            try {
                JSONObject json = new JSONObject(jsonStr);

                assertTrue(json.has(schemaForeignKey));
                assertTrue(json.has(schemaExEnum));
                assertTrue(json.has(schemaInEnum));

                // Now query the provider and examine its contents, checking that they're
                // the same as the original JSON.
                final String[] projection = null;
                final String selection = null;
                final String[] selectArgs = null;
                final String orderBy = null;
                final Cursor cursor = provider.query(uri, projection, selection, selectArgs, orderBy);

                // The query should have succeeded
                assertNotNull("Query into provider failed", cursor);

                // There should be only one entry
                assertEquals("Unexpected number of rows in cursor", 1, cursor.getCount());

                // Row should be accessible with a cursor
                assertTrue("Row not accessible with cursor", (cursor.moveToFirst()));

                // Examine the provider content in detail, making sure it contains what we expect
                // (i.e. the contents of the original JSON)
                JSONArray names = json.names();
                JSONArray values = json.toJSONArray(names);
                for(int i = 0 ; i < values.length(); i++) {
                    if(names.getString(i).equals(schemaForeignKey)) {
                        int expected = values.getInt(i);
                        int actual = cursor.getInt(cursor.getColumnIndex(schemaForeignKey));
                        Log.d(TAG, "   json value='" + expected + "'     db value='" + actual + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaExEnum)) {
                        int expected = values.getInt(i);
                        int actual = cursor.getInt(cursor.getColumnIndex(schemaExEnum));
                        Log.d(TAG, "   json value='" + expected + "'     db value='" + actual + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaInEnum)) {
                        int expected = values.getInt(i);
                        int actual = cursor.getInt(cursor.getColumnIndex(schemaInEnum));
                        Log.d(TAG, "   json value='" + expected + "'     db value='" + actual + "'");
                        assertEquals(actual, expected);
                    }

                }

                // Close cursor when finished
                cursor.close();
            } catch (JSONException e) {
                e.printStackTrace();
                fail("unexpected JSONException");
                return;
            }
        }
    }

    // =========================================================
    // Encapsulate knowledge of Table 2 ("Quick") in the schema
    // =========================================================
    private class SchemaTable2Data implements SchemaTable {
        public SchemaTable2Data() {}

        public Uri mBaseUri = QuickTableSchema.CONTENT_URI;
        public String mTable =  Tables.QUICK_TBL;

        private final String schemaShortInt = QuickTableSchema.A_SHORT_INTEGER;
        private final String schemaLongInt = QuickTableSchema.A_LONG_INTEGER;
        private final String schemaInt = QuickTableSchema.AN_INTEGER;
        private final String schemaBool = QuickTableSchema.A_BOOLEAN;
        private final String schemaTime = QuickTableSchema.A_ABSOLUTE_TIME;

        public ContentValues createContentValues()
        {
            final ContentValues cv = new ContentValues();
            cv.put(schemaShortInt, TestUtils.TEST_SHORT_INTEGER);
            cv.put(schemaInt, TestUtils.TEST_INTEGER);
            cv.put(schemaLongInt, TestUtils.TEST_LONG_INTEGER);
            cv.put(schemaBool, TestUtils.TEST_BOOLEAN);
            //cv.put(schemaTime, ???);
            Log.d(TAG, "generated ContentValues: cv=[" + cv.toString() + "]");
            return cv;
        }

        public ContentValues createContentValuesRandom()
        {
            final ContentValues cv = new ContentValues();
            cv.put(schemaShortInt, TestUtils.randomShort());
            cv.put(schemaInt, TestUtils.randomInt());
            cv.put(schemaLongInt, TestUtils.randomLong());
            cv.put(schemaBool, TestUtils.randomBoolean());
            //cv.put(schemaTime, ???);
            Log.d(TAG, "generated ContentValues: cv=[" + cv.toString() + "]");
            return cv;
        }

        public Uri populateProviderWithData(AmmoMockProvider01 provider, ContentValues cv)
        {
            SQLiteDatabase db = provider.getDatabase();
            long rowid = -1;
            Uri tupleUri = null;

            rowid = db.insert(Tables.QUICK_TBL, null, cv);
            tupleUri = ContentUris.withAppendedId(QuickTableSchema.CONTENT_URI, rowid);

            //Log.d(TAG, "rowId = " + String.valueOf(rowid));
            Log.d(TAG, "inserted uri = " + tupleUri.toString());
            return tupleUri;
        }



        // Compare json serialization to the cv which was written to the db originally
        public void compareJsonToCv(JSONObject json, ContentValues cv)
        {
            try {
                assertTrue(json.has(schemaShortInt));
                assertTrue(json.has(schemaInt));
                assertTrue(json.has(schemaLongInt));
                // assertTrue(json.has(schemaBool));
                // assertTrue(json.has(schemaTime));

                JSONArray names = json.names();
                JSONArray values = json.toJSONArray(names);
                for(int i = 0 ; i < values.length(); i++) {
                    if(names.getString(i).equals(schemaShortInt)) {
                        int actual = values.getInt(i);
                        int expected = cv.getAsInteger(schemaShortInt).intValue();
                        Log.d(TAG, "   json value='" + String.valueOf(actual)
                              + "'     cv value='"+ String.valueOf(expected)  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaLongInt)) {
                        long actual = values.getLong(i);
                        long expected = cv.getAsLong(schemaLongInt).longValue();
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaInt)) {
                        int actual = values.getInt(i);
                        int expected = cv.getAsInteger(schemaInt).intValue();
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaBool)) {
                        boolean actual = (values.getInt(i) == 1);  //values.getBoolean(i);
                        boolean expected = cv.getAsBoolean(schemaBool).booleanValue();
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaTime)) {
                        // TODO
                    }
                }
            } catch (JSONException ex) {
                fail("unexpected JSONException");
                return;
            }
        }

        // Compare json serialization to the provider content written from it
        public void compareJsonToUri(String jsonStr, AmmoMockProvider01 provider, Uri uri)
        {
            try {
                JSONObject json = new JSONObject(jsonStr);

                assertTrue(json.has(schemaShortInt));
                assertTrue(json.has(schemaInt));
                assertTrue(json.has(schemaLongInt));
                assertTrue(json.has(schemaBool));

                // Now query the provider and examine its contents, checking that they're
                // the same as the original JSON.
		final String[] projection = null;
                final String selection = null;
                final String[] selectArgs = null;
                final String orderBy = null;
                final Cursor cursor = provider.query(uri, projection, selection, selectArgs, orderBy);

                // The query should have succeeded
                assertNotNull("Query into provider failed", cursor);

                // There should be only one entry
                assertEquals("Unexpected number of rows in cursor", 1, cursor.getCount());

                // Row should be accessible with a cursor
                assertTrue("Row not accessible with cursor", (cursor.moveToFirst()));

                // Examine the provider content in detail, making sure it contains what we expect
                // (i.e. the contents of the original JSON)
                JSONArray names = json.names();
                JSONArray values = json.toJSONArray(names);
                for(int i = 0 ; i < values.length(); i++) {
                    if(names.getString(i).equals(schemaShortInt)) {
                        int actual = values.getInt(i);
                        int expected = cursor.getInt(cursor.getColumnIndex(schemaShortInt));
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaLongInt)) {
                        long actual = values.getLong(i);
                        long expected = cursor.getLong(cursor.getColumnIndex(schemaLongInt));
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaInt)) {
                        int actual = values.getInt(i);
                        int expected = cursor.getInt(cursor.getColumnIndex(schemaInt));
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaBool)) {
                        boolean actual = values.getBoolean(i); //(values.getInt(i) == 1); 
                        boolean expected = (cursor.getInt(cursor.getColumnIndex(schemaBool)) == 1);
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaTime)) {
                        // TODO
                    }
                }

                // Close cursor when finished
                cursor.close();
            } catch (JSONException e) {
                e.printStackTrace();
                fail("unexpected JSONException");
                return;
            }
        }

    }

    // =========================================================
    // Encapsulate knowledge of Table 3 ("Start") in the schema
    // =========================================================
    private class SchemaTable3Data implements SchemaTable {
        public SchemaTable3Data() {}

        public Uri mBaseUri = StartTableSchema.CONTENT_URI;
        public String mTable =  Tables.START_TBL;

        private final String schemaReal = StartTableSchema.A_REAL;
        private final String schemaGuid = StartTableSchema.A_GLOBALLY_UNIQUE_IDENTIFIER;
        private final String schemaText = StartTableSchema.SOME_ARBITRARY_TEXT;
        private final String schemaFile = StartTableSchema.A_FILE;
        private final String schemaBlob = StartTableSchema.A_BLOB;

        public ContentValues createContentValues()
        {
            final ContentValues cv = new ContentValues();
            cv.put(schemaReal, TestUtils.TEST_DOUBLE);
            cv.put(schemaGuid, TestUtils.TEST_GUID_STR);
            cv.put(schemaText, TestUtils.TEST_FIXED_STRING);
            //cv.put(StartTableSchema.A_BLOB, ???);
            //cv.put(StartTableSchema.A_FILE, ???);

            Log.d(TAG, "generated ContentValues: cv=[" + cv.toString() + "]");
            return cv;
        }

        public ContentValues createContentValuesRandom()
        {
            final ContentValues cv = new ContentValues();
            cv.put(schemaReal, TestUtils.randomDouble());
            cv.put(schemaGuid, TestUtils.randomGuidAsString());
            final int max_text_size = 50;
            int text_size = TestUtils.randomInt(max_text_size);
            if (text_size == 0) { text_size = 1; }
            cv.put(schemaText, TestUtils.randomText(text_size));
            //cv.put(StartTableSchema.A_BLOB, ???);
            //cv.put(StartTableSchema.A_FILE, ???);

            Log.d(TAG, "generated ContentValues: cv=[" + cv.toString() + "]");
            return cv;
        }

        public Uri populateProviderWithData(AmmoMockProvider01 provider, ContentValues cv)
        {
            SQLiteDatabase db = provider.getDatabase();
            long rowid = -1;
            Uri tupleUri = null;

            rowid = db.insert(Tables.START_TBL, null, cv);
            tupleUri = ContentUris.withAppendedId(StartTableSchema.CONTENT_URI, rowid);

            //Log.d(TAG, "rowId = " + String.valueOf(rowid));
            Log.d(TAG, "inserted uri = " + tupleUri.toString());
            return tupleUri;
        }

        // Compare json serialization to the cv which was written to the db originally
        public void compareJsonToCv(JSONObject json, ContentValues cv)
        {
            final double error_bar = 0.00001;
            try {

                assertTrue(json.has(schemaReal));
                assertTrue(json.has(schemaGuid));
                assertTrue(json.has(schemaText));
                //assertTrue(json.has(schemaBlob));
                //assertTrue(json.has(schemaFile));

                JSONArray names = json.names();
                JSONArray values = json.toJSONArray(names);
                for(int i = 0 ; i < values.length(); i++) {
                    if(names.getString(i).equals(schemaReal)) {
                        double actual = values.getDouble(i);
                        double expected = cv.getAsDouble(schemaReal).doubleValue();
                        Log.d(TAG, "   json value='" + String.valueOf(actual)
                              + "'     cv value='"+ String.valueOf(expected)  + "'");
                        assertEquals(actual, expected, error_bar);
                    }
                    if(names.getString(i).equals(schemaText)) {
                        String actual = values.getString(i);
                        String expected = cv.getAsString(schemaText);
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaGuid)) {
                        String actual = values.getString(i);
                        String expected = cv.getAsString(schemaGuid);
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaFile)) {
                        // TODO
                    }
                    if(names.getString(i).equals(schemaBlob)) {
                        // TODO
                    }
                }
            } catch (JSONException ex) {
                fail("unexpected JSONException");
                return;
            }
        }

        // Compare json serialization to the provider content written from it
        public void compareJsonToUri(String jsonStr, AmmoMockProvider01 provider, Uri uri)
        {
            final double error_bar = 0.00001;
            try {
                JSONObject json = new JSONObject(jsonStr);

                assertTrue(json.has(schemaReal));
                assertTrue(json.has(schemaGuid));
                assertTrue(json.has(schemaText));
                //assertTrue(json.has(schemaBlob));
                //assertTrue(json.has(schemaFile));

                // Now query the provider and examine its contents, checking that they're
                // the same as the original JSON.
		final String[] projection = null;
                final String selection = null;
                final String[] selectArgs = null;
                final String orderBy = null;
                final Cursor cursor = provider.query(uri, projection, selection, selectArgs, orderBy);


                // The query should have succeeded
                assertNotNull("Query into provider failed", cursor);

                // There should be only one entry
                assertEquals("Unexpected number of rows in cursor", 1, cursor.getCount());

                // Row should be accessible with a cursor
                assertTrue("Row not accessible with cursor", (cursor.moveToFirst()));

                // Examine the provider content in detail, making sure it contains what we expect
                // (i.e. the contents of the original JSON)
                JSONArray names = json.names();
                JSONArray values = json.toJSONArray(names);
                for(int i = 0 ; i < values.length(); i++) {
                    if(names.getString(i).equals(schemaReal)) {
                        double actual = values.getDouble(i);
                        double expected = cursor.getDouble(cursor.getColumnIndex(schemaReal));
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected + "'");
                        assertEquals(actual, expected, error_bar);
                    }
                    if(names.getString(i).equals(schemaText)) {
                        String actual = values.getString(i);
                        String expected = cursor.getString(cursor.getColumnIndex(schemaText));
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaGuid)) {
                        String actual = values.getString(i);
                        String expected = cursor.getString(cursor.getColumnIndex(schemaGuid));
                        Log.d(TAG, "   json value='" + actual + "'     cv value='"+ expected  + "'");
                        assertEquals(actual, expected);
                    }
                    if(names.getString(i).equals(schemaFile)) {
                        // TODO
                    }
                    if(names.getString(i).equals(schemaBlob)) {
                        // TODO
                    }
                }

                // Close cursor when finished
                cursor.close();
            } catch (JSONException e) {
                e.printStackTrace();
                fail("unexpected JSONException");
                return;
            }
        }
    }

}

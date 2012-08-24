
package edu.vu.isis.ammo.core.distributor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;
import ch.qos.logback.classic.Level;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.provider.AmmoMockProvider01;
import edu.vu.isis.ammo.provider.AmmoMockProviderBase;
import edu.vu.isis.ammo.provider.AmmoMockProviderBase.Tables;
import edu.vu.isis.ammo.provider.AmmoMockSchema01;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.AmmoTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchemaBase.AmmoTableSchemaBase;

/**
 * This is a simple framework for a test of an Application. See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more
 * information on how to write and extend Application tests.
 * <p/>
 * To run this test, you can type: adb shell am instrument -w \ -e class
 * edu.vu.isis.ammo.core.ui.RequestSerializerTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 */

public class RequestSerializerComponentTest extends AndroidTestCase {

    private static final Logger logger = LoggerFactory.getLogger("test.request.serial");

    private Context mContext;
    private Uri mBaseUri;

    public RequestSerializerComponentTest() {
        // super("edu.vu.isis.ammo.core.distributor", RequestSerializer.class);
    }

    public RequestSerializerComponentTest(String testName)
    {
        // super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite(RequestSerializerComponentTest.class);
    }

    protected void setUp() throws Exception
    {
        mContext = getContext();
        mBaseUri = AmmoTableSchema.CONTENT_URI;
        // cr = new MockContentResolver();
    }

    protected void tearDown() throws Exception
    {
        mContext = null;
        mBaseUri = null;
        // cr = null;
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

    /**
     * ====================================================================
     * ---W---I---D---E------T---E---S---T---S--- These tests focus on the
     * Request Serializer objects as components. Namely how the parts interact
     * with the differing content.
     * =====================================================================
     */
    private interface SerialChecker {
        public void check(final byte[] bytes);
    }

    public void testRoundTripJson()
    {
        final ContentValues cv = new ContentValues();
        final int sampleForeignKey = -1;
        cv.put(AmmoTableSchema.A_FOREIGN_KEY_REF, sampleForeignKey);
        cv.put(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION,
                AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);
        cv.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION,
                AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);

        this.roundTripTrial(Encoding.newInstance(Encoding.Type.JSON), cv, Tables.AMMO_TBL,
                new SerialChecker() {
                    @Override
                    public void check(final byte[] bytes) {
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
                            Assert.fail("invalid json " + jsonStr);
                            return;
                        }
                    }
                });
    }

    /**
     * This is round trip test what is taken from the database is identical to
     * what the database ends up with.
     * <ol>
     * <li>constructs a mock content provider,
     * <li>loads some data into the content provider,(imitating the application)
     * <li>serializes that data into a json string
     * <li>clear the content provider (imitating the network)
     * <li>deserialize into the content provider
     * <li>check the content of the content provider,(imitating the application)
     * </ol>
     * 
     * @param serialChecker
     * @param ammoTbl
     * @param cv
     * @param encoding
     */
    private void roundTripTrial(Encoding encoding, ContentValues cv, String table,
            SerialChecker checker) {

        ((ch.qos.logback.classic.Logger) RequestSerializerComponentTest.logger)
                .setLevel(Level.TRACE);
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
            if (provider != null)
                provider.release();

            ((ch.qos.logback.classic.Logger) RequestSerializerComponentTest.logger)
                    .setLevel(Level.OFF);
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
        } catch (NonConformingAmmoContentProvider ex)
        {
            Assert.fail("Should not have thrown NonConformingAmmoContentProvider in this case");
            return null;
        } catch (TupleNotFoundException ex)
        {
            Assert.fail("Should not have thrown TupleNotFoundException in this case");
            return null;
        } catch (IOException ex)
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

        // We ought to know that the URI... we deleted row 1 so it should be row
        // 2
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

        // Examine the provider content in detail, making sure it contains what
        // we expect
        // (i.e. the contents of the original JSON)
        for (final Map.Entry<String, Object> entry : cv.valueSet()) {
            final Object valueObj = entry.getValue();
            if (valueObj instanceof Integer) {
                Assert.assertEquals("foreign key changed/not verified",
                        entry.getValue(),
                        cursor.getInt(cursor.getColumnIndex(entry.getKey())));
            } else {
                Assert.fail("unhandled data type" + valueObj.getClass().getCanonicalName());
                return;
            }
        }
    }

    static ArrayList<ContentValues> CVs = RequestSerializerTestCases
            .getCVsForRoundTripTests();
    static ArrayList<String> mimeTypes = RequestSerializerTestCases
            .getMimeTypesForRoundTripTests();
    static ArrayList<String> description = RequestSerializerTestCases
            .getDescriptionsForRoundTripTests();

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
        final Set<Entry<String, Object>> cv1Values = cv1.valueSet();
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
        ContentValues cv1 = RequestSerializerTestCases.createDefaultAmmoCV();
        ContentValues cv2 = RequestSerializerTestCases.createDefaultAmmoCV();

        Assert.assertEquals(
                "compareCVs() isn't working : test 1, compare 2 defualt ",
                true, compareCVs(cv1, cv2));

        cv1.put("new value to test with", "some randome string");

        Assert.assertEquals(
                "compareCVs() isn't working : test 2, compare 2 different with addition ",
                false, compareCVs(cv1, cv2));

        cv1.remove("new value to test with");

        Assert.assertEquals(
                "compareCVs() isn't working : test 3, compare 2 'same' but after mutation ",
                true, compareCVs(cv1, cv2));

        cv1.remove(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION);

        Assert.assertEquals(
                "compareCVs() isn't working : test 4, compare 2 different with reduction",
                false, compareCVs(cv1, cv2));

    }

    // 3 test sets

    // putting the tests into the 3 test sets

    /**
     * test round trip for a ContentValue for JSON encoding with all the test
     * cases. serializes the CV to byte[] then de-serializes encoded byte[] to a
     * 2nd CV compares the input CV and output CV for sameness
     */
    public void testSerializeFromContentValuestoRoundTripJSON() {

        Encoding jsonEncoding = Encoding.newInstance(Encoding.Type.JSON);

        ContractStore contractStore = ContractStore.newInstance(mContext);

        boolean testSetsEqual = false;
        if ((CVs.size() == mimeTypes.size())
                && (CVs.size() == description.size())
                && (description.size() == mimeTypes.size())) {
            testSetsEqual = true;
        }

        assertNotNull("CV test 0 was null", CVs.get(0));
        assertNotNull("mimeTypes test 0 was null", mimeTypes.get(0));
        assertNotNull("description test 0 was null", description.get(0));

        // check that all 3 have the same input sizes
        assertEquals("all 3 test sets are not the same size", true,
                testSetsEqual);

        String jsonStr = null;
        int i = 0;
        try {

            // check all CVs loaded into 'CVs' for proper serial/de-serial
            // with appropriate mimetypes and encoding
            for (i = 0; i < CVs.size(); ++i) {
                // setup shortTestVariables
                ContentValues testCV = CVs.get(i);
                String mimeType = mimeTypes.get(i);
                String desc = description.get(i);

                // test JSON Encoding
                byte[] encodedBytes = RequestSerializer
                        .serializeFromContentValues(testCV, jsonEncoding,
                                mimeType, contractStore);

                ContentValues resultCV = RequestSerializer
                        .deserializeToContentValues(encodedBytes, jsonEncoding,
                                mimeType, contractStore);

                assertEquals("Serialize/Deserialze to/from CV of type "
                        + mimeType + " do not match for JSON for " + desc,
                        true, compareCVs(testCV, resultCV));

                // see if encoded bytes can properly be made into JSON object
                jsonStr = new String(encodedBytes, "UTF-8");
                final JSONObject jsonObj = new JSONObject(jsonStr);

                assertEquals("Encoded JSON object for '" + mimeType
                        + "' doesn't contain as many objects as input CV for "
                        + desc, true, (testCV.size() == jsonObj.length()));
            }

        } catch (UnsupportedEncodingException ex) {
            Assert.fail("unsupported encoding " + i + " " + ex.getStackTrace());
        } catch (JSONException ex) {
            Assert.fail("invalid json " + i + " " + ex.getStackTrace());
        } catch (Exception ex) {
            // not sure 'i' will have right value, not sure when stack unrolling
            // happens, before or after catch, this handles either
            if (i == -1) {
                Assert.fail("\n\nException Caught: " + ex.getStackTrace()
                        + "\n\n");
            } else {
                Assert.fail("\n\nException Caught: test case # " + i + " "
                        + ex.getStackTrace() + "\n\n");
            }
        }

    }

    /**
     * tests if all the test cases will properly encode from CV to TERSE
     * encoding and back. TODO doesn't handle the variations on data values
     * based on terse possibly encoding a subset of the data. TODO doesn't
     * handle the terse encoding deserializing then putting in the default
     * values from the contract
     */
    public void testSerializeFromContentValuestoRoundTripTerse() {

        Encoding terseEncoding = Encoding.newInstance(Encoding.Type.TERSE);

        ContractStore contractStore = ContractStore.newInstance(mContext);

        boolean testSetsEqual = false;
        if ((CVs.size() == mimeTypes.size())
                && (CVs.size() == description.size())
                && (description.size() == mimeTypes.size())) {
            testSetsEqual = true;
        }

        // check that all 3 have the same input sizes
        assertEquals("all 3 test sets are not the same size", true,
                testSetsEqual);

        int i = -1;
        try {

            // check all CVs loaded into 'CVs' for proper serial/de-serial
            // with appropriate mimetypes and encoding
            for (i = 0; i < CVs.size(); ++i) {
                // setup shortTestVariables
                ContentValues testCV = CVs.get(i);
                String mimeType = mimeTypes.get(i);
                String desc = description.get(i);

                // test Terse Encoding
                byte[] encodedBytesTerse = RequestSerializer
                        .serializeFromContentValues(testCV, terseEncoding,
                                mimeType, contractStore);

                ContentValues resultCvTerse = RequestSerializer
                        .deserializeToContentValues(encodedBytesTerse,
                                terseEncoding, mimeType, contractStore);

                assertEquals("Serialize/Deserialze to/from CV of type "
                        + mimeType + " do not match for Terse for" + desc,
                        true, compareCVs(testCV, resultCvTerse));

            }
        } catch (Exception ex) {
            // not sure 'i' will have right value, not sure when stack unrolling
            // happens, before or after catch, this handles either
            if (i == -1) {
                Assert.fail("\n\nException Caught: " + ex.getStackTrace()
                        + "\n\n");
            } else {
                Assert.fail("\n\nException Caught: test case # " + i + " "
                        + ex.getStackTrace() + "\n\n");
            }

        }

    }

}

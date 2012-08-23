package edu.vu.isis.ammo.core.distributor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;


import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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


public class RequestSerializerComponentTest extends AndroidTestCase {

    private static final Logger logger = LoggerFactory.getLogger("test.request.serial");

    private Context mContext;
    private Uri mBaseUri;

    public RequestSerializerComponentTest() {
        //super("edu.vu.isis.ammo.core.distributor", RequestSerializer.class);
    }

    public RequestSerializerComponentTest( String testName )
    {
        //super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( RequestSerializerComponentTest.class );
    }

    protected void setUp() throws Exception
    {
        mContext = getContext();
        mBaseUri = AmmoTableSchema.CONTENT_URI;
        //cr = new MockContentResolver();
    }

    protected void tearDown() throws Exception
    {
        mContext = null;
        mBaseUri = null;
        //cr = null;
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
     * This is round trip test what is taken from the database
     * is identical to what the database ends up with.
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

        ((ch.qos.logback.classic.Logger) RequestSerializerComponentTest.logger).setLevel(Level.TRACE);
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

            ((ch.qos.logback.classic.Logger) RequestSerializerComponentTest.logger).setLevel(Level.OFF);
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

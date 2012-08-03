
package edu.vu.isis.ammo.core.distributor;

import android.test.AndroidTestCase;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import android.test.mock.MockContentResolver;
import android.test.mock.MockContentProvider;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import edu.vu.isis.ammo.provider.AmmoMockProvider01;

import edu.vu.isis.ammo.core.distributor.RequestSerializer;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.NonConformingAmmoContentProvider;
import edu.vu.isis.ammo.core.distributor.TupleNotFoundException;
import edu.vu.isis.ammo.provider.AmmoMockSchema01;
import edu.vu.isis.ammo.provider.AmmoMockProviderBase.Tables;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.AmmoTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.QuickTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.StartTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchemaBase.AmmoTableSchemaBase;

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

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    private RequestSerializer rsc;

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
        //rsc = RequestSerializer.newInstance();
    }

    protected void tearDown() throws Exception
    {
        // ...
    }

    // =========================================================
    // 
    // test methods
    // 
    // =========================================================

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
        Uri uri = null;
        Provider p1 = new Provider(uri);

        Parcel par = utilCreatePayloadParcel();
        Payload  p2 = new Payload(par);

        // Provider.Type.URI, Payload.Type.CV
        RequestSerializer rs = RequestSerializer.newInstance(p1,p2);
        assertNotNull(rs);
    }

    // =========================================================
    // serialize from ContentValues (JSON encoding)
    // =========================================================
    public void testSerializeFromContentValuesJSON()
    {
        ContentValues cv = utilCreateContentValues();

        RequestSerializer rs = RequestSerializer.newInstance();
        //assertNotNull(rs);

        // JSON encoding
        Encoding encJson = Encoding.newInstance(Encoding.Type.JSON);
        byte[] rval = RequestSerializer.serializeFromContentValues(cv, encJson);

        assertTrue(true);
    }

    // =========================================================
    // serialize from ContentValues (terse encoding)
    // =========================================================
    public void testSerializeFromContentValuesTerse()
    {
        ContentValues cv = utilCreateContentValues();

        RequestSerializer rs = RequestSerializer.newInstance();
        //assertNotNull(rs);

        // Terse encoding
        Encoding encTerse = Encoding.newInstance(Encoding.Type.TERSE);
        byte[] rval = RequestSerializer.serializeFromContentValues(cv, encTerse);

        assertTrue(true);
    }

    /**
     * serialize from ContentProvider (JSON encoding)
     * 
     * This test 
     * <ol>
     * <li>constructs a mock content provider,
     * <li>loads some data into the content provider,(imitating the application)
     * <li>serializes that data into a json string
     * <li>clears the database (imitating the network transmission to a different device)
     * <li>deserializes back into the content provider,
     * <li>checks that the result in the database is as expected.
     * </ol>
     * This procedure is repeated with different cases.
     */
    public void testSerializeFromProviderJson()
    {
        final Context context = getContext();
        final AmmoMockProvider01 provider = AmmoMockProvider01.getInstance(context);
        final SQLiteDatabase db = provider.getDatabase();

        final ContentValues cv = new ContentValues();
        final int sampleForeignKey = -1;
        cv.put(AmmoTableSchema.A_FOREIGN_KEY_REF, sampleForeignKey);
        cv.put(AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION, AmmoTableSchema.AN_EXCLUSIVE_ENUMERATION_HIGH);
        cv.put(AmmoTableSchema.AN_INCLUSIVE_ENUMERATION, AmmoTableSchema.AN_INCLUSIVE_ENUMERATION_APPLE);
        final long rowid = db.insert(Tables.AMMO_TBL, AmmoTableSchemaBase.A_FOREIGN_KEY_REF, cv);

        final RequestSerializer rs = RequestSerializer.newInstance();
        assertNotNull(rs);

        final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

        final MockContentResolver cr = new MockContentResolver();
        cr.addProvider(AmmoMockSchema01.AUTHORITY, provider);

        final Uri baseUri = AmmoTableSchema.CONTENT_URI;
        final Uri tupleUri = ContentUris.withAppendedId(baseUri, rowid);

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
            Assert.fail("failure of the test itself");
            return;
        }

        final String jsonString;
        try {
            jsonString = new String(jsonBlob, "US-ASCII");
        } catch (UnsupportedEncodingException ex) {
            Assert.fail(new StringBuilder().
                    append("could not convert json blob to string").append(jsonBlob).
                    append(" with exception ").append(ex.getLocalizedMessage()).
                    toString());
            return;
        }
        final JSONObject json;
        try {
            json = new JSONObject(jsonString);
        } catch (JSONException ex) {
            Assert.fail(new StringBuilder().
                    append("could not parse json blob ").append(jsonBlob).
                    append(" with exception ").append(ex.getLocalizedMessage()).
                    toString());
        }
        logger.info("encoded json=[{}]", jsonString);
        Assert.assertEquals("the enumeration changed", "foo", "foo");
        
        // assertFalse("a bad foreign key", );
        
        final long count = db.delete(Tables.AMMO_TBL, new StringBuilder().
                append(AmmoTableSchema._ID).append(" = ?").
                toString(),
                new String[]{ String.valueOf(rowid) } );
        Assert.assertTrue("did not delete the record added", (count == 1));

        final Uri tupleIn;
        tupleIn = RequestSerializer.deserializeToProvider(context, "dummy", tupleUri, enc, jsonBlob);
        Assert.assertEquals("foo", tupleIn.toString());

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
        assertFalse("failed cursor", (cursor == null));
        assertTrue("empty cursor", (cursor.getCount() == 1));
        assertTrue("could not get first tuple", cursor.moveToFirst());
        assertTrue("a mis-decoded foreign key", 
                (sampleForeignKey == cursor.getInt(cursor.getColumnIndex(AmmoTableSchema.A_FOREIGN_KEY_REF))));
        
        
    }

    public void testFail()
    {
        //fail("RS failure");
        //assertTrue(true);
    }

    // =========================================================
    // 
    // utility methods to assist testing
    // 
    // =========================================================

    private Parcel utilCreatePayloadParcel()
    {
        return null;
    }

    private ContentValues utilCreateContentValues()
    {
        ContentValues cv = new ContentValues();
        cv.put("foo", "bar");
        return cv;
    }


    private MockContentResolver utilGetContentResolver()
    {
        final MockContentResolver mcr = new MockContentResolver();
        mcr.addProvider(AmmoMockSchema01.AUTHORITY, 
                AmmoMockProvider01.getInstance(getContext()));

        return mcr;
    }



}

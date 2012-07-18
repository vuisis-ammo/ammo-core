
package edu.vu.isis.ammo.core;

import android.test.AndroidTestCase;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import edu.vu.isis.ammo.core.distributor.RequestSerializer;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;

import android.net.Uri;
import android.content.ContentValues;
import android.os.Parcel;


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

    public void testNewInstanceNoArgs()
    {
	RequestSerializer rs = RequestSerializer.newInstance();
        assertNotNull(rs);
    }

    public void testNewInstanceArgs()
    {
	Uri uri = null;
	Provider p1 = new Provider(uri);
	
	Parcel par = null;
	Payload  p2 = new Payload(par);

	// Provider.Type.URI, Payload.Type.CV
	RequestSerializer rs = RequestSerializer.newInstance(p1,p2);
        assertNotNull(rs);
    }
    
    public void testSerializeFromContentValues()
    {
	//TLogger.TEST_LOG.info("testSerializeFromContentValues");

	ContentValues cv = new ContentValues();
	cv.put("foo", "bar");

	RequestSerializer rs = RequestSerializer.newInstance();
        //assertNotNull(rs);

	// JSON encoding
	Encoding encJson = Encoding.newInstance(Encoding.Type.JSON);
	
	byte[] rval = RequestSerializer.serializeFromContentValues(cv, encJson);
	
	// Terse encoding
	assertTrue(true);
    }

    public void testFail()
    {
	//fail("RS failure");
	//assertTrue(true);
    }

}

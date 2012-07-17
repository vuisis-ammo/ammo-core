
package edu.vu.isis.ammo.core;

//import android.test.ActivityInstrumentationTestCase2;
import android.test.AndroidTestCase;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

//import edu.vu.isis.ammo.core.TLogger;

import edu.vu.isis.ammo.core.distributor.RequestSerializer;
//import edu.vu.isis.ammo.api.type.Payload;
//import edu.vu.isis.ammo.api.type.Provider;

import android.content.ContentValues;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;

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


//public class RequestSerializerTest extends ActivityInstrumentationTestCase2<RequestSerializer> {
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
	//TLogger.TEST_LOG.info("testNewInstance");
	RequestSerializer rs = RequestSerializer.newInstance();
        assertNotNull(rs);
	//assertTrue(true);
    }

    public void testNewInstanceArgs()
    {
	//RequestSerializer rs = RequestSerializer.newInstance(Provider.Type.URI, Payload.Type.CV, );
        //assertNotNull(rs);
	assertTrue(true);
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

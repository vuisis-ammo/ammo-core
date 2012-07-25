
package edu.vu.isis.ammo.core;

import android.test.AndroidTestCase;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for the Payload API class 
 * 
 * Use this class as a template to create new Ammo unit tests
 * for classes which use Android-specific components.
 * 
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class edu.vu.isis.ammo.core.PayloadTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 */

import edu.vu.isis.ammo.api.type.Payload;
import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;


public class PayloadTest extends AndroidTestCase 
{
    public PayloadTest() 
    {
    }

    public PayloadTest( String testName )
    {
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( PayloadTest.class );
    }

    /**
     * Called before every test
     */
    protected void setUp() throws Exception
    {
	// ...
    }

    /**
     * Called after every test
     */
    protected void tearDown() throws Exception
    {
	// ...
    }

    /**
     * Test methods
     */
    public void testConstructorWithParcel()
    {
	//Parcel par = null;
	//Payload p = new Payload(par);
	//assertNotNull(p);
    }

    public void testConstructorWithString()
    {
	final String in = "foo";
	Payload p = new Payload(in);
	assertNotNull(p);

	// Need some Payload public accessors to examine content
	// e.g.
	// assertTrue(p.getString() == in);
    }
    
    public void testConstructorWithByteArray()
    {
	byte[] ba = new byte[10];
	Payload p = new Payload(ba);
	assertNotNull(p);
    }

    public void testConstructorWithContentValues()
    {
	ContentValues cv = new ContentValues();
	cv.put("ammo", "great");
	Payload p = new Payload(cv);
	assertNotNull(p);
    }
    
    public void testReadFromParcel()
    {
	// Test case of passing in a null Parcel (should return null)
	Parcel p1 = null;
	Payload rv1 = Payload.readFromParcel(p1);
	assertTrue(rv1 == null);
	
	// Pass in a non-null Parcel (should return non-null)
	//Parcel p2 = new Parcel(...);
	//Payload rv2 = Payload.readFromParcel(p2);
	//assertNotNull(rv2);
    }
    
    public void testWhatContent()
    {
	// Type STR
	Payload p1 = new Payload("foo");
	assertTrue(p1.whatContent() == Payload.Type.STR);
	
	// Type BYTE
	byte[] ba = new byte[10];
	Payload p2 = new Payload(ba);
	assertNotNull(p2);
	assertTrue(p2.whatContent() == Payload.Type.BYTE);

	// Type CV
	ContentValues cv = new ContentValues();
	cv.put("ammo", "great");
	Payload p3 = new Payload(cv);
	assertNotNull(p3);
	assertTrue(p3.whatContent() == Payload.Type.CV);
    }

    public void testAsBytes()
    {
	// Construct a payload from byte array
	byte[] ba = new byte[10];
	Payload p = new Payload(ba);
	assertNotNull(p);
	assertTrue(p.whatContent() == Payload.Type.BYTE);
	
	// Make sure the returned byte array is same as original
	assertTrue(Arrays.equals(ba, p.asBytes()));
    }

    public void testGetCV()
    {
	// cv to initialize with
	ContentValues cv = new ContentValues();
	cv.put("foo", "bar");
	
	// Construct a payload with the cv
	Payload p = new Payload(cv);
	assertNotNull(p);
	assertTrue(p.whatContent() == Payload.Type.CV);

	// Check that retrieved cv is same as original
	assertTrue(p.getCV() == cv);
    }
}

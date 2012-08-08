
package edu.vu.isis.ammo.api.type;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;
import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;
import android.test.AndroidTestCase;

/**
 * Unit test for Payload API class
 * <p>
 * Use this class as a template to create new Ammo unit tests for classes which
 * use Android-specific components.
 * <p>
 * To run this test, you can type: <code>
 * adb shell am instrument -w \
 * -e class edu.vu.isis.ammo.core.TopicTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 * </code>
 */


public class PayloadTest extends AndroidTestCase 
{
    final static private Logger logger = LoggerFactory.getLogger("trial.api.type.payload");
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

    /**
     * All the tests expect equivalence to work correctly.
     * So we best verify that equivalence works.
     */
    public void testEquivalence() {
        Assert.assertEquals("a none is equal to itself", Payload.NONE, Payload.NONE);
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

    /**
     * Test case of passing in a null Parcel 
     * - should throw a null pointer exception
     */
    public void testNullParcel() {
        /**
         * Test case of passing in a null Parcel 
         * - should throw a null pointer exception
         */
        boolean success = false;
        try {
            final Parcel p1 = null;
            Payload.readFromParcel(p1);

        } catch (NullPointerException ex) {
            success = true;
        }
        Assert.assertTrue("passing a null reference should fail", success);
    }

    /**
     * Generate a non-null Parcel containing a null payload
     * When unmarshalled this produces a NONE payload.
     * - should return non-null
     */
    public void testNullContentParcel() {
        {
            final Payload expected = null;
            final Parcel parcel = Parcel.obtain();
            Payload.writeToParcel(expected, parcel, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            parcel.setDataPosition(0);
            final Payload actual = Payload.CREATOR.createFromParcel(parcel);
            Assert.assertEquals("wrote a null but got something else", actual, Payload.NONE);
        }
    }
    /**
     * Generate a non-null Parcel containing a simple string payload
     * - should return non-null
     */
    public void testParcel() {
        ((ch.qos.logback.classic.Logger) logger).setLevel(Level.TRACE);
        
        final Parcel parcel1 = Parcel.obtain();
        final Parcel parcel2 = Parcel.obtain();
        try {
            final Payload expected = new Payload("an arbitrary Payload");
            Payload.writeToParcel(expected, parcel1, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            final byte[] expectedBytes = parcel1.marshall();
            // Assert.assertEquals(4, bytes[0]);
            parcel2.unmarshall(expectedBytes, 0, expectedBytes.length);
            parcel2.setDataPosition(0);
            final Payload actual = Payload.readFromParcel(parcel2);
            Assert.assertNotNull("wrote something but got a null back", actual);
            Assert.assertEquals("did not get back an equivalent Payload", expected, actual);
        } finally {
            parcel1.recycle();
            parcel2.recycle();
        }
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

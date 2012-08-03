
package edu.vu.isis.ammo.api.type;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;
import android.os.Parcel;
import android.os.Parcelable;
import android.test.AndroidTestCase;

/**
 * Unit test for Topic API class 
 * 
 * Use this class as a template to create new Ammo unit tests
 * for classes which use Android-specific components.
 * 
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class edu.vu.isis.ammo.core.TopicTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 */
// [IMPORT AMMO CLASS(ES) TO BE TESTED HERE]

public class TopicTest extends AndroidTestCase 
{
    public TopicTest() 
    {
    }

    public TopicTest( String testName )
    {
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( TopicTest.class );
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
     * Test case of passing in a null Parcel 
     * - should throw a null pointer exception
     */
    public void testParcel()
    {
        /**
         * Test case of passing in a null Parcel 
         * - should throw a null pointer exception
         */
        boolean success = false;
        try {
            final Parcel p1 = null;
            Topic.readFromParcel(p1);
            
        } catch (NullPointerException ex) {
            success = true;
        }
        Assert.assertTrue("passing a null reference should fail", success);

        /**
         * Pass in a non-null Parcel containing a null Topic
         * - should return non-null
         */
        {
            final Topic expected = null;
            final Parcel parcel = Parcel.obtain();
            Topic.writeToParcel(expected, parcel, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            final Topic actual = Topic.CREATOR.createFromParcel(parcel);
            Assert.assertNull("wrote a null but got something else back", actual);
        }
        /**
         * Pass in a non-null Parcel 
         * - should return non-null
         */
        {
            final Topic expected = new Topic("an arbitrary topic");
            final Parcel parcel = Parcel.obtain();
            Topic.writeToParcel(expected, parcel, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            final Topic actual = Topic.CREATOR.createFromParcel(parcel);
            Assert.assertNotNull("wrote something but got a null back", actual);
            Assert.assertEquals("did not get back an equivalent topic", expected, actual);
        }
    }

    public void testConstructorWithString()
    {
        final String in = "foo";
        Topic t = new Topic(in);
        assertNotNull(t);

        // Need some Topic public accessors to examine content
        // e.g.
        // assertTrue(t.getString() == in);
    }

    public void testReadFromParcel()
    {
        // Test case of passing in a null Parcel (should return null)
        Parcel p1 = null;
        Topic rv1 = Topic.readFromParcel(p1);
        assertTrue(rv1 == null);

        // Pass in a non-null Parcel (should return non-null)
        //Parcel p2 = new Parcel(...);
        //Topic rv2 = Topic.readFromParcel(p2);
        //assertNotNull(rv2);
    }
}

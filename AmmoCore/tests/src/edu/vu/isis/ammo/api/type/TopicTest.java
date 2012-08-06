
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
     * All the tests expect equivalence to work correctly.
     * So we best verify that equivalence works.
     */
    public void testEquivalence() {
        final Topic first = new Topic("this is a string");
        final Topic second = new Topic("this is a differenct string");
        Assert.assertEquals("an object should be equal to itself", first, first);
        Assert.assertFalse("an objects which are not equal", first.equals(second));
    }



    /**
     * Test case of passing in a null Parcel 
     * - should throw a null pointer exception
     */
    public void testNullParcel() {
        boolean success = false;
        try {
            final Parcel p1 = null;
            Topic.readFromParcel(p1);

        } catch (NullPointerException ex) {
            success = true;
        }
        Assert.assertTrue("passing a null reference should fail", success);
    }

    /**
     * Generate a non-null Parcel containing a null Topic
     * When unmarshalled this produces a NONE Topic.
     * - should return non-null
     */
    public void testNullContentParcel() {
        final Topic expected = null;
        final Parcel parcel = Parcel.obtain();
        Topic.writeToParcel(expected, parcel, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        parcel.setDataPosition(0);
        final Topic actual = Topic.CREATOR.createFromParcel(parcel);
        Assert.assertEquals("wrote a null expecting a NONE but got something else back", actual, Topic.NONE);
    }
    /**
     * Generate a non-null Parcel containing a simple string Topic
     * - should return non-null
     */
    public void testParcel() {
        final Parcel parcel1 = Parcel.obtain();
        final Parcel parcel2 = Parcel.obtain();
        try {
            final Topic expected = new Topic("an arbitrary Topic");
            Topic.writeToParcel(expected, parcel1, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            final byte[] bytes = parcel1.marshall();
            // Assert.assertEquals(4, bytes[0]);
            parcel2.unmarshall(bytes, 0, bytes.length);
            parcel2.setDataPosition(0);
            final Topic actual = Topic.CREATOR.createFromParcel(parcel2);
            Assert.assertNotNull("wrote something but got a null back", actual);
            // Assert.assertEquals("did not get back an equivalent Topic", expected, actual);
        } finally {
            parcel1.recycle();
            parcel2.recycle();
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

}

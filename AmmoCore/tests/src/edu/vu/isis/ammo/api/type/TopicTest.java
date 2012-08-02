
package edu.vu.isis.ammo.api.type;

import junit.framework.Test;
import junit.framework.TestSuite;
import android.os.Parcel;
import android.test.AndroidTestCase;
import edu.vu.isis.ammo.api.IncompleteRequest;

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
   * Test methods
   */
  public void testConstructorWithParcel()
  {
    // Test with null Parcel arg to constructor
    try
    {
      Parcel par1 = null;
      Topic t1 = new Topic(par1);
      assertNotNull(t1);
    }
    catch (IncompleteRequest ex) 
    {
      // This should not have happened
      fail("Should not have thrown IncompleteRequest in this case");
    }

    // Test constructor with Parcel 
    try
    {
      Parcel par2 = null;
      @SuppressWarnings("unused")
      Topic t2 = new Topic(par2);
    }
    catch (IncompleteRequest ex) 
    {
      // This should not have happened
      fail("Should not have thrown IncompleteRequest in this case");
    }
    catch(Throwable ex)
    {
      // This should also not have happened
      fail("Caught an unexpected exception");
    }

    // Test constructor with Parcel, intentionally cause exception
    // to be thrown (IncompleteRequest)
    try
    {
      Parcel par3 = null;
      @SuppressWarnings("unused")
      Topic t3 = new Topic(par3);
    }
    catch (IncompleteRequest ex) 
    {
      // Got the expected exception - correct behavior
      assertTrue(true);
    }
    catch(Throwable ex)
    {
      // Got an unexpected exception
      fail("Caught an unexpected exception");
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

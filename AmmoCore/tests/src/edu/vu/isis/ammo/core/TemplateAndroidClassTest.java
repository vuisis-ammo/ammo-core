
package edu.vu.isis.ammo.core;

import android.test.AndroidTestCase;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Template unit test for a plain Java class 
 * 
 * Use this class as a template to create new Ammo unit tests
 * for classes which use Android-specific components.
 * 
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class edu.vu.isis.ammo.core.TemplateAndroidClassTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 */

// [IMPORT AMMO CLASS(ES) TO BE TESTED HERE]

public class TemplateAndroidClassTest extends AndroidTestCase 
{
    public TemplateAndroidClassTest() 
    {
    }

    public TemplateAndroidClassTest( String testName )
    {
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( TemplateAndroidClassTest.class );
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
    public void testNumberOne()
    {
	assertTrue(true);
    }
    
    public void testNumberTwo()
    {
	assertTrue(true);
    }
}

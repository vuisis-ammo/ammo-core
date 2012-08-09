
package edu.vu.isis.ammo.core;

import android.test.AndroidTestCase;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for DistributorPolicy
 * 
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class edu.vu.isis.ammo.core.DistributorPolicyTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 */

import edu.vu.isis.ammo.core.distributor.DistributorPolicy;

public class DistributorPolicyTest extends AndroidTestCase 
{
    public DistributorPolicyTest() 
    {
    }

    public DistributorPolicyTest( String testName )
    {
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( DistributorPolicyTest.class );
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

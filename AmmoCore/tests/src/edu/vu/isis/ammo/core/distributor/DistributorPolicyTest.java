
package edu.vu.isis.ammo.core.distributor;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.test.AndroidTestCase;
import ch.qos.logback.classic.Level;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Category;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Topic;

/**
 * Unit test for DistributorPolicy To run this test, you can type: adb shell am
 * instrument -w \ -e class edu.vu.isis.ammo.core.DistributorPolicyTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 */

public class DistributorPolicyTest extends AndroidTestCase
{
    private static final Logger logger = LoggerFactory.getLogger("test.policy.routing");

    private DistributorPolicy policy;

    public DistributorPolicyTest()
    {
    }

    public DistributorPolicyTest(String testName)
    {
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite(DistributorPolicyTest.class);
    }

    /**
     * Called before every test
     */
    protected void setUp() throws Exception
    {
        this.policy = DistributorPolicy.newInstance(getContext());
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
     * This test verifies that the default policy file works as intended.
     */
    public void testDefaultMatchExactInterstitial()
    {
        ((ch.qos.logback.classic.Logger) logger).setLevel(Level.TRACE);

        final String topicName = "ammo/transapps.pli.locations";
        final Topic expected = this.policy
                .newBuilder()
                .newRouting(
                        Category.POSTAL,
                        0, DistributorDataStore.DEFAULT_POSTAL_LIFESPAN)
                .addClause()
                .addLiteral("serial", true, Encoding.TERSE)
                .addLiteral("multicast", true, Encoding.JSON)
                .addLiteral("gateway", true, Encoding.JSON)
                .build();

        expected.setType(topicName);

        final Topic actual = this.policy.matchPostal(topicName);
        assertEquals("topic does not match", expected, actual);

        ((ch.qos.logback.classic.Logger) logger).setLevel(Level.OFF);
    }

    public void testNumberTwo()
    {
        assertTrue(true);
    }
}

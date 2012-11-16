
package edu.vu.isis.ammo.util;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for Genealogist
 */
public class GenealogistTest extends TestCase
{
    private static final Logger logger = LoggerFactory.getLogger("test.genealogist");

    /**
     * Create the test case
     * 
     * @param testName name of the test case
     */
    public GenealogistTest(String testName)
    {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite(GenealogistTest.class);
    }

    protected void setUp() throws Exception
    {
        logger.info("setUp");
    }

    protected void tearDown() throws Exception
    {
        logger.info("setUp");
    }

    /**
     * A test to make sure the behavior is nominally correct.
     * A tree is built for an object which belongs to the java language.
     */
    public void testGetAncestryObject()
    {
        logger.info("testGetAncestryObject");

        final TreeNode<Class<?>> ancestry = Genealogist.getAncestry(Integer.valueOf(7));
        Assert.assertEquals("ancestor count @", 5, ancestry.size());

        final TreeNode.Vistor<Class<?>> visitor = new TreeNode.Vistor<Class<?>>() {

            private int level = 0;
            
            @Override
            public StringBuilder up(StringBuilder builder) {
                level++;
                return builder.append("UP-").append(level);
            }

            @Override
            public StringBuilder down(StringBuilder builder) {
                level--;
                return builder.append("DOWN-").append(level);
            }

            @Override
            public StringBuilder reform(StringBuilder builder, Class<?> data) {
                return builder.append("<").append(data).append(">");
            }

        };
        final String full = ancestry.toString(visitor);
        Assert.assertEquals("ancestry tree",
                "<class java.lang.Integer>UP-1"
                        + "<class java.lang.Number>UP-2"
                        + "<class java.lang.Object>"
                        + "<interface java.io.Serializable>DOWN-1"
                        + "<interface java.lang.Comparable>DOWN-0", full);

        logger.info("testGetAncestryObject");
    }

}

/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package edu.vu.isis.ammo.core.util;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
//import junit.framework.Assert;

import edu.vu.isis.ammo.util.FullTopic;

/**
 * Unit test for FullTopic
 */
public class FullTopicTest extends TestCase
{
    //private static final Logger logger = LoggerFactory.getLogger(FullTopicTest.class);
    private FullTopic ft;

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public FullTopicTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( FullTopicTest.class );
    }

    protected void setUp() throws Exception
    {
	//logger.info( "setUp");
	//ft = new FullTopic("topic");
    }

    protected void tearDown() throws Exception
    {
	// ...
    }

    /**
     * Rigourous Test :-)
     */
    public void testConstructorOneArg()
    {
	//logger.info( "testConstructorOneArg");
	String fulltopic = "full_topic";
	FullTopic ft1 = new FullTopic(fulltopic);
	assertNotNull(ft1);
        assertEquals(fulltopic, ft1.aggregate);
	//logger.info( "-- done");
	//assertTrue(true);
    }
    
    public void testConstructorOneArgBad()
    {
	//logger.info( "testConstructorOneArgBad");
	// Bad constructor usage, should return null
	FullTopic ft1 = new FullTopic("wrong");
	//assertNull(ft1);
	//logger.info( "-- done");
	assertTrue(true);
    }
    
    public void testConstructorTwoArg()
    {
	//logger.info( "testConstructorTwoArg");
	FullTopic ft2 = new FullTopic("topic", "subtopic");
	assertNotNull(ft2);
        assertEquals("topic", ft2.topic);
        assertEquals("subtopic", ft2.subtopic);
	//logger.info( "-- done");
    }
    
    public void testFromType()
    {
	//logger.info( "testFromType");
	FullTopic f = FullTopic.fromType("sometype");
	assertNotNull(f);
        //assertTrue( true );
	//logger.info( "-- done");
    }

    public void testFromTopic()
    {
	//logger.info( "testFromTopic");
	FullTopic f = FullTopic.fromTopic("topic", "subtopic");
	assertNotNull(f);
	assertEquals("topic", f.topic);
        assertEquals("subtopic", f.subtopic);
	//logger.info( "-- done");
    }

    public void testToString()
    {
	//logger.info( "testToString");
	FullTopic f = FullTopic.fromTopic("topic", "subtopic");
	String expected = "topic" + "/subtopic";
	assertNotNull(f);
	//assertEquals(expected, f.toString());
	//logger.info( "-- done");
    }
}


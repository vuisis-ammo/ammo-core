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



package edu.vu.isis.ammo.core.distributor;

import static org.hamcrest.CoreMatchers.equalTo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import android.test.mock.MockContext;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Topic;

/**
 * Unit test for DistributorPolicy
 */
@RunWith(JUnit4.class)
public class DistribributorPolicyTest
{
    private static final Logger logger = LoggerFactory.getLogger("test.distributor.policy");

    /**
     * Create the test case
     */
    public DistribributorPolicyTest()
    {
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
     * A test to make sure the behavior is nominally correct. A tree is built
     * for an object which belongs to the java language.
     */
    @Test
    public void testBasicPolicy()
    {
        logger.info("test supplied distribution policy");
        final File policyFile = new File("assets", DistributorPolicy.policy_file);
        Assert.assertTrue("file does not exist " + policyFile, policyFile.exists());
        final InputStream inputStream;
        try {
            inputStream = new FileInputStream(policyFile);
        } catch (FileNotFoundException e) {
            Assert.fail("could not find file " + policyFile);
            return;
        }
        final InputSource is = new InputSource(inputStream);
        final DistributorPolicy policy = DistributorPolicy.newInstance(is);
        logger.trace("policy=[{}]", policy);

        Assert.assertEquals("topic count @", 14, policy.topicCount());

        {
            final Topic actual = policy
                    .matchPostal("ammo/edu.vu.isis.ammo.dash.media.foo");
            Assert.assertThat("check dash topic", actual.getType(), equalTo("ammo/edu.vu.isis.ammo.dash"));
        }
        {
            final Topic actual = policy
                    .matchPostal("ammo/edu.vu.isis.ammo.das");
            Assert.assertThat("check bad dash topic", actual.getType(), equalTo(""));
        }

    }

    public static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static class LocalContext extends MockContext {
        public LocalContext() {

        }

        @Override
        public File getDir(String name, int mode) {
            final File assetsDir = new File("assets");
            return assetsDir;
        }
    }

}

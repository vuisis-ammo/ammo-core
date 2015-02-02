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



package edu.vu.isis.ammo.api;

import junit.framework.Test;
import junit.framework.TestSuite;
import android.content.ContentValues;
import android.test.AndroidTestCase;

/**
 * Unit test for AmmoValues API class
 * <p>
 * Use this class as a template to create new Ammo unit tests for classes which
 * use Android-specific components.
 * <p>
 * To run this test, you can type: <code>
 * adb shell am instrument -w \
 * -e class edu.vu.isis.ammo.core.AmmoValuesTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 * </code>
 */

public class AmmoValuesTest extends AndroidTestCase
{
    public AmmoValuesTest()
    {
    }

    public AmmoValuesTest(String testName)
    {
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite(AmmoValuesTest.class);
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

    // =========================================================
    //
    // test methods
    //
    // =========================================================

    public void testConstructor()
    {
        AmmoValues av = new AmmoValues();
        assertNotNull(av);
    }

    public void testConstructorWithSize()
    {
        final int size = 10;
        AmmoValues av = new AmmoValues(size);
        assertNotNull(av);
    }

    public void testConstructorWithContentValues()
    {
        ContentValues cv = new ContentValues();
        cv.put("ammo", "great");
        AmmoValues av = new AmmoValues(cv);
        assertNotNull(av);

        // TODO: [test is failing] assertTrue(av.equals(cv));
        assertEquals(cv.size(), av.size());
        assertTrue(av.containsKey("ammo"));
    }

    public void testClearCV()
    {
        ContentValues cv = new ContentValues();
        cv.put("ammo", "great"); // cv size == 1
        final int size = 1;
        AmmoValues av = new AmmoValues(cv);
        assertNotNull(av);
        assertEquals(size, av.size());

        // Clear and check that size is now zero
        av.clear();
        assertEquals(0, av.size());
        assertFalse(av.containsKey("ammo"));
    }

    public void testContainsKey()
    {
        // Construct a CV object with known contents
        ContentValues cv = new ContentValues();
        cv.put("ammo", "great");
        AmmoValues av = new AmmoValues(cv);
        assertNotNull(av);

        // Verify that it contains the key we added
        assertTrue(av.containsKey("ammo"));
    }

    public void testRemoveKey()
    {
        ContentValues cv = new ContentValues();
        cv.put("ammo", "great");

        // Construct a CV object with known contents
        // Case 0: size=0
        AmmoValues av0 = new AmmoValues(cv);
        assertNotNull(av0);
        av0.remove("ammo");
        assertFalse(av0.containsKey("ammo"));
        assertEquals(0, av0.size());

        // Case 1 : size=1
        AmmoValues av1 = new AmmoValues(cv);
        assertNotNull(av1);
        av1.remove("ammo");
        assertFalse(av1.containsKey("ammo"));
        assertEquals(0, av1.size());

        // Case 2: size=2
        cv.put("foo", "bar");
        AmmoValues av2 = new AmmoValues(cv);
        assertNotNull(av2);
        av2.remove("ammo");
        assertFalse(av2.containsKey("ammo"));
        assertEquals(1, av2.size());
    }

}

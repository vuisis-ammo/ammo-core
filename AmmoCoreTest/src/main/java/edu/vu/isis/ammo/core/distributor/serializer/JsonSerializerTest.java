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



package edu.vu.isis.ammo.core.distributor.serializer;

import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;

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
 * <code>
  adb shell am instrument \
  -w edu.vu.isis.ammo.core.tests/pl.polidea.instrumentation.PolideaInstrumentationTestRunner \
  -e class edu.vu.isis.ammo.core.TemplateAndroidClassTest 
 */

import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.provider.AmmoMockProvider01;
import edu.vu.isis.ammo.provider.AmmoMockProviderBase;
import edu.vu.isis.ammo.provider.AmmoMockProviderBase.Tables;
import edu.vu.isis.ammo.provider.AmmoMockSchema01;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.AmmoTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.QuickTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchema01.StartTableSchema;
import edu.vu.isis.ammo.provider.AmmoMockSchemaBase.AmmoTableSchemaBase;

import edu.vu.isis.ammo.core.distributor.NonConformingAmmoContentProvider;
import edu.vu.isis.ammo.core.distributor.TupleNotFoundException;

import android.net.Uri;
import android.util.Log;
import java.io.IOException;


public class JsonSerializerTest extends AndroidTestCase 
{
    private static final String TAG = "JsonSerializerTest";


    public JsonSerializerTest() 
    {
    }

    public JsonSerializerTest( String testName )
    {
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( JsonSerializerTest.class );
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
    public void testSerialize()
    {
	try {
	    Uri uri = null;
	    MockContentResolver cr = utilGetContentResolver();
	    final Encoding enc = Encoding.newInstance(Encoding.Type.JSON);
	    ContentProviderContentItem cpc = new ContentProviderContentItem(uri, cr, enc);

	    JsonSerializer x = new JsonSerializer();
	    assertNotNull(x);
	    
	    byte[] y = x.serialize(cpc);
	    assertNotNull(y);
	    
	} catch (NonConformingAmmoContentProvider e) {
	    Log.e(TAG, "foo");
	    fail("unexpected NonConformingAmmoContentProvider exception");
	} catch (TupleNotFoundException e) {
	    Log.e(TAG, "bar");
	    fail("unexpected TupleNotFoundException");
	} catch (IOException e) {
	    Log.e(TAG, "baz");
	    fail("unexpected IOException");
	}
    }
    
    public void testDeserialize()
    {
	assertTrue(true);
    }

    private MockContentResolver utilGetContentResolver()
    {
        final MockContentResolver mcr = new MockContentResolver();
        mcr.addProvider(AmmoMockSchema01.AUTHORITY,
                        AmmoMockProvider01.getInstance(getContext()));

        return mcr;
    }
}

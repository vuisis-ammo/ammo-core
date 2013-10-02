
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

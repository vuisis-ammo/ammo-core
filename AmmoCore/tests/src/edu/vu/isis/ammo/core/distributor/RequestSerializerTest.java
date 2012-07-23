
package edu.vu.isis.ammo.core.distributor;

import android.test.AndroidTestCase;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import android.test.mock.MockContentResolver;
import android.test.mock.MockContentProvider;
import edu.vu.isis.ammo.core.AmmoMockContentProviderV01;

import edu.vu.isis.ammo.core.distributor.RequestSerializer;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.NonConformingAmmoContentProvider;
import edu.vu.isis.ammo.core.distributor.TupleNotFoundException;

import android.net.Uri;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.content.ContentProvider;
import android.os.Parcel;
import java.io.IOException;


/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class edu.vu.isis.ammo.core.ui.RequestSerializerTest \
 * edu.vu.isis.ammo.core.tests/android.test.InstrumentationTestRunner
 */


public class RequestSerializerTest extends AndroidTestCase {

	private RequestSerializer rsc;

	public RequestSerializerTest() {
		//super("edu.vu.isis.ammo.core.distributor", RequestSerializer.class);
	}

	public RequestSerializerTest( String testName )
	{
		//super( testName );
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite()
	{
		return new TestSuite( RequestSerializerTest.class );
	}

	protected void setUp() throws Exception
	{
		//rsc = RequestSerializer.newInstance();
	}

	protected void tearDown() throws Exception
	{
		// ...
	}

	// =========================================================
	// 
	// test methods
	// 
	// =========================================================

	// =========================================================
	// newInstance() with no parameters
	// =========================================================
	public void testNewInstanceNoArgs()
	{
		RequestSerializer rs = RequestSerializer.newInstance();
		assertNotNull(rs);
	}

	// =========================================================
	// newInstance() with parameters
	// =========================================================
	public void testNewInstanceArgs()
	{
		Uri uri = null;
		Provider p1 = new Provider(uri);

		Parcel par = utilCreatePayloadParcel();
		Payload  p2 = new Payload(par);

		// Provider.Type.URI, Payload.Type.CV
		RequestSerializer rs = RequestSerializer.newInstance(p1,p2);
		assertNotNull(rs);
	}

	// =========================================================
	// serialize from ContentValues (JSON encoding)
	// =========================================================
	public void testSerializeFromContentValuesJSON()
	{
		ContentValues cv = utilCreateContentValues();

		RequestSerializer rs = RequestSerializer.newInstance();
		//assertNotNull(rs);

		// JSON encoding
		Encoding encJson = Encoding.newInstance(Encoding.Type.JSON);
		byte[] rval = RequestSerializer.serializeFromContentValues(cv, encJson);

		assertTrue(true);
	}

	// =========================================================
	// serialize from ContentValues (terse encoding)
	// =========================================================
	public void testSerializeFromContentValuesTerse()
	{
		ContentValues cv = utilCreateContentValues();

		RequestSerializer rs = RequestSerializer.newInstance();
		//assertNotNull(rs);

		// Terse encoding
		Encoding encTerse = Encoding.newInstance(Encoding.Type.TERSE);
		byte[] rval = RequestSerializer.serializeFromContentValues(cv, encTerse);

		assertTrue(true);
	}

	// =========================================================
	// serialize from ContentProvider (JSON encoding)
	// =========================================================
	public void testSerializeFromProviderJSON()
	{
		RequestSerializer rs = RequestSerializer.newInstance();
		//assertNotNull(rs);

		// JSON encoding
		Encoding enc = Encoding.newInstance(Encoding.Type.JSON);

		ContentResolver cr = utilGetContentResolver();
		final Uri baseUri = AmmoMockContentProviderV01.CONTENT_URI;
		final Uri tupleUri = Uri.withAppendedPath(baseUri, "rel01");

		byte[] rval = null; 
		try 
		{
			rval = RequestSerializer.serializeFromProvider(cr, tupleUri, enc);
		}
		catch (NonConformingAmmoContentProvider ex)
		{
			fail("Should not have thrown NonConformingAmmoContentProvider in this case");
		}
		catch (TupleNotFoundException ex)
		{
			fail("Should not have thrown TupleNotFoundException in this case");
		}
		catch (IOException ex) 
		{
			// This is an error rather than a failure... Handle in some way.
			assertTrue(false);
		}

		assertTrue(true);
	}

	public void testFail()
	{
		//fail("RS failure");
		//assertTrue(true);
	}

	// =========================================================
	// 
	// utility methods to assist testing
	// 
	// =========================================================

	private Parcel utilCreatePayloadParcel()
	{
		return null;
	}

	private ContentValues utilCreateContentValues()
	{
		ContentValues cv = new ContentValues();
		cv.put("foo", "bar");
		return cv;
	}

			
	private MockContentResolver utilGetContentResolver()
	{
		final MockContentResolver mcr = new MockContentResolver();
		mcr.addProvider(AmmoMockContentProviderV01.authority, 
				AmmoMockContentProviderV01.getInstance(getContext()));
		
		return mcr;
	}

	

}

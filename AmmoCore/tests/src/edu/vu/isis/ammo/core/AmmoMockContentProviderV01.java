package edu.vu.isis.ammo.core;

import android.content.Context;
import android.net.Uri;
import android.test.mock.MockContentProvider;

/**
 * 
 */

/**
 * @author phreed
 *
 */
public class AmmoMockContentProviderV01 extends MockContentProvider {
	
	public static String authority = "content://edu.vu.isis.ammo/ammogen_test";
	public static Uri CONTENT_URI = Uri.parse(authority);

	private AmmoMockContentProviderV01(Context context) {
		super(context);
	}
	public static MockContentProvider getInstance(Context context)
	{
		final MockContentProvider mcp = new AmmoMockContentProviderV01(context);
		return mcp;
	}
	
	
	
}

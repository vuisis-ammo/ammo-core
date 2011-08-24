package edu.vu.isis.ammo.core;

import java.util.HashMap;


/**
 * Definitions for mime-type to mime-type-id mapping.
 *
 */
public class AmmoMimeTypes {
	public static final HashMap<Integer, String> mimeTypes;
	public static final HashMap<String, Integer> mimeIds;

    static
    {
        mimeTypes = new HashMap<Integer, String>();
        mimeIds = new HashMap<String, Integer>();

        mimeTypes.put( 1, "application/vnd.edu.vu.isis.ammo.sms.message" );
        mimeIds.put( "application/vnd.edu.vu.isis.ammo.sms.message",   1 );

        mimeTypes.put( 2, "application/vnd.com.aterrasys.nevada.locations" );
        mimeIds.put( "application/vnd.com.aterrasys.nevada.locations", 2 );
    }
}

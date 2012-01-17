/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
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

        mimeTypes.put( 1, "ammo/edu.vu.isis.ammo.sms.message" );
        mimeIds.put( "ammo/edu.vu.isis.ammo.sms.message",   1 );

        mimeTypes.put( 2, "ammo/com.aterrasys.nevada.locations" );
        mimeIds.put( "ammo/com.aterrasys.nevada.locations", 2 );

        mimeTypes.put( 3, "ammo/edu.vu.isis.ammo.dash.event" );
        mimeIds.put( "ammo/edu.vu.isis.ammo.dash.event", 3 );
        
        // NO blob, no media
        // mimeTypes.put( 4, "ammo/edu.vu.isis.ammo.dash.media" );
        // mimeIds.put( "ammo/edu.vu.isis.ammo.dash.media", 4 );

	// NOTE: The following is a Rochester-specific workaround for
	// the issue of static/dynamic MIME types serialized over the
	// serial channel (relevant for SMS, obviously).
    // a/e.v.i.a.sms.message_ta152-1  == 128
    // a/e.v.i.a.sms.message_ta152-2  == 129   
	String smsMimeBase = "ammo/edu.vu.isis.ammo.sms.message_ta152-";
	for (int i=64; i < 94; i++) {	    
	    //String smsMime = smsMimeBase;
	    //smsMime.concat(String.valueOf(i-63));
	    mimeTypes.put( i, smsMimeBase + String.valueOf(i-63) );
	    mimeIds.put( smsMimeBase + String.valueOf(i-63),  i );
	}

    }
}

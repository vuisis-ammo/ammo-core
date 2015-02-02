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
		
        mimeTypes.put( 4, "ammo/transapps.chat.message_groupAll" );
        mimeIds.put( "ammo/transapps.chat.message_groupAll", 4 );
        
        mimeTypes.put( 5, "ammo/transapps.pli.locations" );
        mimeIds.put( "ammo/transapps.pli.locations", 5 );

        mimeTypes.put( 6, "ammo/transapps.pli.group_locations" );
        mimeIds.put( "ammo/transapps.pli.group_locations", 6 );

        mimeTypes.put( 7, "ammo/transapps.chat.media_groupAll" );
        mimeIds.put( "ammo/transapps.chat.media_groupAll", 7 ); // we are supporting small media attachments
        
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

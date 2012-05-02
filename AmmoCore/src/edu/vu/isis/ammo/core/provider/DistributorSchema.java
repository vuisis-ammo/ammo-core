/*
Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.core.provider;

import java.util.HashMap;
import java.util.Map;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;
import edu.vu.isis.ammo.core.distributor.store.Tables;

public class DistributorSchema implements BaseColumns {
	
	public static final String AUTHORITY = "edu.vu.isis.ammo.core.provider.distributorprovider";
	
	  /**
	    * The content:// style URL for this table
	    * This Uri is a Map containing all table Uris contained in the distributor database.
	    * The map is indexed by DistributorSchema.Tables.<TABLE_NAME>.n where n is the 
	    * name of the enum declared in Tables.
	    */
	   public static final Map<String, Uri> CONTENT_URI;
	   //= 
	     // Uri.parse("content://"+AUTHORITY+"/distributor");
	   static {
		  CONTENT_URI = new HashMap<String, Uri>();
		  for (Tables table : Tables.values()) {
			   CONTENT_URI.put(table.n, Uri.parse("content://"+AUTHORITY+"/"+table.n));
		   }
	   }
	   
	   /**
	    * Special URI that when queried, returns a cursor to the number of 
	    * queued messages per channel (where each channel is in a separate row)
	    */
	   
	   
	   /**
	    * The MIME type of {@link #CONTENT_URI} providing a directory
	    */
	   public static final String CONTENT_TYPE =
	      ContentResolver.CURSOR_DIR_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.distributor";
	   
	   /**
	    * A mime type used for publisher subscriber.
	    */
	   public static final String CONTENT_TOPIC =
	      "ammo/edu.vu.isis.ammo.core.distributor";
	   
	   /**
	    * The MIME type of a {@link #CONTENT_URI} sub-directory of a single media entry.
	    */
	   public static final String CONTENT_ITEM_TYPE = 
	      ContentResolver.CURSOR_ITEM_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.distributor";
	   
	   public static final String DEFAULT_SORT_ORDER = ""; //"modified_date DESC";
	   
	
}




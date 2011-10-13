package edu.vu.isis.ammo.core.provider;

import java.util.HashMap;
import java.util.Map;

import android.content.ContentResolver;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;
import edu.vu.isis.ammo.util.BaseDateColumns;

public class DistributorSchema implements BaseColumns, BaseDateColumns {
	
	public static final String AUTHORITY = "edu.vu.isis.ammo.core.provider.distributorprovider";
	public static final String DATABASE_NAME = DistributorDataStore.Tables.NAME;

	
	
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




package edu.vu.isis.ammo.core.distributor;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.IntentFilter;
import android.content.UriMatcher;
import android.content.Context;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.Cursor;

import android.net.Uri;
import android.os.Environment;
import android.os.FileObserver;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;


import edu.vu.isis.ammo.core.provider.DistributorProvider.Tables;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PublicationTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;


public class DistributorDataStore {


// Table definitions 
public interface Tables {
      public static final String POSTAL_TBL = "postal";
      public static final String RETRIEVAL_TBL = "retrieval";
      public static final String PUBLICATION_TBL = "publication";
      public static final String SUBSCRIPTION_TBL = "subscription";
      
}

   public static final String DATABASE_NAME = "distributor.db";

   public enum Disposition {
	   START = 0,
	   SEND = 1,
	   RECV = 2,
	   COMPLETE = 3};


public static final String[] POSTAL_CURSOR_COLUMNS = new String[] {
  PostalTableSchema.CP_TYPE ,
     PostalTableSchema.URI ,
     PostalTableSchema.NOTICE ,
     PostalTableSchema.PRIORITY ,
     PostalTableSchema.SERIALIZE_TYPE ,
     PostalTableSchema.DISPOSITION ,
     PostalTableSchema.EXPIRATION ,
     PostalTableSchema.UNIT ,
     PostalTableSchema.VALUE ,
     PostalTableSchema.DATA ,
     PostalTableSchema.CREATED_DATE ,
     PostalTableSchema.MODIFIED_DATE 
};

public static class PostalTableSchema implements Columns {
   protected PostalTableSchema() {} // No instantiation.
   
   /**
    * The content:// style URL for this table
    */
   public static final Uri CONTENT_URI =
      Uri.parse("content://"+AUTHORITY+"/postal");

   public static Uri getUri(Cursor cursor) {
     Integer id = cursor.getInt(cursor.getColumnIndex(Columns._ID));
     return  Uri.withAppendedPath(PostalTableSchema.CONTENT_URI, id.toString());
   }
   
   /**
    * The MIME type of {@link #CONTENT_URI} providing a directory
    */
   public static final String CONTENT_TYPE =
      ContentResolver.CURSOR_DIR_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.postal";
   
   /**
    * A mime type used for publisher subscriber.
    */
   public static final String CONTENT_TOPIC =
      "application/vnd.edu.vu.isis.ammo.core.postal";
   
   /**
    * The MIME type of a {@link #CONTENT_URI} sub-directory of a single postal entry.
    */
   public static final String CONTENT_ITEM_TYPE = 
      ContentResolver.CURSOR_ITEM_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.postal";
   
   
   public static final String DEFAULT_SORT_ORDER = ""; //"modified_date DESC";
   

      /** 
      * Description: This is used for post requests.
          This along with the cost is used to decide how to deliver the specific object.
      * <P>Type: TEXT</P> 
      */
          public static final String CP_TYPE = "cp_type";
      
      /** 
      * Description: URI of the data to be distributed.
      * <P>Type: TEXT</P> 
      */
          public static final String URI = "uri";
      
      /** 
      * Description: A description of what is to be done when various state-transistions occur.
      * <P>Type: BLOB</P> 
      */
          public static final String NOTICE = "notice";
      
      /** 
      * Description: What order should this message be sent.
           Negative priorities indicated less than normal.
      * <P>Type: INTEGER</P> 
      */
          public static final String PRIORITY = "priority";
      
      /** 
      * Description: Indicates if the uri indicates a table or whether the data has been preserialized.
             DIRECT : the serialized data is found in the data field (or a suitable file).
             INDIRECT : the serialized data is obtained from the named uri.
             DEFERRED : the same as INDIRECT but the serialization doesn't happen until the data is sent.
      * <P>Type: EXCLUSIVE</P> 
      */
              public static final int SERIALIZE_TYPE_DIRECT = 1;
                 public static final int SERIALIZE_TYPE_INDIRECT = 2;
                 public static final int SERIALIZE_TYPE_DEFERRED = 3;
            
         public static final String SERIALIZE_TYPE = "serialize_type";
      
      /** 
      * Description: Status of the entry (sent or not sent).
      * <P>Type: EXCLUSIVE</P> 
      */
              public static final int DISPOSITION_PENDING = 1;
                 public static final int DISPOSITION_QUEUED = 2;
                 public static final int DISPOSITION_SENT = 3;
                 public static final int DISPOSITION_JOURNAL = 4;
                 public static final int DISPOSITION_FAIL = 5;
                 public static final int DISPOSITION_EXPIRED = 6;
                 public static final int DISPOSITION_SATISFIED = 7;
            
         public static final String DISPOSITION = "disposition";
      
      /** 
      * Description: Timestamp at which point entry becomes stale.
      * <P>Type: INTEGER</P> 
      */
          public static final String EXPIRATION = "expiration";
      
      /** 
      * Description: Units associated with {@link #VALUE}. Used to determine whether should occur.
      * <P>Type: TEXT</P> 
      */
          public static final String UNIT = "unit";
      
      /** 
      * Description: Arbitrary value linked to importance that entry is 
          transmitted and battery drain.
      * <P>Type: INTEGER</P> 
      */
          public static final String VALUE = "value";
      
      /** 
      * Description: If the If null then the data file corresponding to the column name and record id should be used.
           This is done when the data size is larger than that allowed for a field contents.
      * <P>Type: TEXT</P> 
      */
          public static final String DATA = "data";
      
      /** 
      * Description: 
      * <P>Type: LONG</P> 
      */
          public static final String CREATED_DATE = "created_date";
      
      /** 
      * Description: 
      * <P>Type: LONG</P> 
      */
          public static final String MODIFIED_DATE = "modified_date";
   
   public static final String _RECEIVED_DATE = "_received_date";


} 
public static final String[] RETRIEVAL_CURSOR_COLUMNS = new String[] {
  RetrievalTableSchema.DISPOSITION ,
     RetrievalTableSchema.NOTICE ,
     RetrievalTableSchema.PRIORITY ,
     RetrievalTableSchema.URI ,
     RetrievalTableSchema.MIME ,
     RetrievalTableSchema.PROJECTION ,
     RetrievalTableSchema.SELECTION ,
     RetrievalTableSchema.ARGS ,
     RetrievalTableSchema.ORDERING ,
     RetrievalTableSchema.CONTINUITY ,
     RetrievalTableSchema.CONTINUITY_VALUE ,
     RetrievalTableSchema.EXPIRATION ,
     RetrievalTableSchema.CREATED_DATE ,
     RetrievalTableSchema.MODIFIED_DATE 
};

public static class RetrievalTableSchema implements Columns {
   protected RetrievalTableSchema() {} // No instantiation.
   
   /**
    * The content:// style URL for this table
    */
   public static final Uri CONTENT_URI =
      Uri.parse("content://"+AUTHORITY+"/retrieval");

   public static Uri getUri(Cursor cursor) {
     Integer id = cursor.getInt(cursor.getColumnIndex(Columns._ID));
     return  Uri.withAppendedPath(RetrievalTableSchema.CONTENT_URI, id.toString());
   }
   
   /**
    * The MIME type of {@link #CONTENT_URI} providing a directory
    */
   public static final String CONTENT_TYPE =
      ContentResolver.CURSOR_DIR_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.retrieval";
   
   /**
    * A mime type used for publisher subscriber.
    */
   public static final String CONTENT_TOPIC =
      "application/vnd.edu.vu.isis.ammo.core.retrieval";
   
   /**
    * The MIME type of a {@link #CONTENT_URI} sub-directory of a single retrieval entry.
    */
   public static final String CONTENT_ITEM_TYPE = 
      ContentResolver.CURSOR_ITEM_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.retrieval";
   
   
   public static final String DEFAULT_SORT_ORDER = ""; //"modified_date DESC";
   

      /** 
      * Description: Status of the entry (sent or not sent).
      * <P>Type: EXCLUSIVE</P> 
      */
              public static final int DISPOSITION_PENDING = 1;
                 public static final int DISPOSITION_QUEUED = 2;
                 public static final int DISPOSITION_SENT = 3;
                 public static final int DISPOSITION_JOURNAL = 4;
                 public static final int DISPOSITION_FAIL = 5;
                 public static final int DISPOSITION_EXPIRED = 6;
                 public static final int DISPOSITION_SATISFIED = 7;
            
         public static final String DISPOSITION = "disposition";
      
      /** 
      * Description: A description of what is to be done when various state-transistions occur.
      * <P>Type: BLOB</P> 
      */
          public static final String NOTICE = "notice";
      
      /** 
      * Description: What order should this message be sent.
           Negative priorities indicated less than normal.
      * <P>Type: INTEGER</P> 
      */
          public static final String PRIORITY = "priority";
      
      /** 
      * Description: URI target for the data to be pulled.
      * <P>Type: TEXT</P> 
      */
          public static final String URI = "uri";
      
      /** 
      * Description: mime type of the data to be pulled.
      * <P>Type: TEXT</P> 
      */
          public static final String MIME = "mime";
      
      /** 
      * Description: The fields/columns wanted.
      * <P>Type: TEXT</P> 
      */
          public static final String PROJECTION = "projection";
      
      /** 
      * Description: The rows/tuples wanted.
      * <P>Type: TEXT</P> 
      */
          public static final String SELECTION = "selection";
      
      /** 
      * Description: The values using in the selection.
      * <P>Type: TEXT</P> 
      */
          public static final String ARGS = "args";
      
      /** 
      * Description: The order the values are to be returned in.
      * <P>Type: TEXT</P> 
      */
          public static final String ORDERING = "ordering";
      
      /** 
      * Description: Continuity indicates whether this is a...
             - one time rerieval or
             - should continue for some period of time
             - should be limited to a specific quantity of elements
      * <P>Type: EXCLUSIVE</P> 
      */
              public static final int CONTINUITY_ONCE = 1;
                 public static final int CONTINUITY_TEMPORAL = 2;
                 public static final int CONTINUITY_QUANTITY = 3;
            
         public static final String CONTINUITY = "continuity";
      
      /** 
      * Description: The meaning changes based on the continuity type.
             - ONCE : undefined
             - TEMPORAL : chronic  
             - QUANTITY : the maximum number of objects to return
      * <P>Type: INTEGER</P> 
      */
          public static final String CONTINUITY_VALUE = "continuity_value";
      
      /** 
      * Description: Timestamp at which point entry becomes stale.
           This is different than a temporal continuity. (I believe?)
      * <P>Type: INTEGER</P> 
      */
          public static final String EXPIRATION = "expiration";
      
      /** 
      * Description: 
      * <P>Type: LONG</P> 
      */
          public static final String CREATED_DATE = "created_date";
      
      /** 
      * Description: 
      * <P>Type: LONG</P> 
      */
          public static final String MODIFIED_DATE = "modified_date";
      
}

public static final String[] PUBLICATION_CURSOR_COLUMNS = new String[] {
  PublicationTableSchema.DISPOSITION ,
     PublicationTableSchema.URI ,
     PublicationTableSchema.MIME ,
     PublicationTableSchema.EXPIRATION ,
     PublicationTableSchema.CREATED_DATE ,
     PublicationTableSchema.MODIFIED_DATE 
};

public static class PublicationTableSchema implements Columns {
   protected PublicationTableSchema() {} // No instantiation.
   
   /**
    * The content:// style URL for this table
    */
   public static final Uri CONTENT_URI =
      Uri.parse("content://"+AUTHORITY+"/publication");

   public static Uri getUri(Cursor cursor) {
     Integer id = cursor.getInt(cursor.getColumnIndex(Columns._ID));
     return  Uri.withAppendedPath(PublicationTableSchema.CONTENT_URI, id.toString());
   }
   
   /**
    * The MIME type of {@link #CONTENT_URI} providing a directory
    */
   public static final String CONTENT_TYPE =
      ContentResolver.CURSOR_DIR_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.publication";
   
   /**
    * A mime type used for publisher subscriber.
    */
   public static final String CONTENT_TOPIC =
      "application/vnd.edu.vu.isis.ammo.core.publication";
   
   /**
    * The MIME type of a {@link #CONTENT_URI} sub-directory of a single publication entry.
    */
   public static final String CONTENT_ITEM_TYPE = 
      ContentResolver.CURSOR_ITEM_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.publication";
   
   
   public static final String DEFAULT_SORT_ORDER = ""; //"modified_date DESC";
   

      /** 
      * Description: Status of the entry (sent or not sent).
      * <P>Type: EXCLUSIVE</P> 
      */
              public static final int DISPOSITION_PENDING = 1;
                 public static final int DISPOSITION_QUEUED = 2;
                 public static final int DISPOSITION_SENT = 3;
                 public static final int DISPOSITION_JOURNAL = 4;
                 public static final int DISPOSITION_FAIL = 5;
                 public static final int DISPOSITION_EXPIRED = 6;
            
         public static final String DISPOSITION = "disposition";
      
      /** 
      * Description: URI target for the data to be pulled.
      * <P>Type: TEXT</P> 
      */
          public static final String URI = "uri";
      
      /** 
      * Description: mime type of the data to be pulled.
      * <P>Type: TEXT</P> 
      */
          public static final String MIME = "mime";
      
      /** 
      * Description: Timestamp at which point entry becomes stale.
      * <P>Type: INTEGER</P> 
      */
          public static final String EXPIRATION = "expiration";
      
      /** 
      * Description: 
      * <P>Type: LONG</P> 
      */
          public static final String CREATED_DATE = "created_date";
      
      /** 
      * Description: 
      * <P>Type: LONG</P> 
      */
          public static final String MODIFIED_DATE = "modified_date";
      

   public static final String _DISPOSITION = "_disp"; 
   
   public static final String _RECEIVED_DATE = "_received_date";

// BEGIN CUSTOM PUBLICATION_SCHEMA PROPERTIES
// END   CUSTOM PUBLICATION_SCHEMA PROPERTIES
} 
public static final String[] SUBSCRIPTION_CURSOR_COLUMNS = new String[] {
  SubscriptionTableSchema.DISPOSITION ,
     SubscriptionTableSchema.URI ,
     SubscriptionTableSchema.MIME ,
     SubscriptionTableSchema.SELECTION ,
     SubscriptionTableSchema.EXPIRATION ,
     SubscriptionTableSchema.NOTICE ,
     SubscriptionTableSchema.PRIORITY ,
     SubscriptionTableSchema.CREATED_DATE ,
     SubscriptionTableSchema.MODIFIED_DATE 
};

public static class SubscriptionTableSchema implements Columns {
   protected SubscriptionTableSchema() {} // No instantiation.
   
   /**
    * The content:// style URL for this table
    */
   public static final Uri CONTENT_URI =
      Uri.parse("content://"+AUTHORITY+"/subscription");

   public static Uri getUri(Cursor cursor) {
     Integer id = cursor.getInt(cursor.getColumnIndex(Columns._ID));
     return  Uri.withAppendedPath(SubscriptionTableSchema.CONTENT_URI, id.toString());
   }
   
   /**
    * The MIME type of {@link #CONTENT_URI} providing a directory
    */
   public static final String CONTENT_TYPE =
      ContentResolver.CURSOR_DIR_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.subscription";
   
   /**
    * A mime type used for publisher subscriber.
    */
   public static final String CONTENT_TOPIC =
      "application/vnd.edu.vu.isis.ammo.core.subscription";
   
   /**
    * The MIME type of a {@link #CONTENT_URI} sub-directory of a single subscription entry.
    */
   public static final String CONTENT_ITEM_TYPE = 
      ContentResolver.CURSOR_ITEM_BASE_TYPE+"/vnd.edu.vu.isis.ammo.core.subscription";
   
   
   public static final String DEFAULT_SORT_ORDER = ""; //"modified_date DESC";
   

      /** 
      * Description: Status of the entry (sent or not sent).
      * <P>Type: EXCLUSIVE</P> 
      */
              public static final int DISPOSITION_PENDING = 1;
                 public static final int DISPOSITION_QUEUED = 2;
                 public static final int DISPOSITION_SENT = 3;
                 public static final int DISPOSITION_JOURNAL = 4;
                 public static final int DISPOSITION_FAIL = 5;
                 public static final int DISPOSITION_EXPIRED = 6;
            
         public static final String DISPOSITION = "disposition";
      
      /** 
      * Description: URI target for the data to be pulled.
      * <P>Type: TEXT</P> 
      */
          public static final String URI = "uri";
      
      /** 
      * Description: mime type of the data to be pulled.
      * <P>Type: TEXT</P> 
      */
          public static final String MIME = "mime";
      
      /** 
      * Description: The rows/tuples wanted.
      * <P>Type: TEXT</P> 
      */
          public static final String SELECTION = "selection";
      
      /** 
      * Description: Timestamp at which point entry becomes stale.
      * <P>Type: INTEGER</P> 
      */
          public static final String EXPIRATION = "expiration";
      
      /** 
      * Description: A description of what is to be done when various state-transistions occur.
      * <P>Type: BLOB</P> 
      */
          public static final String NOTICE = "notice";
      
      /** 
      * Description: What order should this message be sent.
           Negative priorities indicated less than normal.
      * <P>Type: INTEGER</P> 
      */
          public static final String PRIORITY = "priority";
      
      /** 
      * Description: 
      * <P>Type: LONG</P> 
      */
          public static final String CREATED_DATE = "created_date";
      
      /** 
      * Description: 
      * <P>Type: LONG</P> 
      */
          public static final String MODIFIED_DATE = "modified_date";
      

   public static final String _DISPOSITION = "_disp"; 
   
   public static final String _RECEIVED_DATE = "_received_date";

// BEGIN CUSTOM SUBSCRIPTION_SCHEMA PROPERTIES
// END   CUSTOM SUBSCRIPTION_SCHEMA PROPERTIES
} 


} 
package edu.vu.isis.ammo.core.provider;

import android.provider.Columns;

public class DistributorSchema extends DistributorSchema {

   public static final int DATABASE_VERSION = 6;

      public static class PostalTableSchema extends PostalTableSchema {

         protected PostalTableSchema() { super(); }

         public static final String PRIORITY_SORT_ORDER = 
        	 Columns._ID + " ASC";
               //PostalTableSchema.EXPIRATION + " DESC, " +
               //PostalTableSchema.MODIFIED_DATE + " DESC ";
      }    
    
      public static class RetrievalTableSchema extends RetrievalTableSchema {

         protected RetrievalTableSchema() { super(); }

         public static final String PRIORITY_SORT_ORDER = 
        	 Columns._ID + " ASC";
             //  RetrievalTableSchema.EXPIRATION + " DESC, " +
             //  RetrievalTableSchema.MODIFIED_DATE + " DESC ";
      }    
      public static class PublicationTableSchema extends PublicationTableSchema {

         protected PublicationTableSchema() { super(); }

         public static final String PRIORITY_SORT_ORDER = 
        	 Columns._ID + " ASC";
             //  PublicationTableSchema.EXPIRATION + " DESC, " +
             //  PublicationTableSchema.MODIFIED_DATE + " DESC ";
      }    
      public static class SubscriptionTableSchema extends SubscriptionTableSchema {

         protected SubscriptionTableSchema() { super(); }

         public static final String PRIORITY_SORT_ORDER = 
        	 Columns._ID + " ASC";
             //  SubscriptionTableSchema.EXPIRATION + " DESC, " +
             //  SubscriptionTableSchema.MODIFIED_DATE + " DESC ";
      }    
      
}

// Views.
public interface Views {
   // Nothing to put here yet.
}

protected class DistributorDatabaseHelper extends SQLiteOpenHelper {
   // ===========================================================
   // Constants
   // ===========================================================
   private final Logger logger = LoggerFactory.getLogger(DistributorDatabaseHelper.class);
   
   // ===========================================================
   // Fields
   // ===========================================================
   
   /** Nothing to put here */
   
   
   // ===========================================================
   // Constructors
   // ===========================================================
   public DistributorDatabaseHelper(Context context) {
      super(context, DistributorSchema.DATABASE_NAME, 
               null, DistributorSchema.DATABASE_VERSION);
   }
   
   
   // ===========================================================
   // SQLiteOpenHelper Methods
   // ===========================================================

   @Override
   public void onCreate(SQLiteDatabase db) {
      logger.info( "Bootstrapping database");
      try {

        /** 
         * Table Name: postal <P>
         */
        db.execSQL("CREATE TABLE \"" + Tables.POSTAL_TBL + "\" (" 
          + "\""+PostalTableSchema.CP_TYPE + "\" TEXT, " 
          + "\""+PostalTableSchema.URI + "\" TEXT, " 
          + "\""+PostalTableSchema.NOTICE + "\" BLOB, " 
          + "\""+PostalTableSchema.PRIORITY + "\" INTEGER, " 
          + "\""+PostalTableSchema.SERIALIZE_TYPE + "\" INTEGER, " 
          + "\""+PostalTableSchema.DISPOSITION + "\" INTEGER, " 
          + "\""+PostalTableSchema.EXPIRATION + "\" INTEGER, " 
          + "\""+PostalTableSchema.UNIT + "\" TEXT, " 
          + "\""+PostalTableSchema.VALUE + "\" INTEGER, " 
          + "\""+PostalTableSchema.DATA + "\" TEXT, " 
          + "\""+PostalTableSchema.CREATED_DATE + "\" INTEGER, " 
          + "\""+PostalTableSchema.MODIFIED_DATE + "\" INTEGER, " 
          + "\""+PostalTableSchema._ID + "\" INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "\""+PostalTableSchema._RECEIVED_DATE + "\" LONG, "
          + "\""+PostalTableSchema._DISPOSITION + "\" INTEGER );" ); 
        /** 
         * Table Name: retrieval <P>
         */
        db.execSQL("CREATE TABLE \"" + Tables.RETRIEVAL_TBL + "\" (" 
          + "\""+RetrievalTableSchema.DISPOSITION + "\" INTEGER, " 
          + "\""+RetrievalTableSchema.NOTICE + "\" BLOB, " 
          + "\""+RetrievalTableSchema.PRIORITY + "\" INTEGER, " 
          + "\""+RetrievalTableSchema.URI + "\" TEXT, " 
          + "\""+RetrievalTableSchema.MIME + "\" TEXT, " 
          + "\""+RetrievalTableSchema.PROJECTION + "\" TEXT, " 
          + "\""+RetrievalTableSchema.SELECTION + "\" TEXT, " 
          + "\""+RetrievalTableSchema.ARGS + "\" TEXT, " 
          + "\""+RetrievalTableSchema.ORDERING + "\" TEXT, " 
          + "\""+RetrievalTableSchema.CONTINUITY + "\" INTEGER, " 
          + "\""+RetrievalTableSchema.CONTINUITY_VALUE + "\" INTEGER, " 
          + "\""+RetrievalTableSchema.EXPIRATION + "\" INTEGER, " 
          + "\""+RetrievalTableSchema.CREATED_DATE + "\" INTEGER, " 
          + "\""+RetrievalTableSchema.MODIFIED_DATE + "\" INTEGER, " 
          + "\""+RetrievalTableSchema._ID + "\" INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "\""+RetrievalTableSchema._RECEIVED_DATE + "\" LONG, "
          + "\""+RetrievalTableSchema._DISPOSITION + "\" INTEGER );" ); 
        /** 
         * Table Name: publication <P>
         */
        db.execSQL("CREATE TABLE \"" + Tables.PUBLICATION_TBL + "\" (" 
          + "\""+PublicationTableSchema.DISPOSITION + "\" INTEGER, " 
          + "\""+PublicationTableSchema.URI + "\" TEXT, " 
          + "\""+PublicationTableSchema.MIME + "\" TEXT, " 
          + "\""+PublicationTableSchema.EXPIRATION + "\" INTEGER, " 
          + "\""+PublicationTableSchema.CREATED_DATE + "\" INTEGER, " 
          + "\""+PublicationTableSchema.MODIFIED_DATE + "\" INTEGER, " 
          + "\""+PublicationTableSchema._ID + "\" INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "\""+PublicationTableSchema._RECEIVED_DATE + "\" LONG, "
          + "\""+PublicationTableSchema._DISPOSITION + "\" INTEGER );" ); 
        /** 
         * Table Name: subscription <P>
         */
        db.execSQL("CREATE TABLE \"" + Tables.SUBSCRIPTION_TBL + "\" (" 
          + "\""+SubscriptionTableSchema.DISPOSITION + "\" INTEGER, " 
          + "\""+SubscriptionTableSchema.URI + "\" TEXT, " 
          + "\""+SubscriptionTableSchema.MIME + "\" TEXT, " 
          + "\""+SubscriptionTableSchema.SELECTION + "\" TEXT, " 
          + "\""+SubscriptionTableSchema.EXPIRATION + "\" INTEGER, " 
          + "\""+SubscriptionTableSchema.NOTICE + "\" BLOB, " 
          + "\""+SubscriptionTableSchema.PRIORITY + "\" INTEGER, " 
          + "\""+SubscriptionTableSchema.CREATED_DATE + "\" INTEGER, " 
          + "\""+SubscriptionTableSchema.MODIFIED_DATE + "\" INTEGER, " 
          + "\""+SubscriptionTableSchema._ID + "\" INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "\""+SubscriptionTableSchema._RECEIVED_DATE + "\" LONG, "
          + "\""+SubscriptionTableSchema._DISPOSITION + "\" INTEGER );" ); 

        preloadTables(db);
        createViews(db);
        createTriggers(db);
             
        } catch (SQLException ex) {
           ex.printStackTrace();
        }
   }

   @Override
   public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      logger.warn( "Upgrading database from version {} to {} which will destroy all old data",
            oldVersion, newVersion);
         db.execSQL("DROP TABLE IF EXISTS \"" + Tables.POSTAL_TBL + "\";");
            db.execSQL("DROP TABLE IF EXISTS \"" + Tables.RETRIEVAL_TBL + "\";");
            db.execSQL("DROP TABLE IF EXISTS \"" + Tables.PUBLICATION_TBL + "\";");
            db.execSQL("DROP TABLE IF EXISTS \"" + Tables.SUBSCRIPTION_TBL + "\";");
      
      onCreate(db);
   }
   
   // ===========================================================
   // Database Creation Helper Methods
   // ===========================================================

   /**
    * Can be overriden to cause tables to be loaded
    */
   protected void preloadTables(SQLiteDatabase db) { }

   /** View creation */
   protected void createViews(SQLiteDatabase db) { }

   /** Trigger creation */
   protected void createTriggers(SQLiteDatabase db) { }
}

/**
* Table Name: postal <P>
*/
static public class PostalWrapper {
   public PostalWrapper() {
      logger.trace("building PostalWrapper");
   }
     private String cpType;
     public String getCpType() {
       return this.cpType;
     }
     public PostalWrapper setCpType(String val) {
       this.cpType = val;
       return this;
     } 
     private String uri;
     public String getUri() {
       return this.uri;
     }
     public PostalWrapper setUri(String val) {
       this.uri = val;
       return this;
     } 
     private byte[] notice;
     public byte[] getNotice() {
       return this.notice;
     }
     public PostalWrapper setNotice(byte[] val) {
       this.notice = val;
       return this;
     } 
     private int priority;
     public int getPriority() {
       return this.priority;
     }
     public PostalWrapper setPriority(int val) {
       this.priority = val;
       return this;
     } 
     private int serializeType;
     public int getSerializeType() {
       return this.serializeType;
     }
     public PostalWrapper setSerializeType(int val) {
       this.serializeType = val;
       return this;
     } 
     private int disposition;
     public int getDisposition() {
       return this.disposition;
     }
     public PostalWrapper setDisposition(int val) {
       this.disposition = val;
       return this;
     } 
     private int expiration;
     public int getExpiration() {
       return this.expiration;
     }
     public PostalWrapper setExpiration(int val) {
       this.expiration = val;
       return this;
     } 
     private String unit;
     public String getUnit() {
       return this.unit;
     }
     public PostalWrapper setUnit(String val) {
       this.unit = val;
       return this;
     } 
     private int value;
     public int getValue() {
       return this.value;
     }
     public PostalWrapper setValue(int val) {
       this.value = val;
       return this;
     } 
     private String data;
     public String getData() {
       return this.data;
     }
     public PostalWrapper setData(String val) {
       this.data = val;
       return this;
     } 
     private long createdDate;
     public long getCreatedDate() {
       return this.createdDate;
     }
     public PostalWrapper setCreatedDate(long val) {
       this.createdDate = val;
       return this;
     } 
     private long modifiedDate;
     public long getModifiedDate() {
       return this.modifiedDate;
     }
     public PostalWrapper setModifiedDate(long val) {
       this.modifiedDate = val;
       return this;
     } 
     private int _disposition;
     public int get_Disposition() {
       return this._disposition;
     }
     public PostalWrapper set_Disposition(int val) {
       this._disposition = val;
       return this;
     }
     private long _received_date;
     public long get_ReceivedDate() {
         return this._received_date;
     }
     public PostalWrapper set_ReceivedDate(long val) {
         this._received_date = val;
         return this;
     }
} 
/**
* Table Name: retrieval <P>
*/
static public class RetrievalWrapper {
   public RetrievalWrapper() {
      logger.trace("building RetrievalWrapper");
   }
     private int disposition;
     public int getDisposition() {
       return this.disposition;
     }
     public RetrievalWrapper setDisposition(int val) {
       this.disposition = val;
       return this;
     } 
     private byte[] notice;
     public byte[] getNotice() {
       return this.notice;
     }
     public RetrievalWrapper setNotice(byte[] val) {
       this.notice = val;
       return this;
     } 
     private int priority;
     public int getPriority() {
       return this.priority;
     }
     public RetrievalWrapper setPriority(int val) {
       this.priority = val;
       return this;
     } 
     private String uri;
     public String getUri() {
       return this.uri;
     }
     public RetrievalWrapper setUri(String val) {
       this.uri = val;
       return this;
     } 
     private String mime;
     public String getMime() {
       return this.mime;
     }
     public RetrievalWrapper setMime(String val) {
       this.mime = val;
       return this;
     } 
     private String projection;
     public String getProjection() {
       return this.projection;
     }
     public RetrievalWrapper setProjection(String val) {
       this.projection = val;
       return this;
     } 
     private String selection;
     public String getSelection() {
       return this.selection;
     }
     public RetrievalWrapper setSelection(String val) {
       this.selection = val;
       return this;
     } 
     private String args;
     public String getArgs() {
       return this.args;
     }
     public RetrievalWrapper setArgs(String val) {
       this.args = val;
       return this;
     } 
     private String ordering;
     public String getOrdering() {
       return this.ordering;
     }
     public RetrievalWrapper setOrdering(String val) {
       this.ordering = val;
       return this;
     } 
     private int continuity;
     public int getContinuity() {
       return this.continuity;
     }
     public RetrievalWrapper setContinuity(int val) {
       this.continuity = val;
       return this;
     } 
     private int continuity_value;
     public int getContinuity_value() {
       return this.continuity_value;
     }
     public RetrievalWrapper setContinuity_value(int val) {
       this.continuity_value = val;
       return this;
     } 
     private int expiration;
     public int getExpiration() {
       return this.expiration;
     }
     public RetrievalWrapper setExpiration(int val) {
       this.expiration = val;
       return this;
     } 
     private long createdDate;
     public long getCreatedDate() {
       return this.createdDate;
     }
     public RetrievalWrapper setCreatedDate(long val) {
       this.createdDate = val;
       return this;
     } 
     private long modifiedDate;
     public long getModifiedDate() {
       return this.modifiedDate;
     }
     public RetrievalWrapper setModifiedDate(long val) {
       this.modifiedDate = val;
       return this;
     } 
     private int _disposition;
     public int get_Disposition() {
       return this._disposition;
     }
     public RetrievalWrapper set_Disposition(int val) {
       this._disposition = val;
       return this;
     }
     private long _received_date;
     public long get_ReceivedDate() {
         return this._received_date;
     }
     public RetrievalWrapper set_ReceivedDate(long val) {
         this._received_date = val;
         return this;
     }
} 
/**
* Table Name: publication <P>
*/
static public class PublicationWrapper {
   public PublicationWrapper() {
      logger.trace("building PublicationWrapper");
   }
     private int disposition;
     public int getDisposition() {
       return this.disposition;
     }
     public PublicationWrapper setDisposition(int val) {
       this.disposition = val;
       return this;
     } 
     private String uri;
     public String getUri() {
       return this.uri;
     }
     public PublicationWrapper setUri(String val) {
       this.uri = val;
       return this;
     } 
     private String mime;
     public String getMime() {
       return this.mime;
     }
     public PublicationWrapper setMime(String val) {
       this.mime = val;
       return this;
     } 
     private int expiration;
     public int getExpiration() {
       return this.expiration;
     }
     public PublicationWrapper setExpiration(int val) {
       this.expiration = val;
       return this;
     } 
     private long createdDate;
     public long getCreatedDate() {
       return this.createdDate;
     }
     public PublicationWrapper setCreatedDate(long val) {
       this.createdDate = val;
       return this;
     } 
     private long modifiedDate;
     public long getModifiedDate() {
       return this.modifiedDate;
     }
     public PublicationWrapper setModifiedDate(long val) {
       this.modifiedDate = val;
       return this;
     } 
     private int _disposition;
     public int get_Disposition() {
       return this._disposition;
     }
     public PublicationWrapper set_Disposition(int val) {
       this._disposition = val;
       return this;
     }
     private long _received_date;
     public long get_ReceivedDate() {
         return this._received_date;
     }
     public PublicationWrapper set_ReceivedDate(long val) {
         this._received_date = val;
         return this;
     }
} 
/**
* Table Name: subscription <P>
*/
static public class SubscriptionWrapper {
   public SubscriptionWrapper() {
      logger.trace("building SubscriptionWrapper");
   }
     private int disposition;
     public int getDisposition() {
       return this.disposition;
     }
     public SubscriptionWrapper setDisposition(int val) {
       this.disposition = val;
       return this;
     } 
     private String uri;
     public String getUri() {
       return this.uri;
     }
     public SubscriptionWrapper setUri(String val) {
       this.uri = val;
       return this;
     } 
     private String mime;
     public String getMime() {
       return this.mime;
     }
     public SubscriptionWrapper setMime(String val) {
       this.mime = val;
       return this;
     } 
     private String selection;
     public String getSelection() {
       return this.selection;
     }
     public SubscriptionWrapper setSelection(String val) {
       this.selection = val;
       return this;
     } 
     private int expiration;
     public int getExpiration() {
       return this.expiration;
     }
     public SubscriptionWrapper setExpiration(int val) {
       this.expiration = val;
       return this;
     } 
     private byte[] notice;
     public byte[] getNotice() {
       return this.notice;
     }
     public SubscriptionWrapper setNotice(byte[] val) {
       this.notice = val;
       return this;
     } 
     private int priority;
     public int getPriority() {
       return this.priority;
     }
     public SubscriptionWrapper setPriority(int val) {
       this.priority = val;
       return this;
     } 
     private long createdDate;
     public long getCreatedDate() {
       return this.createdDate;
     }
     public SubscriptionWrapper setCreatedDate(long val) {
       this.createdDate = val;
       return this;
     } 
     private long modifiedDate;
     public long getModifiedDate() {
       return this.modifiedDate;
     }
     public SubscriptionWrapper setModifiedDate(long val) {
       this.modifiedDate = val;
       return this;
     } 
     private int _disposition;
     public int get_Disposition() {
       return this._disposition;
     }
     public SubscriptionWrapper set_Disposition(int val) {
       this._disposition = val;
       return this;
     }
     private long _received_date;
     public long get_ReceivedDate() {
         return this._received_date;
     }
     public SubscriptionWrapper set_ReceivedDate(long val) {
         this._received_date = val;
         return this;
     }
} 



/**
 * This method is provided with the express purpose of being overridden and extended.
 *
 *    StringBuilder sb = new StringBuilder();
 *    sb.append("\""+PostalTableSchema.FUNCTION_CODE+"\" = '"+ wrap.getFunctionCode()+"'"); 
 *    return sb.toString();   
 *
 * @param wrap
 */
protected String postalSelectKeyClause(PostalWrapper wrap) {
  return null;
}

/**
 * This method is provided with the express purpose of being overridden and extended.
 * @param wrap
 */
protected ContentValues postalComposeValues(PostalWrapper wrap) {
   ContentValues cv = new ContentValues();
   cv.put(PostalTableSchema.CP_TYPE, wrap.getCpType()); 
   cv.put(PostalTableSchema.URI, wrap.getUri()); 
   cv.put(PostalTableSchema.NOTICE, wrap.getNotice()); 
   cv.put(PostalTableSchema.PRIORITY, wrap.getPriority()); 
   cv.put(PostalTableSchema.SERIALIZE_TYPE, wrap.getSerializeType()); 
   cv.put(PostalTableSchema.DISPOSITION, wrap.getDisposition()); 
   cv.put(PostalTableSchema.EXPIRATION, wrap.getExpiration()); 
   cv.put(PostalTableSchema.UNIT, wrap.getUnit()); 
   cv.put(PostalTableSchema.VALUE, wrap.getValue()); 
   cv.put(PostalTableSchema.DATA, wrap.getData()); 
   cv.put(PostalTableSchema.CREATED_DATE, wrap.getCreatedDate()); 
   cv.put(PostalTableSchema.MODIFIED_DATE, wrap.getModifiedDate()); 
   cv.put(PostalTableSchema._RECEIVED_DATE, wrap.get_ReceivedDate());
   cv.put(PostalTableSchema._DISPOSITION, wrap.get_Disposition());
   return cv;   
}



/**
 * This method is provided with the express purpose of being overridden and extended.
 *
 *    StringBuilder sb = new StringBuilder();
 *    sb.append("\""+RetrievalTableSchema.FUNCTION_CODE+"\" = '"+ wrap.getFunctionCode()+"'"); 
 *    return sb.toString();   
 *
 * @param wrap
 */
protected String retrievalSelectKeyClause(RetrievalWrapper wrap) {
  return null;
}

/**
 * This method is provided with the express purpose of being overridden and extended.
 * @param wrap
 */
protected ContentValues retrievalComposeValues(RetrievalWrapper wrap) {
   ContentValues cv = new ContentValues();
   cv.put(RetrievalTableSchema.DISPOSITION, wrap.getDisposition()); 
   cv.put(RetrievalTableSchema.NOTICE, wrap.getNotice()); 
   cv.put(RetrievalTableSchema.PRIORITY, wrap.getPriority()); 
   cv.put(RetrievalTableSchema.URI, wrap.getUri()); 
   cv.put(RetrievalTableSchema.MIME, wrap.getMime()); 
   cv.put(RetrievalTableSchema.PROJECTION, wrap.getProjection()); 
   cv.put(RetrievalTableSchema.SELECTION, wrap.getSelection()); 
   cv.put(RetrievalTableSchema.ARGS, wrap.getArgs()); 
   cv.put(RetrievalTableSchema.ORDERING, wrap.getOrdering()); 
   cv.put(RetrievalTableSchema.CONTINUITY, wrap.getContinuity()); 
   cv.put(RetrievalTableSchema.CONTINUITY_VALUE, wrap.getContinuity_value()); 
   cv.put(RetrievalTableSchema.EXPIRATION, wrap.getExpiration()); 
   cv.put(RetrievalTableSchema.CREATED_DATE, wrap.getCreatedDate()); 
   cv.put(RetrievalTableSchema.MODIFIED_DATE, wrap.getModifiedDate()); 
   cv.put(RetrievalTableSchema._RECEIVED_DATE, wrap.get_ReceivedDate());
   cv.put(RetrievalTableSchema._DISPOSITION, wrap.get_Disposition());
   return cv;   
}



/**
 * This method is provided with the express purpose of being overridden and extended.
 *
 *    StringBuilder sb = new StringBuilder();
 *    sb.append("\""+PublicationTableSchema.FUNCTION_CODE+"\" = '"+ wrap.getFunctionCode()+"'"); 
 *    return sb.toString();   
 *
 * @param wrap
 */
protected String publicationSelectKeyClause(PublicationWrapper wrap) {
  return null;
}

/**
 * This method is provided with the express purpose of being overridden and extended.
 * @param wrap
 */
protected ContentValues publicationComposeValues(PublicationWrapper wrap) {
   ContentValues cv = new ContentValues();
   cv.put(PublicationTableSchema.DISPOSITION, wrap.getDisposition()); 
   cv.put(PublicationTableSchema.URI, wrap.getUri()); 
   cv.put(PublicationTableSchema.MIME, wrap.getMime()); 
   cv.put(PublicationTableSchema.EXPIRATION, wrap.getExpiration()); 
   cv.put(PublicationTableSchema.CREATED_DATE, wrap.getCreatedDate()); 
   cv.put(PublicationTableSchema.MODIFIED_DATE, wrap.getModifiedDate()); 
   cv.put(PublicationTableSchema._RECEIVED_DATE, wrap.get_ReceivedDate());
   cv.put(PublicationTableSchema._DISPOSITION, wrap.get_Disposition());
   return cv;   
}



/**
 * This method is provided with the express purpose of being overridden and extended.
 *
 *    StringBuilder sb = new StringBuilder();
 *    sb.append("\""+SubscriptionTableSchema.FUNCTION_CODE+"\" = '"+ wrap.getFunctionCode()+"'"); 
 *    return sb.toString();   
 *
 * @param wrap
 */
protected String subscriptionSelectKeyClause(SubscriptionWrapper wrap) {
  return null;
}

/**
 * This method is provided with the express purpose of being overridden and extended.
 * @param wrap
 */
protected ContentValues subscriptionComposeValues(SubscriptionWrapper wrap) {
   ContentValues cv = new ContentValues();
   cv.put(SubscriptionTableSchema.DISPOSITION, wrap.getDisposition()); 
   cv.put(SubscriptionTableSchema.URI, wrap.getUri()); 
   cv.put(SubscriptionTableSchema.MIME, wrap.getMime()); 
   cv.put(SubscriptionTableSchema.SELECTION, wrap.getSelection()); 
   cv.put(SubscriptionTableSchema.EXPIRATION, wrap.getExpiration()); 
   cv.put(SubscriptionTableSchema.NOTICE, wrap.getNotice()); 
   cv.put(SubscriptionTableSchema.PRIORITY, wrap.getPriority()); 
   cv.put(SubscriptionTableSchema.CREATED_DATE, wrap.getCreatedDate()); 
   cv.put(SubscriptionTableSchema.MODIFIED_DATE, wrap.getModifiedDate()); 
   cv.put(SubscriptionTableSchema._RECEIVED_DATE, wrap.get_ReceivedDate());
   cv.put(SubscriptionTableSchema._DISPOSITION, wrap.get_Disposition());
   return cv;   
}



  interface IMyWriter {
      public long meta(StringBuilder sb);
      public long payload(long rowId, String label, byte[] buf);
  }

  static final int READING_META = 0;
  static final int READING_LABEL = 1;
  static final int READING_PAYLOAD_SIZE = 2;
  static final int READING_PAYLOAD = 3;
  static final int READING_PAYLOAD_CHECK = 4;

  protected long deserializer(File file, IMyWriter writer) {
     logger.debug("::deserializer");
     InputStream ins;
     try {
        ins = new FileInputStream(file);
     } catch (FileNotFoundException e1) {
        return -1;
     }
     BufferedInputStream bufferedInput = new BufferedInputStream(ins);
     byte[] buffer = new byte[1024];
     StringBuilder sb = new StringBuilder();
     long rowId = -1;
     String label = "";
     byte[] payloadSizeBuf = new byte[4];
     int payloadSizeBufPos = 0;
     int payloadSize = 0;
     byte[] payloadBuf = null;
     int payloadPos = 0;
     try {
        int bytesBuffered = bufferedInput.read(buffer);
        int bufferPos = 0;
        int state = READING_META;
        boolean eod = false;
        while (bytesBuffered > -1) {
            if (bytesBuffered == bufferPos) { 
                bytesBuffered = bufferedInput.read(buffer);
                bufferPos = 0; // reset buffer position
            }
            if (bytesBuffered < 0) eod = true;
              
            switch (state) {
            case READING_META:
                if (eod) {
                    writer.meta(sb);
                    break;
                }
                for (; bytesBuffered > bufferPos; bufferPos++) {
                    byte b = buffer[bufferPos];
                    if (b == '\0') {
                        bufferPos++;
                        state = READING_LABEL;
                        rowId = writer.meta(sb);
                        sb = new StringBuilder();
                        break;
                    }
                    sb.append((char)b);
                }
                break;
            case READING_LABEL:
                if (eod)  break;
                
                for (; bytesBuffered > bufferPos; bufferPos++) {
                    byte b = buffer[bufferPos];
                    if (b == '\0') {
                        label = sb.toString();
                        bufferPos++;
                        state = READING_PAYLOAD_SIZE;
                        payloadPos = 0;
                        break;
                    }
                    sb.append((char)b);
                }
                break;
            case READING_PAYLOAD_SIZE:
                if ((bytesBuffered - bufferPos) < (payloadSizeBuf.length - payloadPos)) { 
                    // buffer doesn't contain the last byte of the length
                    for (; bytesBuffered > bufferPos; bufferPos++, payloadPos++) { 
                        payloadSizeBuf[payloadPos] = buffer[bufferPos];
                    }
                } else {
                    // buffer contains the last byte of the length
                    for (; payloadSizeBuf.length > payloadPos; bufferPos++, payloadPos++) { 
                        payloadSizeBuf[payloadPos] = buffer[bufferPos];
                    }
                    ByteBuffer dataSizeBuf = ByteBuffer.wrap(payloadSizeBuf);
                      dataSizeBuf.order(ByteOrder.LITTLE_ENDIAN);
                      payloadSize = dataSizeBuf.getInt();
                      payloadBuf = new byte[payloadSize];
                      payloadPos = 0;
                    state = READING_PAYLOAD;
                }
                break;
            case READING_PAYLOAD:
                if ((bytesBuffered - bufferPos) < (payloadSize - payloadPos)) { 
                    for (; bytesBuffered > bufferPos; bufferPos++, payloadPos++) { 
                        payloadBuf[payloadPos] = buffer[bufferPos];
                    }
                } else {
                    for (; payloadSize > payloadPos; bufferPos++, payloadPos++) { 
                        payloadBuf[payloadPos] = buffer[bufferPos];
                    }
                   
                    payloadPos = 0;
                    state = READING_PAYLOAD_CHECK;
                }
                break;
            case READING_PAYLOAD_CHECK:
                if ((bytesBuffered - bufferPos) < (payloadSizeBuf.length - payloadPos)) { 
                    for (; bytesBuffered > bufferPos; bufferPos++, payloadPos++) { 
                        payloadSizeBuf[payloadPos] = buffer[bufferPos];
                    }
                } else {
                    for (; payloadSizeBuf.length > payloadPos; bufferPos++, payloadPos++) { 
                        payloadSizeBuf[payloadPos] = buffer[bufferPos];
                    }
                    ByteBuffer dataSizeBuf = ByteBuffer.wrap(payloadSizeBuf);
                      dataSizeBuf.order(ByteOrder.LITTLE_ENDIAN);
                      if (payloadSize != dataSizeBuf.getInt()) {
                         logger.error("message garbled {} {}", payloadSize, dataSizeBuf.getInt());
                         state = READING_LABEL;
                         break;
                      } 
                      writer.payload(rowId, label, payloadBuf);
                    state = READING_LABEL;
                }
                break;
            }
        }
        bufferedInput.close();
     } catch (IOException e) {
        logger.error("could not read serialized file");
        return -1;
     }
     return rowId;
  }

  //@Override 
  public ArrayList<File> postalSerialize(Cursor cursor) {
      logger.trace( "::postalSerialize");
      ArrayList<File> paths = new ArrayList<File>();      
      if (1 > cursor.getCount()) return paths;

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream eos = new DataOutputStream(baos);
      
      for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
           PostalWrapper iw = new PostalWrapper();
             iw.setCpType(cursor.getString(cursor.getColumnIndex(PostalTableSchema.CP_TYPE)));  
             iw.setUri(cursor.getString(cursor.getColumnIndex(PostalTableSchema.URI)));  
             iw.setNotice(cursor.getBlob(cursor.getColumnIndex(PostalTableSchema.NOTICE)));  
             iw.setPriority(cursor.getInt(cursor.getColumnIndex(PostalTableSchema.PRIORITY)));  
             iw.setSerializeType(cursor.getInt(cursor.getColumnIndex(PostalTableSchema.SERIALIZE_TYPE)));  
             iw.setDisposition(cursor.getInt(cursor.getColumnIndex(PostalTableSchema.DISPOSITION)));  
             iw.setExpiration(cursor.getInt(cursor.getColumnIndex(PostalTableSchema.EXPIRATION)));  
             iw.setUnit(cursor.getString(cursor.getColumnIndex(PostalTableSchema.UNIT)));  
             iw.setValue(cursor.getInt(cursor.getColumnIndex(PostalTableSchema.VALUE)));  
             iw.setData(cursor.getString(cursor.getColumnIndex(PostalTableSchema.DATA)));  
             iw.setCreatedDate(cursor.getLong(cursor.getColumnIndex(PostalTableSchema.CREATED_DATE)));  
             iw.setModifiedDate(cursor.getLong(cursor.getColumnIndex(PostalTableSchema.MODIFIED_DATE)));  
             iw.set_ReceivedDate(cursor.getLong(cursor.getColumnIndex(PostalTableSchema._RECEIVED_DATE))); 
             iw.set_Disposition(cursor.getInt(cursor.getColumnIndex(PostalTableSchema._DISPOSITION))); 

           Gson gson = new Gson();

           try {
              eos.writeBytes(gson.toJson(iw));
              eos.writeByte(0);
           } catch (IOException ex) {
              ex.printStackTrace();
           }

           // not a reference field name :cp type cpType cp_type\n 
           // not a reference field name :uri uri uri\n 
           // not a reference field name :notice notice notice\n 
           // not a reference field name :priority priority priority\n 
           // not a reference field name :serialize type serializeType serialize_type\n 
           // not a reference field name :disposition disposition disposition\n 
           // not a reference field name :expiration expiration expiration\n 
           // not a reference field name :unit unit unit\n 
           // not a reference field name :value value value\n 
           // not a reference field name :data data data\n 
           // not a reference field name :created date createdDate created_date\n 
           // not a reference field name :modified date modifiedDate modified_date\n 
           // PostalTableSchema._DISPOSITION;

           try {
              if (!applCachePostalDir.exists() ) applCachePostalDir.mkdirs();
              
              File outfile = new File(applCachePostalDir, Integer.toHexString((int) System.currentTimeMillis())); 
              BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outfile), 8192);
              bufferedOutput.write(baos.toByteArray());
              bufferedOutput.flush();
              bufferedOutput.close();
           
              paths.add(outfile);
           } catch (FileNotFoundException e) {
              e.printStackTrace();
           } catch (IOException e) {
              e.printStackTrace();
           }
      }
      return paths;
   } 
  //@Override 
  public ArrayList<File> retrievalSerialize(Cursor cursor) {
      logger.trace( "::retrievalSerialize");
      ArrayList<File> paths = new ArrayList<File>();      
      if (1 > cursor.getCount()) return paths;

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream eos = new DataOutputStream(baos);
      
      for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
           RetrievalWrapper iw = new RetrievalWrapper();
             iw.setDisposition(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchema.DISPOSITION)));  
             iw.setNotice(cursor.getBlob(cursor.getColumnIndex(RetrievalTableSchema.NOTICE)));  
             iw.setPriority(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchema.PRIORITY)));  
             iw.setUri(cursor.getString(cursor.getColumnIndex(RetrievalTableSchema.URI)));  
             iw.setMime(cursor.getString(cursor.getColumnIndex(RetrievalTableSchema.MIME)));  
             iw.setProjection(cursor.getString(cursor.getColumnIndex(RetrievalTableSchema.PROJECTION)));  
             iw.setSelection(cursor.getString(cursor.getColumnIndex(RetrievalTableSchema.SELECTION)));  
             iw.setArgs(cursor.getString(cursor.getColumnIndex(RetrievalTableSchema.ARGS)));  
             iw.setOrdering(cursor.getString(cursor.getColumnIndex(RetrievalTableSchema.ORDERING)));  
             iw.setContinuity(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchema.CONTINUITY)));  
             iw.setContinuity_value(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchema.CONTINUITY_VALUE)));  
             iw.setExpiration(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchema.EXPIRATION)));  
             iw.setCreatedDate(cursor.getLong(cursor.getColumnIndex(RetrievalTableSchema.CREATED_DATE)));  
             iw.setModifiedDate(cursor.getLong(cursor.getColumnIndex(RetrievalTableSchema.MODIFIED_DATE)));  
             iw.set_ReceivedDate(cursor.getLong(cursor.getColumnIndex(RetrievalTableSchema._RECEIVED_DATE))); 
             iw.set_Disposition(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchema._DISPOSITION))); 

           Gson gson = new Gson();

           try {
              eos.writeBytes(gson.toJson(iw));
              eos.writeByte(0);
           } catch (IOException ex) {
              ex.printStackTrace();
           }

           // not a reference field name :disposition disposition disposition\n 
           // not a reference field name :notice notice notice\n 
           // not a reference field name :priority priority priority\n 
           // not a reference field name :uri uri uri\n 
           // not a reference field name :mime mime mime\n 
           // not a reference field name :projection projection projection\n 
           // not a reference field name :selection selection selection\n 
           // not a reference field name :args args args\n 
           // not a reference field name :ordering ordering ordering\n 
           // not a reference field name :continuity continuity continuity\n 
           // not a reference field name :continuity_value continuity_value continuity_value\n 
           // not a reference field name :expiration expiration expiration\n 
           // not a reference field name :created date createdDate created_date\n 
           // not a reference field name :modified date modifiedDate modified_date\n 
           // RetrievalTableSchema._DISPOSITION;

           try {
              if (!applCacheRetrievalDir.exists() ) applCacheRetrievalDir.mkdirs();
              
              File outfile = new File(applCacheRetrievalDir, Integer.toHexString((int) System.currentTimeMillis())); 
              BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outfile), 8192);
              bufferedOutput.write(baos.toByteArray());
              bufferedOutput.flush();
              bufferedOutput.close();
           
              paths.add(outfile);
           } catch (FileNotFoundException e) {
              e.printStackTrace();
           } catch (IOException e) {
              e.printStackTrace();
           }
      }
      return paths;
   } 
  //@Override 
  public ArrayList<File> publicationSerialize(Cursor cursor) {
      logger.trace( "::publicationSerialize");
      ArrayList<File> paths = new ArrayList<File>();      
      if (1 > cursor.getCount()) return paths;

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream eos = new DataOutputStream(baos);
      
      for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
           PublicationWrapper iw = new PublicationWrapper();
             iw.setDisposition(cursor.getInt(cursor.getColumnIndex(PublicationTableSchema.DISPOSITION)));  
             iw.setUri(cursor.getString(cursor.getColumnIndex(PublicationTableSchema.URI)));  
             iw.setMime(cursor.getString(cursor.getColumnIndex(PublicationTableSchema.MIME)));  
             iw.setExpiration(cursor.getInt(cursor.getColumnIndex(PublicationTableSchema.EXPIRATION)));  
             iw.setCreatedDate(cursor.getLong(cursor.getColumnIndex(PublicationTableSchema.CREATED_DATE)));  
             iw.setModifiedDate(cursor.getLong(cursor.getColumnIndex(PublicationTableSchema.MODIFIED_DATE)));  
             iw.set_ReceivedDate(cursor.getLong(cursor.getColumnIndex(PublicationTableSchema._RECEIVED_DATE))); 
             iw.set_Disposition(cursor.getInt(cursor.getColumnIndex(PublicationTableSchema._DISPOSITION))); 

           Gson gson = new Gson();

           try {
              eos.writeBytes(gson.toJson(iw));
              eos.writeByte(0);
           } catch (IOException ex) {
              ex.printStackTrace();
           }

           // not a reference field name :disposition disposition disposition\n 
           // not a reference field name :uri uri uri\n 
           // not a reference field name :mime mime mime\n 
           // not a reference field name :expiration expiration expiration\n 
           // not a reference field name :created date createdDate created_date\n 
           // not a reference field name :modified date modifiedDate modified_date\n 
           // PublicationTableSchema._DISPOSITION;

           try {
              if (!applCachePublicationDir.exists() ) applCachePublicationDir.mkdirs();
              
              File outfile = new File(applCachePublicationDir, Integer.toHexString((int) System.currentTimeMillis())); 
              BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outfile), 8192);
              bufferedOutput.write(baos.toByteArray());
              bufferedOutput.flush();
              bufferedOutput.close();
           
              paths.add(outfile);
           } catch (FileNotFoundException e) {
              e.printStackTrace();
           } catch (IOException e) {
              e.printStackTrace();
           }
      }
      return paths;
   } 
  //@Override 
  public ArrayList<File> subscriptionSerialize(Cursor cursor) {
      logger.trace( "::subscriptionSerialize");
      ArrayList<File> paths = new ArrayList<File>();      
      if (1 > cursor.getCount()) return paths;

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream eos = new DataOutputStream(baos);
      
      for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
           SubscriptionWrapper iw = new SubscriptionWrapper();
             iw.setDisposition(cursor.getInt(cursor.getColumnIndex(SubscriptionTableSchema.DISPOSITION)));  
             iw.setUri(cursor.getString(cursor.getColumnIndex(SubscriptionTableSchema.URI)));  
             iw.setMime(cursor.getString(cursor.getColumnIndex(SubscriptionTableSchema.MIME)));  
             iw.setSelection(cursor.getString(cursor.getColumnIndex(SubscriptionTableSchema.SELECTION)));  
             iw.setExpiration(cursor.getInt(cursor.getColumnIndex(SubscriptionTableSchema.EXPIRATION)));  
             iw.setNotice(cursor.getBlob(cursor.getColumnIndex(SubscriptionTableSchema.NOTICE)));  
             iw.setPriority(cursor.getInt(cursor.getColumnIndex(SubscriptionTableSchema.PRIORITY)));  
             iw.setCreatedDate(cursor.getLong(cursor.getColumnIndex(SubscriptionTableSchema.CREATED_DATE)));  
             iw.setModifiedDate(cursor.getLong(cursor.getColumnIndex(SubscriptionTableSchema.MODIFIED_DATE)));  
             iw.set_ReceivedDate(cursor.getLong(cursor.getColumnIndex(SubscriptionTableSchema._RECEIVED_DATE))); 
             iw.set_Disposition(cursor.getInt(cursor.getColumnIndex(SubscriptionTableSchema._DISPOSITION))); 

           Gson gson = new Gson();

           try {
              eos.writeBytes(gson.toJson(iw));
              eos.writeByte(0);
           } catch (IOException ex) {
              ex.printStackTrace();
           }

           // not a reference field name :disposition disposition disposition\n 
           // not a reference field name :uri uri uri\n 
           // not a reference field name :mime mime mime\n 
           // not a reference field name :selection selection selection\n 
           // not a reference field name :expiration expiration expiration\n 
           // not a reference field name :notice notice notice\n 
           // not a reference field name :priority priority priority\n 
           // not a reference field name :created date createdDate created_date\n 
           // not a reference field name :modified date modifiedDate modified_date\n 
           // SubscriptionTableSchema._DISPOSITION;

           try {
              if (!applCacheSubscriptionDir.exists() ) applCacheSubscriptionDir.mkdirs();
              
              File outfile = new File(applCacheSubscriptionDir, Integer.toHexString((int) System.currentTimeMillis())); 
              BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outfile), 8192);
              bufferedOutput.write(baos.toByteArray());
              bufferedOutput.flush();
              bufferedOutput.close();
           
              paths.add(outfile);
           } catch (FileNotFoundException e) {
              e.printStackTrace();
           } catch (IOException e) {
              e.printStackTrace();
           }
      }
      return paths;
   } 

  class postalDeserializer implements IMyWriter {
     
      public long meta(StringBuilder sb) {
         String json = sb.toString();
         Gson gson = new Gson();
         PostalWrapper wrap = null;
         try {
            wrap = gson.fromJson(json, PostalWrapper.class);
         } catch (JsonParseException ex) {
            ex.getMessage();
            ex.printStackTrace();
            return -1;
         } catch (java.lang.RuntimeException ex) {
            ex.getMessage();
            ex.printStackTrace();
            return -1;
         }
         if (wrap == null) return -1;
 
         SQLiteDatabase db = openHelper.getReadableDatabase();
 
         ContentValues cv = postalComposeValues(wrap);
         // Put the current system time into the received column for relative time pulls.
         cv.put(PostalTableSchema._RECEIVED_DATE, System.currentTimeMillis());
         String whereClause = postalSelectKeyClause(wrap);
 
         if (whereClause != null) {
            // Switch on the path in the uri for what we want to query.
            Cursor updateCursor = db.query(Tables.POSTAL_TBL, postalProjectionKey, whereClause, null, null, null, null);
            long rowId = -1;
            for (boolean more = updateCursor.moveToFirst(); more;)
            {
                rowId = updateCursor.getLong(updateCursor.getColumnIndex(PostalTableSchema._ID));  
 
                db.update(Tables.POSTAL_TBL, cv, 
                       "\""+PostalTableSchema._ID+"\" = '"+ Long.toString(rowId)+"'",
                        null); 
                break;
            }
            updateCursor.close();
            if (rowId > 0) {
                getContext().getContentResolver().notifyChange(PostalTableSchema.CONTENT_URI, null); 
                return rowId;
            }
         }
         //long rowId = db.insert(Tables.POSTAL_TBL, 
         //         PostalTableSchema.CP_TYPE,
         //         cv);
     Uri rowUri = getContext().getContentResolver().insert(PostalTableSchema.CONTENT_URI, cv);
         long rowId = Long.valueOf(rowUri.getLastPathSegment()).longValue();

         getContext().getContentResolver().notifyChange(PostalTableSchema.CONTENT_URI, null); 
         return rowId;
      }
 
    @Override
    public long payload(long rowId, String label, byte[] buf) {
       ContentResolver cr = getContext().getContentResolver();
       Uri rowUri = ContentUris.withAppendedId(PostalTableSchema.CONTENT_URI, rowId);
       Cursor cursor = cr.query(rowUri, null, null, null, null);
       cursor.moveToFirst();
       String filename = cursor.getString(cursor.getColumnIndex(label));  
       cursor.close();
        File dataFile = new File(filename);
        File dataDir = dataFile.getParentFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        FileOutputStream fos;
       try {
          fos = new FileOutputStream(dataFile);
       } catch (FileNotFoundException e) {
          return -1;
       }
        try {
          fos.write(buf);
          fos.close();
       } catch (IOException e) {
          return -1;
       }
       return 0;
    }
  }
 
  public long postalDeserialize(File file) {
     return this.deserializer(file, new postalDeserializer());
  } 

  class retrievalDeserializer implements IMyWriter {
     
      public long meta(StringBuilder sb) {
         String json = sb.toString();
         Gson gson = new Gson();
         RetrievalWrapper wrap = null;
         try {
            wrap = gson.fromJson(json, RetrievalWrapper.class);
         } catch (JsonParseException ex) {
            ex.getMessage();
            ex.printStackTrace();
            return -1;
         } catch (java.lang.RuntimeException ex) {
            ex.getMessage();
            ex.printStackTrace();
            return -1;
         }
         if (wrap == null) return -1;
 
         SQLiteDatabase db = openHelper.getReadableDatabase();
 
         ContentValues cv = retrievalComposeValues(wrap);
         // Put the current system time into the received column for relative time pulls.
         cv.put(RetrievalTableSchema._RECEIVED_DATE, System.currentTimeMillis());
         String whereClause = retrievalSelectKeyClause(wrap);
 
         if (whereClause != null) {
            // Switch on the path in the uri for what we want to query.
            Cursor updateCursor = db.query(Tables.RETRIEVAL_TBL, retrievalProjectionKey, whereClause, null, null, null, null);
            long rowId = -1;
            for (boolean more = updateCursor.moveToFirst(); more;)
            {
                rowId = updateCursor.getLong(updateCursor.getColumnIndex(RetrievalTableSchema._ID));  
 
                db.update(Tables.RETRIEVAL_TBL, cv, 
                       "\""+RetrievalTableSchema._ID+"\" = '"+ Long.toString(rowId)+"'",
                        null); 
                break;
            }
            updateCursor.close();
            if (rowId > 0) {
                getContext().getContentResolver().notifyChange(RetrievalTableSchema.CONTENT_URI, null); 
                return rowId;
            }
         }
         //long rowId = db.insert(Tables.RETRIEVAL_TBL, 
         //         RetrievalTableSchema.DISPOSITION,
         //         cv);
     Uri rowUri = getContext().getContentResolver().insert(RetrievalTableSchema.CONTENT_URI, cv);
         long rowId = Long.valueOf(rowUri.getLastPathSegment()).longValue();

         getContext().getContentResolver().notifyChange(RetrievalTableSchema.CONTENT_URI, null); 
         return rowId;
      }
 
    @Override
    public long payload(long rowId, String label, byte[] buf) {
       ContentResolver cr = getContext().getContentResolver();
       Uri rowUri = ContentUris.withAppendedId(RetrievalTableSchema.CONTENT_URI, rowId);
       Cursor cursor = cr.query(rowUri, null, null, null, null);
       cursor.moveToFirst();
       String filename = cursor.getString(cursor.getColumnIndex(label));  
       cursor.close();
        File dataFile = new File(filename);
        File dataDir = dataFile.getParentFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        FileOutputStream fos;
       try {
          fos = new FileOutputStream(dataFile);
       } catch (FileNotFoundException e) {
          return -1;
       }
        try {
          fos.write(buf);
          fos.close();
       } catch (IOException e) {
          return -1;
       }
       return 0;
    }
  }
 
  public long retrievalDeserialize(File file) {
     return this.deserializer(file, new retrievalDeserializer());
  } 

  class publicationDeserializer implements IMyWriter {
     
      public long meta(StringBuilder sb) {
         String json = sb.toString();
         Gson gson = new Gson();
         PublicationWrapper wrap = null;
         try {
            wrap = gson.fromJson(json, PublicationWrapper.class);
         } catch (JsonParseException ex) {
            ex.getMessage();
            ex.printStackTrace();
            return -1;
         } catch (java.lang.RuntimeException ex) {
            ex.getMessage();
            ex.printStackTrace();
            return -1;
         }
         if (wrap == null) return -1;
 
         SQLiteDatabase db = openHelper.getReadableDatabase();
 
         ContentValues cv = publicationComposeValues(wrap);
         // Put the current system time into the received column for relative time pulls.
         cv.put(PublicationTableSchema._RECEIVED_DATE, System.currentTimeMillis());
         String whereClause = publicationSelectKeyClause(wrap);
 
         if (whereClause != null) {
            // Switch on the path in the uri for what we want to query.
            Cursor updateCursor = db.query(Tables.PUBLICATION_TBL, publicationProjectionKey, whereClause, null, null, null, null);
            long rowId = -1;
            for (boolean more = updateCursor.moveToFirst(); more;)
            {
                rowId = updateCursor.getLong(updateCursor.getColumnIndex(PublicationTableSchema._ID));  
 
                db.update(Tables.PUBLICATION_TBL, cv, 
                       "\""+PublicationTableSchema._ID+"\" = '"+ Long.toString(rowId)+"'",
                        null); 
                break;
            }
            updateCursor.close();
            if (rowId > 0) {
                getContext().getContentResolver().notifyChange(PublicationTableSchema.CONTENT_URI, null); 
                return rowId;
            }
         }
         //long rowId = db.insert(Tables.PUBLICATION_TBL, 
         //         PublicationTableSchema.DISPOSITION,
         //         cv);
     Uri rowUri = getContext().getContentResolver().insert(PublicationTableSchema.CONTENT_URI, cv);
         long rowId = Long.valueOf(rowUri.getLastPathSegment()).longValue();

         getContext().getContentResolver().notifyChange(PublicationTableSchema.CONTENT_URI, null); 
         return rowId;
      }
 
    @Override
    public long payload(long rowId, String label, byte[] buf) {
       ContentResolver cr = getContext().getContentResolver();
       Uri rowUri = ContentUris.withAppendedId(PublicationTableSchema.CONTENT_URI, rowId);
       Cursor cursor = cr.query(rowUri, null, null, null, null);
       cursor.moveToFirst();
       String filename = cursor.getString(cursor.getColumnIndex(label));  
       cursor.close();
        File dataFile = new File(filename);
        File dataDir = dataFile.getParentFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        FileOutputStream fos;
       try {
          fos = new FileOutputStream(dataFile);
       } catch (FileNotFoundException e) {
          return -1;
       }
        try {
          fos.write(buf);
          fos.close();
       } catch (IOException e) {
          return -1;
       }
       return 0;
    }
  }
 
  public long publicationDeserialize(File file) {
     return this.deserializer(file, new publicationDeserializer());
  } 

  class subscriptionDeserializer implements IMyWriter {
     
      public long meta(StringBuilder sb) {
         String json = sb.toString();
         Gson gson = new Gson();
         SubscriptionWrapper wrap = null;
         try {
            wrap = gson.fromJson(json, SubscriptionWrapper.class);
         } catch (JsonParseException ex) {
            ex.getMessage();
            ex.printStackTrace();
            return -1;
         } catch (java.lang.RuntimeException ex) {
            ex.getMessage();
            ex.printStackTrace();
            return -1;
         }
         if (wrap == null) return -1;
 
         SQLiteDatabase db = openHelper.getReadableDatabase();
 
         ContentValues cv = subscriptionComposeValues(wrap);
         // Put the current system time into the received column for relative time pulls.
         cv.put(SubscriptionTableSchema._RECEIVED_DATE, System.currentTimeMillis());
         String whereClause = subscriptionSelectKeyClause(wrap);
 
         if (whereClause != null) {
            // Switch on the path in the uri for what we want to query.
            Cursor updateCursor = db.query(Tables.SUBSCRIPTION_TBL, subscriptionProjectionKey, whereClause, null, null, null, null);
            long rowId = -1;
            for (boolean more = updateCursor.moveToFirst(); more;)
            {
                rowId = updateCursor.getLong(updateCursor.getColumnIndex(SubscriptionTableSchema._ID));  
 
                db.update(Tables.SUBSCRIPTION_TBL, cv, 
                       "\""+SubscriptionTableSchema._ID+"\" = '"+ Long.toString(rowId)+"'",
                        null); 
                break;
            }
            updateCursor.close();
            if (rowId > 0) {
                getContext().getContentResolver().notifyChange(SubscriptionTableSchema.CONTENT_URI, null); 
                return rowId;
            }
         }
         //long rowId = db.insert(Tables.SUBSCRIPTION_TBL, 
         //         SubscriptionTableSchema.DISPOSITION,
         //         cv);
     Uri rowUri = getContext().getContentResolver().insert(SubscriptionTableSchema.CONTENT_URI, cv);
         long rowId = Long.valueOf(rowUri.getLastPathSegment()).longValue();

         getContext().getContentResolver().notifyChange(SubscriptionTableSchema.CONTENT_URI, null); 
         return rowId;
      }
 
    @Override
    public long payload(long rowId, String label, byte[] buf) {
       ContentResolver cr = getContext().getContentResolver();
       Uri rowUri = ContentUris.withAppendedId(SubscriptionTableSchema.CONTENT_URI, rowId);
       Cursor cursor = cr.query(rowUri, null, null, null, null);
       cursor.moveToFirst();
       String filename = cursor.getString(cursor.getColumnIndex(label));  
       cursor.close();
        File dataFile = new File(filename);
        File dataDir = dataFile.getParentFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        FileOutputStream fos;
       try {
          fos = new FileOutputStream(dataFile);
       } catch (FileNotFoundException e) {
          return -1;
       }
        try {
          fos.write(buf);
          fos.close();
       } catch (IOException e) {
          return -1;
       }
       return 0;
    }
  }
 
  public long subscriptionDeserialize(File file) {
     return this.deserializer(file, new subscriptionDeserializer());
  } 

 
   
   // ===========================================================
   // Constants
   // ===========================================================
   private final static Logger logger = LoggerFactory.getLogger(DistributorDataStore.class);

   // ===========================================================
   // Fields
   // ===========================================================
   /** Projection Maps */
      protected static String[] postalProjectionKey;
      protected static HashMap<String, String> postalProjectionMap;
      
      protected static String[] retrievalProjectionKey;
      protected static HashMap<String, String> retrievalProjectionMap;
      
      protected static String[] publicationProjectionKey;
      protected static HashMap<String, String> publicationProjectionMap;
      
      protected static String[] subscriptionProjectionKey;
      protected static HashMap<String, String> subscriptionProjectionMap;
      
   
   /** Uri Matcher tags */
      protected static final int POSTAL_BLOB = 10;
      protected static final int POSTAL_SET = 11;
      protected static final int POSTAL_ID = 12;
      
      protected static final int RETRIEVAL_BLOB = 20;
      protected static final int RETRIEVAL_SET = 21;
      protected static final int RETRIEVAL_ID = 22;
      
      protected static final int PUBLICATION_BLOB = 30;
      protected static final int PUBLICATION_SET = 31;
      protected static final int PUBLICATION_ID = 32;
      
      protected static final int SUBSCRIPTION_BLOB = 40;
      protected static final int SUBSCRIPTION_SET = 41;
      protected static final int SUBSCRIPTION_ID = 42;
      
   
   /** Uri matcher */
   protected static final UriMatcher uriMatcher;
   
   /** Database helper */
   protected DistributorDatabaseHelper openHelper;

   /** Remote controller */
   private BroadcastReceiver controller;

   protected abstract boolean createDatabaseHelper();

   /**
    * In support of cr.openInputStream
    */
    private static final UriMatcher blobUriMatcher;
    static {
      blobUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.POSTAL_TBL+"/#/_serial", POSTAL_ID);
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.POSTAL_TBL+"/_serial", POSTAL_SET);
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.POSTAL_TBL+"/#/*", POSTAL_BLOB);
            
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.RETRIEVAL_TBL+"/#/_serial", RETRIEVAL_ID);
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.RETRIEVAL_TBL+"/_serial", RETRIEVAL_SET);
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.RETRIEVAL_TBL+"/#/*", RETRIEVAL_BLOB);
            
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.PUBLICATION_TBL+"/#/_serial", PUBLICATION_ID);
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.PUBLICATION_TBL+"/_serial", PUBLICATION_SET);
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.PUBLICATION_TBL+"/#/*", PUBLICATION_BLOB);
            
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.SUBSCRIPTION_TBL+"/#/_serial", SUBSCRIPTION_ID);
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.SUBSCRIPTION_TBL+"/_serial", SUBSCRIPTION_SET);
            blobUriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.SUBSCRIPTION_TBL+"/#/*", SUBSCRIPTION_BLOB);
            
    }
    
    /**
     * Examines uri's from clients:
     *  long fkId = cursor.getLong(cursor.getColumnIndex(Table.FK));
     *    Drawable icon = null;
     *    Uri fkUri = ContentUris.withAppendedId(TableSchema.CONTENT_URI, fkId);
     *  // then the fkUri can be used to get a tuple using a query.
     *    Cursor categoryCursor = this.managedQuery(categoryUri, null, null, null, null);
     *  // ...or the fkUri can be used to get a file descriptor.
     *    Uri iconUri = Uri.withAppendedPath(categoryUri, CategoryTableSchema.ICON);
     *  InputStream is = this.getContentResolver().openInputStream(iconUri);
     *  Drawableicon = Drawable.createFromStream(is, null);
     *  
     *  It is expected that the uri passed in will be of the form <content_uri>/<table>/<id>/<column>
     *  This is simple enough that a UriMatcher is not needed and 
     *  a simple uri.getPathSegments will suffice to identify the file.
     */
    protected Set<FileObserver> observerSet = new HashSet<FileObserver>();

    static private String showEvent(int event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event).append(" = ");
        if ((event & FileObserver.ACCESS) != 0) sb.append("ACCESS:");
        if ((event & FileObserver.MODIFY) != 0) sb.append("MODIFY:");
        if ((event & FileObserver.ATTRIB) != 0) sb.append("ATTRIB:");
        if ((event & FileObserver.CLOSE_WRITE) != 0) sb.append("CLOSE_WRITE:");
        if ((event & FileObserver.CLOSE_NOWRITE) != 0) sb.append("CLOSE_READ:");
        if ((event & FileObserver.OPEN) != 0) sb.append("OPEN:");
        if ((event & FileObserver.MOVED_FROM) != 0) sb.append("MOVED_FROM:");
        if ((event & FileObserver.MOVED_TO) != 0) sb.append("MOVED_TO:");
        if ((event & FileObserver.CREATE) != 0) sb.append("CREATE:");
        if ((event & FileObserver.DELETE) != 0) sb.append("DELETE:");
        if ((event & FileObserver.DELETE_SELF) != 0) sb.append("DELETE_SELF:");
        if (event > FileObserver.ALL_EVENTS) sb.append("unknown:");
        return sb.toString();
    }

    @Override
    public ParcelFileDescriptor openFile (Uri uri, String mode) {
        int imode = 0;
        if (mode.contains("w")) imode |= ParcelFileDescriptor.MODE_WRITE_ONLY;
        if (mode.contains("r")) imode |= ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode.contains("+")) imode |= ParcelFileDescriptor.MODE_APPEND;
        
        SQLiteQueryBuilder qb = null;
        SQLiteDatabase db = null;
        Cursor cursor = null;
        ArrayList<File> paths = null;
        List<String> pseg = uri.getPathSegments();
        
        int match = blobUriMatcher.match(uri);
        switch (match) {

         case POSTAL_BLOB:
            if (pseg.size() < 3)
                return null;

            try {
                File filePath = blobFile("postal", pseg.get(1), pseg.get(2));
                return ParcelFileDescriptor.open(filePath, imode);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            break;

         case POSTAL_SET:
            try {
               final File tempFile = tempFilePath("postal");
               final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                     tempFile, ParcelFileDescriptor.MODE_READ_WRITE);
               final FileObserver observer = new FileObserver(tempFile.getCanonicalPath()) {
                  @Override
                  public void onEvent(int event, String path) {
                     switch (event) {
                     case FileObserver.CLOSE_WRITE:
                        this.stopWatching();
                        try {
                          pfd.close();
                          postalDeserialize(tempFile);
                        } catch (IOException e) {
                          e.printStackTrace();
                        }
                        tempFile.delete();
                        observerSet.remove(this);
                        return;

                     default:
                        logger.info("unknown file disposition: "+ DistributorDataStore.showEvent(event));
                     }
                  }
               };
               observer.startWatching();
               observerSet.add(observer);
               return pfd;
             } catch (FileNotFoundException e1) {
                e1.printStackTrace();
             } catch (IOException e1) {
                e1.printStackTrace();
             }
             break;

         case POSTAL_ID:
            qb = new SQLiteQueryBuilder();
            db = openHelper.getReadableDatabase();
                    
            // Switch on the path in the uri for what we want to query.
            qb.setTables(Tables.POSTAL_TBL);
            qb.setProjectionMap(postalProjectionMap);
            qb.appendWhere(PostalTableSchema._ID + " = " + uri.getPathSegments().get(1));
            cursor = qb.query(db, null, null, null, null, null, null);
            if (1 > cursor.getCount()) {
               logger.info("no data of type POSTAL_ID"); 
               cursor.close();
               return null;
            }
            paths = this.postalSerialize(cursor);
            cursor.close();
            // this.postalDeserialize(paths.get(0)); // left in to aid in debugging
            try {
               final File file = paths.get(0);
               logger.info("serialization temp file: " + file.getAbsolutePath());
               final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, imode);
               final FileObserver observer = new FileObserver(file.getCanonicalPath()) {
                  @Override
                  public void onEvent(int event, String path) {
                     switch (event) {
                     case FileObserver.CLOSE_WRITE:
                     case FileObserver.CLOSE_NOWRITE:
                        this.stopWatching();
                        try {
                          logger.info("deleting serialization temp file: " + file.getAbsolutePath());
                          pfd.close();
                        } catch (IOException e) {
                          e.printStackTrace();
                        }
                        file.delete();
                        observerSet.remove(this);
                        return;
                     default:
                        logger.info("unknown file disposition: "+event);
                     }
                  }
               };
               observer.startWatching();
               observerSet.add(observer);
               return pfd;
            } catch (FileNotFoundException e) {
               e.printStackTrace();
            } catch (IOException e1) {
               e1.printStackTrace();
            }
            break;    

         case RETRIEVAL_BLOB:
            if (pseg.size() < 3)
                return null;

            try {
                File filePath = blobFile("retrieval", pseg.get(1), pseg.get(2));
                return ParcelFileDescriptor.open(filePath, imode);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            break;

         case RETRIEVAL_SET:
            try {
               final File tempFile = tempFilePath("retrieval");
               final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                     tempFile, ParcelFileDescriptor.MODE_READ_WRITE);
               final FileObserver observer = new FileObserver(tempFile.getCanonicalPath()) {
                  @Override
                  public void onEvent(int event, String path) {
                     switch (event) {
                     case FileObserver.CLOSE_WRITE:
                        this.stopWatching();
                        try {
                          pfd.close();
                          retrievalDeserialize(tempFile);
                        } catch (IOException e) {
                          e.printStackTrace();
                        }
                        tempFile.delete();
                        observerSet.remove(this);
                        return;

                     default:
                        logger.info("unknown file disposition: "+ DistributorDataStore.showEvent(event));
                     }
                  }
               };
               observer.startWatching();
               observerSet.add(observer);
               return pfd;
             } catch (FileNotFoundException e1) {
                e1.printStackTrace();
             } catch (IOException e1) {
                e1.printStackTrace();
             }
             break;

         case RETRIEVAL_ID:
            qb = new SQLiteQueryBuilder();
            db = openHelper.getReadableDatabase();
                    
            // Switch on the path in the uri for what we want to query.
            qb.setTables(Tables.RETRIEVAL_TBL);
            qb.setProjectionMap(retrievalProjectionMap);
            qb.appendWhere(RetrievalTableSchema._ID + " = " + uri.getPathSegments().get(1));
            cursor = qb.query(db, null, null, null, null, null, null);
            if (1 > cursor.getCount()) {
               logger.info("no data of type RETRIEVAL_ID"); 
               cursor.close();
               return null;
            }
            paths = this.retrievalSerialize(cursor);
            cursor.close();
            // this.retrievalDeserialize(paths.get(0)); // left in to aid in debugging
            try {
               final File file = paths.get(0);
               logger.info("serialization temp file: " + file.getAbsolutePath());
               final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, imode);
               final FileObserver observer = new FileObserver(file.getCanonicalPath()) {
                  @Override
                  public void onEvent(int event, String path) {
                     switch (event) {
                     case FileObserver.CLOSE_WRITE:
                     case FileObserver.CLOSE_NOWRITE:
                        this.stopWatching();
                        try {
                          logger.info("deleting serialization temp file: " + file.getAbsolutePath());
                          pfd.close();
                        } catch (IOException e) {
                          e.printStackTrace();
                        }
                        file.delete();
                        observerSet.remove(this);
                        return;
                     default:
                        logger.info("unknown file disposition: "+event);
                     }
                  }
               };
               observer.startWatching();
               observerSet.add(observer);
               return pfd;
            } catch (FileNotFoundException e) {
               e.printStackTrace();
            } catch (IOException e1) {
               e1.printStackTrace();
            }
            break;    

         case PUBLICATION_BLOB:
            if (pseg.size() < 3)
                return null;

            try {
                File filePath = blobFile("publication", pseg.get(1), pseg.get(2));
                return ParcelFileDescriptor.open(filePath, imode);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            break;

         case PUBLICATION_SET:
            try {
               final File tempFile = tempFilePath("publication");
               final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                     tempFile, ParcelFileDescriptor.MODE_READ_WRITE);
               final FileObserver observer = new FileObserver(tempFile.getCanonicalPath()) {
                  @Override
                  public void onEvent(int event, String path) {
                     switch (event) {
                     case FileObserver.CLOSE_WRITE:
                        this.stopWatching();
                        try {
                          pfd.close();
                          publicationDeserialize(tempFile);
                        } catch (IOException e) {
                          e.printStackTrace();
                        }
                        tempFile.delete();
                        observerSet.remove(this);
                        return;

                     default:
                        logger.info("unknown file disposition: "+ DistributorDataStore.showEvent(event));
                     }
                  }
               };
               observer.startWatching();
               observerSet.add(observer);
               return pfd;
             } catch (FileNotFoundException e1) {
                e1.printStackTrace();
             } catch (IOException e1) {
                e1.printStackTrace();
             }
             break;

         case PUBLICATION_ID:
            qb = new SQLiteQueryBuilder();
            db = openHelper.getReadableDatabase();
                    
            // Switch on the path in the uri for what we want to query.
            qb.setTables(Tables.PUBLICATION_TBL);
            qb.setProjectionMap(publicationProjectionMap);
            qb.appendWhere(PublicationTableSchema._ID + " = " + uri.getPathSegments().get(1));
            cursor = qb.query(db, null, null, null, null, null, null);
            if (1 > cursor.getCount()) {
               logger.info("no data of type PUBLICATION_ID"); 
               cursor.close();
               return null;
            }
            paths = this.publicationSerialize(cursor);
            cursor.close();
            // this.publicationDeserialize(paths.get(0)); // left in to aid in debugging
            try {
               final File file = paths.get(0);
               logger.info("serialization temp file: " + file.getAbsolutePath());
               final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, imode);
               final FileObserver observer = new FileObserver(file.getCanonicalPath()) {
                  @Override
                  public void onEvent(int event, String path) {
                     switch (event) {
                     case FileObserver.CLOSE_WRITE:
                     case FileObserver.CLOSE_NOWRITE:
                        this.stopWatching();
                        try {
                          logger.info("deleting serialization temp file: " + file.getAbsolutePath());
                          pfd.close();
                        } catch (IOException e) {
                          e.printStackTrace();
                        }
                        file.delete();
                        observerSet.remove(this);
                        return;
                     default:
                        logger.info("unknown file disposition: "+event);
                     }
                  }
               };
               observer.startWatching();
               observerSet.add(observer);
               return pfd;
            } catch (FileNotFoundException e) {
               e.printStackTrace();
            } catch (IOException e1) {
               e1.printStackTrace();
            }
            break;    

         case SUBSCRIPTION_BLOB:
            if (pseg.size() < 3)
                return null;

            try {
                File filePath = blobFile("subscription", pseg.get(1), pseg.get(2));
                return ParcelFileDescriptor.open(filePath, imode);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            break;

         case SUBSCRIPTION_SET:
            try {
               final File tempFile = tempFilePath("subscription");
               final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                     tempFile, ParcelFileDescriptor.MODE_READ_WRITE);
               final FileObserver observer = new FileObserver(tempFile.getCanonicalPath()) {
                  @Override
                  public void onEvent(int event, String path) {
                     switch (event) {
                     case FileObserver.CLOSE_WRITE:
                        this.stopWatching();
                        try {
                          pfd.close();
                          subscriptionDeserialize(tempFile);
                        } catch (IOException e) {
                          e.printStackTrace();
                        }
                        tempFile.delete();
                        observerSet.remove(this);
                        return;

                     default:
                        logger.info("unknown file disposition: "+ DistributorDataStore.showEvent(event));
                     }
                  }
               };
               observer.startWatching();
               observerSet.add(observer);
               return pfd;
             } catch (FileNotFoundException e1) {
                e1.printStackTrace();
             } catch (IOException e1) {
                e1.printStackTrace();
             }
             break;

         case SUBSCRIPTION_ID:
            qb = new SQLiteQueryBuilder();
            db = openHelper.getReadableDatabase();
                    
            // Switch on the path in the uri for what we want to query.
            qb.setTables(Tables.SUBSCRIPTION_TBL);
            qb.setProjectionMap(subscriptionProjectionMap);
            qb.appendWhere(SubscriptionTableSchema._ID + " = " + uri.getPathSegments().get(1));
            cursor = qb.query(db, null, null, null, null, null, null);
            if (1 > cursor.getCount()) {
               logger.info("no data of type SUBSCRIPTION_ID"); 
               cursor.close();
               return null;
            }
            paths = this.subscriptionSerialize(cursor);
            cursor.close();
            // this.subscriptionDeserialize(paths.get(0)); // left in to aid in debugging
            try {
               final File file = paths.get(0);
               logger.info("serialization temp file: " + file.getAbsolutePath());
               final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, imode);
               final FileObserver observer = new FileObserver(file.getCanonicalPath()) {
                  @Override
                  public void onEvent(int event, String path) {
                     switch (event) {
                     case FileObserver.CLOSE_WRITE:
                     case FileObserver.CLOSE_NOWRITE:
                        this.stopWatching();
                        try {
                          logger.info("deleting serialization temp file: " + file.getAbsolutePath());
                          pfd.close();
                        } catch (IOException e) {
                          e.printStackTrace();
                        }
                        file.delete();
                        observerSet.remove(this);
                        return;
                     default:
                        logger.info("unknown file disposition: "+event);
                     }
                  }
               };
               observer.startWatching();
               observerSet.add(observer);
               return pfd;
            } catch (FileNotFoundException e) {
               e.printStackTrace();
            } catch (IOException e1) {
               e1.printStackTrace();
            }
            break;    
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        return null;
    }

   // ===========================================================
   // Content Provider Overrides
   // ===========================================================
   @Override
   public boolean onCreate() {
      this.createDatabaseHelper();
      this.controller = null; // to be set by concrete class
      return true;
   }

      static private final String[] STRING_ARRAY_TYPE = {""};

      @Override
      public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
         
         SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
         
         // Switch on the path in the uri for what we want to query.
         String tableName = null;
         HashMap<String, String> projectionMap = null;
         String orderBy = null;

         switch (uriMatcher.match(uri)) {
               case POSTAL_SET:
                  tableName = Tables.POSTAL_TBL;
                  projectionMap = postalProjectionMap;
                  orderBy = (! TextUtils.isEmpty(sortOrder)) ? sortOrder
                             : PostalTableSchema.DEFAULT_SORT_ORDER;
                  break;
               
               case POSTAL_ID:
                  tableName = Tables.POSTAL_TBL;
                  projectionMap = postalProjectionMap;
                  qb.appendWhere(PostalTableSchema._ID + "="
                        + uri.getPathSegments().get(1));
                  break;

               
               case RETRIEVAL_SET:
                  tableName = Tables.RETRIEVAL_TBL;
                  projectionMap = retrievalProjectionMap;
                  orderBy = (! TextUtils.isEmpty(sortOrder)) ? sortOrder
                             : RetrievalTableSchema.DEFAULT_SORT_ORDER;
                  break;
               
               case RETRIEVAL_ID:
                  tableName = Tables.RETRIEVAL_TBL;
                  projectionMap = retrievalProjectionMap;
                  qb.appendWhere(RetrievalTableSchema._ID + "="
                        + uri.getPathSegments().get(1));
                  break;

               
               case PUBLICATION_SET:
                  tableName = Tables.PUBLICATION_TBL;
                  projectionMap = publicationProjectionMap;
                  orderBy = (! TextUtils.isEmpty(sortOrder)) ? sortOrder
                             : PublicationTableSchema.DEFAULT_SORT_ORDER;
                  break;
               
               case PUBLICATION_ID:
                  tableName = Tables.PUBLICATION_TBL;
                  projectionMap = publicationProjectionMap;
                  qb.appendWhere(PublicationTableSchema._ID + "="
                        + uri.getPathSegments().get(1));
                  break;

               
               case SUBSCRIPTION_SET:
                  tableName = Tables.SUBSCRIPTION_TBL;
                  projectionMap = subscriptionProjectionMap;
                  orderBy = (! TextUtils.isEmpty(sortOrder)) ? sortOrder
                             : SubscriptionTableSchema.DEFAULT_SORT_ORDER;
                  break;
               
               case SUBSCRIPTION_ID:
                  tableName = Tables.SUBSCRIPTION_TBL;
                  projectionMap = subscriptionProjectionMap;
                  qb.appendWhere(SubscriptionTableSchema._ID + "="
                        + uri.getPathSegments().get(1));
                  break;

               
         default:
            throw new IllegalArgumentException("Unknown URI " + uri);
         }
         qb.setTables(tableName);
         qb.setProjectionMap(projectionMap);

         // Get the database and run the query.
         SQLiteDatabase db = openHelper.getReadableDatabase();
         Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

         // Tell the cursor what uri to watch, so it knows when its source data changes.
         cursor.setNotificationUri(getContext().getContentResolver(), uri);
         return cursor;
      }
   
      public Uri addPostal(ContentValues cv) {
         String insertTable = "";
         String nullColumnHack = "";
         Uri tableUri = null;
	  logger.info("insert: " + uri.toString() );

         ContentValues values = (initialValues != null) 
            ? new ContentValues(initialValues)
            : new ContentValues();
         
         /** Validate the requested uri and do default initialization. */
         switch (uriMatcher.match(uri)) {
               case POSTAL_SET:
                  values = this.initializePostalDefaults(values);
                  insertTable = Tables.POSTAL_TBL;
                  nullColumnHack = PostalTableSchema.CP_TYPE;
                  tableUri = PostalTableSchema.CONTENT_URI;
                  break;
               
               case RETRIEVAL_SET:
                  values = this.initializeRetrievalDefaults(values);
                  insertTable = Tables.RETRIEVAL_TBL;
                  nullColumnHack = RetrievalTableSchema.DISPOSITION;
                  tableUri = RetrievalTableSchema.CONTENT_URI;
                  break;
               
               case PUBLICATION_SET:
                  values = this.initializePublicationDefaults(values);
                  insertTable = Tables.PUBLICATION_TBL;
                  nullColumnHack = PublicationTableSchema.DISPOSITION;
                  tableUri = PublicationTableSchema.CONTENT_URI;
                  break;
               
               case SUBSCRIPTION_SET:
                  values = this.initializeSubscriptionDefaults(values);
                  insertTable = Tables.SUBSCRIPTION_TBL;
                  nullColumnHack = SubscriptionTableSchema.DISPOSITION;
                  tableUri = SubscriptionTableSchema.CONTENT_URI;
                  break;
               
            
         default:
            throw new IllegalArgumentException("Unknown URI " + uri);
         }
         
         SQLiteDatabase db = openHelper.getWritableDatabase();

         long rowID = db.insert(insertTable, nullColumnHack, values);
         if (rowID < 1) {
            throw new SQLException("Failed to insert row into " + uri);
         }
         Uri playerURI = ContentUris.withAppendedId(tableUri, rowID);

         //getContext().getContentResolver().notifyChange(uri, null);
	 // TBD SKN - notify change on a row URI, this would still satisfy the broad observers
	 // by setting their notifyForDescendant to true, and would prevent row observers from
	 // getting unnecessary events

         getContext().getContentResolver().notifyChange(playerURI, null);
         return playerURI;
      }

      @Override
      public Uri insert(Uri uri, ContentValues initialValues) {
         String insertTable = "";
         String nullColumnHack = "";
         Uri tableUri = null;

         ContentValues values = (initialValues != null) 
            ? new ContentValues(initialValues)
            : new ContentValues();
         
         /** Validate the requested uri and do default initialization. */
         switch (uriMatcher.match(uri)) {
               case POSTAL_SET:
                  values = this.initializePostalDefaults(values);
                  insertTable = Tables.POSTAL_TBL;
                  nullColumnHack = PostalTableSchema.CP_TYPE;
                  tableUri = PostalTableSchema.CONTENT_URI;
                  break;
               
               case RETRIEVAL_SET:
                  values = this.initializeRetrievalDefaults(values);
                  insertTable = Tables.RETRIEVAL_TBL;
                  nullColumnHack = RetrievalTableSchema.DISPOSITION;
                  tableUri = RetrievalTableSchema.CONTENT_URI;
                  break;
               
               case PUBLICATION_SET:
                  values = this.initializePublicationDefaults(values);
                  insertTable = Tables.PUBLICATION_TBL;
                  nullColumnHack = PublicationTableSchema.DISPOSITION;
                  tableUri = PublicationTableSchema.CONTENT_URI;
                  break;
               
               case SUBSCRIPTION_SET:
                  values = this.initializeSubscriptionDefaults(values);
                  insertTable = Tables.SUBSCRIPTION_TBL;
                  nullColumnHack = SubscriptionTableSchema.DISPOSITION;
                  tableUri = SubscriptionTableSchema.CONTENT_URI;
                  break;
               
            
         default:
            throw new IllegalArgumentException("Unknown URI " + uri);
         }
         
         SQLiteDatabase db = openHelper.getWritableDatabase();

         long rowID = db.insert(insertTable, nullColumnHack, values);
         if (rowID < 1) {
            throw new SQLException("Failed to insert row into " + uri);
         }
         Uri playerURI = ContentUris.withAppendedId(tableUri, rowID);
         getContext().getContentResolver().notifyChange(uri, null);
         return playerURI;
      }

      /** Insert method helper */
      protected ContentValues initializePostalDefaults(ContentValues values) {
         Long now = Long.valueOf(System.currentTimeMillis());
         
           if (!values.containsKey(PostalTableSchema.CP_TYPE)) {
              values.put("\""+PostalTableSchema.CP_TYPE+"\"", "unknown");
           } 
           if (!values.containsKey(PostalTableSchema.URI)) {
              values.put("\""+PostalTableSchema.URI+"\"", "unknown");
           } 
           if (!values.containsKey(PostalTableSchema.NOTICE)) {
              values.put("\""+PostalTableSchema.NOTICE+"\"", "");
           } 
           if (!values.containsKey(PostalTableSchema.PRIORITY)) {
              values.put("\""+PostalTableSchema.PRIORITY+"\"", 0);
           } 
           if (!values.containsKey(PostalTableSchema.SERIALIZE_TYPE)) {
              values.put("\""+PostalTableSchema.SERIALIZE_TYPE+"\"", PostalTableSchema.SERIALIZE_TYPE_INDIRECT);
           } 
           if (!values.containsKey(PostalTableSchema.DISPOSITION)) {
              values.put("\""+PostalTableSchema.DISPOSITION+"\"", PostalTableSchema.DISPOSITION_PENDING);
           } 
           if (!values.containsKey(PostalTableSchema.EXPIRATION)) {
              values.put("\""+PostalTableSchema.EXPIRATION+"\"", now);
           } 
           if (!values.containsKey(PostalTableSchema.UNIT)) {
              values.put("\""+PostalTableSchema.UNIT+"\"", "unknown");
           } 
           if (!values.containsKey(PostalTableSchema.VALUE)) {
              values.put("\""+PostalTableSchema.VALUE+"\"", -1);
           } 
           if (!values.containsKey(PostalTableSchema.DATA)) {
              values.put("\""+PostalTableSchema.DATA+"\"", "");
           } 
           if (!values.containsKey(PostalTableSchema.CREATED_DATE)) {
              values.put("\""+PostalTableSchema.CREATED_DATE+"\"", now);
           } 
           if (!values.containsKey(PostalTableSchema.MODIFIED_DATE)) {
              values.put("\""+PostalTableSchema.MODIFIED_DATE+"\"", now);
           } 
           // if (!values.containsKey(PostalTableSchema._DISPOSITION)) {
           //    values.put("\""+PostalTableSchema._RECEIVED_DATE+"\"", DistributorSchema._RECEIVED_DATE);
           // }
           if (!values.containsKey(PostalTableSchema._DISPOSITION)) {
              values.put("\""+PostalTableSchema._DISPOSITION+"\"", DistributorSchema._DISPOSITION_START);
           }
         return values;
      }
      
      /** Insert method helper */
      protected ContentValues initializeRetrievalDefaults(ContentValues values) {
         Long now = Long.valueOf(System.currentTimeMillis());
         
           if (!values.containsKey(RetrievalTableSchema.DISPOSITION)) {
              values.put("\""+RetrievalTableSchema.DISPOSITION+"\"", RetrievalTableSchema.DISPOSITION_PENDING);
           } 
           if (!values.containsKey(RetrievalTableSchema.NOTICE)) {
              values.put("\""+RetrievalTableSchema.NOTICE+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchema.PRIORITY)) {
              values.put("\""+RetrievalTableSchema.PRIORITY+"\"", 0);
           } 
           if (!values.containsKey(RetrievalTableSchema.URI)) {
              values.put("\""+RetrievalTableSchema.URI+"\"", "unknown");
           } 
           if (!values.containsKey(RetrievalTableSchema.MIME)) {
              values.put("\""+RetrievalTableSchema.MIME+"\"", "unknown");
           } 
           if (!values.containsKey(RetrievalTableSchema.PROJECTION)) {
              values.put("\""+RetrievalTableSchema.PROJECTION+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchema.SELECTION)) {
              values.put("\""+RetrievalTableSchema.SELECTION+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchema.ARGS)) {
              values.put("\""+RetrievalTableSchema.ARGS+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchema.ORDERING)) {
              values.put("\""+RetrievalTableSchema.ORDERING+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchema.CONTINUITY)) {
              values.put("\""+RetrievalTableSchema.CONTINUITY+"\"", RetrievalTableSchema.CONTINUITY_ONCE);
           } 
           if (!values.containsKey(RetrievalTableSchema.CONTINUITY_VALUE)) {
              values.put("\""+RetrievalTableSchema.CONTINUITY_VALUE+"\"", now);
           } 
           if (!values.containsKey(RetrievalTableSchema.EXPIRATION)) {
              values.put("\""+RetrievalTableSchema.EXPIRATION+"\"", now);
           } 
           if (!values.containsKey(RetrievalTableSchema.CREATED_DATE)) {
              values.put("\""+RetrievalTableSchema.CREATED_DATE+"\"", now);
           } 
           if (!values.containsKey(RetrievalTableSchema.MODIFIED_DATE)) {
              values.put("\""+RetrievalTableSchema.MODIFIED_DATE+"\"", now);
           } 
           // if (!values.containsKey(RetrievalTableSchema._DISPOSITION)) {
           //    values.put("\""+RetrievalTableSchema._RECEIVED_DATE+"\"", DistributorSchema._RECEIVED_DATE);
           // }
           if (!values.containsKey(RetrievalTableSchema._DISPOSITION)) {
              values.put("\""+RetrievalTableSchema._DISPOSITION+"\"", DistributorSchema._DISPOSITION_START);
           }
         return values;
      }
      
      /** Insert method helper */
      protected ContentValues initializePublicationDefaults(ContentValues values) {
         Long now = Long.valueOf(System.currentTimeMillis());
         
           if (!values.containsKey(PublicationTableSchema.DISPOSITION)) {
              values.put("\""+PublicationTableSchema.DISPOSITION+"\"", PublicationTableSchema.DISPOSITION_PENDING);
           } 
           if (!values.containsKey(PublicationTableSchema.URI)) {
              values.put("\""+PublicationTableSchema.URI+"\"", "unknown");
           } 
           if (!values.containsKey(PublicationTableSchema.MIME)) {
              values.put("\""+PublicationTableSchema.MIME+"\"", "unknown");
           } 
           if (!values.containsKey(PublicationTableSchema.EXPIRATION)) {
              values.put("\""+PublicationTableSchema.EXPIRATION+"\"", now);
           } 
           if (!values.containsKey(PublicationTableSchema.CREATED_DATE)) {
              values.put("\""+PublicationTableSchema.CREATED_DATE+"\"", now);
           } 
           if (!values.containsKey(PublicationTableSchema.MODIFIED_DATE)) {
              values.put("\""+PublicationTableSchema.MODIFIED_DATE+"\"", now);
           } 
           // if (!values.containsKey(PublicationTableSchema._DISPOSITION)) {
           //    values.put("\""+PublicationTableSchema._RECEIVED_DATE+"\"", DistributorSchema._RECEIVED_DATE);
           // }
           if (!values.containsKey(PublicationTableSchema._DISPOSITION)) {
              values.put("\""+PublicationTableSchema._DISPOSITION+"\"", DistributorSchema._DISPOSITION_START);
           }
         return values;
      }
      
      /** Insert method helper */
      protected ContentValues initializeSubscriptionDefaults(ContentValues values) {
         Long now = Long.valueOf(System.currentTimeMillis());
         
           if (!values.containsKey(SubscriptionTableSchema.DISPOSITION)) {
              values.put("\""+SubscriptionTableSchema.DISPOSITION+"\"", SubscriptionTableSchema.DISPOSITION_PENDING);
           } 
           if (!values.containsKey(SubscriptionTableSchema.URI)) {
              values.put("\""+SubscriptionTableSchema.URI+"\"", "unknown");
           } 
           if (!values.containsKey(SubscriptionTableSchema.MIME)) {
              values.put("\""+SubscriptionTableSchema.MIME+"\"", "unknown");
           } 
           if (!values.containsKey(SubscriptionTableSchema.SELECTION)) {
              values.put("\""+SubscriptionTableSchema.SELECTION+"\"", "");
           } 
           if (!values.containsKey(SubscriptionTableSchema.EXPIRATION)) {
              values.put("\""+SubscriptionTableSchema.EXPIRATION+"\"", now);
           } 
           if (!values.containsKey(SubscriptionTableSchema.NOTICE)) {
              values.put("\""+SubscriptionTableSchema.NOTICE+"\"", "");
           } 
           if (!values.containsKey(SubscriptionTableSchema.PRIORITY)) {
              values.put("\""+SubscriptionTableSchema.PRIORITY+"\"", 0);
           } 
           if (!values.containsKey(SubscriptionTableSchema.CREATED_DATE)) {
              values.put("\""+SubscriptionTableSchema.CREATED_DATE+"\"", now);
           } 
           if (!values.containsKey(SubscriptionTableSchema.MODIFIED_DATE)) {
              values.put("\""+SubscriptionTableSchema.MODIFIED_DATE+"\"", now);
           } 
           // if (!values.containsKey(SubscriptionTableSchema._DISPOSITION)) {
           //    values.put("\""+SubscriptionTableSchema._RECEIVED_DATE+"\"", DistributorSchema._RECEIVED_DATE);
           // }
           if (!values.containsKey(SubscriptionTableSchema._DISPOSITION)) {
              values.put("\""+SubscriptionTableSchema._DISPOSITION+"\"", DistributorSchema._DISPOSITION_START);
           }
         return values;
      }
      
   
   
      @Override
      public int delete(Uri uri, String selection, String[] selectionArgs) {
         SQLiteDatabase db = openHelper.getWritableDatabase();
         final int count;
         Cursor cursor;
         int match = uriMatcher.match(uri);

         logger.info("running delete with uri(" + uri + ") selection(" + selection + ") match(" + match + ")");

         switch (match) {
               case POSTAL_SET:
                  cursor = db.query(Tables.POSTAL_TBL, new String[] {PostalTableSchema._ID}, selection, selectionArgs, null, null, null);
                  logger.info("cursor rows: " + cursor.getCount());
                  if (cursor.moveToFirst()) {
                      do {
                          long rowid = cursor.getLong(cursor.getColumnIndex(PostalTableSchema._ID));
                          String tuple = Long.toString(rowid);
                          logger.info("found rowid (" + rowid + ") and tuple (" + tuple + ") for deletion");
                          try {
                              File file = blobDir(Tables.POSTAL_TBL, tuple);
                              logger.info("deleting directory: " + file.getAbsolutePath());
                              recursiveDelete(file);
                          }
                          catch (IOException ioe) {
                              logger.error("failed to delete cached file during db delete for Tables.POSTAL_TBL:" + tuple + " because " + ioe.getMessage());
                          }
                      }
                      while (cursor.moveToNext());
                  }
                  cursor.close();
                  count = db.delete(Tables.POSTAL_TBL, selection, selectionArgs);
                  break;

               case POSTAL_ID:
                  String postalID = uri.getPathSegments().get(1);
                  cursor = db.query(Tables.POSTAL_TBL, new String[] {PostalTableSchema._ID}, selection, selectionArgs, null, null, null);
                  logger.info("cursor rows: " + cursor.getCount());
                  if (cursor.moveToFirst()) {
                      do {
                          long rowid = cursor.getLong(cursor.getColumnIndex(PostalTableSchema._ID));
                          String tuple = Long.toString(rowid);
                          logger.info("found rowid (" + rowid + ") and tuple (" + tuple + ") for deletion");
                          try {
                              File file = blobDir(Tables.POSTAL_TBL, tuple);
                              logger.info("deleting directory: " + file.getAbsolutePath());
                              recursiveDelete(file);
                          }
                          catch (IOException ioe) {
                              logger.error("failed to delete cached file during db delete for Tables.POSTAL_TBL:" + tuple + " because " + ioe.getMessage());
                          }
                      }
                      while (cursor.moveToNext());
                  }
                  cursor.close();
                  count = db.delete(Tables.POSTAL_TBL,
                        PostalTableSchema._ID
                              + "="
                              + postalID
                              + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                              selectionArgs);
                  break;
               
               case RETRIEVAL_SET:
                  cursor = db.query(Tables.RETRIEVAL_TBL, new String[] {RetrievalTableSchema._ID}, selection, selectionArgs, null, null, null);
                  logger.info("cursor rows: " + cursor.getCount());
                  if (cursor.moveToFirst()) {
                      do {
                          long rowid = cursor.getLong(cursor.getColumnIndex(RetrievalTableSchema._ID));
                          String tuple = Long.toString(rowid);
                          logger.info("found rowid (" + rowid + ") and tuple (" + tuple + ") for deletion");
                          try {
                              File file = blobDir(Tables.RETRIEVAL_TBL, tuple);
                              logger.info("deleting directory: " + file.getAbsolutePath());
                              recursiveDelete(file);
                          }
                          catch (IOException ioe) {
                              logger.error("failed to delete cached file during db delete for Tables.RETRIEVAL_TBL:" + tuple + " because " + ioe.getMessage());
                          }
                      }
                      while (cursor.moveToNext());
                  }
                  cursor.close();
                  count = db.delete(Tables.RETRIEVAL_TBL, selection, selectionArgs);
                  break;

               case RETRIEVAL_ID:
                  String retrievalID = uri.getPathSegments().get(1);
                  cursor = db.query(Tables.RETRIEVAL_TBL, new String[] {RetrievalTableSchema._ID}, selection, selectionArgs, null, null, null);
                  logger.info("cursor rows: " + cursor.getCount());
                  if (cursor.moveToFirst()) {
                      do {
                          long rowid = cursor.getLong(cursor.getColumnIndex(RetrievalTableSchema._ID));
                          String tuple = Long.toString(rowid);
                          logger.info("found rowid (" + rowid + ") and tuple (" + tuple + ") for deletion");
                          try {
                              File file = blobDir(Tables.RETRIEVAL_TBL, tuple);
                              logger.info("deleting directory: " + file.getAbsolutePath());
                              recursiveDelete(file);
                          }
                          catch (IOException ioe) {
                              logger.error("failed to delete cached file during db delete for Tables.RETRIEVAL_TBL:" + tuple + " because " + ioe.getMessage());
                          }
                      }
                      while (cursor.moveToNext());
                  }
                  cursor.close();
                  count = db.delete(Tables.RETRIEVAL_TBL,
                        RetrievalTableSchema._ID
                              + "="
                              + retrievalID
                              + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                              selectionArgs);
                  break;
               
               case PUBLICATION_SET:
                  cursor = db.query(Tables.PUBLICATION_TBL, new String[] {PublicationTableSchema._ID}, selection, selectionArgs, null, null, null);
                  logger.info("cursor rows: " + cursor.getCount());
                  if (cursor.moveToFirst()) {
                      do {
                          long rowid = cursor.getLong(cursor.getColumnIndex(PublicationTableSchema._ID));
                          String tuple = Long.toString(rowid);
                          logger.info("found rowid (" + rowid + ") and tuple (" + tuple + ") for deletion");
                          try {
                              File file = blobDir(Tables.PUBLICATION_TBL, tuple);
                              logger.info("deleting directory: " + file.getAbsolutePath());
                              recursiveDelete(file);
                          }
                          catch (IOException ioe) {
                              logger.error("failed to delete cached file during db delete for Tables.PUBLICATION_TBL:" + tuple + " because " + ioe.getMessage());
                          }
                      }
                      while (cursor.moveToNext());
                  }
                  cursor.close();
                  count = db.delete(Tables.PUBLICATION_TBL, selection, selectionArgs);
                  break;

               case PUBLICATION_ID:
                  String publicationID = uri.getPathSegments().get(1);
                  cursor = db.query(Tables.PUBLICATION_TBL, new String[] {PublicationTableSchema._ID}, selection, selectionArgs, null, null, null);
                  logger.info("cursor rows: " + cursor.getCount());
                  if (cursor.moveToFirst()) {
                      do {
                          long rowid = cursor.getLong(cursor.getColumnIndex(PublicationTableSchema._ID));
                          String tuple = Long.toString(rowid);
                          logger.info("found rowid (" + rowid + ") and tuple (" + tuple + ") for deletion");
                          try {
                              File file = blobDir(Tables.PUBLICATION_TBL, tuple);
                              logger.info("deleting directory: " + file.getAbsolutePath());
                              recursiveDelete(file);
                          }
                          catch (IOException ioe) {
                              logger.error("failed to delete cached file during db delete for Tables.PUBLICATION_TBL:" + tuple + " because " + ioe.getMessage());
                          }
                      }
                      while (cursor.moveToNext());
                  }
                  cursor.close();
                  count = db.delete(Tables.PUBLICATION_TBL,
                        PublicationTableSchema._ID
                              + "="
                              + publicationID
                              + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                              selectionArgs);
                  break;
               
               case SUBSCRIPTION_SET:
                  cursor = db.query(Tables.SUBSCRIPTION_TBL, new String[] {SubscriptionTableSchema._ID}, selection, selectionArgs, null, null, null);
                  logger.info("cursor rows: " + cursor.getCount());
                  if (cursor.moveToFirst()) {
                      do {
                          long rowid = cursor.getLong(cursor.getColumnIndex(SubscriptionTableSchema._ID));
                          String tuple = Long.toString(rowid);
                          logger.info("found rowid (" + rowid + ") and tuple (" + tuple + ") for deletion");
                          try {
                              File file = blobDir(Tables.SUBSCRIPTION_TBL, tuple);
                              logger.info("deleting directory: " + file.getAbsolutePath());
                              recursiveDelete(file);
                          }
                          catch (IOException ioe) {
                              logger.error("failed to delete cached file during db delete for Tables.SUBSCRIPTION_TBL:" + tuple + " because " + ioe.getMessage());
                          }
                      }
                      while (cursor.moveToNext());
                  }
                  cursor.close();
                  count = db.delete(Tables.SUBSCRIPTION_TBL, selection, selectionArgs);
                  break;

               case SUBSCRIPTION_ID:
                  String subscriptionID = uri.getPathSegments().get(1);
                  cursor = db.query(Tables.SUBSCRIPTION_TBL, new String[] {SubscriptionTableSchema._ID}, selection, selectionArgs, null, null, null);
                  logger.info("cursor rows: " + cursor.getCount());
                  if (cursor.moveToFirst()) {
                      do {
                          long rowid = cursor.getLong(cursor.getColumnIndex(SubscriptionTableSchema._ID));
                          String tuple = Long.toString(rowid);
                          logger.info("found rowid (" + rowid + ") and tuple (" + tuple + ") for deletion");
                          try {
                              File file = blobDir(Tables.SUBSCRIPTION_TBL, tuple);
                              logger.info("deleting directory: " + file.getAbsolutePath());
                              recursiveDelete(file);
                          }
                          catch (IOException ioe) {
                              logger.error("failed to delete cached file during db delete for Tables.SUBSCRIPTION_TBL:" + tuple + " because " + ioe.getMessage());
                          }
                      }
                      while (cursor.moveToNext());
                  }
                  cursor.close();
                  count = db.delete(Tables.SUBSCRIPTION_TBL,
                        SubscriptionTableSchema._ID
                              + "="
                              + subscriptionID
                              + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                              selectionArgs);
                  break;
               
            
         default:
            throw new IllegalArgumentException("Unknown URI " + uri);
         }

         if (count > 0) getContext().getContentResolver().notifyChange(uri, null);
         return count;
      }



      @Override
      public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
         SQLiteDatabase db = openHelper.getWritableDatabase();
         Uri notifyUri = uri;
         int count;
	  logger.debug("update: " + uri.toString() );

         switch (uriMatcher.match(uri)) {
               case POSTAL_SET:
                  logger.debug("POSTAL_SET");
                  count = db.update(Tables.POSTAL_TBL, values, selection,
                        selectionArgs);
                  break;

               case POSTAL_ID:
                  logger.debug("POSTAL_ID");
                  //  notify on the base URI - without the ID ?
                  // notifyUri = PostalTableSchema.CONTENT_URI;    --- TBD SKN MOD
                  String postalID = uri.getPathSegments().get(1);
                  count = db.update(Tables.POSTAL_TBL, values, PostalTableSchema._ID
                        + "="
                        + postalID
                        + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                        selectionArgs);
                  break;
               
               case RETRIEVAL_SET:
                  logger.debug("RETRIEVAL_SET");
                  count = db.update(Tables.RETRIEVAL_TBL, values, selection,
                        selectionArgs);
                  break;

               case RETRIEVAL_ID:
                  logger.debug("RETRIEVAL_ID");
                  //  notify on the base URI - without the ID ?
                  // notifyUri = RetrievalTableSchema.CONTENT_URI; 
                  String retrievalID = uri.getPathSegments().get(1);
                  count = db.update(Tables.RETRIEVAL_TBL, values, RetrievalTableSchema._ID
                        + "="
                        + retrievalID
                        + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                        selectionArgs);
                  break;
               
               case PUBLICATION_SET:
                  logger.debug("PUBLICATION_SET");
                  count = db.update(Tables.PUBLICATION_TBL, values, selection,
                        selectionArgs);
                  break;

               case PUBLICATION_ID:
                  logger.debug("PUBLICATION_ID");
                  //  notify on the base URI - without the ID ?
                  // notifyUri = PublicationTableSchema.CONTENT_URI; 
                  String publicationID = uri.getPathSegments().get(1);
                  count = db.update(Tables.PUBLICATION_TBL, values, PublicationTableSchema._ID
                        + "="
                        + publicationID
                        + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                        selectionArgs);
                  break;
               
               case SUBSCRIPTION_SET:
                  logger.debug("SUBSCRIPTION_SET");
                  count = db.update(Tables.SUBSCRIPTION_TBL, values, selection,
                        selectionArgs);
                  break;

               case SUBSCRIPTION_ID:
                  logger.debug("SUBSCRIPTION_ID");
                  //  notify on the base URI - without the ID ?
                  // notifyUri = SubscriptionTableSchema.CONTENT_URI; 
                  String subscriptionID = uri.getPathSegments().get(1);
                  count = db.update(Tables.SUBSCRIPTION_TBL, values, SubscriptionTableSchema._ID
                        + "="
                        + subscriptionID
                        + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                        selectionArgs);
                  break;
               
            
         default:
            throw new IllegalArgumentException("Unknown URI " + uri);
         }

         getContext().getContentResolver().notifyChange(notifyUri, null);
         return count;   
      }
      @Override
      public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
         SQLiteDatabase db = openHelper.getWritableDatabase();
         Uri notifyUri = uri;
         int count;
         switch (uriMatcher.match(uri)) {
               case POSTAL_SET:
                  logger.debug("POSTAL_SET");
                  count = db.update(Tables.POSTAL_TBL, values, selection,
                        selectionArgs);
                  break;

               case POSTAL_ID:
                  logger.debug("POSTAL_ID");
                  //  notify on the base URI - without the ID ?
                  notifyUri = PostalTableSchema.CONTENT_URI; 
                  String postalID = uri.getPathSegments().get(1);
                  count = db.update(Tables.POSTAL_TBL, values, PostalTableSchema._ID
                        + "="
                        + postalID
                        + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                        selectionArgs);
                  break;
               
               case RETRIEVAL_SET:
                  logger.debug("RETRIEVAL_SET");
                  count = db.update(Tables.RETRIEVAL_TBL, values, selection,
                        selectionArgs);
                  break;

               case RETRIEVAL_ID:
                  logger.debug("RETRIEVAL_ID");
                  //  notify on the base URI - without the ID ?
                  notifyUri = RetrievalTableSchema.CONTENT_URI; 
                  String retrievalID = uri.getPathSegments().get(1);
                  count = db.update(Tables.RETRIEVAL_TBL, values, RetrievalTableSchema._ID
                        + "="
                        + retrievalID
                        + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                        selectionArgs);
                  break;
               
               case PUBLICATION_SET:
                  logger.debug("PUBLICATION_SET");
                  count = db.update(Tables.PUBLICATION_TBL, values, selection,
                        selectionArgs);
                  break;

               case PUBLICATION_ID:
                  logger.debug("PUBLICATION_ID");
                  //  notify on the base URI - without the ID ?
                  notifyUri = PublicationTableSchema.CONTENT_URI; 
                  String publicationID = uri.getPathSegments().get(1);
                  count = db.update(Tables.PUBLICATION_TBL, values, PublicationTableSchema._ID
                        + "="
                        + publicationID
                        + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                        selectionArgs);
                  break;
               
               case SUBSCRIPTION_SET:
                  logger.debug("SUBSCRIPTION_SET");
                  count = db.update(Tables.SUBSCRIPTION_TBL, values, selection,
                        selectionArgs);
                  break;

               case SUBSCRIPTION_ID:
                  logger.debug("SUBSCRIPTION_ID");
                  //  notify on the base URI - without the ID ?
                  notifyUri = SubscriptionTableSchema.CONTENT_URI; 
                  String subscriptionID = uri.getPathSegments().get(1);
                  count = db.update(Tables.SUBSCRIPTION_TBL, values, SubscriptionTableSchema._ID
                        + "="
                        + subscriptionID
                        + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                        selectionArgs);
                  break;
               
            
         default:
            throw new IllegalArgumentException("Unknown URI " + uri);
         }

         getContext().getContentResolver().notifyChange(notifyUri, null);
         return count;   
      }

   @Override
   public String getType(Uri uri) {
      switch (uriMatcher.match(uri)) {
            case POSTAL_SET:
            case POSTAL_ID:
               return PostalTableSchema.CONTENT_ITEM_TYPE;
            
            case RETRIEVAL_SET:
            case RETRIEVAL_ID:
               return RetrievalTableSchema.CONTENT_ITEM_TYPE;
            
            case PUBLICATION_SET:
            case PUBLICATION_ID:
               return PublicationTableSchema.CONTENT_ITEM_TYPE;
            
            case SUBSCRIPTION_SET:
            case SUBSCRIPTION_ID:
               return SubscriptionTableSchema.CONTENT_ITEM_TYPE;
            
         
      default:
         throw new IllegalArgumentException("Unknown URI " + uri);
      }   
   }
   
   // ===========================================================
   // Static declarations
   // ===========================================================
   static {
      uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
            uriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.POSTAL_TBL, POSTAL_SET);
            uriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.POSTAL_TBL + "/#", POSTAL_ID);
            
            uriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.RETRIEVAL_TBL, RETRIEVAL_SET);
            uriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.RETRIEVAL_TBL + "/#", RETRIEVAL_ID);
            
            uriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.PUBLICATION_TBL, PUBLICATION_SET);
            uriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.PUBLICATION_TBL + "/#", PUBLICATION_ID);
            
            uriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.SUBSCRIPTION_TBL, SUBSCRIPTION_SET);
            uriMatcher.addURI(DistributorSchema.AUTHORITY, Tables.SUBSCRIPTION_TBL + "/#", SUBSCRIPTION_ID);
            
      
      HashMap<String, String> columns;
            postalProjectionKey = new String[1];
            postalProjectionKey[0] = PostalTableSchema._ID;

            columns = new HashMap<String, String>();
            columns.put(PostalTableSchema._ID, PostalTableSchema._ID);
               columns.put(PostalTableSchema.CP_TYPE, "\""+PostalTableSchema.CP_TYPE+"\""); 
               columns.put(PostalTableSchema.URI, "\""+PostalTableSchema.URI+"\""); 
               columns.put(PostalTableSchema.NOTICE, "\""+PostalTableSchema.NOTICE+"\""); 
               columns.put(PostalTableSchema.PRIORITY, "\""+PostalTableSchema.PRIORITY+"\""); 
               columns.put(PostalTableSchema.SERIALIZE_TYPE, "\""+PostalTableSchema.SERIALIZE_TYPE+"\""); 
               columns.put(PostalTableSchema.DISPOSITION, "\""+PostalTableSchema.DISPOSITION+"\""); 
               columns.put(PostalTableSchema.EXPIRATION, "\""+PostalTableSchema.EXPIRATION+"\""); 
               columns.put(PostalTableSchema.UNIT, "\""+PostalTableSchema.UNIT+"\""); 
               columns.put(PostalTableSchema.VALUE, "\""+PostalTableSchema.VALUE+"\""); 
               columns.put(PostalTableSchema.DATA, "\""+PostalTableSchema.DATA+"\""); 
               columns.put(PostalTableSchema.CREATED_DATE, "\""+PostalTableSchema.CREATED_DATE+"\""); 
               columns.put(PostalTableSchema.MODIFIED_DATE, "\""+PostalTableSchema.MODIFIED_DATE+"\""); 
               columns.put(PostalTableSchema._RECEIVED_DATE, "\""+PostalTableSchema._RECEIVED_DATE+"\"");
               columns.put(PostalTableSchema._DISPOSITION, "\""+PostalTableSchema._DISPOSITION+"\"");

            postalProjectionMap = columns;
            
            retrievalProjectionKey = new String[1];
            retrievalProjectionKey[0] = RetrievalTableSchema._ID;

            columns = new HashMap<String, String>();
            columns.put(RetrievalTableSchema._ID, RetrievalTableSchema._ID);
               columns.put(RetrievalTableSchema.DISPOSITION, "\""+RetrievalTableSchema.DISPOSITION+"\""); 
               columns.put(RetrievalTableSchema.NOTICE, "\""+RetrievalTableSchema.NOTICE+"\""); 
               columns.put(RetrievalTableSchema.PRIORITY, "\""+RetrievalTableSchema.PRIORITY+"\""); 
               columns.put(RetrievalTableSchema.URI, "\""+RetrievalTableSchema.URI+"\""); 
               columns.put(RetrievalTableSchema.MIME, "\""+RetrievalTableSchema.MIME+"\""); 
               columns.put(RetrievalTableSchema.PROJECTION, "\""+RetrievalTableSchema.PROJECTION+"\""); 
               columns.put(RetrievalTableSchema.SELECTION, "\""+RetrievalTableSchema.SELECTION+"\""); 
               columns.put(RetrievalTableSchema.ARGS, "\""+RetrievalTableSchema.ARGS+"\""); 
               columns.put(RetrievalTableSchema.ORDERING, "\""+RetrievalTableSchema.ORDERING+"\""); 
               columns.put(RetrievalTableSchema.CONTINUITY, "\""+RetrievalTableSchema.CONTINUITY+"\""); 
               columns.put(RetrievalTableSchema.CONTINUITY_VALUE, "\""+RetrievalTableSchema.CONTINUITY_VALUE+"\""); 
               columns.put(RetrievalTableSchema.EXPIRATION, "\""+RetrievalTableSchema.EXPIRATION+"\""); 
               columns.put(RetrievalTableSchema.CREATED_DATE, "\""+RetrievalTableSchema.CREATED_DATE+"\""); 
               columns.put(RetrievalTableSchema.MODIFIED_DATE, "\""+RetrievalTableSchema.MODIFIED_DATE+"\""); 
               columns.put(RetrievalTableSchema._RECEIVED_DATE, "\""+RetrievalTableSchema._RECEIVED_DATE+"\"");
               columns.put(RetrievalTableSchema._DISPOSITION, "\""+RetrievalTableSchema._DISPOSITION+"\"");

            retrievalProjectionMap = columns;
            
            publicationProjectionKey = new String[1];
            publicationProjectionKey[0] = PublicationTableSchema._ID;

            columns = new HashMap<String, String>();
            columns.put(PublicationTableSchema._ID, PublicationTableSchema._ID);
               columns.put(PublicationTableSchema.DISPOSITION, "\""+PublicationTableSchema.DISPOSITION+"\""); 
               columns.put(PublicationTableSchema.URI, "\""+PublicationTableSchema.URI+"\""); 
               columns.put(PublicationTableSchema.MIME, "\""+PublicationTableSchema.MIME+"\""); 
               columns.put(PublicationTableSchema.EXPIRATION, "\""+PublicationTableSchema.EXPIRATION+"\""); 
               columns.put(PublicationTableSchema.CREATED_DATE, "\""+PublicationTableSchema.CREATED_DATE+"\""); 
               columns.put(PublicationTableSchema.MODIFIED_DATE, "\""+PublicationTableSchema.MODIFIED_DATE+"\""); 
               columns.put(PublicationTableSchema._RECEIVED_DATE, "\""+PublicationTableSchema._RECEIVED_DATE+"\"");
               columns.put(PublicationTableSchema._DISPOSITION, "\""+PublicationTableSchema._DISPOSITION+"\"");

            publicationProjectionMap = columns;
            
            subscriptionProjectionKey = new String[1];
            subscriptionProjectionKey[0] = SubscriptionTableSchema._ID;

            columns = new HashMap<String, String>();
            columns.put(SubscriptionTableSchema._ID, SubscriptionTableSchema._ID);
               columns.put(SubscriptionTableSchema.DISPOSITION, "\""+SubscriptionTableSchema.DISPOSITION+"\""); 
               columns.put(SubscriptionTableSchema.URI, "\""+SubscriptionTableSchema.URI+"\""); 
               columns.put(SubscriptionTableSchema.MIME, "\""+SubscriptionTableSchema.MIME+"\""); 
               columns.put(SubscriptionTableSchema.SELECTION, "\""+SubscriptionTableSchema.SELECTION+"\""); 
               columns.put(SubscriptionTableSchema.EXPIRATION, "\""+SubscriptionTableSchema.EXPIRATION+"\""); 
               columns.put(SubscriptionTableSchema.NOTICE, "\""+SubscriptionTableSchema.NOTICE+"\""); 
               columns.put(SubscriptionTableSchema.PRIORITY, "\""+SubscriptionTableSchema.PRIORITY+"\""); 
               columns.put(SubscriptionTableSchema.CREATED_DATE, "\""+SubscriptionTableSchema.CREATED_DATE+"\""); 
               columns.put(SubscriptionTableSchema.MODIFIED_DATE, "\""+SubscriptionTableSchema.MODIFIED_DATE+"\""); 
               columns.put(SubscriptionTableSchema._RECEIVED_DATE, "\""+SubscriptionTableSchema._RECEIVED_DATE+"\"");
               columns.put(SubscriptionTableSchema._DISPOSITION, "\""+SubscriptionTableSchema._DISPOSITION+"\"");

            subscriptionProjectionMap = columns;
            
   }

   public void setController(BroadcastReceiver controller, IntentFilter filter) {
       this.controller = controller;
       Context context = this.getContext();
       context.registerReceiver(controller, filter);
   }



   static public final File applDir;
   static public final File applCacheDir;

   static public final File applCachePostalDir;

   static public final File applCacheRetrievalDir;

   static public final File applCachePublicationDir;

   static public final File applCacheSubscriptionDir;


   static public final File applTempDir;
   static {
       applDir = new File(Environment.getExternalStorageDirectory(), "support/edu.vu.isis.ammo.core"); 
       applDir.mkdirs();
       if (! applDir.mkdirs()) {
         logger.error("cannot create files check permissions in manifest : " + applDir.toString());
       }

       applCacheDir = new File(applDir, "cache/distributor"); 
       applCacheDir.mkdirs();

       applCachePostalDir = new File(applCacheDir, "postal"); 
       applCacheDir.mkdirs();

       applCacheRetrievalDir = new File(applCacheDir, "retrieval"); 
       applCacheDir.mkdirs();

       applCachePublicationDir = new File(applCacheDir, "publication"); 
       applCacheDir.mkdirs();

       applCacheSubscriptionDir = new File(applCacheDir, "subscription"); 
       applCacheDir.mkdirs();


       applTempDir = new File(applDir, "tmp/distributor"); 
       applTempDir.mkdirs();
   }

   protected File blobFile(String table, String tuple, String field) throws IOException {
      File tupleCacheDir = blobDir(table, tuple);
      File cacheFile = new File(tupleCacheDir, field+".blob");
      if (cacheFile.exists()) return cacheFile;    

      cacheFile.createNewFile();
      return cacheFile;
   }

   protected File blobDir(String table, String tuple) throws IOException {
      File tableCacheDir = new File(applCacheDir, table);
      File tupleCacheDir = new File(tableCacheDir, tuple);
      if (!tupleCacheDir.exists()) tupleCacheDir.mkdirs();
      return tupleCacheDir;
   }

   protected File tempFilePath(String table) throws IOException {
      return File.createTempFile(table, ".tmp", applTempDir);
   }


   protected void clearBlobCache(String table, String tuple) {
      if (table == null) {
        if (applCacheDir.isDirectory()) {
           for (File child : applCacheDir.listFiles()) {
               recursiveDelete(child);
           }
           return;
        }
      }
      File tableCacheDir = new File(applCacheDir, table);
      if (tuple == null) {
        if (tableCacheDir.isDirectory()) {
           for (File child : tableCacheDir.listFiles()) {
               recursiveDelete(child);
           }
           return;
        }
      }
      File tupleCacheDir = new File(tableCacheDir, tuple);
      if (tupleCacheDir.isDirectory()) {
         for (File child : tupleCacheDir.listFiles()) {
            recursiveDelete(child);
         }
      }
   }

   /** 
    * Recursively delete all children of this directory and the directory itself.
    * 
    * @param dir
    */
   protected void recursiveDelete(File dir) {
       if (!dir.exists()) return;

       if (dir.isFile()) {
           dir.delete();
           return;
       }
       if (dir.isDirectory()) {
           for (File child : dir.listFiles()) {
               recursiveDelete(child);
           }
           dir.delete();
           return;
       }
   }
} 

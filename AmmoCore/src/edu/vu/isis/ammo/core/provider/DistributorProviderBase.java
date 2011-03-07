// THIS IS GENERATED CODE, MAKE SURE ANY CHANGES MADE HERE ARE PROPAGATED INTO THE GENERATOR TEMPLATES
package edu.vu.isis.ammo.core.provider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentFilter;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.os.FileObserver;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.DeliveryMechanismTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.PostalTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.PublicationTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.RetrievalTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.SubscriptionTableSchemaBase;


// BEGIN CUSTOM Distributor IMPORTS
// END   CUSTOM  Distributor IMPORTS

public abstract class DistributorProviderBase extends ContentProvider {


// Table definitions 
public interface Tables {
      public static final String DELIVERY_MECHANISM_TBL = "delivery_mechanism";
         public static final String POSTAL_TBL = "postal";
         public static final String RETRIEVAL_TBL = "retrieval";
         public static final String PUBLICATION_TBL = "publication";
         public static final String SUBSCRIPTION_TBL = "subscription";
      
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
      super(context, DistributorSchemaBase.DATABASE_NAME, 
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
         * Table Name: delivery mechanism <P>
         */
        db.execSQL("CREATE TABLE \"" + Tables.DELIVERY_MECHANISM_TBL + "\" (" 
          + "\""+DeliveryMechanismTableSchemaBase.CONN_TYPE + "\" INTEGER, " 
          + "\""+DeliveryMechanismTableSchemaBase.STATUS + "\" TEXT, " 
          + "\""+DeliveryMechanismTableSchemaBase.UNIT + "\" TEXT, " 
          + "\""+DeliveryMechanismTableSchemaBase.COST_UP + "\" INTEGER, " 
          + "\""+DeliveryMechanismTableSchemaBase.COST_DOWN + "\" INTEGER, " 
          + "\""+DeliveryMechanismTableSchemaBase._ID + "\" INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "\""+DeliveryMechanismTableSchemaBase._DISPOSITION + "\" INTEGER );" ); 
        /** 
         * Table Name: postal <P>
         */
        db.execSQL("CREATE TABLE \"" + Tables.POSTAL_TBL + "\" (" 
          + "\""+PostalTableSchemaBase.CP_TYPE + "\" TEXT, " 
          + "\""+PostalTableSchemaBase.URI + "\" TEXT, " 
          + "\""+PostalTableSchemaBase.NOTICE + "\" BLOB, " 
          + "\""+PostalTableSchemaBase.SERIALIZE_TYPE + "\" INTEGER, " 
          + "\""+PostalTableSchemaBase.DISPOSITION + "\" INTEGER, " 
          + "\""+PostalTableSchemaBase.EXPIRATION + "\" INTEGER, " 
          + "\""+PostalTableSchemaBase.UNIT + "\" TEXT, " 
          + "\""+PostalTableSchemaBase.VALUE + "\" INTEGER, " 
          + "\""+PostalTableSchemaBase.DATA + "\" TEXT, " 
          + "\""+PostalTableSchemaBase.CREATED_DATE + "\" INTEGER, " 
          + "\""+PostalTableSchemaBase.MODIFIED_DATE + "\" INTEGER, " 
          + "\""+PostalTableSchemaBase._ID + "\" INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "\""+PostalTableSchemaBase._DISPOSITION + "\" INTEGER );" ); 
        /** 
         * Table Name: retrieval <P>
         */
        db.execSQL("CREATE TABLE \"" + Tables.RETRIEVAL_TBL + "\" (" 
          + "\""+RetrievalTableSchemaBase.DISPOSITION + "\" INTEGER, " 
          + "\""+RetrievalTableSchemaBase.NOTICE + "\" BLOB, " 
          + "\""+RetrievalTableSchemaBase.URI + "\" TEXT, " 
          + "\""+RetrievalTableSchemaBase.MIME + "\" TEXT, " 
          + "\""+RetrievalTableSchemaBase.PROJECTION + "\" TEXT, " 
          + "\""+RetrievalTableSchemaBase.SELECTION + "\" TEXT, " 
          + "\""+RetrievalTableSchemaBase.ARGS + "\" TEXT, " 
          + "\""+RetrievalTableSchemaBase.ORDERING + "\" TEXT, " 
          + "\""+RetrievalTableSchemaBase.CONTINUITY + "\" INTEGER, " 
          + "\""+RetrievalTableSchemaBase.CONTINUITY_VALUE + "\" INTEGER, " 
          + "\""+RetrievalTableSchemaBase.EXPIRATION + "\" INTEGER, " 
          + "\""+RetrievalTableSchemaBase.CREATED_DATE + "\" INTEGER, " 
          + "\""+RetrievalTableSchemaBase.MODIFIED_DATE + "\" INTEGER, " 
          + "\""+RetrievalTableSchemaBase._ID + "\" INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "\""+RetrievalTableSchemaBase._DISPOSITION + "\" INTEGER );" ); 
        /** 
         * Table Name: publication <P>
         */
        db.execSQL("CREATE TABLE \"" + Tables.PUBLICATION_TBL + "\" (" 
          + "\""+PublicationTableSchemaBase.DISPOSITION + "\" INTEGER, " 
          + "\""+PublicationTableSchemaBase.URI + "\" TEXT, " 
          + "\""+PublicationTableSchemaBase.MIME + "\" TEXT, " 
          + "\""+PublicationTableSchemaBase.EXPIRATION + "\" INTEGER, " 
          + "\""+PublicationTableSchemaBase.CREATED_DATE + "\" INTEGER, " 
          + "\""+PublicationTableSchemaBase.MODIFIED_DATE + "\" INTEGER, " 
          + "\""+PublicationTableSchemaBase._ID + "\" INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "\""+PublicationTableSchemaBase._DISPOSITION + "\" INTEGER );" ); 
        /** 
         * Table Name: subscription <P>
         */
        db.execSQL("CREATE TABLE \"" + Tables.SUBSCRIPTION_TBL + "\" (" 
          + "\""+SubscriptionTableSchemaBase.DISPOSITION + "\" INTEGER, " 
          + "\""+SubscriptionTableSchemaBase.URI + "\" TEXT, " 
          + "\""+SubscriptionTableSchemaBase.MIME + "\" TEXT, " 
          + "\""+SubscriptionTableSchemaBase.SELECTION + "\" TEXT, " 
          + "\""+SubscriptionTableSchemaBase.EXPIRATION + "\" INTEGER, " 
          + "\""+SubscriptionTableSchemaBase.NOTICE + "\" BLOB, " 
          + "\""+SubscriptionTableSchemaBase.CREATED_DATE + "\" INTEGER, " 
          + "\""+SubscriptionTableSchemaBase.MODIFIED_DATE + "\" INTEGER, " 
          + "\""+SubscriptionTableSchemaBase._ID + "\" INTEGER PRIMARY KEY AUTOINCREMENT, "
          + "\""+SubscriptionTableSchemaBase._DISPOSITION + "\" INTEGER );" ); 

        preloadTables(db);
        createViews(db);
        createTriggers(db);
             
        } catch (SQLException ex) {
           ex.printStackTrace();
        }
   }

   @Override
   public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      logger.warn( "Upgrading database from version " + oldVersion + " to "
            + newVersion + ", which will destroy all old data");
         db.execSQL("DROP TABLE IF EXISTS \"" + Tables.DELIVERY_MECHANISM_TBL + "\";");
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
* Table Name: delivery mechanism <P>
*/
static public class DeliveryMechanismWrapper {
   public DeliveryMechanismWrapper() {
     // logger.info("building DeliveryMechanismWrapper");
   }
     private int connType;
     public int getConnType() {
       return this.connType;
     }
     public DeliveryMechanismWrapper setConnType(int val) {
       this.connType = val;
       return this;
     } 
     private String status;
     public String getStatus() {
       return this.status;
     }
     public DeliveryMechanismWrapper setStatus(String val) {
       this.status = val;
       return this;
     } 
     private String unit;
     public String getUnit() {
       return this.unit;
     }
     public DeliveryMechanismWrapper setUnit(String val) {
       this.unit = val;
       return this;
     } 
     private int costUp;
     public int getCostUp() {
       return this.costUp;
     }
     public DeliveryMechanismWrapper setCostUp(int val) {
       this.costUp = val;
       return this;
     } 
     private int costDown;
     public int getCostDown() {
       return this.costDown;
     }
     public DeliveryMechanismWrapper setCostDown(int val) {
       this.costDown = val;
       return this;
     } 
     private int _disposition;
     public int get_Disposition() {
       return this._disposition;
     }
     public DeliveryMechanismWrapper set_Disposition(int val) {
       this._disposition = val;
       return this;
     }
} 
/**
* Table Name: postal <P>
*/
static public class PostalWrapper {
   public PostalWrapper() {
     // logger.info("building PostalWrapper");
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
} 
/**
* Table Name: retrieval <P>
*/
static public class RetrievalWrapper {
   public RetrievalWrapper() {
     // logger.info("building RetrievalWrapper");
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
} 
/**
* Table Name: publication <P>
*/
static public class PublicationWrapper {
   public PublicationWrapper() {
     // logger.info("building PublicationWrapper");
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
} 
/**
* Table Name: subscription <P>
*/
static public class SubscriptionWrapper {
   public SubscriptionWrapper() {
     // logger.info("building SubscriptionWrapper");
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
} 

  static private File dirDeliveryMechanism = 
           new File(Environment.getExternalStorageDirectory(),
                    "ammo_cp_delivery_mechanism_cache");

  public long deliveryMechanismDeserializer(File file) {
     logger.debug("::deliveryMechanismdeserializer");
     InputStream ins;
     try {
        ins = new FileInputStream(file);
     } catch (FileNotFoundException e1) {
        return -1;
     }
     BufferedInputStream bufferedInput = new BufferedInputStream(ins);
     byte[] buffer = new byte[1024];
     StringBuffer strbuf = new StringBuffer();
     try {
       int bytesRead = 0;
       while ((bytesRead = bufferedInput.read(buffer)) != -1) {
         strbuf.append( new String(buffer, 0, bytesRead));
       }
       bufferedInput.close();
     } catch (IOException e) {
       logger.error("could not read serialized file");
       return -1;
     }
     String json = strbuf.toString();
     Gson gson = new Gson();
     DeliveryMechanismWrapper wrap = null;
     try {
         wrap = gson.fromJson(json, DeliveryMechanismWrapper.class);
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
     
     ContentValues cv = deliveryMechanismComposeValues(wrap);
     String whereClause = deliveryMechanismSelectKeyClause(wrap);
     
     if (whereClause != null) {
         // Switch on the path in the uri for what we want to query.
         Cursor updateCursor = db.query(Tables.DELIVERY_MECHANISM_TBL, deliveryMechanismProjectionKey, whereClause, null, null, null, null);
         long rowId = -1;
         for (boolean more = updateCursor.moveToFirst(); more;)
         {
            rowId = updateCursor.getLong(updateCursor.getColumnIndex(DeliveryMechanismTableSchemaBase._ID));  
         
            db.update(Tables.DELIVERY_MECHANISM_TBL, cv, 
               "\""+DeliveryMechanismTableSchemaBase._ID+"\" = '"+ Long.toString(rowId)+"'",
               null); 
            break;
         }
         updateCursor.close();
         if (rowId > 0) {
             getContext().getContentResolver().notifyChange(DeliveryMechanismTableSchemaBase.CONTENT_URI, null); 
             return rowId;
         }
     }
     long rowId = db.insert(Tables.DELIVERY_MECHANISM_TBL, 
         DeliveryMechanismTableSchemaBase.CONN_TYPE,
         cv);
     getContext().getContentResolver().notifyChange(DeliveryMechanismTableSchemaBase.CONTENT_URI, null); 
     return rowId;
   }
  /**
   * This method is provided with the express purpose of being overridden and extended.
   * @param wrap
   */
  protected ContentValues deliveryMechanismComposeValues(DeliveryMechanismWrapper wrap) {
     ContentValues cv = new ContentValues();
     cv.put(DeliveryMechanismTableSchemaBase.CONN_TYPE, wrap.getConnType()); 
     cv.put(DeliveryMechanismTableSchemaBase.STATUS, wrap.getStatus()); 
     cv.put(DeliveryMechanismTableSchemaBase.UNIT, wrap.getUnit()); 
     cv.put(DeliveryMechanismTableSchemaBase.COST_UP, wrap.getCostUp()); 
     cv.put(DeliveryMechanismTableSchemaBase.COST_DOWN, wrap.getCostDown()); 
     cv.put(DeliveryMechanismTableSchemaBase._DISPOSITION, wrap.get_Disposition());
     return cv;   
  }
  
  /**
   * This method is provided with the express purpose of being overridden and extended.
   *
   *    StringBuilder sb = new StringBuilder();
   *    sb.append("\""+DeliveryMechanismTableSchemaBase.FUNCTION_CODE+"\" = '"+ wrap.getFunctionCode()+"'"); 
   *    return sb.toString();   
   *
   * @param wrap
   */
  protected String deliveryMechanismSelectKeyClause(DeliveryMechanismWrapper wrap) {
      return null;
  }

  //@Override 
  public ArrayList<String> deliveryMechanismSerializer(Cursor cursor) {
      logger.debug( "::serializer");
      ArrayList<String> paths = new ArrayList<String>();      
      if (1 > cursor.getCount()) return paths;

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream eos = new DataOutputStream(baos);
      
      for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
           DeliveryMechanismWrapper iw = new DeliveryMechanismWrapper();
             iw.setConnType(cursor.getInt(cursor.getColumnIndex(DeliveryMechanismTableSchemaBase.CONN_TYPE)));  
             iw.setStatus(cursor.getString(cursor.getColumnIndex(DeliveryMechanismTableSchemaBase.STATUS)));  
             iw.setUnit(cursor.getString(cursor.getColumnIndex(DeliveryMechanismTableSchemaBase.UNIT)));  
             iw.setCostUp(cursor.getInt(cursor.getColumnIndex(DeliveryMechanismTableSchemaBase.COST_UP)));  
             iw.setCostDown(cursor.getInt(cursor.getColumnIndex(DeliveryMechanismTableSchemaBase.COST_DOWN)));  
             iw.set_Disposition(cursor.getInt(cursor.getColumnIndex(DeliveryMechanismTableSchemaBase._DISPOSITION))); 

           Gson gson = new Gson();

           try {
              eos.writeBytes(gson.toJson(iw));
              eos.writeByte(0);
           } catch (IOException ex) {
              ex.printStackTrace();
           }

           // not a reference field name :conn type connType conn_type\n 
           // not a reference field name :status status status\n 
           // not a reference field name :unit unit unit\n 
           // not a reference field name :cost up costUp cost_up\n 
           // not a reference field name :cost down costDown cost_down\n 
           // DeliveryMechanismTableSchemaBase._DISPOSITION;

           try {
              if (!dirDeliveryMechanism.exists() ) dirDeliveryMechanism.mkdirs();
              
              File outfile = new File(dirDeliveryMechanism, Integer.toHexString((int) System.currentTimeMillis())); 
              BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outfile), 8192);
              bufferedOutput.write(baos.toByteArray());
              bufferedOutput.flush();
              bufferedOutput.close();
           
              paths.add(outfile.getCanonicalPath());
           } catch (FileNotFoundException e) {
              e.printStackTrace();
           } catch (IOException e) {
              e.printStackTrace();
           }
      }
      return paths;
   } 
  static private File dirPostal = 
           new File(Environment.getExternalStorageDirectory(),
                    "ammo_cp_postal_cache");

  public long postalDeserializer(File file) {
     logger.debug("::postaldeserializer");
     InputStream ins;
     try {
        ins = new FileInputStream(file);
     } catch (FileNotFoundException e1) {
        return -1;
     }
     BufferedInputStream bufferedInput = new BufferedInputStream(ins);
     byte[] buffer = new byte[1024];
     StringBuffer strbuf = new StringBuffer();
     try {
       int bytesRead = 0;
       while ((bytesRead = bufferedInput.read(buffer)) != -1) {
         strbuf.append( new String(buffer, 0, bytesRead));
       }
       bufferedInput.close();
     } catch (IOException e) {
       logger.error("could not read serialized file");
       return -1;
     }
     String json = strbuf.toString();
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
     String whereClause = postalSelectKeyClause(wrap);
     
     if (whereClause != null) {
         // Switch on the path in the uri for what we want to query.
         Cursor updateCursor = db.query(Tables.POSTAL_TBL, postalProjectionKey, whereClause, null, null, null, null);
         long rowId = -1;
         for (boolean more = updateCursor.moveToFirst(); more;)
         {
            rowId = updateCursor.getLong(updateCursor.getColumnIndex(PostalTableSchemaBase._ID));  
         
            db.update(Tables.POSTAL_TBL, cv, 
               "\""+PostalTableSchemaBase._ID+"\" = '"+ Long.toString(rowId)+"'",
               null); 
            break;
         }
         updateCursor.close();
         if (rowId > 0) {
             getContext().getContentResolver().notifyChange(PostalTableSchemaBase.CONTENT_URI, null); 
             return rowId;
         }
     }
     long rowId = db.insert(Tables.POSTAL_TBL, 
         PostalTableSchemaBase.CP_TYPE,
         cv);
     getContext().getContentResolver().notifyChange(PostalTableSchemaBase.CONTENT_URI, null); 
     return rowId;
   }
  /**
   * This method is provided with the express purpose of being overridden and extended.
   * @param wrap
   */
  protected ContentValues postalComposeValues(PostalWrapper wrap) {
     ContentValues cv = new ContentValues();
     cv.put(PostalTableSchemaBase.CP_TYPE, wrap.getCpType()); 
     cv.put(PostalTableSchemaBase.URI, wrap.getUri()); 
     cv.put(PostalTableSchemaBase.NOTICE, wrap.getNotice()); 
     cv.put(PostalTableSchemaBase.SERIALIZE_TYPE, wrap.getSerializeType()); 
     cv.put(PostalTableSchemaBase.DISPOSITION, wrap.getDisposition()); 
     cv.put(PostalTableSchemaBase.EXPIRATION, wrap.getExpiration()); 
     cv.put(PostalTableSchemaBase.UNIT, wrap.getUnit()); 
     cv.put(PostalTableSchemaBase.VALUE, wrap.getValue()); 
     cv.put(PostalTableSchemaBase.DATA, wrap.getData()); 
     cv.put(PostalTableSchemaBase.CREATED_DATE, wrap.getCreatedDate()); 
     cv.put(PostalTableSchemaBase.MODIFIED_DATE, wrap.getModifiedDate()); 
     cv.put(PostalTableSchemaBase._DISPOSITION, wrap.get_Disposition());
     return cv;   
  }
  
  /**
   * This method is provided with the express purpose of being overridden and extended.
   *
   *    StringBuilder sb = new StringBuilder();
   *    sb.append("\""+PostalTableSchemaBase.FUNCTION_CODE+"\" = '"+ wrap.getFunctionCode()+"'"); 
   *    return sb.toString();   
   *
   * @param wrap
   */
  protected String postalSelectKeyClause(PostalWrapper wrap) {
      return null;
  }

  //@Override 
  public ArrayList<String> postalSerializer(Cursor cursor) {
      logger.debug( "::serializer");
      ArrayList<String> paths = new ArrayList<String>();      
      if (1 > cursor.getCount()) return paths;

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream eos = new DataOutputStream(baos);
      
      for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
           PostalWrapper iw = new PostalWrapper();
             iw.setCpType(cursor.getString(cursor.getColumnIndex(PostalTableSchemaBase.CP_TYPE)));  
             iw.setUri(cursor.getString(cursor.getColumnIndex(PostalTableSchemaBase.URI)));  
             iw.setNotice(cursor.getBlob(cursor.getColumnIndex(PostalTableSchemaBase.NOTICE)));  
             iw.setSerializeType(cursor.getInt(cursor.getColumnIndex(PostalTableSchemaBase.SERIALIZE_TYPE)));  
             iw.setDisposition(cursor.getInt(cursor.getColumnIndex(PostalTableSchemaBase.DISPOSITION)));  
             iw.setExpiration(cursor.getInt(cursor.getColumnIndex(PostalTableSchemaBase.EXPIRATION)));  
             iw.setUnit(cursor.getString(cursor.getColumnIndex(PostalTableSchemaBase.UNIT)));  
             iw.setValue(cursor.getInt(cursor.getColumnIndex(PostalTableSchemaBase.VALUE)));  
             iw.setData(cursor.getString(cursor.getColumnIndex(PostalTableSchemaBase.DATA)));  
             iw.setCreatedDate(cursor.getLong(cursor.getColumnIndex(PostalTableSchemaBase.CREATED_DATE)));  
             iw.setModifiedDate(cursor.getLong(cursor.getColumnIndex(PostalTableSchemaBase.MODIFIED_DATE)));  
             iw.set_Disposition(cursor.getInt(cursor.getColumnIndex(PostalTableSchemaBase._DISPOSITION))); 

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
           // not a reference field name :serialize type serializeType serialize_type\n 
           // not a reference field name :disposition disposition disposition\n 
           // not a reference field name :expiration expiration expiration\n 
           // not a reference field name :unit unit unit\n 
           // not a reference field name :value value value\n 
           // not a reference field name :data data data\n 
           // not a reference field name :created date createdDate created_date\n 
           // not a reference field name :modified date modifiedDate modified_date\n 
           // PostalTableSchemaBase._DISPOSITION;

           try {
              if (!dirPostal.exists() ) dirPostal.mkdirs();
              
              File outfile = new File(dirPostal, Integer.toHexString((int) System.currentTimeMillis())); 
              BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outfile), 8192);
              bufferedOutput.write(baos.toByteArray());
              bufferedOutput.flush();
              bufferedOutput.close();
           
              paths.add(outfile.getCanonicalPath());
           } catch (FileNotFoundException e) {
              e.printStackTrace();
           } catch (IOException e) {
              e.printStackTrace();
           }
      }
      return paths;
   } 
  static private File dirRetrieval = 
           new File(Environment.getExternalStorageDirectory(),
                    "ammo_cp_retrieval_cache");

  public long retrievalDeserializer(File file) {
     logger.debug("::retrievaldeserializer");
     InputStream ins;
     try {
        ins = new FileInputStream(file);
     } catch (FileNotFoundException e1) {
        return -1;
     }
     BufferedInputStream bufferedInput = new BufferedInputStream(ins);
     byte[] buffer = new byte[1024];
     StringBuffer strbuf = new StringBuffer();
     try {
       int bytesRead = 0;
       while ((bytesRead = bufferedInput.read(buffer)) != -1) {
         strbuf.append( new String(buffer, 0, bytesRead));
       }
       bufferedInput.close();
     } catch (IOException e) {
       logger.error("could not read serialized file");
       return -1;
     }
     String json = strbuf.toString();
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
     String whereClause = retrievalSelectKeyClause(wrap);
     
     if (whereClause != null) {
         // Switch on the path in the uri for what we want to query.
         Cursor updateCursor = db.query(Tables.RETRIEVAL_TBL, retrievalProjectionKey, whereClause, null, null, null, null);
         long rowId = -1;
         for (boolean more = updateCursor.moveToFirst(); more;)
         {
            rowId = updateCursor.getLong(updateCursor.getColumnIndex(RetrievalTableSchemaBase._ID));  
         
            db.update(Tables.RETRIEVAL_TBL, cv, 
               "\""+RetrievalTableSchemaBase._ID+"\" = '"+ Long.toString(rowId)+"'",
               null); 
            break;
         }
         updateCursor.close();
         if (rowId > 0) {
             getContext().getContentResolver().notifyChange(RetrievalTableSchemaBase.CONTENT_URI, null); 
             return rowId;
         }
     }
     long rowId = db.insert(Tables.RETRIEVAL_TBL, 
         RetrievalTableSchemaBase.DISPOSITION,
         cv);
     getContext().getContentResolver().notifyChange(RetrievalTableSchemaBase.CONTENT_URI, null); 
     return rowId;
   }
  /**
   * This method is provided with the express purpose of being overridden and extended.
   * @param wrap
   */
  protected ContentValues retrievalComposeValues(RetrievalWrapper wrap) {
     ContentValues cv = new ContentValues();
     cv.put(RetrievalTableSchemaBase.DISPOSITION, wrap.getDisposition()); 
     cv.put(RetrievalTableSchemaBase.NOTICE, wrap.getNotice()); 
     cv.put(RetrievalTableSchemaBase.URI, wrap.getUri()); 
     cv.put(RetrievalTableSchemaBase.MIME, wrap.getMime()); 
     cv.put(RetrievalTableSchemaBase.PROJECTION, wrap.getProjection()); 
     cv.put(RetrievalTableSchemaBase.SELECTION, wrap.getSelection()); 
     cv.put(RetrievalTableSchemaBase.ARGS, wrap.getArgs()); 
     cv.put(RetrievalTableSchemaBase.ORDERING, wrap.getOrdering()); 
     cv.put(RetrievalTableSchemaBase.CONTINUITY, wrap.getContinuity()); 
     cv.put(RetrievalTableSchemaBase.CONTINUITY_VALUE, wrap.getContinuity_value()); 
     cv.put(RetrievalTableSchemaBase.EXPIRATION, wrap.getExpiration()); 
     cv.put(RetrievalTableSchemaBase.CREATED_DATE, wrap.getCreatedDate()); 
     cv.put(RetrievalTableSchemaBase.MODIFIED_DATE, wrap.getModifiedDate()); 
     cv.put(RetrievalTableSchemaBase._DISPOSITION, wrap.get_Disposition());
     return cv;   
  }
  
  /**
   * This method is provided with the express purpose of being overridden and extended.
   *
   *    StringBuilder sb = new StringBuilder();
   *    sb.append("\""+RetrievalTableSchemaBase.FUNCTION_CODE+"\" = '"+ wrap.getFunctionCode()+"'"); 
   *    return sb.toString();   
   *
   * @param wrap
   */
  protected String retrievalSelectKeyClause(RetrievalWrapper wrap) {
      return null;
  }

  //@Override 
  public ArrayList<String> retrievalSerializer(Cursor cursor) {
      logger.debug( "::serializer");
      ArrayList<String> paths = new ArrayList<String>();      
      if (1 > cursor.getCount()) return paths;

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream eos = new DataOutputStream(baos);
      
      for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
           RetrievalWrapper iw = new RetrievalWrapper();
             iw.setDisposition(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchemaBase.DISPOSITION)));  
             iw.setNotice(cursor.getBlob(cursor.getColumnIndex(RetrievalTableSchemaBase.NOTICE)));  
             iw.setUri(cursor.getString(cursor.getColumnIndex(RetrievalTableSchemaBase.URI)));  
             iw.setMime(cursor.getString(cursor.getColumnIndex(RetrievalTableSchemaBase.MIME)));  
             iw.setProjection(cursor.getString(cursor.getColumnIndex(RetrievalTableSchemaBase.PROJECTION)));  
             iw.setSelection(cursor.getString(cursor.getColumnIndex(RetrievalTableSchemaBase.SELECTION)));  
             iw.setArgs(cursor.getString(cursor.getColumnIndex(RetrievalTableSchemaBase.ARGS)));  
             iw.setOrdering(cursor.getString(cursor.getColumnIndex(RetrievalTableSchemaBase.ORDERING)));  
             iw.setContinuity(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchemaBase.CONTINUITY)));  
             iw.setContinuity_value(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchemaBase.CONTINUITY_VALUE)));  
             iw.setExpiration(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchemaBase.EXPIRATION)));  
             iw.setCreatedDate(cursor.getLong(cursor.getColumnIndex(RetrievalTableSchemaBase.CREATED_DATE)));  
             iw.setModifiedDate(cursor.getLong(cursor.getColumnIndex(RetrievalTableSchemaBase.MODIFIED_DATE)));  
             iw.set_Disposition(cursor.getInt(cursor.getColumnIndex(RetrievalTableSchemaBase._DISPOSITION))); 

           Gson gson = new Gson();

           try {
              eos.writeBytes(gson.toJson(iw));
              eos.writeByte(0);
           } catch (IOException ex) {
              ex.printStackTrace();
           }

           // not a reference field name :disposition disposition disposition\n 
           // not a reference field name :notice notice notice\n 
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
           // RetrievalTableSchemaBase._DISPOSITION;

           try {
              if (!dirRetrieval.exists() ) dirRetrieval.mkdirs();
              
              File outfile = new File(dirRetrieval, Integer.toHexString((int) System.currentTimeMillis())); 
              BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outfile), 8192);
              bufferedOutput.write(baos.toByteArray());
              bufferedOutput.flush();
              bufferedOutput.close();
           
              paths.add(outfile.getCanonicalPath());
           } catch (FileNotFoundException e) {
              e.printStackTrace();
           } catch (IOException e) {
              e.printStackTrace();
           }
      }
      return paths;
   } 
  static private File dirPublication = 
           new File(Environment.getExternalStorageDirectory(),
                    "ammo_cp_publication_cache");

  public long publicationDeserializer(File file) {
     logger.debug("::publicationdeserializer");
     InputStream ins;
     try {
        ins = new FileInputStream(file);
     } catch (FileNotFoundException e1) {
        return -1;
     }
     BufferedInputStream bufferedInput = new BufferedInputStream(ins);
     byte[] buffer = new byte[1024];
     StringBuffer strbuf = new StringBuffer();
     try {
       int bytesRead = 0;
       while ((bytesRead = bufferedInput.read(buffer)) != -1) {
         strbuf.append( new String(buffer, 0, bytesRead));
       }
       bufferedInput.close();
     } catch (IOException e) {
       logger.error("could not read serialized file");
       return -1;
     }
     String json = strbuf.toString();
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
     String whereClause = publicationSelectKeyClause(wrap);
     
     if (whereClause != null) {
         // Switch on the path in the uri for what we want to query.
         Cursor updateCursor = db.query(Tables.PUBLICATION_TBL, publicationProjectionKey, whereClause, null, null, null, null);
         long rowId = -1;
         for (boolean more = updateCursor.moveToFirst(); more;)
         {
            rowId = updateCursor.getLong(updateCursor.getColumnIndex(PublicationTableSchemaBase._ID));  
         
            db.update(Tables.PUBLICATION_TBL, cv, 
               "\""+PublicationTableSchemaBase._ID+"\" = '"+ Long.toString(rowId)+"'",
               null); 
            break;
         }
         updateCursor.close();
         if (rowId > 0) {
             getContext().getContentResolver().notifyChange(PublicationTableSchemaBase.CONTENT_URI, null); 
             return rowId;
         }
     }
     long rowId = db.insert(Tables.PUBLICATION_TBL, 
         PublicationTableSchemaBase.DISPOSITION,
         cv);
     getContext().getContentResolver().notifyChange(PublicationTableSchemaBase.CONTENT_URI, null); 
     return rowId;
   }
  /**
   * This method is provided with the express purpose of being overridden and extended.
   * @param wrap
   */
  protected ContentValues publicationComposeValues(PublicationWrapper wrap) {
     ContentValues cv = new ContentValues();
     cv.put(PublicationTableSchemaBase.DISPOSITION, wrap.getDisposition()); 
     cv.put(PublicationTableSchemaBase.URI, wrap.getUri()); 
     cv.put(PublicationTableSchemaBase.MIME, wrap.getMime()); 
     cv.put(PublicationTableSchemaBase.EXPIRATION, wrap.getExpiration()); 
     cv.put(PublicationTableSchemaBase.CREATED_DATE, wrap.getCreatedDate()); 
     cv.put(PublicationTableSchemaBase.MODIFIED_DATE, wrap.getModifiedDate()); 
     cv.put(PublicationTableSchemaBase._DISPOSITION, wrap.get_Disposition());
     return cv;   
  }
  
  /**
   * This method is provided with the express purpose of being overridden and extended.
   *
   *    StringBuilder sb = new StringBuilder();
   *    sb.append("\""+PublicationTableSchemaBase.FUNCTION_CODE+"\" = '"+ wrap.getFunctionCode()+"'"); 
   *    return sb.toString();   
   *
   * @param wrap
   */
  protected String publicationSelectKeyClause(PublicationWrapper wrap) {
      return null;
  }

  //@Override 
  public ArrayList<String> publicationSerializer(Cursor cursor) {
      logger.debug( "::serializer");
      ArrayList<String> paths = new ArrayList<String>();      
      if (1 > cursor.getCount()) return paths;

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream eos = new DataOutputStream(baos);
      
      for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
           PublicationWrapper iw = new PublicationWrapper();
             iw.setDisposition(cursor.getInt(cursor.getColumnIndex(PublicationTableSchemaBase.DISPOSITION)));  
             iw.setUri(cursor.getString(cursor.getColumnIndex(PublicationTableSchemaBase.URI)));  
             iw.setMime(cursor.getString(cursor.getColumnIndex(PublicationTableSchemaBase.MIME)));  
             iw.setExpiration(cursor.getInt(cursor.getColumnIndex(PublicationTableSchemaBase.EXPIRATION)));  
             iw.setCreatedDate(cursor.getLong(cursor.getColumnIndex(PublicationTableSchemaBase.CREATED_DATE)));  
             iw.setModifiedDate(cursor.getLong(cursor.getColumnIndex(PublicationTableSchemaBase.MODIFIED_DATE)));  
             iw.set_Disposition(cursor.getInt(cursor.getColumnIndex(PublicationTableSchemaBase._DISPOSITION))); 

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
           // PublicationTableSchemaBase._DISPOSITION;

           try {
              if (!dirPublication.exists() ) dirPublication.mkdirs();
              
              File outfile = new File(dirPublication, Integer.toHexString((int) System.currentTimeMillis())); 
              BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outfile), 8192);
              bufferedOutput.write(baos.toByteArray());
              bufferedOutput.flush();
              bufferedOutput.close();
           
              paths.add(outfile.getCanonicalPath());
           } catch (FileNotFoundException e) {
              e.printStackTrace();
           } catch (IOException e) {
              e.printStackTrace();
           }
      }
      return paths;
   } 
  static private File dirSubscription = 
           new File(Environment.getExternalStorageDirectory(),
                    "ammo_cp_subscription_cache");

  public long subscriptionDeserializer(File file) {
     logger.debug("::subscriptiondeserializer");
     InputStream ins;
     try {
        ins = new FileInputStream(file);
     } catch (FileNotFoundException e1) {
        return -1;
     }
     BufferedInputStream bufferedInput = new BufferedInputStream(ins);
     byte[] buffer = new byte[1024];
     StringBuffer strbuf = new StringBuffer();
     try {
       int bytesRead = 0;
       while ((bytesRead = bufferedInput.read(buffer)) != -1) {
         strbuf.append( new String(buffer, 0, bytesRead));
       }
       bufferedInput.close();
     } catch (IOException e) {
       logger.error("could not read serialized file");
       return -1;
     }
     String json = strbuf.toString();
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
     String whereClause = subscriptionSelectKeyClause(wrap);
     
     if (whereClause != null) {
         // Switch on the path in the uri for what we want to query.
         Cursor updateCursor = db.query(Tables.SUBSCRIPTION_TBL, subscriptionProjectionKey, whereClause, null, null, null, null);
         long rowId = -1;
         for (boolean more = updateCursor.moveToFirst(); more;)
         {
            rowId = updateCursor.getLong(updateCursor.getColumnIndex(SubscriptionTableSchemaBase._ID));  
         
            db.update(Tables.SUBSCRIPTION_TBL, cv, 
               "\""+SubscriptionTableSchemaBase._ID+"\" = '"+ Long.toString(rowId)+"'",
               null); 
            break;
         }
         updateCursor.close();
         if (rowId > 0) {
             getContext().getContentResolver().notifyChange(SubscriptionTableSchemaBase.CONTENT_URI, null); 
             return rowId;
         }
     }
     long rowId = db.insert(Tables.SUBSCRIPTION_TBL, 
         SubscriptionTableSchemaBase.DISPOSITION,
         cv);
     getContext().getContentResolver().notifyChange(SubscriptionTableSchemaBase.CONTENT_URI, null); 
     return rowId;
   }
  /**
   * This method is provided with the express purpose of being overridden and extended.
   * @param wrap
   */
  protected ContentValues subscriptionComposeValues(SubscriptionWrapper wrap) {
     ContentValues cv = new ContentValues();
     cv.put(SubscriptionTableSchemaBase.DISPOSITION, wrap.getDisposition()); 
     cv.put(SubscriptionTableSchemaBase.URI, wrap.getUri()); 
     cv.put(SubscriptionTableSchemaBase.MIME, wrap.getMime()); 
     cv.put(SubscriptionTableSchemaBase.SELECTION, wrap.getSelection()); 
     cv.put(SubscriptionTableSchemaBase.EXPIRATION, wrap.getExpiration()); 
     cv.put(SubscriptionTableSchemaBase.NOTICE, wrap.getNotice()); 
     cv.put(SubscriptionTableSchemaBase.CREATED_DATE, wrap.getCreatedDate()); 
     cv.put(SubscriptionTableSchemaBase.MODIFIED_DATE, wrap.getModifiedDate()); 
     cv.put(SubscriptionTableSchemaBase._DISPOSITION, wrap.get_Disposition());
     return cv;   
  }
  
  /**
   * This method is provided with the express purpose of being overridden and extended.
   *
   *    StringBuilder sb = new StringBuilder();
   *    sb.append("\""+SubscriptionTableSchemaBase.FUNCTION_CODE+"\" = '"+ wrap.getFunctionCode()+"'"); 
   *    return sb.toString();   
   *
   * @param wrap
   */
  protected String subscriptionSelectKeyClause(SubscriptionWrapper wrap) {
      return null;
  }

  //@Override 
  public ArrayList<String> subscriptionSerializer(Cursor cursor) {
      logger.debug( "::serializer");
      ArrayList<String> paths = new ArrayList<String>();      
      if (1 > cursor.getCount()) return paths;

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream eos = new DataOutputStream(baos);
      
      for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
           SubscriptionWrapper iw = new SubscriptionWrapper();
             iw.setDisposition(cursor.getInt(cursor.getColumnIndex(SubscriptionTableSchemaBase.DISPOSITION)));  
             iw.setUri(cursor.getString(cursor.getColumnIndex(SubscriptionTableSchemaBase.URI)));  
             iw.setMime(cursor.getString(cursor.getColumnIndex(SubscriptionTableSchemaBase.MIME)));  
             iw.setSelection(cursor.getString(cursor.getColumnIndex(SubscriptionTableSchemaBase.SELECTION)));  
             iw.setExpiration(cursor.getInt(cursor.getColumnIndex(SubscriptionTableSchemaBase.EXPIRATION)));  
             iw.setNotice(cursor.getBlob(cursor.getColumnIndex(SubscriptionTableSchemaBase.NOTICE)));  
             iw.setCreatedDate(cursor.getLong(cursor.getColumnIndex(SubscriptionTableSchemaBase.CREATED_DATE)));  
             iw.setModifiedDate(cursor.getLong(cursor.getColumnIndex(SubscriptionTableSchemaBase.MODIFIED_DATE)));  
             iw.set_Disposition(cursor.getInt(cursor.getColumnIndex(SubscriptionTableSchemaBase._DISPOSITION))); 

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
           // not a reference field name :created date createdDate created_date\n 
           // not a reference field name :modified date modifiedDate modified_date\n 
           // SubscriptionTableSchemaBase._DISPOSITION;

           try {
              if (!dirSubscription.exists() ) dirSubscription.mkdirs();
              
              File outfile = new File(dirSubscription, Integer.toHexString((int) System.currentTimeMillis())); 
              BufferedOutputStream bufferedOutput = new BufferedOutputStream(new FileOutputStream(outfile), 8192);
              bufferedOutput.write(baos.toByteArray());
              bufferedOutput.flush();
              bufferedOutput.close();
           
              paths.add(outfile.getCanonicalPath());
           } catch (FileNotFoundException e) {
              e.printStackTrace();
           } catch (IOException e) {
              e.printStackTrace();
           }
      }
      return paths;
   } 
    
   
   // ===========================================================
   // Constants
   // ===========================================================
   private final static Logger logger = LoggerFactory.getLogger(DistributorProviderBase.class);

   // ===========================================================
   // Fields
   // ===========================================================
   /** Projection Maps */
      protected static String[] deliveryMechanismProjectionKey;
      protected static HashMap<String, String> deliveryMechanismProjectionMap;
      
      protected static String[] postalProjectionKey;
      protected static HashMap<String, String> postalProjectionMap;
      
      protected static String[] retrievalProjectionKey;
      protected static HashMap<String, String> retrievalProjectionMap;
      
      protected static String[] publicationProjectionKey;
      protected static HashMap<String, String> publicationProjectionMap;
      
      protected static String[] subscriptionProjectionKey;
      protected static HashMap<String, String> subscriptionProjectionMap;
      
   
   /** Uri Matcher tags */
      protected static final int DELIVERY_MECHANISM_BLOB = 10;
      protected static final int DELIVERY_MECHANISM_SET = 11;
      protected static final int DELIVERY_MECHANISM_ID = 12;
      
      protected static final int POSTAL_BLOB = 20;
      protected static final int POSTAL_SET = 21;
      protected static final int POSTAL_ID = 22;
      
      protected static final int RETRIEVAL_BLOB = 30;
      protected static final int RETRIEVAL_SET = 31;
      protected static final int RETRIEVAL_ID = 32;
      
      protected static final int PUBLICATION_BLOB = 40;
      protected static final int PUBLICATION_SET = 41;
      protected static final int PUBLICATION_ID = 42;
      
      protected static final int SUBSCRIPTION_BLOB = 50;
      protected static final int SUBSCRIPTION_SET = 51;
      protected static final int SUBSCRIPTION_ID = 52;
      
   
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
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.DELIVERY_MECHANISM_TBL+"/#/_serial", DELIVERY_MECHANISM_ID);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.DELIVERY_MECHANISM_TBL+"/_serial", DELIVERY_MECHANISM_SET);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.DELIVERY_MECHANISM_TBL+"/#/*", DELIVERY_MECHANISM_BLOB);
            
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.POSTAL_TBL+"/#/_serial", POSTAL_ID);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.POSTAL_TBL+"/_serial", POSTAL_SET);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.POSTAL_TBL+"/#/*", POSTAL_BLOB);
            
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.RETRIEVAL_TBL+"/#/_serial", RETRIEVAL_ID);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.RETRIEVAL_TBL+"/_serial", RETRIEVAL_SET);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.RETRIEVAL_TBL+"/#/*", RETRIEVAL_BLOB);
            
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.PUBLICATION_TBL+"/#/_serial", PUBLICATION_ID);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.PUBLICATION_TBL+"/_serial", PUBLICATION_SET);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.PUBLICATION_TBL+"/#/*", PUBLICATION_BLOB);
            
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.SUBSCRIPTION_TBL+"/#/_serial", SUBSCRIPTION_ID);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.SUBSCRIPTION_TBL+"/_serial", SUBSCRIPTION_SET);
            blobUriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.SUBSCRIPTION_TBL+"/#/*", SUBSCRIPTION_BLOB);
            
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
    private Set<FileObserver> observerSet = new HashSet<FileObserver>();

    @Override
    public ParcelFileDescriptor openFile (Uri uri, String mode) {
        int imode = 0;
        if (mode.contains("w")) imode |= ParcelFileDescriptor.MODE_WRITE_ONLY;
        if (mode.contains("r")) imode |= ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode.contains("+")) imode |= ParcelFileDescriptor.MODE_APPEND;
        
        SQLiteQueryBuilder qb = null;
        SQLiteDatabase db = null;
        Cursor cursor = null;
        ArrayList<String> paths = null;
        List<String> pseg = uri.getPathSegments();
        
        int match = blobUriMatcher.match(uri);
        switch (match) {

         case DELIVERY_MECHANISM_BLOB:
            if (pseg.size() < 3)
                return null;

            try {
                File filePath = blobFile("delivery_mechanism", pseg.get(1), pseg.get(2));
                return ParcelFileDescriptor.open(filePath, imode);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            break;

         case DELIVERY_MECHANISM_SET:
            try {
               final File tempFile = tempFilePath("delivery_mechanism");
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
                          deliveryMechanismDeserializer(tempFile);
                        } catch (IOException e) {
                        }
                        tempFile.delete();
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
             } catch (FileNotFoundException e1) {
                e1.printStackTrace();
             } catch (IOException e1) {
                e1.printStackTrace();
             }
             break;

         case DELIVERY_MECHANISM_ID:
            qb = new SQLiteQueryBuilder();
            db = openHelper.getReadableDatabase();
                    
            // Switch on the path in the uri for what we want to query.
            qb.setTables(Tables.DELIVERY_MECHANISM_TBL);
            qb.setProjectionMap(deliveryMechanismProjectionMap);
            qb.appendWhere(DeliveryMechanismTableSchemaBase._ID + " = " + uri.getPathSegments().get(1));
            cursor = qb.query(db, null, null, null, null, null, null);
            if (1 > cursor.getCount()) {
               logger.info("no data of type DELIVERY_MECHANISM_ID"); 
               cursor.close();
               return null;
            }
            paths = this.deliveryMechanismSerializer(cursor);
            cursor.close();
            try {
               return ParcelFileDescriptor.open(new File(paths.get(0)), imode);
            } catch (FileNotFoundException e) {
               e.printStackTrace();
            }
            break;    

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
                          postalDeserializer(tempFile);
                        } catch (IOException e) {
                        }
                        tempFile.delete();
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
            qb.appendWhere(PostalTableSchemaBase._ID + " = " + uri.getPathSegments().get(1));
            cursor = qb.query(db, null, null, null, null, null, null);
            if (1 > cursor.getCount()) {
               logger.info("no data of type POSTAL_ID"); 
               cursor.close();
               return null;
            }
            paths = this.postalSerializer(cursor);
            cursor.close();
            try {
               return ParcelFileDescriptor.open(new File(paths.get(0)), imode);
            } catch (FileNotFoundException e) {
               e.printStackTrace();
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
                          retrievalDeserializer(tempFile);
                        } catch (IOException e) {
                        }
                        tempFile.delete();
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
            qb.appendWhere(RetrievalTableSchemaBase._ID + " = " + uri.getPathSegments().get(1));
            cursor = qb.query(db, null, null, null, null, null, null);
            if (1 > cursor.getCount()) {
               logger.info("no data of type RETRIEVAL_ID"); 
               cursor.close();
               return null;
            }
            paths = this.retrievalSerializer(cursor);
            cursor.close();
            try {
               return ParcelFileDescriptor.open(new File(paths.get(0)), imode);
            } catch (FileNotFoundException e) {
               e.printStackTrace();
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
                          publicationDeserializer(tempFile);
                        } catch (IOException e) {
                        }
                        tempFile.delete();
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
            qb.appendWhere(PublicationTableSchemaBase._ID + " = " + uri.getPathSegments().get(1));
            cursor = qb.query(db, null, null, null, null, null, null);
            if (1 > cursor.getCount()) {
               logger.info("no data of type PUBLICATION_ID"); 
               cursor.close();
               return null;
            }
            paths = this.publicationSerializer(cursor);
            cursor.close();
            try {
               return ParcelFileDescriptor.open(new File(paths.get(0)), imode);
            } catch (FileNotFoundException e) {
               e.printStackTrace();
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
                          subscriptionDeserializer(tempFile);
                        } catch (IOException e) {
                        }
                        tempFile.delete();
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
            qb.appendWhere(SubscriptionTableSchemaBase._ID + " = " + uri.getPathSegments().get(1));
            cursor = qb.query(db, null, null, null, null, null, null);
            if (1 > cursor.getCount()) {
               logger.info("no data of type SUBSCRIPTION_ID"); 
               cursor.close();
               return null;
            }
            paths = this.subscriptionSerializer(cursor);
            cursor.close();
            try {
               return ParcelFileDescriptor.open(new File(paths.get(0)), imode);
            } catch (FileNotFoundException e) {
               e.printStackTrace();
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
               case DELIVERY_MECHANISM_SET:
                  tableName = Tables.DELIVERY_MECHANISM_TBL;
                  projectionMap = deliveryMechanismProjectionMap;
                  orderBy = (! TextUtils.isEmpty(sortOrder)) ? sortOrder
                             : DeliveryMechanismTableSchemaBase.DEFAULT_SORT_ORDER;
                  break;
               
               case DELIVERY_MECHANISM_ID:
                  tableName = Tables.DELIVERY_MECHANISM_TBL;
                  projectionMap = deliveryMechanismProjectionMap;
                  qb.appendWhere(DeliveryMechanismTableSchemaBase._ID + "="
                        + uri.getPathSegments().get(1));
                  break;

               
               case POSTAL_SET:
                  tableName = Tables.POSTAL_TBL;
                  projectionMap = postalProjectionMap;
                  orderBy = (! TextUtils.isEmpty(sortOrder)) ? sortOrder
                             : PostalTableSchemaBase.DEFAULT_SORT_ORDER;
                  break;
               
               case POSTAL_ID:
                  tableName = Tables.POSTAL_TBL;
                  projectionMap = postalProjectionMap;
                  qb.appendWhere(PostalTableSchemaBase._ID + "="
                        + uri.getPathSegments().get(1));
                  break;

               
               case RETRIEVAL_SET:
                  tableName = Tables.RETRIEVAL_TBL;
                  projectionMap = retrievalProjectionMap;
                  orderBy = (! TextUtils.isEmpty(sortOrder)) ? sortOrder
                             : RetrievalTableSchemaBase.DEFAULT_SORT_ORDER;
                  break;
               
               case RETRIEVAL_ID:
                  tableName = Tables.RETRIEVAL_TBL;
                  projectionMap = retrievalProjectionMap;
                  qb.appendWhere(RetrievalTableSchemaBase._ID + "="
                        + uri.getPathSegments().get(1));
                  break;

               
               case PUBLICATION_SET:
                  tableName = Tables.PUBLICATION_TBL;
                  projectionMap = publicationProjectionMap;
                  orderBy = (! TextUtils.isEmpty(sortOrder)) ? sortOrder
                             : PublicationTableSchemaBase.DEFAULT_SORT_ORDER;
                  break;
               
               case PUBLICATION_ID:
                  tableName = Tables.PUBLICATION_TBL;
                  projectionMap = publicationProjectionMap;
                  qb.appendWhere(PublicationTableSchemaBase._ID + "="
                        + uri.getPathSegments().get(1));
                  break;

               
               case SUBSCRIPTION_SET:
                  tableName = Tables.SUBSCRIPTION_TBL;
                  projectionMap = subscriptionProjectionMap;
                  orderBy = (! TextUtils.isEmpty(sortOrder)) ? sortOrder
                             : SubscriptionTableSchemaBase.DEFAULT_SORT_ORDER;
                  break;
               
               case SUBSCRIPTION_ID:
                  tableName = Tables.SUBSCRIPTION_TBL;
                  projectionMap = subscriptionProjectionMap;
                  qb.appendWhere(SubscriptionTableSchemaBase._ID + "="
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
               case DELIVERY_MECHANISM_SET:
                  values = this.initializeDeliveryMechanismDefaults(values);
                  insertTable = Tables.DELIVERY_MECHANISM_TBL;
                  nullColumnHack = DeliveryMechanismTableSchemaBase.CONN_TYPE;
                  tableUri = DeliveryMechanismTableSchemaBase.CONTENT_URI;
                  break;
               
               case POSTAL_SET:
                  values = this.initializePostalDefaults(values);
                  insertTable = Tables.POSTAL_TBL;
                  nullColumnHack = PostalTableSchemaBase.CP_TYPE;
                  tableUri = PostalTableSchemaBase.CONTENT_URI;
                  break;
               
               case RETRIEVAL_SET:
                  values = this.initializeRetrievalDefaults(values);
                  insertTable = Tables.RETRIEVAL_TBL;
                  nullColumnHack = RetrievalTableSchemaBase.DISPOSITION;
                  tableUri = RetrievalTableSchemaBase.CONTENT_URI;
                  break;
               
               case PUBLICATION_SET:
                  values = this.initializePublicationDefaults(values);
                  insertTable = Tables.PUBLICATION_TBL;
                  nullColumnHack = PublicationTableSchemaBase.DISPOSITION;
                  tableUri = PublicationTableSchemaBase.CONTENT_URI;
                  break;
               
               case SUBSCRIPTION_SET:
                  values = this.initializeSubscriptionDefaults(values);
                  insertTable = Tables.SUBSCRIPTION_TBL;
                  nullColumnHack = SubscriptionTableSchemaBase.DISPOSITION;
                  tableUri = SubscriptionTableSchemaBase.CONTENT_URI;
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
      protected ContentValues initializeDeliveryMechanismDefaults(ContentValues values) {
         Long now = Long.valueOf(System.currentTimeMillis());
         
           if (!values.containsKey(DeliveryMechanismTableSchemaBase.CONN_TYPE)) {
              values.put("\""+DeliveryMechanismTableSchemaBase.CONN_TYPE+"\"", DeliveryMechanismTableSchemaBase.CONN_TYPE_UNKNOWN);
           } 
           if (!values.containsKey(DeliveryMechanismTableSchemaBase.STATUS)) {
              values.put("\""+DeliveryMechanismTableSchemaBase.STATUS+"\"", "unknown");
           } 
           if (!values.containsKey(DeliveryMechanismTableSchemaBase.UNIT)) {
              values.put("\""+DeliveryMechanismTableSchemaBase.UNIT+"\"", "unknown");
           } 
           if (!values.containsKey(DeliveryMechanismTableSchemaBase.COST_UP)) {
              values.put("\""+DeliveryMechanismTableSchemaBase.COST_UP+"\"", -1);
           } 
           if (!values.containsKey(DeliveryMechanismTableSchemaBase.COST_DOWN)) {
              values.put("\""+DeliveryMechanismTableSchemaBase.COST_DOWN+"\"", -1);
           } 
           if (!values.containsKey(DeliveryMechanismTableSchemaBase._DISPOSITION)) {
              values.put("\""+DeliveryMechanismTableSchemaBase._DISPOSITION+"\"", DistributorSchema._DISPOSITION_START);
           }
         return values;
      }
      
      /** Insert method helper */
      protected ContentValues initializePostalDefaults(ContentValues values) {
         Long now = Long.valueOf(System.currentTimeMillis());
         
           if (!values.containsKey(PostalTableSchemaBase.CP_TYPE)) {
              values.put("\""+PostalTableSchemaBase.CP_TYPE+"\"", "unknown");
           } 
           if (!values.containsKey(PostalTableSchemaBase.URI)) {
              values.put("\""+PostalTableSchemaBase.URI+"\"", "unknown");
           } 
           if (!values.containsKey(PostalTableSchemaBase.NOTICE)) {
              values.put("\""+PostalTableSchemaBase.NOTICE+"\"", "");
           } 
           if (!values.containsKey(PostalTableSchemaBase.SERIALIZE_TYPE)) {
              values.put("\""+PostalTableSchemaBase.SERIALIZE_TYPE+"\"", PostalTableSchemaBase.SERIALIZE_TYPE_INDIRECT);
           } 
           if (!values.containsKey(PostalTableSchemaBase.DISPOSITION)) {
              values.put("\""+PostalTableSchemaBase.DISPOSITION+"\"", PostalTableSchemaBase.DISPOSITION_PENDING);
           } 
           if (!values.containsKey(PostalTableSchemaBase.EXPIRATION)) {
              values.put("\""+PostalTableSchemaBase.EXPIRATION+"\"", now);
           } 
           if (!values.containsKey(PostalTableSchemaBase.UNIT)) {
              values.put("\""+PostalTableSchemaBase.UNIT+"\"", "unknown");
           } 
           if (!values.containsKey(PostalTableSchemaBase.VALUE)) {
              values.put("\""+PostalTableSchemaBase.VALUE+"\"", -1);
           } 
           if (!values.containsKey(PostalTableSchemaBase.DATA)) {
              values.put("\""+PostalTableSchemaBase.DATA+"\"", "");
           } 
           if (!values.containsKey(PostalTableSchemaBase.CREATED_DATE)) {
              values.put("\""+PostalTableSchemaBase.CREATED_DATE+"\"", now);
           } 
           if (!values.containsKey(PostalTableSchemaBase.MODIFIED_DATE)) {
              values.put("\""+PostalTableSchemaBase.MODIFIED_DATE+"\"", now);
           } 
           if (!values.containsKey(PostalTableSchemaBase._DISPOSITION)) {
              values.put("\""+PostalTableSchemaBase._DISPOSITION+"\"", DistributorSchema._DISPOSITION_START);
           }
         return values;
      }
      
      /** Insert method helper */
      protected ContentValues initializeRetrievalDefaults(ContentValues values) {
         Long now = Long.valueOf(System.currentTimeMillis());
         
           if (!values.containsKey(RetrievalTableSchemaBase.DISPOSITION)) {
              values.put("\""+RetrievalTableSchemaBase.DISPOSITION+"\"", RetrievalTableSchemaBase.DISPOSITION_PENDING);
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.NOTICE)) {
              values.put("\""+RetrievalTableSchemaBase.NOTICE+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.URI)) {
              values.put("\""+RetrievalTableSchemaBase.URI+"\"", "unknown");
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.MIME)) {
              values.put("\""+RetrievalTableSchemaBase.MIME+"\"", "unknown");
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.PROJECTION)) {
              values.put("\""+RetrievalTableSchemaBase.PROJECTION+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.SELECTION)) {
              values.put("\""+RetrievalTableSchemaBase.SELECTION+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.ARGS)) {
              values.put("\""+RetrievalTableSchemaBase.ARGS+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.ORDERING)) {
              values.put("\""+RetrievalTableSchemaBase.ORDERING+"\"", "");
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.CONTINUITY)) {
              values.put("\""+RetrievalTableSchemaBase.CONTINUITY+"\"", RetrievalTableSchemaBase.CONTINUITY_ONCE);
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.CONTINUITY_VALUE)) {
              values.put("\""+RetrievalTableSchemaBase.CONTINUITY_VALUE+"\"", now);
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.EXPIRATION)) {
              values.put("\""+RetrievalTableSchemaBase.EXPIRATION+"\"", now);
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.CREATED_DATE)) {
              values.put("\""+RetrievalTableSchemaBase.CREATED_DATE+"\"", now);
           } 
           if (!values.containsKey(RetrievalTableSchemaBase.MODIFIED_DATE)) {
              values.put("\""+RetrievalTableSchemaBase.MODIFIED_DATE+"\"", now);
           } 
           if (!values.containsKey(RetrievalTableSchemaBase._DISPOSITION)) {
              values.put("\""+RetrievalTableSchemaBase._DISPOSITION+"\"", DistributorSchema._DISPOSITION_START);
           }
         return values;
      }
      
      /** Insert method helper */
      protected ContentValues initializePublicationDefaults(ContentValues values) {
         Long now = Long.valueOf(System.currentTimeMillis());
         
           if (!values.containsKey(PublicationTableSchemaBase.DISPOSITION)) {
              values.put("\""+PublicationTableSchemaBase.DISPOSITION+"\"", PublicationTableSchemaBase.DISPOSITION_PENDING);
           } 
           if (!values.containsKey(PublicationTableSchemaBase.URI)) {
              values.put("\""+PublicationTableSchemaBase.URI+"\"", "unknown");
           } 
           if (!values.containsKey(PublicationTableSchemaBase.MIME)) {
              values.put("\""+PublicationTableSchemaBase.MIME+"\"", "unknown");
           } 
           if (!values.containsKey(PublicationTableSchemaBase.EXPIRATION)) {
              values.put("\""+PublicationTableSchemaBase.EXPIRATION+"\"", now);
           } 
           if (!values.containsKey(PublicationTableSchemaBase.CREATED_DATE)) {
              values.put("\""+PublicationTableSchemaBase.CREATED_DATE+"\"", now);
           } 
           if (!values.containsKey(PublicationTableSchemaBase.MODIFIED_DATE)) {
              values.put("\""+PublicationTableSchemaBase.MODIFIED_DATE+"\"", now);
           } 
           if (!values.containsKey(PublicationTableSchemaBase._DISPOSITION)) {
              values.put("\""+PublicationTableSchemaBase._DISPOSITION+"\"", DistributorSchema._DISPOSITION_START);
           }
         return values;
      }
      
      /** Insert method helper */
      protected ContentValues initializeSubscriptionDefaults(ContentValues values) {
         Long now = Long.valueOf(System.currentTimeMillis());
         
           if (!values.containsKey(SubscriptionTableSchemaBase.DISPOSITION)) {
              values.put("\""+SubscriptionTableSchemaBase.DISPOSITION+"\"", SubscriptionTableSchemaBase.DISPOSITION_PENDING);
           } 
           if (!values.containsKey(SubscriptionTableSchemaBase.URI)) {
              values.put("\""+SubscriptionTableSchemaBase.URI+"\"", "unknown");
           } 
           if (!values.containsKey(SubscriptionTableSchemaBase.MIME)) {
              values.put("\""+SubscriptionTableSchemaBase.MIME+"\"", "unknown");
           } 
           if (!values.containsKey(SubscriptionTableSchemaBase.SELECTION)) {
              values.put("\""+SubscriptionTableSchemaBase.SELECTION+"\"", "");
           } 
           if (!values.containsKey(SubscriptionTableSchemaBase.EXPIRATION)) {
              values.put("\""+SubscriptionTableSchemaBase.EXPIRATION+"\"", now);
           } 
           if (!values.containsKey(SubscriptionTableSchemaBase.NOTICE)) {
              values.put("\""+SubscriptionTableSchemaBase.NOTICE+"\"", "");
           } 
           if (!values.containsKey(SubscriptionTableSchemaBase.CREATED_DATE)) {
              values.put("\""+SubscriptionTableSchemaBase.CREATED_DATE+"\"", now);
           } 
           if (!values.containsKey(SubscriptionTableSchemaBase.MODIFIED_DATE)) {
              values.put("\""+SubscriptionTableSchemaBase.MODIFIED_DATE+"\"", now);
           } 
           if (!values.containsKey(SubscriptionTableSchemaBase._DISPOSITION)) {
              values.put("\""+SubscriptionTableSchemaBase._DISPOSITION+"\"", DistributorSchema._DISPOSITION_START);
           }
         return values;
      }
      
   
   
      @Override
      public int delete(Uri uri, String selection, String[] selectionArgs) {
         SQLiteDatabase db = openHelper.getWritableDatabase();
         int count;
         switch (uriMatcher.match(uri)) {
               case DELIVERY_MECHANISM_SET:
                  count = db.delete(Tables.DELIVERY_MECHANISM_TBL, selection, selectionArgs);
                  break;

               case DELIVERY_MECHANISM_ID:
                  String delivery_mechanismID = uri.getPathSegments().get(1);
                  count = db.delete(Tables.DELIVERY_MECHANISM_TBL,
                        DeliveryMechanismTableSchemaBase._ID
                              + "="
                              + delivery_mechanismID
                              + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                              selectionArgs);
                  break;
               
               case POSTAL_SET:
                  count = db.delete(Tables.POSTAL_TBL, selection, selectionArgs);
                  break;

               case POSTAL_ID:
                  String postalID = uri.getPathSegments().get(1);
                  count = db.delete(Tables.POSTAL_TBL,
                        PostalTableSchemaBase._ID
                              + "="
                              + postalID
                              + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                              selectionArgs);
                  break;
               
               case RETRIEVAL_SET:
                  count = db.delete(Tables.RETRIEVAL_TBL, selection, selectionArgs);
                  break;

               case RETRIEVAL_ID:
                  String retrievalID = uri.getPathSegments().get(1);
                  count = db.delete(Tables.RETRIEVAL_TBL,
                        RetrievalTableSchemaBase._ID
                              + "="
                              + retrievalID
                              + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                              selectionArgs);
                  break;
               
               case PUBLICATION_SET:
                  count = db.delete(Tables.PUBLICATION_TBL, selection, selectionArgs);
                  break;

               case PUBLICATION_ID:
                  String publicationID = uri.getPathSegments().get(1);
                  count = db.delete(Tables.PUBLICATION_TBL,
                        PublicationTableSchemaBase._ID
                              + "="
                              + publicationID
                              + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                              selectionArgs);
                  break;
               
               case SUBSCRIPTION_SET:
                  count = db.delete(Tables.SUBSCRIPTION_TBL, selection, selectionArgs);
                  break;

               case SUBSCRIPTION_ID:
                  String subscriptionID = uri.getPathSegments().get(1);
                  count = db.delete(Tables.SUBSCRIPTION_TBL,
                        SubscriptionTableSchemaBase._ID
                              + "="
                              + subscriptionID
                              + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                              selectionArgs);
                  break;
               
            
         default:
            throw new IllegalArgumentException("Unknown URI " + uri);
         }

         getContext().getContentResolver().notifyChange(uri, null);
         return count;
      }

      @Override
      public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
         SQLiteDatabase db = openHelper.getWritableDatabase();
         Uri notifyUri = uri;
         int count;
         switch (uriMatcher.match(uri)) {
               case DELIVERY_MECHANISM_SET:
                  logger.debug("DELIVERY_MECHANISM_SET");
                  count = db.update(Tables.DELIVERY_MECHANISM_TBL, values, selection,
                        selectionArgs);
                  break;

               case DELIVERY_MECHANISM_ID:
                  logger.debug("DELIVERY_MECHANISM_ID");
                  //  notify on the base URI - without the ID ?
                  notifyUri = DeliveryMechanismTableSchemaBase.CONTENT_URI; 
                  String delivery_mechanismID = uri.getPathSegments().get(1);
                  count = db.update(Tables.DELIVERY_MECHANISM_TBL, values, DeliveryMechanismTableSchemaBase._ID
                        + "="
                        + delivery_mechanismID
                        + (TextUtils.isEmpty(selection) ? "" 
                                     : (" AND (" + selection + ')')),
                        selectionArgs);
                  break;
               
               case POSTAL_SET:
                  logger.debug("POSTAL_SET");
                  count = db.update(Tables.POSTAL_TBL, values, selection,
                        selectionArgs);
                  break;

               case POSTAL_ID:
                  logger.debug("POSTAL_ID");
                  //  notify on the base URI - without the ID ?
                  notifyUri = PostalTableSchemaBase.CONTENT_URI; 
                  String postalID = uri.getPathSegments().get(1);
                  count = db.update(Tables.POSTAL_TBL, values, PostalTableSchemaBase._ID
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
                  notifyUri = RetrievalTableSchemaBase.CONTENT_URI; 
                  String retrievalID = uri.getPathSegments().get(1);
                  count = db.update(Tables.RETRIEVAL_TBL, values, RetrievalTableSchemaBase._ID
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
                  notifyUri = PublicationTableSchemaBase.CONTENT_URI; 
                  String publicationID = uri.getPathSegments().get(1);
                  count = db.update(Tables.PUBLICATION_TBL, values, PublicationTableSchemaBase._ID
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
                  notifyUri = SubscriptionTableSchemaBase.CONTENT_URI; 
                  String subscriptionID = uri.getPathSegments().get(1);
                  count = db.update(Tables.SUBSCRIPTION_TBL, values, SubscriptionTableSchemaBase._ID
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
            case DELIVERY_MECHANISM_SET:
            case DELIVERY_MECHANISM_ID:
               return DeliveryMechanismTableSchemaBase.CONTENT_ITEM_TYPE;
            
            case POSTAL_SET:
            case POSTAL_ID:
               return PostalTableSchemaBase.CONTENT_ITEM_TYPE;
            
            case RETRIEVAL_SET:
            case RETRIEVAL_ID:
               return RetrievalTableSchemaBase.CONTENT_ITEM_TYPE;
            
            case PUBLICATION_SET:
            case PUBLICATION_ID:
               return PublicationTableSchemaBase.CONTENT_ITEM_TYPE;
            
            case SUBSCRIPTION_SET:
            case SUBSCRIPTION_ID:
               return SubscriptionTableSchemaBase.CONTENT_ITEM_TYPE;
            
         
      default:
         throw new IllegalArgumentException("Unknown URI " + uri);
      }   
   }
   
   // ===========================================================
   // Static declarations
   // ===========================================================
   static {
      uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.DELIVERY_MECHANISM_TBL, DELIVERY_MECHANISM_SET);
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.DELIVERY_MECHANISM_TBL + "/#", DELIVERY_MECHANISM_ID);
            
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.POSTAL_TBL, POSTAL_SET);
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.POSTAL_TBL + "/#", POSTAL_ID);
            
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.RETRIEVAL_TBL, RETRIEVAL_SET);
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.RETRIEVAL_TBL + "/#", RETRIEVAL_ID);
            
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.PUBLICATION_TBL, PUBLICATION_SET);
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.PUBLICATION_TBL + "/#", PUBLICATION_ID);
            
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.SUBSCRIPTION_TBL, SUBSCRIPTION_SET);
            uriMatcher.addURI(DistributorSchemaBase.AUTHORITY, Tables.SUBSCRIPTION_TBL + "/#", SUBSCRIPTION_ID);
            
      
      HashMap<String, String> columns;
            deliveryMechanismProjectionKey = new String[1];
            deliveryMechanismProjectionKey[0] = DeliveryMechanismTableSchemaBase._ID;

            columns = new HashMap<String, String>();
            columns.put(DeliveryMechanismTableSchemaBase._ID, DeliveryMechanismTableSchemaBase._ID);
               columns.put(DeliveryMechanismTableSchemaBase.CONN_TYPE, "\""+DeliveryMechanismTableSchemaBase.CONN_TYPE+"\""); 
               columns.put(DeliveryMechanismTableSchemaBase.STATUS, "\""+DeliveryMechanismTableSchemaBase.STATUS+"\""); 
               columns.put(DeliveryMechanismTableSchemaBase.UNIT, "\""+DeliveryMechanismTableSchemaBase.UNIT+"\""); 
               columns.put(DeliveryMechanismTableSchemaBase.COST_UP, "\""+DeliveryMechanismTableSchemaBase.COST_UP+"\""); 
               columns.put(DeliveryMechanismTableSchemaBase.COST_DOWN, "\""+DeliveryMechanismTableSchemaBase.COST_DOWN+"\""); 
               columns.put(DeliveryMechanismTableSchemaBase._DISPOSITION, "\""+DeliveryMechanismTableSchemaBase._DISPOSITION+"\"");

            deliveryMechanismProjectionMap = columns;
            
            postalProjectionKey = new String[1];
            postalProjectionKey[0] = PostalTableSchemaBase._ID;

            columns = new HashMap<String, String>();
            columns.put(PostalTableSchemaBase._ID, PostalTableSchemaBase._ID);
               columns.put(PostalTableSchemaBase.CP_TYPE, "\""+PostalTableSchemaBase.CP_TYPE+"\""); 
               columns.put(PostalTableSchemaBase.URI, "\""+PostalTableSchemaBase.URI+"\""); 
               columns.put(PostalTableSchemaBase.NOTICE, "\""+PostalTableSchemaBase.NOTICE+"\""); 
               columns.put(PostalTableSchemaBase.SERIALIZE_TYPE, "\""+PostalTableSchemaBase.SERIALIZE_TYPE+"\""); 
               columns.put(PostalTableSchemaBase.DISPOSITION, "\""+PostalTableSchemaBase.DISPOSITION+"\""); 
               columns.put(PostalTableSchemaBase.EXPIRATION, "\""+PostalTableSchemaBase.EXPIRATION+"\""); 
               columns.put(PostalTableSchemaBase.UNIT, "\""+PostalTableSchemaBase.UNIT+"\""); 
               columns.put(PostalTableSchemaBase.VALUE, "\""+PostalTableSchemaBase.VALUE+"\""); 
               columns.put(PostalTableSchemaBase.DATA, "\""+PostalTableSchemaBase.DATA+"\""); 
               columns.put(PostalTableSchemaBase.CREATED_DATE, "\""+PostalTableSchemaBase.CREATED_DATE+"\""); 
               columns.put(PostalTableSchemaBase.MODIFIED_DATE, "\""+PostalTableSchemaBase.MODIFIED_DATE+"\""); 
               columns.put(PostalTableSchemaBase._DISPOSITION, "\""+PostalTableSchemaBase._DISPOSITION+"\"");

            postalProjectionMap = columns;
            
            retrievalProjectionKey = new String[1];
            retrievalProjectionKey[0] = RetrievalTableSchemaBase._ID;

            columns = new HashMap<String, String>();
            columns.put(RetrievalTableSchemaBase._ID, RetrievalTableSchemaBase._ID);
               columns.put(RetrievalTableSchemaBase.DISPOSITION, "\""+RetrievalTableSchemaBase.DISPOSITION+"\""); 
               columns.put(RetrievalTableSchemaBase.NOTICE, "\""+RetrievalTableSchemaBase.NOTICE+"\""); 
               columns.put(RetrievalTableSchemaBase.URI, "\""+RetrievalTableSchemaBase.URI+"\""); 
               columns.put(RetrievalTableSchemaBase.MIME, "\""+RetrievalTableSchemaBase.MIME+"\""); 
               columns.put(RetrievalTableSchemaBase.PROJECTION, "\""+RetrievalTableSchemaBase.PROJECTION+"\""); 
               columns.put(RetrievalTableSchemaBase.SELECTION, "\""+RetrievalTableSchemaBase.SELECTION+"\""); 
               columns.put(RetrievalTableSchemaBase.ARGS, "\""+RetrievalTableSchemaBase.ARGS+"\""); 
               columns.put(RetrievalTableSchemaBase.ORDERING, "\""+RetrievalTableSchemaBase.ORDERING+"\""); 
               columns.put(RetrievalTableSchemaBase.CONTINUITY, "\""+RetrievalTableSchemaBase.CONTINUITY+"\""); 
               columns.put(RetrievalTableSchemaBase.CONTINUITY_VALUE, "\""+RetrievalTableSchemaBase.CONTINUITY_VALUE+"\""); 
               columns.put(RetrievalTableSchemaBase.EXPIRATION, "\""+RetrievalTableSchemaBase.EXPIRATION+"\""); 
               columns.put(RetrievalTableSchemaBase.CREATED_DATE, "\""+RetrievalTableSchemaBase.CREATED_DATE+"\""); 
               columns.put(RetrievalTableSchemaBase.MODIFIED_DATE, "\""+RetrievalTableSchemaBase.MODIFIED_DATE+"\""); 
               columns.put(RetrievalTableSchemaBase._DISPOSITION, "\""+RetrievalTableSchemaBase._DISPOSITION+"\"");

            retrievalProjectionMap = columns;
            
            publicationProjectionKey = new String[1];
            publicationProjectionKey[0] = PublicationTableSchemaBase._ID;

            columns = new HashMap<String, String>();
            columns.put(PublicationTableSchemaBase._ID, PublicationTableSchemaBase._ID);
               columns.put(PublicationTableSchemaBase.DISPOSITION, "\""+PublicationTableSchemaBase.DISPOSITION+"\""); 
               columns.put(PublicationTableSchemaBase.URI, "\""+PublicationTableSchemaBase.URI+"\""); 
               columns.put(PublicationTableSchemaBase.MIME, "\""+PublicationTableSchemaBase.MIME+"\""); 
               columns.put(PublicationTableSchemaBase.EXPIRATION, "\""+PublicationTableSchemaBase.EXPIRATION+"\""); 
               columns.put(PublicationTableSchemaBase.CREATED_DATE, "\""+PublicationTableSchemaBase.CREATED_DATE+"\""); 
               columns.put(PublicationTableSchemaBase.MODIFIED_DATE, "\""+PublicationTableSchemaBase.MODIFIED_DATE+"\""); 
               columns.put(PublicationTableSchemaBase._DISPOSITION, "\""+PublicationTableSchemaBase._DISPOSITION+"\"");

            publicationProjectionMap = columns;
            
            subscriptionProjectionKey = new String[1];
            subscriptionProjectionKey[0] = SubscriptionTableSchemaBase._ID;

            columns = new HashMap<String, String>();
            columns.put(SubscriptionTableSchemaBase._ID, SubscriptionTableSchemaBase._ID);
               columns.put(SubscriptionTableSchemaBase.DISPOSITION, "\""+SubscriptionTableSchemaBase.DISPOSITION+"\""); 
               columns.put(SubscriptionTableSchemaBase.URI, "\""+SubscriptionTableSchemaBase.URI+"\""); 
               columns.put(SubscriptionTableSchemaBase.MIME, "\""+SubscriptionTableSchemaBase.MIME+"\""); 
               columns.put(SubscriptionTableSchemaBase.SELECTION, "\""+SubscriptionTableSchemaBase.SELECTION+"\""); 
               columns.put(SubscriptionTableSchemaBase.EXPIRATION, "\""+SubscriptionTableSchemaBase.EXPIRATION+"\""); 
               columns.put(SubscriptionTableSchemaBase.NOTICE, "\""+SubscriptionTableSchemaBase.NOTICE+"\""); 
               columns.put(SubscriptionTableSchemaBase.CREATED_DATE, "\""+SubscriptionTableSchemaBase.CREATED_DATE+"\""); 
               columns.put(SubscriptionTableSchemaBase.MODIFIED_DATE, "\""+SubscriptionTableSchemaBase.MODIFIED_DATE+"\""); 
               columns.put(SubscriptionTableSchemaBase._DISPOSITION, "\""+SubscriptionTableSchemaBase._DISPOSITION+"\"");

            subscriptionProjectionMap = columns;
            
   }

   public void setController(BroadcastReceiver controller, IntentFilter filter) {
       this.controller = controller;
       Context context = this.getContext();
       context.registerReceiver(controller, filter);
   }



   static public final File applDir;
   static public final File applCacheDir;
   static public final File applTempDir;
   static {
       applDir = new File(Environment.getExternalStorageDirectory(), "Android/data/edu.vu.isis.ammo.core"); 
       applDir.mkdirs();
       if (! applDir.mkdirs()) {
         logger.error("cannot create files check permissions in manifest : " + applDir.toString());
       }

       applCacheDir = new File(applDir, "cache/distributor"); 
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

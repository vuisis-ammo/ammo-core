package edu.vu.isis.ammo.core.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

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
import android.util.Log;

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


import edu.vu.isis.ammo.core.provider.DistributorSchema;

import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.PostalTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.RetrievalTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.PublicationTableSchemaBase;
import edu.vu.isis.ammo.core.provider.DistributorSchemaBase.SubscriptionTableSchemaBase;


/**
 * Implements and overrides those elements not completed
 * 
 * @author <yourself>
 *    
 */
public class DistributorProvider extends DistributorProviderBase {

   protected class DistributorDatabaseHelper extends DistributorProviderBase.DistributorDatabaseHelper {
      protected DistributorDatabaseHelper(Context context) { super(context); }

   
    @Override
        protected void preloadTables(SQLiteDatabase db) {
//              db.execSQL("INSERT INTO \"" + Tables.DELIVERY_MECHANISM_TBL + "\" ("
//                      + DeliveryMechanismTableSchema.CONN_TYPE + ") "
//                      + "VALUES ('" + DeliveryMechanismTableSchema.CONN_TYPE_CELLULAR + "');");
//              db.execSQL("INSERT INTO \"" + Tables.DELIVERY_MECHANISM_TBL + "\" ("
//                      + DeliveryMechanismTableSchema.CONN_TYPE + ") "
//                      + "VALUES ('" + DeliveryMechanismTableSchema.CONN_TYPE_WIFI + "');");
//              db.execSQL("INSERT INTO \"" + Tables.DELIVERY_MECHANISM_TBL + "\" ("
//                      + DeliveryMechanismTableSchema.CONN_TYPE + ") "
//                      + "VALUES ('" + DeliveryMechanismTableSchema.CONN_TYPE_USB + "');");
           }
    }
    
    private final Logger logger = LoggerFactory.getLogger(DistributorProvider.class);
   
   // ===========================================================
   // Content Provider Overrides
   // ===========================================================

   @Override
   public boolean onCreate() {
	   super.onCreate();
       return true;
   }

   @Override
   protected boolean createDatabaseHelper() {
	  openHelper = new DistributorDatabaseHelper(getContext());
      return false;
   }
   

      @Override
      public Uri insert(Uri uri, ContentValues initialValues) {
         String insertTable = "";
         String nullColumnHack = "";
         Uri tableUri = null;
	  logger.debug("insert: " + uri.toString() );

         ContentValues values = (initialValues != null) 
            ? new ContentValues(initialValues)
            : new ContentValues();
         
         /** Validate the requested uri and do default initialization. */
         switch (uriMatcher.match(uri)) {
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

         //getContext().getContentResolver().notifyChange(uri, null);
	 // TBD SKN - notify change on a row URI, this would still satisfy the broad observers
	 // by setting their notifyForDescendant to true, and would prevent row observers from
	 // getting unnecessary events

         getContext().getContentResolver().notifyChange(playerURI, null);
         return playerURI;
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
                  // notifyUri = PostalTableSchemaBase.CONTENT_URI;    --- TBD SKN MOD
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
                  // notifyUri = RetrievalTableSchemaBase.CONTENT_URI; 
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
                  // notifyUri = PublicationTableSchemaBase.CONTENT_URI; 
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
                  // notifyUri = SubscriptionTableSchemaBase.CONTENT_URI; 
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

}
    

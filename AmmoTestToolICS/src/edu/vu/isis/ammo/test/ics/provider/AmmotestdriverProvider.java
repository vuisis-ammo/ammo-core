// THIS IS GENERATED CODE, WHEN YOU COMPLETE YOUR HAND EDITS REMOVE THIS LINE
package edu.vu.isis.ammo.test.ics.provider;

import android.content.Context;

/**
 * Implements and overrides those elements not completed
 * 
 * @author <yourself>
 *    
 */
public class AmmotestdriverProvider extends AmmotestdriverProviderBase {
   public final static String VERSION = "1.7.0";
   protected class AmmotestdriverDatabaseHelper extends AmmotestdriverProviderBase.AmmotestdriverDatabaseHelper {
      protected AmmotestdriverDatabaseHelper(Context context) { 
         super(context, AmmotestdriverSchema.DATABASE_NAME, AmmotestdriverSchema.DATABASE_VERSION);
      }

/**
   @Override
   protected void preloadTables(SQLiteDatabase db) {
      db.execSQL("INSERT INTO \""+Tables.*_TBL+"\" (" + *Schema.*+") "+"VALUES ('" + *TableSchema.* + "');");
   }
*/
   }

   // ===========================================================
   // Content Provider Overrides
   // ===========================================================

   @Override
   public synchronized boolean onCreate() {
       super.onCreate();
       this.openHelper = new AmmotestdriverDatabaseHelper(getContext());

       return true;
   }

   @Override
   protected synchronized boolean createDatabaseHelper() {
      return false;
   }

}
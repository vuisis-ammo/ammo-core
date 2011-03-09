package edu.vu.isis.ammo.core.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

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
   
}
    

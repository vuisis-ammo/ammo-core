package edu.vu.isis.ammo.core.preferences;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IPrefKeys;
import edu.vu.isis.ammo.util.UniqueIdentifiers;

/**
 * Leave this class in the project so we have an AIDL example for future reference.
 * @author Demetri Miller
 *
 */
public class PreferenceService extends Service {
     @Override
        public void onCreate() {
            super.onCreate();
     }
     
    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }
    
    private final IPreferenceService.Stub mBinder = new IPreferenceService.Stub() {
        
        @Override
        public String getOperatorId() throws RemoteException {
            Log.d("PreferenceService", "::getOperatorId()");
            return PreferenceManager
                 .getDefaultSharedPreferences(PreferenceService.this)
                 .getString(IPrefKeys.CORE_OPERATOR_ID, "transappuser");
        }
        
        @Override
        public String getDeviceId() throws RemoteException {
            return PreferenceManager
                   .getDefaultSharedPreferences(PreferenceService.this)
                   .getString(INetPrefKeys.CORE_DEVICE_ID, 
                      UniqueIdentifiers.device(null));
        }
    };

}

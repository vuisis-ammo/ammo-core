package edu.vu.isis.ammo.core.preferences;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;

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
			return PreferenceManager.getDefaultSharedPreferences(PreferenceService.this).getString("CORE_OPERATOR_ID", "");
		}
		
		@Override
		public String getDeviceId() throws RemoteException {
			return PreferenceManager.getDefaultSharedPreferences(PreferenceService.this).getString("CORE_DEVICE_ID", "");
		}
	};

}

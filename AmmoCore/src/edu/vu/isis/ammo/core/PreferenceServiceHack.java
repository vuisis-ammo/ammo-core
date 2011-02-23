package edu.vu.isis.ammo.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import edu.vu.isis.ammo.AmmoPrefKeys;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;

/**
 * This service is a quick hack I wrote to access the AmmoCore UID preference
 * from other applications. I didn't realize SharedPreferences are limited in 
 * scope at the application level.
 * 
 * When this service is started, it writes the current userId in core prefs
 * to a txt file which can be read by other applications.
 * @author Demetri Miller
 *
 */
public class PreferenceServiceHack extends Service {


	@Override 
	public void onCreate() {
		
	}
	
	@Override 
	public int onStartCommand (Intent intent, int flags, int startId) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String uid = prefs.getString(AmmoPrefKeys.PREF_OPERATOR_ID, "");
		File sdcard = Environment.getExternalStorageDirectory();
		File dir = new File(sdcard.toString() + "/" + "uid.txt");
		try {
			FileOutputStream fos = new FileOutputStream(dir);
			fos.write(uid.getBytes());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return startId;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

}

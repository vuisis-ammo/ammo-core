package edu.vu.isis.ammo.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import edu.vu.isis.ammo.IPrefKeys;

public class CorePreferenceService extends Service {


	@Override
	public void onCreate() {

	}

	@Override
	public int onStartCommand (Intent intent, int flags, int startId) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String uid = prefs.getString(IPrefKeys.PREF_OPERATOR_ID, "");
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
		return Service.START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

}
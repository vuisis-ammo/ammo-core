package edu.vu.isis.ammo.core.ethertracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import edu.vu.isis.ammo.PrefKeys;
import edu.vu.isis.ammo.core.MainActivity;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.network.NetworkService;

public class EthTrackSvc extends Service {

	private static final String TAG = "EthTrackSvc";

	@Override
	public void onCreate() {
		// handleCommand();
	}

	@Override
	public void onDestroy() {
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d("EthTrackSvc", "::onStartCommand with intent " + intent.getAction());
		handleCommand();

		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * @function handleCommand It initiates the RTM message handling by calling
	 * the native method initEthernetNative.
	 * 
	 * It then starts the thread which waits on any RTM messages signifying
	 * state changes of the interface
	 */
	public void handleCommand() {
		
		int ret = this.initEthernetNative();
		
		if (ret == -1)
		{
			Log.i (TAG, "Error in InitEthernet: create or socket bind error, Exiting ...");
			return;
		}

		EtherStatReceiver stat = new EtherStatReceiver("ethersvc", this);
		stat.start();
	}

	private static final int HELLO_ID = 1;

	/*
	 * @function Notify Send a notification to android once interface goes up or
	 * down
	 */
	public int Notify(String msg) {
		this.updateSharedPreferencesForInterfaceStatus(msg);
		
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

		int icon = R.drawable.icon;
		CharSequence tickerText = "Ethernet Status";
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);

		Context context = getApplicationContext();

		CharSequence contentTitle = "Network Interface";
		CharSequence contentText = msg;

		Intent notificationIntent = new Intent(this, EthTrackSvc.class);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);

		mNotificationManager.notify(HELLO_ID, notification);

		return 0;
	}
	
	/**
	 * Writes a flag to the system preferences based on status of interface.
	 * @param status - Status message relating to interface. Either "Up" or "Down"
	 */
	public void updateSharedPreferencesForInterfaceStatus(String msg) {
		NetworkService.ConnectionStatus status = NetworkService.ConnectionStatus.NO_CONNECTION;
		if (msg.equals("Up")) {
			status = NetworkService.ConnectionStatus.CONNECTED;
		}
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Editor editor = prefs.edit();
		 
		editor.putInt(PrefKeys.PHYSICAL_LINK_PREF_STATUS_KEY, status.ordinal());
		editor.commit();
	}

	/*
	 * A native method that is implemented by the 'android_net_ethernet.cpp'
	 * native library, which is packaged with this application.
	 * 
	 * This function waits for any event from the underlying interface
	 */
	public native String waitForEvent();

	/*
	 * This is a native function implemented in android_net_ethernet function.
	 * It initiates the ethernet interface
	 */
	public native int initEthernetNative();

	/*
	 * This function is not used now
	 */
	public native String getInterfaceName(int index);

	/*
	 * This function is not used now
	 */
	public native int getInterfaceCnt();

	/*
	 * @class EtherStatReceiver
	 * 
	 * This extends the Thread class and makes a call on the Native waitForEvent
	 * function.
	 * 
	 * The waitForEvent returns when there is a status change in the underlying
	 * interface.
	 */
	private class EtherStatReceiver extends Thread {

		EthTrackSvc parent;

		/*
		 * The Thread constructor
		 */

		public EtherStatReceiver(String str, EthTrackSvc _parent) {
			super(str);
			parent = _parent;
		}

		/*
		 * The main thread function.
		 * 
		 * It makes a call on the waitForEvent and makes call on the notify
		 * function to send a notification once the interface is either up or
		 * down.
		 */
		public void run() {
			while (true) {

				String res = waitForEvent();
				
				if (res.indexOf("Error") > 0)
				{
					Log.i (TAG, "Error in waitForEvent: Exiting Thread");
					return;
				}

				// send the notification if the interface is
				// up or down
				if ((res.indexOf("Up") > 0) || (res.indexOf("Down") > 0)) {
					parent.Notify(res);

					// Write a true/false value to the shared preferences
					// indicating status
					// of connection.

				}

				try {
					sleep((int) (Math.random() * 1000));
				} catch (InterruptedException e) {
				}
			}
			// Log.i (TAG, "Thread Done");
		}
	}

	static {
        System.loadLibrary("ethrmon");
    }
}

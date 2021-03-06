/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.vu.isis.ammo.core.ethertracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.AmmoCoreApp;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ServiceEx;

public class EthTrackSvc extends ServiceEx {

    private static final Logger logger = LoggerFactory.getLogger("net.ethermon");
	private AmmoCoreApp application;

    private boolean mIsLinkUp = false;
    private EtherStatReceiver mStatReceiver = null; // ether service thread

    public boolean isLinkUp() { return mIsLinkUp; }

    
    @Override
    public void onCreate() {
        this.application = (AmmoCoreApp)this.getApplication();
    }

    @Override
    public void onDestroy() {
    }

    private final IBinder binder = new MyBinder();

    public class MyBinder extends Binder
    {
        public EthTrackSvc getService()
        {
            logger.trace("MyBinder::getService");
            return EthTrackSvc.this;
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
	// logger.info("::onStartCommand with intent {}", intent.getAction());
      handleCommand();

        //this.application.setWiredState(WIRED_NETLINK_DOWN);

        // FIXME: we need a way to query the current state here.

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        logger.trace("MyBinder::onBind {}", Thread.currentThread().toString());
        return binder;
    }

    /*
     * @function handleCommand It initiates the RTM message handling by calling
     * the native method initEthernetNative.
     *
     * It then starts the thread which waits on any RTM messages signifying
     * state changes of the interface
     */
    public void handleCommand() {
	if (mStatReceiver != null)
	    return;		// ether service is already running

        int ret = this.initEthernetNative();

        if (ret == -1)
        {
            logger.error("Error in InitEthernet: create or socket bind error, Exiting ...");
            return;
        }

        mStatReceiver = new EtherStatReceiver("ethersvc", this);
        mStatReceiver.start();
    }

    private static final int HELLO_ID = 1;

    public static final int WIRED_NETLINK_UP_VALUE = 1;
    public static final int[] WIRED_NETLINK_UP = new int[] {WIRED_NETLINK_UP_VALUE};
    public static final int WIRED_NETLINK_DOWN_VALUE = 2;
    public static final int[] WIRED_NETLINK_DOWN = new int[] {WIRED_NETLINK_DOWN_VALUE};

    /*
     * @function Notify Send a notification to android once interface goes up or
     * down
     */
    public int Notify(String msg) {
        this.updateSharedPreferencesForInterfaceStatus(msg);

	logger.info("EtherTracker {}", msg);

        // Start specific application respond on selection

        String ns = Context.NOTIFICATION_SERVICE;
        @SuppressWarnings("unused")
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

        int icon = 0;
        CharSequence tickerText = "Network Status";
        long when = System.currentTimeMillis();

        CharSequence contentTitle = "Network Status";
        CharSequence contentText = "";
        
        if (msg.indexOf("Up") > 0) {
    	    icon = R.drawable.netstatus_up;
    	    contentText = "Network Up";
        } else if (msg.indexOf("Down") > 0) {
    	    icon = R.drawable.netstatus_down;
    	    contentText = "Network Down";    	    
        }        

        Notification notification = new Notification(icon, tickerText, when);

        Context context = getApplicationContext();

        Intent notificationIntent = new Intent(this, EthTrackSvc.class);

        PendingIntent contentIntent = PendingIntent
            .getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText,
                contentIntent);

	//Commenting this out for now since it is causing a lot of grief in 
	// the wifi/3g community ....
	// mNotificationManager.notify(HELLO_ID, notification);

        // Let applications respond immediately by receiving a broadcast intent.

        Intent broadcastIntent = new Intent(AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE);
        if (msg.indexOf("Up") > 0) {
            mIsLinkUp = true;
            broadcastIntent.putExtra("state",AmmoIntents.LINK_UP);
    	    logger.info("Network Link Up");            
        } else if (msg.indexOf("Down") > 0) {
            mIsLinkUp = false;
            broadcastIntent.putExtra("state", AmmoIntents.LINK_DOWN);
    	    logger.info("Network Link Down");
        }
        this.sendBroadcast(broadcastIntent);

        return 0;
    }

    /**
     * Writes a flag to the system preferences based on status of interface.
     * @param status - Status message relating to interface. Either "Up" or "Down"
     */
    public void updateSharedPreferencesForInterfaceStatus(String msg) {
        boolean status = (msg.equals("Up")) ? true : false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = prefs.edit();

        editor.putBoolean(INetDerivedKeys.PHYSICAL_LINK_PREF_IS_ACTIVE, status);
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
                    logger.error("Error in waitForEvent: Exiting Thread");
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
                    sleep((int) 3000);
                } catch (InterruptedException e) {
                }
            }
            // logger.trace("Thread Done");
        }
    }

    static {
        System.loadLibrary("ammocore");
    }
}

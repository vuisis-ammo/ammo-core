package edu.vu.isis.ammmo.ethertracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class EthTrackSvc extends Service {
	
	private static final String TAG = "EthTrackSvc";

	 @Override	
	public void onCreate() {
//		 handleCommand();
    }

	 @Override
    public void onDestroy() {
    }

	 @Override
	 public int onStartCommand(Intent intent, int flags, int startId) {
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
	 * This function will make native calls 
	 */
	public void handleCommand()
	{
		this.initEthernetNative();
		
		EtherStatReceiver stat = new EtherStatReceiver("ethersvc", this);
		stat.start(); 
	}
	
		
	private static final int HELLO_ID = 1;
	
	public int Notify (String msg)
	{
	    String ns = Context.NOTIFICATION_SERVICE;
	        NotificationManager mNotificationManager = 
	        	(NotificationManager) getSystemService(ns);
	        
	        int icon = R.drawable.icon;
	        CharSequence tickerText = "Ethernet Status";
	        long when = System.currentTimeMillis();

	        Notification notification = 
	        	new Notification(icon, tickerText, when);
	        
	        Context context = getApplicationContext();
	        
	        CharSequence contentTitle = "Network Interface";
	        CharSequence contentText = msg;
	        
	        Intent notificationIntent = 
	        	new Intent(this, EthTrackSvc.class);
	        
	        PendingIntent contentIntent = 
	        	PendingIntent.getActivity(this, 0, notificationIntent, 0);

	        notification.setLatestEventInfo(
	        								context, 
	        								contentTitle, 
	        								contentText, 
	        								contentIntent);
	        

	        mNotificationManager.notify(HELLO_ID, notification);
	        
	        return 0;
	    }
	
    /* A native method that is implemented by the
     * 'android_net_ethernet.cpp' native library, which is packaged
     * with this application.
     * 
     * This function waits for any event from the underlying 
     * interface
     */
    public native String  waitForEvent();
    
    /*
     * This is a native function implemented in 
     * android_net_ethernet function. It initiates the 
     * ethernet interface
     */
    public native int initEthernetNative ();
    
    public native String getInterfaceName (int index);
    
    public native int getInterfaceCnt ();	
    
    private class EtherStatReceiver extends Thread {

    	EthTrackSvc parent;
    	
    	public EtherStatReceiver (String str, EthTrackSvc _parent) {
        	super(str);
        	parent = _parent;
        }
        
        public void run() {
        	while (true){
    		//Log.i (TAG, "Inside Thread");
    		
    		String res = waitForEvent ();
    		
    		//Log.i (TAG, "WaitForEvent Returns :" + res);
    		//Log.i (TAG, "WaitForEvent Returns :");
    		
    		if ((res.indexOf("Up") > 0) || (res.indexOf("Down") > 0))
    			parent.Notify (res);
    		
                try {
                	sleep((int)(Math.random() * 1000));
    	    } catch (InterruptedException e) {}
    	}
    	//Log.i (TAG, "Thread Done");
        }
    }
	
}

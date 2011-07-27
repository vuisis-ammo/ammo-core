package edu.vu.isis.ammmo.ethertracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class EthTrackSvc extends Service {
	
private static final Logger logger = LoggerFactory.getLogger( "net.ethertracking" );

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
	 * @function handleCommand
	 * It initiates the RTM message handling by 
	 * calling the native method initEthernetNative.
	 * 
	 * It then starts the thread which waits on any 
	 * RTM messages signifying state changes of the 
	 * interface
	 */
	public void handleCommand()
	{
		this.initEthernetNative();
		
		EtherStatReceiver stat = new EtherStatReceiver("ethersvc", this);
		stat.start(); 
	}
	
		
	private static final int HELLO_ID = 1;

	/*
	 * @function Notify
	 * Send a notification to android once interface 
	 * goes up or down
	 */
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
    
    /*
     * This function is not used now
     */
    public native String getInterfaceName (int index);
    
    /*
     * This function is not used now
     */
    public native int getInterfaceCnt ();	
    
    /*
     * @class EtherStatReceiver
     * 
     * This extends the Thread class and makes a 
     * call on the Native waitForEvent function. 
     * 
     * The waitForEvent returns when there is a 
     * status change in the underlying interface.
     */
    private class EtherStatReceiver extends Thread {

    	EthTrackSvc parent;
    	
    	/*
    	 * The Thread constructor 
    	 */
    	
    	public EtherStatReceiver (String str, EthTrackSvc _parent) {
        	super(str);
        	parent = _parent;
        }
        
    	/*
    	 * The main thread function. 
    	 * 
    	 * It makes a call on the waitForEvent
    	 * and makes call on the notify function 
    	 * to send a notification once the interface is 
    	 * either up or down.
    	 */
        public void run() {
        	while (true){
    		
    		String res = waitForEvent ();
    		
    		
    		// send the notification if the interface is 
    		// up or down 
    		if ((res.indexOf("Up") > 0) || (res.indexOf("Down") > 0))
    			parent.Notify (res);
    		
                try {
                	sleep((int)(Math.random() * 1000));
    	    } catch (InterruptedException e) {}
    	}
    	// logger.info("Thread Done");
        }
    }
	
}

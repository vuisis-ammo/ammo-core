package edu.vu.isis.ammo.core;
/**
 * This is the place for application global information.
 * 
 * Using global variables should follow the following pattern.
 * example:
 *   File dir = AppEx.getInstance().getPublicDirectory("images");
 *   
 * @author phreed
 *
 */
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.distributor.DistributorService;
import edu.vu.isis.ammo.core.ethertracker.EthTrackSvc;
import edu.vu.isis.ammo.core.ui.AmmoActivity;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Environment;

public class ApplicationEx  extends Application {
	private static final Logger logger = LoggerFactory.getLogger(ApplicationEx.class);

	private static ApplicationEx singleton;
	
	public ApplicationEx() {
		super();
	}
	
	public static ApplicationEx getInstance() { 
		return singleton;
	}
	
	@Override
	public final void onCreate() {
		super.onCreate();
		logger.debug("::onCreate");
		singleton = this;
		
		Intent svc = new Intent();
		
		svc.setClass(this, DistributorService.class);
		this.startService(svc);
		// context.startService(NetworkService.LAUNCH);
		
		svc.setClass(this, EthTrackSvc.class);
        this.startService(svc);
	}
	
	/**
	 * This function returns a directory suitable for storing data external to the application.
	 * This has been largely obviated by Environment.getExternalStoragePublicDirectory (String type) 
	 * But that isn't available until APIv8
	 */
	public File getPublicDirectory (String type) {
		File base = new File(Environment.getExternalStorageDirectory(), ApplicationEx.class.toString());
	    return new File(base, type);
	}
	
	/**
	 * These provide an intra-application communication channel.
	 * Using these services can update activities without the overhead of intents.
	 * This should only be used for rapid updates.
	 */
	 private Activity currentActivity;

	 public void setCurrentActivity(Activity currentActivity) {
	        this.currentActivity = currentActivity;
	 }

	 public Activity getCurrentActivity() {
	        return this.currentActivity;
	 }
	 
	 /**
	  * This is for more efficient inter-task communication than intents.
	  * The calling task may be on the ui thread or it may not, so runOnUiThread is needed.
	  * 
	  * @param status
	  */
	
	 // ============= Gateway connection state ==================
	 
	 private int[] gatewayState = null;
	 
	 public int[] getGatewayState() { 
		 return this.gatewayState; 
     }
	 public void setGatewayState(final int[] status) {
		 this.gatewayState = status;
		 if (this.currentActivity == null) return;
		 if (!(this.currentActivity instanceof OnStatusChangeListenerByName)) return;
		 if (!(this.currentActivity instanceof AmmoActivity)) return;
		 final OnStatusChangeListenerByName scl = (OnStatusChangeListenerByName)this.currentActivity;
		 ((Activity)scl).runOnUiThread(new Runnable() {
		    public void run() {
		        scl.onGatewayStatusChange("default", status);
		    }
		 });	
	 }
	 
	// ============= Net Link state ==================
//	 public int[] getNetlinkState() { 
//		 return this.wiredNetlinkState; 
//     }
	 // ============= Wired Link state ==================
	 
	 private int[] wiredNetlinkState = null;
	 
	 public int[] getWiredNetlinkState() { 
		 return this.wiredNetlinkState; 
     }
	 
	 public void setWiredState(final int[] status) {
		 this.wiredNetlinkState = status;
		 if (this.currentActivity == null) return;
		 if (!(this.currentActivity instanceof OnStatusChangeListenerByName)) return;
		 if (!(this.currentActivity instanceof AmmoActivity)) return;
		 final OnStatusChangeListenerByName scl = (OnStatusChangeListenerByName)this.currentActivity;
		 ((Activity)scl).runOnUiThread(new Runnable() {
		    public void run() {
		        scl.onNetlinkStatusChange("wired", status);
		    }
		 });	
	 }


}

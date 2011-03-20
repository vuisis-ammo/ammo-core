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

import android.app.Activity;
import android.app.Application;
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
		singleton = this;
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
	 
	 private GatewayState gatewayState = null;
	 public GatewayState getGatewayState(GatewayState status) { 
		 return this.gatewayState; 
     }
	 
	 public void setGatewayState(GatewayState status) {
		 this.gatewayState = status;
		 if (this.currentActivity == null) return;
		 if (!(this.currentActivity instanceof OnStatusChangeListener)) return;
		 ((OnStatusChangeListener)this.currentActivity)
		 	.onStatusChange("default", status.conn, status.send, status.recv);
	 }
	 
	 public class GatewayState {
		 int conn;
		 int send;
		 int recv;
		 public GatewayState(int conn, int send, int recv) {
			 this.conn = conn;
			 this.send = send;
			 this.recv = recv;
		 }
	 }

}

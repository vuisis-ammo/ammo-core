/**
 * 
 */
package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.distributor.DistributorService;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

/**
 * The CoreService is the main service that all other applications,
 * activities, etc will bind to. The CoreService can then establish
 * bindings to all sub services (like networking, distributor, etc).
 * 
 * @author Demetri Miller
 *
 */
public class CoreService extends Service implements ICoreService {
	
	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger(CoreService.class);
	
	// ===========================================================
	// Fields
	// ===========================================================
	@SuppressWarnings("unused")
	private boolean onCreateCalled = false;
	private boolean distributorServiceStarted = false;
	private Intent distributorServiceLaunchIntent = new Intent(DistributorService.LAUNCH);
	
	// ===========================================================
	// Constructors
	// ===========================================================

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override 
	public void onCreate() {
		super.onCreate();
		this.onCreateCalled = true;
	}
	
	@Override 
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		
		if (intent != null) {
			if (intent.getAction().equals(CoreService.PREPARE_FOR_STOP)) {
				this.teardownService();
				this.stopSelf();
				return START_NOT_STICKY;
			}
		} 
		if (!distributorServiceStarted) {
			logger.debug("Starting distributor service");
			this.startDistributorService();
			distributorServiceStarted = true;
		} else {
			logger.debug("Not starting distributor service...it's already been started");
		}
			
		// We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
	    return START_STICKY;	
	}		
	
	@Override
	public void onDestroy() {
		logger.debug("destroyed...");
		super.onDestroy();
	}
	
	// ===========================================================
	// Distributor Service handling
	// ===========================================================

	private void startDistributorService() {
		ComponentName cn = new ComponentName(
				this.getPackageName(), 
				DistributorService.class.getName());
		distributorServiceLaunchIntent.setComponent(cn);
		this.startService(distributorServiceLaunchIntent);	
	}
	
	// Tell the distributor service to prepare to stop.
	private void teardownService() {
		logger.debug("Tearing down sub-services");
		Intent i = new Intent(DistributorService.PREPARE_FOR_STOP);
		this.startService(i);
		distributorServiceStarted = false;
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

}

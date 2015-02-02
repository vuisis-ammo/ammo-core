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


package edu.vu.isis.ammo.core;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.IDistributorService;

/**
 * <p>
 * The AmmoService is responsible for prioritizing and serializing requests for
 * data communications between distributed application databases. The
 * AmmoService issues calls to the AmmoService for updates and then writes the
 * results to the correct content provider using the deserialization mechanism
 * defined by each content provider.
 * <p>
 * Any activity or application wishing to send data via the AmmoService should
 * use one of the AmmoRequest API methods for communication between said
 * application and AmmoCore.
 * <p>
 * Any activity or application wishing to receive updates when a content
 * provider has been modified can register via a custom ContentObserver
 * subclass.
 * <p>
 * The real work is delegated to the Distributor Thread, which maintains a
 * queue.
 */
public class AmmoService extends Service  {
	
    // ===========================================================
    // Constants
    // ===========================================================
    public static final Logger logger = LoggerFactory.getLogger("service");

    static final private AtomicBoolean isStartCommandSuppressed = new AtomicBoolean(false);

    static public void suppressStartCommand() {
        AmmoService.isStartCommandSuppressed.set(true);
    }

    static public void activateStartCommand() {
        AmmoService.isStartCommandSuppressed.set(false);
    }

    /**
     * The onBind() is called after construction and onCreate().
     */
    @Override
    public IBinder onBind(Intent intent) {
        logger.trace("client binding {} {}",
                Integer.toHexString(System.identityHashCode(this)), intent);
        return new DistributorServiceAidl();
    }
    

    /**
     *  AIDL Implementation
     *
     */
    public class DistributorServiceAidl extends IDistributorService.Stub {
        @Override
        public String makeRequest(AmmoRequest request) throws RemoteException {
            PLogger.API_REQ.trace("make request via bind {}", request);
            if (request == null) {
                logger.error("bad request");
                return null;
            }
            logger.trace("make request {}", request.action.toString());
            return AmmoService.this.impl.distributeRequest(request);
        }

        @Override
        public AmmoRequest recoverRequest(String uuid) throws RemoteException {
            PLogger.API_REQ.trace("recover request via bind {}", uuid);
            logger.trace("recover data request {}", uuid);
            return null;
        }

        public NetworkManager getService() {
            logger.trace("DistributorServiceAidl::getService");
            return AmmoService.this.impl;
        }
    }
    public DistributorServiceAidl newDistributorServiceInstance() {
    	return new DistributorServiceAidl();
    }


    // ===========================================================
    // Lifecycle
    // ===========================================================

    /**
     * In order for the service to be shutdown cleanly the 'serviceStart()'
     * method may be used to prepare_for_stop, it will be stopped shortly and it
     * needs to have some things done before that happens.
     * <p>
     * When the user changes the configuration 'startService()' is run to change
     * the settings.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (AmmoService.isStartCommandSuppressed.get()) {
            logger.trace("Start Command is suppressed intent=[{}] not processed", intent);
            return Service.START_NOT_STICKY;
        }
        logger.trace("::onStartCommand {}", intent);
        // If we get this intent, unbind from all services
        // so the service can be stopped.
        if (intent != null) {
            final String action = intent.getAction();
            if (action != null) {
                if (action.equals(NetworkManager.PREPARE_FOR_STOP)) {
                    this.impl.teardown();
                    

                    final Timer t = new Timer();
                    t.schedule(new TimerTask() {
                        // Stop this service
                        @Override
                        public void run() {
                            AmmoService.this.stopSelf();
                            stopSelf();
                        }
                    }, 1000);
                    return Service.START_NOT_STICKY;
                }
                /**
                 * The following block is deprecated. All requests should be
                 * made via the AIDL binding.
                 */
                if (action.equals("edu.vu.isis.ammo.api.MAKE_REQUEST")) {
                    try {
                        final AmmoRequest request = intent.getParcelableExtra("request");
                        if (request == null) {
                            logger.error("bad request intent {}", intent);
                            return Service.START_NOT_STICKY;
                        }
                        final String result = this.impl.distributeRequest(request);
                        logger.trace("request result {}", result);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        logger.error("could not unmarshall the ammo request parcel");
                    }
                    return Service.START_NOT_STICKY;
                }
                if (action.equals("edu.vu.isis.ammo.AMMO_HARD_RESET")) {
                    this.impl.acquirePreferences();
                    this.impl.refresh();
                    return Service.START_NOT_STICKY;
                }
                if (action.equals(AmmoSettingsAvailabiltyReceiver.ACTION_AVAILABLE)) {
                    this.impl.reloadGlobalSettings();
                    this.impl.acquirePreferences();
                    return Service.START_NOT_STICKY;
                }
                if (action.equals(AmmoSettingsAvailabiltyReceiver.ACTION_UNAVAILABLE)) {
                    this.impl.reloadGlobalSettings();
                    this.impl.acquirePreferences();
                    return Service.START_NOT_STICKY;
                }
            }
        }

        logger.trace("Started AmmoService");
        return START_STICKY;
    }


	private NetworkManager impl = null;

    /**
     * When the service is first created, we should grab the IP and Port values
     * from the SystemPreferences.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        logger.info("ammo service on create {}",
                Integer.toHexString(System.identityHashCode(this)));
        
        this.impl = NetworkManager.getInstance (this);
    }


    @Override
    public void onDestroy() {
        logger.warn("::onDestroy - AmmoService");
        this.impl.onDestroy();
     
        super.onDestroy();
    }

}

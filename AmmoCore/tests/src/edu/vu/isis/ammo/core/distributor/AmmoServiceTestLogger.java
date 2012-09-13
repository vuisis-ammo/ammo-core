/**
 * 
 */
package edu.vu.isis.ammo.core.distributor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.AmmoService;
import android.content.Intent;
import android.os.IBinder;

/**
 */
abstract public class AmmoServiceTestLogger  extends android.test.ServiceTestCase<AmmoService>{
    static final private Logger logger = LoggerFactory.getLogger("test.service.lifecycle");

    public AmmoServiceTestLogger(Class<AmmoService> serviceClass) {
        super(serviceClass);
    }

    @Override
    protected IBinder bindService(Intent intent) {
        logger.info("bind service {}", intent);
        return super.bindService(intent);
    }

    @Override
    protected void startService(Intent intent) {
        logger.info("start service {}", intent);
        super.startService(intent);
    }

    protected void setupService() {
        logger.info("setup service ");
        super.setupService();
    }


}

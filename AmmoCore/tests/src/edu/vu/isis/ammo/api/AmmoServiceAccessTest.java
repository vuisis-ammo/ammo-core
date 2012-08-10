/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Applications program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Intent;
import android.os.IBinder;
import android.test.suitebuilder.annotation.SmallTest;
import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.model.Netlink;

/**
 * This is a simple framework for a test of a Service.  
 * See {@link android.test.ServiceTestCase ServiceTestCase} 
 * for more information on how to write and extend service tests.
 * 
 * To run this test, you can type:
 * adb shell am instrument -w \
 *   -e class edu.vu.isis.ammo.core.test.AmmoServiceTestDeprecated \
 *   edu.vu.isis.ammo.core.test/android.test.InstrumentationTestRunner
 */
/**
 * Test for AmmoCore::AmmoActivity
 * 
 *
 */

public class AmmoServiceAccessTest  extends android.test.ServiceTestCase<AmmoService> {
    private Logger logger;
    
    public AmmoServiceAccessTest() {
          super(AmmoService.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        logger = LoggerFactory.getLogger("test.service.access");
        logger.info("Set Up " );
    }
    

    /** 
     * Tear down is run once everything is complete.
     */
    @Override
    protected void tearDown () throws Exception {
        logger.info("Tear Down" );
        super.tearDown();
    }

    /**
     * The name 'test preconditions' is a convention to signal that if this
     * test doesn't pass, the test case was not set up properly and it might
     * explain any and all failures in other tests.  This is not guaranteed
     * to run before other tests, as junit uses reflection to find the tests.
     */
    @SmallTest
    public void testPreconditions() {
          // assertNotNull(this.ad);
    }

    /**
     * Test basic startup/shutdown of Service
     */
    @SmallTest
    public void testStartable() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), AmmoService.class);
        startService(startIntent); 
    }

    /**
     * Test binding to service
     */
    @SmallTest
    public void testBindable() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), AmmoService.class);
        @SuppressWarnings("unused")
        IBinder service = bindService(startIntent); 
    }
    
    /**
     * FIXME
     * I believe this test is currently broken.
     * The returned value should be an integer (qua Enum) not a boolean.
     */
    public void testWiredState() {
        logger.info("status : wired network connection");
        
        final boolean actual = AmmoPreference
            .newInstance(this.getContext())
            .getBoolean(INetDerivedKeys.PHYSICAL_LINK_PREF_IS_ACTIVE, true /* AmmoIntents.LINK_UP */ );
        logger.info("wired link status [{}]", actual);
        assertEquals("wired link status", /* AmmoIntents.LINK_DOWN */ false, actual);
    }
    
    public void testWifiState() {
        logger.info("status : WiFi network connection");
        
        final int actual = AmmoPreference
            .newInstance(this.getContext())
            .getInt(INetDerivedKeys.WIFI_PREF_IS_ACTIVE, Netlink.NETLINK_DOWN);
        logger.info("WiFi link status [{}]", actual);
        assertEquals("WiFi link status", Netlink.NETLINK_CONNECTED, actual);
    }
    

}
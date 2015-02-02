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
 * <p>
 * To run this test, you can type:
 * <code>
 * adb shell am instrument -w \
 *   -e class edu.vu.isis.ammo.core.test.AmmoServiceTestDeprecated \
 *   edu.vu.isis.ammo.core.test/android.test.InstrumentationTestRunner
 *   </code>
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
     * The name 'test preconditions' is a convention to signal that if this test
     * doesn't pass, the test case was not set up properly and it might explain
     * any and all failures in other tests. This is not guaranteed to run before
     * other tests, as junit uses reflection to find the tests.
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
	/*
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), AmmoService.class);
        startService(startIntent); 
	*/
    }

    /**
     * Test binding to service
     */
    @SmallTest
    public void testBindable() {
	/*
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), AmmoService.class);
        @SuppressWarnings("unused")
        IBinder service = bindService(startIntent); 
	*/
    }
    
    /**
     * FIXME
     * <p>
     * I believe this test is currently broken.
     * The returned value should be an integer (qua Enum) not a boolean.
     * <p>
     * If the test is broken we don't need to be running it until it's fixed/
    /*
    public void testWiredState() {
        logger.info("status : wired network connection");
        
        final int actual = AmmoPreference
            .newInstance(this.getContext())
            .getInt(INetDerivedKeys.PHYSICAL_LINK_PREF_IS_ACTIVE, AmmoIntents.LINK_UP);
        logger.info("wired link status [{}]", actual);
        assertEquals("wired link status", AmmoIntents.LINK_DOWN, actual);
    }
    
    public void testWifiState() {
        logger.info("status : WiFi network connection");
        
        final int actual = AmmoPreference
            .newInstance(this.getContext())
            .getInt(INetDerivedKeys.WIFI_PREF_IS_ACTIVE, Netlink.NETLINK_DOWN);
        logger.info("WiFi link status [{}]", actual);
        assertEquals("WiFi link status", Netlink.NETLINK_CONNECTED, actual);
    }
    
    */

}
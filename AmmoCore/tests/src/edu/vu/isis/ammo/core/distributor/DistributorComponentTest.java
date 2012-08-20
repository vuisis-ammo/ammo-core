/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
 */

package edu.vu.isis.ammo.core.distributor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.test.suitebuilder.annotation.MediumTest;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.TimeInterval;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.AmmoService.DistributorServiceAidl;
import edu.vu.isis.ammo.core.network.NetChannel;

/**
 * This is a simple framework for a test of a Service.  
 * See {@link android.test.ServiceTestCase ServiceTestCase} 
 * for more information on how to write and extend service tests.
 * 
 * To run this test, you can type:
 * adb shell am instrument -w \
 *   -e class edu.vu.isis.ammo.core.test.AmmoServiceTest \
 *   edu.vu.isis.ammo.core.test/android.test.InstrumentationTestRunner
 */
/**
 * This test treats the distributor as a component. the distributor is bounded
 * by:
 * <ul>
 * <li>the ammolib api {post, subscribe, retrieve}
 * <li>the application content providers via RequestSerializer
 * <li>the channels which MockProvider is used by the test.
 * </ul>
 */
public class DistributorComponentTest extends android.test.ServiceTestCase<AmmoService> {
    private Logger logger;

    private AmmoRequest.Builder builder;
    private AmmoService service;

    @SuppressWarnings("unused")
    private final Uri provider = Uri.parse("content://edu.vu.isis.ammo.core/distributor");

    private final String topic = "arbitrary-topic";
    private final Calendar now = Calendar.getInstance();
    final TimeInterval expiration = new TimeInterval(TimeInterval.Unit.HOUR, 1);
    private final int worth = 5;
    private final String filter = "no filter";
    /** time in seconds */
    @SuppressWarnings("unused")
    private final int lifetime = 10;

    final String serializedString = "{\"greeting\":\"Hello World!\"}";

    // final Notice notice = new Notice(new PendingIntent());

    public DistributorComponentTest() {
        super(AmmoService.class);
        logger = LoggerFactory.getLogger("test.service.request");
    }

    /**
     * Keep in mind when acquiring a context that there are multiple candidates:
     * <ul>
     * <li>the context of the service being tested [getContext() or
     * getSystemContext()]
     * <li>the context of the test itself
     * </ul>
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Tear down is run once everything is complete.
     */
    @Override
    protected void tearDown() throws Exception {
        logger.info("Tear Down");
        // this.service.onDestroy();
        super.tearDown();
    }

    /**
     * Start the service.
     * <p>
     * Load the appropriate distribution policy.
     */
    private void startUp(final String policyFileName) {
        final Context targetContext = this.getContext();
        final Context testContext;
        try {
            testContext = targetContext
                    .createPackageContext("edu.vu.isis.ammo.core.tests",
                            Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException ex) {
            logger.error("invalid package", ex);
            return;
        }

        final Intent startIntent = new Intent();
        startIntent.setClass(getContext(), AmmoService.class);
        final IBinder service = bindService(startIntent);
        this.builder = AmmoRequest.newBuilder(getContext());
        if (!(service instanceof DistributorServiceAidl)) {
            fail("not the ammo service");
        }
        this.service = ((DistributorServiceAidl) service).getService();

        InputStream inputStream = null;
        try {
            final AssetManager am = testContext.getAssets();
            for (String filename : am.list(".")) {
                logger.trace("test assets {}", filename);
            }
            inputStream = am.open(policyFileName);
            final InputSource is = new InputSource(new InputStreamReader(inputStream));
            final DistributorPolicy policy = new DistributorPolicy(is);
            this.service.policy(policy);
            
        } catch (IOException ex) {
            logger.warn("invalid path or file name",ex);
            return;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    fail("could not close distributor configuration file "
                            + ex.getLocalizedMessage());
                }
            }
        }
    }
    

    /**
     * Post messages and verify that they meet their appropriate fates.
     */
    @MediumTest
    public void testPostal() {
        this.startUp("dist-policy-single-rule.xml");
        final MockChannel mockChannel = MockChannel.getInstance("mock", this.service);
        this.service.registerChannel(mockChannel);
        logger.info("postal : exercise the distributor");

        final Uri provider = Uri.parse("content://edu.vu.isis.ammo.core/distributor");

        final ContentValues cv = new ContentValues();
        {
            cv.put("greeting", "Hello");
            cv.put("recipient", "World");
            cv.put("emphasis", "!");
        }

        logger.info(
                "args provider [{}] content [{}] topic [{}] now [{}] expire [{}] worth [{}] filter [{}]",
                new Object[] {
                        provider, cv, topic, now, expiration, worth, filter
                });

        try {
            logger.info("subscribe : provider, topic");
            builder
                    .provider(provider)
                    .topic(topic)
                    .post();

        } catch (RemoteException ex) {
            logger.error("could not post", ex);
        } finally {

        }
        final MockNetworkStack network = mockChannel.mockNetworkStack;
        try {
            final ByteBuffer sentBuf = network.getSent();
            logger.debug("delivered message {}", sentBuf);
            logger.debug("delivered message {}", MockNetworkStack.asString(sentBuf));
        } catch (InterruptedException ex) {
            logger.error("could not get posted message", ex);
        }
    }

}

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

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Calendar;

import junit.framework.Assert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Application;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.test.mock.MockApplication;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.IAmmoRequest;
import edu.vu.isis.ammo.api.type.Notice;
import edu.vu.isis.ammo.api.type.TimeInterval;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.AmmoService.DistributorServiceAidl;
import edu.vu.isis.ammo.testutils.RenamingMockContext;

/**
 * This is a simple framework for a test of a Service.  
 * See {@link android.test.ServiceTestCase ServiceTestCase} 
 * for more information on how to write and extend service tests.
 * <p>
 * To run this test, you can type:
 * <code>
 adb shell am instrument -w \
 -e class edu.vu.isis.ammo.core.distributor.DistributorComponentTests \
 edu.vu.isis.ammo.core.tests/pl.polidea.instrumentation.PolideaInstrumentationTestRunner
 * </code>
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
public class DistributorComponentTests extends AmmoServiceTestLogger {
    private Logger logger;

    private AmmoRequest.Builder builder;
    private Application application;
    @SuppressWarnings("unused")
    final private String packageName;
    @SuppressWarnings("unused")
    final private String className;

    private AmmoService service;

    @SuppressWarnings("unused")
    private final Uri provider = Uri.parse("content://edu.vu.isis.ammo.core/distributor");

    private final String topic = "ammo/arbitrary-topic";
    private final Calendar now = Calendar.getInstance();
    final TimeInterval expiration = new TimeInterval(TimeInterval.Unit.HOUR, 1);
    private final int worth = 5;
    private final String filter = "no filter";
    /** time in seconds */
    @SuppressWarnings("unused")
    private final int lifetime = 10;

    final String serializedString = "{\"greeting\":\"Hello World!\"}";

    // final Notice notice = new Notice(new PendingIntent());

    public DistributorComponentTests() {
        this(AmmoService.class.getPackage().getName(), AmmoService.class.getName());
    }

    static final String LOGBACK_XML =
            "<configuration debug='true'>" +
                    " <property name='LOG_DIR' value='/mnt/sdcard' />" +
                    "  <appender name='FILE' class='ch.qos.logback.core.FileAppender'>" +
                    "    <file>${LOG_DIR}/ammo-dist-comp-test.log</file>" +
                    "    <append>true</append>" +
                    "    <encoder>" +
                    "      <pattern>%-4r [%t] %-5p %c{35} - %m%n</pattern>" +
                    "    </encoder>" +
                    "  </appender>" +
                    "  <logger name='api' level='TRACE'/>" +
                    "  <logger name='dist.state' level='TRACE'/>" +
                    "  <logger name='dist.thread' level='TRACE'/>" +
                    "  <logger name='dist.store' level='TRACE'/>" +
                    "  <logger name='dist.serializer' level='TRACE'/>" +
                    "  <logger name='dist.policy.class' level='TRACE'/>" +
                    "  <logger name='service' level='TRACE'/>" +
                    "  <logger name='net.mock' level='TRACE'/>" +
                    "  <logger name='link.mock' level='TRACE'/>" +
                    "  <logger name='test.context.mock' level='TRACE'/>" +
                    "  <logger name='test.request.distribute' level='TRACE'/>" +
                    "  <logger name='test.service.lifecycle' level='TRACE'/>" +
                    "  <root level='OFF'>" +
                    "    <appender-ref ref='FILE' />" +
                    "  </root>" +
                    "</configuration>";

    private static void logInit() {
        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            // load a specific logback.xml
            final JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(lc);
            lc.reset(); // override default configuration
            configurator.doConfigure(
                    // "assets/logback-dist-comp.xml"
                    new ByteArrayInputStream(LOGBACK_XML.getBytes())
                    );

        } catch (JoranException je) {
            // StatusPrinter will handle this
        }
        StatusPrinter.printInCaseOfErrorsOrWarnings(lc);
    }

    public DistributorComponentTests(String packageName, String className) {
        super(AmmoService.class);
        this.setName(className);

        logger = LoggerFactory.getLogger("test.request.distribute");
        this.className = className;
        this.packageName = packageName;
    }

    /**
     * Keep in mind when acquiring a context that there are multiple candidates:
     * <ul>
     * <li>the context of the service being tested [getContext() or
     * getSystemContext()]
     * <li>the context of the test itself
     * </ul>
     * see http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.
     * android/android-apps/4.1
     * .1_r1/com/android/calendar/AsyncQueryServiceTest.java#AsyncQueryServiceTest.se
     * t U p % 2 8 % 2 9
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.application = new MockApplication();
        this.setApplication(this.application);

        final RenamingMockContext mockContext =
                new RenamingMockContext(this.getContext());
        this.setContext(mockContext);
        logInit();
        AmmoService.suppressStartCommand();
        // this.setupService();
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
     * The data store is provided with a name for forensics.
     * <p>
     * Load the appropriate distribution policy.
     * 
     * @throws Exception
     */
    private void startUp(final String policyFileName) throws Exception {
        try {
            if (!(getContext() instanceof RenamingMockContext)) {
                fail("not proper context class");
            }

            DistributorDataStore.backingFileName("distributor.db");
            AmmoService.distributionPolicyFileName(policyFileName);

            final Intent startIntent = new Intent();
            startIntent.setClass(getContext(), AmmoService.class);

            logger.info("startup: binder intent {}", startIntent);
            final IBinder serviceBinder = this.bindService(startIntent);
            // final boolean serviceBindable = getContext().
            // bindService(startIntent, this.conn, Context.BIND_AUTO_CREATE);
            assertNotNull("could not bind", serviceBinder);

            this.service = ((DistributorServiceAidl) serviceBinder).getService();
            logger.info("test service {}", 
                    Integer.toHexString(System.identityHashCode(this.service)));
            // this.service = this.getService();
            logger.info("test service {}", 
                    Integer.toHexString(System.identityHashCode(this.getService())));

            this.service.getAssets();
            assertNotNull("the service is null", this.service);
            // assertSame("the service is not the same",
            // this.service,ammoService);

            this.builder = AmmoRequest.newBuilder(this.getContext(), serviceBinder);
        } catch (Exception ex) {
            logger.error("super exception");
        }
    }

    @SmallTest
    public void testAndroidTestCaseSetUpPropertly() throws Exception {
        logger.info("test Android TestCase Set Up Propertly : start");
        super.testAndroidTestCaseSetupProperly();
    }

    @SmallTest
    public void testServiceTestCaseSetUpPropertly() throws Exception {
        logger.info("test Service TestCase Set Up Propertly : start");
        super.testServiceTestCaseSetUpProperly();
    }

    /**
     * Post messages and verify that they meet their appropriate fates.
     */
    @MediumTest
    public void testPostal() {
        logger.info("test postal : start");
        try {

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
                cv.put("source", "me");
            }

            logger.info("post: provider [{}] payload [{}] topic [{}]",
                    new Object[] {
                            provider, cv, topic
                    });
            logger.info("args now [{}] expire [{}] worth [{}] filter [{}]",
                    new Object[] {
                            now, expiration, worth, filter
                    });
            try {
               
                final IAmmoRequest request = builder
                        .provider(provider)
                        .topic(topic)
                        .payload(cv)
                        .notice(Notice.RESET)
                        .post();
                logger.info("posted request [{}]", request);

            } catch (RemoteException ex) {
                logger.error("could not post", ex);
            } finally {

            }
            Assert.assertNotNull("mock channel not available", mockChannel);
            final MockNetworkStack network = mockChannel.mockNetworkStack;
            final ByteBuffer sentBuf = network.getSent();
            Assert.assertNotNull("not received into send buffer", sentBuf);
            final byte[] expected = new byte[]{-17, -66, -19, -2, -66, 0, 0, 0, 0, 0, 0, 0, 
                    -94, 118, 50, 21, -68, -65, -2, -102, 8, 0, 26, -71, 1, 10, 36, 54, 101, 56, 
                    99, 49, 55, 52, 50, 45, 51, 53, 54, 53, 45, 52, 102, 99, 56, 45, 57, 49, 53, 
                    49, 45, 100, 97, 57, 98, 98, 49, 49, 56, 52, 98, 102, 56, 18, 69, 123, 34, 
                    115, 111, 117, 114, 99, 101, 34, 58, 34, 109, 101, 34, 44, 34, 101, 109, 
                    112, 104, 97, 115, 105, 115, 34, 58, 34, 33, 34, 44, 34, 103, 114, 101, 101, 
                    116, 105, 110, 103, 34, 58, 34, 72, 101, 108, 108, 111, 34, 44, 34, 114, 101,
                    99, 105, 112, 105, 101, 110, 116, 34, 58, 34, 87, 111, 114, 108, 100, 34, 125,
                    26, 20, 97, 109, 109, 111, 47, 97, 114, 98, 105, 116, 114, 97, 114, 121, 45, 116,
                    111, 112, 105, 99, 42, 4, 74, 83, 79, 78, 50, 3, 48, 48, 52, 58, 41, 97, 109, 109,
                    111, 58, 53, 54, 102, 48, 53, 53, 101, 48, 45, 50, 50, 48, 56, 45, 48, 48, 101, 53,
                    45, 102, 102, 102, 102, 45, 102, 102, 102, 102, 56, 101, 52, 100, 100, 98, 57, 49};
            final byte[] actual = sentBuf.array();
            assertArrayEquals("unexpected bytes", expected, actual);

        } catch (Exception ex) {
            logger.error("some generic exception ", ex);
        }
    }

}

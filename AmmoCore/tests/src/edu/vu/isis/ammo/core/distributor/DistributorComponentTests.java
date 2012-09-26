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
                    "  <logger name='mock.channel' level='TRACE'/>" +
                    "  <logger name='mock.net' level='TRACE'/>" +
                    "  <logger name='queue' level='TRACE'/>" +
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
     * .1_r1/com/android/calendar/AsyncQueryServiceTest.java#AsyncQueryServiceTest.
     * s e t U p % 2 8 % 2 9
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
            }

            Assert.assertNotNull("mock channel not available", mockChannel);
            final MockNetworkStack network = mockChannel.mockNetworkStack;
            final ByteBuffer sentBuf = network.getSent();
            Assert.assertNotNull("not received into send buffer", sentBuf);

            // See AmmoGatewayMessage for details
            final byte[] magic = new byte[4];
            sentBuf.get(magic);
            assertArrayEquals("magic error",
                    new byte[] {
                            -17, -66, -19, -2
                    }, magic);

            final int msgSize = sentBuf.getInt();
            Assert.assertEquals("payload size", 190, msgSize);

            final byte priority = sentBuf.get();
            Assert.assertEquals("msg priority", (byte) 0, priority);

            final byte[] reserved = new byte[3];
            sentBuf.get(reserved);
            assertArrayEquals("reserved", new byte[] {
                    0, 0, 0
            }, reserved);

            final byte[] pcheck = new byte[4];
            sentBuf.get(pcheck);
            // assertArrayEquals("payload checksum", new byte[]{-94, 118, 50,
            // 21}, pcheck);

            final byte[] hcheck = new byte[4];
            sentBuf.get(hcheck);
            // assertArrayEquals("header checksum", new byte[]{-68, -65, -2,
            // -102}, hcheck);

            final byte[] protobuf = new byte[sentBuf.remaining()];
            final int pbPosition = sentBuf.position();
            sentBuf.get(protobuf);
            logger.info("protobuf=[{}]", new String(protobuf, "US-ASCII"));
            sentBuf.position(pbPosition);
            
            final byte[] pbHdr = new byte[5];
            sentBuf.get(pbHdr);
            logger.info("protobuf hdr [{}]", pbHdr);

            final byte[] pbUuidKey = new byte[2];
            sentBuf.get(pbUuidKey);
            logger.info("protobuf uuid key [{}]", pbUuidKey);

            final String sampleUuid = "9f95aa76-9904-4130-821f-155d5b3de296";
            final byte[] pbUuid = new byte[sampleUuid.length()];
            sentBuf.get(pbUuid);
            logger.info("protobuf uuid[{}] <{}>", pbUuid.length, new String(pbUuid, "US-ASCII"));
            // Assert.assertEquals("9f95aa76-9904-4130-821f-155d5b3de296", new
            // String(pUuid, "US-ASCII"));

            final byte[] pbPayloadKey = new byte[2];
            sentBuf.get(pbPayloadKey);
            logger.info("protobuf payload key [{}]", pbPayloadKey);
            final String expectedPayload = "{\"source\":\"me\",\"emphasis\":\"!\",\"greeting\":\"Hello\",\"recipient\":\"World\"}";
            final byte[] pbPayload = new byte[expectedPayload.length()];
            sentBuf.get(pbPayload);
            Assert.assertEquals(expectedPayload, new String(pbPayload, "US-ASCII"));

            final byte[] pbTopicKey = new byte[2];
            sentBuf.get(pbTopicKey);
            logger.info("protobuf topic key [{}]", pbTopicKey);
            final String expectedTopic = "ammo/arbitrary-topic";
            final byte[] pbTopic = new byte[expectedTopic.length()];
            sentBuf.get(pbTopic);
            Assert.assertEquals(expectedTopic, new String(pbTopic, "US-ASCII"));

            final byte[] pbEncodingKey = new byte[2];
            sentBuf.get(pbEncodingKey);
            logger.info("protobuf encoding key [{}]", pbEncodingKey);
            final String expectedEncoding = "JSON";
            final byte[] pbEncoding = new byte[expectedEncoding.length()];
            sentBuf.get(pbEncoding);
            Assert.assertEquals(expectedEncoding, new String(pbEncoding, "US-ASCII"));

            final byte[] pbWhatKey = new byte[2];
            sentBuf.get(pbWhatKey);
            logger.info("protobuf what key [{}]", pbWhatKey);
            final String expectedWhat = "004";
            final byte[] pbWhat = new byte[expectedWhat.length()];
            sentBuf.get(pbWhat);
            Assert.assertEquals(expectedWhat, new String(pbWhat, "US-ASCII"));

            final byte[] pbDeviceKey = new byte[2];
            sentBuf.get(pbDeviceKey);
            logger.info("protobuf device key [{}]", pbDeviceKey);
            final String expectedDevice = "ammo:56f055e0-2208-00e5-ffff-ffff8e4ddb91";
            final byte[] pbDevice = new byte[expectedDevice.length()];
            sentBuf.get(pbDevice);
            Assert.assertEquals(expectedDevice, new String(pbDevice, "US-ASCII"));

        } catch (Exception ex) {
            logger.error("some generic exception ", ex);
        }
    }

}

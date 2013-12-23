package edu.vu.isis.ammo.core.network;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import edu.vu.isis.ammo.core.pb.AmmoMessages.MessageWrapper.Builder;

/**
 * Some parts of this class are currently untestable due to ConnectorThread being an anonymous
 * inner class. I have written parts of the tests that should work if this is changed in the
 * future, but these parts are commented out. 
 * @author matthew
 *
 */
public class MulticastChannelTest {
    
    private static final String TEST_NAME = "testing";
    private static final int JUNK_NUM = 457120;
    private static final String JUNK_STRING = "asbgdhjakfwkla";
    private static final AmmoGatewayMessage TEST_AGM;
    
    static {
    	AmmoGatewayMessage.Builder temp = AmmoGatewayMessage.newBuilder();
    	byte[] payload = new byte[10];
    	temp.size(10);
    	temp.payload(payload);
    	TEST_AGM = temp.build();
    }
    
    private MockChannelManager mockChannelManager = new MockChannelManager();

    @Before
    public void setUp() throws Exception {
    }


/*    @Test
    public final void testGetSendReceiveStats() {
        fail("Not yet implemented"); // TODO
    }*/

    @Test
    public final void testGetInstance() {
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
        assertEquals("Name must match!",TEST_NAME, test.name);
        assertEquals("ChannelManager must match!", mockChannelManager, test.mChannelManager);
        
        // This won't work because ConnectorThread is an anonymous inner class of MulticastChannel
        /*Field f = test.getClass().getDeclaredField("connectorThread"); //NoSuchFieldException
        f.setAccessible(true);
        ConnectorThread realState = (ConnectorThread) f.get(test); //IllegalAccessException
        assertEquals("State must match!", NetChannel.showState(INetChannel.STALE), realState);*/
    }

/*    @Test
    public final void testIsConnected() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
        Field thread = test.getClass().getDeclaredField("connectorThread"); //NoSuchFieldException
        thread.setAccessible(true);
        ConnectorThread realState = (ConnectorThread) thread.get(test); //IllegalAccessException
        assertEquals("Must be connected!", realState.isConnected(), test.isConnected());
    }*/

    @Test
    public final void testEnableDisable() {
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
        assertTrue("Should be enabled", test.isEnabled());
        test.disable();
        assertFalse("Should be disabled", test.isEnabled());
        test.enable();
        assertTrue("Should be enabled (2)", test.isEnabled());
    }

    @Test
    public final void testClose() { //This test is pointless
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
        assertFalse("Must be false", test.close());
    }

    @Test
    public final void testSetConnectTimeout() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
        
        assertTrue("Should return true!", test.setConnectTimeout(JUNK_NUM));
        Field timeout = test.getClass().getDeclaredField("connectTimeout"); //NoSuchFieldException
        timeout.setAccessible(true);
        Integer actualTimeout = (Integer) timeout.get(test); //IllegalAccessException
        assertEquals("Value must match", JUNK_NUM, (int) actualTimeout);
        
    }

    @Test
    public final void testSetSocketTimeout() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
    
        assertTrue("Should return true!", test.setSocketTimeout(JUNK_NUM));
        Field timeout = test.getClass().getDeclaredField("socketTimeout"); //NoSuchFieldException
        timeout.setAccessible(true);
        Integer actualTimeout = (Integer) timeout.get(test); //IllegalAccessException
        assertEquals("Value must match", JUNK_NUM, (int) actualTimeout);
    }

    @Test
    public final void testSetHost() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
        
        assertTrue("Should return true!", test.setHost(JUNK_STRING));
        Field address = test.getClass().getDeclaredField("mMulticastAddress"); //NoSuchFieldException
        address.setAccessible(true);
        Integer actualHost = (Integer) address.get(test); //IllegalAccessException
        assertEquals("Value must match", JUNK_NUM, (int) actualHost);
    }

    @Test
    public final void testSetPort() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
        
        assertTrue("Should return true!", test.setPort(JUNK_NUM));
        Field port = test.getClass().getDeclaredField("mMulticastPort"); //NoSuchFieldException
        port.setAccessible(true);
        Integer actualPort = (Integer) port.get(test); //IllegalAccessException
        assertEquals("Value must match", JUNK_NUM, (int) actualPort);
    }

    @Test
    public final void testSetTTL() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
        
        test.setTTL(JUNK_NUM);
        Field port = test.getClass().getDeclaredField("mMulticastTTL"); //NoSuchFieldException
        port.setAccessible(true);
        AtomicInteger actualPort = (AtomicInteger) port.get(test); //IllegalAccessException
        assertEquals("Value must match", JUNK_NUM, actualPort.get());
    }

/*    @Test
    public final void testLinkUp() {
        fail("Not yet implemented"); // Find a way to check if method is called; mockito?
    }*/

/*    @Test
    public final void testLinkDown() {
        fail("Not yet implemented"); // Mockito?
    }*/

/*    @Test
    public final void testReset() {
        fail("Not yet implemented"); // TODO
    }*/

    @Test
    public final void testAuthorizationSucceeded() {
        MulticastChannel test = MulticastChannel.getInstance(TEST_NAME, mockChannelManager);
    	mockChannelManager.didAuthorizationSucceed = false;
    	test.authorizationSucceeded(TEST_AGM);
    	assertTrue("Must have succeeded", mockChannelManager.didAuthorizationSucceed);
    	assertEquals("Payload must match!", TEST_AGM, mockChannelManager.payload);
    }

/*    @Test
    public final void testAuthorizationFailed() {
        fail("Not yet implemented"); // Mockito?
    }*/

/*
 * 
 * Not sure how to test these three
 *
    @Test
    public final void testSendRequest() {
        fail("Not yet implemented"); // TODO
    }

    @Test
    public final void testPutFromSecurityObject() {
        fail("Not yet implemented"); // TODO
    }

    @Test
    public final void testFinishedPuttingFromSecurityObject() {
        fail("Not yet implemented"); // TODO
    }*/

/*    @Test
    public final void testGetLocalIpAddresses() {
        fail("Not yet implemented"); // This test would require network activity; allowed?
    }*/
    
    private class MockChannelManager implements IChannelManager {
    	public boolean didAuthorizationSucceed;
    	public AmmoGatewayMessage payload;

        @Override
        public boolean auth() {
            return false;
        }

        @Override
        public void authorizationSucceeded(NetChannel arg0,
                AmmoGatewayMessage arg1) {
        	didAuthorizationSucceed = true;
        	payload = arg1;
        }

        @Override
        public Builder buildAuthenticationRequest() {
            return null;
        }

        @Override
        public boolean deliver(AmmoGatewayMessage arg0) {
            return false;
        }

        @Override
        public String getOperatorId() {
            return null;
        }

        @Override
        public boolean isAnyLinkUp() {
            return false;
        }

        @Override
        public void statusChange(NetChannel arg0, int arg1, int arg2, int arg3,
                int arg4, int arg5, int arg6) {
        }
        
    }

}

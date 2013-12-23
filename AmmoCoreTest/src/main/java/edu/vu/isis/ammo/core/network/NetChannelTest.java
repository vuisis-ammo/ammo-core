package edu.vu.isis.ammo.core.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import android.content.Context;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;

public class NetChannelTest {
    
	private static final String DEFAULT_NAME = "test";
	private static final Map<Integer, String> mStateMap = new HashMap<Integer, String>();
	static {
		mStateMap.put(NetChannel.PENDING, "PENDING");
		mStateMap.put(NetChannel.EXCEPTION, "EXCEPTION");
		mStateMap.put(NetChannel.CONNECTING, "CONNECTING");
		mStateMap.put(NetChannel.CONNECTED, "CONNECTED");
		mStateMap.put(NetChannel.BUSY, "BUSY");
		mStateMap.put(NetChannel.READY, "READY");
		mStateMap.put(NetChannel.DISCONNECTED, "DISCONNECTED");
		mStateMap.put(NetChannel.STALE, "STALE");
		mStateMap.put(NetChannel.LINK_WAIT, "LINK_WAIT");
		mStateMap.put(NetChannel.WAIT_CONNECT, "WAIT CONNECT");
		mStateMap.put(NetChannel.SENDING, "SENDING");
		mStateMap.put(NetChannel.TAKING, "TAKING");
		mStateMap.put(NetChannel.INTERRUPTED, "INTERRUPTED");
		mStateMap.put(NetChannel.SHUTDOWN, "SHUTDOWN");
		mStateMap.put(NetChannel.START, "START");
		mStateMap.put(NetChannel.RESTART, "RESTART");
		mStateMap.put(NetChannel.WAIT_RECONNECT, "WAIT_RECONNECT");
		mStateMap.put(NetChannel.STARTED, "STARTED");
		mStateMap.put(NetChannel.SIZED, "SIZED");
		mStateMap.put(NetChannel.CHECKED, "CHECKED");
		mStateMap.put(NetChannel.DELIVER, "DELIVER");
		mStateMap.put(NetChannel.DISABLED, "DISABLED");
		int testState = -1; 
		for (int i = 0; i < 5; i++) {
			mStateMap.put(testState, "Undefined State [" + testState + "]");
			testState = new Random().nextInt(1000) + 60;// 60 is currently larger than any valid states. 
			// Please update this block whenever this is not true.   
		}
	}

	@SuppressWarnings("rawtypes")
	private static Class[] humanReadableParamTypes = {Long.class, Boolean.class};
	
	private NetChannelImpl testChannel;
	private SecureRandom random = new SecureRandom();

    @Before
    public void setUp() throws Exception {
        testChannel = new NetChannelImpl("test");
    }
    
    @Test
    public void testShowState() { //This test is somewhat unnecessary...
        Set<Integer> keySet = mStateMap.keySet();
        
        for (Integer i : keySet) {
            String test = NetChannel.showState(i);
            assertEquals("State strings must match!", mStateMap.get(i), test);
        }
    }

    @Test
    public void testNetChannel() {
        assertEquals("Names should match!", DEFAULT_NAME, testChannel.name);
        
        for (int i = 0; i < 10; i++) {
            final String test2 = new BigInteger(130, random).toString(32);
            NetChannelImpl testChannel2 = new NetChannelImpl(test2);
            assertEquals("Names should match!", test2, testChannel2.name);
        }
    }

    @Test
    public void testToString() { //same as the last one, almost...
        assertEquals("Names should match!", DEFAULT_NAME, testChannel.toString());
        
        for (int i = 0; i < 10; i++) {
            final String test2 = new BigInteger(130, random).toString(32);
            NetChannelImpl testChannel2 = new NetChannelImpl(test2);
            assertEquals("Names should match!", test2, testChannel2.toString());
        }
    }

    @Test
    public void testIsAuthenticatingChannel() {
        assertFalse("Must not be authenticating!", testChannel.isAuthenticatingChannel());
    }

    @Test
    public void testGetSendReceiveStats() {
        assertEquals("Must match expected value!", "", testChannel.getSendReceiveStats());
    }

    @Test
    public void testGetSendBitStats() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Method humanReadable = testChannel.getClass().getDeclaredMethod("humanReadableByteCount", humanReadableParamTypes);
        Object[] args = {testChannel.getmBytesSent(), true};

        //static method, testChannel not a required argument.
        String ret = (String) humanReadable.invoke(testChannel, args);
        String expected = "S: " + ret + ", BPS:" + testChannel.getmBpsSent();
        assertEquals("Strings must match!", expected, testChannel.getSendBitStats());
    }

    @Test
    public void testGetReceiveBitStats() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Method humanReadable = testChannel.getClass().getDeclaredMethod("humanReadableByteCount", humanReadableParamTypes);
        Object[] args = {testChannel.getmBytesRead(), true};

        //static method, testChannel not a required argument.
        String ret = (String) humanReadable.invoke(testChannel, args);
        String expected = "R: " + ret + ", BPS:" + testChannel.getmBpsRead();
        assertEquals("Strings must match!", expected, testChannel.getReceiveBitStats());
    }
    
    private class NetChannelImpl extends NetChannel {

        protected NetChannelImpl(String name) { super(name); } //NOT UNDER TEST
        @Override
        public void disable() {} //NOT UNDER TEST
        @Override
        public void enable() {} //NOT UNDER TEST
        @Override
        public void init(Context arg0) {} //NOT UNDER TEST
        @Override
        public boolean isBusy() { return false; } //NOT UNDER TEST
        @Override
        public boolean isConnected() { return false; } //NOT UNDER TEST
        @Override
        public void linkDown(String arg0) {} //NOT UNDER TEST
        @Override
        public void linkUp(String arg0) {} //NOT UNDER TEST
        @Override
        public void reset() {} //NOT UNDER TEST
        @Override
        public DisposalState sendRequest(AmmoGatewayMessage arg0) { return null; } //NOT UNDER TEST
        @Override
        public void toLog(String arg0) {} //NOT UNDER TEST
        
        /**
         * Added only for testing
         * @return mBpsRead
         */
        public long getmBpsRead() {
        	return mBpsRead;
        }
        
        /**
         * Added only for testing
         * @return mBpsSent
         */
        public long getmBpsSent() {
        	return mBpsSent;
        }
        
        /**
         * Added only for testing
         * @return mBytesRead
         */
        public long getmBytesRead() {
        	return mBytesRead;
        }
        
        /**
         * Added only for testing
         * @return mBytesSent
         */
        public long getmBytesSent() {
        	return mBytesSent;
        }
    }
}

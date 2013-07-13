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

package edu.vu.isis.ammo.core.network;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.ChannelListener;
import org.jgroups.JChannel;
import org.jgroups.MembershipListener;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import edu.vu.isis.ammo.api.IAmmo;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.annotation.Monitored;
import edu.vu.isis.ammo.core.internal.DisposalState;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.util.AmmoConfigurator;
import edu.vu.isis.ammo.util.InetHelper;
import edu.vu.isis.ammo.util.UDPSendException;

public class ReliableMulticastChannel extends AddressedChannel {
    private static final Logger logger = LoggerFactory.getLogger("net.rmcast");

    private static final int BURP_TIME = 5 * 1000; // 5 seconds expressed in
                                                   // milliseconds

    /**
     * <code>
     * $ sysctl net.ipv4.tcp_rmem 
     * or 
     * $ cat /proc/sys/net/ipv4/tcp_rmem 
     * 4096 87380 4194304 0x1000 0x15554 0x400000 
     * </code>
     * <p>
     * The first value tells the kernel the minimum receive buffer for each TCP
     * connection, and this buffer is always allocated to a TCP socket, even
     * under high pressure on the system. The second value specified tells the
     * kernel the default receive buffer allocated for each TCP socket. This
     * value overrides the <code>
     * /proc/sys/net/core/rmem_default </code> value used by other protocols.
     * The third and last value specified in this variable specifies the maximum
     * receive buffer that can be allocated for a TCP socket.
     */
    @SuppressWarnings("unused")
    private static final int TCP_RECV_BUFF_SIZE = 0x15554; // the maximum
                                                           // receive buffer
                                                           // size
    @SuppressWarnings("unused")
    private static final int MAX_MESSAGE_SIZE = 0x100000; // arbitrary max size
    private boolean isEnabled = true;

    public final static String config_dir = "config";
    public final static String config_file = "udp.xml";
    private File configFile = null;

    private final Socket socket = null;
    private ConnectorThread connectorThread;

    // New threads
    private SenderThread mSender;
    private ChannelReceiver mReceiver;

    @SuppressWarnings("unused")
    private int connectTimeout = 5 * 1000; // this should come from network
                                           // preferences
    @SuppressWarnings("unused")
    private int socketTimeout = 5 * 1000; // milliseconds.

    /*
     * private String gatewayHost = null; private int gatewayPort = -1;
     */
    private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
    private final Object syncObj;

    private boolean shouldBeDisabled = false;
    @SuppressWarnings("unused")
    private final long flatLineTime;

    private JChannel mJGroupChannel;
    // private MulticastSocket mSocket;
    private InetAddress mMulticastGroup = null;
    private AtomicInteger mMulticastTTL;

    private SenderQueue mSenderQueue;

    private final AtomicBoolean mIsAuthorized;

    @Monitored
    private final AtomicInteger mMessagesSent = new AtomicInteger();
    @Monitored
    private final AtomicInteger mMessagesReceived = new AtomicInteger();

    // I made this public to support the hack to get authentication
    // working before Nilabja's code is ready. Make it private again
    // once his stuff is in.
    public final IChannelManager mChannelManager;
    private final AtomicReference<ISecurityObject> mSecurityObject = new AtomicReference<ISecurityObject>();
    private Context context;

    private ReliableMulticastChannel(String name,
            IChannelManager iChannelManager) {
        super(name);

        logger.trace("Thread <{}>ReliableMulticastChannel::<constructor>",
                Thread.currentThread().getId());
        this.syncObj = this;

        mIsAuthorized = new AtomicBoolean(false);
        mMulticastTTL = new AtomicInteger(1);

        mChannelManager = iChannelManager;

        this.flatLineTime = 20 * 1000; // 20 seconds in milliseconds

        mSenderQueue = new SenderQueue(this);

        // The thread is start()ed the first time the network disables and
        // reenables it.
        this.connectorThread = new ConnectorThread(this);

        // Set up timer to trigger once per minute.
        TimerTask updateBps = new UpdateBpsTask();
        mUpdateBpsTimer.scheduleAtFixedRate(updateBps, 0, BPS_STATS_UPDATE_INTERVAL * 1000);

        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    private Timer mUpdateBpsTimer = new Timer();

    private int mFragDelay = 0;

    class UpdateBpsTask extends TimerTask {
        public void run() {
            logger.trace("UpdateBpsTask fired");

            // Update the BPS stats for the sending and receiving.
            mBpsSent = (mBytesSent - mLastBytesSent) / BPS_STATS_UPDATE_INTERVAL;
            mLastBytesSent = mBytesSent;

            mBpsRead = (mBytesRead - mLastBytesRead) / BPS_STATS_UPDATE_INTERVAL;
            mLastBytesRead = mBytesRead;
        }
    };

    @Override
    public void init(Context context) {
        this.context = context;

        // We need a context, so do this here instead of in the channel.
        createReliableMulticastConfigFile(this.context);
    }

    private void createReliableMulticastConfigFile(Context context) {
        final File dir = this.context.getDir(config_dir,
                Context.MODE_WORLD_READABLE);
        this.configFile = new File(dir, config_file);
        if (!this.configFile.exists()) {
            try {
                InputStream inputStream = context.getAssets().open("udp.xml");
                OutputStream out = new FileOutputStream(this.configFile);

                byte[] buffer = new byte[4096];
                int n = 0;
                while (-1 != (n = inputStream.read(buffer))) {
                    out.write(buffer, 0, n);
                }
                out.close();
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getSendReceiveStats() {
        StringBuilder countsString = new StringBuilder();
        countsString.append("S:").append(mMessagesSent.get()).append(" ");
        countsString.append("R:").append(mMessagesReceived.get());
        return countsString.toString();
    }

    public static ReliableMulticastChannel getInstance(String name,
            IChannelManager iChannelManager, Context context) {
        logger.trace("Thread <{}> ReliableMulticastChannel::getInstance()",
                Thread.currentThread().getId());
        ReliableMulticastChannel instance = new ReliableMulticastChannel(name,
                iChannelManager);
        return instance;
    }

    public boolean isConnected() {
        return this.connectorThread.isConnected();
    }

    /**
     * Was the status changed as a result of enabling the connection.
     * 
     * @return
     */
    public boolean isEnabled() {
        return this.isEnabled;
    }

    public void enable() {
        logger.trace("Thread <{}>::enable", Thread.currentThread().getId());
        synchronized (this.syncObj) {
            if (!this.isEnabled) {
                this.isEnabled = true;

                // if (! this.connectorThread.isAlive())
                // this.connectorThread.start();

                logger.warn("::enable - Setting the state to STALE");
                this.shouldBeDisabled = false;
                this.connectorThread.state.set(IAmmo.NetChannelState.STALE);
            }
        }
    }

    public void disable() {
        logger.trace("Thread <{}>::disable", Thread.currentThread().getId());
        synchronized (this.syncObj) {
            if (this.isEnabled) {
                this.isEnabled = false;
                logger.warn("::disable - Setting the state to DISABLED");
                this.shouldBeDisabled = true;
                this.connectorThread.state.set(IAmmo.NetChannelState.DISABLED);

                // this.connectorThread.stop();
            }
        }
    }

    public boolean close() {
        return false;
    }

    public boolean setConnectTimeout(int value) {
        logger.trace("Thread <{}>::setConnectTimeout {}", Thread
                .currentThread().getId(), value);
        this.connectTimeout = value;
        return true;
    }

    public boolean setSocketTimeout(int value) {
        logger.trace("Thread <{}>::setSocketTimeout {}", Thread.currentThread()
                .getId(), value);
        this.socketTimeout = value;
        this.reset();
        return true;
    }

    public void setFlatLineTime(long flatLineTime) {
        // this.flatLineTime = flatLineTime; // currently broken
    }

    public boolean setHost(String host) {
        logger.trace("Thread <{}>::setHost {}", Thread.currentThread().getId(),
                host);
        if (getAddress() != null
                && getAddress().equals(host))
            return false;
        setAddress(host);
        ReliableMulticastSettings.setIpAddress(host, context);
        this.reset();
        return true;
    }

    public boolean setPort(int port) {
        logger.trace("Thread <{}>::setPort {}", Thread.currentThread().getId(),
                port);
        if (super.setPort(port)) {
            ReliableMulticastSettings.setPort(String.valueOf(port), this.context);
            this.reset();
            return true;
        } else {
            return false;
        }
    }

    public boolean setFragDelay(int frag_delay) {
        logger.trace("Thread <{}>::setFragDelay {}", Thread.currentThread().getId(),
                frag_delay);
        if (this.mFragDelay == frag_delay)
            return false;
        this.mFragDelay = frag_delay;

        // we do not need to change the udp.xml since we are phasing out this
        // feature
        // ReliableMulticastSettings.setPort(String.valueOf(port),
        // this.context);
        this.reset();
        return true;
    }

    public void setTTL(int ttl) {
        logger.trace("Thread <{}>::setTTL {}", Thread.currentThread().getId(),
                ttl);
        this.mMulticastTTL.set(ttl);
    }

    public String toString() {
        return "socket: host[" + getAddress() + "] port["
                + getPort() + "]";
    }

    @Override
    public void linkUp(String name) {
        this.connectorThread.state.linkUp();
    }

    @Override
    public void linkDown(String name) {
        this.connectorThread.state.linkDown();
    }

    /**
     * forces a reconnection.
     */
    public void reset() {
        logger.trace("Thread <{}>::reset", Thread.currentThread().getId());
        logger.trace(
                "connector: {} sender: {} receiver: {}",
                new Object[] {
                        this.connectorThread.showState(),
                        (this.mSender == null ? "none" : this.mSender
                                .getSenderState()),
                        (this.mReceiver == null ? "none" : this.mReceiver
                                .getReceiverState())
                });

        synchronized (this.syncObj) {
            if (!this.connectorThread.isAlive()) {
                this.connectorThread = new ConnectorThread(this);
                this.connectorThread.start();
            }

            this.connectorThread.reset();
        }
    }

    private void statusChange() {
        int connState = this.connectorThread.state.value;
        int senderState = (mSender != null) ? mSender.getSenderState()
                : IAmmo.NetChannelState.PENDING;
        int receiverState = (mReceiver != null) ? mReceiver.getReceiverState()
                : IAmmo.NetChannelState.PENDING;

        try {
            mChannelManager.statusChange(this,
                    this.lastConnState, connState,
                    this.lastSenderState, senderState,
                    this.lastReceiverState, receiverState);
        } catch (Exception ex) {
            logger.error("Exception thrown in statusChange()", ex);
        }
        this.lastConnState = connState;
        this.lastSenderState = senderState;
        this.lastReceiverState = receiverState;
    }

    private void setSecurityObject(ISecurityObject iSecurityObject) {
        mSecurityObject.set(iSecurityObject);
    }

    private ISecurityObject getSecurityObject() {
        return mSecurityObject.get();
    }

    private void setIsAuthorized(boolean iValue) {
        logger.trace("In setIsAuthorized(). value={}", iValue);

        mIsAuthorized.set(iValue);
    }

    public boolean getIsAuthorized() {
        return mIsAuthorized.get();
    }

    public void authorizationSucceeded(AmmoGatewayMessage agm) {
        setIsAuthorized(true);
        mSenderQueue.markAsAuthorized();

        // Tell the AmmoService that we're authorized and have it
        // notify the apps.
        mChannelManager.authorizationSucceeded(this, agm);
    }

    public void authorizationFailed() {
        // Disconnect the channel.
        reset();
    }

    // Called by ReceiverThread to send an incoming message to the
    // appropriate destination.
    private boolean deliverMessage(AmmoGatewayMessage agm) {
        logger.debug("In deliverMessage() {} ", agm);

        boolean result;
        if (mIsAuthorized.get()) {
            logger.trace(" delivering to channel manager");
            result = mChannelManager.deliver(agm);
        } else {
            logger.trace(" delivering to security object");
            result = getSecurityObject().deliverMessage(agm);
        }
        return result;
    }

    /**
     * Called by the SenderThread. This exists primarily to make a place to add
     * instrumentation. Also, follows the delegation pattern.
     */
    private boolean ackToHandler(INetworkService.OnSendMessageHandler handler,
            DisposalState status) {
        return handler.ack(this.name, status);
    }

    // Called by the ConnectorThread.
    private boolean isAnyLinkUp() {
        return mChannelManager.isAnyLinkUp();
    }

    @SuppressWarnings("unused")
    private final AtomicLong mTimeOfLastGoodRead = new AtomicLong(0);

    // Heartbeat-related members.
    private final long mHeartbeatInterval = 10 * 1000; // ms
    private final AtomicLong mNextHeartbeatTime = new AtomicLong(0);

    // Send a heartbeat packet to the gateway if enough time has elapsed.
    // Note: the way this currently works, the heartbeat can only be sent
    // in intervals that are multiples of the burp time. This may change
    // later if I can eliminate some of the wait()s.
    @SuppressWarnings("unused")
    private void sendHeartbeatIfNeeded() {
        // logger.warn( "In sendHeartbeatIfNeeded()." );

        long nowInMillis = System.currentTimeMillis();
        if (nowInMillis < mNextHeartbeatTime.get())
            return;

        // Send the heartbeat here.
        logger.warn("Sending a heartbeat. t={}", nowInMillis);

        // Create a heartbeat message and call the method to send it.
        final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper
                .newBuilder();
        mw.setType(AmmoMessages.MessageWrapper.MessageType.HEARTBEAT);
        mw.setMessagePriority(AmmoGatewayMessage.PriorityLevel.FLASH.v);

        final AmmoMessages.Heartbeat.Builder message = AmmoMessages.Heartbeat
                .newBuilder();
        message.setSequenceNumber(nowInMillis); // Just for testing

        mw.setHeartbeat(message);

        final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(
                mw, null);
        agmb.isGateway(true);
        sendRequest(agmb.build());

        mNextHeartbeatTime.set(nowInMillis + mHeartbeatInterval);
        // logger.warn( "Next heartbeat={}", mNextHeartbeatTime );
    }

    /**
     * manages the connection. enable or disable expresses the operator intent.
     * There is no reason to run the thread unless the channel is enabled. Any
     * of the properties of the channel
     */
    private class ConnectorThread extends Thread implements ChannelListener {
        private final Logger logger = LoggerFactory
                .getLogger("net.rmcast.connector");

        // private final String DEFAULT_HOST = "192.168.1.100";
        // private final int DEFAULT_PORT = 33289;

        private ReliableMulticastChannel parent;
        private final State state;

        private AtomicBoolean mIsConnected;

        protected String acquiredInterfaceName = null;
        private InetHelper inetHelper = InetHelper.INSTANCE;

        public void statusChange() {
            parent.statusChange();
        }

        public void channelConnected(Channel channel) {
        }

        public void channelDisconnected(Channel channel) {
            socketOperationFailed();
        } // Is this right?

        public void channelClosed(Channel channel) {
            socketOperationFailed();
        } // Is this right?

        // Called by the sender and receiver when they have an exception on the
        // socket. We only want to call reset() once, so we use an
        // AtomicBoolean to keep track of whether we need to call it.
        public void socketOperationFailed() {
            if (mIsConnected.compareAndSet(true, false))
                state.reset();
        }

        private ConnectorThread(ReliableMulticastChannel parent) {
            super(new StringBuilder("RMcast-Connect-").append(Thread.activeCount()).toString());
            logger.trace("Thread <{}>ConnectorThread::<constructor>", Thread
                    .currentThread().getId());
            this.parent = parent;
            this.state = new State();
            mIsConnected = new AtomicBoolean(false);
        }

        private class State {
            private int value;
            private int actual;

            private long attempt; // used to uniquely name the connection

            public State() {
                this.value = IAmmo.NetChannelState.STALE;
                this.attempt = Long.MIN_VALUE;
            }

            public synchronized void linkUp() {
                logger.debug("link up request {} {}", this.value, this.actual);
                this.notifyAll();
            }

            public synchronized void linkDown() {
                logger.debug("link down request {} {}", this.value, this.actual);
                this.reset();
            }

            public synchronized void set(int state) {
                logger.trace("Thread <{}>State::set", Thread.currentThread()
                        .getId());
                if (state == IAmmo.NetChannelState.STALE) {
                    this.reset();
                } else {
                    this.value = state;
                    this.notifyAll();
                }
            }

            /**
             * changes the state as requested unless the current state is
             * disabled.
             * 
             * @param state
             * @return false if disabled; true otherwise
             */
            public synchronized boolean setUnlessDisabled(int state) {
                logger.trace("Thread <{}>State::setUnlessDisabled", Thread
                        .currentThread().getId());
                if (state == IAmmo.NetChannelState.DISABLED)
                    return false;
                this.set(state);
                return true;
            }

            public synchronized int get() {
                return this.value;
            }

            public synchronized boolean isConnected() {
                return this.value == IAmmo.NetChannelState.CONNECTED;
            }

            public synchronized boolean isDisabled() {
                return this.value == IAmmo.NetChannelState.DISABLED;
            }

            /**
             * Previously this method would only set the state to stale if the
             * current state were CONNECTED. It may be important to return to
             * STALE from other states as well. For example during a failed link
             * attempt. Therefore if the attempt value matches then reset to
             * STALE This also causes a reset to reliably perform a notify.
             * 
             * @param attempt value (an increasing integer)
             * @return
             */
            public synchronized boolean failure(long attempt) {
                if (attempt != this.attempt)
                    return true;
                return this.reset();
            }

            public synchronized boolean failureUnlessDisabled(long attempt) {
                if (this.value == IAmmo.NetChannelState.DISABLED)
                    return false;
                return this.failure(attempt);
            }

            public synchronized boolean reset() {
                attempt++;
                this.value = IAmmo.NetChannelState.STALE;
                this.notifyAll();
                return true;
            }

            public String showState() {
                if (this.value == this.actual)
                    return NetChannel.showState(this.value);
                else
                    return NetChannel.showState(this.actual) + "->"
                            + NetChannel.showState(this.value);
            }
        }

        public boolean isConnected() {
            return this.state.isConnected();
        }

        public long getAttempt() {
            return this.state.attempt;
        }

        public String showState() {
            return this.state.showState();
        }

        /**
         * reset forces the channel closed if open.
         */
        public void reset() {
            this.state.failure(this.state.attempt);
        }


        /**
         * A value machine based. Most of the time this machine will be in a
         * CONNECTED value. In that CONNECTED value the machine wait for the
         * connection value to change or for an interrupt indicating that the
         * thread is being shut down. The value machine takes care of the
         * following constraints: We don't need to reconnect unless. 1) the
         * connection has been lost 2) the connection has been marked stale 3)
         * the connection is enabled. 4) an explicit reconnection was requested
         * 
         * @return
         */
        @Override
        public void run() {
            try {
                logger.info("Thread <{}>ConnectorThread::run", Thread
                        .currentThread().getId());
                MAINTAIN_CONNECTION: while (true) {
                    logger.trace("connector state: {}", this.showState());

                    if (this.parent.shouldBeDisabled)
                        this.state.set(IAmmo.NetChannelState.DISABLED);
                    switch (this.state.get()) {
                        case IAmmo.NetChannelState.DISABLED:
                            try {
                                synchronized (this.state) {
                                    logger.trace("this.state.get() = {}",
                                            this.state.get());
                                    this.parent.statusChange();
                                    disconnect();

                                    // Wait for a link interface.
                                    while (this.state.isDisabled()) {
                                        logger.trace("Looping in Disabled");
                                        this.state.wait(BURP_TIME);
                                    }
                                }
                            } catch (InterruptedException ex) {
                                logger.warn("connection intentionally disabled {}",
                                        this.state);
                                this.state.setUnlessDisabled(IAmmo.NetChannelState.STALE);
                                break MAINTAIN_CONNECTION;
                            }
                            break;
                        case IAmmo.NetChannelState.STALE:
                            disconnect();
                            this.state.setUnlessDisabled(IAmmo.NetChannelState.LINK_WAIT);
                            break;

                        case IAmmo.NetChannelState.LINK_WAIT:
                            this.parent.statusChange();
                            try {
                                synchronized (this.state) {
                                    while (!parent.isAnyLinkUp()
                                            && !this.state.isDisabled()
                                            && !this.isInterfaceAcquired()) {

                                        // wait for a link interface
                                        this.state.wait(BURP_TIME);
                                    }
                                    this.state
                                            .setUnlessDisabled(IAmmo.NetChannelState.DISCONNECTED);
                                }
                            } catch (InterruptedException ex) {
                                logger.warn("connection intentionally disabled {}",
                                        this.state);
                                this.state.setUnlessDisabled(IAmmo.NetChannelState.STALE);
                                break MAINTAIN_CONNECTION;
                            }
                            this.parent.statusChange();
                            // or else wait for link to come up, triggered
                            // through
                            // broadcast receiver

                            break;

                        case IAmmo.NetChannelState.DISCONNECTED:
                            this.parent.statusChange();
                            if (!this.connect()) {
                                this.state.setUnlessDisabled(IAmmo.NetChannelState.CONNECTING);
                            } else {
                                this.state.setUnlessDisabled(IAmmo.NetChannelState.CONNECTED);
                            }
                            break;

                        case IAmmo.NetChannelState.CONNECTING: // keep trying
                            try {
                                this.parent.statusChange();
                                long attempt = this.getAttempt();

                                synchronized (this.state) {
                                    this.state
                                            .wait(NetChannel.CONNECTION_RETRY_DELAY);
                                    if (this.connect()) {
                                        this.state
                                                .setUnlessDisabled(IAmmo.NetChannelState.CONNECTED);
                                    } else {
                                        this.state.failureUnlessDisabled(attempt);
                                    }
                                }
                                this.parent.statusChange();
                            } catch (InterruptedException ex) {
                                logger.trace("sleep interrupted - intentional disable, exiting thread ...");
                                this.reset();
                                break MAINTAIN_CONNECTION;
                            }
                            break;

                        case IAmmo.NetChannelState.CONNECTED: {
                            this.parent.statusChange();
                            try {
                                synchronized (this.state) {
                                    while (this.isConnected()) 
                                    {
                                        if (HEARTBEAT_ENABLED) {
                                        // parent.sendHeartbeatIfNeeded();
                                        }
                                        // wait for somebody to change the
                                        // connection status
                                        this.state.wait(BURP_TIME);
                                    }
                                }
                            } catch (InterruptedException ex) {
                                logger.warn("connection intentionally disabled {}",
                                        this.state);
                                this.state.setUnlessDisabled(IAmmo.NetChannelState.STALE);
                                break MAINTAIN_CONNECTION;
                            }
                            this.parent.statusChange();
                        }
                            break;
                        default:
                            try {
                                long attempt = this.getAttempt();
                                this.parent.statusChange();
                                synchronized (this.state) {
                                    this.state
                                            .wait(NetChannel.CONNECTION_RETRY_DELAY);

                                    this.state.failureUnlessDisabled(attempt);
                                }
                                this.parent.statusChange();
                            } catch (InterruptedException ex) {
                                logger.trace("sleep interrupted - intentional disable, exiting thread ...");
                                this.reset();
                                break MAINTAIN_CONNECTION;
                            }
                    }
                }

            } catch (Exception ex) {
                logger.warn("failed to run multicast", ex);
                this.state.set(IAmmo.NetChannelState.EXCEPTION);
            }
            try {
                if (this.parent.socket == null) {
                    logger.error("channel closing without active socket");
                    return;
                }
                this.parent.socket.close();
            } catch (IOException ex) {
                logger.error("channel closing without proper socket", ex);
            }
            logger.error("channel closing");
        }

        /**
         * Prepare the socket to connect to the target interface.
         * 
         * @return
         * @throws SocketException
         */
        private boolean isInterfaceAcquired() throws SocketException {
            this.acquiredInterfaceName = this.inetHelper.acquireInterface();
            if (this.acquiredInterfaceName == null) {
                return false;
            }
            final NetworkInterface networkInterface = NetworkInterface
                    .getByName(this.acquiredInterfaceName);
            final List<InetAddress> networkAddresses = Collections.list(networkInterface.getInetAddresses()); 
            for (InetAddress networkAddr : networkAddresses) {
                System.setProperty("jgroups.bind_addr", networkAddr.getHostAddress());
                return true;
            }
            return false;
        }

        private boolean connect() {
            logger.trace("Thread <{}>ConnectorThread::connect", Thread
                    .currentThread().getId());

            try {
                parent.mMulticastGroup = InetAddress
                        .getByName(getAddress());
            } catch (UnknownHostException e) {
                logger.warn("could not resolve host name");
                return false;
            }

            // Create the MulticastSocket.
            if (parent.mJGroupChannel != null)
                logger.error("Tried to create mJGroupChannel when we already had one.");
            try {

                AmmoConfigurator ammoConfigurator =
                        new AmmoConfigurator(parent.configFile, parent.context);

                // parent.mJGroupChannel = new JChannel(parent.configFile);
                parent.mJGroupChannel = new JChannel(ammoConfigurator);
                // Put call to set operator ID here.
                parent.mJGroupChannel.setName(mChannelManager.getOperatorId());

                // parent.mJGroupChannel.setOpt( Channel.AUTO_RECONNECT,
                // Boolean.TRUE ); // deprecated
                parent.mJGroupChannel.connect("AmmoGroup");
            } catch (Exception ex) {
                logger.warn("connection to {}:{} failed: ", new Object[] {
                        parent.mMulticastGroup, getPort()
                }, ex);
                parent.mJGroupChannel.disconnect();
                parent.mJGroupChannel.close();
                parent.mJGroupChannel = null;
                return false;
            }

            logger.info("connection to {}:{} established ",
                    parent.mMulticastGroup, getPort());

            mIsConnected.set(true);
            mBytesSent = 0;
            mBytesRead = 0;
            mLastBytesSent = 0;
            mLastBytesRead = 0;
            mBpsSent = 0;
            mBpsRead = 0;

            // Create the security object. This must be done before
            // the ReceiverThread is created in case we receive a
            // message before the SecurityObject is ready to have it
            // delivered.
            if (parent.getSecurityObject() != null)
                logger.error("Tried to create SecurityObject when we already had one.");
            parent.setSecurityObject(new ReliableMulticastSecurityObject(parent));

            // Create the sending thread.
            if (parent.mSender != null)
                logger.error("Tried to create Sender when we already had one.");
            parent.mSender = new SenderThread(this, parent,
                    parent.mSenderQueue, parent.mJGroupChannel);
            parent.mSender.start();

            // Create the receiving thread.
            if (parent.mReceiver != null)
                logger.error("Tried to create Receiver when we already had one.");
            parent.mReceiver = new ChannelReceiver(this, parent);

            parent.mJGroupChannel.setReceiver(parent.mReceiver);
            // parent.mJGroupChannel.addChannelListener( this ); // don't do
            // this yet

            /*
             * try { parent.mJGroupChannel.connect( "AmmoGroup" ); } catch (
             * Exception ex ) { // FIXME: shouldn't happen, but figure out how
             * to clean up and return false. }
             */

            // Should this be moved to before the construction of the Sender and
            // Receiver?
            // FIXME: don't pass in the result of buildAuthenticationRequest().
            // This is
            // just a temporary hack.
            // parent.getSecurityObject().authorize(
            // mChannelManager.buildAuthenticationRequest());
            setIsAuthorized(true);
            mSenderQueue.markAsAuthorized();

            return true;
        }

        private boolean disconnect() {
            logger.trace("Thread <{}>ConnectorThread::disconnect", Thread
                    .currentThread().getId());
            try {
                mIsConnected.set(false);

                // Have to close the socket first unless we convert to
                // an interruptible datagram socket.
                if (parent.mJGroupChannel != null) {
                    logger.debug("Closing ReliableMulticastSocket.");
                    parent.mJGroupChannel.disconnect();
                    parent.mJGroupChannel.close(); // will disconnect first if
                                                   // still connected
                    logger.debug("Done");

                    parent.mJGroupChannel = null;
                }

                if (mSender != null) {
                    logger.debug("interrupting SenderThread");
                    mSender.interrupt();
                }

                // We need to wait here until the threads have stopped.
                try {
                    logger.debug("calling join() on SenderThread");
                    if (mSender != null) {
                        mSender.join();
                    }
                } catch (InterruptedException ex) {
                    logger.info("socket i/o exception", ex);
                    // Do this here, too, since if we exited early because
                    // of an exception, we want to make sure that we're in
                    // an unauthorized state.
                    mSenderQueue.reset();
                    setIsAuthorized(false);
                    return false;
                }

                parent.mSender = null;
                parent.mReceiver = null;

                logger.debug("resetting SenderQueue");
                mSenderQueue.reset();

                setIsAuthorized(false);

                parent.setSecurityObject(null);
            } catch (Exception e) {
                logger.error("Caught Exception", e);
                // Do this here, too, since if we exited early because
                // of an exception, we want to make sure that we're in
                // an unauthorized state.
                setIsAuthorized(false);
                return false;
            }
            logger.debug("returning after successful disconnect().");
            return true;
        }
    }

    /**
     * do your best to send the message. This makes use of the blocking "put"
     * call. A proper producer-consumer should use put or add and not offer.
     * "put" is blocking call. If this were on the UI thread then offer would be
     * used.
     * 
     * @param agm AmmoGatewayMessage
     * @return
     */
    public DisposalState sendRequest(AmmoGatewayMessage agm) {
        return mSenderQueue.putFromDistributor(agm);
    }

    public void putFromSecurityObject(AmmoGatewayMessage agm) {
        mSenderQueue.putFromSecurityObject(agm);
    }

    public void finishedPuttingFromSecurityObject() {
        mSenderQueue.finishedPuttingFromSecurityObject();
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    class SenderQueue {
        public SenderQueue(ReliableMulticastChannel iChannel) {
            mChannel = iChannel;

            setIsAuthorized(false);
            // mDistQueue = new LinkedBlockingQueue<AmmoGatewayMessage>( 20 );
            mDistQueue = new PriorityBlockingQueue<AmmoGatewayMessage>(20);
            mAuthQueue = new LinkedList<AmmoGatewayMessage>();
        }

        // In the new design, aren't we supposed to let the
        // AmmoService know if the outgoing queue is full or not?
        public DisposalState putFromDistributor(AmmoGatewayMessage iMessage) {
            logger.trace("putFromDistributor()");
            try {
                if (!mDistQueue.offer(iMessage, 1, TimeUnit.SECONDS)) {
                    logger.warn(
                            "reliable multicast channel not taking messages {}",
                            DisposalState.BUSY);
                    return DisposalState.BUSY;
                }
            } catch (InterruptedException e) {
                return DisposalState.REJECTED;
            }
            return DisposalState.QUEUED;
        }

        public synchronized void putFromSecurityObject(
                AmmoGatewayMessage iMessage) {
            logger.trace("putFromSecurityObject()");
            mAuthQueue.offer(iMessage);
        }

        public synchronized void finishedPuttingFromSecurityObject() {
            logger.trace("finishedPuttingFromSecurityObject()");
            notifyAll();
        }

        // This is called when the SecurityObject has successfully
        // authorized the channel.
        public synchronized void markAsAuthorized() {
            logger.trace("Marking channel as authorized");
            notifyAll();
        }

        /**
         * Condition wait for the some request to the channel. An initial
         * request cannot be processed until the channel has authenticated. This
         * is where the authorized SenderThread blocks when taking a
         * distribution request. If not yet authorized then return the first
         * item in the authentication queue, removing that item from its queue.
         * 
         * @return
         * @throws InterruptedException
         */
        public synchronized AmmoGatewayMessage take()
                throws InterruptedException {
            logger.trace("taking from SenderQueue");
            while (!mChannel.getIsAuthorized() && mAuthQueue.size() < 1) {
                logger.trace("wait()ing in SenderQueue");
                wait();
            }
            if (mChannel.getIsAuthorized()) {
                return mDistQueue.take();
            }
            // must be the mAuthQueue.size() > 0
            return mAuthQueue.remove();
        }

        // Somehow synchronize this here.
        public synchronized void reset() {
            logger.trace("reset()ing the SenderQueue");
            // Tell the distributor that we couldn't send these
            // packets.
            AmmoGatewayMessage msg = mDistQueue.poll();
            while (msg != null) {
                if (msg.handler != null)
                    mChannel.ackToHandler(msg.handler, DisposalState.REJECTED);
                msg = mDistQueue.poll();
            }

            setIsAuthorized(false);
        }

        private BlockingQueue<AmmoGatewayMessage> mDistQueue;
        private LinkedList<AmmoGatewayMessage> mAuthQueue;
        private ReliableMulticastChannel mChannel;
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    class SenderThread extends Thread {

        // Asserted maximum useful size of trace logging message (e.g. size of
        // PLI msg)
        private static final int TRACE_CUTOFF_SIZE = 512;

        public SenderThread(ConnectorThread iParent,
                ReliableMulticastChannel iChannel, SenderQueue iQueue,
                JChannel iJChannel) {
            super(new StringBuilder("RMcast-Sender-").append(Thread.activeCount()).toString());
            mParent = iParent;
            mChannel = iChannel;
            mQueue = iQueue;
            mJChannel = iJChannel;
        }

        /**
         * the message format is
         */
        @Override
        public void run() {
            logger.info("Thread <{}>::run()", Thread.currentThread().getId());

            // Block on reading from the queue until we get a message to send.
            // Then send it on the socket channel. Upon getting a socket error,
            // notify our parent and go into an error state.

            while (mState != IAmmo.NetChannelState.INTERRUPTED) {
                AmmoGatewayMessage msg = null;
                try {
                    setSenderState(IAmmo.NetChannelState.TAKING);
                    msg = mQueue.take(); // The main blocking call

                    logger.debug("Took a message from the send queue");
                } catch (InterruptedException ex) {
                    logger.debug("interrupted taking messages from send queue",
                            ex);
                    setSenderState(IAmmo.NetChannelState.INTERRUPTED);
                    mParent.socketOperationFailed();
                    break;
                } catch (Exception ex) {
                    logger.warn("sender threw exception while take()ing", ex);
                    setSenderState(IAmmo.NetChannelState.INTERRUPTED);
                    mParent.socketOperationFailed();
                    break;
                }

                try {
                    ByteBuffer buf = msg.serialize(endian,
                            AmmoGatewayMessage.VERSION_1_FULL, (byte) 0);
                    setSenderState(IAmmo.NetChannelState.SENDING);

                    DatagramPacket packet = new DatagramPacket(buf.array(),
                            buf.remaining(), mChannel.mMulticastGroup,
                            mChannel.getPort());
                    logger.debug("Sending datagram packet. length={}",
                            packet.getLength());

                    if (buf.array().length <= TRACE_CUTOFF_SIZE) {
                        logger.debug("...{}", buf.array());
                    } else {
                        logger.debug("...buffer: {} bytes", buf.array().length);
                    }
                    logger.debug("...{}", buf.remaining());
                    logger.debug("...{}", mChannel.mMulticastGroup);
                    logger.debug("...{}", mChannel.getPort());

                    mJChannel.send(null, buf.array());

                    mMessagesSent.incrementAndGet();
                    mBytesSent += packet.getLength();
                    notifyObserver();

                    logger.info("Send packet to Network, size ({})",
                            packet.getLength());

                    // legitimately sent to gateway.
                    if (msg.handler != null)
                        mChannel.ackToHandler(msg.handler, DisposalState.SENT);
                } catch (SocketException ex) {
                    logger.debug("sender caught SocketException");
                    if (msg.handler != null)
                        mChannel.ackToHandler(msg.handler,
                                DisposalState.REJECTED);
                    setSenderState(IAmmo.NetChannelState.INTERRUPTED);
                    mParent.socketOperationFailed();
                    break;
                } catch (UDPSendException ex) {
                    logger.debug("sender caught SocketException");
                    if (msg.handler != null)
                        mChannel.ackToHandler(msg.handler,
                                DisposalState.REJECTED);
                    setSenderState(IAmmo.NetChannelState.INTERRUPTED);
                    mParent.socketOperationFailed();
                    break;
                } catch (Exception ex) {
                    logger.warn("sender threw exception", ex);
                    if (msg.handler != null)
                        mChannel.ackToHandler(msg.handler, DisposalState.BAD);
                    setSenderState(IAmmo.NetChannelState.INTERRUPTED);
                    mParent.socketOperationFailed();
                    break;
                }
            }
            logger.info("Thread <{}>::end()", Thread.currentThread().getId());
        }

        private void setSenderState(int iState) {
            synchronized (this) {
                mState = iState;
            }
            mParent.statusChange();
        }

        public synchronized int getSenderState() {
            return mState;
        }

        private int mState = IAmmo.NetChannelState.TAKING;
        private ConnectorThread mParent;
        private ReliableMulticastChannel mChannel;
        private SenderQueue mQueue;
        private JChannel mJChannel;
        private final Logger logger = LoggerFactory
                .getLogger("net.rmcast.sender");
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    class ChannelReceiver extends ReceiverAdapter implements MembershipListener {
        public ChannelReceiver(ConnectorThread iParent,
                ReliableMulticastChannel iDestination) {
            mParent = iParent;
            mDestination = iDestination;
            setReceiverState(IAmmo.NetChannelState.START);
        }

        @Override
        public void receive(Message msg) {
            logger.trace("Thread <{}>: ChannelReceiver::receive()", Thread
                    .currentThread().getId());

            // If we get an error, notify our parent and go into an error state.

            // This code assumes that each datagram contained exactly one
            // message.
            // If this needs to change in the future, this code will need to be
            // revised.

            // List<InetAddress> addresses = getLocalIpAddresses();

            // byte[] raw = new byte[100000]; // FIXME: What is max datagram
            // size?
            if (getReceiverState() != IAmmo.NetChannelState.INTERRUPTED) {
                try {
                    // DatagramPacket packet = new DatagramPacket( raw,
                    // raw.length );
                    // logger.debug( "Calling receive() on the MulticastSocket."
                    // );

                    setReceiverState(IAmmo.NetChannelState.START);

                    // mSocket.receive( packet );
                    // logger.debug( "Received a packet. length={}",
                    // packet.getLength() );
                    // logger.debug( "source IP={}", packet.getAddress() );
                    // if ( addresses.contains( packet.getAddress() ))
                    // {
                    // logger.error( "Discarding packet from self." );
                    // continue;
                    // }
                    logger.info("Received a packet from ({}) size({})",
                            msg.getSrc(), msg.getLength());
                    mBytesRead += msg.getLength();

                    if (msg.getSrc().toString()
                            .equals(mChannelManager.getOperatorId())) {
                        logger.debug("Got a Message looped back to me : Ignoring");
                        return;
                    }

                    ByteBuffer buf = ByteBuffer.wrap(msg.getBuffer());
                    // ByteBuffer buf = ByteBuffer.wrap( packet.getData(),
                    // packet.getOffset(),
                    // packet.getLength() );
                    buf.order(endian);

                    // wrap() creates a buffer that is ready to be drained,
                    // so there is no need to flip() it.
                    AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage
                            .extractHeader(buf);

                    if (agmb == null) {
                        logger.error("Deserialization failure. Discarded invalid packet.");
                        return;
                    }

                    // extract the payload
                    byte[] payload = new byte[agmb.size()];
                    buf.get(payload, 0, buf.remaining());

                    AmmoGatewayMessage agm = agmb.payload(payload)
                            .channel(this.mDestination).build();
                    setReceiverState(IAmmo.NetChannelState.DELIVER);
                    mDestination.deliverMessage(agm);
                    logger.trace("received a message, size ({})",
                            payload.length);

                    mMessagesReceived.incrementAndGet(); // got another msg
                    notifyObserver();
                } catch (ClosedChannelException ex) {
                    logger.warn("receiver threw ClosedChannelException", ex);
                    setReceiverState(IAmmo.NetChannelState.INTERRUPTED);
                    mParent.socketOperationFailed();
                } catch (Exception ex) {
                    logger.warn("receiver threw exception", ex);
                    setReceiverState(IAmmo.NetChannelState.INTERRUPTED);
                    mParent.socketOperationFailed();
                }
            }
        }

        @Override
        public void viewAccepted(View new_view) {
            // I have kept this error, need to change it to info .. NR
            logger.error("Membership View Changed: {}", new_view);
        }

        @Override
        public void suspect(Address suspected_mbr) {
            logger.error("Member Suspected : {}", suspected_mbr.toString());
        }

        private void setReceiverState(int iState) {
            synchronized (this) {
                mState = iState;
            }
            mParent.statusChange();
        }

        public synchronized int getReceiverState() {
            return mState;
        }

        private int mState = IAmmo.NetChannelState.TAKING; // fixme
        private ConnectorThread mParent;
        private ReliableMulticastChannel mDestination;
        private final Logger logger = LoggerFactory
                .getLogger("net.rmcast.receiver");
    }

    // ********** UTILITY METHODS ****************

    // A routine to get all local IP addresses
    //
    public List<InetAddress> getLocalIpAddresses() {
        List<InetAddress> addresses = new ArrayList<InetAddress>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    addresses.add(inetAddress);
                    logger.error("address: {}", inetAddress);
                }
            }
        } catch (SocketException ex) {
            logger.error("get local IP address", ex);
        }

        return addresses;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public void toLog(String context) {
        PLogger.SET_PANTHR_RMC.debug("{} {}:{} ", new Object[] {
                context,
                getAddress(), getPort()
        });
    }
    
    @Override
    public void notifyObserver() {
        if (mObserver != null) {
            mObserver.notifyUpdate(this);
        }
    }
}

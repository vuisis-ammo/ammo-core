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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.pb.AmmoMessages;

/**
 * Two long running threads and one short. The long threads are for sending and
 * receiving messages. The short thread is to connect the socket. The sent
 * messages are placed into a queue if the socket is connected.
 * 
 */
public class SSLChannel extends TcpChannelBase {
    // a class based logger to be used by static methods ...
    private static final Logger classlogger = LoggerFactory
            .getLogger("net.ssl");

    // private instance logger which is created in the constructor and used
    // all over ...
    private Logger logger = null;

    private static final int BURP_TIME = 5 * 1000; // 5 seconds expressed in
                                                   // milliseconds

    /**
     * $ sysctl net.ipv4.tcp_rmem or $ cat /proc/sys/net/ipv4/tcp_rmem 4096
     * 87380 4194304 0x1000 0x15554 0x400000
     * 
     * The first value tells the kernel the minimum receive buffer for each TCP
     * connection, and this buffer is always allocated to a TCP socket, even
     * under high pressure on the system.
     * 
     * The second value specified tells the kernel the default receive buffer
     * allocated for each TCP socket. This value overrides the
     * /proc/sys/net/core/rmem_default value used by other protocols.
     * 
     * The third and last value specified in this variable specifies the maximum
     * receive buffer that can be allocated for a TCP socket.
     * 
     */
    private static final int TCP_RECV_BUFF_SIZE = 0x15554; // the maximum
                                                           // receive buffer
                                                           // size
    private static final int MAX_MESSAGE_SIZE = 0x100000; // arbitrary max size
    private boolean isEnabled = true;

    /** default timeout is 45 seconds */
    private int DEFAULT_WATCHDOG_TIMOUT = 45;

    private ConnectorThread connectorThread;

    // New threads
    private SenderThread mSender;
    private ReceiverThread mReceiver;

    /** these should come from network preferences, both are in milliseconds */
    private int connectTimeout = 30 * 1000;
    private int socketTimeout = 30 * 1000;

    private String gatewayHost = null;
    private int gatewayPort = -1;

    private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
    private final Object syncObj;

    private boolean shouldBeDisabled = false;
    private long flatLineTime;

    // TCP socket
    // private Socket mSocket;
    // SSL Socket
    private SSLSocket mSSLSocket;

    private DataInputStream mDataInputStream;
    private DataOutputStream mDataOutputStream;

    private final SenderQueue mSenderQueue;

    private final AtomicBoolean mIsAuthorized;

    private String channelName = null;

    // status counts for gui
    private final AtomicInteger mMessagesSent = new AtomicInteger();
    private final AtomicInteger mMessagesReceived = new AtomicInteger();

    // I made this public to support the hack to get authentication
    // working before Nilabja's code is ready. Make it private again
    // once his stuff is in.
    public final IChannelManager mChannelManager;
    private final AtomicReference<ISecurityObject> mSecurityObject = new AtomicReference<ISecurityObject>();

    private final AtomicLong mTimeOfLastGoodRead = new AtomicLong(0);

    private final AtomicLong mTimeOfLastGoodSend = new AtomicLong(0);

    // Heartbeat-related members.
    private final long mHeartbeatInterval = 10 * 1000; // ms
    private final AtomicLong mNextHeartbeatTime = new AtomicLong(0);

    // timer for regular updates
    private Timer mUpdateBpsTimer = new Timer();

    /**
     * Constructor for SSLChannel
     * 
     * @param name
     * @param iChannelManager
     */
    protected SSLChannel(String name, IChannelManager iChannelManager) {
        // super:
        super(name);
        // store values from parameters:
        channelName = name;
        mChannelManager = iChannelManager;
        // create the instance logger for instance methods:
        logger = LoggerFactory.getLogger("net.channel.tcp.base." + channelName);
        logger.trace("Thread <{}>SSLChannel::<constructor>", Thread
                .currentThread().getId());
        // internal use objects:
        this.syncObj = this;
        mIsAuthorized = new AtomicBoolean(false);
        mSenderQueue = new SenderQueue(this);
        this.connectorThread = new ConnectorThread(this);
        this.flatLineTime = DEFAULT_WATCHDOG_TIMOUT * 1000; // seconds to ms

        // Set up timer to trigger once per minute.
        TimerTask updateBps = new UpdateBpsTask();
        mUpdateBpsTimer.scheduleAtFixedRate(updateBps, 0,
                BPS_STATS_UPDATE_INTERVAL * 1000);
    }

    class UpdateBpsTask extends TimerTask {
        public void run() {
            logger.trace("UpdateBpsTask fired");

            // Update the BPS stats for the sending and receiving.
            mBpsSent = (mBytesSent - mLastBytesSent)
                    / BPS_STATS_UPDATE_INTERVAL;
            mLastBytesSent = mBytesSent;

            mBpsRead = (mBytesRead - mLastBytesRead)
                    / BPS_STATS_UPDATE_INTERVAL;
            mLastBytesRead = mBytesRead;
        }
    };

    public static SSLChannel getInstance(String name,
            IChannelManager iChannelManager) {
        classlogger.trace("Thread <{}>::getInstance", Thread.currentThread()
                .getId());
        final SSLChannel instance = new SSLChannel(name, iChannelManager);
        return instance;
    }

    @Override
    public String getSendReceiveStats() {
        StringBuilder countsString = new StringBuilder();
        countsString.append("S:").append(mMessagesSent.get()).append(" ");
        countsString.append("R:").append(mMessagesReceived.get());
        return countsString.toString();
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

                logger.trace("::enable - Setting the state to STALE");
                this.shouldBeDisabled = false;
                this.connectorThread.state.set(NetChannel.STALE);
            }
        }
    }

    public void disable() {
        logger.trace("Thread <{}>::disable", Thread.currentThread().getId());
        synchronized (this.syncObj) {
            if (this.isEnabled) {
                this.isEnabled = false;
                logger.trace("::disable - Setting the state to DISABLED");
                this.shouldBeDisabled = true;
                this.connectorThread.state.set(NetChannel.DISABLED);
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
        if (gatewayHost != null && gatewayHost.equals(host))
            return false;
        this.gatewayHost = host;
        this.reset();
        return true;
    }

    public boolean setPort(int port) {
        logger.trace("Thread <{}>::setPort {}", Thread.currentThread().getId(),
                port);
        if (gatewayPort == port)
            return false;
        this.gatewayPort = port;
        this.reset();
        return true;
    }

    public String toString() {
        return new StringBuilder().append("channel ").append(super.toString())
                .append("socket: host[").append(this.gatewayHost).append("] ")
                .append("port[").append(this.gatewayPort).append("]")
                .toString();
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
        // PLogger.proc_debug("reset ")
        logger.trace("Thread <{}>::reset", Thread.currentThread().getId());
        logger.trace(
                "connector: {} sender: {} receiver: {}",
                new Object[] {
                        this.connectorThread.showState(),
                        (this.mSender == null ? "none" : this.mSender
                                .getSenderState()),
                        (this.mReceiver == null ? "none" : this.mReceiver
                                .getReceiverState()) });

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
                : INetChannel.PENDING;
        int receiverState = (mReceiver != null) ? mReceiver.getReceiverState()
                : INetChannel.PENDING;

        try {
            mChannelManager.statusChange(this, this.lastConnState, connState,
                    this.lastSenderState, senderState, this.lastReceiverState,
                    receiverState);
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

        final boolean result;
        if (mIsAuthorized.get()) {
            logger.trace(" delivering to channel manager");
            result = mChannelManager.deliver(agm);
        } else {
            logger.trace(" delivering to security object");
            ISecurityObject so = getSecurityObject();
            if (so == null) {
                logger.warn("security object not set");
                return false;
            }
            result = so.deliverMessage(agm);
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

    // This should be called each time we successfully read data from the
    // socket.
    private void resetTimeoutWatchdog() {
        // logger.debug( "Resetting watchdog timer" );
        mTimeOfLastGoodRead.set(System.currentTimeMillis());
        // update the time of last good send ...
        mTimeOfLastGoodSend.set(0);
    }

    // Returns true if we have gone more than flatLineTime without reading
    // any data from the socket.
    private boolean hasWatchdogExpired() {
        logger.trace("Check for connection expiry {}",
                mTimeOfLastGoodSend.get());

        if (mTimeOfLastGoodSend.get() == 0)
            return false;
        if ((System.currentTimeMillis() - mTimeOfLastGoodSend.get()) > flatLineTime)
            return true;
        else
            return false;
        // return (System.currentTimeMillis() - mTimeOfLastGoodRead.get()) >
        // flatLineTime;
    }

    // Send a heartbeat packet to the gateway if enough time has elapsed.
    // Note: the way this currently works, the heartbeat can only be sent
    // in intervals that are multiples of the burp time. This may change
    // later if I can eliminate some of the wait()s.
    private void sendHeartbeatIfNeeded() {
        // logger.warn( "In sendHeartbeatIfNeeded()." );

        long nowInMillis = System.currentTimeMillis();
        if (nowInMillis < mNextHeartbeatTime.get())
            return;

        // check DistQueue, if some thing is already there to be sent,
        // no need for a heartbeat
        if (mSenderQueue.sizeOfDistQ() > 0)
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
        agmb.isHeartbeat(true);
        this.sendRequest(agmb.build());

        mNextHeartbeatTime.set(nowInMillis + mHeartbeatInterval);
        // logger.warn( "Next heartbeat={}", mNextHeartbeatTime );
    }

    /**
     * manages the connection. enable or disable expresses the operator intent.
     * There is no reason to run the thread unless the channel is enabled.
     * 
     * Any of the properties of the channel
     * 
     */
    private class ConnectorThread extends Thread {
        private Logger logger = null;

        private final String DEFAULT_HOST = "192.168.1.100";
        private final int DEFAULT_PORT = 33289;

        private SSLChannel parent;
        private final State state;

        private AtomicBoolean mIsConnected;

        public void statusChange() {
            parent.statusChange();
        }

        // Called by the sender and receiver when they have an exception on the
        // Socket. We only want to call reset() once, so we use an
        // AtomicBoolean to keep track of whether we need to call it.
        public void socketOperationFailed() {
            if (mIsConnected.compareAndSet(true, false))
                state.reset();
        }

        private ConnectorThread(SSLChannel parent) {
            super(new StringBuilder("Tcp-Connect-")
                    .append(Thread.activeCount()).toString());
            // create the logger
            logger = LoggerFactory.getLogger("net.channel.tcp.connecgtor."
                    + channelName);
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
                this.value = STALE;
                this.attempt = Long.MIN_VALUE;
            }

            public synchronized void linkUp() {
                logger.debug("link status {} {}", this.value, this.actual);
                this.notifyAll();
            }

            public synchronized void linkDown() {
                this.reset();
            }

            public synchronized void set(int state) {
                logger.trace("Thread <{}>State::set", Thread.currentThread()
                        .getId());
                if (state == STALE) {
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
                if (state == DISABLED)
                    return false;
                this.set(state);
                return true;
            }

            public synchronized int get() {
                return this.value;
            }

            public synchronized boolean isConnected() {
                return this.value == INetChannel.CONNECTED;
            }

            public synchronized boolean isDisabled() {
                return this.value == INetChannel.DISABLED;
            }

            /**
             * Previously this method would only set the state to stale if the
             * current state were CONNECTED. It may be important to return to
             * STALE from other states as well. For example during a failed link
             * attempt. Therefore if the attempt value matches then reset to
             * STALE This also causes a reset to reliably perform a notify.
             * 
             * @param attempt
             *            value (an increasing integer)
             * @return
             */
            public synchronized boolean failure(long attempt) {
                if (attempt != this.attempt)
                    return true;
                return this.reset();
            }

            public synchronized boolean failureUnlessDisabled(long attempt) {
                if (this.value == INetChannel.DISABLED)
                    return false;
                return this.failure(attempt);
            }

            public synchronized boolean reset() {
                attempt++;
                this.value = STALE;
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
         * thread is being shut down.
         * 
         * The value machine takes care of the following constraints: We don't
         * need to reconnect unless. 1) the connection has been lost 2) the
         * connection has been marked stale 3) the connection is enabled. 4) an
         * explicit reconnection was requested
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
                        this.state.set(NetChannel.DISABLED);
                    switch (this.state.get()) {
                    case NetChannel.DISABLED:
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
                            this.state.setUnlessDisabled(NetChannel.STALE);
                            break MAINTAIN_CONNECTION;
                        }
                        break;
                    case NetChannel.STALE:
                        disconnect();
                        this.state.setUnlessDisabled(NetChannel.LINK_WAIT);
                        break;

                    case NetChannel.LINK_WAIT:
                        this.parent.statusChange();
                        try {
                            synchronized (this.state) {
                                while (!parent.isAnyLinkUp()
                                        && !this.state.isDisabled()) {
                                    this.state.wait(BURP_TIME); // wait for a
                                                                // link
                                                                // interface
                                }
                                this.state
                                        .setUnlessDisabled(NetChannel.DISCONNECTED);
                            }
                        } catch (InterruptedException ex) {
                            logger.warn("connection intentionally disabled {}",
                                    this.state);
                            this.state.setUnlessDisabled(NetChannel.STALE);
                            break MAINTAIN_CONNECTION;
                        }
                        this.parent.statusChange();
                        // or else wait for link to come up, triggered through
                        // broadcast receiver
                        break;

                    case NetChannel.DISCONNECTED:
                        this.parent.statusChange();
                        if (!this.connect()) {
                            this.state.setUnlessDisabled(NetChannel.CONNECTING);
                        } else {
                            this.state.setUnlessDisabled(NetChannel.CONNECTED);
                        }
                        break;

                    case NetChannel.CONNECTING: // keep trying
                        try {
                            this.parent.statusChange();
                            long attempt = this.getAttempt();
                            synchronized (this.state) {
                                this.state
                                        .wait(NetChannel.CONNECTION_RETRY_DELAY);
                                if (this.connect()) {
                                    this.state
                                            .setUnlessDisabled(NetChannel.CONNECTED);
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

                    case NetChannel.CONNECTED: {
                        this.parent.statusChange();
                        try {
                            synchronized (this.state) {
                                while (this.isConnected()) {
                                    if (HEARTBEAT_ENABLED)
                                        parent.sendHeartbeatIfNeeded();

                                    // wait for somebody to change the
                                    // connection status
                                    this.state.wait(BURP_TIME);

                                    if (HEARTBEAT_ENABLED
                                            && parent.hasWatchdogExpired()) {
                                        logger.warn("Watchdog timer expired!!");
                                        this.state
                                                .failureUnlessDisabled(getAttempt());
                                    }
                                }
                            }
                        } catch (InterruptedException ex) {
                            logger.warn("connection intentionally disabled {}",
                                    this.state);
                            this.state.setUnlessDisabled(NetChannel.STALE);
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
                this.state.setUnlessDisabled(NetChannel.EXCEPTION);
                logger.error("channel exception", ex);
            }
            try {
                if (this.parent.mSSLSocket == null) {
                    logger.error("channel closing without active socket}");
                    return;
                }
                this.parent.mSSLSocket.close();
            } catch (IOException ex) {
                logger.error("channel closing without proper socket", ex);
            }
            logger.error("channel closing");
        }

        private boolean connect() {
            logger.trace("Thread <{}>ConnectorThread::connect", Thread
                    .currentThread().getId());

            // Resolve the hostname to an IP address.
            String host = (parent.gatewayHost != null) ? parent.gatewayHost
                    : DEFAULT_HOST;
            int port = (parent.gatewayPort > 10) ? parent.gatewayPort
                    : DEFAULT_PORT;
            InetAddress ipaddr = null;
            try {
                ipaddr = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                logger.warn("could not resolve host name");
                return false;
            }

            // Create the Socket
            InetSocketAddress sockAddr = new InetSocketAddress(ipaddr, port);
            try {
                if (parent.mSSLSocket != null) {
                    logger.error("Tried to create mSocket when we already had one.");
                }
                final long startConnectionMark = System.currentTimeMillis();

                /**
                 * 
                 */
                // Security.addProvider(new
                // com.sun.net.ssl.internal.ssl.Provider()); see if I have to
                // add provider like this

                // TODO here is SSL socket creation

                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory
                        .getDefault();

                parent.mSSLSocket = (SSLSocket) factory.createSocket();

                // parent.mSSLSocket.connect(sockAddr);
                if (parent.mSSLSocket == null) {
                    logger.warn("mSSLSocket was null");

                }

                parent.mSSLSocket.connect(sockAddr);

                // parent.mSocket = new Socket();
                // parent.mSocket.connect(sockAddr, parent.connectTimeout);

                parent.mSSLSocket.setSoTimeout(parent.socketTimeout);
                final long finishConnectionMark = System.currentTimeMillis();
                logger.info("connection time to establish={} ms",
                        finishConnectionMark - startConnectionMark);

                // SSL "Should" be the exact same after Input & Output Streams
                // are created.
                parent.mDataInputStream = new DataInputStream(
                        parent.mSSLSocket.getInputStream());
                parent.mDataOutputStream = new DataOutputStream(
                        parent.mSSLSocket.getOutputStream());

            } catch (AsynchronousCloseException ex) {
                logger.warn("connection async close failure to {}:{} ", ipaddr,
                        port, ex);
                parent.mSSLSocket = null;
                return false;
            } catch (ClosedChannelException ex) {
                logger.info("connection closed channel failure to {}:{} ",
                        ipaddr, port, ex);
                parent.mSSLSocket = null;
                return false;
            } catch (ConnectException ex) {
                logger.info("connection failed to {}:{}", ipaddr, port, ex);
                parent.mSSLSocket = null;
                return false;
            } catch (SocketException ex) {
                logger.warn("connection timeout={} sec, socket {}:{}",
                        parent.connectTimeout / 1000, ipaddr, port, ex);
                parent.mSSLSocket = null;
                return false;
            } catch (Exception ex) {
                logger.warn("connection failed to {}:{}", ipaddr, port, ex);
                parent.mSSLSocket = null;
                return false;
            }

            logger.info("connection established to {}:{}", ipaddr, port);

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
            parent.setSecurityObject(new TcpSecurityObject(parent));

            // Create the sending thread.
            if (parent.mSender != null)
                logger.error("Tried to create Sender when we already had one.");
            parent.mSender = new SenderThread(this, parent,
                    parent.mSenderQueue, parent.mSSLSocket);
            parent.mSender.start();

            // Create the receiving thread.
            if (parent.mReceiver != null)
                logger.error("Tried to create Receiver when we already had one.");
            parent.mReceiver = new ReceiverThread(this, parent,
                    parent.mSSLSocket);
            parent.mReceiver.start();

            // FIXME: don't pass in the result of buildAuthenticationRequest().
            // This is
            // just a temporary hack.
            parent.getSecurityObject().authorize(
                    mChannelManager.buildAuthenticationRequest());

            return true;
        }

        private boolean disconnect() {
            logger.trace("Thread <{}>ConnectorThread::disconnect", Thread
                    .currentThread().getId());
            try {
                mIsConnected.set(false);

                if (mSender != null) {
                    logger.debug("interrupting SenderThread");
                    mSender.interrupt();
                }
                if (mReceiver != null) {
                    logger.debug("interrupting ReceiverThread");
                    mReceiver.interrupt();
                }

                mSenderQueue.reset();

                if (parent.mSSLSocket != null) {
                    logger.debug("Closing socket...");
                    parent.mSSLSocket.close();
                    logger.debug("Done");
                    parent.mSSLSocket = null;
                }

                setIsAuthorized(false);

                parent.setSecurityObject(null);
                parent.mSender = null;
                parent.mReceiver = null;
            } catch (IOException e) {
                logger.error("Caught IOException");
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
     * @param agm
     *            AmmoGatewayMessage
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
        public SenderQueue(SSLChannel iChannel) {
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
                    logger.warn("channel not taking messages {}",
                            DisposalState.BUSY);
                    return DisposalState.BUSY;
                }
            } catch (InterruptedException e) {
                return DisposalState.BAD;
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
         * Condition wait for the some request to the channel.
         * 
         * An initial request cannot be processed until the channel has
         * authenticated.
         * 
         * This is where the authorized SenderThread blocks when taking a
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
                    mChannel.ackToHandler(msg.handler, DisposalState.PENDING);
                msg = mDistQueue.poll();
            }

            setIsAuthorized(false);
        }

        public int sizeOfDistQ() {
            return mDistQueue.size();
        }

        private BlockingQueue<AmmoGatewayMessage> mDistQueue;
        private LinkedList<AmmoGatewayMessage> mAuthQueue;
        private SSLChannel mChannel;
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    class SenderThread extends Thread {
        public SenderThread(ConnectorThread iParent, SSLChannel iChannel,
                SenderQueue iQueue, SSLSocket iSSLSocket) {
            super(new StringBuilder("Tcp-Sender-").append(Thread.activeCount())
                    .toString());
            mParent = iParent;
            mChannel = iChannel;
            mQueue = iQueue;
            mSSLSocket = iSSLSocket;
            // create the logger
            logger = LoggerFactory.getLogger("net.channel.tcp.sender."
                    + channelName);
        }

        /**
         * the message format is
         * 
         */
        @Override
        public void run() {
            logger.trace("Thread <{}>::run()", Thread.currentThread().getId());

            // Block on reading from the queue until we get a message to send.
            // Then send it on the socket channel. Upon getting a socket error,
            // notify our parent and go into an error state.

            while (mState != INetChannel.INTERRUPTED && !isInterrupted()) {
                AmmoGatewayMessage msg = null;
                try {
                    setSenderState(INetChannel.TAKING);
                    msg = mQueue.take(); // The main blocking call
                    logger.debug("Took a message from the send queue");
                } catch (InterruptedException ex) {
                    logger.debug("interrupted taking messages from send queue",
                            ex);
                    setSenderState(INetChannel.INTERRUPTED);
                    break;
                }

                try {
                    ByteBuffer buf = msg.serialize(endian,
                            AmmoGatewayMessage.VERSION_1_FULL, (byte) 0);
                    setSenderState(INetChannel.SENDING);
                    int bytesToSend = buf.remaining();
                    mDataOutputStream.write(buf.array(), 0, bytesToSend);
                    mBytesSent += bytesToSend;

                    logger.info("Send packet to Network, size ({})",
                            bytesToSend);

                    // set time of heartbeat sent
                    if (msg.isHeartbeat()) {
                        if (mTimeOfLastGoodSend.get() == 0)
                            mTimeOfLastGoodSend.set(System.currentTimeMillis());
                    }

                    // update status count
                    mMessagesSent.incrementAndGet();

                    // legitimately sent to gateway.
                    if (msg.handler != null)
                        mChannel.ackToHandler(msg.handler, DisposalState.SENT);
                } catch (Exception ex) {
                    logger.warn("sender threw exception", ex);
                    if (msg.handler != null)
                        mChannel.ackToHandler(msg.handler,
                                DisposalState.REJECTED);
                    setSenderState(INetChannel.INTERRUPTED);
                    mParent.socketOperationFailed();
                }
            }
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

        private int mState = INetChannel.TAKING;
        private ConnectorThread mParent;

        private SSLChannel mChannel;

        private SenderQueue mQueue;
        @SuppressWarnings("unused")
        private Logger logger = null;
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    class ReceiverThread extends Thread {
        public ReceiverThread(ConnectorThread iParent, SSLChannel iDestination,
                SSLSocket iSSLSocket) {
            super(new StringBuilder("Tcp-Receiver-").append(
                    Thread.activeCount()).toString());
            mParent = iParent;
            mDestination = iDestination;
            mSSLSocket = iSSLSocket;
            logger = LoggerFactory.getLogger("net.channel.tcp.receiver."
                    + channelName);

        }

        /**
         * Block on reading from the Socket until we get some data. Then examine
         * the buffer to see if we have any complete packets. If we have an
         * error, notify our parent and go into an error state.
         */
        @Override
        public void run() {
            logger.trace("Thread <{}>::run()", Thread.currentThread().getId());

            ByteBuffer bbuf = ByteBuffer.allocate(TCP_RECV_BUFF_SIZE);
            bbuf.order(endian); // mParent.endian
            byte[] bbufArray = bbuf.array();

            threadWhile: while (mState != INetChannel.INTERRUPTED
                    && !isInterrupted()) {
                try {
                    int position = bbuf.position();
                    int bytesRead = mDataInputStream.read(bbufArray, position,
                            bbuf.remaining());
                    if (isInterrupted())
                        break threadWhile; // exit thread
                    bbuf.position(position + bytesRead);

                    mDestination.resetTimeoutWatchdog();
                    logger.debug("Socket getting header read bytes={}",
                            bytesRead);
                    if (bytesRead == 0)
                        continue;

                    if (bytesRead < 0) {
                        logger.error("bytes read = {}, exiting", bytesRead);
                        setReceiverState(INetChannel.INTERRUPTED);
                        mParent.socketOperationFailed();
                        return;
                    }

                    mBytesRead += bytesRead;
                    setReceiverState(INetChannel.START);

                    // prepare to drain buffer
                    bbuf.flip();
                    for (AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage
                            .extractHeader(bbuf); agmb != null; agmb = AmmoGatewayMessage
                            .extractHeader(bbuf)) {
                        // if the message size is zero then there may be an
                        // error
                        if (agmb.size() < 1) {
                            logger.warn("discarding empty message error {}",
                                    agmb.error());
                            // TODO cause the reconnection behavior to change
                            // based on the error code
                            continue;
                        }
                        // if the message is TOO BIG then throw away the message
                        if (agmb.size() > MAX_MESSAGE_SIZE) {
                            logger.warn(
                                    "discarding message of size {} with checksum {}",
                                    agmb.size(),
                                    Long.toHexString(agmb.checksum()));
                            int size = agmb.size();
                            while (true) {
                                if (bbuf.remaining() < size) {
                                    int rem = bbuf.remaining();
                                    size -= rem;
                                    bbuf.clear();
                                    position = bbuf.position();
                                    bytesRead = mDataInputStream.read(
                                            bbufArray, position,
                                            bbuf.remaining());
                                    if (isInterrupted())
                                        break threadWhile; // exit thread
                                    bbuf.position(position + bytesRead);
                                    if (bytesRead < 0) {
                                        logger.error(
                                                "bytes read = {}, exiting",
                                                bytesRead);
                                        setReceiverState(INetChannel.INTERRUPTED);
                                        mParent.socketOperationFailed();
                                        return;
                                    }
                                    mBytesRead += bytesRead;
                                    mDestination.resetTimeoutWatchdog(); // a
                                                                         // successfull
                                                                         // read
                                                                         // should
                                                                         // reset
                                                                         // the
                                                                         // timer
                                    bbuf.flip();
                                    continue;
                                }
                                bbuf.position(size);
                                break;
                            }
                            continue;
                        }
                        // extract the payload
                        byte[] payload = new byte[agmb.size()];
                        int size = agmb.size();
                        int offset = 0;
                        while (true) {
                            if (bbuf.remaining() < size) {
                                int rem = bbuf.remaining();
                                bbuf.get(payload, offset, rem);
                                offset += rem;
                                size -= rem;
                                bbuf.clear();
                                position = bbuf.position();
                                bytesRead = mDataInputStream.read(bbufArray,
                                        position, bbuf.remaining());
                                if (isInterrupted())
                                    break threadWhile; // exit thread
                                bbuf.position(position + bytesRead);
                                if (bytesRead < 0) {
                                    logger.error("bytes read = {}, exiting",
                                            bytesRead);
                                    setReceiverState(INetChannel.INTERRUPTED);
                                    mParent.socketOperationFailed();
                                    return;
                                }
                                mDestination.resetTimeoutWatchdog(); // a
                                                                     // successfull
                                                                     // read
                                                                     // should
                                                                     // reset
                                                                     // the
                                                                     // timer
                                mBytesRead += bytesRead;
                                bbuf.flip();
                                continue;
                            }
                            bbuf.get(payload, offset, size);

                            AmmoGatewayMessage agm = agmb.payload(payload)
                                    .channel(this.mDestination).build();
                            logger.info(
                                    "Received a packet from gateway size({}) @{}, csum {}",
                                    new Object[] { agm.size, agm.buildTime,
                                            agm.payload_checksum });

                            setReceiverState(INetChannel.DELIVER);
                            mDestination.deliverMessage(agm);

                            // received a valid message, update status count
                            // ....
                            mMessagesReceived.incrementAndGet();
                            break;
                        }
                    }
                    // prepare to fill buffer
                    // if any bytes remain in the buffer they are a partial
                    // header
                    bbuf.compact();

                } catch (ClosedChannelException ex) {
                    logger.warn("receiver threw exception", ex);
                    setReceiverState(INetChannel.INTERRUPTED);
                    mParent.socketOperationFailed();
                } catch (Exception ex) {
                    logger.warn("receiver threw exception", ex);
                    setReceiverState(INetChannel.INTERRUPTED);
                    mParent.socketOperationFailed();
                }
            }
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

        private int mState = INetChannel.TAKING; // FIXME
        private ConnectorThread mParent;
        private SSLChannel mDestination;
        @SuppressWarnings("unused")
        private SSLSocket mSSLSocket;

        // private SSLSocket mSSLSocket;

        private Logger logger = null;
    }

    // ********** UTILITY METHODS ****************

    /**
     * A routine to get the local ip address TODO use this someplace
     * 
     * @return
     */
    public String getLocalIpAddress() {
        logger.trace("Thread <{}>::getLocalIpAddress", Thread.currentThread()
                .getId());
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            logger.error("get local IP address", ex);
        }
        return null;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public boolean isAuthenticatingChannel() {
        return true;
    }

    @Override
    public void init(Context context) {
        // TODO Auto-generated method stub

    }

    @Override
    public void toLog(String context) {
        PLogger.SET_PANTHR_GW.debug(" {}:{} timeout={} sec", new Object[] {
                gatewayHost, gatewayPort, flatLineTime });
    }
}

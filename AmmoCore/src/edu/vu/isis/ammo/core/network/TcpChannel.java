/**
 *
 */
package edu.vu.isis.ammo.core.network;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.lang.Long;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.network.NetworkService.MsgHeader;
import edu.vu.isis.ammo.core.pb.AmmoMessages;


/**
 * Two long running threads and one short.
 * The long threads are for sending and receiving messages.
 * The short thread is to connect the socket.
 * The sent messages are placed into a queue if the socket is connected.
 *
 * @author phreed
 *
 */
public class TcpChannel extends NetChannel {
    private static final Logger logger = LoggerFactory.getLogger(TcpChannel.class);

    private static final int BURP_TIME = 5 * 1000; // 5 seconds expressed in milliseconds
    private boolean isEnabled = true;

    private Socket socket = null;
    private ConnectorThread connectorThread;
    //private ReceiverThread receiverThread;
    //private SenderThread senderThread;

    // New threads
    private NewSenderThread mSender;
    private NewReceiverThread mReceiver;

    private int connectTimeout = 5 * 1000;     // this should come from network preferences
    private int socketTimeout = 5 * 1000; // milliseconds.

    private String gatewayHost = null;
    private int gatewayPort = -1;

    private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
    private final Object syncObj;

    private boolean shouldBeDisabled = false;
    private long flatLineTime;
    private IChannelManager driver;
    // Move me to a better place.
    BlockingQueue<GwMessage> mSenderQueue;
    SocketChannel mSocketChannel;

    private TcpChannel(IChannelManager driver) {
        super();
        logger.info("Thread <{}>TcpChannel::<constructor>", Thread.currentThread().getId());
        this.syncObj = this;

        this.driver = driver;
        this.connectorThread = new ConnectorThread(this);
        //this.senderThread = new SenderThread(this);
        //this.receiverThread = new ReceiverThread(this);

        this.flatLineTime = 20 * 1000; // 20 seconds in milliseconds

        mSenderQueue = new LinkedBlockingQueue<GwMessage>( 20 );
    }

    public static TcpChannel getInstance(IChannelManager driver) {
        logger.trace("Thread <{}>::getInstance", Thread.currentThread().getId());
        TcpChannel instance = new TcpChannel(driver);
        return instance;
    }

    public boolean isConnected() { return this.connectorThread.isConnected(); }
    /**
     * Was the status changed as a result of enabling the connection.
     * @return
     */
    public boolean isEnabled() { return this.isEnabled; }
    public boolean enable() {
        logger.trace("Thread <{}>::enable", Thread.currentThread().getId());
        synchronized (this.syncObj) {
            if (this.isEnabled == true)
                return false;
            this.isEnabled = true;

            // if (! this.connectorThread.isAlive()) this.connectorThread.start();
            // if (! this.senderThread.isAlive()) this.senderThread.start();
            // if (! this.receiverThread.isAlive()) this.receiverThread.start();

            logger.warn("::enable - Setting the state to STALE");
            this.shouldBeDisabled = false;
            this.connectorThread.state.set(NetChannel.STALE);
        }
        return true;
    }

    public boolean disable() {
        logger.trace("Thread <{}>::disable", Thread.currentThread().getId());
        synchronized (this.syncObj) {
            if (this.isEnabled == false)
                return false;
            this.isEnabled = false;
            logger.warn("::disable - Setting the state to DISABLED");
            this.shouldBeDisabled = true;
            this.connectorThread.state.set(NetChannel.DISABLED);

            //          this.connectorThread.stop();
            //          this.senderThread.stop();
            //          this.receiverThread.stop();
        }
        return true;
    }

    public boolean close() { return false; }

    public boolean setConnectTimeout(int value) {
        logger.trace("Thread <{}>::setConnectTimeout {}", Thread.currentThread().getId(), value);
        this.connectTimeout = value;
        return true;
    }
    public boolean setSocketTimeout(int value) {
        logger.trace("Thread <{}>::setSocketTimeout {}", Thread.currentThread().getId(), value);
        this.socketTimeout = value;
        this.reset();
        return true;
    }

    public void setFlatLineTime(long flatLineTime) {
        //this.flatLineTime = flatLineTime;  // currently broken
    }

    public boolean setHost(String host) {
        logger.info("Thread <{}>::setHost {}", Thread.currentThread().getId(), host);
        if ( gatewayHost != null && gatewayHost.equals(host) ) return false;
        this.gatewayHost = host;
        this.reset();
        return true;
    }
    public boolean setPort(int port) {
        logger.info("Thread <{}>::setPort {}", Thread.currentThread().getId(), port);
        if (gatewayPort == port) return false;
        this.gatewayPort = port;
        this.reset();
        return true;
    }

    public String toString() {
        return "socket: host["+this.gatewayHost+"] port["+this.gatewayPort+"]";
    }

    public void linkUp() {
        this.connectorThread.state.linkUp();
    }
    public void linkDown() {
        this.connectorThread.state.linkDown();
    }
    /**
     * forces a reconnection.
     */
    public void reset() {
        logger.trace("Thread <{}>::reset", Thread.currentThread().getId());
        logger.info("connector: {} sender: {} receiver: {}",
                    new String[] {
                        this.connectorThread.showState(),
                        "blah", //this.senderThread.showState(),
                        "blah" } ); //this.receiverThread.showState()});

        synchronized (this.syncObj) {
            if (! this.connectorThread.isAlive()) {
                this.connectorThread = new ConnectorThread(this);
                this.connectorThread.start();
            }
            //if (! this.senderThread.isAlive()) {
            //    this.senderThread = new SenderThread(this);
            //    this.senderThread.start();
            //}
            // if (! this.receiverThread.isAlive()) {
            //     this.receiverThread = new ReceiverThread(this);
            //     this.receiverThread.start();
            // }

            this.connectorThread.reset();
        }
    }
    private void statusChange()
    {
        int senderState = (mSender != null) ? mSender.getSenderState() : INetChannel.PENDING;
        int receiverState = (mReceiver != null) ? mReceiver.getReceiverState() : INetChannel.PENDING;

        driver.statusChange( this, this.connectorThread.state.value,
                             senderState,
                             receiverState );
    }


    // Called by ReceiverThread to send an incoming message to the
    // NetworkService.
    private boolean deliverMessage( byte[] message,
                                    long checksum )
    {
        logger.error( "In deliverMessage()" );
        resetTimeoutWatchdog();
        return driver.deliver( message, checksum );
    }

    /**
     *  Called by the SenderThread.
     *  This exists primarily to make a place to add instrumentation.
     *  Also, follows the delegation pattern.
     */
    private boolean ackToHandler( INetworkService.OnSendMessageHandler handler,
                                  boolean status )
    {
        return handler.ack( status );
    }


    // Called by the ConnectorThread.
    private boolean auth()
    {
        return driver.auth();
    }


    // Called by the ConnectorThread.
    private boolean isAnyLinkUp()
    {
        return driver.isAnyLinkUp();
    }


    private final AtomicLong mTimeOfLastGoodRead = new AtomicLong( 0 );

    // This should be called each time we successfully read data from the
    // socket.
    private void resetTimeoutWatchdog()
    {
        //logger.warn( "Resetting watchdog timer" );
        mTimeOfLastGoodRead.set( System.currentTimeMillis() );
    }


    // Returns true if we have gone more than flatLineTime without reading
    // any data from the socket.
    private boolean hasWatchdogExpired()
    {
        return (System.currentTimeMillis() - mTimeOfLastGoodRead.get()) > flatLineTime;
    }


    // Heartbeat-related members.
    private final long mHeartbeatInterval = 10 * 1000; // ms
    private final AtomicLong mNextHeartbeatTime = new AtomicLong( 0 );

    // Send a heartbeat packet to the gateway if enough time has elapsed.
    // Note: the way this currently works, the heartbeat can only be sent
    // in intervals that are multiples of the burp time.  This may change
    // later if I can eliminate some of the wait()s.
    private void sendHeartbeatIfNeeded()
    {
        //logger.warn( "In sendHeartbeatIfNeeded()." );

        long nowInMillis = System.currentTimeMillis();
        if ( nowInMillis > mNextHeartbeatTime.get() )
        {
            // Send the heartbeat here.
            logger.warn( "Sending a heartbeat. t={}", nowInMillis );

            // Create a heartbeat message and call the method to send it.
            AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
            mw.setType( AmmoMessages.MessageWrapper.MessageType.HEARTBEAT );

            AmmoMessages.Heartbeat.Builder message = AmmoMessages.Heartbeat.newBuilder();
            message.setSequenceNumber( nowInMillis ); // Just for testing

            mw.setHeartbeat( message );

            byte[] protocByteBuf = mw.build().toByteArray();
            MsgHeader msgHeader = MsgHeader.getInstance( protocByteBuf, true );

            sendRequest( msgHeader.size, msgHeader.checksum, protocByteBuf, null );

            mNextHeartbeatTime.set( nowInMillis + mHeartbeatInterval );
            //logger.warn( "Next heartbeat={}", mNextHeartbeatTime );
        }
    }

    /**
     * manages the connection.
     * enable or disable expresses the operator intent.
     * There is no reason to run the thread unless the channel is enabled.
     *
     * Any of the properties of the channel
     * @author phreed
     *
     */
    private class ConnectorThread extends Thread {
        private final Logger logger = LoggerFactory.getLogger(ConnectorThread.class);

        private final String DEFAULT_HOST = "10.0.2.2";
        private final int DEFAULT_PORT = 32896;
        private final int GATEWAY_RETRY_TIME = 20 * 1000; // 20 seconds

        private TcpChannel parent;
        private final State state;

        public void statusChange()
        {
            parent.statusChange();
        }

        public void socketOperationFailed()
        {
            // Is there a better way to deal with this?
            disconnect();
        }

        private ConnectorThread(TcpChannel parent) {
            logger.info("Thread <{}>ConnectorThread::<constructor>", Thread.currentThread().getId());
            this.parent = parent;
            this.state = new State();
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
                this.notifyAll();
            }
            public synchronized void linkDown() {
                this.reset();
            }
            public synchronized void set(int state) {
                logger.info("Thread <{}>State::set {}", Thread.currentThread().getId(), this.toString());
                switch (state) {
                case STALE:
                    this.reset();
                    return;
                }
                this.value = state;
                this.notifyAll();
            }
            public synchronized int get() { return this.value; }

            public synchronized boolean isConnected() {
                return this.value == CONNECTED;
            }


            /**
             * Previously this method would only set the state to stale
             * if the current state were CONNECTED.  It may be important
             * to return to STALE from other states as well.
             * For example during a failed link attempt.
             * Therefore if the attempt value matches then reset to STALE
             * This also causes a reset to reliably perform a notify.
             *
             * @param attempt value (an increasing integer)
             * @return
             */
            public synchronized boolean failure(long attempt) {
                if (attempt != this.attempt) return true;
                return this.reset();
            }
            public synchronized boolean reset() {
                attempt++;
                this.value = STALE;
                this.notifyAll();
                return true;
            }

            public String showState () {
                if (this.value == this.actual)
                    return parent.showState(this.value);
                else
                    return parent.showState(this.actual) + "->" + parent.showState(this.value);
            }
        }

        public boolean isConnected() {
            return this.state.isConnected();
        }
        public long getAttempt() {
            return this.state.attempt;
        }
        public String showState() { return this.state.showState( ); }

        /**
         * reset forces the channel closed if open.
         */
        public void reset() {
            this.state.failure(this.state.attempt);
        }

        public void failure(long attempt) {
            this.state.failure(attempt);
        }

        /**
         * A value machine based.
         * Most of the time this machine will be in a CONNECTED value.
         * In that CONNECTED value the machine wait for the connection value to
         * change or for an interrupt indicating that the thread is being shut down.
         *
         *  The value machine takes care of the following constraints:
         * We don't need to reconnect unless.
         * 1) the connection has been lost
         * 2) the connection has been marked stale
         * 3) the connection is enabled.
         * 4) an explicit reconnection was requested
         *
         * @return
         */
        @Override
        public void run() {
            try {
                logger.info("Thread <{}>ConnectorThread::run", Thread.currentThread().getId());
                MAINTAIN_CONNECTION: while (true) {
                    logger.info("connector state: {}",this.showState());

                    if(this.parent.shouldBeDisabled) this.state.set(NetChannel.DISABLED);
                    switch (this.state.get()) {
                    case NetChannel.DISABLED:
                        try {
                            synchronized (this.state) {
                                logger.info("this.state.get() = {}", this.state.get());

                                while (this.state.get() == NetChannel.DISABLED) // this is IMPORTANT don't remove it.
                                {
                                    this.parent.statusChange();
                                    this.state.wait(BURP_TIME);   // wait for a link interface
                                    logger.info("Looping in Disabled");

                                }
                            }
                        } catch (InterruptedException ex) {
                            logger.warn("connection intentionally disabled {}", this.state );
                            this.state.set(NetChannel.STALE);
                            break MAINTAIN_CONNECTION;
                        }
                        break;
                    case NetChannel.STALE:
                        disconnect();
                        this.state.set(NetChannel.LINK_WAIT);
                        break;

                    case NetChannel.LINK_WAIT:
                        this.parent.statusChange();
                        try {
                            synchronized (this.state) {
                                while (! this.isAnyLinkUp()) // this is IMPORTANT don't remove it.
                                    this.state.wait(BURP_TIME);   // wait for a link interface
                            }
                            this.state.set(NetChannel.DISCONNECTED);
                        } catch (InterruptedException ex) {
                            logger.warn("connection intentionally disabled {}", this.state );
                            this.state.set(NetChannel.STALE);
                            break MAINTAIN_CONNECTION;
                        }
                        this.parent.statusChange();
                        // or else wait for link to come up, triggered through broadcast receiver
                        break;

                    case NetChannel.DISCONNECTED:
                        this.parent.statusChange();
                        if ( !this.connect() ) {
                            this.state.set(NetChannel.CONNECTING);
                        } else {
                            this.state.set(NetChannel.CONNECTED);
                        }
                        break;

                    case NetChannel.CONNECTING: // keep trying
                        try {
                            this.parent.statusChange();
                            long attempt = this.getAttempt();
                            Thread.sleep(GATEWAY_RETRY_TIME);
                            if ( this.connect() ) {
                                this.state.set(NetChannel.CONNECTED);
                            } else {
                                this.failure(attempt);
                            }
                            this.parent.statusChange();
                        } catch (InterruptedException ex) {
                            logger.info("sleep interrupted - intentional disable, exiting thread ...");
                            this.reset();
                            break MAINTAIN_CONNECTION;
                        }
                        break;

                    case NetChannel.CONNECTED:
                        parent.auth();
                        {
                            this.parent.statusChange();
                            try {
                                synchronized (this.state) {
                                    while (this.isConnected()) // this is IMPORTANT don't remove it.
                                    {
                                        //parent.sendHeartbeatIfNeeded();
                                        this.state.wait(BURP_TIME);   // wait for somebody to change the connection status
                                        // if ( parent.hasWatchdogExpired() )
                                        // {
                                        //     //logger.error( "Watchdog timer expired!!" );
                                        //     failure( getAttempt() );
                                        // }
                                    }
                                }
                            } catch (InterruptedException ex) {
                                logger.warn("connection intentionally disabled {}", this.state );
                                this.state.set(NetChannel.STALE);
                                break MAINTAIN_CONNECTION;
                            }
                            this.parent.statusChange();
                        }
                        break;
                    default:
                        try {
                            long attempt = this.getAttempt();
                            this.parent.statusChange();
                            Thread.sleep(GATEWAY_RETRY_TIME);
                            this.failure(attempt);
                            this.parent.statusChange();
                        } catch (InterruptedException ex) {
                            logger.info("sleep interrupted - intentional disable, exiting thread ...");
                            this.reset();
                            break MAINTAIN_CONNECTION;
                        }
                    }
                }

            } catch (Exception ex) {
                this.state.set(NetChannel.EXCEPTION);
            }
            try {
                if (this.parent.socket == null) {
                    logger.error("channel closing without active socket");
                    return;
                }
                this.parent.socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
                logger.error("channel closing without proper socket");
            }
            logger.error("channel closing");
        }

        // private boolean disconnect() {
        //     logger.info("Thread <{}>ConnectorThread::disconnect", Thread.currentThread().getId());
        //     try {
        //         if (this.parent.socket == null) return true;
        //         this.parent.socket.close();
        //     } catch (IOException e) {
        //         return false;
        //     }
        //     return true;
        // }

        // private boolean isAnyLinkUp() {
        //     return this.parent.isAnyLinkUp();
        // }

        /**
         * connects to the gateway
         * @return
         */
        private boolean connect() {
            logger.info("Thread <{}>ConnectorThread::connect", Thread.currentThread().getId());

            String host = (parent.gatewayHost != null) ? parent.gatewayHost : DEFAULT_HOST;
            int port =  (parent.gatewayPort > 10) ? parent.gatewayPort : DEFAULT_PORT;
            InetAddress ipaddr = null;
            try {
                ipaddr = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                logger.warn("could not resolve host name");
                return false;
            }
            parent.socket = new Socket();
            InetSocketAddress sockAddr = new InetSocketAddress(ipaddr, port);
            try
            {
                parent.mSocketChannel = SocketChannel.open( sockAddr );
                boolean result = parent.mSocketChannel.finishConnect();
                logger.warn( "1 {}", result );
            }
            catch ( Exception e )
            {
                logger.warn( "2" );
                logger.warn("connection 2 to {}:{} failed : " + e.getLocalizedMessage(), ipaddr, port);
                return false;
            }
            // try {
            //     //parent.socket.connect(sockAddr, parent.connectTimeout);
            //     logger.warn( "3" );
            // } catch (IOException ex) {
            //     logger.warn( "4" );
            //     logger.warn("connection to {}:{} failed : " + ex.getLocalizedMessage(), ipaddr, port);
            //     parent.socket = null;
            //     return false;
            // }
                logger.warn( "5" );
            if (parent.socket == null) return false;
                logger.warn( "6" );
            // try {
            //     //parent.socket.setSoTimeout(parent.socketTimeout);
            //     logger.warn( "7" );
            // } catch (SocketException ex) {
            //     return false;
            // }

            logger.warn( "8" );
            logger.info("connection to {}:{} established ", ipaddr, port);
            // Create the sending and receiving threads here.
            if ( parent.mSender != null )
                logger.error( "Tried to create Sender when we already had one." );
            mSender = new NewSenderThread( this, parent, parent.mSenderQueue, parent.mSocketChannel );
            mSender.start();

            logger.warn( "9" );

            if ( parent.mReceiver != null )
                logger.error( "Tried to create Receiver when we already had one." );
            mReceiver = new NewReceiverThread( this, parent, parent.mSocketChannel );
            mReceiver.start();
            logger.warn( "10" );
            return true;
        }

        private boolean disconnect() {
            logger.info("Thread <{}>ConnectorThread::disconnect", Thread.currentThread().getId());
            try {
                //if (this.parent.socket == null)
                //    return true;

                //this.parent.socket.close();

                if ( mSender != null )
                    mSender.interrupt();
                if ( mReceiver != null )
                    mReceiver.interrupt();

                if ( parent.mSocketChannel != null )
                {
                    parent.mSocketChannel.close();
                    parent.mSocketChannel = null;
                }

                parent.mSender = null;
                parent.mReceiver = null;
            } catch (IOException e) {
                return false;
            }
            return true;
        }


        private boolean isAnyLinkUp() {
            return this.parent.driver.isAnyLinkUp();
        }
    }

    /**
     * do your best to send the message.
     * This makes use of the blocking "put" call.
     * A proper producer-consumer should use put or add and not offer.
     * "put" is blocking call.
     * If this were on the UI thread then offer would be used.
     *
     * @param size
     * @param checksum
     * @param message
     * @return
     */
    public boolean sendRequest(int size, CRC32 checksum, byte[] payload, INetworkService.OnSendMessageHandler handler)
    {
        //return this.senderThread.queueMsg(new GwMessage(size, checksum, payload, handler) );
        try
        {
            mSenderQueue.put( new GwMessage(size, checksum, payload, handler) );
        }
        catch ( InterruptedException e )
        {
            return false;
        }
        return true;
    }

    public class GwMessage {
        public final int size;
        public final CRC32 checksum;
        public final byte[] payload;
        public final INetworkService.OnSendMessageHandler handler;
        public GwMessage(int size, CRC32 checksum, byte[] payload, INetworkService.OnSendMessageHandler handler) {
            this.size = size;
            this.checksum = checksum;
            this.payload = payload;
            this.handler = handler;
        }
    }

    /**
     * A thread for receiving incoming messages on the socket.
     * The main method is run().
     *
     */
    // private static class SenderThread extends Thread {
    //     private static final Logger logger = LoggerFactory.getLogger(SenderThread.class);

    //     public String showState () {
    //         if (this.state == this.actual)
    //             return parent.showState(this.state);
    //         else
    //             return parent.showState(this.actual) + "->" + parent.showState(this.actual);
    //     }

    //     volatile private int state;
    //     volatile private int actual;

    //     private final TcpChannel parent;
    //     private ConnectorThread connector;

    //     private final BlockingQueue<GwMessage> queue;

    //     private SenderThread(TcpChannel parent) {
    //         logger.info("Thread <{}>SenderThread::<constructor>", Thread.currentThread().getId());
    //         this.parent = parent;
    //         this.connector = parent.connectorThread;
    //         this.queue = new LinkedBlockingQueue<GwMessage>(20);
    //     }

    //     /**
    //      * This makes use of the blocking "put" call.
    //      * A proper producer-consumer should use put or add and not offer.
    //      * "put" is blocking call.
    //      * If this were on the UI thread then offer would be used.
    //      *
    //      * @param msg
    //      * @return
    //      */
    //     public boolean queueMsg(GwMessage msg) {
    //         try {
    //             this.queue.put(msg);
    //         } catch (InterruptedException e) {
    //             return false;
    //         }
    //         return true;
    //     }

    //     private void failOutStream(OutputStream os, long attempt) {
    //         if (os == null) return;
    //         try {
    //             os.close();
    //         } catch (IOException ex) {
    //             logger.warn("close failed {}", ex.getLocalizedMessage());
    //         }
    //         this.connector.failure(attempt);
    //     }
    //     /**
    //      * Initiate a connection to the server and then wait for a response.
    //      * All responses are of the form:
    //      * size     : int32
    //      * checksum : int32
    //      * bytes[]  : <size>
    //      * This is done via a simple value machine.
    //      * If the checksum doesn't match the connection is dropped and restarted.
    //      *
    //      * Once the message has been read it is passed off to...
    //      */
    //     @Override
    //     public void run() {
    //         logger.info("Thread <{}>SenderThread::run", Thread.currentThread().getId());

    //         this.state = TAKING;

    //         DataOutputStream dos = null;
    //         try {
    //             // one integer for size & four bytes for checksum
    //             ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE/Byte.SIZE + 4);
    //             buf.order(parent.endian);
    //             GwMessage msg = null;
    //             long attempt = Long.MAX_VALUE;

    //             while (true) {
    //                 logger.info("sender state: {}",this.showState());
    //                 this.parent.statusChange();
    //                 this.actual = this.state;

    //                 switch (state) {
    //                 case WAIT_CONNECT:
    //                     synchronized (this.connector.state) {
    //                         while (! this.connector.isConnected()) {
    //                             try {
    //                                 logger.trace("Thread <{}>SenderThread::value.wait",
    //                                              Thread.currentThread().getId());

    //                                 this.connector.state.wait(BURP_TIME);
    //                             } catch (InterruptedException ex) {
    //                                 logger.warn("thread interupted {}",ex.getLocalizedMessage());
    //                                 return ; // looks like the thread is being shut down.
    //                             }
    //                         }
    //                         attempt = this.connector.getAttempt();
    //                     }
    //                     try {
    //                         // if connected then proceed
    //                         // keep the working socket so that if something goes wrong
    //                         // the socket can be checked to see if it has changed
    //                         // in the interim.
    //                         OutputStream os = this.parent.socket.getOutputStream();
    //                         dos = new DataOutputStream(os);
    //                     } catch (IOException ex) {
    //                         logger.warn("io exception acquiring socket for writing messages {}", ex.getLocalizedMessage());
    //                         if (msg.handler != null)
    //                             parent.ackToHandler( msg.handler, false );
    //                         this.failOutStream(dos, attempt);
    //                         break;
    //                     }
    //                     state = SENDING;
    //                     break;

    //                 case TAKING:
    //                     msg = queue.take(); // THE MAIN BLOCKING CALL
    //                     state = WAIT_CONNECT;
    //                     break;

    //                 case SENDING:
    //                     buf.rewind();
    //                     buf.putInt(msg.size);
    //                     long cvalue = msg.checksum.getValue();
    //                     byte[] checksum = new byte[] {
    //                         (byte)cvalue,
    //                         (byte)(cvalue >>> 8),
    //                         (byte)(cvalue >>> 16),
    //                         (byte)(cvalue >>> 24)
    //                     };
    //                     logger.debug("checksum [{}]", checksum);

    //                     buf.put(checksum, 0, 4);
    //                     try {
    //                         dos.write(buf.array());
    //                         dos.write(msg.payload);
    //                         dos.flush();
    //                     } catch (SocketException ex) {
    //                         logger.warn("exception writing to a socket {}", ex.getLocalizedMessage());
    //                         if (msg.handler != null)
    //                             parent.ackToHandler( msg.handler, false );
    //                         this.failOutStream(dos, attempt);
    //                         this.state = WAIT_CONNECT;
    //                         break;

    //                     } catch (IOException ex) {
    //                         logger.warn("io exception writing messages");
    //                         if (msg.handler != null)
    //                             parent.ackToHandler( msg.handler, false );
    //                         this.failOutStream(dos, attempt);
    //                         this.state = WAIT_CONNECT;
    //                         break;
    //                     }

    //                     // legitimately sent to gateway.
    //                     if (msg.handler != null)
    //                         parent.ackToHandler( msg.handler, true );

    //                     state = TAKING;
    //                     break;
    //                 }
    //             }
    //         } catch (InterruptedException ex) {
    //             logger.error("interupted writing messages {}", ex.getLocalizedMessage());
    //             this.actual = INTERRUPTED;
    //         } catch (Exception ex) {
    //             logger.error("exception writing messages ({}) {} ", ex, ex.getStackTrace());
    //             this.actual = EXCEPTION;
    //         }
    //         logger.error("sender thread exiting ...");
    //     }
    // }
    /**
     * A thread for receiving incoming messages on the socket.
     * The main method is run().
     *
     */



    // private static class ReceiverThread extends Thread {
    //     private static final Logger logger = LoggerFactory.getLogger(ReceiverThread.class);

    //     private TcpChannel parent = null;
    //     private ConnectorThread connector = null;

    //     // private TcpChannel.ConnectorThread;
    //     volatile private int state;
    //     volatile private int actual;

    //     public String showState () {
    //         if (this.state == this.actual)
    //             return parent.showState(this.state);
    //         else
    //             return parent.showState(this.actual) + "->" + parent.showState(this.actual);
    //     }

    //     private ReceiverThread(TcpChannel parent) {
    //         logger.info("Thread <{}>ReceiverThread::<constructor>", Thread.currentThread().getId());
    //         this.parent = parent;
    //         this.connector = parent.connectorThread;
    //     }

    //     @Override
    //     public void start() {
    //         super.start();
    //         logger.trace("Thread <{}>::start", Thread.currentThread().getId());
    //     }

    //     private void failInStream(InputStream is, long attempt) {
    //         if (is == null) return;
    //         try {
    //             is.close();
    //         } catch (IOException e) {
    //             logger.warn("close failed {}", e.getLocalizedMessage());
    //         }
    //         this.connector.failure(attempt);
    //     }
    //     /**
    //      * Initiate a connection to the server and then wait for a response.
    //      * All responses are of the form:
    //      * size     : int32
    //      * checksum : int32
    //      * bytes[]  : <size>
    //      * This is done via a simple value machine.
    //      * If the checksum doesn't match the connection is dropped and restarted.
    //      *
    //      * Once the message has been read it is passed off to...
    //      */
    //     @Override
    //     public void run() {
    //         logger.info("Thread <{}>ReceiverThread::run", Thread.currentThread().getId());
    //         //Looper.prepare();

    //         try {
    //             state = WAIT_CONNECT;

    //             int bytesToRead = 0; // indicates how many bytes should be read
    //             int bytesRead = 0;   // indicates how many bytes have been read
    //             long checksum = 0;

    //             byte[] message = null;
    //             byte[] byteToReadBuffer = new byte[Integer.SIZE/Byte.SIZE];
    //             byte[] checksumBuffer = new byte[Long.SIZE/Byte.SIZE];
    //             BufferedInputStream bis = null;
    //             long attempt = Long.MAX_VALUE;

    //             while (true) {
    //                 logger.info("receiver state: {}",this.showState());
    //                 this.parent.statusChange();

    //                 switch (state) {
    //                 case WAIT_RECONNECT: break;
    //                 case RESTART: break;
    //                 default:
    //                     logger.debug("state: {}",this.showState());
    //                 }

    //                 this.actual = WAIT_CONNECT;

    //                 switch (state) {
    //                 case WAIT_RECONNECT:
    //                 case WAIT_CONNECT:  // look for the size

    //                     synchronized (this.connector.state) {
    //                         while (! this.connector.isConnected() ) {
    //                             try {
    //                                 logger.trace("Thread <{}>ReceiverThread::value.wait",
    //                                              Thread.currentThread().getId());

    //                                 this.connector.state.wait(BURP_TIME);
    //                             } catch (InterruptedException ex) {
    //                                 logger.warn("thread interupted {}",ex.getLocalizedMessage());
    //                                 shutdown(bis); // looks like the thread is being shut down.
    //                                 return;
    //                             }
    //                         }
    //                         attempt = this.connector.getAttempt();
    //                     }

    //                     try {
    //                         InputStream insock = this.parent.socket.getInputStream();
    //                         bis = new BufferedInputStream(insock, 1024);
    //                     } catch (IOException ex) {
    //                         logger.error("could not open input stream on socket {}", ex.getLocalizedMessage());
    //                         failInStream(bis, attempt);
    //                         break;
    //                     }
    //                     if (bis == null) break;
    //                     this.state = START;
    //                     break;

    //                 case RESTART:
    //                 case START:
    //                     try {
    //                         int temp = bis.read(byteToReadBuffer);
    //                         if (temp < 0) {
    //                             logger.error("START: end of socket");
    //                             failInStream(bis, attempt);
    //                             this.state = WAIT_CONNECT;
    //                             break; // read error - end of connection
    //                         } else {
    //                             parent.resetTimeoutWatchdog();
    //                         }
    //                     } catch (SocketTimeoutException ex) {
    //                         // the following checks the heart-stamp
    //                         // TODO no pace-maker messages are sent, this could be added if needed.

    //                         this.state = RESTART;
    //                         break;
    //                     } catch (IOException ex) {
    //                         logger.error("START: read error {}", ex.getLocalizedMessage());
    //                         failInStream(bis, attempt);
    //                         this.state = WAIT_CONNECT;
    //                         break; // read error - set our value back to wait for connect
    //                     }
    //                     this.state = STARTED;
    //                     break;

    //                 case STARTED:  // look for the size
    //                 {
    //                     ByteBuffer bbuf = ByteBuffer.wrap(byteToReadBuffer);
    //                     bbuf.order(this.parent.endian);
    //                     bytesToRead = bbuf.getInt();

    //                     if (bytesToRead < 0) break; // bad read keep trying

    //                     if (bytesToRead > 4000000) {
    //                         logger.warn("message too large {} wrong size!!, we will be out of sync, disconnect ", bytesToRead);
    //                         failInStream(bis, attempt);
    //                         this.state = WAIT_CONNECT;
    //                         break;
    //                     }
    //                     this.state = SIZED;
    //                 }
    //                 break;
    //                 case SIZED: // look for the checksum
    //                 {
    //                     try {
    //                         int temp = bis.read(checksumBuffer, 0, 4);
    //                         if ( temp >= 0 )
    //                             parent.resetTimeoutWatchdog();
    //                     } catch (SocketTimeoutException ex) {
    //                         logger.trace("timeout on socket");
    //                         continue;
    //                     } catch (IOException e) {
    //                         logger.trace("SIZED: read error");
    //                         failInStream(bis, attempt);
    //                         this.state = WAIT_CONNECT;
    //                         break;
    //                     }
    //                     ByteBuffer bbuf = ByteBuffer.wrap(checksumBuffer);
    //                     bbuf.order(this.parent.endian);
    //                     checksum =  bbuf.getLong();

    //                     message = new byte[bytesToRead];

    //                     logger.info("checksum {} {}", checksumBuffer, checksum);
    //                     bytesRead = 0;
    //                     this.state = CHECKED;
    //                 }
    //                 break;
    //                 case CHECKED: // read the message
    //                     while (bytesRead < bytesToRead) {
    //                         try {
    //                             int temp = bis.read(message, bytesRead, bytesToRead - bytesRead);
    //                             if ( temp >= 0 )
    //                             {
    //                                 bytesRead += temp;
    //                                 parent.resetTimeoutWatchdog();
    //                             }
    //                         } catch (SocketTimeoutException ex) {
    //                             logger.trace("timeout on socket");
    //                             continue;
    //                         } catch (IOException ex) {
    //                             logger.trace("CHECKED: read error");
    //                             this.state = WAIT_CONNECT;
    //                             failInStream(bis, attempt);
    //                             break;
    //                         }
    //                     }
    //                     if (bytesRead < bytesToRead) {
    //                         failInStream(bis, attempt);
    //                         this.state = WAIT_CONNECT;
    //                         break;
    //                     }
    //                     this.state = DELIVER;
    //                     break;
    //                 case DELIVER: // deliver the message to the gateway
    //                     this.parent.deliverMessage( message, checksum );
    //                     message = null;
    //                     this.state = START;
    //                     break;
    //                 }
    //             }
    //         } catch (Exception ex) {
    //             logger.warn("interupted writing messages {}",ex.getLocalizedMessage());
    //             this.actual = EXCEPTION;
    //             ex.printStackTrace();
    //         }
    //         logger.error("reciever thread exiting ...");
    //     }

    //     private void shutdown(BufferedInputStream bis) {
    //         logger.warn("no longer listening, thread closing");
    //         try { bis.close(); } catch (IOException e) {}
    //         return;
    //     }
    // }

    // ********** UTILITY METHODS ****************

    /**
     * A routine to get the local ip address
     * TODO use this someplace
     *
     * @return
     */
    public String getLocalIpAddress() {
        logger.trace("Thread <{}>::getLocalIpAddress", Thread.currentThread().getId());
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            logger.error( ex.toString());
        }
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    class NewSenderThread extends Thread
    {
        public NewSenderThread( ConnectorThread iParent,
                                TcpChannel iChannel,
                                BlockingQueue<GwMessage> iQueue,
                                SocketChannel iSocketChannel )
        {
            mParent = iParent;
            mChannel = iChannel;
            mQueue = iQueue;
            mSocketChannel = iSocketChannel;
        }


        @Override
        public void run()
        {
            logger.info( "Thread <{}>::run()", Thread.currentThread().getId() );

            // Block on reading from the queue until we get a message to send.
            // Then send it on the socketchannel. Upon getting a socket error,
            // notify our parent and go into an error state.

            while ( mState != INetChannel.INTERRUPTED )
            {
                GwMessage msg = null;

                try
                {
                    setSenderState( INetChannel.TAKING );
                    msg = mQueue.take(); // The main blocking call
                    logger.error( "Took a message from the send queue" );
                }
                catch ( InterruptedException ex )
                {
                    logger.error( "interrupted taking messages from send queue: {}", ex.getLocalizedMessage() );
                    setSenderState( INetChannel.INTERRUPTED );
                    break;
                }

                int total_length = (Integer.SIZE/Byte.SIZE) + 4 + msg.payload.length;
                ByteBuffer buf = ByteBuffer.allocate( total_length );
                buf.order( ByteOrder.LITTLE_ENDIAN ); // mParent.endian

                buf.putInt( msg.size );
                logger.error( "   size={}", msg.size );
                long cvalue = msg.checksum.getValue();
                byte[] checksum = new byte[]
                    {
                        (byte) cvalue,
                        (byte) (cvalue >>> 8),
                        (byte) (cvalue >>> 16),
                        (byte) (cvalue >>> 24)
                    };
                logger.error( "   checksum={}", checksum );

                buf.put( checksum, 0, 4 );
                buf.put( msg.payload );
                logger.error( "   payload={}", msg.payload );
                buf.flip();

                try
                {
                    setSenderState( INetChannel.SENDING );
                    int bytesWritten = mSocketChannel.write( buf );
                    logger.error( "Wrote packet to SocketChannel" );

                    // legitimately sent to gateway.
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, true );
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                    logger.error("sender threw exception");
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, false );
                    setSenderState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                }
            }
        }

        private synchronized void setSenderState( int iState )
        {
            mState = iState;
            mParent.statusChange();
        }

        public synchronized int getSenderState() { return mState; }

        private int mState = INetChannel.TAKING;
        private ConnectorThread mParent;
        private TcpChannel mChannel;
        private BlockingQueue<GwMessage> mQueue;
        private SocketChannel mSocketChannel;
        private final Logger logger = LoggerFactory.getLogger( "network.tcp.sender" );
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    class NewReceiverThread extends Thread
    {
        public NewReceiverThread( ConnectorThread iParent,
                                  TcpChannel iDestination,
                                  SocketChannel iSocketChannel )
        {
            mParent = iParent;
            mDestination = iDestination;
            mSocketChannel = iSocketChannel;

            mBuffer = ByteBuffer.allocate( 400000 );
            mBuffer.order( ByteOrder.LITTLE_ENDIAN ); // mParent.endian
        }


        @Override
        public void run()
        {
            logger.info( "Thread <{}>::run()", Thread.currentThread().getId() );

            // Block on reading from the SocketChannel until we get some data.
            // Then examine the buffer to see if we have any complete packets.
            // If we have an error, notify our parent and go into an error state.

            while ( mState != INetChannel.INTERRUPTED )
            {
                try
                {
                    setReceiverState( INetChannel.START );
                    logger.error( "Reading from SocketChannel..." );
                    int bytesRead =  mSocketChannel.read( mBuffer );
                    logger.error( "SocketChannel read bytes={}", bytesRead );

                    // We loop here because a single read() may have  read in
                    // the data for several messages
                    if ( bufferContainsAMessage() )
                    {
                        logger.error( "flipping" );
                        mBuffer.flip();  // Switch to draining
                        while ( processAMessageIfAvailable() )
                            logger.error( "processed a message" );
                        logger.error( "compacting buffer" );
                        mBuffer.compact(); // Switches back to filling
                    }

                }
                catch ( InterruptedException e )
                {
                    logger.error( "interrupted reading messages {}", e.getLocalizedMessage() );
                    setReceiverState( INetChannel.INTERRUPTED );
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                    logger.error("receiver threw exception");
                    setReceiverState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                }
            }
        }


        // Flips the buffer, but returns it back to filling. We can't tell if
        // a buffer contains a complete message without entering draining mode,
        // so this method does that but returns mBuffer to its original state.
        private boolean bufferContainsAMessage() throws InterruptedException
        {
            int position = mBuffer.position();
            int limit = mBuffer.limit();

            mBuffer.flip();

            // If we haven't received enough bytes for an integer, then we
            // even have enough for the size.
            boolean containsMessage = false;
            if ( mBuffer.remaining() >= 4 )
            {
                // Get the size from the first four bytes.  If that value is less
                // than the number of bytes in the buffer, we have a complete
                // packet.
                int messageSize = mBuffer.getInt();

                // Take into account the checksum.
                containsMessage = (messageSize + 4) <= mBuffer.remaining();
            }

            // Return buffer back to normal before returning
            mBuffer.position( position );
            mBuffer.limit( limit );

            return containsMessage;
        }


        // Used in draining mode, and leaves it in draining mode with the
        // pointers unchanged.
        // private boolean bufferContainsAMessageDraining()
        // {
        //     if ( mBuffer.remaining() < 4 )
        //     {
        //         return false;
        //     }
        //     else
        //     {
        //         mBuffer.mark();
        //         int messageSize = mBuffer.getInt();
        //         mBuffer.reset();
        //         return (messageSize + 4) <= mBuffer.remaining();
        //     }
        // }


        // Only called while in draining mode. Returns true if we process a
        // message successfully and others may be available; returns false
        // if there are not further messages available.
        private boolean processAMessageIfAvailable() throws InterruptedException
        {
            if ( mBuffer.remaining() < 4 )
                return false;

            mBuffer.mark();
            int messageSize = mBuffer.getInt();

            // Take into account checksum.
            if ( (4 + messageSize) > mBuffer.remaining() )
            {
                mBuffer.reset();
                return false;
            }

            logger.error( "Receiving message:" );
            logger.info( "   message size={}", messageSize );

            // The four bytes of the checksum go into the least significant
            // four bytes of a long.
            byte[] checkBytes = new byte[ 4 ];
            mBuffer.get( checkBytes, 0, 4 );
            logger.error( "   checkBytes={}", checkBytes );
            long checksum = ( ((0xFFL & checkBytes[0]) << 0)
                            | ((0xFFL & checkBytes[1]) << 8)
                            | ((0xFFL & checkBytes[2]) << 16)
                            | ((0xFFL & checkBytes[3]) << 24) );
            logger.error( "   checksum={}", Long.toHexString(checksum) );

            byte[] message = new byte[messageSize];
            mBuffer.get( message, 0, messageSize );
            logger.error( "   message={}", message );

            setReceiverState( INetChannel.DELIVER );
            mDestination.deliverMessage( message, checksum );

            return true;
        }


        private void setReceiverState( int iState )
        {
            synchronized ( this )
            {
                mState = iState;
            }
            mParent.statusChange();
        }

        public synchronized int getReceiverState() { return mState; }

        private int mState = INetChannel.TAKING; // fixme
        private ConnectorThread mParent;
        private TcpChannel mDestination;
        private SocketChannel mSocketChannel;
        private ByteBuffer mBuffer;
        private final Logger logger = LoggerFactory.getLogger( "network.tcp.receiver" );
    }
}

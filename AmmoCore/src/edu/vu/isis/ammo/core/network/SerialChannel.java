/**
 *
 */
package edu.vu.isis.ammo.core.network;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.util.Enumeration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedList;
import java.util.zip.CRC32;
import java.lang.Long;

import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.pb.AmmoMessages;


public class SerialChannel extends NetChannel
{
    private static final Logger logger = LoggerFactory.getLogger( "net.serial" );

    private static final int BURP_TIME = 5 * 1000; // 5 seconds expressed in milliseconds

    /**
     * $ sysctl net.ipv4.tcp_rmem
     * or
     * $ cat /proc/sys/net/ipv4/tcp_rmem
     * 4096   87380   4194304
     * 0x1000 0x15554 0x400000
     *
     * The first value tells the kernel the minimum receive buffer for each TCP connection, and
     * this buffer is always allocated to a TCP socket, even under high pressure on the system.
     *
     * The second value specified tells the kernel the default receive buffer allocated for each TCP socket.
     * This value overrides the /proc/sys/net/core/rmem_default value used by other protocols.
     *
     * The third and last value specified in this variable specifies the maximum receive buffer
     * that can be allocated for a TCP socket.
     *
     */
    private static final int TCP_RECV_BUFF_SIZE = 0x15554; // the maximum receive buffer size
    private static final int MAX_MESSAGE_SIZE = 0x100000;  // arbitrary max size
    private boolean isEnabled = true;

    private Socket socket = null;
    private ConnectorThread connectorThread;

    // New threads
    private SenderThread mSender;
    private ReceiverThread mReceiver;

    @SuppressWarnings("unused")
    private int connectTimeout = 5 * 1000; // this should come from network preferences
    @SuppressWarnings("unused")
    private int socketTimeout = 5 * 1000; // milliseconds.

    private String gatewayHost = null;
    private int gatewayPort = -1;

    private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
    private final Object syncObj;

    private boolean shouldBeDisabled = false;
    private long flatLineTime;

    private SerialPort mPort;
    //private String mMulticastAddress = "228.1.2.3";
    //private InetAddress mMulticastGroup = null;
    //private int mMulticastPort = 1234;

    private SenderQueue mSenderQueue;

    private AtomicBoolean mIsAuthorized;

    // I made this public to support the hack to get authentication
    // working before Nilabja's code is ready.  Make it private again
    // once his stuff is in.
    public IChannelManager mChannelManager;
    private ISecurityObject mSecurityObject;


    private SerialChannel( IChannelManager iChannelManager ) {
        super();

        logger.info("Thread <{}>SerialChannel::<constructor>", Thread.currentThread().getId());
        this.syncObj = this;

        mIsAuthorized = new AtomicBoolean( false );

        mChannelManager = iChannelManager;

        this.flatLineTime = 20 * 1000; // 20 seconds in milliseconds

        mSenderQueue = new SenderQueue( this );

        this.connectorThread = new ConnectorThread(this);
        // The thread is start()ed the first time the network disables and
        // reenables it.
    }


    public static SerialChannel getInstance( IChannelManager iChannelManager )
    {
        logger.trace("Thread <{}> SerialChannel::getInstance()",
                     Thread.currentThread().getId());
        SerialChannel instance = new SerialChannel( iChannelManager );
        return instance;
    }


    public boolean isConnected() { return this.connectorThread.isConnected(); }

    /**
     * Was the status changed as a result of enabling the connection.
     * @return
     */
    public boolean isEnabled() { return this.isEnabled; }


    public void enable() {
        logger.trace("Thread <{}>::enable", Thread.currentThread().getId());
        synchronized (this.syncObj) {
            if ( !this.isEnabled ) {
                this.isEnabled = true;

                // if (! this.connectorThread.isAlive()) this.connectorThread.start();

                logger.warn("::enable - Setting the state to STALE");
                this.shouldBeDisabled = false;
                this.connectorThread.state.set(NetChannel.STALE);
            }
        }
    }


    public void disable() {
        logger.trace("Thread <{}>::disable", Thread.currentThread().getId());
        synchronized (this.syncObj) {
            if ( this.isEnabled ) {
                this.isEnabled = false;
                logger.warn("::disable - Setting the state to DISABLED");
                this.shouldBeDisabled = true;
                this.connectorThread.state.set(NetChannel.DISABLED);

                //          this.connectorThread.stop();
            }
        }
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
                    new Object[] {
                        this.connectorThread.showState(),
                        (this.mSender == null ? "none" : this.mSender.getSenderState()),
                        (this.mReceiver == null ? "none" : this.mReceiver.getReceiverState())});

        synchronized (this.syncObj) {
            if (! this.connectorThread.isAlive()) {
                this.connectorThread = new ConnectorThread(this);
                this.connectorThread.start();
            }

            this.connectorThread.reset();
        }
    }
    private void statusChange()
    {
        int senderState = (mSender != null) ? mSender.getSenderState() : INetChannel.PENDING;
        int receiverState = (mReceiver != null) ? mReceiver.getReceiverState() : INetChannel.PENDING;

        mChannelManager.statusChange( this,
                                      this.connectorThread.state.value,
                                      senderState,
                                      receiverState );
    }


    private synchronized void setSecurityObject( ISecurityObject iSecurityObject )
    {
        mSecurityObject = iSecurityObject;
    }


    private synchronized ISecurityObject getSecurityObject()
    {
        return mSecurityObject;
    }


    private void setIsAuthorized( boolean iValue )
    {
        logger.info( "In setIsAuthorized(). value={}", iValue );

        mIsAuthorized.set( iValue );
    }


    public boolean getIsAuthorized()
    {
        return mIsAuthorized.get();
    }


    public void authorizationSucceeded( AmmoGatewayMessage agm )
    {
        setIsAuthorized( true );
        mSenderQueue.markAsAuthorized();

        // Tell the NetworkService that we're authorized and have it
        // notify the apps.
        mChannelManager.authorizationSucceeded( agm );
    }


    public void authorizationFailed()
    {
        // Disconnect the channel.
        reset();
    }


    // Called by ReceiverThread to send an incoming message to the
    // appropriate destination.
    private boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.error( "In deliverMessage()" );

        boolean result;
        if ( mIsAuthorized.get() )
        {
            logger.info( " delivering to channel manager" );
            result = mChannelManager.deliver( agm );
        }
        else
        {
            logger.info( " delivering to security object" );
            result = getSecurityObject().deliverMessage( agm );
        }
        return result;
    }

    /**
     *  Called by the SenderThread.
     *  This exists primarily to make a place to add instrumentation.
     *  Also, follows the delegation pattern.
     */
    private boolean ackToHandler( INetworkService.OnSendMessageHandler handler,
                                  boolean status )
    {
        return handler.ack( SerialChannel.class, status );
    }

    // Called by the ConnectorThread.
    private boolean isAnyLinkUp()
    {
        return mChannelManager.isAnyLinkUp();
    }


    private final AtomicLong mTimeOfLastGoodRead = new AtomicLong( 0 );


    // Heartbeat-related members.
    private final long mHeartbeatInterval = 10 * 1000; // ms
    private final AtomicLong mNextHeartbeatTime = new AtomicLong( 0 );

    private String mDevice;
    private int mBaudRate;

    private AtomicInteger mSlotNumber = new AtomicInteger();
    private AtomicInteger mRadiosInGroup = new AtomicInteger();

    private AtomicInteger mSlotDuration = new AtomicInteger();
    private AtomicInteger mTransmitDuration = new AtomicInteger();

    private AtomicBoolean mSenderEnabled = new AtomicBoolean();
    private AtomicBoolean mReceiverEnabled = new AtomicBoolean();


    // Send a heartbeat packet to the gateway if enough time has elapsed.
    // Note: the way this currently works, the heartbeat can only be sent
    // in intervals that are multiples of the burp time.  This may change
    // later if I can eliminate some of the wait()s.
    private void sendHeartbeatIfNeeded()
    {
        //logger.warn( "In sendHeartbeatIfNeeded()." );

        long nowInMillis = System.currentTimeMillis();
        if ( nowInMillis < mNextHeartbeatTime.get() ) return;

        // Send the heartbeat here.
        logger.warn( "Sending a heartbeat. t={}", nowInMillis );

        // Create a heartbeat message and call the method to send it.
        AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType( AmmoMessages.MessageWrapper.MessageType.HEARTBEAT );
        mw.setMessagePriority(AmmoGatewayMessage.PriorityLevel.FLASH.v);

        AmmoMessages.Heartbeat.Builder message = AmmoMessages.Heartbeat.newBuilder();
        message.setSequenceNumber( nowInMillis ); // Just for testing

        mw.setHeartbeat( message );

        AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(mw, null);
        agmb.isGateway(true);
        sendRequest( agmb.build() );

        mNextHeartbeatTime.set( nowInMillis + mHeartbeatInterval );
        //logger.warn( "Next heartbeat={}", mNextHeartbeatTime );
    }

    /**
     * manages the connection.
     * enable or disable expresses the operator intent.
     * There is no reason to run the thread unless the channel is enabled.
     *
     * Any of the properties of the channel
     *
     */
    private class ConnectorThread extends Thread {
        private final Logger logger = LoggerFactory.getLogger( "net.serial.connector" );

        private final String DEFAULT_HOST = "10.0.2.2";
        private final int DEFAULT_PORT = 32896;
        private final int GATEWAY_RETRY_TIME = 20 * 1000; // 20 seconds

        private SerialChannel parent;
        private final State state;

        private AtomicBoolean mIsConnected;

        public void statusChange()
        {
            parent.statusChange();
        }


        // Called by the sender and receiver when they have an exception on the
        // socket.  We only want to call reset() once, so we use an
        // AtomicBoolean to keep track of whether we need to call it.
        public void socketOperationFailed()
        {
            if ( mIsConnected.compareAndSet( true, false ))
                state.reset();
        }


        private ConnectorThread( SerialChannel parent ) {
            logger.info("Thread <{}>ConnectorThread::<constructor>", Thread.currentThread().getId());
            this.parent = parent;
            this.state = new State();
            mIsConnected = new AtomicBoolean( false );
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
                logger.info("Thread <{}>State::set", Thread.currentThread().getId());
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
                                while (! parent.isAnyLinkUp()) // this is IMPORTANT don't remove it.
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
                    {
                        this.parent.statusChange();
                        try {
                            synchronized (this.state) {
                                while (this.isConnected()) // this is IMPORTANT don't remove it.
                                {
                                    //parent.sendHeartbeatIfNeeded();

                                    // wait for somebody to change the connection status
                                    this.state.wait(BURP_TIME);
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
                ex.printStackTrace();
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


        private boolean connect()
        {
            logger.info( "Thread <{}>ConnectorThread::connect",
                         Thread.currentThread().getId() );

            // try
            // {
            //     parent.mMulticastGroup = InetAddress.getByName( parent.mMulticastAddress );
            // }
            // catch ( UnknownHostException e )
            // {
            //     logger.warn( "could not resolve host name" );
            //     return false;
            // }

            // Create the SerialPort.
            if ( parent.mPort != null )
                logger.error( "Tried to create mPort when we already had one." );
            try
            {
                parent.mPort = new SerialPort( new File(mDevice), mBaudRate );
            }
            catch ( Exception e )
            {
                logger.warn( "connection to serial port failed" );
                parent.mPort = null;
                return false;
            }

            logger.info( "connection to serial port established " );
            mIsConnected.set( true );

            // Create the security object.  This must be done before
            // the ReceiverThread is created in case we receive a
            // message before the SecurityObject is ready to have it
            // delivered.
            if ( parent.getSecurityObject() != null )
                logger.error( "Tried to create SecurityObject when we already had one." );
            parent.setSecurityObject( new SerialSecurityObject( parent ));

            // Create the sending thread.
            if ( parent.mSender != null )
                logger.error( "Tried to create Sender when we already had one." );
            parent.mSender = new SenderThread( this,
                                               parent,
                                               parent.mSenderQueue,
                                               parent.mPort );
            setIsAuthorized( true );
            parent.mSender.start();

            // Create the receiving thread.
            if ( parent.mReceiver != null )
                logger.error( "Tried to create Receiver when we already had one." );
            parent.mReceiver = new ReceiverThread( this, parent, parent.mPort );
            parent.mReceiver.start();

            // FIXME: don't pass in the result of buildAuthenticationRequest(). This is
            // just a temporary hack.
            //parent.getSecurityObject().authorize( mChannelManager.buildAuthenticationRequest());
            mSenderQueue.markAsAuthorized();

            return true;
        }


        private boolean disconnect()
        {
            logger.info( "Thread <{}>ConnectorThread::disconnect",
                         Thread.currentThread().getId() );
            try
            {
                mIsConnected.set( false );

                if ( mSender != null )
                    mSender.interrupt();
                if ( mReceiver != null )
                    mReceiver.interrupt();

                mSenderQueue.reset();

                if ( parent.mPort != null )
                {
                    logger.debug( "Closing SerialPort." );
                    parent.mPort.close();
                    logger.debug( "Done" );

                    parent.mPort = null;
                }

                setIsAuthorized( false );

                parent.setSecurityObject( null );
                parent.mSender = null;
                parent.mReceiver = null;
            }
            catch ( Exception e )
            {
                logger.error( "Caught Exception" );
                // Do this here, too, since if we exited early because
                // of an exception, we want to make sure that we're in
                // an unauthorized state.
                setIsAuthorized( false );
                return false;
            }
            logger.debug( "returning after successful disconnect()." );
            return true;
        }
    }


    /**
     * do your best to send the message.
     * This makes use of the blocking "put" call.
     * A proper producer-consumer should use put or add and not offer.
     * "put" is blocking call.
     * If this were on the UI thread then offer would be used.
     *
     * @param agm AmmoGatewayMessage
     * @return
     */
    public boolean sendRequest( AmmoGatewayMessage agm )
    {
        return mSenderQueue.putFromDistributor( agm );
    }

    public void putFromSecurityObject( AmmoGatewayMessage agm )
    {
        mSenderQueue.putFromSecurityObject( agm );
    }

    public void finishedPuttingFromSecurityObject()
    {
        mSenderQueue.finishedPuttingFromSecurityObject();
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    class SenderQueue
    {
        public SenderQueue( SerialChannel iChannel )
        {
            mChannel = iChannel;

            setIsAuthorized( false );
            mDistQueue = new LinkedBlockingQueue<AmmoGatewayMessage>( 20 );
            mAuthQueue = new LinkedList<AmmoGatewayMessage>();
        }


        // In the new design, aren't we supposed to let the
        // NetworkService know if the outgoing queue is full or not?
        public boolean putFromDistributor( AmmoGatewayMessage iMessage )
        {
            try
            {
                logger.info( "putFromDistributor()" );
                mDistQueue.put( iMessage );
            }
            catch ( InterruptedException e )
            {
                return false;
            }
            return true;
        }


        public synchronized void putFromSecurityObject( AmmoGatewayMessage iMessage )
        {
            logger.info( "putFromSecurityObject()" );
            mAuthQueue.offer( iMessage );
        }


        public synchronized void finishedPuttingFromSecurityObject()
        {
            logger.info( "finishedPuttingFromSecurityObject()" );
            notifyAll();
        }


        // This is called when the SecurityObject has successfully
        // authorized the channel.
        public synchronized void markAsAuthorized()
        {
            logger.info( "Marking channel as authorized" );
            notifyAll();
        }


        public synchronized boolean messageIsAvailable()
        {
            return mDistQueue.peek() != null;
        }


        public synchronized AmmoGatewayMessage take() throws InterruptedException
        {
            logger.info( "taking from SenderQueue" );
            if ( mChannel.getIsAuthorized() )
            {
                // This is where the authorized SenderThread blocks.
                return mDistQueue.take();
            }
            else
            {
                if ( mAuthQueue.size() > 0 )
                {
                    // return the first item in mAuthqueue and remove
                    // it from the queue.
                    return mAuthQueue.remove();
                }
                else
                {
                    logger.info( "wait()ing in SenderQueue" );
                    wait(); // This is where the SenderThread blocks.

                    if ( mChannel.getIsAuthorized() )
                    {
                        return mDistQueue.take();
                    }
                    else
                    {
                        // We are not yet authorized, so return the
                        // first item in mAuthqueue and remove
                        // it from the queue.
                        return mAuthQueue.remove();
                    }
                }
            }
        }


        // Somehow synchronize this here.
        public synchronized void reset()
        {
            logger.info( "reset()ing the SenderQueue" );
            // Tell the distributor that we couldn't send these
            // packets.
            AmmoGatewayMessage msg = mDistQueue.poll();
            while ( msg != null )
            {
                if ( msg.handler != null )
                    mChannel.ackToHandler( msg.handler, false );
                msg = mDistQueue.poll();
            }

            setIsAuthorized( false );
        }


        private BlockingQueue<AmmoGatewayMessage> mDistQueue;
        private LinkedList<AmmoGatewayMessage> mAuthQueue;
        private SerialChannel mChannel;
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    class SenderThread extends Thread
    {
        public SenderThread( ConnectorThread iParent,
                             SerialChannel iChannel,
                             SenderQueue iQueue,
                             SerialPort iPort )
        {
            mParent = iParent;
            mChannel = iChannel;
            mQueue = iQueue;
            mPort = iPort;
        }


        @Override
        public void run()
        {
            logger.info( "SenderThread <{}>::run()", Thread.currentThread().getId() );

            // Sleep until our slot in the round.  If, upon waking, we find that
            // we are in the right slot, check to see if a packet is available
            // to be sent and, if so, send it. Upon getting a serial port error,
            // notify our parent and go into an error state.

            while ( mState.get() != INetChannel.INTERRUPTED ) {
                AmmoGatewayMessage msg = null;
                try {
                    setSenderState( INetChannel.TAKING );

                    // Try to sleep until our next take time.
                    long currentTime = System.currentTimeMillis();

                    int slotDuration = mSlotDuration.get();
                    int offset = mSlotNumber.get() * slotDuration;
                    int cycleDuration = slotDuration * mRadiosInGroup.get();

                    long thisCycleStartTime = (long) (currentTime / cycleDuration) * cycleDuration;
                    long thisCycleTakeTime = thisCycleStartTime + offset;

                    long goalTakeTime;
                    if ( thisCycleTakeTime > currentTime ) {
                        // We haven't yet reached our take time for this cycle,
                        // so that's our goal.
                        goalTakeTime = thisCycleTakeTime;
                    } else {
                        // We've already missed our turn this cycle, so add
                        // cycleDuration and wait until the next round.
                        goalTakeTime = thisCycleTakeTime + cycleDuration;
                    }
                    Thread.sleep( goalTakeTime - currentTime );


                    // Once we wake up, we need to see if we are in our slot.
                    // Sometimes the sleep() will not wake up on time, and we
                    // have missed our slot.  If so, don't do a take() and just
                    // wait until our next slot.
                    currentTime = System.currentTimeMillis();
                    logger.debug( "Woke up: slotNumber={}, (time, mu-s)={}, jitter={}",
                                 new Object[] {
                                      mSlotNumber.get(),
                                      currentTime,
                                      currentTime - goalTakeTime } );
                    if ( currentTime - goalTakeTime > WINDOW_DURATION ) {
                        logger.debug( "Missed slot: attempted={}, current={}, jitter={}",
                                      new Object[] {
                                          goalTakeTime,
                                          currentTime,
                                          currentTime - goalTakeTime } );
                        continue;
                    }

                    // At this point, we've woken up near the start of our window
                    // and should send a message if one is available.
                    if ( !mQueue.messageIsAvailable() ) {
                        continue;
                    }
                    msg = mQueue.take(); // Will not block

                    logger.debug( "Took a message from the send queue" );
                } catch ( InterruptedException ex ) {
                    logger.debug( "interrupted taking messages from send queue: {}",
                                  ex.getLocalizedMessage() );
                    setSenderState( INetChannel.INTERRUPTED );
                    break;
                }

                try {
                    ByteBuffer buf = msg.serialize( endian,
                                                    AmmoGatewayMessage.VERSION_1_TERSE,
                                                    (byte) mSlotNumber.get() );

                    setSenderState( INetChannel.SENDING );

                    if ( mSenderEnabled.get() ) {
                        OutputStream outputStream = mPort.getOutputStream();
                        outputStream.write( buf.array() );
                        outputStream.flush();

                        logger.info( "sent message size={}, checksum={}, data:{}",
                                     new Object[] {
                                         msg.size,
                                         Long.toHexString(msg.payload_checksum),
                                         msg.payload } );
                    }

                    // legitimately sent to gateway.
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, true );
                } catch ( Exception e ) {
                    logger.warn("sender threw exception {}", e.getStackTrace() );
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, false );
                    setSenderState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                }
            }
        }


        private void setSenderState( int state )
        {
            mState.set( state );
            mParent.statusChange();
        }

        public int getSenderState() { return mState.get(); }

        // If we miss our window's start time by more than this amount, we
        // give up until our turn in the next cycle.
        private static final int WINDOW_DURATION = 25;

        private AtomicInteger mState = new AtomicInteger( INetChannel.TAKING );
        private ConnectorThread mParent;
        private SerialChannel mChannel;
        private SenderQueue mQueue;
        private SerialPort mPort;
        private final Logger logger = LoggerFactory.getLogger( "net.serial.sender" );
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    class ReceiverThread extends Thread
    {
        public ReceiverThread( ConnectorThread iParent,
                               SerialChannel iDestination,
                               SerialPort iPort )
        {
            mParent = iParent;
            mDestination = iDestination;
            mPort = iPort;
            mInputStream = mPort.getInputStream();
        }


        @Override
        public void run()
        {
            logger.info( "Thread <{}>::run()", Thread.currentThread().getId() );

            // Block on reading from the SerialPort until we get some data.
            // If we get an error, notify our parent and go into an error state.

            // NOTE: We found that our reads were less reliable when reading more than
            // one byte at a time using the standard stream and ByteBuffer patterns.
            // Reading one byte at a time in the code below is intentional.

            try {
                final byte first = (byte) 0xef;
                final byte second = (byte) 0xbe;
                final byte third = (byte) 0xed;

                byte[] buf_header = new byte[ 32 ];// AmmoGatewayMessage.HEADER_DATA_LENGTH_TERSE ];
                ByteBuffer header = ByteBuffer.wrap( buf_header );
                header.order( endian );

                int state = 0;
                byte c = 0;
                AmmoGatewayMessage.Builder agmb = null;

                while ( mState.get() != INetChannel.INTERRUPTED ) {
                    setReceiverState( INetChannel.START );

                    switch ( state ) {
                    case 0:
                        logger.debug( "Waiting for magic sequence." );
                        c = readAByte();
                        if ( c == first )
                            state = c;
                        break;

                    case first:
                        c = readAByte();
                        if ( c == second || c == first )
                            state = c;
                        else
                            state = 0;
                        break;

                    case second:
                        c = readAByte();
                        if ( c == third )
                            state = 1;
                        else if ( c == 0xef )
                            state = c;
                        else
                            state = 0;
                        break;

                    case 1:
                        {
                            long currentTime = System.currentTimeMillis();
                            int slotDuration = mSlotDuration.get();
                            int cycleDuration = slotDuration * mRadiosInGroup.get();
                            long thisCycleStartTime = (long) (currentTime / cycleDuration) * cycleDuration;

                            long currentSlot = (currentTime - thisCycleStartTime) / slotDuration;
                            logger.debug( "Read magic sequence in slot {} at {}",
                                          currentSlot,
                                          currentTime );

                            // Set these in buf_header, since extractHeader() expects them.
                            buf_header[0] = first;
                            buf_header[1] = second;
                            buf_header[2] = third;

                            // For some unknown reason, this was writing past the end of the
                            // array when length=16. It may have been ant not recompiling things
                            // properly.  Look into it when I have time.
                            for ( int i = 0; i < 13; ++i ) {
                                c = readAByte();
                                buf_header[i+3] = c;
                            }
                            logger.debug( " Received terse header, reading payload " );

                            agmb = AmmoGatewayMessage.extractHeader( header );
                            if ( agmb == null ) {
                                logger.error( "Deserialization failure." );
                                state = 0;
                            } else {
                                state = 2;
                            }
                        }
                        break;

                    case 2:
                        {
                            int payload_size = agmb.size();
                            byte[] buf_payload = new byte[ payload_size ];

                            for ( int i = 0; i < payload_size; ++i ) {
                                c = readAByte();
                                buf_payload[i] = c;
                            }

                            AmmoGatewayMessage agm = agmb.payload( buf_payload ).build();

                            long currentTime = System.currentTimeMillis();
                            int slotDuration = mSlotDuration.get();
                            int cycleDuration = slotDuration * mRadiosInGroup.get();

                            long thisCycleStartTime = (long) (currentTime / cycleDuration) * cycleDuration;
                            long currentSlot = (currentTime - thisCycleStartTime) / slotDuration;

                            logger.debug( "Finished reading payload in slot {} at {}",
                                          currentSlot,
                                          currentTime );
                            logger.info( "received message size={}, checksum={}, data:{}",
                                         new Object[] {
                                             agm.size,
                                             Long.toHexString(agm.payload_checksum),
                                             agm.payload } );

                            if ( mReceiverEnabled.get() ) {
                                setReceiverState( INetChannel.DELIVER );
                                mDestination.deliverMessage( agm );
                            } else {
                                logger.info( "Receiving disabled, discarding message." );
                            }

                            header.clear();
                            setReceiverState( INetChannel.START );
                            state = 0;
                        }
                        break;

                    default:
                        logger.debug( "Unknown value for state variable" );
                    }
                }
            } catch ( IOException ex ) {
                logger.warn( "receiver threw an IOException {}", ex.getStackTrace() );
                setReceiverState( INetChannel.INTERRUPTED );
                mParent.socketOperationFailed();
            } catch ( Exception ex ) {
                logger.warn( "receiver threw an exception {}", ex.getStackTrace() );
                setReceiverState( INetChannel.INTERRUPTED );
                mParent.socketOperationFailed();
            }
        }


        private byte readAByte() throws IOException
        {
            //logger.debug( "Calling receive() on the SerialPort." );
            int val = mInputStream.read();
            if ( val == -1 ) {
                logger.debug( "The serial port returned -1 from read()." );
                throw new IOException();
            }
            //logger.debug( "{}", Integer.toHexString(val) );
            return (byte) val;
        }


        private void setReceiverState( int state )
        {
            mState.set( state );
            mParent.statusChange();
        }

        public int getReceiverState() { return mState.get(); }

        private AtomicInteger mState = new AtomicInteger( INetChannel.TAKING ); // FIXME: better states
        private ConnectorThread mParent;
        private SerialChannel mDestination;
        private SerialPort mPort;
        private InputStream mInputStream;
        private final Logger logger
            = LoggerFactory.getLogger( "net.serial.receiver" );
    }



    // ********** UTILITY METHODS ****************

    // The following methods will require a disconnect and reconnect,
    // because the variables can't changed while running.  They probably aren't
    // that important in the short-term.
    //
    // NOTE: The following two function are probably not working atm.  We need
    // to make sure that they're synchronized, and deal with disconnecting the
    // channel (to force a reconnect).  This functionality isn't presently
    // needed, but fix this at some point.
    public void setDevice( String device )
    {
        logger.error( "Device set to {}", device );
        mDevice = device;
    }

    public void setBaudRate( int baudRate )
    {
        logger.error( "Baud rate set to {}", baudRate );
        mBaudRate = baudRate;
    }


    // The following methods can be changed while connected.  Modify the
    // threads to get the values from synchronized members.
    //
    public void setSlotNumber( int slotNumber )
    {
        logger.error( "Slot set to {}", slotNumber );
        mSlotNumber.set( slotNumber );
    }

    public void setRadiosInGroup( int radiosInGroup )
    {
        logger.error( "Radios in group set to {}", radiosInGroup );
        mRadiosInGroup.set( radiosInGroup );
    }


    public void setSlotDuration( int slotDuration )
    {
        logger.error( "Slot duration set to {}", slotDuration );
        mSlotDuration.set( slotDuration );
    }

    public void setTransmitDuration( int transmitDuration )
    {
        logger.error( "Transmit duration set to {}", transmitDuration );
        mTransmitDuration.set( transmitDuration );
    }


    public void setSenderEnabled( boolean enabled )
    {
        logger.error( "Sender enabled set to {}", enabled );
        mSenderEnabled.set( enabled );
    }

    public void setReceiverEnabled( boolean enabled )
    {
        logger.error( "Receiver enabled set to {}", enabled );
        mReceiverEnabled.set( enabled );
    }


    //
    // A routine to get the local ip address
    // TODO use this someplace
    //
    public String getLocalIpAddress()
    {
        return null;
    }
}

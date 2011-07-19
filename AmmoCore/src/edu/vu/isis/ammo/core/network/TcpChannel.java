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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.LinkedList;
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

    // New threads
    private SenderThread mSender;
    private ReceiverThread mReceiver;

    private int connectTimeout = 5 * 1000;     // this should come from network preferences
    private int socketTimeout = 5 * 1000; // milliseconds.

    private String gatewayHost = null;
    private int gatewayPort = -1;

    private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
    private final Object syncObj;

    private boolean shouldBeDisabled = false;
    private long flatLineTime;

    SocketChannel mSocketChannel;

    private SenderQueue mSenderQueue;

    private AtomicBoolean mIsAuthorized;
    private IChannelManager mChannelManager;
    private ISecurityObject mSecurityObject;

    private TcpChannel( IChannelManager iChannelManager ) {
        super();
        logger.info("Thread <{}>TcpChannel::<constructor>", Thread.currentThread().getId());
        this.syncObj = this;

        mIsAuthorized = new AtomicBoolean( false );

        mChannelManager = iChannelManager;
        this.connectorThread = new ConnectorThread(this);

        this.flatLineTime = 20 * 1000; // 20 seconds in milliseconds

        mSenderQueue = new SenderQueue( this );
    }


    public static TcpChannel getInstance( IChannelManager iChannelManager )
    {
        logger.trace("Thread <{}>::getInstance", Thread.currentThread().getId());
        TcpChannel instance = new TcpChannel( iChannelManager );
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


    public void authorizationSucceeded()
    {
        setIsAuthorized( true );
        mSenderQueue.markAsAuthorized();

        // Tell the NetworkService that we're authorized and have it
        // notify the apps.
        mChannelManager.authorizationSucceeded();
    }


    public void authorizationFailed()
    {
        // Disconnect the channel.
        reset();
    }


    // Called by ReceiverThread to send an incoming message to the
    // appropriate destination.
    private boolean deliverMessage( byte[] message,
                                    long checksum )
    {
        logger.info( "In deliverMessage()" );

        boolean result;
        if ( mIsAuthorized.get() )
        {
            logger.info( " delivering to channel manager" );
            result = mChannelManager.deliver( message, checksum );
        }
        else
        {
            logger.info( " delivering to security object" );
            result = getSecurityObject().deliverMessage( message, checksum );
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
        return handler.ack( status );
    }


    // Called by the ConnectorThread.
    private boolean auth()
    {
        return mChannelManager.auth();
    }


    // Called by the ConnectorThread.
    private boolean isAnyLinkUp()
    {
        return mChannelManager.isAnyLinkUp();
    }


    private final AtomicLong mTimeOfLastGoodRead = new AtomicLong( 0 );

    // This should be called each time we successfully read data from the
    // socket.
    private void resetTimeoutWatchdog()
    {
        //logger.debug( "Resetting watchdog timer" );
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

        private AtomicBoolean mIsConnected;

        public void statusChange()
        {
            parent.statusChange();
        }


        // Called by the sender and receiver when they have an exception on the
        // SocketChannel.  We only want to call reset() once, so we use an
        // AtomicBoolean to keep track of whether we need to call it.
        public void socketOperationFailed()
        {
            if ( mIsConnected.compareAndSet( true, false ))
                state.reset();
        }


        private ConnectorThread(TcpChannel parent) {
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
                                        parent.sendHeartbeatIfNeeded();
                                        this.state.wait(BURP_TIME);   // wait for somebody to change the connection status
                                        if ( parent.hasWatchdogExpired() )
                                        {
                                            //logger.warn( "Watchdog timer expired!!" );
                                            failure( getAttempt() );
                                        }
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


        private boolean connect()
        {
            logger.info( "Thread <{}>ConnectorThread::connect",
                         Thread.currentThread().getId() );

            // Resolve the hostname to an IP address.
            String host = (parent.gatewayHost != null) ? parent.gatewayHost : DEFAULT_HOST;
            int port =  (parent.gatewayPort > 10) ? parent.gatewayPort : DEFAULT_PORT;
            InetAddress ipaddr = null;
            try
            {
                ipaddr = InetAddress.getByName( host );
            }
            catch ( UnknownHostException e )
            {
                logger.warn( "could not resolve host name" );
                return false;
            }

            // Create the SocketChannel.
            InetSocketAddress sockAddr = new InetSocketAddress( ipaddr, port );
            try
            {
                if ( parent.mSocketChannel != null )
                    logger.error( "Tried to create mSocketChannel when we already had one." );
                parent.mSocketChannel = SocketChannel.open( sockAddr );
                boolean result = parent.mSocketChannel.finishConnect();
            }
            catch ( Exception e )
            {
                logger.warn( "connection to {}:{} failed: " + e.getLocalizedMessage(),
                             ipaddr, port );
                parent.mSocketChannel = null;
                return false;
            }

            // I spoke to Fred, and we've decided that this is unnecessary
            // because we have the watchdog timer now.  Also, the user can
            // modify this to have innappropriate values using AmmoCore, and
            // we need to prevent this.  We'll revisit this at some point.

            // Set the socket timeout.
            // try
            // {
            //     Socket s = parent.mSocketChannel.socket();
            //     if ( s != null )
            //         s.setSoTimeout( parent.socketTimeout );
            // }
            // catch ( SocketException ex )
            // {
            //     return false;
            // }

            logger.info( "connection to {}:{} established ", ipaddr, port );

            mIsConnected.set( true );

            // Create the security object.  This must be done before
            // the ReceiverThread is created in case we receive a
            // message before the SecurityObject is ready to have it
            // delivered.
            if ( parent.getSecurityObject() != null )
                logger.error( "Tried to create SecurityObject when we already had one." );
            parent.setSecurityObject( new TcpSecurityObject( parent ));

            // Create the sending thread.
            if ( parent.mSender != null )
                logger.error( "Tried to create Sender when we already had one." );
            parent.mSender = new SenderThread( this,
                                               parent,
                                               parent.mSenderQueue,
                                               parent.mSocketChannel );
            parent.mSender.start();

            // Create the receiving thread.
            if ( parent.mReceiver != null )
                logger.error( "Tried to create Receiver when we already had one." );
            parent.mReceiver = new ReceiverThread( this, parent, parent.mSocketChannel );
            parent.mReceiver.start();

            parent.getSecurityObject().authorize();

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

                if ( parent.mSocketChannel != null )
                {
                    Socket s = parent.mSocketChannel.socket();
                    if ( s != null )
                    {
                        logger.debug( "Closing underlying socket." );
                        s.close();
                        logger.debug( "Done" );
                    }
                    else
                    {
                        logger.debug( "SocketChannel had no underlying socket!" );
                    }
                    logger.info( "Closing SocketChannel..." );
                    parent.mSocketChannel.close();
                    parent.mSocketChannel = null;
                }

                setIsAuthorized( false );

                parent.setSecurityObject( null );
                parent.mSender = null;
                parent.mReceiver = null;
            }
            catch ( IOException e )
            {
                logger.error( "Caught IOException" );
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
     * @param size
     * @param checksum
     * @param message
     * @return
     */
    public boolean sendRequest(int size, CRC32 checksum, byte[] payload, INetworkService.OnSendMessageHandler handler)
    {
        return mSenderQueue.putFromDistributor( new GwMessage(size, checksum, payload, handler) );
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


    public void putFromSecurityObject( int size,
                                       CRC32 checksum,
                                       byte[] payload,
                                       INetworkService.OnSendMessageHandler handler )
    {
        mSenderQueue.putFromSecurityObject( new GwMessage(size, checksum, payload, handler) );
    }


    public void finishedPuttingFromSecurityObject()
    {
        mSenderQueue.finishedPuttingFromSecurityObject();
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    class SenderQueue
    {
        public SenderQueue( TcpChannel iChannel )
        {
            mChannel = iChannel;

            setIsAuthorized( false );
            mDistQueue = new LinkedBlockingQueue<GwMessage>( 20 );
            mAuthQueue = new LinkedList<GwMessage>();
        }


        // In the new design, aren't we supposed to let the
        // NetworkService know if the outgoing queue is full or not?
        public boolean putFromDistributor( GwMessage iMessage )
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


        public synchronized void putFromSecurityObject( GwMessage iMessage )
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


        public synchronized GwMessage take() throws InterruptedException
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
            GwMessage msg = mDistQueue.poll();
            while ( msg != null )
            {
                if ( msg.handler != null )
                    mChannel.ackToHandler( msg.handler, false );
                msg = mDistQueue.poll();
            }

            setIsAuthorized( false );
        }


        private BlockingQueue<GwMessage> mDistQueue;
        private LinkedList<GwMessage> mAuthQueue;
        private TcpChannel mChannel;
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    class SenderThread extends Thread
    {
        public SenderThread( ConnectorThread iParent,
                             TcpChannel iChannel,
                             SenderQueue iQueue,
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
                    logger.debug( "Took a message from the send queue" );
                }
                catch ( InterruptedException ex )
                {
                    logger.debug( "interrupted taking messages from send queue: {}",
                                  ex.getLocalizedMessage() );
                    setSenderState( INetChannel.INTERRUPTED );
                    break;
                }

                try
                {
                    int total_length = (Integer.SIZE/Byte.SIZE) + 4 + msg.payload.length;
                    ByteBuffer buf = ByteBuffer.allocate( total_length );
                    buf.order( ByteOrder.LITTLE_ENDIAN ); // mParent.endian

                    buf.putInt( msg.size );
                    logger.debug( "   size={}", msg.size );
                    long cvalue = msg.checksum.getValue();
                    byte[] checksum = new byte[]
                        {
                            (byte) cvalue,
                            (byte) (cvalue >>> 8),
                            (byte) (cvalue >>> 16),
                            (byte) (cvalue >>> 24)
                        };
                    logger.debug( "   checksum={}", checksum );

                    buf.put( checksum, 0, 4 );
                    buf.put( msg.payload );
                    logger.debug( "   payload={}", msg.payload );
                    buf.flip();

                    setSenderState( INetChannel.SENDING );
                    int bytesWritten = mSocketChannel.write( buf );
                    logger.info( "Wrote packet to SocketChannel" );

                    // legitimately sent to gateway.
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, true );
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                    logger.warn("sender threw exception");
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, false );
                    setSenderState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                }
            }
        }


        private void setSenderState( int iState )
        {
            synchronized ( this )
            {
                mState = iState;
            }
            mParent.statusChange();
        }

        public synchronized int getSenderState() { return mState; }

        private int mState = INetChannel.TAKING;
        private ConnectorThread mParent;
        private TcpChannel mChannel;
        private SenderQueue mQueue;
        private SocketChannel mSocketChannel;
        private final Logger logger = LoggerFactory.getLogger( "network.tcp.sender" );
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    class ReceiverThread extends Thread
    {
        public ReceiverThread( ConnectorThread iParent,
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
                    logger.debug( "Reading from SocketChannel..." );
                    int bytesRead =  mSocketChannel.read( mBuffer );
                    logger.debug( "SocketChannel read bytes={}", bytesRead );

                    mDestination.resetTimeoutWatchdog();

                    // We loop here because a single read() may have  read in
                    // the data for several messages
                    if ( bufferContainsAMessage() )
                    {
                        logger.debug( "flipping" );
                        mBuffer.flip();  // Switch to draining
                        while ( processAMessageIfAvailable() )
                            logger.debug( "processed a message" );
                        logger.debug( "compacting buffer" );
                        mBuffer.compact(); // Switches back to filling
                    }

                }
                catch ( InterruptedException e )
                {
                    logger.debug( "interrupted reading messages {}",
                                  e.getLocalizedMessage() );
                    setReceiverState( INetChannel.INTERRUPTED );
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                    logger.warn("receiver threw exception");
                    setReceiverState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                }
            }
        }


        // If the header is corrupted, things could go horribly wrong. We need
        // better error detection and error handling when we're reading stuff
        // in off the network.  Add this when we do the enhancement to handle
        // priorities.

        // Flips the buffer, but returns it back to filling. We can't tell if
        // a buffer contains a complete message without entering draining mode,
        // so this method does that but returns mBuffer to its original state.
        private boolean bufferContainsAMessage()
        {
            int position = mBuffer.position();
            int limit = mBuffer.limit();

            mBuffer.flip(); // Switches to draining mode

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

            // Return buffer back to normal before returning. This has the
            // effect of switching back to filling mode and it will be in
            // exactly the same state as when this function was called.
            mBuffer.position( position );
            mBuffer.limit( limit );

            return containsMessage;
        }


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

            logger.debug( "Receiving message:" );
            logger.debug( "   message size={}", messageSize );

            // The four bytes of the checksum go into the least significant
            // four bytes of a long.
            byte[] checkBytes = new byte[ 4 ];
            mBuffer.get( checkBytes, 0, 4 );
            logger.debug( "   checkBytes={}", checkBytes );
            long checksum = ( ((0xFFL & checkBytes[0]) << 0)
                            | ((0xFFL & checkBytes[1]) << 8)
                            | ((0xFFL & checkBytes[2]) << 16)
                            | ((0xFFL & checkBytes[3]) << 24) );
            logger.debug( "   checksum={}", Long.toHexString(checksum) );

            byte[] message = new byte[messageSize];
            mBuffer.get( message, 0, messageSize );
            logger.debug( "   message={}", message );

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
        private final Logger logger
            = LoggerFactory.getLogger( "network.tcp.receiver" );
    }


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
}

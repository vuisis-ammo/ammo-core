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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;

import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelDisposal;
import edu.vu.isis.ammo.core.pb.AmmoMessages;


/**
 * Two long running threads and one short.
 * The long threads are for sending and receiving messages.
 * The short thread is to connect the socket.
 * The sent messages are placed into a queue if the socket is connected.
 *
 */
public class TcpChannel extends NetChannel {
    private static final Logger logger = LoggerFactory.getLogger( "net.tcp" );

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
	private SocketChannel mSocketChannel;

	private final SenderQueue mSenderQueue;

	private final AtomicBoolean mIsAuthorized;

	// I made this public to support the hack to get authentication
	// working before Nilabja's code is ready.  Make it private again
	// once his stuff is in.
	public final IChannelManager mChannelManager;
	private final AtomicReference<ISecurityObject> mSecurityObject = new AtomicReference<ISecurityObject>();

	private TcpChannel(String name, IChannelManager iChannelManager ) {
		super(name);
		logger.info("Thread <{}>TcpChannel::<constructor>", Thread.currentThread().getId());
		this.syncObj = this;

		mIsAuthorized = new AtomicBoolean( false );

		mChannelManager = iChannelManager;
		this.connectorThread = new ConnectorThread(this);

		this.flatLineTime = 20 * 1000; // 20 seconds in milliseconds

		mSenderQueue = new SenderQueue( this );
	}

	public static TcpChannel getInstance(String name, IChannelManager iChannelManager )
	{
		logger.trace("Thread <{}>::getInstance", Thread.currentThread().getId());
		final TcpChannel instance = new TcpChannel(name, iChannelManager );
		return instance;
	}

	public boolean isConnected() { 
		return this.connectorThread.isConnected(); 
	}

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

                logger.info("::enable - Setting the state to STALE");
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
                logger.info("::disable - Setting the state to DISABLED");
                this.shouldBeDisabled = true;
                this.connectorThread.state.set(NetChannel.DISABLED);
                // this.connectorThread.stop();
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
		return new StringBuilder().append("channel ").append(super.toString())
            .append("socket: host[").append(this.gatewayHost).append("] ")
            .append("port[").append(this.gatewayPort).append("]").toString();
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
		// PLogger.proc_debug("reset ")
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

        try {
            mChannelManager.statusChange( this,
                                          this.connectorThread.state.value,
                                          senderState,
                                          receiverState );
        } catch ( Exception ex ) {
            logger.error( "Exception thrown in statusChange() {} \n {}",
                          ex.getLocalizedMessage(),
                          ex.getStackTrace());
        }
	}


	private void setSecurityObject( ISecurityObject iSecurityObject )
	{
        mSecurityObject.set( iSecurityObject );
	}


	private ISecurityObject getSecurityObject()
	{
		return mSecurityObject.get();
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

		// Tell the AmmoService that we're authorized and have it
		// notify the apps.
		mChannelManager.authorizationSucceeded(this, agm );
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
		logger.error( "In deliverMessage() {} ", agm );

		final boolean result;
		if ( mIsAuthorized.get() )
		{
			logger.info( " delivering to channel manager" );
			result = mChannelManager.deliver( agm );
		}
		else
		{
			logger.info( " delivering to security object" );
			ISecurityObject so = getSecurityObject();
			if (so == null) {
				logger.warn("security object not set");
				return false;
			}
			result = so.deliverMessage( agm );
		}
		return result;
	}

	/**
	 *  Called by the SenderThread.
	 *  This exists primarily to make a place to add instrumentation.
	 *  Also, follows the delegation pattern.
	 */
	private boolean ackToHandler( INetworkService.OnSendMessageHandler handler,
                                  ChannelDisposal status )
	{
		return handler.ack( this.name, status );
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
		if ( nowInMillis < mNextHeartbeatTime.get() ) return;

		// Send the heartbeat here.
		logger.warn( "Sending a heartbeat. t={}", nowInMillis );

		// Create a heartbeat message and call the method to send it.
		final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType( AmmoMessages.MessageWrapper.MessageType.HEARTBEAT );
		mw.setMessagePriority(AmmoGatewayMessage.PriorityLevel.FLASH.v);

		final AmmoMessages.Heartbeat.Builder message = AmmoMessages.Heartbeat.newBuilder();
		message.setSequenceNumber( nowInMillis ); // Just for testing

		mw.setHeartbeat( message );

		final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(mw, null);
		agmb.isGateway(true);
		this.sendRequest( agmb.build() );

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
		private final Logger logger = LoggerFactory.getLogger( "net.tcp.connector" );

		private final String DEFAULT_HOST = "192.168.1.100";
		private final int DEFAULT_PORT = 33289;

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
				logger.info("Thread <{}>State::set", Thread.currentThread().getId());
                if ( state == STALE ) {
					this.reset();
                } else {
                    this.value = state;
                    this.notifyAll();
                }
			}

            public synchronized int get() { return this.value; }

            public synchronized boolean isConnected() {
                return this.value == INetChannel.CONNECTED;
            }
            
            public synchronized boolean isSuppressed() {
            	return this.value == INetChannel.DISABLED;
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
                                this.parent.statusChange();
                                disconnect();

                                // Wait for a link interface.
                                while (this.state.isSuppressed())
                                {
                                    logger.info("Looping in Disabled");
                                    this.state.wait(BURP_TIME);
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
                                while (! parent.isAnyLinkUp()  && ! this.state.isSuppressed()) {
                                	this.state.wait(BURP_TIME);   // wait for a link interface
                                }   
                                if (! this.state.isSuppressed()) {
                                	this.state.set(NetChannel.DISCONNECTED);
                                }
                            }
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
                            synchronized (this.state) {
	                            this.state.wait(NetChannel.CONNECTION_RETRY_DELAY);
	                            if (this.state.isSuppressed()) {
	                            	logger.debug("halt connection attempt");
	                            } else if ( this.connect() ) {
	                                this.state.set(NetChannel.CONNECTED);
	                            } else {
	                                this.failure(attempt);
	                            }
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
                                while (this.isConnected())
                                {
                                    if ( HEARTBEAT_ENABLED )
                                        parent.sendHeartbeatIfNeeded();

                                    // wait for somebody to change the connection status
                                    this.state.wait(BURP_TIME);
                                    
                                    if ( HEARTBEAT_ENABLED && parent.hasWatchdogExpired() )
                                    {
                                        logger.warn( "Watchdog timer expired!!" );
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
                            synchronized (this.state){ 
	                            this.state.wait(NetChannel.CONNECTION_RETRY_DELAY);
	                            
	                            if (this.state.isSuppressed()) {
	                            	logger.debug("halt connection attempt");
	                            } else {
	                                this.failure(attempt);
	                            }
                            }
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
                logger.error("channel exception {} \n {}", ex.getLocalizedMessage(), ex.getStackTrace());
            }
            try {
                if (this.parent.socket == null) {
                    logger.error("channel closing without active socket}");
                    return;
                }
                this.parent.socket.close();
            } catch (IOException ex) {
                logger.error("channel closing without proper socket {}", ex.getStackTrace());
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
                @SuppressWarnings("unused")
                    boolean result = parent.mSocketChannel.finishConnect();
            }
            catch ( AsynchronousCloseException ex ) {
                logger.warn( "connection to {}:{} {} async close failure: {}",
                             new Object[]{ipaddr, port, 
                                          ex.getLocalizedMessage()});
                parent.mSocketChannel = null;
                return false;
            }
            catch ( ClosedChannelException ex ) {
                logger.warn( "connection to {}:{} {} closed channel failure: {}",
                             new Object[]{ipaddr, port, 
                                          ex.getLocalizedMessage()});
                parent.mSocketChannel = null;
                return false;
            }
            catch ( Exception e )
            {
                logger.warn( "connection to {}:{} {} failed: {}",
                             new Object[]{ipaddr, port, 
                                          e.getClass().getName(),
                                          e.getLocalizedMessage()});
                parent.mSocketChannel = null;
                return false;
            }

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

            // FIXME: don't pass in the result of buildAuthenticationRequest(). This is
            // just a temporary hack.
            parent.getSecurityObject().authorize( mChannelManager.buildAuthenticationRequest() );

            return true;
        }


        private boolean disconnect()
        {
            logger.info( "Thread <{}>ConnectorThread::disconnect",
                         Thread.currentThread().getId() );
            try
            {
                mIsConnected.set( false );

                if ( mSender != null ) {
                    logger.debug( "interrupting SenderThread" );
                    mSender.interrupt();
                }
                if ( mReceiver != null ) {
                    logger.debug( "interrupting ReceiverThread" );
                    mReceiver.interrupt();
                }

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
     * @param agm AmmoGatewayMessage
     * @return
     */
    public ChannelDisposal sendRequest( AmmoGatewayMessage agm )
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
        public SenderQueue( TcpChannel iChannel )
        {
            mChannel = iChannel;

            setIsAuthorized( false );
            // mDistQueue = new LinkedBlockingQueue<AmmoGatewayMessage>( 20 );
            mDistQueue = new PriorityBlockingQueue<AmmoGatewayMessage>( 20 );
            mAuthQueue = new LinkedList<AmmoGatewayMessage>();
        }


        // In the new design, aren't we supposed to let the
        // AmmoService know if the outgoing queue is full or not?
        public ChannelDisposal putFromDistributor( AmmoGatewayMessage iMessage )
        {
            logger.info( "putFromDistributor()" );
            try {
				if (! mDistQueue.offer( iMessage, 1, TimeUnit.SECONDS )) {
					logger.warn("channel not taking messages {}", ChannelDisposal.BUSY );
				    return ChannelDisposal.BUSY;
				}
			} catch (InterruptedException e) {
				return ChannelDisposal.BAD;
			}
            return ChannelDisposal.QUEUED;
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
                    mChannel.ackToHandler( msg.handler, ChannelDisposal.PENDING );
                msg = mDistQueue.poll();
            }

            setIsAuthorized( false );
        }


        private BlockingQueue<AmmoGatewayMessage> mDistQueue;
        private LinkedList<AmmoGatewayMessage> mAuthQueue;
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


        /**
         * the message format is
         * 
         */
        @Override
        public void run()
        {
            logger.info( "Thread <{}>::run()", Thread.currentThread().getId() );

            // Block on reading from the queue until we get a message to send.
            // Then send it on the socket channel. Upon getting a socket error,
            // notify our parent and go into an error state.

            while ( mState != INetChannel.INTERRUPTED )
            {
                AmmoGatewayMessage msg = null;
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
                    ByteBuffer buf = msg.serialize( endian, AmmoGatewayMessage.VERSION_1_FULL, (byte)0 );
                    setSenderState( INetChannel.SENDING );
                    @SuppressWarnings("unused")
                        int bytesWritten = mSocketChannel.write( buf );
                    logger.info( "Wrote packet to SocketChannel" );

                    // legitimately sent to gateway.
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, ChannelDisposal.SENT );
                }
                catch ( Exception ex )
                {
                    logger.warn("sender threw exception {}", ex.getStackTrace());
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, ChannelDisposal.REJECTED );
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
        private final Logger logger = LoggerFactory.getLogger( "net.tcp.sender" );
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
        }

        /**
         * Block on reading from the SocketChannel until we get some data.
         * Then examine the buffer to see if we have any complete packets.
         * If we have an error, notify our parent and go into an error state.
         */
        @Override
        public void run()
        {
            logger.info( "Thread <{}>::run()", Thread.currentThread().getId() );


            ByteBuffer bbuf = ByteBuffer.allocate( TCP_RECV_BUFF_SIZE );
            bbuf.order( endian ); // mParent.endian

            while ( mState != INetChannel.INTERRUPTED ) {
                try {
                    int bytesRead =  mSocketChannel.read( bbuf );
                    mDestination.resetTimeoutWatchdog();
                    logger.debug( "SocketChannel getting header read bytes={}", bytesRead );
                    if (bytesRead == 0) continue;

                    setReceiverState( INetChannel.START );

                    // prepare to drain buffer
                    bbuf.flip(); 
                    for (AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.extractHeader(bbuf);
                         agmb != null; agmb = AmmoGatewayMessage.extractHeader(bbuf)) 
                    {
                        // if the message size is zero then there may be an error
                        if (agmb.size() < 1) {
                            logger.warn("discarding empty message error {}",
                                        agmb.error());
                            // TODO cause the reconnection behavior to change based on the error code
                            continue;
                        }
                        // if the message is TOO BIG then throw away the message
                        if (agmb.size() > MAX_MESSAGE_SIZE) {
                            logger.warn("discarding message of size {} with checksum {}", 
                                        agmb.size(), Long.toHexString(agmb.checksum()));
                            int size = agmb.size();
                            while (true) {
                                if (bbuf.remaining() < size) {
                                    int rem =  bbuf.remaining();
                                    size -= rem;
                                    bbuf.clear();
                                    bytesRead =  mSocketChannel.read( bbuf );
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
                                int rem =  bbuf.remaining();
                                bbuf.get(payload, offset, rem);
                                offset += rem;
                                size -= rem;
                                bbuf.clear();
                                bytesRead =  mSocketChannel.read( bbuf );
                                bbuf.flip();
                                continue;
                            }
                            bbuf.get(payload, offset, size);

                            AmmoGatewayMessage agm = agmb.payload(payload).build();
                            setReceiverState( INetChannel.DELIVER );
                            mDestination.deliverMessage( agm );
                            logger.info( "processed a message {}", 
                                         Long.toHexString(agm.payload_checksum) );
                            break;
                        }
                    }
                    // prepare to fill buffer
                    // if any bytes remain in the buffer they are a partial header
                    bbuf.compact();

                } catch (ClosedChannelException ex) {
                    logger.warn("receiver threw exception {}", ex.getStackTrace());
                    setReceiverState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                } catch ( Exception ex ) {
                    logger.warn("receiver threw exception {}", ex.getStackTrace());
                    setReceiverState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                }
            }
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

        private int mState = INetChannel.TAKING; // FIXME
        private ConnectorThread mParent;
        private TcpChannel mDestination;
        private SocketChannel mSocketChannel;
        private final Logger logger
        = LoggerFactory.getLogger( "net.tcp.receiver" );
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

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public boolean isAuthenticatingChannel() { return true; }

	@Override
	public void init(Context context) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void toLog(String context) {
		PLogger.ipc_panthr_gw_log.debug(" {}:{} timeout={} sec", 
				new Object[]{ gatewayHost, gatewayPort, flatLineTime});
	}
}

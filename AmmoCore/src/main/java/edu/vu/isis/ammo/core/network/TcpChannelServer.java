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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.util.ByteBufferAdapter;


/**
 * Two long running threads and one short.
 * The long threads are for sending and receiving messages.
 * The short thread is to connect the socket.
 * The sent messages are placed into a queue if the socket is connected.
 *
 */
public class TcpChannelServer extends TcpChannelAbstract {

	// ===========================================================
	// Constants
	// ===========================================================

	// a class based logger to be used by static methods ... 
	private static final Logger classlogger = LoggerFactory.getLogger("net.server");

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


	// ===========================================================
	// Factory
	// ===========================================================
	public static TcpChannelServer getInstance(String name, IChannelManager iChannelManager )
	{
		classlogger.trace("Thread <{}>::getInstance", Thread.currentThread().getId());
		final TcpChannelServer instance = new TcpChannelServer(name, iChannelManager );
		return instance;
	}

	// ===========================================================
	// Members
	// ===========================================================

	// private instance logger which is created in the constructor and used 
	// all over ...
	final private Logger logger;

	/** default timeout is 45 seconds */
	private int DEFAULT_WATCHDOG_TIMOUT = 45;

	private ConnectorThread connectorThread;

	/** these should come from network preferences, both are in milliseconds */
	private int connectTimeout = 30 * 1000; 
	private int socketTimeout = 30 * 1000;

	private String serverHost = null;
	private int serverPort = -1;

	private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
	private final Object syncObj;

	private boolean shouldBeEnabled = true;
	private long flatLineTime;

	private Socket mSocket;
	private SenderThread mSender;
	private ReceiverThread mReceiver;
	private SocketChannel mSocketChannel;

	private final SenderQueue mSenderQueue;

	private final AtomicBoolean mIsAuthorized;

	private String channelName = null;

	// status counts for gui
	private final AtomicInteger mMessagesSent = new AtomicInteger();
	private final AtomicInteger mMessagesReceived = new AtomicInteger();
	private final AtomicLong mTimeOfLastGoodRead = new AtomicLong( 0 );
	private final AtomicLong mTimeOfLastGoodSend = new AtomicLong( 0 );

	// Heartbeat-related members.
	private final long mHeartbeatInterval = 10 * 1000; // ms
	private final AtomicLong mNextHeartbeatTime = new AtomicLong( 0 );

	private final IChannelManager mChannelManager;
	private final AtomicReference<ISecurityObject> mSecurityObject = new AtomicReference<ISecurityObject>();

	private final Timer mUpdateBpsTimer = new Timer();

	private final TimerTask mUpdateBps = new TimerTask() {
		@Override
		public void run() {
			logger.trace( "UpdateBpsTask fired" );

			// Update the BPS stats for the sending and receiving.
			mBpsSent = (mBytesSent - mLastBytesSent) / BPS_STATS_UPDATE_INTERVAL;
			mLastBytesSent = mBytesSent;

			mBpsRead = (mBytesRead - mLastBytesRead) / BPS_STATS_UPDATE_INTERVAL;
			mLastBytesRead = mBytesRead;
		}
	};

	private TcpChannelServer(String name, IChannelManager iChannelManager ) {
		super(name); 
		// create the instance logger for instance methods
		// store the channel name
		channelName = name;    
		logger = LoggerFactory.getLogger("net.channel.server.base." + channelName);
		logger.trace("Thread <{}>TcpChannelServer::<constructor>", Thread.currentThread().getId());    

		this.syncObj = this;

		mIsAuthorized = new AtomicBoolean( false );

		mChannelManager = iChannelManager;
		this.connectorThread = new ConnectorThread(this);

		this.flatLineTime = DEFAULT_WATCHDOG_TIMOUT * 1000; // seconds into milliseconds

		mSenderQueue = new SenderQueue( this );

		// Set up timer to trigger once per minute.
		mUpdateBpsTimer.scheduleAtFixedRate( mUpdateBps, 0, BPS_STATS_UPDATE_INTERVAL * 1000 );
	}

	@Override
	public String getSendReceiveStats () {
		StringBuilder countsString = new StringBuilder();
		countsString.append( "S:" ).append( mMessagesSent.get() ).append( " " );
		countsString.append( "R:" ).append( mMessagesReceived.get() );
		return countsString.toString();
	}


	public boolean isConnected() { 
		return this.connectorThread.isConnected(); 
	}

	/**
	 * Was the status changed as a result of enabling the connection.
	 * @return
	 */
	public boolean isEnabled() { return this.shouldBeEnabled; }

	public void enable() {
		logger.trace("Thread <{}>::enable", Thread.currentThread().getId());
		synchronized (this.syncObj) {
			if ( !this.shouldBeEnabled ) {
				this.shouldBeEnabled = true;
				logger.trace("::enable - Setting the state to STALE");
				this.connectorThread.state.set(NetChannel.STALE);
			}
		}
	}

	public void disable() {
		logger.trace("Thread <{}>::disable", Thread.currentThread().getId());
		synchronized (this.syncObj) {
			if ( this.shouldBeEnabled ) {
				this.shouldBeEnabled = false;
				logger.trace("::disable - Setting the state to DISABLED");
				this.connectorThread.state.set(NetChannel.DISABLED);
			}
		}
	}

	public boolean setSocketTimeout(int value) {
		logger.trace("Thread <{}>::setSocketTimeout {}", Thread.currentThread().getId(), value);
		this.socketTimeout = value;
		this.reset();
		return true;
	}
	
	public boolean setPort(int port) {
		logger.trace("Thread <{}>::setPort {}", Thread.currentThread().getId(), port);
		if (serverPort == port) return false;
		this.serverPort = port;
		this.reset();
		return true;
	}

	public String toString() {
		return new StringBuilder().append("channel ").append(super.toString())
				.append("socket: host[").append(this.serverHost).append("] ")
				.append("port[").append(this.serverPort).append("]").toString();
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
		logger.trace("connector: {} sender: {} receiver: {}",
				this.connectorThread.showState(),
				(this.mSender == null ? "none" : this.mSender.getSenderState()),
				(this.mReceiver == null ? "none" : this.mReceiver.getReceiverState()));

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
		int connState = this.connectorThread.state.value;
		int senderState = (mSender != null) ? mSender.getSenderState() : INetChannel.PENDING;
		int receiverState = (mReceiver != null) ? mReceiver.getReceiverState() : INetChannel.PENDING;

		try {
			mChannelManager.statusChange(this,
					this.lastConnState, connState,
					this.lastSenderState, senderState,
					this.lastReceiverState, receiverState);
		} catch ( Exception ex ) {
			logger.error( "Exception thrown in statusChange()", ex);
		}
		this.lastConnState = connState;
		this.lastSenderState = senderState;
		this.lastReceiverState = receiverState;
	}


	// ===========================================================
	// Security things
	// ===========================================================

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
		logger.trace( "In setIsAuthorized(). value={}", iValue );

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

	// ===========================================================
	// Protocol
	// ===========================================================

	// Called by ReceiverThread to send an incoming message to the
	// appropriate destination.
	private boolean deliverMessage( AmmoGatewayMessage agm )
	{
		logger.debug( "In deliverMessage() {} ", agm );

		final boolean result;
		if ( mIsAuthorized.get() )
		{
			logger.trace( " delivering to channel manager" );
			result = mChannelManager.deliver( agm );
		}
		else
		{
			logger.trace( " delivering to security object" );
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
			DisposalState status )
	{
		return handler.ack( this.name, status );
	}

	// This should be called each time we successfully read data from the
	// socket.
	private void resetTimeoutWatchdog()
	{
		//logger.debug( "Resetting watchdog timer" );
		mTimeOfLastGoodRead.set( System.currentTimeMillis() );
		// update the time of last good send ...
		mTimeOfLastGoodSend.set(0);
	}

	// Send a heartbeat packet to the gateway if enough time has elapsed.
	// Note: the way this currently works, the heartbeat can only be sent
	// in intervals that are multiples of the burp time.  This may change
	// later if I can eliminate some of the wait()s.
	private void sendHeartbeatIfNeeded()
	{
		//logger.warn( "In sendHeartbeatIfNeeded()." );

		long nowInMillis = System.currentTimeMillis();
		if ( nowInMillis < mNextHeartbeatTime.get() ) return;

		//check DistQueue, if some thing is already there to be sent, 
		// no need for a heartbeat
		if (mSenderQueue.sizeOfDistQ() > 0)
			return;

		// Send the heartbeat here.
		logger.debug( "Sending a heartbeat. t={}", nowInMillis );

		// Create a heartbeat message and call the method to send it.
		final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
		mw.setType( AmmoMessages.MessageWrapper.MessageType.HEARTBEAT );
		mw.setMessagePriority(AmmoGatewayMessage.PriorityLevel.FLASH.v);

		final AmmoMessages.Heartbeat.Builder message = AmmoMessages.Heartbeat.newBuilder();
		message.setSequenceNumber( nowInMillis ); // Just for testing

		mw.setHeartbeat( message );

		final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(
				mw.build().toByteArray(), null);
		agmb.isGateway(true);
		agmb.isHeartbeat(true);
		this.sendRequest( agmb.build() );

		mNextHeartbeatTime.set( nowInMillis + mHeartbeatInterval );
		//logger.warn( "Next heartbeat={}", mNextHeartbeatTime );
	}

	// ===========================================================
	// Queues/Senders/Receivers
	// ===========================================================

	/**
	 * manages the connection.
	 * enable or disable expresses the operator intent.
	 * There is no reason to run the thread unless the channel is enabled.
	 *
	 * Any of the properties of the channel
	 *
	 */
	private class ConnectorThread extends Thread {
		final private Logger logger;

		private final int DEFAULT_PORT = 51423;//INetPrefKeys.DEFAULT_SERVER_PORT;

		private TcpChannelServer parent;
		private final State state;

		private AtomicBoolean mIsConnected;
		private ServerSocketChannel server;


		public void statusChange()
		{
			parent.statusChange();
		}


		// Called by the sender and receiver when they have an exception on the
		// Socket.  We only want to call reset() once, so we use an
		// AtomicBoolean to keep track of whether we need to call it.
		public void socketOperationFailed()
		{
			if ( mIsConnected.compareAndSet( true, false )) {
				logger.warn("Socket op failed", new Exception());
				state.reset();
			}
		}


		private ConnectorThread(TcpChannelServer parent) 
		{
			super(new StringBuilder("USB-Connect-").append(Thread.activeCount()).toString());
			//create the logger 
			logger = LoggerFactory.getLogger("net.channel.server.connector." + channelName );
			logger.trace("Thread <{}>ConnectorThread::<constructor>", Thread.currentThread().getId());
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
				logger.debug("link status {} {}", this.value, this.actual);
				this.notifyAll();
			}
			public synchronized void linkDown() {
				this.reset();
			}
			public synchronized void set(int state) {
				logger.trace("Thread <{}>State::set: {}", 
						Thread.currentThread().getId(),
						state);
				if ( state == STALE ) {
					this.reset();
				} else {
					this.value = state;
					this.notifyAll();
				}
			}
			/**
			 * changes the state as requested unless
			 * the current state is disabled.
			 * 
			 * @param state
			 * @return false if disabled; true otherwise
			 */
			public synchronized boolean setUnlessDisabled(int state) {
				logger.trace("Thread <{}>State::setUnlessDisabled", 
						Thread.currentThread().getId());
				if (state == DISABLED) return false;
				this.set(state);
				return true;
			}

			public synchronized int get() { return this.value; }

			public synchronized boolean isConnected() {
				return this.value == INetChannel.CONNECTED;
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
				if( server != null ) {
					try {
						server.close();	
					} catch ( Exception e ) {}
				}
				this.notifyAll();
				return true;
			}

			public String showState () {
				if (this.value == this.actual)
					return NetChannel.showState(this.value);
				else
					return NetChannel.showState(this.actual) + "->" + NetChannel.showState(this.value);
			}
		}

		public boolean isConnected() {
			return this.state.isConnected();
		}
		public String showState() { return this.state.showState( ); }

		/**
		 * reset forces the channel closed if open.
		 */
		public void reset() {
			this.state.failure(this.state.attempt);
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

			// start the server and wait for a client connect
			logger.debug("Thread <{}>ConnectorThread::run", Thread.currentThread().getId());
			for( ;; ) {
				logger.debug("channel goal {}", (parent.shouldBeEnabled) ? "enable" : "disable");
				if(! parent.shouldBeEnabled) {
					logger.debug("disabling channel... {}", parent.shouldBeEnabled);
					state.set(NetChannel.DISABLED);
				}

				// if disabled, wait till we're enabled again				
				synchronized (state) {
					logger.debug("channel is {}", state.get());
					while( state.get() == DISABLED ) {
						try {
							state.wait(BURP_TIME);
							logger.debug("burp {}", state);
						} catch ( InterruptedException ex ) {
							logger.trace("interrupting channel wait.");
						}
					}
				}

				logger.debug("set to disconnected state");
				state.setUnlessDisabled(NetChannel.DISCONNECTED);
				final int port =  (parent.serverPort > 10) ? parent.serverPort : DEFAULT_PORT;
				
				try {
					// open the server socket
					server = ServerSocketChannel.open();
					server.configureBlocking(true);
					server.socket().bind(new InetSocketAddress(port));
					logger.info("Opened server socket {}", server.socket().getLocalSocketAddress());					

					// got a socket, wait for a client connection
					while( server != null && !server.socket().isClosed() ) {
						Socket client = null;
						try {
							state.setUnlessDisabled(NetChannel.WAIT_CONNECT);
							logger.info("Awaiting client connection...");
							client = server.accept().socket();
							state.setUnlessDisabled(NetChannel.CONNECTING);

							logger.info("Received client connection, send GTG...");
							client.getOutputStream().write(1);

							logger.info("Prepare socket and threads");
							initSocket(client);
							
							// I think we're good
							state.setUnlessDisabled(NetChannel.CONNECTED);

							// while the socket is good, send a heartbeat
							while( !client.isClosed() ) {
								int s = state.get();
								if( s == DISABLED || s == STALE ) {
									logger.warn("dropped connection {}", s);
									disconnect();
								} else {
									sendHeartbeatIfNeeded();
									synchronized (state) {
										state.wait(mHeartbeatInterval);
									}
								}
							}

						} catch ( Exception e ) {
							logger.error("Failed to handle client connection on {}", port, e);
							state.setUnlessDisabled(NetChannel.DISCONNECTED);
							disconnect();
							if( client != null ) try { client.close(); } catch ( Exception ignored ) {}
						}
					}

				} catch ( Exception e ) {
					logger.error("Failed to open server socket on {}", port, e);
					state.setUnlessDisabled(NetChannel.DISCONNECTED);
					disconnect();
				}
			}
			
		}


		private void initSocket( Socket socket ) throws Exception {
			logger.trace( "Thread <{}>ConnectorThread::connect",
					Thread.currentThread().getId() );

			if ( parent.mSocket != null ) {
				logger.error( "Tried to create mSocket when we already had one." );
				parent.mSocket.close();
			}

			final long startConnectionMark = System.currentTimeMillis();
			parent.mSocket = socket;
			parent.mSocket.setSoTimeout( parent.socketTimeout );
			final long finishConnectionMark = System.currentTimeMillis();
			logger.info("connection time to establish={} ms", finishConnectionMark-startConnectionMark);

			parent.mSocketChannel = parent.mSocket.getChannel();
//			parent.mDataInputStream = new DataInputStream( parent.mSocket.getInputStream() );
//			parent.mDataOutputStream = new DataOutputStream( parent.mSocket.getOutputStream() );

			logger.info( "connection established to {}", socket.getLocalSocketAddress() );

			mIsConnected.set( true );
			mBytesSent = 0;
			mBytesRead = 0;
			mLastBytesSent = 0;
			mLastBytesRead = 0;
			mBpsSent = 0;
			mBpsRead = 0;

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
					parent.mSocket );
			parent.mSender.start();

			// Create the receiving thread.
			if ( parent.mReceiver != null )
				logger.error( "Tried to create Receiver when we already had one." );
			parent.mReceiver = new ReceiverThread( this, parent, parent.mSocket );
			parent.mReceiver.start();

			// FIXME: don't pass in the result of buildAuthenticationRequest(). This is
			// just a temporary hack.
			parent.getSecurityObject().authorize( mChannelManager.buildAuthenticationRequest() );
		}


		private boolean disconnect()
		{
			logger.trace( "Thread <{}>ConnectorThread::disconnect",
					Thread.currentThread().getId() );
			mIsConnected.set( false );

			if ( mSender != null ) {
				logger.debug( "interrupting SenderThread" );
				try {
					mSender.interrupt();
				} catch ( Exception e ) {
					logger.error( "Failed to interrupt sender", e );		
				}
				mSender = null;
			}
			if ( mReceiver != null ) {
				logger.debug( "interrupting ReceiverThread" );
				try {
					mReceiver.interrupt();
				} catch ( Exception e ) {
					logger.error( "Failed to interrupt receiver", e );
				}
				mReceiver = null;
			}

			mSenderQueue.reset();

			if ( parent.mSocket != null )
			{
				logger.debug( "Closing socket..." );
				try {
					parent.mSocket.close();
				} catch ( Exception e ) {
					logger.error( "Failed to close socket", e );
				}
				logger.debug( "Done" );
				parent.mSocket = null;
			}

			if( server != null ) {
				logger.debug( "Closing socket..." );
				try {
					server.close();
				} catch ( Exception e ) {
					logger.error( "Failed to close socket", e );
				}
				server = null;
			}

			setIsAuthorized( false );
			parent.setSecurityObject( null );
			parent.mSender = null;
			parent.mReceiver = null;
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
	public DisposalState sendRequest( AmmoGatewayMessage agm )
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
		public SenderQueue( TcpChannelServer iChannel )
		{
			mChannel = iChannel;

			setIsAuthorized( false );
			// mDistQueue = new LinkedBlockingQueue<AmmoGatewayMessage>( 20 );
			mDistQueue = new PriorityBlockingQueue<AmmoGatewayMessage>( 20 );
			mAuthQueue = new LinkedList<AmmoGatewayMessage>();
		}


		// In the new design, aren't we supposed to let the
		// AmmoService know if the outgoing queue is full or not?
		public DisposalState putFromDistributor( AmmoGatewayMessage iMessage )
		{
			logger.trace( "putFromDistributor()" );
			try {
				if (! mDistQueue.offer( iMessage, 1, TimeUnit.SECONDS )) {
					logger.warn("channel not taking messages {}", DisposalState.BUSY );
					return DisposalState.BUSY;
				}
			} catch (InterruptedException e) {
				return DisposalState.BAD;
			}
			return DisposalState.QUEUED;
		}


		public synchronized void putFromSecurityObject( AmmoGatewayMessage iMessage )
		{
			logger.trace( "putFromSecurityObject()" );
			mAuthQueue.offer( iMessage );
		}


		public synchronized void finishedPuttingFromSecurityObject()
		{
			logger.trace( "finishedPuttingFromSecurityObject()" );
			notifyAll();
		}


		// This is called when the SecurityObject has successfully
		// authorized the channel.
		public synchronized void markAsAuthorized()
		{
			logger.trace( "Marking channel as authorized" );
			notifyAll();
		}


		/**
		 * Condition wait for the some request to the channel.
		 * 
		 * An initial request cannot be processed until
		 * the channel has authenticated.
		 * 
		 * This is where the authorized SenderThread blocks when 
		 * taking a distribution request.
		 * If not yet authorized then return the first item in 
		 * the authentication queue, removing that item from its queue.
		 * 
		 * @return
		 * @throws InterruptedException
		 */
		public synchronized AmmoGatewayMessage take() throws InterruptedException
		{
			logger.trace( "taking from SenderQueue" );
			while (! mChannel.getIsAuthorized() && mAuthQueue.size() < 1) {
				logger.trace( "wait()ing in SenderQueue" );
				wait();
			}
			if ( mChannel.getIsAuthorized() ) {
				return mDistQueue.take();
			}
			// must be the  mAuthQueue.size() > 0
			return mAuthQueue.remove();
		}

		// Somehow synchronize this here.
		public synchronized void reset()
		{
			logger.trace( "reset()ing the SenderQueue" );
			// Tell the distributor that we couldn't send these
			// packets.
			AmmoGatewayMessage msg = mDistQueue.poll();
			while ( msg != null )
			{
				if ( msg.handler != null )
					mChannel.ackToHandler( msg.handler, DisposalState.PENDING );
				msg = mDistQueue.poll();
			}

			setIsAuthorized( false );
		}

		public int sizeOfDistQ () {
			return mDistQueue.size();
		}

		private BlockingQueue<AmmoGatewayMessage> mDistQueue;
		private LinkedList<AmmoGatewayMessage> mAuthQueue;
		private TcpChannelServer mChannel;
	}

	///////////////////////////////////////////////////////////////////////////
	//
	class SenderThread extends Thread
	{
		public SenderThread( ConnectorThread iParent,
				TcpChannelServer iChannel,
				SenderQueue iQueue,
				Socket iSocket )
		{
			super(new StringBuilder("USB-Sender-").append(Thread.activeCount()).toString());
			mParent = iParent;
			mChannel = iChannel;
			mQueue = iQueue;
			mSocket = iSocket;
			// create the logger 
			logger = LoggerFactory.getLogger("net.channel.server.sender." + channelName );
		}


		/**
		 * the message format is
		 * 
		 */
		@Override
		public void run()
		{
			logger.trace( "Thread <{}>::run()", Thread.currentThread().getId() );

			// Block on reading from the queue until we get a message to send.
			// Then send it on the socket channel. Upon getting a socket error,
			// notify our parent and go into an error state.

			while ( mState != INetChannel.INTERRUPTED && !isInterrupted() )
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
					logger.debug( "interrupted taking messages from send queue", ex );
					setSenderState( INetChannel.INTERRUPTED );
					break;
				}

				ByteBufferAdapter buf = null;
				try
				{
					buf = msg.serialize( endian, AmmoGatewayMessage.VERSION_1_FULL, (byte)0 );
					setSenderState( INetChannel.SENDING );
					int bytesSent = 0;
					while( buf.remaining() > 0 ) {
						bytesSent = buf.write(mSocketChannel);
						mBytesSent += bytesSent;

						logger.info( "Send packet to Network, size ({})", bytesSent );

						//set time of heartbeat sent 
						if (msg.isHeartbeat()) {
							if (mTimeOfLastGoodSend.get() == 0)
								mTimeOfLastGoodSend.set( System.currentTimeMillis() );
						}
					}


					//update status count 
					mMessagesSent.incrementAndGet();

					// legitimately sent to gateway.
					if ( msg.handler != null )
						mChannel.ackToHandler( msg.handler, DisposalState.SENT );
				}
				catch ( Exception ex )
				{
					logger.warn("sender threw exception", ex);
					if ( msg.handler != null )
						mChannel.ackToHandler( msg.handler, DisposalState.REJECTED );
					setSenderState( INetChannel.INTERRUPTED );
					mParent.socketOperationFailed();
				} finally {
					if( buf != null ) buf.release();
					if( msg != null ) msg.releasePayload();
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
		private TcpChannelServer mChannel;
		private SenderQueue mQueue;
		@SuppressWarnings("unused")
		private Socket mSocket;
		private Logger logger = null;
	}


	///////////////////////////////////////////////////////////////////////////
	//
	class ReceiverThread extends Thread
	{
		public ReceiverThread( ConnectorThread iParent,
				TcpChannelServer iDestination,
				Socket iSocket )
		{
			super(new StringBuilder("USB-Receiver-").append(Thread.activeCount()).toString());
			mParent = iParent;
			mDestination = iDestination;
			mSocket = iSocket;
			logger = LoggerFactory.getLogger( "net.channel.server.receiver." + channelName );

		}

		/**
		 * Block on reading from the Socket until we get some data.
		 * Then examine the buffer to see if we have any complete packets.
		 * If we have an error, notify our parent and go into an error state.
		 */
		@Override
		public void run()
		{
			logger.trace( "Thread <{}>::run()", Thread.currentThread().getId() );


			ByteBuffer bbuf = ByteBuffer.allocate( TCP_RECV_BUFF_SIZE );
			bbuf.order( endian ); // mParent.endian
			ByteBufferAdapter payload = null;

			threadWhile:
				while ( mState != INetChannel.INTERRUPTED && !isInterrupted() )
				{
					try {
						int bytesRead = mSocketChannel.read( bbuf );
						if ( isInterrupted() )
							break threadWhile; // exit thread

						mDestination.resetTimeoutWatchdog();
						logger.debug( "Socket getting header read bytes={}", bytesRead );
						if (bytesRead == 0) continue;

						if (bytesRead < 0) {
							logger.error("bytes read = {}, exiting", bytesRead);
							setReceiverState( INetChannel.INTERRUPTED );
							mParent.socketOperationFailed();
							return;
						}

						mBytesRead += bytesRead;
						setReceiverState( INetChannel.START );

						// prepare to drain buffer
						bbuf.flip(); 
						for (AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.extractHeader(bbuf);
								agmb != null; agmb = AmmoGatewayMessage.extractHeader(bbuf)) 
						{
							// if the message size is zero then there may be an error
							int size = agmb.size();
							if (size < 1) {
								logger.warn("discarding empty message error {}",
										agmb.error());
								// TODO cause the reconnection behavior to change based on the error code
								continue;
							}

							// extract the payload
							payload = ByteBufferAdapter.obtain(size);
							int bytesToRead = size;
							while (true) {
								// we done with this payload?
								if (bytesToRead > 0) {

									// if we have some of the payload on the buffer
									// after the header, make sure to grab it first
									if( bbuf.remaining() > 0 ) {
										// go ahead and slice the buffer because we
										// may not want all of it
										ByteBuffer slice = bbuf.slice();
										if( slice.limit() > bytesToRead ) {
											// we didn't wan't that many so limit the slice
											slice.limit(bytesToRead);
										}										
										// add the slice to the payload
										payload.put(slice);
										// advance the buffer for the bytes we took.  There may
										// be more data on the buffer for the header of the next
										// message
										bbuf.position(bbuf.position()+slice.limit());
										// keep track so we know when we have all of the payload
										bytesToRead -= slice.limit();
										bytesRead = 0;


									} else {
										// otherwise get the rest of the bytes from the channel
										bytesRead = payload.read(mSocketChannel);
										bytesToRead -= bytesRead;
									}


									if ( isInterrupted() )
										break threadWhile; // exit thread

									// something aint right
									if (bytesRead < 0) {
										logger.error("bytes read = {}, exiting", bytesRead);
										setReceiverState( INetChannel.INTERRUPTED );
										mParent.socketOperationFailed();
										return;                    
									}                

									// a successful read should reset the timer
									mDestination.resetTimeoutWatchdog();
									// also keep the count of the bytes that we've read
									mBytesRead += bytesRead;
									continue;
								}

								// flip to prepare for read
								payload.flip();
								AmmoGatewayMessage agm = agmb.payload(payload).channel(this.mDestination).build();
								if( logger.isDebugEnabled() ) {
									logger.debug("Received a packet in {} from gateway size({}) @{"+agm.buildTime+"}, csum({})",
											payload.time(), agm.size, agm.payload_checksum);
								}

								setReceiverState( INetChannel.DELIVER );
								mDestination.deliverMessage( agm );

								// received a valid message, update status count .... 
								mMessagesReceived.incrementAndGet();

								// unset payload
								payload = null;
								break;
							}
						}
						// prepare to fill buffer
						// if any bytes remain in the buffer they are a partial header
						bbuf.compact();

					} catch (ClosedChannelException ex) {
						logger.warn("receiver threw exception", ex);
						setReceiverState( INetChannel.INTERRUPTED );
						mParent.socketOperationFailed();
					} catch ( Exception ex ) {
						logger.warn("receiver threw exception", ex);
						setReceiverState( INetChannel.INTERRUPTED );
						mParent.socketOperationFailed();
					} finally {
						if( payload != null ) payload.release();
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
		private TcpChannelServer mDestination;
		@SuppressWarnings("unused")
		private Socket mSocket;
		private Logger logger = null;
	}


	// ********** UTILITY METHODS ****************

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public boolean isAuthenticatingChannel() { return true; }

	@Override
	public void init(Context context) {
	}

	@Override
	public void toLog(String context) {
		PLogger.SET_PANTHR_GW.debug(" {}:{} timeout={} sec", 
				serverHost, serverPort, flatLineTime );
	}
}

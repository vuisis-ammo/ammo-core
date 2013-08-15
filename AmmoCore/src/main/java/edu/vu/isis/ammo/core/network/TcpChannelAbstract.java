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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.pb.AmmoMessages;

/**
 * Two long running threads and one short. The long threads are for sending and
 * receiving messages. The short thread is to connect the socket. The sent
 * messages are placed into a queue if the socket is connected.
 * 
 */
abstract public class TcpChannelAbstract extends NetChannel {
	
	/**
	 *  private instance logger which is created in the constructor and used
	 *  all over ...
	 */
	protected final Logger logger;

	protected static final int BURP_TIME = 5 * 1000; // 5 seconds expressed in
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
	/** the maximum receive buffer size */
	private static final int TCP_RECV_BUFF_SIZE = 0x15554; 
	/** arbitrary max size */
	private static final int MAX_MESSAGE_SIZE = 0x100000; 
	
	/** default timeout is 45 seconds */
	private int DEFAULT_WATCHDOG_TIMOUT = 45;

	/** these should come from network preferences, both are in milliseconds */
	protected int connectTimeout = 30 * 1000;
	protected int socketTimeout = 30 * 1000;

	private int mMaxMessageSize = MAX_MESSAGE_SIZE;
	
	private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
	private final Object syncObj;

	protected boolean shouldBeEnabled = true;
	protected long flatLineTime;

	protected Socket mSocket;
	/** new threads */
	private ConnectorThread connectorThread;
	private SenderThread mSender;
	private ReceiverThread mReceiver;
	/** data streams */
	protected DataInputStream mDataInputStream;
	protected DataOutputStream mDataOutputStream;

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

	private Timer mUpdateBpsTimer = new Timer();

	protected final TimerTask mUpdateBpsTask = new TimerTask() {
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

	protected abstract ConnectorThread newConnectorThread(
			final TcpChannelAbstract parent);

	protected TcpChannelAbstract(String name, IChannelManager iChannelManager,
			final String childName) {
		super(name);
		// create the instance logger for instance methods
		// store the channel name
		channelName = name;
		logger = LoggerFactory.getLogger("net.channel.tcp." + childName
				+ ".base." + channelName);
		logger.trace("Thread <{}>TcpChannel::<constructor>", Thread
				.currentThread().getId());

		this.syncObj = this;

		mIsAuthorized = new AtomicBoolean(false);

		mChannelManager = iChannelManager;
		this.connectorThread = newConnectorThread(this);

		this.flatLineTime = DEFAULT_WATCHDOG_TIMOUT * 1000; // seconds into
															// milliseconds

		mSenderQueue = new SenderQueue(this);

		// Set up timer to trigger once per minute.
		mUpdateBpsTimer.scheduleAtFixedRate(mUpdateBpsTask, 0,
				BPS_STATS_UPDATE_INTERVAL * 1000);
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
		return this.shouldBeEnabled;
	}

	public void enable() {
		logger.trace("Thread <{}>::enable", Thread.currentThread().getId());
		synchronized (this.syncObj) {
			if (!this.shouldBeEnabled) {
				this.shouldBeEnabled = true;
				logger.trace("::enable - Setting the state to STALE");
				this.connectorThread.state.set(NetChannel.STALE);
			}
		}
	}

	public void disable() {
		logger.trace("Thread <{}>::disable", Thread.currentThread().getId());
		synchronized (this.syncObj) {
			if (this.shouldBeEnabled) {
				this.shouldBeEnabled = false;
				logger.trace("::disable - Setting the state to DISABLED");
				this.connectorThread.state.set(NetChannel.DISABLED);
			}
		}
	}

	public boolean close() {
		return false;
	}

	public boolean setSocketTimeout(int value) {
		logger.trace("Thread <{}>::setSocketTimeout {}", Thread.currentThread()
				.getId(), value);
		this.socketTimeout = value;
		this.reset();
		return true;
	}

	 public boolean setMaxMsgSize (int size) {
	    logger.trace("Thread <{}>::setMaxMsgSize {}", Thread.currentThread().getId(), size);
	    if (mMaxMessageSize  == (size * 0x100000)) return false;
	    this.mMaxMessageSize = size * 0x100000;
	    this.reset();
	    return true;
	  }
	 
	@Override
	public String toString() {
		return super.toString();
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
				this.connectorThread.showState(),
				(this.mSender == null ? "none" : this.mSender.getSenderState()),
				(this.mReceiver == null ? "none" : this.mReceiver
						.getReceiverState()));

		synchronized (this.syncObj) {
			if (!this.connectorThread.isAlive()) {
				this.connectorThread = newConnectorThread(this);
				this.connectorThread.start();
			}

			this.connectorThread.reset();
		}
	}

	protected void statusChange() {
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

	// ===========================================================
	// Security things
	// ===========================================================

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

	// ===========================================================
	// Protocol
	// ===========================================================

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
	protected boolean isAnyLinkUp() {
		return mChannelManager.isAnyLinkUp();
	}

	private final AtomicLong mTimeOfLastGoodRead = new AtomicLong(0);

	private final AtomicLong mTimeOfLastGoodSend = new AtomicLong(0);

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
	protected boolean hasWatchdogExpired() {
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

	// Heartbeat-related members.
	private final long mHeartbeatInterval = 10 * 1000; // ms
	private final AtomicLong mNextHeartbeatTime = new AtomicLong(0);

	// Send a heartbeat packet to the gateway if enough time has elapsed.
	// Note: the way this currently works, the heartbeat can only be sent
	// in intervals that are multiples of the burp time. This may change
	// later if I can eliminate some of the wait()s.
	protected void sendHeartbeatIfNeeded() {
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

	// ===========================================================
	// Queues/Senders/Receivers
	// ===========================================================

	/**
	 * manages the connection. enable or disable expresses the operator intent.
	 * There is no reason to run the thread unless the channel is enabled.
	 * 
	 * Any of the properties of the channel
	 * 
	 */
	protected class ConnectorThread extends Thread {
		protected final Logger logger;

		protected TcpChannelAbstract parent;
		protected final State state;

		protected AtomicBoolean mIsConnected;

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

		protected ConnectorThread(TcpChannelAbstract parent,
				final String childName) {
			super(new StringBuilder("Tcp-Connect-")
					.append(Thread.activeCount()).toString());
			// create the logger
			logger = LoggerFactory.getLogger("net.channel.tcp." + childName
					+ ".connecgtor." + channelName);
			logger.trace("Thread <{}>ConnectorThread::<constructor>", Thread
					.currentThread().getId());
			this.parent = parent;
			this.state = new State();
			mIsConnected = new AtomicBoolean(false);
		}

		protected class State {
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
				logger.trace("Thread <{}>State::set {}", Thread.currentThread()
						.getId(), state);
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

		protected boolean makeThreads() {

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
					parent.mSenderQueue, parent.mSocket);
			parent.mSender.start();

			// Create the receiving thread.
			if (parent.mReceiver != null)
				logger.error("Tried to create Receiver when we already had one.");
			parent.mReceiver = new ReceiverThread(this, parent, parent.mSocket);
			parent.mReceiver.start();

			// FIXME: don't pass in the result of buildAuthenticationRequest().
			// This is
			// just a temporary hack.
			parent.getSecurityObject().authorize(
					mChannelManager.buildAuthenticationRequest());

			return true;
		}

		protected boolean disconnect() {
			logger.trace("Thread <{}>ConnectorThread::disconnect", Thread
					.currentThread().getId());

			mIsConnected.set(false);

			if (mSender != null) {
				logger.debug("interrupting SenderThread");
				try {
					mSender.interrupt();
				} catch (Exception e) {
					logger.error("Failed to interrupt sender", e);
				}
				mSender = null;
			}
			if (mReceiver != null) {
				logger.debug("interrupting ReceiverThread");
				try {
					mReceiver.interrupt();
				} catch (Exception e) {
					logger.error("Failed to interrupt receiver", e);
				}
				mReceiver = null;
			}

			mSenderQueue.reset();

			if (parent.mSocket != null) {
				logger.debug("Closing socket...");
				try {
					parent.mSocket.close();
				} catch (Exception e) {
					logger.error("Failed to close socket", e);
				}
				logger.debug("Done");
				parent.mSocket = null;
			}

			setIsAuthorized(false);

			parent.setSecurityObject(null);
			parent.mSender = null;
			parent.mReceiver = null;

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

	/**
	 * Send the messages
	 */
	class SenderQueue {
		public SenderQueue(TcpChannelAbstract iChannel) {
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
		private TcpChannelAbstract mChannel;
	}

	// /////////////////////////////////////////////////////////////////////////
	//
	class SenderThread extends Thread {
		public SenderThread(ConnectorThread iParent,
				TcpChannelAbstract iChannel, SenderQueue iQueue, Socket iSocket) {
			super(new StringBuilder("Tcp-Sender-").append(Thread.activeCount())
					.toString());
			mParent = iParent;
			mChannel = iChannel;
			mQueue = iQueue;
			mSocket = iSocket;
			// create the logger
			logger = LoggerFactory.getLogger("net.channel.tcp.sender."
					+ channelName);
		}

		/**
		 * Block on reading from the queue until we get a message to send.
		 * Then send it on the socket channel. Upon getting a socket error,
		 *  notify our parent and go into an error state.
		 */
		@Override
		public void run() {
			logger.trace("Thread <{}>::run()", Thread.currentThread().getId());

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
				    if (msg.size  > TcpChannelAbstract.this.mMaxMessageSize) {
			            logger.info("Large Message, Rejecting: Message Size [" + msg.size + "]");
			            if ( msg.handler != null )
			                mChannel.ackToHandler( msg.handler, DisposalState.BAD);            
			            continue;
			        }
					
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
		private TcpChannelAbstract mChannel;
		private SenderQueue mQueue;
		@SuppressWarnings("unused")
		private Socket mSocket;
		private Logger logger = null;
	}

	// /////////////////////////////////////////////////////////////////////////
	//
	class ReceiverThread extends Thread {
		public ReceiverThread(ConnectorThread iParent,
				TcpChannelAbstract iDestination, Socket iSocket) {
			super(new StringBuilder("Tcp-Receiver-").append(
					Thread.activeCount()).toString());
			mParent = iParent;
			mDestination = iDestination;
			mSocket = iSocket;
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

			threadWhile: 
				while (mState != INetChannel.INTERRUPTED
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
						if (agmb.size() > TcpChannelAbstract.this.mMaxMessageSize) {
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
		private TcpChannelAbstract mDestination;
		@SuppressWarnings("unused")
		private Socket mSocket;
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

	public abstract void toLog(String context);

}

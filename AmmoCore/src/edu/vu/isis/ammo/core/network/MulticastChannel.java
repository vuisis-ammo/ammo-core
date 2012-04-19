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
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
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
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.store.DistributorDataStore.DisposalState;


public class MulticastChannel extends NetChannel
{
    private static final Logger logger = LoggerFactory.getLogger("net.mcast");

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
    @SuppressWarnings("unused")
	private static final int TCP_RECV_BUFF_SIZE = 0x15554; // the maximum receive buffer size
    @SuppressWarnings("unused")
	private static final int MAX_MESSAGE_SIZE = 0x100000;  // arbitrary max size
    private boolean isEnabled = true;

    private final Socket socket = null;
    private ConnectorThread connectorThread;

    // New threads
    private SenderThread mSender;
    private ReceiverThread mReceiver;

    @SuppressWarnings("unused")
    private int connectTimeout = 5 * 1000; // this should come from network preferences
    @SuppressWarnings("unused")
    private int socketTimeout = 5 * 1000; // milliseconds.

    /*
    private String gatewayHost = null;
    private int gatewayPort = -1;
     */
    private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
    private final Object syncObj;

    private boolean shouldBeDisabled = false;
    @SuppressWarnings("unused")
	private final long flatLineTime;

    private MulticastSocket mSocket;
    private String mMulticastAddress;
    private InetAddress mMulticastGroup = null;
    private int mMulticastPort;
    private AtomicInteger mMulticastTTL;

    private SenderQueue mSenderQueue;

    private final AtomicBoolean mIsAuthorized;

    // I made this public to support the hack to get authentication
    // working before Nilabja's code is ready.  Make it private again
    // once his stuff is in.
    public final IChannelManager mChannelManager;
	private final AtomicReference<ISecurityObject> mSecurityObject = new AtomicReference<ISecurityObject>();


    private MulticastChannel(String name, IChannelManager iChannelManager ) {
        super(name);

        logger.trace("Thread <{}>MulticastChannel::<constructor>", Thread.currentThread().getId());
        this.syncObj = this;

        mIsAuthorized = new AtomicBoolean( false );
        mMulticastTTL = new AtomicInteger( 1 );

        mChannelManager = iChannelManager;

        this.flatLineTime = 20 * 1000; // 20 seconds in milliseconds

        mSenderQueue = new SenderQueue( this );

        // The thread is start()ed the first time the network disables and
        // reenables it.
        this.connectorThread = new ConnectorThread(this);
    }


    public static MulticastChannel getInstance(String name, IChannelManager iChannelManager )
    {
        logger.trace("Thread <{}> MulticastChannel::getInstance()",
                     Thread.currentThread().getId());
        MulticastChannel instance = new MulticastChannel(name, iChannelManager );
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
        logger.trace("Thread <{}>::setHost {}", Thread.currentThread().getId(), host);
        if ( this.mMulticastAddress != null && this.mMulticastAddress.equals(host) ) return false;
        this.mMulticastAddress = host;
        this.reset();
        return true;
    }

    public boolean setPort(int port) {
        logger.trace("Thread <{}>::setPort {}", Thread.currentThread().getId(), port);
        if (this.mMulticastPort == port) return false;
        this.mMulticastPort = port;
        this.reset();
        return true;
    }

    public void setTTL( int ttl ) {
        logger.trace("Thread <{}>::setTTL {}", Thread.currentThread().getId(), ttl);
        this.mMulticastTTL.set( ttl );
    }

    public String toString() {
        return "socket: host["+this.mMulticastAddress+"] port["+this.mMulticastPort+"]";
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
        logger.trace("connector: {} sender: {} receiver: {}",
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
        } catch ( Exception e ) {
            logger.error( "Exception thrown in statusChange() {} \n {}",
                          e.getLocalizedMessage(),
                          e.getStackTrace());
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


    // Called by ReceiverThread to send an incoming message to the
    // appropriate destination.
    private boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.trace( "deliverMessage() {} ", agm );

        boolean result;
        if ( mIsAuthorized.get() )
        {
            logger.trace( " delivering to channel manager" );
            result = mChannelManager.deliver( agm );
        }
        else
        {
            logger.trace( " delivering to security object" );
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
    		DisposalState status )
    {
        return handler.ack( this.name, status );
    }

    // Called by the ConnectorThread.
    private boolean isAnyLinkUp()
    {
        return mChannelManager.isAnyLinkUp();
    }


    @SuppressWarnings("unused")
	private final AtomicLong mTimeOfLastGoodRead = new AtomicLong( 0 );


    // Heartbeat-related members.
    private final long mHeartbeatInterval = 10 * 1000; // ms
    private final AtomicLong mNextHeartbeatTime = new AtomicLong( 0 );

    // Send a heartbeat packet to the gateway if enough time has elapsed.
    // Note: the way this currently works, the heartbeat can only be sent
    // in intervals that are multiples of the burp time.  This may change
    // later if I can eliminate some of the wait()s.
    @SuppressWarnings("unused")
	private void sendHeartbeatIfNeeded()
    {
        //logger.warn( "In sendHeartbeatIfNeeded()." );

        long nowInMillis = System.currentTimeMillis();
        if ( nowInMillis < mNextHeartbeatTime.get() ) return;

        // Send the heartbeat here.
        logger.trace( "Sending a heartbeat. t={}", nowInMillis );

        // Create a heartbeat message and call the method to send it.
        final AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType( AmmoMessages.MessageWrapper.MessageType.HEARTBEAT );
        mw.setMessagePriority(AmmoGatewayMessage.PriorityLevel.FLASH.v);

        final AmmoMessages.Heartbeat.Builder message = AmmoMessages.Heartbeat.newBuilder();
        message.setSequenceNumber( nowInMillis ); // Just for testing

        mw.setHeartbeat( message );

        final AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder(mw, null);
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
        private final Logger logger = LoggerFactory.getLogger( "net.mcast.connector" );

        // private final String DEFAULT_HOST = "192.168.1.100";
        // private final int DEFAULT_PORT = 33289;

        private MulticastChannel parent;
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


        private ConnectorThread( MulticastChannel parent ) {
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
                this.notifyAll();
            }
            public synchronized void linkDown() {
                this.reset();
            }
            public synchronized void set(int state) {
                logger.trace("Thread <{}>State::set", 
                		Thread.currentThread().getId());
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
            
            public synchronized boolean isDisabled() {
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
            public synchronized boolean failureUnlessDisabled(long attempt) {
            	if (this.value == INetChannel.DISABLED) return false; 
            	return this.failure(attempt);
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
                logger.trace("Thread <{}>ConnectorThread::run", Thread.currentThread().getId());
                MAINTAIN_CONNECTION: while (true) {
                    logger.trace("connector state: {}",this.showState());

                    if(this.parent.shouldBeDisabled) this.state.set(NetChannel.DISABLED);
                    switch (this.state.get()) {
                    case NetChannel.DISABLED:
                        try {
                            synchronized (this.state) {
                                logger.trace("this.state.get() = {}", this.state.get());
                                this.parent.statusChange();
                                disconnect();

                                // Wait for a link interface.
                                while (this.state.isDisabled())
                                {
                                    logger.trace("Looping in Disabled");
                                    this.state.wait(BURP_TIME);
                                }
                            }
                        } catch (InterruptedException ex) {
                            logger.warn("connection intentionally disabled {}", this.state );
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
	                        	while (! parent.isAnyLinkUp()  && ! this.state.isDisabled()) {
	                            	this.state.wait(BURP_TIME);   // wait for a link interface
	                            }   
	                            this.state.setUnlessDisabled(NetChannel.DISCONNECTED);
                        	}
                        } catch (InterruptedException ex) {
                            logger.warn("connection intentionally disabled {}", this.state );
                            this.state.setUnlessDisabled(NetChannel.STALE);
                            break MAINTAIN_CONNECTION;
                        }
                        this.parent.statusChange();
                        // or else wait for link to come up, triggered through broadcast receiver
                        break;

                    case NetChannel.DISCONNECTED:
                        this.parent.statusChange();
                        if ( !this.connect() ) {
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
	                            this.state.wait(NetChannel.CONNECTION_RETRY_DELAY);
	                            if ( this.connect() ) {
	                                this.state.setUnlessDisabled(NetChannel.CONNECTED);
	                            } else {
	                                this.state.failureUnlessDisabled(attempt);
	                            }
                            }
                            this.parent.statusChange();
                        } catch (InterruptedException ex) {
                            logger.warn("sleep interrupted - intentional disable, exiting thread ... {}", ex.getStackTrace());
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
                                        // if ( HEARTBEAT_ENABLED )
                                        //     parent.sendHeartbeatIfNeeded();

                                        // wait for somebody to change the connection status
                                        this.state.wait(BURP_TIME);
                                    }
                                }
                            } catch (InterruptedException ex) {
                                logger.warn("connection intentionally disabled {}", this.state );
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
                            synchronized (this.state){ 
	                            this.state.wait(NetChannel.CONNECTION_RETRY_DELAY);
	                            this.state.failureUnlessDisabled(attempt);
                            }
                            this.parent.statusChange();
                        } catch (InterruptedException ex) {
                            logger.warn("sleep interrupted - intentional disable, exiting thread ...{}", ex.getStackTrace());
                            this.reset();
                            break MAINTAIN_CONNECTION;
                        }
                    }
                }

            } catch (Exception ex) {
                logger.error("failed to run multicast {}", ex.getLocalizedMessage());
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
            logger.trace( "Thread <{}>ConnectorThread::connect",
                         Thread.currentThread().getId() );

            try
            {
                parent.mMulticastGroup = InetAddress.getByName( parent.mMulticastAddress );
            }
            catch ( UnknownHostException e )
            {
                logger.warn( "could not resolve host name" );
                return false;
            }

            // Create the MulticastSocket.
            if ( parent.mSocket != null )
                logger.error( "Tried to create mSocket when we already had one." );
            try
            {
                parent.mSocket = new MulticastSocket( parent.mMulticastPort );
                parent.mSocket.joinGroup( parent.mMulticastGroup );
            }
            catch ( Exception e )
            {
                logger.warn( "connection to {}:{} failed: {}", new Object[]{
                             parent.mMulticastGroup,
                             parent.mMulticastPort,
			     e.getStackTrace() });
                parent.mSocket = null;
                return false;
            }

            logger.info( "connection to {}:{} established ",
                         parent.mMulticastGroup,
                         parent.mMulticastPort );

            mIsConnected.set( true );

            // Create the security object.  This must be done before
            // the ReceiverThread is created in case we receive a
            // message before the SecurityObject is ready to have it
            // delivered.
            if ( parent.getSecurityObject() != null )
                logger.error( "Tried to create SecurityObject when we already had one." );
            parent.setSecurityObject( new MulticastSecurityObject( parent ));

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
            //parent.getSecurityObject().authorize( mChannelManager.buildAuthenticationRequest());
            setIsAuthorized( true );
            mSenderQueue.markAsAuthorized();

            return true;
        }


        private boolean disconnect()
        {
            logger.trace( "Thread <{}>ConnectorThread::disconnect",
                         Thread.currentThread().getId() );
            try
            {
                mIsConnected.set( false );

                // Have to close the socket first unless we convert to
                // an interruptible datagram socket.
                if ( parent.mSocket != null )
                {
                    logger.debug( "Closing MulticastSocket." );
                    parent.mSocket.close();
                    logger.debug( "Done" );

                    parent.mSocket = null;
                }

 				if ( mSender != null ) {
                    logger.debug( "interrupting SenderThread" );
					mSender.interrupt();
                }
				if ( mReceiver != null ) {
                    logger.debug( "interrupting ReceiverThread" );
					mReceiver.interrupt();
                }

                // We need to wait here until the threads have stopped.
                logger.debug( "calling join() on SenderThread" );
                mSender.join();
                logger.debug( "calling join() on ReceiverThread" );
                mReceiver.join();

                parent.mSender = null;
                parent.mReceiver = null;

                logger.debug( "resetting SenderQueue" );
                mSenderQueue.reset();

                setIsAuthorized( false );

                parent.setSecurityObject( null );
            }
            catch ( Exception e )
            {
                logger.error( "Caught Exception {}", e.getStackTrace() );
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
        public SenderQueue( MulticastChannel iChannel )
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
            logger.info( "putFromDistributor() in ChannelQueue size={}", mDistQueue.size() );
            try {
            	PLogger.QUEUE_CHANNEL_MC_ENTER.trace("offer msg: {}", iMessage);
				if (! mDistQueue.offer( iMessage, 1, TimeUnit.SECONDS )) {
				    logger.warn("multicast channel not taking messages {} {}", 
				    		DisposalState.BUSY, mDistQueue.size() );
				    return DisposalState.BUSY;
				}
			} catch (InterruptedException e) {
				return DisposalState.REJECTED;
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


        public synchronized AmmoGatewayMessage take() throws InterruptedException
        {
            logger.info( "taking from SenderQueue - queue size: {}", mDistQueue.size() );
            if ( mChannel.getIsAuthorized() )
            {
                // This is where the authorized SenderThread blocks.
            	final AmmoGatewayMessage msg = mDistQueue.take();
                PLogger.QUEUE_CHANNEL_MC_EXIT.trace("take msg: {}", msg);
                return msg;
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
                    logger.trace( "wait()ing in SenderQueue" );
                    wait(); // This is where the SenderThread blocks.

                    if ( mChannel.getIsAuthorized() )
                    {
                    	final AmmoGatewayMessage msg = mDistQueue.take();
                        PLogger.QUEUE_CHANNEL_MC_EXIT.trace("take msg: {}", msg);
                        return msg;
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
            logger.warn( "reset()ing the SenderQueue" );
            // Tell the distributor that we couldn't send these
            // packets.
            AmmoGatewayMessage msg = mDistQueue.poll();
            while ( msg != null )
            {
                if ( msg.handler != null )
                    mChannel.ackToHandler( msg.handler, DisposalState.REJECTED );
                msg = mDistQueue.poll();
            }

            setIsAuthorized( false );
        }


        private BlockingQueue<AmmoGatewayMessage> mDistQueue;
        private LinkedList<AmmoGatewayMessage> mAuthQueue;
        private MulticastChannel mChannel;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    class SenderThread extends Thread
    {
        public SenderThread( ConnectorThread iParent,
                             MulticastChannel iChannel,
                             SenderQueue iQueue,
                             MulticastSocket iSocket )
        {
            mParent = iParent;
            mChannel = iChannel;
            mQueue = iQueue;
            mSocket = iSocket;
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

            while ( mState != INetChannel.INTERRUPTED )
            {
                AmmoGatewayMessage msg = null;
                try
                {
                    setSenderState( INetChannel.TAKING );
                    msg = mQueue.take(); // The main blocking call

                    // logger.debug( "Took a message from the send queue size{}, msgsize{}", mQueue.size(), msg.size );
                }
                catch ( InterruptedException ex )
                {
                    logger.error( "interrupted taking messages from send queue: {}",
                                  ex.getLocalizedMessage() );
                    setSenderState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                    break;
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                    logger.error("sender threw exception while take()ing {}", e.getStackTrace());
                    setSenderState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                    break;
                }

                try
                {
                    ByteBuffer buf = msg.serialize( endian, AmmoGatewayMessage.VERSION_1_FULL, (byte)0);
                    setSenderState( INetChannel.SENDING );

                    DatagramPacket packet =
                        new DatagramPacket( buf.array(),
                                            buf.remaining(),
                                            mChannel.mMulticastGroup,
                                            mChannel.mMulticastPort );
                    logger.debug( "Sending datagram packet. length={}", packet.getLength() );

                    logger.debug( "...{}", buf.array() );
                    logger.debug( "...{}", buf.remaining() );
                    logger.debug( "...{}", mChannel.mMulticastGroup );
                    logger.debug( "...{}", mChannel.mMulticastPort );

                    mSocket.setTimeToLive( mChannel.mMulticastTTL.get() );
                    mSocket.send( packet );

                    logger.info( "Send packet to Network: size({})", packet.getLength() );

                    // legitimately sent to gateway.
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, DisposalState.SENT );
                }
                catch ( SocketException ex )
                {
                    logger.debug( "sender caught SocketException" );
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, DisposalState.REJECTED );
                    setSenderState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                    break;
                }
                catch ( Exception e )
                {
                    logger.warn("sender threw exception {}", e.getStackTrace() );
                    if ( msg.handler != null )
                        mChannel.ackToHandler( msg.handler, DisposalState.BAD );
                    setSenderState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                    break;
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
        private MulticastChannel mChannel;
        private SenderQueue mQueue;
        private MulticastSocket mSocket;
        private final Logger logger = LoggerFactory.getLogger( "net.mcast.sender" );
    }


    ///////////////////////////////////////////////////////////////////////////
    //
     class ReceiverThread extends Thread
    {
        public ReceiverThread( ConnectorThread iParent,
                               MulticastChannel iDestination,
                               MulticastSocket iSocket )
        {
            mParent = iParent;
            mDestination = iDestination;
            mSocket = iSocket;
        }

        @Override
        public void run()
        {
            logger.trace( "Thread <{}>::run()", Thread.currentThread().getId() );

            // Block on reading from the MulticastSocket until we get some data.
            // If we get an error, notify our parent and go into an error state.

            // This code assumes that each datagram contained exactly one message.
            // If this needs to change in the future, this code will need to be
            // revised.

            List<InetAddress> addresses = getLocalIpAddresses();

            byte[] raw = new byte[100000]; // FIXME: What is max datagram size?
            while ( getReceiverState() != INetChannel.INTERRUPTED )
            {
                try
                {
                    DatagramPacket packet = new DatagramPacket( raw, raw.length );
                    logger.debug( "Calling receive() on the MulticastSocket." );

                    setReceiverState( INetChannel.START );

                    mSocket.receive( packet );
                    if ( addresses.contains( packet.getAddress() ))
                    {
                        logger.debug( "Discarding packet from self." );
                        continue;
                    }
                    logger.info( "Received a packet from ({}) size({})", packet.getAddress(), packet.getLength()  );

                    ByteBuffer buf = ByteBuffer.wrap( packet.getData(),
                                                      packet.getOffset(),
                                                      packet.getLength() );
                    buf.order( endian );

                    // wrap() creates a buffer that is ready to be drained,
                    // so there is no need to flip() it.
                    AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.extractHeader( buf );

                    if ( agmb == null )
                    {
                        logger.error( "Deserialization failure. Discarded invalid packet." );
                        continue;
                    }

                    // extract the payload
                    byte[] payload = new byte[agmb.size()];
                    buf.get( payload, 0, buf.remaining() );

                    final AmmoGatewayMessage agm = agmb
                    		.payload( payload )
                    		.channel(this.mDestination)
                    		.build();
                    
                    setReceiverState( INetChannel.DELIVER );
                    mDestination.deliverMessage( agm );
                    logger.trace( "received a message {}", payload.length );
                }
                catch ( ClosedChannelException ex )
                {
                    logger.warn( "receiver threw ClosedChannelException {}", ex.getStackTrace() );
                    setReceiverState( INetChannel.INTERRUPTED );
                    mParent.socketOperationFailed();
                }
                catch ( Exception ex )
                {
                    logger.warn( "receiver threw exception {}", ex.getStackTrace() );
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

        private int mState = INetChannel.TAKING; // fixme
        private ConnectorThread mParent;
        private MulticastChannel mDestination;
        private MulticastSocket mSocket;
        private final Logger logger
            = LoggerFactory.getLogger( "net.mcast.receiver" );
    }


    // ********** UTILITY METHODS ****************


    // A routine to get all local IP addresses
    //
    public List<InetAddress> getLocalIpAddresses()
    {
        List<InetAddress> addresses = new ArrayList<InetAddress>();
        try
        {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
            {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
                {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    addresses.add( inetAddress );
                    logger.error( "address: {}", inetAddress );
                }
            }
        }
        catch (SocketException ex)
        {
            logger.error( ex.toString());
        }

        return addresses;
    }


	@Override
	public boolean isBusy() {
		return false;
	}


	@Override
	public void init(Context context) {
		// TODO Auto-generated method stub
		
	}

    @Override
	public void toLog(String context) {
    	PLogger.SET_PANTHR_MC.debug("{} {}:{} ", 
				new Object[]{context, mMulticastAddress, mMulticastPort});
	}
}

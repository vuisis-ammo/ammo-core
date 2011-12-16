/**
 *
 */
package edu.vu.isis.ammo.core.network;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelDisposal;


/**
 *
 */
public class SerialChannel extends NetChannel
{
    private static final Logger logger = LoggerFactory.getLogger( "net.serial" );
	
    // Move these to the interface class later.
    public static final int SERIAL_DISABLED        = INetChannel.DISABLED;
    public static final int SERIAL_WAITING_FOR_TTY = INetChannel.LINK_WAIT;
    public static final int SERIAL_CONNECTED       = INetChannel.CONNECTED;


    static {
        System.loadLibrary("serialchan");
    }
    
    

    /**
     *
     */
    public SerialChannel( String theName, IChannelManager iChannelManager )
    {
        super( theName );
        logger.info( "SerialChannel::SerialChannel()" );

        mChannelManager = iChannelManager;

        // The channel is created in the disabled state, so it will
        // not have a Connector thread.
    }


    /**
     *
     */
    public synchronized void enable()
    {
        logger.info( "SerialChannel::enable()" );

        if ( mState.compareAndSet( SERIAL_DISABLED, SERIAL_WAITING_FOR_TTY )) {
            setState( SERIAL_WAITING_FOR_TTY ); // Need this to update the GUI.
            mConnector = new Connector();
            mConnector.start();
        }
        else {
            logger.error( "enable() called on an already enabled channel" );
        }
    }


    /**
     *
     */
    public synchronized void disable()
    {
        logger.info( "SerialChannel::disable()" );

        if ( getState() == SERIAL_DISABLED ) {
            logger.error( "disable() called on an already disabled channel" );
        } else {
            disconnect();
            setState( SERIAL_DISABLED );
        }
    }


    /**
     * Do we even need this? Does it need to be public? Couldn't the
     * NS just call enable/disable in sequence? Ah, reset() might be
     * more explicit about what is going on.
     */
    public synchronized void reset()
    {
        logger.info( "SerialChannel::reset()" );
        disable();
        enable();
    }


    /**
     * Rename this to send() once the merge is done.
     */
    public ChannelDisposal sendRequest( AmmoGatewayMessage message )
    {
        return mSenderQueue.putFromDistributor( message );
    }


    /**
     *
     */
    public boolean isConnected() { return getState() == SERIAL_CONNECTED; }


    // The following methods will require a disconnect and reconnect,
    // because the variables can't changed while running.  They probably aren't
    // that important in the short-term.
    //
    // NOTE: The following two functions are probably not working atm.  We need
    // to make sure that they're synchronized, and deal with disconnecting the
    // channel (to force a reconnect).  This functionality isn't presently
    // needed, but fix this at some point.

    /**
     * FIXME
     */
    public void setDevice( String device )
    {
        logger.info( "Device set to {}", device );
        mDevice = device;
    }


    /**
     * FIXME
     */
    public void setBaudRate( int baudRate )
    {
        logger.info( "Baud rate set to {}", baudRate );
        mBaudRate = baudRate;
    }


    // The following methods can be changed while connected.  The sender and
    // receiver threads always use the current values.
    //

    /**
     *
     */
    public void setSlotNumber( int slotNumber )
    {
        logger.info( "Slot set to {}", slotNumber );
        mSlotNumber.set( slotNumber );
    }

    /**
     *
     */
    public void setRadiosInGroup( int radiosInGroup )
    {
        logger.error( "Radios in group set to {}", radiosInGroup );
        mRadiosInGroup.set( radiosInGroup );
    }


    /**
     *
     */
    public void setSlotDuration( int slotDuration )
    {
        logger.error( "Slot duration set to {}", slotDuration );
        mSlotDuration.set( slotDuration );
    }

    /**
     *
     */
    public void setTransmitDuration( int transmitDuration )
    {
        logger.error( "Transmit duration set to {}", transmitDuration );
        mTransmitDuration.set( transmitDuration );
    }


    /**
     *
     */
    public void setSenderEnabled( boolean enabled )
    {
        logger.error( "Sender enabled set to {}", enabled );
        mSenderEnabled.set( enabled );
    }

    /**
     *
     */
    public void setReceiverEnabled( boolean enabled )
    {
        logger.error( "Receiver enabled set to {}", enabled );
        mReceiverEnabled.set( enabled );
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    // Private classes, methods, and members
    //


    /**
     *
     */
    private class Connector extends Thread
    {
        private final Logger logger = LoggerFactory.getLogger( "net.serial.connector" );

        /**
         *
         */
        public Connector()
        {
            logger.info( "SerialChannel.Connector::Connector()" );
        }


        /**
         *
         */
        @Override
        public void run()
        {
            logger.info( "SerialChannel.Connector::run()",
                         Thread.currentThread().getId() );

            // We might have been disabled before the thread even gets
            // a chance to run, so check that before doing anything.
            if ( isInterrupted() ) {
                logger.info( "SenderThread <{}>::run() was interrupted before run().", Thread.currentThread().getId() );
                return;
            }

            try {
                // The channel is already in the SERIAL_WAITING_FOR_TTY state.
                synchronized ( SerialChannel.this ) {
                    while ( !connect() ) {
                        logger.debug( "Connect failed. Waiting to retry..." );
                        SerialChannel.this.wait( WAIT_TIME );
                    }
                    setState( SERIAL_CONNECTED );
                }
            } catch ( IllegalMonitorStateException e ) {
                logger.error("IllegalMonitorStateException thrown.");
            } catch ( InterruptedException e ) {
                logger.error("Connector interrupted. Exiting.");
                // Do nothing here.  If we were interrupted, we need
                // to catch the exception and exit cleanly.
            } catch ( Exception e ) {
                logger.warn("Connector threw exception {}", e.getStackTrace() );
            }

            mConnector = null;
            logger.info( "Connector <{}>::run() exiting.", Thread.currentThread().getId() );
        }


        /**
         *
         */
        public void terminate()
        {
            logger.info( "SerialChannel.Connector::terminate()" );
            interrupt();
        }


        /**
         *
         */
        private boolean connect()
        {
            logger.info( "SerialChannel.Connector::connect()" );

            // Create the SerialPort.
            if ( mPort != null )
                logger.error( "Tried to create mPort when we already had one." );
            try {
                mPort = new SerialPort( new File(mDevice), mBaudRate );
            } catch ( Exception e ) {
                logger.info( "Connection to serial port failed" );
                mPort = null;
                return false;
            }

            logger.info( "Connection to serial port established " );
            mIsConnected.set( true );

            // Create the security object.  This must be done before
            // the ReceiverThread is created in case we receive a
            // message before the SecurityObject is ready to have it
            // delivered.
            if ( getSecurityObject() != null )
                logger.error( "Tried to create SecurityObject when we already had one." );
            setSecurityObject( new SerialSecurityObject( SerialChannel.this ));

            // Create the sending thread.
            if ( mSender != null )
                logger.error( "Tried to create Sender when we already had one." );
            mSender = new SenderThread();
            setIsAuthorized( true );
            mSender.start();

            // Create the receiving thread.
            if ( mReceiver != null )
                logger.error( "Tried to create Receiver when we already had one." );
            mReceiver = new ReceiverThread();
            mReceiver.start();

            // FIXME: don't pass in the result of buildAuthenticationRequest(). This is
            // just a temporary hack.
            //parent.getSecurityObject().authorize( mChannelManager.buildAuthenticationRequest());

            // HACK: We are currently not using authentication or
            // encryption with the 152s, so just force the
            // authorization so the senderqueue will start sending
            // packets out.
            mSenderQueue.markAsAuthorized();

            return true;
        }
    }


    /**
     *
     */
    private void disconnect()
    {
        logger.info( "SerialChannel::disconnect()" );

        try {
            mIsConnected.set( false );

            if ( mConnector != null )
                mConnector.terminate();
            if ( mSender != null )
                mSender.interrupt();
            if ( mReceiver != null )
                mReceiver.interrupt();

            mSenderQueue.reset();

            if ( mPort != null ) {
                logger.debug( "Closing SerialPort..." );

                // Closing the port doesn't interrupt blocked read()s,
                // so we close the streams first.
                mPort.getInputStream().getChannel().close();
                mPort.getOutputStream().getChannel().close();
                mPort.close();
                logger.debug( "Done" );

                mPort = null;
            }

            setIsAuthorized( false );

            setSecurityObject( null );
            mConnector = null;
            mSender = null;
            mReceiver = null;
        } catch ( Exception e ) {
            logger.error( "Caught exception while closing serial port." );
            // Do this here, too, since if we exited early because
            // of an exception, we want to make sure that we're in
            // an unauthorized state.
            setIsAuthorized( false );
            return;
        }
        logger.debug( "Disconnected successfully." );
    }


    // Called by the sender and receiver when they have an exception on the
    // port.  We only want to call reset() once, so we use an
    // AtomicBoolean to keep track of whether we need to call it.
    /**
     *
     */
    public void ioOperationFailed()
    {
        if ( mIsConnected.compareAndSet( true, false )) {
            logger.error( "I/O operation failed.  Resetting channel." );
            reset();
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    /**
     *
     */
    private class SenderQueue
    {
        /**
         *
         */
        public SenderQueue()
        {
            setIsAuthorized( false );
            mDistQueue = new LinkedBlockingQueue<AmmoGatewayMessage>( 20 );
            mAuthQueue = new LinkedList<AmmoGatewayMessage>();
        }


        // In the new design, aren't we supposed to let the
        // NetworkService know if the outgoing queue is full or not?
        /**
         *
         */
        public ChannelDisposal putFromDistributor( AmmoGatewayMessage iMessage )
        {
            logger.info( "putFromDistributor()" );
            try {
				if (! mDistQueue.offer(iMessage, 1, TimeUnit.SECONDS)) {
					logger.warn("serial channel not taking messages {}", ChannelDisposal.BUSY );
					return ChannelDisposal.BUSY;
				}
			} catch (InterruptedException e) {
				return ChannelDisposal.BAD;
			}
            return ChannelDisposal.QUEUED;
        }


        /**
         *
         */
        public synchronized void putFromSecurityObject( AmmoGatewayMessage iMessage )
        {
            logger.info( "putFromSecurityObject()" );
            mAuthQueue.offer( iMessage );
        }


        /**
         *
         */
        public synchronized void finishedPuttingFromSecurityObject()
        {
            logger.info( "finishedPuttingFromSecurityObject()" );
            notifyAll();
        }


        // This is called when the SecurityObject has successfully
        // authorized the channel.
        /**
         *
         */
        public synchronized void markAsAuthorized()
        {
            logger.info( "Marking channel as authorized" );
            notifyAll();
        }


        /**
         *
         */
        public synchronized boolean messageIsAvailable()
        {
            return mDistQueue.peek() != null;
        }


        /**
         *
         */
        public synchronized AmmoGatewayMessage take() throws InterruptedException
        {
            logger.info( "taking from SenderQueue" );
            if ( getIsAuthorized() ) {
                // This is where the authorized SenderThread blocks.
                return mDistQueue.take();
            } else {
                if ( mAuthQueue.size() > 0 ) {
                    // return the first item in mAuthqueue and remove
                    // it from the queue.
                    return mAuthQueue.remove();
                } else {
                    logger.info( "wait()ing in SenderQueue" );
                    wait(); // This is where the SenderThread blocks.

                    if ( getIsAuthorized() ) {
                        return mDistQueue.take();
                    } else {
                        // We are not yet authorized, so return the
                        // first item in mAuthqueue and remove
                        // it from the queue.
                        return mAuthQueue.remove();
                    }
                }
            }
        }


        // Somehow synchronize this here.
        /**
         *
         */
        public synchronized void reset()
        {
            logger.info( "reset()ing the SenderQueue" );
            // Tell the distributor that we couldn't send these
            // packets.
            AmmoGatewayMessage msg = mDistQueue.poll();
            while ( msg != null )
            {
                if ( msg.handler != null )
                    ackToHandler( msg.handler, ChannelDisposal.PENDING );
                msg = mDistQueue.poll();
            }

            setIsAuthorized( false );
        }


        private BlockingQueue<AmmoGatewayMessage> mDistQueue;
        private LinkedList<AmmoGatewayMessage> mAuthQueue;
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    /**
     *
     */
    private class SenderThread extends Thread
    {
        /**
         *
         */
        public SenderThread()
        {
            logger.info( "SenderThread::SenderThread", Thread.currentThread().getId() );
        }


        /**
         *
         */
        @Override
        public void run()
        {
            logger.info( "SenderThread <{}>::run()", Thread.currentThread().getId() );

            // Sleep until our slot in the round.  If, upon waking, we find that
            // we are in the right slot, check to see if a packet is available
            // to be sent and, if so, send it. Upon getting a serial port error,
            // notify our parent and go into an error state.

            while ( mSenderState.get() != INetChannel.INTERRUPTED ) {
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
                    if ( !mSenderQueue.messageIsAvailable() ) {
                        continue;
                    }
                    msg = mSenderQueue.take(); // Will not block

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
                        FileOutputStream outputStream = mPort.getOutputStream();
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
                        ackToHandler( msg.handler, ChannelDisposal.SENT );
                } catch ( IOException e ) {
                    logger.warn("sender threw exception {}", e.getStackTrace() );
                    if ( msg.handler != null )
                        ackToHandler( msg.handler, ChannelDisposal.REJECTED );
                    setSenderState( INetChannel.INTERRUPTED );
                    ioOperationFailed();
                } catch ( Exception e ) {
                    logger.warn("sender threw exception {}", e.getStackTrace() );
                    if ( msg.handler != null )
                        ackToHandler( msg.handler, ChannelDisposal.BAD );
                    setSenderState( INetChannel.INTERRUPTED );
                    ioOperationFailed();
                }
            }

            logger.info( "SenderThread <{}>::run() exiting.", Thread.currentThread().getId() );
        }


        /**
         *
         */
        private void setSenderState( int state )
        {
            mSenderState.set( state );
            statusChange();
        }

        /**
         *
         */
        public int getSenderState() { return mSenderState.get(); }

        // If we miss our window's start time by more than this amount, we
        // give up until our turn in the next cycle.
        private static final int WINDOW_DURATION = 25;

        private AtomicInteger mSenderState = new AtomicInteger( INetChannel.TAKING );
        private final Logger logger = LoggerFactory.getLogger( "net.serial.sender" );
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    /**
     *
     */
    private class ReceiverThread extends Thread
    {
        /**
         *
         */
        public ReceiverThread()
        {
            logger.info( "ReceiverThread::ReceiverThread()", Thread.currentThread().getId() );
            mInputStream = mPort.getInputStream();
        }


        /**
         *
         */
        @Override
        public void run()
        {
            logger.info( "ReceiverThread <{}>::run()", Thread.currentThread().getId() );

            // Block on reading from the SerialPort until we get some data.
            // If we get an error, notify our parent and go into an error state.

            // NOTE: We found that our reads were less reliable when reading more than
            // one byte at a time using the standard stream and ByteBuffer patterns.
            // Reading one byte at a time in the code below is intentional.

            try {
                final byte first = (byte) 0xef;
                final byte second = (byte) 0xbe;
                final byte third = (byte) 0xed;

                // See note about length=16 below.
                byte[] buf_header = new byte[ 32 ];// AmmoGatewayMessage.HEADER_DATA_LENGTH_TERSE ];
                ByteBuffer header = ByteBuffer.wrap( buf_header );
                header.order( endian );

                int state = 0;
                byte c = 0;
                AmmoGatewayMessage.Builder agmb = null;

                while ( mReceiverState.get() != INetChannel.INTERRUPTED ) {
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
                                deliverMessage( agm );
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
                ioOperationFailed();
            } catch ( Exception ex ) {
                logger.warn( "receiver threw an exception {}", ex.getStackTrace() );
                setReceiverState( INetChannel.INTERRUPTED );
                ioOperationFailed();
            }

            logger.info( "ReceiverThread <{}>::run() exiting.", Thread.currentThread().getId() );
        }


        /**
         *
         */
        private byte readAByte() throws IOException
        {
            //logger.debug( "Calling read() on the SerialPort's InputStream." );
            int val = mInputStream.read();
            if ( val == -1 ) {
                logger.warn( "The serial port returned -1 from read()." );
                throw new IOException();
            }

            // I was trying to make this interruptable, but it didn't
            // work.  Why not?

            // FileChannel fc = mInputStream.getChannel();
            // byte[] buf = new byte[1];
            // ByteBuffer bb = ByteBuffer.wrap( buf );

            // int bytesRead = 0;
            // while ( bytesRead == 0 ) {
            //     logger.debug( "before read()" );
            //     try {
            //         bytesRead = fc.read( bb );
            //     } catch ( Exception e ) {
            //         logger.warn( "Caught an exception from the read" );
            //     }
            //     logger.debug( "after read()" );
            // }

            // if ( bytesRead == -1 ) {
            //     logger.warn( "The serial port returned -1 from read()." );
            //     throw new IOException();
            // }

            // int val = buf[0];
            //logger.debug( "Read: {}", Integer.toHexString(val) );
            return (byte) val;
        }


        /**
         *
         */
        private void setReceiverState( int state )
        {
            mReceiverState.set( state );
            statusChange();
        }

        /**
         *
         */
        public int getReceiverState() { return mReceiverState.get(); }

        private AtomicInteger mReceiverState = new AtomicInteger( INetChannel.TAKING ); // FIXME: better states
        private FileInputStream mInputStream;
        private final Logger logger
            = LoggerFactory.getLogger( "net.serial.receiver" );
    }


    // Called by ReceiverThread to send an incoming message to the
    // appropriate destination.
    /**
     *
     */
    private boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.error( "In deliverMessage()" );

        boolean result;
        if ( mIsAuthorized.get() ) {
            logger.info( " delivering to channel manager" );
            result = mChannelManager.deliver( agm );
        } else {
            logger.info( " delivering to security object" );
            result = getSecurityObject().deliverMessage( agm );
        }
        return result;
    }


    // Called by the SenderThread.
    /**
     *
     */
    private boolean ackToHandler( INetworkService.OnSendMessageHandler handler,
                                  ChannelDisposal status )
    {
        return handler.ack( name, status );
    }


    /**
     *
     */
    private void setSecurityObject( ISecurityObject iSecurityObject )
    {
        mSecurityObject.set( iSecurityObject );
    }


    /**
     *
     */
    private ISecurityObject getSecurityObject()
    {
        return mSecurityObject.get();
    }


    /**
     *
     */
    private void setIsAuthorized( boolean iValue )
    {
        logger.info( "In setIsAuthorized(). value={}", iValue );

        mIsAuthorized.set( iValue );
    }


    /**
     *
     */
    public boolean getIsAuthorized()
    {
        return mIsAuthorized.get();
    }


    /**
     *
     */
    public void authorizationSucceeded( AmmoGatewayMessage agm )
    {
        setIsAuthorized( true );
        mSenderQueue.markAsAuthorized();

        // Tell the NetworkService that we're authorized and have it
        // notify the apps.
        mChannelManager.authorizationSucceeded( this, agm );
    }


    /**
     *
     */
    public void authorizationFailed()
    {
        // Disconnect the channel.
        reset();
    }


    /**
     *
     */
    private void statusChange()
    {
        // FIXME: make a better state than PENDING.  At this point
        // they have *no* state since they don't exist.
        int senderState = (mSender != null) ? mSender.getSenderState() : PENDING;
        int receiverState = (mReceiver != null) ? mReceiver.getReceiverState() : PENDING;

        mChannelManager.statusChange( this,
                                      getState(),
                                      senderState,
                                      receiverState );
    }


    // I made this public to support the hack to get authentication
    // working before Nilabja's code is ready.  Make it private again
    // once his stuff is in.
    public IChannelManager mChannelManager;

    private final AtomicReference<ISecurityObject> mSecurityObject = new AtomicReference<ISecurityObject>();

    private static final int WAIT_TIME = 5 * 1000; // 5 s
    private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;

    private String mDevice;
    private int mBaudRate;

    private AtomicInteger mSlotNumber = new AtomicInteger();
    private AtomicInteger mRadiosInGroup = new AtomicInteger();

    private AtomicInteger mSlotDuration = new AtomicInteger();
    private AtomicInteger mTransmitDuration = new AtomicInteger();

    private AtomicBoolean mSenderEnabled = new AtomicBoolean();
    private AtomicBoolean mReceiverEnabled = new AtomicBoolean();


    private AtomicInteger mState = new AtomicInteger( SERIAL_DISABLED );
    private int getState() { return mState.get(); }
    private void setState( int state )
    {
        // Create a method is NetChannel to convert the numbers to strings.
        logger.info( "changing state from {} to {}",
                     mState,
                     state );
        mState.set( state );
        statusChange();
    }

    private Connector mConnector;
    private SerialPort mPort;
    private AtomicBoolean mIsConnected = new AtomicBoolean( false );

    private AtomicBoolean mIsAuthorized = new AtomicBoolean( false );

    private SenderThread mSender;
    private ReceiverThread mReceiver;

    private SenderQueue mSenderQueue = new SenderQueue();
    
    @Override
	public boolean isBusy() {
    	return false;
	}
    
}

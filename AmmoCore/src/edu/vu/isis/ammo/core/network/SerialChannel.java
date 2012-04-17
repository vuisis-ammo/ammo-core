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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Looper;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;


/**
 *
 */
public class SerialChannel extends NetChannel
{
    static
    {
        System.loadLibrary( "serialchan" );
    }


    // Move these to the interface class later.
    public static final int SERIAL_DISABLED        = INetChannel.DISABLED;
    public static final int SERIAL_WAITING_FOR_TTY = INetChannel.LINK_WAIT;
    public static final int SERIAL_CONNECTED       = INetChannel.CONNECTED;


    /**
     *
     */
    public SerialChannel( String theName,
                          IChannelManager iChannelManager,
                          Context context )
    {
        super( theName );
        logger.trace( "SerialChannel::SerialChannel()" );

        mChannelManager = iChannelManager;
        mContext = context;

        // The channel is created in the disabled state, so it will
        // not have a Connector thread.
    }


    /**
     *
     */
    public void enable()
    {
        logger.trace( "SerialChannel::enable()" );

        mEnabled = true;

		// For now, start without worrying about a link up, till we
		// have a reliable way of knowing that link is up when AMMO
		// starts or restarts.
        start();
    }


    /**
     *
     */
    public void disable()
    {
        logger.trace( "SerialChannel::disable()" );

        mEnabled = false;

        stop();
    }


    /**
     * Should only be called from main thread in response to any intent (so no
     * need for synchonized.
     */
    public void linkUp( String devname )
    {
        logger.error( "SerialChannel::linkUp() old: {}, new: {}", mDevice, devname );

        // Ff device name changed and the channel was running for some
        // reason, we should stop and then start again.
        if ( !devname.equals( mDevice )) {
            mDevice = devname;
            reset();
        }
    }


    /**
     * Should only be called from main thread in response to any intent (so no
     * need for synchonized.
     */
    public void linkDown( String devname )
    {
        logger.error( "SerialChannel::linkDown() old: {}, new: {}", mDevice, devname );

        if ( devname.equals( mDevice )) {
            logger.error( "SerialChannel::linkDown(). Resetting channel." );
            reset();
        }
    }


    /**
     *
     */
    public void reset()
    {
        logger.trace( "SerialChannel::reset()" );

        if ( mIsConnected.compareAndSet( true, false )) {
            logger.error( "I/O operation failed.  Resetting channel." );
            synchronized ( this ) {
                stop();
                if ( mEnabled ) {
                    start();
                }
            }
        }
    }


    /**
     *
     */
    private void start()
    {
        logger.error( "SerialChannel::start()" );
        if ( mState.compareAndSet( SERIAL_DISABLED, SERIAL_WAITING_FOR_TTY )) {
            setState( SERIAL_WAITING_FOR_TTY ); // Need this to update the GUI.
            mConnector = new Connector();
            mConnector.start();
        } else {
            logger.warn( "enable() called on an already enabled channel" );
        }
    }


    /**
     *
     */
    private void stop()
    {
        logger.error( "SerialChannel::stop()" );
        if ( mState.getAndSet(SERIAL_DISABLED) == SERIAL_DISABLED )
            logger.warn( "disable() called on an already disabled channel" );
        else {
            //disconnect();
            if ( mConnector != null) {
                mConnector.interrupt();  // tell connector thread to stop
                mConnector.mLooper.quit();
                // now join the connector thread to make sure it stops
                try {
                    mConnector.join();
                } catch (InterruptedException ex) {
                    logger.warn("Interrupted while trying to join connector thread {}", ex.getStackTrace() );
                }
                mConnector = null;
            }
            //setState( SERIAL_DISABLED );
        }
    }


    /**
     * Rename this to send() once the merge is done.
     */
    public DisposalState sendRequest( AmmoGatewayMessage message )
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
     * FIXME: This call is no longer needed.  Go through the code and remove it
     * and the prefs that correspond to it.
     */
    public void setDevice( String device )
    {
        //logger.trace( "Device set to {}", device );
        //mDevice = device;
    }


    /**
     * FIXME
     */
    public void setBaudRate( int baudRate )
    {
        logger.trace( "Baud rate set to {}", baudRate );
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
        logger.trace( "Slot set to {}", slotNumber );
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


    /**
     *
     */
    public int getMessagesSent() { return mMessagesSent.get(); }


    /**
     *
     */
    public int getMessagesReceived() { return mMessagesReceived.get(); }


    /**
     *
     */
    public int getCorruptMessages() { return mCorruptMessages.get(); }


    /**
     *
     */
    public void receivedCorruptPacket() { mCorruptMessages.getAndIncrement(); }


    /**
     *
     */
    public int getBytesSinceMagic() { return mBytesSinceMagic.get(); }


    /**
     *
     */
    public int getSecondsSinceByteRead() { return mSecondsSinceByteRead.get(); }


    /**
     *
     */
    public int getReceiverSubstate() { return mReceiverSubstate.get(); }


    /**
     *
     */
    @Override
    public boolean isBusy() { return false; }


    /**
     *
     */
    @Override
    public void init(Context context)
    {
        // TODO Auto-generated method stub
    }


    /**
     *
     */
    @Override
    public void toLog( String context )
    {
        PLogger.ipc_panthr_mc_log.debug( "{} {} for {} msec",
                                         new Object[] { context,
                                                        mSlotNumber,
                                                        mSlotDuration } );
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
        private final Logger logger = LoggerFactory.getLogger( "SerialChannel.Connector" );

        protected Looper mLooper;

        /**
         *
         */
        public Connector()
        {
            logger.trace( "SerialChannel.Connector::Connector()" );
        }


        /**
         *
         */
        @Override
        public void run()
        {
            logger.trace( "SerialChannel.Connector::run()",
                         Thread.currentThread().getId() );

            // We might have been disabled before the thread even gets
            // a chance to run, so check that before doing anything.
            if ( isInterrupted() ) {
                logger.trace( "SenderThread <{}>::run() was interrupted before run().",
                        Thread.currentThread().getId() );
                return;
            }

            try {
                // For a thread to receive the NMEA messages and location
                // updates, it must have a looper.  We create one here so that
                // our callback gets called.
                Looper.prepare();
                mLooper = Looper.myLooper();
                // The channel is already in the SERIAL_WAITING_FOR_TTY state.
                synchronized ( SerialChannel.this ) {
                    while ( !this.isInterrupted() ) {
                        if  ( !connect()  ) {
                            logger.debug( "Connect failed. Waiting to retry..." );
                            SerialChannel.this.wait( WAIT_TIME );
                        }
                        else if ( !isDisabled() ) {
                            setState( SERIAL_CONNECTED );
                            break;
                        }
                    }
                }
                if ( !isDisabled() )
                    Looper.loop();
            } catch ( IllegalMonitorStateException e ) {
                logger.error("IllegalMonitorStateException thrown.");
            } catch ( InterruptedException e ) {
                logger.error("Connector interrupted. Exiting.");
                // Do nothing here.  If we were interrupted, we need
                // to catch the exception and exit cleanly.
            } catch ( Exception e ) {
                logger.warn("Connector threw exception {}", e.getStackTrace() );
            }

            // Do we need to call some sort of quit() for the looper here? The
            // docs disagree.
            Looper.myLooper().quit();
            disconnect();

            logger.trace( "Connector <{}>::run() exiting.", Thread.currentThread().getId() );
        }


        /**
         *
         */
        public void terminate()
        {
            logger.trace( "SerialChannel.Connector::terminate()" );
            this.interrupt();
        }


        /**
         *
         */
        private boolean connect()
        {
            logger.trace( "SerialChannel.Connector::connect()" );

            // Create the SerialPort.
            if ( mPort != null )
                logger.error( "Tried to create mPort when we already had one." );
            try {
                mPort = new SerialPort( new File(mDevice), mBaudRate );
            } catch ( Exception e ) {
                logger.trace( "Connection to serial port failed" );
                mPort = null;
                return false;
            }

            logger.trace( "Connection to serial port established " );

            // FIXME: Do better error handling.  If we can't enable Nmea
            // messages, should we close the channel?
            // TBD SKN: Start the NMEA Message after we have made a connection to the serial port
            try {
                if ( !enableNmeaMessages() )
                {
                    logger.error( "Could not enable Nmea messages." );
                    return false;
                }
            } catch ( Exception e ) {
                logger.error( "Exception thrown in enableNmeaMessages() {} \n {}",
                              e,
                              e.getStackTrace());
                logger.trace( "Connection to serial port failed" );
                return false;
            }

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

            mIsConnected.set( true );

            return true;
        }
    }


    /**
     *
     */
    private void disconnect()
    {
        logger.trace( "SerialChannel::disconnect()" );

        try {
            mIsConnected.set( false );

            disableNmeaMessages();

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

            // Need to do a join here
            try {
                if ( mSender != null
                     && Thread.currentThread().getId() != mSender.getId() )
                    mSender.join();
                if ( mReceiver != null
                     && Thread.currentThread().getId() != mReceiver.getId() )
                    mReceiver.join();
            } catch (java.lang.InterruptedException ex ) {
                logger.warn( "disconnect: interrupted exception while waiting for threads to die: {}",
                             ex.getStackTrace() );
            }

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
    private void ioOperationFailed()
    {
        logger.error( "I/O operation failed.  Resetting channel." );
        reset();
    }


    /**
     *
     */
    private boolean enableNmeaMessages()
    {
        //TelephonyManager tManager = (TelephonyManager) mContext.getSystemService( Context.TELEPHONY_SERVICE );

        if ( mLocationManager == null )
            mLocationManager = (LocationManager) mContext.getSystemService( Context.LOCATION_SERVICE );

        if ( !mLocationManager.isProviderEnabled( LocationManager.GPS_PROVIDER )) {
            logger.warn( "GPS is disabled.  Nmea messages will not work." );
        }

        mLocationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER,
                                                 60000,
                                                 0,
                                                 mLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {}

                @Override
                public void onProviderDisabled(String provider) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    switch (status) {
                        // We only care if the location provider goes out of service
                    case LocationProvider.OUT_OF_SERVICE:
                        //mSensorCallback.onSensorUpdate(SensorCallback.GPS, getUnknownLocation());
                        break;
                    default:
                        // Otherwise, ignore the situation
                    }
                }
            } );

        mLocationManager.addNmeaListener( mNmeaListener = new NmeaListener() {
                @Override
                public void onNmeaReceived(long timestamp, String nmea) {
                    if (nmea.indexOf("GPGGA") >= 0) {
                        //logger.error( "Received an NMEA message" );
                        String[] toks = nmea.split(",");
                        if (toks[6].compareTo("1") == 0) {
                            // calendar from callback timestamp
                            Calendar sysCal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
                            sysCal.setTime(new Date(timestamp));

                            // gps hr/min/sec/fracsec
                            String hr = toks[1].substring(0,2);
                            String mm = toks[1].substring(2,4);
                            String ss = toks[1].substring(4,6);
                            String fs = toks[1].substring(7,8);

                            // instantaneous deltas
                            int dHr = sysCal.get(Calendar.HOUR_OF_DAY) - Integer.parseInt(hr);
                            int dMm = sysCal.get(Calendar.MINUTE) - Integer.parseInt(mm);
                            int dSs = sysCal.get(Calendar.SECOND) - Integer.parseInt(ss);
                            int dMs = sysCal.get(Calendar.MILLISECOND) - Integer.parseInt(fs)*100;

                            long delta = ((dHr*60 + dMm)*60 + dSs)*1000 + dMs;

                            // average delta
                            mDelta = (mCount > 0) ? (mCount*mDelta + delta)/(mCount+1)
                                : delta;
                            mCount++;

                            logger.debug( String.valueOf(mDelta) + ",TS,"
                                          + String.valueOf(timestamp) + "," + nmea );
                        }
                    }

                    // every 10 minutes - set time
                    long now = System.currentTimeMillis();
                    if ( (now - mLast) > 600000 ) {
                        // stuff removed
                        mLast = now;
                        mCount = 0;
                        // mDelta = 0;
                    }
                }
            });

        return true;
    }


    /**
     *
     */
    private void disableNmeaMessages()
    {
    if (mLocationManager != null) {
        mLocationManager.removeNmeaListener(mNmeaListener);
        mLocationManager.removeUpdates(mLocationListener);
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
        public DisposalState putFromDistributor( AmmoGatewayMessage iMessage )
        {
            logger.trace( "putFromDistributor()" );
            try {
                if ( !mDistQueue.offer( iMessage, 1, TimeUnit.SECONDS )) {
                    logger.warn( "serial channel not taking messages {}",
                                 DisposalState.BUSY );
					return DisposalState.BUSY;
                }
			} catch ( InterruptedException e ) {
				return DisposalState.BAD;
            }
            return DisposalState.QUEUED;
        }


        /**
         *
         */
        @SuppressWarnings("unused")
        public synchronized void putFromSecurityObject( AmmoGatewayMessage iMessage )
        {
            logger.trace( "putFromSecurityObject()" );
            mAuthQueue.offer( iMessage );
        }


        /**
         *
         */
        @SuppressWarnings("unused")
        public synchronized void finishedPuttingFromSecurityObject()
        {
            logger.trace( "finishedPuttingFromSecurityObject()" );
            notifyAll();
        }


        // This is called when the SecurityObject has successfully
        // authorized the channel.
        /**
         *
         */
        public synchronized void markAsAuthorized()
        {
            logger.trace( "Marking channel as authorized" );
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
       public synchronized AmmoGatewayMessage peek()
       {
           if ( getIsAuthorized() ) {
               return mDistQueue.peek();
           } else {
               return mAuthQueue.peek();
           }
       }


        /**
         *
         */
        public synchronized AmmoGatewayMessage take() throws InterruptedException
        {
            logger.trace( "taking from SenderQueue" );
            if ( getIsAuthorized() ) {
                // This is where the authorized SenderThread blocks.
                return mDistQueue.take();
            } else {
                if ( mAuthQueue.size() > 0 ) {
                    // return the first item in mAuthqueue and remove
                    // it from the queue.
                    return mAuthQueue.remove();
                } else {
                    logger.trace( "wait()ing in SenderQueue" );
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
            logger.trace( "reset()ing the SenderQueue" );
            // Tell the distributor that we couldn't send these
            // packets.
            AmmoGatewayMessage msg = mDistQueue.poll();
            while ( msg != null )
            {
                if ( msg.handler != null )
                    ackToHandler( msg.handler, DisposalState.PENDING );
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
            logger.trace( "SenderThread::SenderThread", Thread.currentThread().getId() );
        }


        /**
         *
         */
        @Override
        public void run()
        {
            logger.trace( "SenderThread <{}>::run()", Thread.currentThread().getId() );

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
                    long currentGpsTime = currentTime - mDelta;

                    int slotDuration = mSlotDuration.get();
                    int offset = mSlotNumber.get() * slotDuration;
                    int cycleDuration = slotDuration * mRadiosInGroup.get();

                    long thisCycleStartTime = (long) (currentGpsTime / cycleDuration) * cycleDuration;
                    long thisCycleTakeTime = thisCycleStartTime + offset;

                    long goalTakeTime;
                    if ( thisCycleTakeTime > currentGpsTime ) {
                        // We haven't yet reached our take time for this cycle,
                        // so that's our goal.
                        goalTakeTime = thisCycleTakeTime;
                    } else {
                        // We've already missed our turn this cycle, so add
                        // cycleDuration and wait until the next round.
                        goalTakeTime = thisCycleTakeTime + cycleDuration;
                    }
                    Thread.sleep( goalTakeTime - currentGpsTime );

                    currentTime = System.currentTimeMillis();
                    currentGpsTime = currentTime - mDelta;

                    // Calculate things here that will remain valid for the
                    // rest of the slot.

                    // slotDuration, offset, and cycleDuration may have changed
                    // while sleeping, but they are not something that will
                    // usually change in the field, and even if they change,
                    // the will pick up the new value the next time through.

                    thisCycleStartTime = (long) (currentGpsTime / cycleDuration) * cycleDuration;
                    thisCycleTakeTime = thisCycleStartTime + offset;


                    long endOfTransmitWindow = thisCycleTakeTime + mTransmitDuration.get();
                    logger.debug( "Woke up: slotNumber={}, (time, mu-s)={}, jitter={}",
                                 new Object[] {
                                      mSlotNumber.get(),
                                      currentGpsTime,
                                      currentGpsTime - goalTakeTime } );

                    while (true) {
                        // Send all available packets until we run out of time
                        // in our slot.
                        currentTime = System.currentTimeMillis();
                        currentGpsTime = currentTime - mDelta;


                        if ( endOfTransmitWindow < currentGpsTime ) {
                            logger.debug( "currentGpsTime={}, endOfTransmitWindow={}", currentGpsTime, endOfTransmitWindow);
                            logger.debug( "Out of time in slot: time remaining={}",
                                          endOfTransmitWindow - currentGpsTime );
                            break;
                        }

                        long timeLeftToTransmit = endOfTransmitWindow - currentGpsTime; // in ms

                        // baudrate == symbols/sec, 1 byte == 10 symbols, 1 sec = 1000msec
                        double bytesPerMs = mBaudRate / (10*1000.0);
                        long bytesThatWillFit = (long) (timeLeftToTransmit * bytesPerMs);

                        long MAX_SEND_PAYLOAD_SIZE = (long) (mTransmitDuration.get() * bytesPerMs);

                        // At this point, we've woken up near the start of our
                        // window and should send a message if one is available.
                        if (!mSenderQueue.messageIsAvailable()) {
                            logger.debug( "Time remaining in slot={}, but no messages in queue",
                                          endOfTransmitWindow - currentGpsTime );
                            break;
                        }
                        AmmoGatewayMessage peekedMsg = mSenderQueue.peek();
                        int peekedMsgLength = peekedMsg.payload.length + AmmoGatewayMessage.HEADER_DATA_LENGTH_TERSE;

                        if ( peekedMsgLength > MAX_SEND_PAYLOAD_SIZE ) {
                            logger.warn( "Rejecting: messageLength={}, maxSize={}",
                                         peekedMsgLength,
                                         MAX_SEND_PAYLOAD_SIZE );
                            // Take the message out of the queue and discard it,
                            // since it's too big to ever send.
                            msg = mSenderQueue.take(); // Will not block
                            if ( msg.handler != null )
                                ackToHandler( msg.handler, DisposalState.BAD );
                            break;
                        }

                        if ( peekedMsgLength > bytesThatWillFit ) {
                            logger.debug( "Holding: messageLength={}, bytesThatWillFit={}",
                                          peekedMsgLength,
                                          bytesThatWillFit );
                            // Leave the message in the queue and try to send it next time.
                            break;
                        }

                        msg = mSenderQueue.take(); // Will not block

                        logger.debug("Took a message from the send queue");
                        try {
                            sendMessage(msg);
                            mMessagesSent.getAndIncrement();
                        } catch ( IOException e ) {
                            logger.warn("sender threw exception {}", e.getStackTrace() );
                            if ( msg.handler != null )
                                ackToHandler( msg.handler, DisposalState.REJECTED );
                            setSenderState( INetChannel.INTERRUPTED );
                            ioOperationFailed();
                        } catch ( Exception e ) {
                            logger.warn("sender threw exception {}", e.getStackTrace() );
                            if ( msg.handler != null )
                                ackToHandler( msg.handler, DisposalState.BAD );
                            setSenderState( INetChannel.INTERRUPTED );
                            ioOperationFailed();
                            break;
                        }
                    }
                } catch ( InterruptedException ex ) {
                    logger.debug( "interrupted taking messages from send queue: {}",
                                  ex.getLocalizedMessage() );
                    setSenderState( INetChannel.INTERRUPTED );
                    break;
                }
            }

            logger.trace( "SenderThread <{}>::run() exiting.", Thread.currentThread().getId() );
        }


        private void sendMessage(AmmoGatewayMessage msg) throws IOException
        {
            ByteBuffer buf = msg.serialize( endian,
                                            AmmoGatewayMessage.VERSION_1_TERSE,
                                            (byte) mSlotNumber.get());

            setSenderState(INetChannel.SENDING);

            if ( mSenderEnabled.get() ) {
                FileOutputStream outputStream = mPort.getOutputStream();
                outputStream.write( buf.array() );
                outputStream.flush();

                logger.trace(
                        "sent message size={}, checksum={}, data:{}",
                        new Object[] { msg.size,
                                Long.toHexString(msg.payload_checksum),
                                msg.payload });
            }

            // legitimately sent to gateway.
            if ( msg.handler != null )
                ackToHandler(msg.handler, DisposalState.SENT);
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

        private AtomicInteger mSenderState = new AtomicInteger( INetChannel.TAKING );
        private final Logger logger = LoggerFactory.getLogger( "s.s" );
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
            logger.trace( "ReceiverThread::ReceiverThread()", Thread.currentThread().getId() );
            mInputStream = mPort.getInputStream();
        }


        /**
         *
         */
        @Override
        public void run()
        {
            logger.trace( "ReceiverThread <{}>::run()", Thread.currentThread().getId() );

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
                        if ( c == first ) {
                            state = c;
                            mReceiverSubstate.set( 11 );
                        }
                        break;

                    case first:
                        c = readAByte();
                        if ( c == first ) {
                            state = c;
                            mReceiverSubstate.set( 11 );
                        } else if ( c == second ) {
                            state = c;
                            mReceiverSubstate.set( 12 );
                        } else {
                            state = 0;
                            mReceiverSubstate.set( state );
                        }
                        break;

                    case second:
                        c = readAByte();
                        if ( c == third ) {
                            state = 1;
                            mReceiverSubstate.set( state );
                        } else if ( c == first ) {
                            state = c;
                            mReceiverSubstate.set( 11 );
                        } else {
                            state = 0;
                            mReceiverSubstate.set( state );
                        }
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

                            mBytesSinceMagic.set( 0 );

                            header.clear();

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
                                mCorruptMessages.getAndIncrement();
                                state = 0;
                            } else {
                                state = 2;
                            }
                            mReceiverSubstate.set( state );
                        }
                        break;

                    case 2:
                        {
                            int payload_size = agmb.size();
                            if ( payload_size > MAX_RECEIVE_PAYLOAD_SIZE ) {
                                logger.warn( "Discarding packet of size {}. Maximum payload size exceeded.",
                                             payload_size );
                                state = 0;
                                mReceiverSubstate.set( state );
                                break;
                            }
                            byte[] buf_payload = new byte[ payload_size ];

                            for ( int i = 0; i < payload_size; ++i ) {
                                c = readAByte();
                                buf_payload[i] = c;
                            }

                            agmb.isSerialChannel( true );
                            AmmoGatewayMessage agm = agmb.payload( buf_payload ).build();

                            long currentTime = System.currentTimeMillis();
                            int slotDuration = mSlotDuration.get();
                            int cycleDuration = slotDuration * mRadiosInGroup.get();

                            long thisCycleStartTime = (long) (currentTime / cycleDuration) * cycleDuration;
                            long currentSlot = (currentTime - thisCycleStartTime) / slotDuration;

                            logger.debug( "Finished reading payload in slot {} at {}",
                                          currentSlot,
                                          currentTime );
                            mMessagesReceived.getAndIncrement();
                            logger.trace( "received message size={}, checksum={}, data:{}",
                                         new Object[] {
                                             agm.size,
                                             Long.toHexString(agm.payload_checksum),
                                             agm.payload } );

                            if ( mReceiverEnabled.get() ) {
                                setReceiverState( INetChannel.DELIVER );
                                deliverMessage( agm );
                            } else {
                                logger.trace( "Receiving disabled, discarding message." );
                            }

                            header.clear();
                            setReceiverState( INetChannel.START );
                            state = 0;
                            mReceiverSubstate.set( state );
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

            logger.trace( "ReceiverThread <{}>::run() exiting.", Thread.currentThread().getId() );
        }


        /**
         *
         */
        private byte readAByte() throws IOException
        {
            int val = -1;
            mSecondsSinceByteRead.set( 0 );
            while ( val == -1 &&  mReceiverState.get() != INetChannel.INTERRUPTED ) {
                logger.debug( "SerialPort.read()" );
                val = mInputStream.read();
                if ( val == -1 )
                    mSecondsSinceByteRead.getAndIncrement();
            }

            logger.warn( "val={}", (byte) val );
            mBytesSinceMagic.getAndIncrement();

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
            = LoggerFactory.getLogger( "s.r" );
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
            logger.trace( " delivering to channel manager" );
            result = mChannelManager.deliver( agm );
        } else {
            logger.trace( " delivering to security object" );
            result = getSecurityObject().deliverMessage( agm );
        }
        return result;
    }


    // Called by the SenderThread.
    /**
     *
     */
    private boolean ackToHandler( INetworkService.OnSendMessageHandler handler,
                                  DisposalState status )
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
        logger.trace( "In setIsAuthorized(). value={}", iValue );

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

    private Context mContext;

    private final AtomicReference<ISecurityObject> mSecurityObject = new AtomicReference<ISecurityObject>();

    private static final int WAIT_TIME = 5 * 1000; // 5 s
    private static final int MAX_RECEIVE_PAYLOAD_SIZE = 2000; // Should this be set based on baud and slot duration?
    private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;

    private boolean mEnabled = false;

    private String mDevice = "/dev/ttyUSB0";
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
        logger.trace( "changing state from {} to {}",
                     mState,
                     state );
        mState.set( state );
        statusChange();
    }
    private boolean isDisabled() { return (getState() == SERIAL_DISABLED); }

    private Connector mConnector;
    private SerialPort mPort;
    private AtomicBoolean mIsConnected = new AtomicBoolean( false );

    private AtomicBoolean mIsAuthorized = new AtomicBoolean( false );

    private SenderThread mSender;
    private ReceiverThread mReceiver;

    private SenderQueue mSenderQueue = new SenderQueue();

    private long mDelta = 0;
    private long mCount = 0;
    private long mLast = 0;

    private final AtomicInteger mReceiverSubstate = new AtomicInteger( 0 );

    private final AtomicInteger mMessagesSent = new AtomicInteger();
    private final AtomicInteger mMessagesReceived = new AtomicInteger();
    private final AtomicInteger mCorruptMessages = new AtomicInteger();
    private final AtomicInteger mBytesSinceMagic = new AtomicInteger();
    private final AtomicInteger mSecondsSinceByteRead = new AtomicInteger();

    private LocationManager mLocationManager;
    private NmeaListener mNmeaListener;
    private LocationListener mLocationListener;

    private static final Logger logger = LoggerFactory.getLogger( "SerialChannel" );
}

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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import android.os.Process;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;


/**
 *
 */
public class SerialChannel extends NetChannel
{
    static
    {
        System.loadLibrary( "ammocore" );
    }


    // Move these to the interface class later.
    public static final int SERIAL_DISABLED        = INetChannel.DISABLED;
    public static final int SERIAL_WAITING_FOR_TTY = INetChannel.LINK_WAIT;
    public static final int SERIAL_CONNECTED       = INetChannel.CONNECTED;
    public static final int SERIAL_BUSY            = INetChannel.BUSY;


    /**
     *
     */
    public SerialChannel( String theName,
                          IChannelManager iChannelManager,
                          Context context )
    {
        super( theName );
        logger.debug( "SerialChannel::SerialChannel()" );

        mChannelManager = iChannelManager;
        mContext = context;

        // The channel is created in the disabled state, so it will
        // not have a Connector thread.

        // Set up timer to trigger once per minute.
        TimerTask updateBps = new UpdateBpsTask();
        mUpdateBpsTimer.scheduleAtFixedRate( updateBps, 0, BPS_STATS_UPDATE_INTERVAL * 1000 );
    }

    private Timer mUpdateBpsTimer = new Timer();
    private int mMaxMessageSize = 0x100000;

    class UpdateBpsTask extends TimerTask {
        public void run() {
            logger.trace( "UpdateBpsTask fired" );

            // Update the BPS stats for the sending and receiving.
            mBpsSent = (mBytesSent - mLastBytesSent) / BPS_STATS_UPDATE_INTERVAL;
            mLastBytesSent = mBytesSent;

            mBpsRead = (mBytesRead - mLastBytesRead) / BPS_STATS_UPDATE_INTERVAL;
            mLastBytesRead = mBytesRead;
        }
    };


    /**
     *
     */
    public void enable()
    {
        logger.info( "SerialChannel::enable()" );

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
        logger.info( "SerialChannel::disable()" );

        mEnabled = false;

        stop();
        setState( SERIAL_DISABLED );
    }


    /**
     * Should only be called from main thread in response to any intent (so no
     * need for synchronized.
     */
    @Override
    public void linkUp( String devname )
    {
        logger.debug( "SerialChannel::linkUp() old: {}, new: {}", mDevice, devname );

        // Ff device name changed and the channel was running for some
        // reason, we should stop and then start again.
        if ( !devname.equals( mDevice )) {
            mDevice = devname;
            reset();
        }
    }


    /**
     * Should only be called from main thread in response to any intent (so no
     * need for synchronized.
     */
    @Override
    public void linkDown( String devname )
    {
        logger.debug( "SerialChannel::linkDown() old: {}, new: {}", mDevice, devname );

        if ( devname.equals( mDevice )) {
            logger.debug( "SerialChannel::linkDown(). Resetting channel." );
            reset();
        }
    }

    public void systemTimeChange( )
    {
        // System Time Change
        logger.debug("Handling Time Change caused by Sync svc, by resetting delta...");
        mCount = 0;
        for(int i=0; i<numSamples; i++) mDeltaSamples[i] = 0;
    }


    /**
     *
     */
    public void reset()
    {
        logger.debug( "SerialChannel::reset()" );

        if ( mIsConnected.compareAndSet( true, false )) {
            logger.warn( "I/O operation failed.  Resetting channel." );
            stop();     // this was wrapped in synchronized, took that out to prevent deadlock
            if ( mEnabled ) {
                start();
            }
        }
    }


    /**
     *
     */
    private void start()
    {
        logger.debug( "SerialChannel::start()" );
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
        logger.debug( "SerialChannel::stop()" );
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
                    logger.warn("Interrupted while trying to join connector thread" );
                }
                mConnector = null;
            }
            //setState( SERIAL_DISABLED );
        }
    }


    /**
     *
     */
    public DisposalState sendRequest( AmmoGatewayMessage message )
    {
        logger.debug( "sendRequest()" );
        // There is a chance that the Distributor could try to send a
        // message while the channel is going down, and we don't want
        // the message to be stranded in the queue until the channel
        // comes back up.  Reject the packet in these cases.
        if ( getState() != SERIAL_CONNECTED ) {
            logger.debug( "...1" );
            return DisposalState.REJECTED;
        }

        synchronized ( mFragmenter ) {
            SerialFragmenter f = getFragmenter();
            if ( f != null ) {
                logger.debug( "...2" );
                return f.putFromDistributor( message );
            } else {
                logger.debug( "...3" );
                return mSenderQueue.putFromDistributor( message );
            }
        }
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
     *
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

        // The UI shows slot numbers 1-based to be user friendly.  Internally,
        // all slot numbers are 0-based.
        mSlotNumber.set( slotNumber - 1 );
    }

    /**
     *
     */
    public void setRadiosInGroup( int radiosInGroup )
    {
        logger.info( "Radios in group set to {}", radiosInGroup );
        mRadiosInGroup.set( radiosInGroup );
    }


    /**
     *
     */
    public void setSlotDuration( int slotDuration )
    {
        logger.info( "Slot duration set to {}", slotDuration );
        mSlotDuration.set( slotDuration );
    }

    /**
     *
     */
    public void setTransmitDuration( int transmitDuration )
    {
        logger.info( "Transmit duration set to {}", transmitDuration );
        mTransmitDuration.set( transmitDuration );
    }


    /**
     *
     */
    public void setSenderEnabled( boolean enabled )
    {
        logger.info( "Sender enabled set to {}", enabled );
        mSenderEnabled.set( enabled );
    }

    /**
     *
     */
    public boolean setMaxMsgSize (int size) {
      logger.trace("Thread <{}>::setMaxMsgSize {}", Thread.currentThread().getId(), size);
      if (mMaxMessageSize   == (size*0x100000)) return false;
      this.mMaxMessageSize = size*0x100000;
      this.reset();
      return true;
    }

    /**
     * Sets whether SATCOM functionality is enabled/disabled.
     * Will not take effect until the channel is disabled and then reenabled.
     */
    public void setSatcomEnabled( boolean enabled )
    {
        logger.info( "SATCOM enabled set to {}", enabled );
        mSatcomEnabled = enabled;
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
        PLogger.SET_PANTHR_MC.debug( "{} {} for {} msec",
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
        private final Logger logger = LoggerFactory.getLogger( "net.serial.connector" );

        protected Looper mLooper;

        /**
         *
         */
        public Connector()
        {
            super(new StringBuilder("Serial-Connector-").append(Thread.activeCount()).toString());
            logger.debug( "SerialChannel.Connector::Connector()" );
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
                logger.debug( "SenderThread <{}>::run() was interrupted before run().",
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
                logger.warn( "IllegalMonitorStateException thrown", e );
            } catch ( InterruptedException e ) {
                logger.warn( "Connector interrupted. Exiting", e );
                // Do nothing here.  If we were interrupted, we need
                // to catch the exception and exit cleanly.
            } catch ( Exception e ) {
                logger.warn("Connector threw exception", e );
            }

            // Do we need to call some sort of quit() for the looper here? The
            // docs disagree.
            Looper.myLooper().quit();
            disconnect();

            logger.info( "Connector <{}>::run() exiting.", Thread.currentThread().getId() );
        }


        /**
         * I'm not sure this is used anymore, if it ever was.
         */
        // @SuppressWarnings("unused")
		// public void terminate()
        // {
        //     logger.debug( "SerialChannel.Connector::terminate()" );
        //     this.interrupt();
        // }


        // Because of the permissions on the Notes's dev directory,
        // the standard code using listFiles() doesn't work.  This
        // code below is a kluge, but it will work unless TTY's don't
        // get freed up the normal way, for some reason, and in that
        // case we have bigger problems than this function failing.
        private List findTTYs() {
            String[] names = { "/dev/ttyUSB0",
                               "/dev/ttyUSB1",
                               "/dev/ttyUSB2",
                               "/dev/ttyUSB3",
                               "/dev/ttyUSB4",
                               "/dev/ttyUSB5",
                               "/dev/ttyUSB6",
                               "/dev/ttyUSB7",
                               "/dev/ttyUSB8",
                               "/dev/ttyUSB9" };
            
            List<String> result = new ArrayList<String>();
            for ( String n : names ) {
                File f = new File( n );
                if ( f.exists() ) {
                    logger.error( f.getName() + " exists" );
                    result.add( n );
                }
            }
            
            return result;
        }


        /**
         *
         */
        private boolean connect()
        {
            logger.info( "SerialChannel.Connector::connect()" );

            // With the new setup with control and data connections,
            // there will be multiple TTYs present, so we need to make
            // a list and test CTS for each until we find the right
            // one.
            List<String> ttys = findTTYs();

            String deviceToUse = "";
            for ( String pathname : ttys ) {
                boolean x = SerialPort.isCorrectTTY( pathname );
                logger.error( "{} isCorrectTTY={}", pathname, x );
                if ( x ) {
                    deviceToUse = pathname;
                    break;
                }
            }

            // Now use the one we found instead of mDevice below.
            if ( deviceToUse.equals( "" )) {
                logger.info( "No appropriate TTY found" );
                return false;
            }

            // Create the SerialPort.
            if ( mPort != null )
                logger.warn( "Tried to create mPort when we already had one." );
            try {
                mPort = new SerialPort( new File(deviceToUse), mBaudRate );
            } catch ( Exception e ) {
                logger.warn( "Connection to serial port failed" );
                mPort = null;
                return false;
            }

            logger.info( "Connection to serial port established " );

            // FIXME: Do better error handling.  If we can't enable Nmea
            // messages, should we close the channel?
            try {
                if ( !enableNmeaMessages() )
                {
                    logger.warn( "Could not enable Nmea messages." );
                    return false;
                }
            } catch ( Exception e ) {
                logger.warn( "Exception thrown in enableNmeaMessages()", e );
                logger.warn( "Connection to serial port failed" );
                return false;
            }

            // Create the security object.  This must be done before
            // the ReceiverThread is created in case we receive a
            // message before the SecurityObject is ready to have it
            // delivered.
            if ( getSecurityObject() != null )
                logger.warn( "Tried to create SecurityObject when we already had one." );
            setSecurityObject( new SerialSecurityObject( SerialChannel.this ));

            // If SATCOM functionality is enabled, create the SerialFragmenter and
            // no retransmitter.  Otherwise, create the retransmitter but no fragmenter.
            if ( mSatcomEnabled ) {
                logger.debug( "SATCOM enabled. Creating SATCOM sender and receiver threads" );
                if ( getFragmenter() != null )
                    logger.warn( "Tried to create SerialFragmenter when we already had one." );
                setFragmenter( new SerialFragmenter( SerialChannel.this, mChannelManager ));

                // Create the sending thread.
                if ( mSatcomSender != null )
                    logger.warn( "Tried to create SatcomSender when we already had one." );
                mSatcomSender = new SatcomSenderThread();
                setIsAuthorized( true );
                mSatcomSender.start();

                // Create the receiving thread.
                if ( mSatcomReceiver != null )
                    logger.warn( "Tried to create SatcomReceiver when we already had one." );
                mSatcomReceiver = new SatcomReceiverThread();
                mSatcomReceiver.start();

                getFragmenter().startSending();
            } else {
                if ( getRetransmitter() != null )
                    logger.warn( "Tried to create SerialRetransmitter when we already had one." );
                setRetransmitter( new SerialRetransmitter( SerialChannel.this, mChannelManager,
                                                           mSlotNumber.get() ));
                // Create the sending thread.
                if ( mSender != null )
                    logger.warn( "Tried to create Sender when we already had one." );
                mSender = new SenderThread();
                setIsAuthorized( true );
                mSender.start();

                // Create the receiving thread.
                if ( mReceiver != null )
                    logger.warn( "Tried to create Receiver when we already had one." );
                mReceiver = new ReceiverThread();
                mReceiver.start();
            }


            // FIXME: don't pass in the result of buildAuthenticationRequest(). This is
            // just a temporary hack.
            //parent.getSecurityObject().authorize( mChannelManager.buildAuthenticationRequest());

            // HACK: We are currently not using authentication or
            // encryption with the 152s, so just force the
            // authorization so the senderqueue will start sending
            // packets out.
            mSenderQueue.markAsAuthorized();

            mIsConnected.set( true );
            mBytesSent = 0;
            mBytesRead = 0;
            mLastBytesSent = 0;
            mLastBytesRead = 0;
            mBpsSent = 0;
            mBpsRead = 0;

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

            disableNmeaMessages();

            if ( mSender != null )
                mSender.interrupt();
            if ( mReceiver != null )
                mReceiver.interrupt();
            if ( mSatcomSender != null )
                mSatcomSender.interrupt();
            if ( mSatcomReceiver != null )
                mSatcomReceiver.interrupt();

            mSenderQueue.reset();

            if ( mPort != null ) {
                logger.info( "Closing SerialPort..." );

                // Closing the port doesn't interrupt blocked read()s,
                // so we close the streams first.
                mPort.getInputStream().getChannel().close();
                mPort.getOutputStream().getChannel().close();
                mPort.close();
                logger.info( "Done" );

                mPort = null;
            }

            setIsAuthorized( false );

            setSecurityObject( null );
            setRetransmitter( null );
            setFragmenter( null );

            // We've interrupted the threads, so wait for them to finish here
            // before continuing.
            try {
                if ( mSatcomEnabled ) {
                    if ( mSatcomSender != null
                         && Thread.currentThread().getId() != mSatcomSender.getId() )
                        mSatcomSender.join();
                    if ( mSatcomReceiver != null
                         && Thread.currentThread().getId() != mSatcomReceiver.getId() )
                        mSatcomReceiver.join();
                } else {
                    if ( mSender != null
                         && Thread.currentThread().getId() != mSender.getId() )
                        mSender.join();
                    if ( mReceiver != null
                         && Thread.currentThread().getId() != mReceiver.getId() )
                        mReceiver.join();
                }
            } catch ( java.lang.InterruptedException ex ) {
                logger.warn( "disconnect: interrupted exception while waiting for threads to die",
                             ex );
            }

            mSender = null;
            mReceiver = null;
            mSatcomSender = null;
            mSatcomReceiver = null;
        } catch ( Exception e ) {
            logger.warn( "Caught exception while closing serial port", e );
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
        logger.warn( "I/O operation failed.  Resetting channel." );
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

        for(int i=0; i<numSamples; i++) mDeltaSamples[i] = 0;
        mLocationManager.addNmeaListener( mNmeaListener = new NmeaListener() {
                @Override
                public void onNmeaReceived(long timestamp, String nmea) {
                    if (nmea.indexOf("GPGGA") >= 0) {
                        //logger.trace( "Received an NMEA message" );
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

                            // IIR Filter
                            // mDelta = (mCount*mDelta + delta)/(mCount+1);

                            // FIR Filter
                            // I'm commenting out the following line, since this
                            // looks like a race condition.  If another thread
                            // reads mDelta while it is 0 and in the process of
                            // being recalculated, this will give a momentary
                            // erroneous value.
                            //mDelta.set( 0 );
                            long accumulator = 0;
                            for (long d : mDeltaSamples)
                                accumulator += d;
                            accumulator = accumulator / Math.min( mCount + 1, numSamples );
                            mDelta.set( accumulator );

                            // Store samples
                            mDeltaSamples[ mCount % numSamples ] = delta;
                            mCount++;

                            logger.trace( "{},TS,{},{}",
                                   new Object[] {mDelta, timestamp, nmea });
                        }
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
            mDistQueue = new LinkedBlockingQueue<AmmoGatewayMessage>( SENDQUEUE_MAX_SIZE );
            mAuthQueue = new LinkedList<AmmoGatewayMessage>();
        }


        // In the new design, aren't we supposed to let the
        // NetworkService know if the outgoing queue is full or not?
        /**
         *
         */
        public DisposalState putFromDistributor( AmmoGatewayMessage iMessage )
        {
            logger.debug( "putFromDistributor()" );

            try {
                if ( !mDistQueue.offer( iMessage, 1, TimeUnit.SECONDS )) {
                    logger.debug( "serial channel not taking messages {}",
                                 DisposalState.BUSY );
                    return DisposalState.BUSY;
                }
            } catch ( InterruptedException e ) {
                return DisposalState.BAD;
            }

            synchronized ( mDistQueueSize ) {
                ++mDistQueueSize;
                logger.trace( "Incrementing mDistQueueSize to {}", mDistQueueSize );
                if ( mDistQueueSize == SENDQUEUE_MAX_SIZE ) {
                    mWasBusy = true;
                    setState( SERIAL_BUSY );
                }
            }

            return DisposalState.QUEUED;
        }


        /**
         *
         */
        @SuppressWarnings("unused")
        public synchronized void putFromSecurityObject( AmmoGatewayMessage iMessage )
        {
            logger.debug( "putFromSecurityObject()" );
            mAuthQueue.offer( iMessage );
        }


        /**
         *
         */
        @SuppressWarnings("unused")
        public synchronized void finishedPuttingFromSecurityObject()
        {
            logger.debug( "finishedPuttingFromSecurityObject()" );
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
            logger.debug( "taking from SenderQueue" );
            if ( getIsAuthorized() ) {
                // This is where the authorized SenderThread blocks.
                return mDistQueue.take();
            } else {
                if ( mAuthQueue.size() > 0 ) {
                    // return the first item in mAuthqueue and remove
                    // it from the queue.
                    return mAuthQueue.remove();
                } else {
                    logger.debug( "wait()ing in SenderQueue" );
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
            logger.debug( "reset()ing the SenderQueue" );
            // Tell the distributor that we couldn't send these
            // packets.
            AmmoGatewayMessage msg = mDistQueue.poll();
            while ( msg != null )
            {
                if ( msg.handler != null )
                    ackToHandler( msg.handler, DisposalState.PENDING );
                msg = mDistQueue.poll();
            }

            mDistQueueSize = 0;
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
            logger.debug( "SenderThread::SenderThread", Thread.currentThread().getId() );
        }


        /**
         *
         */
        @Override
        public void run()
        {
            // this is a jitter sensitive thread, it should run on real-time-priority
            Process.setThreadPriority( -8 );
            logger.debug( "SenderThread <{}>::run() @prio: {}",
                          Thread.currentThread().getId(),
                          Process.getThreadPriority( Process.myTid() ));

            // Sleep until our slot in the round.  If, upon waking, we find that
            // we are in the right slot, check to see if a packet is available
            // to be sent and, if so, send it. Upon getting a serial port error,
            // notify our parent and go into an error state.

            // CONSTANTS
            final int slotIndex = mSlotNumber.get();
            final int slotDuration = mSlotDuration.get();
            final int offset = (slotIndex % mRadiosInGroup.get()) * slotDuration;
            final int cycleDuration = slotDuration * mRadiosInGroup.get();
            final double bytesPerMs = mBaudRate / (10*1000.0); // baudrate == symbols/sec,
                                                               // 1 byte == 10 symbols,
                                                               // 1 sec = 1000msec

            // We have found that the variability in the timing due to
            // context switches and other factors can prevent us from
            // sending packets that are smaller than the theoretical
            // maximum but close to it.  Use a slightly larger value
            // for transmit duration so that we can fill up the
            // transmit window.
            final long transmitDuration = mTransmitDuration.get();
            final long tweakedTransmitDuration = transmitDuration + (long) Math.min( 50, 0.1 * transmitDuration );
            final long MAX_SEND_PAYLOAD_SIZE = ((long) (transmitDuration * bytesPerMs));

            long currentGpsTime = System.currentTimeMillis() - mDelta.get();
            long goalTakeTime = currentGpsTime; // initialize to now, will get recomputed

            while ( mSenderState.get() != INetChannel.INTERRUPTED ) {
                AmmoGatewayMessage msg = null;
                try {
                    waitSlot: {
                        currentGpsTime = System.currentTimeMillis() - mDelta.get();

                        // which cycle are we in (start time)
                        final long thisCycleStartTime = (long) (currentGpsTime / cycleDuration) * cycleDuration;

                        // our hyperperiod is the low order short of (currentGpsTime / cycleDuration).
                        // We use this so the retransmitter can tell which slot it's in.
                        final int hyperperiod = ((int) (currentGpsTime / cycleDuration)) & 0x0000FFFF;
                        logger.trace( "Sender HP calc: {}, {}, {}",
                                      new Object[] { currentGpsTime,
                                                     cycleDuration,
                                                     hyperperiod });

                        // for this cycle when does our slot begin
                        final long thisSlotBegin = thisCycleStartTime + offset;

                        // for this cycle when does our slot end (begin + xmit-window)
                        final long thisSlotEnd = thisSlotBegin + tweakedTransmitDuration;

                        // how much data (in time units) have we sent so far in this slot
                        long thisSlotConsumed = 0;

                        // How many packets we have sent in this slot
                        int indexInSlot = 0;

                        if ( currentGpsTime < thisSlotBegin ) {
                            // too early - goto sleep till current slot begins
                            goalTakeTime = thisSlotBegin;
                            break waitSlot;
                        } else if ( currentGpsTime > thisSlotEnd ) {
                            // too late - goto sleep till next slot begin
                            goalTakeTime = thisSlotBegin + cycleDuration;
                            break waitSlot;
                        }

                        // else we are in slot, and should send
                        setSenderState( INetChannel.TAKING );

                        logger.debug( "Waking: hyperperiod={}, slotNumber={}, (time, ms)={}, jitter={}, cputime={}, mDelta={}",
                                      new Object[] {
                                          hyperperiod,
                                          slotIndex,
                                          currentGpsTime,
                                          currentGpsTime - thisSlotBegin,
                                          System.currentTimeMillis(),
                                          mDelta.get() } );

                        if ( getRetransmitter() != null )
                            getRetransmitter().switchHyperperiodsIfNeeded( hyperperiod );

                        while (true) {
                            try {
                                // At this point, we've woken up near the start of our
                                // window and should send a message if one is available.
                                if (!mSenderQueue.messageIsAvailable()) {
                                    logger.debug( "Time remaining in slot={}, but no messages in queue",
                                                  thisSlotEnd - currentGpsTime  );
                                    if ( getRetransmitter() != null )
                                        resendAndAck( thisSlotEnd,
                                                      thisSlotConsumed,
                                                      bytesPerMs,
                                                      hyperperiod,
                                                      slotIndex,
                                                      indexInSlot );
                                    // nothing in queue, wait till next slot
                                    goalTakeTime = thisSlotBegin + cycleDuration;
                                    break waitSlot;
                                }

                                AmmoGatewayMessage peekedMsg = mSenderQueue.peek();
                                int peekedMsgLength = peekedMsg.payload.length
                                                      + AmmoGatewayMessage.HEADER_DATA_LENGTH_TERSE;

                                if ( peekedMsgLength > MAX_SEND_PAYLOAD_SIZE ) {
                                    logger.warn( "Rejecting: messageLength={}, maxSize={}",
                                                 peekedMsgLength,
                                                 MAX_SEND_PAYLOAD_SIZE );
                                    // Take the message out of the queue and discard it,
                                    // since it's too big to ever send.
                                    msg = mSenderQueue.take(); // Will not block
                                    if ( msg.handler != null )
                                        ackToHandler( msg.handler, DisposalState.BAD );
                                    synchronized ( mDistQueueSize ) {
                                        --mDistQueueSize;
                                        logger.trace( "Decrementing mDistQueueSize to {}", mDistQueueSize );
                                        if ( mDistQueueSize == SENDQUEUE_LOW_WATER && mWasBusy ) {
                                            mWasBusy = false;
                                            setState( SERIAL_CONNECTED );
                                        }
                                    }

                                    continue; // examine the next item in queue
                                }

                                // update our time (could potentially change from last read
                                // because of context switch etc..)
                                currentGpsTime = System.currentTimeMillis() - mDelta.get();
                                // how much time do we have left in slot
                                final long timeLeft = (thisSlotEnd - currentGpsTime) - thisSlotConsumed;
                                final long bytesThatWillFit = (long) (timeLeft * bytesPerMs);

                                // HACK-FIXME: I'm subtracting out 50 here, since we need to have
                                // room to append the ack packet.  Redo all this later, since the
                                // logic for how to put packets in the slot will have to change.
                                // Right now, during development, I just want to make sure that
                                // the ack packets go out.
                                if ( peekedMsgLength > (bytesThatWillFit - RESERVE_FOR_ACK) ) {
                                    logger.debug( "Holding: messageLength={}, bytesThatWillFit={}",
                                                  peekedMsgLength,
                                                  bytesThatWillFit );
                                    // since we process queue in order and next message is bigger
                                    // than our available time, goto sleep till next slot
                                    if ( getRetransmitter() != null )
                                        resendAndAck( thisSlotEnd,
                                                      thisSlotConsumed,
                                                      bytesPerMs,
                                                      hyperperiod,
                                                      slotIndex,
                                                      indexInSlot );
                                    goalTakeTime = thisSlotBegin + cycleDuration;
                                    break waitSlot;
                                }

                                // now take and send assuming that our time has not diverged
                                // significantly from the last time we measured
                                msg = mSenderQueue.take(); // Will not block
                                synchronized ( mDistQueueSize ) {
                                    --mDistQueueSize;
                                    logger.trace( "Decrementing mDistQueueSize to {}", mDistQueueSize );
                                    if ( mDistQueueSize == SENDQUEUE_LOW_WATER && mWasBusy ) {
                                        mWasBusy = false;
                                        setState( SERIAL_CONNECTED );
                                    }
                                }

                                logger.debug("Took a message from the send queue");
                                logger.trace( "...hyperperiod={}", hyperperiod );
                                logger.trace( "...slot={}", slotIndex );
                                logger.trace( "...indexInSlot={}", indexInSlot );

                                // Before sending, set the values that DO NOT
                                // come from the distributor.
                                msg.mPacketType = AmmoGatewayMessage.PACKETTYPE_NORMAL;
                                logger.error( "setting packetType={}", msg.mPacketType );

                                msg.mHopCount = SerialRetransmitter.DEFAULT_HOP_COUNT;
                                logger.error( "setting hopCount={}", msg.mHopCount );

                                sendMessage( msg, hyperperiod, slotIndex, indexInSlot );
                                ++indexInSlot;
                                // we keep track of how much time we consumed in data transmit,
                                // since the send call is not true synchronous
                                thisSlotConsumed += (peekedMsgLength / bytesPerMs);
                            } catch ( IOException e ) {
                                logger.warn("sender threw exception", e );
                                if ( msg != null && msg.handler != null )
                                    ackToHandler( msg.handler, DisposalState.REJECTED );
                                setSenderState( INetChannel.INTERRUPTED );
                                ioOperationFailed();
                            } catch ( Exception e ) {
                                logger.warn("sender threw exception", e );
                                if ( msg != null && msg.handler != null )
                                    ackToHandler( msg.handler, DisposalState.BAD );
                                setSenderState( INetChannel.INTERRUPTED );
                                ioOperationFailed();
                                break;
                            }
                        }
                    }
                    currentGpsTime = System.currentTimeMillis() - mDelta.get();
                    if ( goalTakeTime > currentGpsTime )
                        Thread.sleep( goalTakeTime - currentGpsTime );
                } catch ( InterruptedException ex ) {
                    logger.debug( "interrupted taking messages from send queue" );
                    setSenderState( INetChannel.INTERRUPTED );
                    break;
                }
            }

            logger.trace( "SenderThread <{}>::run() exiting.", Thread.currentThread().getId() );
        }


        /**
         *
         */
        private void sendMessage( AmmoGatewayMessage msg,
                                  int hyperperiod,
                                  int slotIndex,
                                  int indexInSlot ) throws IOException
        {
            msg.gpsOffset = mDelta.get();
            msg.mHyperperiod = hyperperiod;
            logger.trace( "hyperperiod={}, msg.mHyperperiod={}", hyperperiod, msg.mHyperperiod );
            msg.mSlotID = slotIndex;
            msg.mIndexInSlot = indexInSlot;
            ByteBuffer buf = msg.serialize( endian,
                                            AmmoGatewayMessage.VERSION_1_TERSE,
                                            (byte) slotIndex );

            setSenderState(INetChannel.SENDING);

            if ( mSenderEnabled.get() ) {
                //FileOutputStream outputStream = mPort.getOutputStream();
                //outputStream.write( buf.array() );
                //outputStream.flush();

                int result = mPort.write( buf.array() );
                if ( result < 0 ) {
                    // If we got a negative number from the write(),
                    // we are in an error state, so throw an exception
                    // and shut down.
                    throw new IOException( "write on serial port returned: " + result );
                }
                mMessagesSent.getAndIncrement();
                mBytesSent += buf.array().length;

                logger.debug( "sent message size={}, checksum={}, data:{}",
                              new Object[] { msg.size,
                                             msg.payload_checksum.toHexString(),
                                             msg.payload });
                if ( getRetransmitter() != null ) {
                    getRetransmitter().sendingPacket( msg,
                                                      hyperperiod,
                                                      slotIndex,
                                                      indexInSlot );
                }
            }

            // legitimately sent to gateway.
            if ( msg.handler != null )
                ackToHandler(msg.handler, DisposalState.SENT);
        }


        /**
         *
         */
        private void resendAndAck( long thisSlotEnd,
                                   long thisSlotConsumed,
                                   double bytesPerMs,
                                   int hyperperiod,
                                   int slotIndex,
                                   int indexInSlot ) throws IOException
        {
            // update our time (could potentially change from last read because of context switch etc..)
            final long currentGpsTime = System.currentTimeMillis() - mDelta.get();

            // how much time do we have left in slot
            final long timeLeft = (thisSlotEnd - currentGpsTime) - thisSlotConsumed;
            long bytesThatWillFit = (long) (timeLeft * bytesPerMs);

            // Loop as long as resend packets will fit.
            // Subtract out 50 to leave room to append the ack packet.
            while ( bytesThatWillFit - RESERVE_FOR_ACK > 0 ) {
                AmmoGatewayMessage agm = getRetransmitter().createResendPacket( bytesThatWillFit - RESERVE_FOR_ACK );
                if ( agm != null ) {
                    sendMessage( agm, hyperperiod, slotIndex, indexInSlot );
                    // decrement bytes that will fit
                    bytesThatWillFit -= (agm.size + AmmoGatewayMessage.HEADER_DATA_LENGTH_TERSE);
                } else {
                    break;
                }
            }

            // Once we've sent all the resend packets we have room for,
            // tack on the ack packet, which will be the last packet in
            // the slot.
            AmmoGatewayMessage agm = getRetransmitter().createAckPacket( hyperperiod );
            if ( agm != null ) {
                agm.mPacketType = AmmoGatewayMessage.PACKETTYPE_ACK;
                logger.error( "before ack packetType={}", agm.mPacketType );
                agm.mIndexInSlot = indexInSlot;
                sendMessage( agm, hyperperiod, slotIndex, indexInSlot );
            }
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

        private static final int RESERVE_FOR_ACK = 50;

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
            logger.debug( "ReceiverThread::ReceiverThread()", Thread.currentThread().getId() );
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

                int hyperperiod = 0;

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
                        logger.trace( "Waiting for magic sequence." );
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
                        long currentTime = System.currentTimeMillis() - mDelta.get();
                        int slotDuration = mSlotDuration.get();
                        int cycleDuration = slotDuration * mRadiosInGroup.get();
                        long thisCycleStartTime = (long) (currentTime / cycleDuration) * cycleDuration;

                        // our hyperperiod is the low order short of (currentGpsTime / cycleDuration).
                        // We use this so the retransmitter can tell which slot it's in.
                        hyperperiod = ((int) (currentTime / cycleDuration)) & 0x0000FFFF;
                        logger.trace( "Receiver HP calc: {}, {}, {}",
                                      new Object[] { currentTime,
                                                     cycleDuration,
                                                     hyperperiod });

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
                            logger.debug( "Deserialization failure." );
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
                        AmmoGatewayMessage agm = agmb
                            .payload( buf_payload )
                            .channel(SerialChannel.this)
                            .build();

                        logger.error( "received packettype={}", agm.mPacketType );

                        // Begin logging stuff
                        long currentTime = System.currentTimeMillis() - mDelta.get();
                        int slotDuration = mSlotDuration.get();
                        int cycleDuration = slotDuration * mRadiosInGroup.get();

                        long thisCycleStartTime = (long) (currentTime / cycleDuration) * cycleDuration;
                        long currentSlot = (currentTime - thisCycleStartTime) / slotDuration;

                        logger.debug( "Finished reading payload in slot {} at {}",
                                      currentSlot,
                                      currentTime );
                        // End logging stuff

                        mMessagesReceived.getAndIncrement();
                        logger.debug( "received message size={}, checksum={}, data:{}",
                                      new Object[] {
                                          agm.size,
                                          agm.payload_checksum.toHexString(),
                                          agm.payload } );

                        if ( getRetransmitter() != null ) {
                            setReceiverState( INetChannel.DELIVER );
                            getRetransmitter().processReceivedMessage( agm,
                                                                       hyperperiod );
                        } else {
                            setReceiverState( INetChannel.DELIVER );
                            deliverMessage( agm );
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
                logger.warn( "receiver threw an IOException", ex );
                setReceiverState( INetChannel.INTERRUPTED );
                ioOperationFailed();
            } catch ( Exception ex ) {
                logger.warn( "receiver threw an exception", ex );
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
            int val = -1;
            mSecondsSinceByteRead.set( 0 );
            while ( val == -1 &&  mReceiverState.get() != INetChannel.INTERRUPTED ) {
                logger.trace( "SerialPort.read()" );
                val = mInputStream.read();
                if ( val == -1 )
                    mSecondsSinceByteRead.getAndIncrement();
            }

            logger.trace( "val={}", (byte) val );
            mBytesSinceMagic.getAndIncrement();
            mBytesRead += 1;

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







    ///////////////////////////////////////////////////////////////////////////
    //
    private class SatcomSenderThread extends Thread
    {
        /**
         * 
         */
        public SatcomSenderThread()
        {
            super( new StringBuilder("Tcp-Sender-").append( Thread.activeCount() ).toString() );
        }


        /**
         * 
         */
        @Override
        public void run()
        {
            logger.trace( "SatcomSenderThread <{}>::run()", Thread.currentThread().getId() );

            // Block on reading from the queue until we get a message to send.
            // Then send it on the socket channel. Upon getting a socket error,
            // notify our parent and go into an error state.

            while ( mSenderState.get() != INetChannel.INTERRUPTED ) {
                AmmoGatewayMessage msg = null;
                try
                {
                    setSenderState( INetChannel.TAKING );
                    msg = mSenderQueue.take(); // The main blocking call
                    synchronized ( mDistQueueSize ) {
                        --mDistQueueSize;
                        logger.trace( "Decrementing mDistQueueSize to {}", mDistQueueSize );
                        if ( mDistQueueSize == SENDQUEUE_LOW_WATER && mWasBusy ) {
                            mWasBusy = false;
                            setState( SERIAL_CONNECTED );
                        }
                    }

                    logger.debug( "Took a message from the send queue" );
                }
                catch ( InterruptedException ex )
                {
                    logger.debug( "interrupted taking messages from send queue", ex );
                    setSenderState( INetChannel.INTERRUPTED );
                    break;
                }

                try
                {
                    ByteBuffer buf = msg.serialize( endian, AmmoGatewayMessage.VERSION_1_SATCOM, (byte) 0 );

                    if ( mSenderEnabled.get() ) {
                        setSenderState( INetChannel.SENDING );

                        int result = mPort.write( buf.array() );
                        if ( result < 0 ) {
                            // If we got a negative number from the write(),
                            // we are in an error state, so throw an exception
                            // and shut down.
                            throw new IOException( "write on serial port returned: " + result );
                        }
                        mMessagesSent.getAndIncrement();
                        mBytesSent += buf.array().length;

                        logger.debug( "sent message size={}, checksum={}, data:{}",
                                      new Object[] { msg.size,
                                                     msg.payload_checksum.toHexString(),
                                                     msg.payload });
                    }

                    // legitimately sent to gateway.
                    if ( msg.handler != null )
                        ackToHandler( msg.handler, DisposalState.SENT );

                } catch ( Exception ex ) {
                    logger.warn( "sender threw exception", ex );
                    if ( msg != null && msg.handler != null )
                        ackToHandler( msg.handler, DisposalState.BAD );
                    setSenderState( INetChannel.INTERRUPTED );
                    ioOperationFailed();
                }
            }
        }


        private void setSenderState( int state )
        {
            mSenderState.set( state );
            statusChange();
        }

        public int getSenderState() { return mSenderState.get(); }

        private AtomicInteger mSenderState = new AtomicInteger( INetChannel.TAKING );
        private final Logger logger = LoggerFactory.getLogger( "net.serial.satcom.sender" );
    }


    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////
    //
    /**
     *
     */
    private class SatcomReceiverThread extends Thread
    {
        /**
         *
         */
        public SatcomReceiverThread()
        {
            super( new StringBuilder("SATCOM-Reciever-").append( Thread.activeCount() ).toString() );
            logger.debug( "SatcomReceiverThread::SatcomReceiverThread()",
                          Thread.currentThread().getId() );
            mInputStream = mPort.getInputStream();
        }


        /**
         *
         */
        @Override
        public void run()
        {
            logger.info( "SatcomReceiverThread <{}>::run()", Thread.currentThread().getId() );

            // Block on reading from the SerialPort until we get some data.
            // If we get an error, notify our parent and go into an error state.

            // NOTE: We found that our reads were less reliable when reading more than
            // one byte at a time using the standard stream and ByteBuffer patterns.
            // Reading one byte at a time in the code below is intentional.

            try {
                // These have to be hardcoded here in order to be seen as compile-time constants
                // by the Java compile.  Otherwise, they can't be used in the switch statement below.
                final byte first  = (byte) 0xea;
                final byte second = (byte) 0x1d;
                final byte third  = (byte) 0xad;
                final byte fourth = (byte) 0xab;

                // See note about length=16 below.
                byte[] buf_header = new byte[ 32 ];// AmmoGatewayMessage.HEADER_DATA_LENGTH_SATCOM;
                ByteBuffer header = ByteBuffer.wrap( buf_header );
                header.order( endian );

                int state = 0;
                byte c = 0;
                AmmoGatewayMessage.Builder agmb = null;

                while ( mReceiverState.get() != INetChannel.INTERRUPTED ) {
                    setReceiverState( INetChannel.START );

                    switch ( state ) {
                    case 0:
                        logger.trace( "Waiting for magic sequence." );
                        c = readAByte();
                        if ( c == first ) {
                            state = first;
                            mReceiverSubstate.set( 11 );
                        }
                        break;

                    case first:
                        c = readAByte();
                        if ( c == first ) {
                            state = first;
                            mReceiverSubstate.set( 11 );
                        } else if ( c == second ) {
                            state = second;
                            mReceiverSubstate.set( 12 );
                        } else {
                            state = 0;
                            mReceiverSubstate.set( 0 );
                        }
                        break;

                    case second:
                        c = readAByte();
                        if ( c == third ) {
                            state = third;
                            mReceiverSubstate.set( 13 );
                        } else if ( c == first ) {
                            state = first;
                            mReceiverSubstate.set( 11 );
                        } else {
                            state = 0;
                            mReceiverSubstate.set( 0 );
                        }
                        break;

                    case third:
                        c = readAByte();
                        if ( c == fourth ) {
                            state = 1;
                            mReceiverSubstate.set( 1 );
                        } else if ( c == first ) {
                            state = first;
                            mReceiverSubstate.set( 11 );
                        } else {
                            state = 0;
                            mReceiverSubstate.set( 0 );
                        }
                        break;

                    case 1: // 1 == reading header
                    {
                        logger.debug( "Read magic sequence in SATCOM channel" );

                        mBytesSinceMagic.set( 0 );

                        header.clear();

                        // Set these in buf_header, since extractHeader() expects them.
                        buf_header[0] = first;
                        buf_header[1] = second;
                        buf_header[2] = third;
                        buf_header[3] = fourth;

                        // For some unknown reason, this was writing past the end of the
                        // array when length = 16.
                        int headerBytesLeft = AmmoGatewayMessage.HEADER_LENGTH_SATCOM - 4;
                        for ( int i = 0; i < headerBytesLeft; ++i ) {
                            c = readAByte();
                            buf_header[i+4] = c;
                        }
                        logger.debug( " Received SATCOM header, reading payload " );

                        agmb = AmmoGatewayMessage.extractHeaderSatcom( header );
                        if ( agmb == null ) {
                            logger.debug( "Deserialization failure." );
                            mCorruptMessages.getAndIncrement();
                            state = 0;
                        } else {
                            state = 2;
                        }
                        mReceiverSubstate.set( state );
                    }
                    break;

                    case 2: // 2 == reading payload
                    {
                        int payload_size = agmb.size();
                        if ( payload_size > MAX_RECEIVE_PAYLOAD_SIZE ) {
                            logger.warn( "Discarding packet of size {}. Maximum payload size exceeded.",
                                         payload_size );
                            state = 0;
                            mReceiverSubstate.set( 0 );
                            break;
                        }
                        byte[] buf_payload = new byte[ payload_size ];

                        for ( int i = 0; i < payload_size; ++i ) {
                            c = readAByte();
                            buf_payload[i] = c;
                        }

                        agmb.isSerialChannel( true );
                        AmmoGatewayMessage agm = agmb
                            .payload( buf_payload )
                            .channel( SerialChannel.this )
                            .build();

                        mMessagesReceived.getAndIncrement();
                        logger.debug( "received message size={}, checksum={}, data:{}",
                                      new Object[] {
                                          agm.size,
                                          agm.payload_checksum.toHexString(),
                                          agm.payload } );

                        setReceiverState( INetChannel.DELIVER );
                        deliverMessage( agm );

                        header.clear();
                        setReceiverState( INetChannel.START );
                        state = 0;
                        mReceiverSubstate.set( 0 );
                    }
                    break;

                    default:
                        logger.debug( "Unknown value for state variable" );
                    }
                }
            } catch ( IOException ex ) {
                logger.warn( "receiver threw an IOException", ex );
                setReceiverState( INetChannel.INTERRUPTED );
                ioOperationFailed();
            } catch ( Exception ex ) {
                logger.warn( "receiver threw an exception", ex );
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
            int val = -1;
            mSecondsSinceByteRead.set( 0 );
            while ( val == -1 &&  mReceiverState.get() != INetChannel.INTERRUPTED ) {
                logger.trace( "SerialPort.read()" );
                val = mInputStream.read();
                if ( val == -1 )
                    mSecondsSinceByteRead.getAndIncrement();
            }

            logger.trace( "val={}", (byte) val );
            mBytesSinceMagic.getAndIncrement();
            mBytesRead += 1;

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

        private AtomicInteger mReceiverState = new AtomicInteger( INetChannel.TAKING );
        private FileInputStream mInputStream;
        private final Logger logger = LoggerFactory.getLogger( "net.serial.satcom.receiver" );
    }


    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////


    // Called by ReceiverThread to send an incoming message to the appropriate
    // destination.  This method really should be private, but the
    // SerialRetransmitter needs to use it.  Is there a better Java way to
    // fix this?
    /**
     *
     */
    public boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.debug( "In deliverMessage()" );

        boolean result = false;
        if ( mIsAuthorized.get() ) {
            if ( mSatcomEnabled ) {
                logger.debug( " delivering to SerialFragmenter" );
                synchronized ( mFragmenter ) {
                    SerialFragmenter f = getFragmenter();
                    if ( f != null ) {
                        result = f.deliver( agm );
                    } else {
                        logger.error( "  In SATCOM mode, but there is no fragmenter" );
                    }
                }
            } else {
                logger.debug( " delivering to channel manager" );
                result = mChannelManager.deliver( agm );
            }
        } else {
            logger.debug( " delivering to security object" );
            result = getSecurityObject().deliverMessage( agm );
        }
        return result;
    }


    /**
     *
     */
    public DisposalState addMessageToSenderQueue( AmmoGatewayMessage agm )
    {
        // We shouldn't expose the sender queue this way, but the
        // SerialFragmenter needs to be able to add things to it.
        // Java doesn't really have good support for things like this.
        return mSenderQueue.putFromDistributor( agm );
    }


    /**
     *
     */
    private void setRetransmitter( SerialRetransmitter retransmitter )
    {
        mRetransmitter.set( retransmitter );
    }


    /**
     *
     */
    private SerialRetransmitter getRetransmitter()
    {
        return mRetransmitter.get();
    }


    /**
     *
     */
    private void setFragmenter( SerialFragmenter fragmenter )
    {
        synchronized ( mFragmenter ) {
            mFragmenter.set( fragmenter );
        }
    }


    /**
     *
     */
    private SerialFragmenter getFragmenter()
    {
        return mFragmenter.get();
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
        logger.debug( "In setIsAuthorized(). value={}", iValue );

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
    	int connState = this.getState();
        int senderState = (mSender != null) ? mSender.getSenderState() : PENDING;
        int receiverState = (mReceiver != null) ? mReceiver.getReceiverState() : PENDING;

        try {
            mChannelManager.statusChange(this,
            		this.lastConnState, connState,
                    this.lastSenderState, senderState,
                    this.lastReceiverState, receiverState);
        } catch (Exception ex) {
            logger.error("Exception thrown in statusChange()", ex);
        }
        this.lastConnState = connState;
        this.lastSenderState = senderState;
        this.lastReceiverState = receiverState;
    }


	@Override
    public String getSendBitStats() {
        //StringBuilder result = new StringBuilder();
        //result.append( "S: " ).append( humanReadableByteCount(mBytesSent, true) );
        //result.append( ", BPS:" ).append( mBpsSent );
        //return result.toString();
        if ( getRetransmitter() != null )
            return getRetransmitter().getSendBitStats();  // just for field testing
        else
            return "";
    }


	@Override
    public String getReceiveBitStats() {
        //StringBuilder result = new StringBuilder();
        //result.append( "R: " ).append( humanReadableByteCount(mBytesRead, true) );
        //result.append( ", BPS:" ).append( mBpsRead );
        //return result.toString();
        if ( getRetransmitter() != null )
            return getRetransmitter().getReceiveBitStats();  // just for field testing
        else
            return "";
    }


    // I made this public to support the hack to get authentication
    // working before Nilabja's code is ready.  Make it private again
    // once his stuff is in.
    public IChannelManager mChannelManager;

    private Context mContext;

    private final AtomicReference<ISecurityObject> mSecurityObject = new AtomicReference<ISecurityObject>();
    private final AtomicReference<SerialRetransmitter> mRetransmitter = new AtomicReference<SerialRetransmitter>();
    private final AtomicReference<SerialFragmenter> mFragmenter = new AtomicReference<SerialFragmenter>();

    private static final int WAIT_TIME = 5 * 1000; // 5 s
    private static final int MAX_RECEIVE_PAYLOAD_SIZE = 2000; // Should this be set based on baud and slot duration?
    private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;

    private boolean mEnabled = false;

    private String mDevice = "/dev/ttyUSB0";
    private int mBaudRate;

    private AtomicInteger mSlotNumber  = new AtomicInteger();
    private AtomicInteger mRadiosInGroup = new AtomicInteger();

    private AtomicInteger mSlotDuration = new AtomicInteger();
    private AtomicInteger mTransmitDuration = new AtomicInteger();

    private AtomicBoolean mSenderEnabled = new AtomicBoolean();

    private AtomicInteger mState = new AtomicInteger( SERIAL_DISABLED );
    private int getState() { return mState.get(); }
    private void setState( int state )
    {
        // Create a method is NetChannel to convert the numbers to strings.
        logger.debug( "changing state from {} to {}",
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

    // FIXME: MCJ: I'm hardcoding this to true during development.
    // It should default to false;
    private volatile boolean mSatcomEnabled = true;

    private SenderThread mSender;
    private ReceiverThread mReceiver;

    private SatcomSenderThread mSatcomSender;
    private SatcomReceiverThread mSatcomReceiver;

    private static final int SENDQUEUE_MAX_SIZE = 20;
    private static final int SENDQUEUE_LOW_WATER = 5;

    // Used for synchronization
    private Integer mDistQueueSize = Integer.valueOf( 0 );
    private boolean mWasBusy = false;

    private SenderQueue mSenderQueue = new SenderQueue();

    private int mCount = 0;
    private final int numSamples = 20;
    private AtomicLong mDelta = new AtomicLong( 0 );
    private long mDeltaSamples[] = new long[numSamples];

    private final AtomicInteger mReceiverSubstate = new AtomicInteger( 0 );

    private final AtomicInteger mMessagesSent = new AtomicInteger();
    private final AtomicInteger mMessagesReceived = new AtomicInteger();
    private final AtomicInteger mCorruptMessages = new AtomicInteger();
    private final AtomicInteger mBytesSinceMagic = new AtomicInteger();
    private final AtomicInteger mSecondsSinceByteRead = new AtomicInteger();

    private LocationManager mLocationManager;
    private NmeaListener mNmeaListener;
    private LocationListener mLocationListener;

    private static final Logger logger = LoggerFactory.getLogger( "net.serial" );
}

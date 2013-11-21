// SerialFragmenter.java

/* Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
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

import java.lang.Short;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;



public class SerialFragmenter {
    private static final Logger logger = LoggerFactory.getLogger( "net.serial.fragmenter" );


    //
    // SATCOM-related members
    // (The framing part is in the AGM.  The fragmenter handles everything
    // internal to the 
    // in the Fragmenter, leaving only the framing part for the AGM.)

    // // Message type (0 = data, 1 = ack or token)
    // // Use mPacketType above.

    // // Should ack (0 = don't ack; 1 = do ack)
    // public boolean mShouldAck;

    // // Datatype-specific identifier (set to 0 for ack/token packets)
    // public int mDataType;

    // // uint16 sequenceNumber: should be increasing, for all packets;
    // // resent packets should have same sequence number
    // public int mSequenceNumber;

    // // uint16 index: index of packet in a multi-packet message
    // // (first message should be 0; last message should be (count - 1))
    // public int mIndexInMultipart;

    // // uint16 count : total number of packets in a multi-packet message
    // public int mTotalInMultipart;

    // // Actual data to be sent; this is different from the payload, since
    // // the payload included the messagetype, sequence number, etc., plus
    // // mData.
    // public byte[] mData;
    
    // // number of acknowledgements that we are acking
    // public int mNumberOfAcknowledgements;

    // // list of sequence numbers being acknowledged
    // public int[] mAcks;

    // // Indicates if this is a token packet: high bit will be 1 if passing
    // // token; 0 if not
    // public boolean mIsThisAToken;


    ///////////////////////////////////////////////////////////////////////////


    // Here is a description of the modifications to the SerialChannel that
    // we'll make:

    // First, we will add a class called the "Fragmenter", which will be
    // responsible for taking media larger than a certain size and breaking
    // it up into 1K chunks.

    // Packets will not be put into the SenderQueue as they currently are
    // now, but rather passed to the Fragmenter.  The fragmenter will put
    // smaller packets in one queue and ones larger than the limit in a
    // different queue.

    // The ReceiverThread takes all packets that it receives and passes them
    // to the Fragmenter.  When a token packet is received, the fragmenter
    // takes all of the packets in the small queue and puts them in the
    // SenderQueue, so the SenderThread starts sending them.  In addition, it
    // then takes its current image (the one at the head of the large queue)
    // and find the appropriate number of chunks and puts them in the
    // SenderQueue.  At the end, it puts a Token packet and sets a timeout.

    // The SenderThread then sends them out normally.

    // If the timeout expires, that means that the other end did not send us
    // back an ack packet so the Fragmenter puts another packet in the
    // SenderQueue.  We do this until we receive back an ack packet.

    // This means that in an error situation, we must inform the Distributor
    // of the status of the contents of all three queues.

    // How should we allow the queue to change from TDMA mode to
    // token-passing mode?  I guess we should error out the packets, and
    // create/destroy the Fragment manager.  We need to somehow pause the
    // SenderThread and ReceiverThread, though.  Ah, we probably want to
    // destroy the threads and create new ones, since they may need to
    // connect to a different TTY.  I think maybe a disconnect() and
    // connect() would do the trick correctly.

    // (Actually, I think that changing the setting should have no effect
    // except to change the setting member variable, and the user should have
    // to disable/enable the channel to have it effect the change.)

    // The only tricky part would be that if something happened and the TTY
    // blocked or had some sort of problem, and the data didn't get sent
    // before the timer expired.  In that case, we only want to send another
    // token if the SenderQueue is empty; otherwise we should just set the
    // timer again.

    // The SenderThread and ReceiverThread can stay the same, but the
    // ReceiverThread will need an interface to deliver to, since it has to
    // send to either the Fragmenter or the ChannelManager.

    // I think that I can just make the connect() and disconnect() methods
    // instantiate the Fragmenter and hook things up differently.

    private static final int DATA  = 1;
    private static final int ACK   = 2;
    private static final int TOKEN = 3;

    private static final int RESET_PACKET_INTERVAL = 5000; // Make this configurable
    private static final int TOKEN_PACKET_INTERVAL = 10000; // Make this configurable

    public static final String SATCOM_MEDIA_PROGRESS = "edu.vu.ammo.core.SATCOM_IMAGE_PROGRESS";
    
    public SerialFragmenter( SerialChannel channel, IChannelManager channelManager, Context context, int slotNumber )
    {
        logger.debug( "Constructor" );

        mChannel = channel;
        mChannelManager = channelManager;
        mContext = context;

        String operatorId = mChannelManager.getOperatorId();

        // CRC this and put the bytes in mOperatorIdCrc.
        byte bytes[] = operatorId.getBytes();
        Checksum checksum = new CRC32();
        checksum.update( bytes, 0, bytes.length );
        long checksumValue = checksum.getValue();

        logger.debug( "slotNumber=={}", slotNumber);
        mHandheldRole = ( slotNumber % 2 == 0 );
        logger.debug( "mHandheldRole=={}", mHandheldRole );

        mOperatorIdAsInt = (int) checksumValue;

        mOperatorIdCrc[0] = (byte) checksumValue;
        mOperatorIdCrc[1] = (byte) (checksumValue >>> 8);
        mOperatorIdCrc[2] = (byte) (checksumValue >>> 16);
        mOperatorIdCrc[3] = (byte) (checksumValue >>> 24);

        logger.debug( "Created Fragmenter for operatorID={}", operatorId );
    }


    public void destroy()
    {
        logger.debug( "SerialFragmenter::destroy()" );
        stopResetTimer();
        stopTokenTimer();
        logger.debug( "Calling shutdownNow() on mScheduler." );
        mScheduler.shutdownNow();
    }


    public void startSending()
    {
        logger.debug( "startSending()" );

        // If we are in the gateway role, just listen and send nothing.
        // Only the handheld role sends resets.
        if ( !mHandheldRole ) {
            logger.debug( "Not handheld, so returning." );
            return;
        }

        // Now, send a reset packet and set a timer.

        final Runnable resetter = new Runnable() {
                public void run() {
                    logger.debug( "Sending a reset packet" );
                    mChannel.addMessageToSenderQueue( createResetPacket() );
                }
            };

        if ( resetterHandle != null ) {
            logger.error( "Tried to create reset timer when we already had one." );
        }
        resetterHandle = mScheduler.scheduleAtFixedRate( resetter, 
                                                         0, // initial delay
                                                         RESET_PACKET_INTERVAL,
                                                         TimeUnit.MILLISECONDS );
        logger.debug( "XXXX resetterHandle={}", resetterHandle );
    }


    public void stopResetTimer()
    {
        logger.debug( "SerialFragmenter::stopResetTimer()" );
        // We received an acknowledgement for an ack, so disable the timer.
        logger.debug( "XXXX resetterHandle={}", resetterHandle );
        if ( resetterHandle != null ) {
            logger.debug( "  cancelling the reset timer" );
            boolean result = resetterHandle.cancel( true );
            logger.debug( "  returned: {}", result );
            resetterHandle = null;
            logger.debug( "XXXX resetterHandle={}", resetterHandle );
        }
    }


    /**
     *
     */
    public DisposalState putFromDistributor( AmmoGatewayMessage agm )
    {
        logger.debug( "putFromDistributor()" );

        if ( !mSynced ) {
            logger.debug( "serial channel not synced; not taking messages {}",
                          DisposalState.BUSY );
            return DisposalState.BUSY;
        }

        if ( agm.payload.length > MAX_PACKET_SIZE ) {
            // It's a large packet and needs to be fragmented.
            // Put it in the queue for large packets.

            if ( !mLargeQueue.offer( agm )) {
                logger.debug( "serial channel not taking messages {}",
                              DisposalState.BUSY );
                return DisposalState.BUSY;
            } else {
                if ( mCurrentLarge == null ) {
                    startSendingLargePacket();
                }
            }

            return DisposalState.QUEUED;
        } else {
            // Small packet
            if ( !mSmallQueue.offer( agm )) {
                logger.debug( "serial channel not taking messages {}",
                              DisposalState.BUSY );
                return DisposalState.BUSY;
            }

            // We may want to add the busy and low water code here, like we have
            // in the normal DistQueue.  I'm leaving it out for now.

            return DisposalState.QUEUED;
        }
    }


    /**
     *
     */
    private synchronized void startSendingLargePacket()
    {
        if ( mLargeQueue.size() > 0 ) {
            mCurrentLarge = mLargeQueue.poll();

            // Figure out how many bits we need in the BitSet.
            mNumberOfFragments = mCurrentLarge.payload.length / MAX_PACKET_SIZE;
            if ( mCurrentLarge.payload.length % MAX_PACKET_SIZE > 0 ) {
                ++mNumberOfFragments;
            }

            mCurrentLargeBaseSequence = mSequenceNumber;
            incrementSequenceNumber( mNumberOfFragments );

            // Create a BitSet and default to true, so we can test for all
            // falses to tell if we're done.
            mAckedPackets = new BitSet( mNumberOfFragments );
            for ( int i = 0; i < mNumberOfFragments; ++i ) {
                mAckedPackets.set( i );
            }
        }
    }


    /**
     *
     */
    private synchronized void sendFragments()
    {
        // Find lowest bit in BitSet that is true, starting from zero.
        int index = mAckedPackets.nextSetBit( 0 );

        for ( int i = 0; i < FRAGMENTS_PER_TOKEN && index != -1; ++i ) {
            // The last fragment's length may be a fraction of MAX_PACKET_SIZE.
            // Figure out what it should be.
            int lengthOfFragment = MAX_PACKET_SIZE;
            if ( index == mNumberOfFragments - 1 ) {
                lengthOfFragment = mCurrentLarge.payload.length % MAX_PACKET_SIZE;
            }

            // Create the AGM for the fragment
            byte[] new_payload = new byte[lengthOfFragment + 7];
            ByteBuffer buf = ByteBuffer.wrap( new_payload );
            buf.order( ByteOrder.LITTLE_ENDIAN );

            // Message type
            buf.put( (byte) 0x31 );
            
            // Sequence number (2 bytes)
            int sequenceNumber = (mCurrentLargeBaseSequence + index) % Short.MAX_VALUE;
            buf.putShort( (short) sequenceNumber );

            // Index of packet in multi-packet sequence (2 bytes)
            buf.putShort( (short) index );

            // Total number of packets in multi-packet sequence (2 bytes)
            buf.putShort( (short) mNumberOfFragments );

            // Now put old_payload in
            buf.put( mCurrentLarge.payload, index * MAX_PACKET_SIZE, lengthOfFragment );

            // Compute the checksum of the new payload.
            AmmoGatewayMessage.CheckSum csum = AmmoGatewayMessage.CheckSum.newInstance( new_payload );
            long payload_checksum = csum.asLong();

            AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder();
            agmb.version( AmmoGatewayMessage.VERSION_1_SATCOM )
                .payload( new_payload )
                .size( new_payload.length )
                .checksum( payload_checksum );

            mChannel.addMessageToSenderQueue( agmb.build() );

            index = mAckedPackets.nextSetBit( index + 1 );
        }
    }


    private boolean isInCurrentLargePacket( short sequenceNumber )
    {
        // This maps sequence numbers below the large packet into
        // areas high in the range of a short.  As a result, we can
        // just test whether index < mNumberOfFragments to tell if the
        // sequenceNumber belongs to a fragment.
        short index = (short) (sequenceNumber - mCurrentLargeBaseSequence);
        if ( index < 0 ) {
            index += Short.MAX_VALUE;
        }
        
        return index < mNumberOfFragments;
    }


    /**
     *
     */
    private synchronized void processAck( int count, ByteBuffer buf )
    {
        logger.debug( "processAck() with count={}", count );

        // For each item in the ack, we need to figure out if it's a
        // fragment of a large one or an individual small one, since
        // we need to treat these differently.

        for ( int i = 0; i < count; ++i ) {
            short sequenceNumber = buf.getShort();
            logger.debug( "  processing sequence number {}", sequenceNumber );

            if ( isInCurrentLargePacket( sequenceNumber )) {
                // This is a fragment of the current large packet.

                if ( mAckedPackets == null )
                    continue;

                // Unset the bits with the numbers in the ack.
                short index = (short) (sequenceNumber - mCurrentLargeBaseSequence);
                if ( index < 0 ) {
                    index += Short.MAX_VALUE;
                }
                logger.debug( "  fragment at index {}", index );

                mAckedPackets.clear( index );

                // Test the BitSet to see if all are false. If so, start
                // sending the next packet if there is one in the queue.
                logger.debug( "Packets still not acked: {}", mAckedPackets.cardinality() );
                if ( mAckedPackets.cardinality() == 0 ) {
                    // legitimately sent (all packets were acked).
                    if ( mCurrentLarge.handler != null ) {
                        mCurrentLarge.handler.ack( mChannel.name , DisposalState.SENT );
                    }

                    // Now reset the relevant member variables
                    mCurrentLarge = null;
                    mCurrentLargeBaseSequence = -1;
                    mNumberOfFragments = -1;
                    mAckedPackets = null;

                    startSendingLargePacket();
                }
            } else {
                // This is an individual packet (not a fragment).
                

                // Remove the packet from the list if it's present.
                logger.debug( "Removing wagm from mWaitingToBeAcked" );
                for ( WrappedAgm wagm : mWaitingToBeAcked ) {
                    if ( wagm.mSequenceNumber == sequenceNumber ) {
                        logger.debug( "Removing {}", wagm );
                        mWaitingToBeAcked.remove( wagm );

                        // Let the distributor know that the packet has
                        // been successfully sent.
                        if ( wagm.mAgm.handler != null ) {
                            wagm.mAgm.handler.ack( mChannel.name , DisposalState.SENT );
                        }

                        break;
                    }
                }
            }
        }        
    }


    private void processReceivedFragment( int sequenceNumber,
                                          int index,
                                          int count,
                                          byte[] payloadBuffer )
    {
        logger.debug( "processReceivedFragment(" );

        // If there is already a FragmentReceiver, then we are already
        // in the process of receiving a large packet.  If not, this
        // is the first packet we're receiving and we need to create a
        // new FragmentReceiver.
        if ( mFR == null ) {
            mFR = new FragmentReceiver();

            // Now set some of the values here.
            mFR.mIncomingBytes = new byte[ count * MAX_PACKET_SIZE ];
            mFR.mBaseSequenceNumber = sequenceNumber - index;
            mFR.mNumberOfFragments = count;

            mFR.mReceived = new BitSet( mFR.mNumberOfFragments );
            for ( int i = 0; i < mFR.mNumberOfFragments; ++i ) {
                mFR.mReceived.set( i );
            }
        }

        // Mark that we received it, so we can ack properly.
        logger.debug( "Marking for ack: {}", sequenceNumber );
        mMarkedForAck.add( sequenceNumber );

        // Mark the bitset to denote that we received it.
        mFR.mReceived.clear( index );

        // Now put the actual data in the buffer.
        System.arraycopy( payloadBuffer, 7,
                          mFR.mIncomingBytes, index * MAX_PACKET_SIZE,
                          payloadBuffer.length - 7 );

        if ( index == count - 1 ) {
            // This is the fragment at the end of the large payload.
            // Since it may not be the whole MAX_PACKET_LENGTH, we
            // need to save the length, since protocol buffers will
            // have problems with extra data after the end.
            mFR.sizeOfLastFragment = payloadBuffer.length - 7;
        }

        // Send an intent denoting how many fragments we've received.
        Intent intent = new Intent( SATCOM_MEDIA_PROGRESS );
        int fragmentsRemaining = mFR.mReceived.cardinality();

        if ( fragmentsRemaining > 0 ) {
            int fragmentsReceived = count - fragmentsRemaining;
            intent.putExtra( Intent.EXTRA_TEXT,
                             "Received " + Integer.toString(fragmentsReceived) + " out of "
                             + Integer.toString( count ));
        } else {
            intent.putExtra( Intent.EXTRA_TEXT, "Received complete image" );
        }
        logger.debug( "Sending broadcast intent" );
        mContext.sendBroadcast( intent );

        // Check to see if all fragments have been received.
        if ( mFR.mReceived.cardinality() == 0 ) {
            logger.debug( "Finished receiving large packet" );

            // Recopy the array to one of the correct length.
            // Google protocol buffers can't deal with extra data after
            // the end.
            int correctLength = ((count - 1) * MAX_PACKET_SIZE) + mFR.sizeOfLastFragment;
            logger.debug( "correct length={}", correctLength );
            byte[] newPayload = new byte[ correctLength ];
            System.arraycopy( mFR.mIncomingBytes, 0,
                              newPayload, 0,
                              correctLength );

            // We've received all the framents, so pass the result up
            // to the distributor.  We need to dummy up an agm of the
            // appropriate type.
            AmmoGatewayMessage.CheckSum crc32 = AmmoGatewayMessage.CheckSum.newInstance( newPayload,
                                                                                         0,
                                                                                         correctLength );

            AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder();
            agmb.version( AmmoGatewayMessage.VERSION_1_SATCOM )
                .size( correctLength ) // payload size
                .checksum( crc32.asLong() ); // checksum from the packet.

            agmb.isSerialChannel( true );
            AmmoGatewayMessage agm = agmb
                .payload( newPayload )
                .channel( mChannel )
                .build();

            logger.debug( "Delivering large packet to DistributorThread." );
            boolean result = mChannelManager.deliver( agm );

            mFR = null;
        }
    }


    /**
     * Called when the SatcomReceiverThread receives a message from the network
     */
    public boolean deliver( AmmoGatewayMessage agm )
    {
        logger.debug( "  deliver()" );
        boolean result = false;

        if ( !agm.hasValidChecksum() ) {
            logger.debug( "!!! Received packet with corrupt payload !!!  Discarding..." );
            // If this message came from the serial channel, let it know that
            // a corrupt message occurred, so it can update its statistics.
            // Make this a more general mechanism later on.
            mChannel.receivedCorruptPacket();

            return false;
        }

        // When the token packet is sent, set the timeout.  Upon
        // expiring, we put another token in the queue and set the
        // timeout again.

        byte[] payload = agm.payload; 

        ByteBuffer payloadBuffer = ByteBuffer.wrap( payload );
        payloadBuffer.order( ByteOrder.LITTLE_ENDIAN );
        byte messageType = payloadBuffer.get();

        byte packetType = (byte) (messageType & 0x80);
        boolean resetPacket = (messageType & 0x40) != 0;
        boolean shouldAck = (messageType & 0x20) != 0;
        boolean sentFromHandheld = (messageType & 0x10) != 0;
        byte identifier = (byte) (messageType & 0x0F);

        // Classify the type of the packet
        int type = -1;
        short count = -1;
        if ( packetType == 0 ) {
            type = DATA;
        } else {
            // Token or ack
            count = payloadBuffer.getShort();
            logger.debug( "count={}", count );

            if ( (count & 0x00008000) != 0 ) {
                type = TOKEN;
            } else {
                type = ACK;
            }
        }

        // For any received packet, we know that the other side
        // received the token and it's no longer ours, so we must not
        // resend the token.
        stopTokenTimer();

        if ( type == DATA ) {
            // Data packets can be either reset packets or things
            // destined for the distributor.  For normal packets, send
            // them up to the distributor.  Process the fragment
            // packets here, though.

            if ( resetPacket ) {
                logger.debug( " received a reset packet" );
                receivedResetPacket( payloadBuffer );
            } else {

                int sequenceNumber = payloadBuffer.getShort();
                int index = payloadBuffer.getShort();
                count = payloadBuffer.getShort();

                logger.debug( "---sequenceNumber={}", sequenceNumber );
                logger.debug( "---index={}", index );
                logger.debug( "---count={}", count );

                // If the count is greater than one, we have a
                // fragment, and we should handle that separately.
                if ( count > 1 ) {
                    processReceivedFragment( sequenceNumber, index, count, payloadBuffer.array() );
                } else {
                    logger.debug( " delivering to channel manager" );

                    // Mark that we received it, so we can ack properly.
                    logger.debug( "Marking for ack: {}", sequenceNumber );
                    mMarkedForAck.add( sequenceNumber );
                    
                    // Removes the remaining SATCOM stuff from the
                    // protocol buffers payload.
                    agm.removeSatcomHeaderFromPayload();

                    result = mChannelManager.deliver( agm );
                }
            }
        } else if ( type == ACK ) {
            // Ack packet
            logger.debug( "type==ACK, mSynced={}", mSynced );
            if ( !mSynced ) {
                logger.debug( "  ack packet received. Stopping the reset packet timer." );
                stopResetTimer();
                mSynced = true;
                logger.debug( "XXXX mSynced={}", mSynced );
                
                // We're connected and we have the token.  Start off the token
                // passing process by sending the token to the other side.
                sendToken();
            } else {
                logger.debug( "  ack packet received." );
                // Received an ack packet.  That means that the token was received
                // on the other side.
                processAck( count, payloadBuffer );
            }
        } else if ( type == TOKEN ) {
            logger.debug( "  token packet received." );

            // If the low seven bits of count match the cached value,
            // this is a duplicate, so ignore this token.  If not,
            // process the token packet.
            logger.debug( "count={}", count );
            logger.debug( "The &={}", (count & 0x00007FFF) );
            logger.debug( "mLastTokenReceived={}", mLastTokenReceived );
            logger.debug( "Is equals? = ", (count & 0x00007FFF) == mLastTokenReceived );

            if ( (count & 0x00007FFF) == mLastTokenReceived ) {
                logger.debug( "Received duplicate token packet={}. Discarding...", mLastTokenReceived );
            } else {
                // When an token packet is received
                //   1. Disable the timer if it is set,
                //   2. Move all the messages in mSmallQueue to
                //   mSenderQueue.

                mLastTokenReceived = count & 0x00007FFF;
                logger.debug( "Token packet number={}.", mLastTokenReceived );

                // First send an empty ack packet.
                logger.debug( "  adding ack packet to sender queue." );
                mChannel.addMessageToSenderQueue( createAckPacket() );

                // Now send all the packets in the small queue.
                AmmoGatewayMessage message = mSmallQueue.poll();
                while ( message != null ) {
                    logger.debug( "  moving message from small queue to sender queue." );
                    logger.debug( "mSmallQueue.size() = {}", mSmallQueue.size() );
                    WrappedAgm wagm = wrapAgm( message );

                    mChannel.addMessageToSenderQueue( wagm.mAgm );

                    // Once we've put the message in the send queue, also
                    // add it to the mWaitingToBeAcked queue, if the
                    // message needs to be acked.
                    if ( wagm.mAgm.mReliable ) {
                        logger.debug( "Putting {} in mWaitingToBeAcked", wagm );
                        mWaitingToBeAcked.add( wagm );
                    } else {
                        logger.debug( "Not putting wagm in mWaitingToBeAcked" );
                    }

                    message = mSmallQueue.poll();
                }

                // Resend any unacked small packets each time.  Once
                // they're acked, they won't be in this list anymore, so
                // just send the whole list each time.
                if ( !mWaitingToBeAcked.isEmpty() ) {
                    for ( WrappedAgm wagm : mWaitingToBeAcked ) {
                        mChannel.addMessageToSenderQueue( wagm.mAgm );
                    }
                }

                if ( mCurrentLarge != null ) {
                    sendFragments();
                }

                // After that, add a token packet to the queue, too.
                sendToken();
            }
        } else {
            logger.debug( "  Invalid packet type received. Discarding..." );
        }

        return result;
    }


    //
    // Token-related members.
    //
    private void sendToken()
    {
        logger.debug( "  adding token packet to sender queue." );

        // Note: we are putting the token in the send queue, but since we
        // don't know when it actually gets sent, we have to make the timer
        // duration long enough to take that into account.  We may want to
        // do some sort of callback later on for when the send queue becomes
        // empty.

        final int newTokenNumber = (mLastTokenReceived + 1) % 0x00007FFF;
        logger.debug( "Sending token number {}.", newTokenNumber );

        if ( mTokenTimerHandle != null ) {
            logger.error( "Tried to create token timer when we already had one." );
        }
        final Runnable tokenTimerRunnable = new Runnable() {
                public void run() {
                    logger.debug( "Sending a token packet" );
                    mChannel.addMessageToSenderQueue( createTokenPacket(newTokenNumber) );
                }
            };

        mTokenTimerHandle = mScheduler.scheduleAtFixedRate( tokenTimerRunnable,
                                                            0, // initial delay
                                                            TOKEN_PACKET_INTERVAL,
                                                            TimeUnit.MILLISECONDS );
    }


    private void stopTokenTimer()
    {
        logger.debug( "SerialFragmenter::stopTokenTimer()" );

        if ( mTokenTimerHandle != null ) {
            logger.debug( "  stopping the token timer" );
            boolean result = mTokenTimerHandle.cancel( true );
            logger.debug( "  returned: {}", result );
            mTokenTimerHandle = null;
        }
    }


    /**
     * This function takes an AmmoGatewayMessage that came from the distributor
     * and wraps is with additional SATCOM channel information.
     */
    private WrappedAgm wrapAgm( AmmoGatewayMessage orig )
    {
        WrappedAgm wagm = new WrappedAgm();

        byte[] old_payload = orig.payload; 
        byte[] new_payload = new byte[orig.payload.length + 7];

        ByteBuffer buf = ByteBuffer.wrap( new_payload );
        buf.order( ByteOrder.LITTLE_ENDIAN );

        // For wrapped packets, messageType should be 0x10. (1 byte)
        // NOTE: 0x10 is for when we don't need ack.  With reliable
        // chat messages and dash events, we need to use 0x30.
        buf.put( (byte) 0x30 );
        
        // Sequence number (2 bytes)
        
        buf.putShort( mSequenceNumber );
        wagm.mSequenceNumber = mSequenceNumber;
        incrementSequenceNumber( 1 );

        // Index of packet in multi-packet sequence (0 for wrapped packets) (2 bytes)
        buf.putShort( (short) 0 );

        // Total number of packets in multi-packet sequence (1 for wrapped packets) (2 bytes)
        buf.putShort( (short) 1 );

        // Now put old_payload in
        buf.put( old_payload );

        // Compute the checksum of the new payload.
        AmmoGatewayMessage.CheckSum csum = AmmoGatewayMessage.CheckSum.newInstance( new_payload );
        long payload_checksum = csum.asLong();

        AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder();
        agmb.version( AmmoGatewayMessage.VERSION_1_SATCOM )
            .payload( new_payload )
            .size( new_payload.length )
            .checksum( payload_checksum );

        wagm.mAgm = agmb.build();

        return wagm;
    }


    private AmmoGatewayMessage createTokenPacket( int tokenNumber )
    {
        logger.debug( "creating token packet. tokenNumber={}", tokenNumber );

        int newBytes = (0x00008000 | (tokenNumber & 0x00007FFF));
        byte highByte = (byte) ((newBytes >>> 8) & 0x000000FF);
        byte lowByte = (byte) (newBytes & 0x000000FF);

        // Remember: the second and third bytes are a short using LSB,
        // so put the lowByte in, then the highByte.
        byte[] payload = { (byte) 0xB0, lowByte, highByte };

        return createPacket( payload );
    }


    // Original
    // private AmmoGatewayMessage createTokenPacket( int tokenNumber )
    // {
    //     // Remember to put the last two bytes in in reverse order,
    //     // since they need to be LSB.
    //     byte[] payload = { (byte) 0xB0, (byte) 0x00, (byte) 0x80 };
    //     return createPacket( payload );
    // }


    private AmmoGatewayMessage createResetPacket()
    {
        // The current reset packet is the following:
        // byte[] payload = { (byte) 0x70, (byte) 0x00, (byte) 0x00 };
        // However, to do handheld to handheld, we have to add a 32-bit
        // number to the end, which will be the CRC32 of the operator_id.

        byte[] payload = new byte[7];

        payload[0] = (byte) 0x70;  // messageType
        payload[1] = (byte) 0x00;  // 2-byte count of acks
        payload[2] = (byte) 0x00;

        // Four-byte crc of operator id
        payload[3] = mOperatorIdCrc[3];
        payload[4] = mOperatorIdCrc[2];
        payload[5] = mOperatorIdCrc[1];
        payload[6] = mOperatorIdCrc[0];

        return createPacket( payload );
    }


    // Reset packets are only received by the Android that should
    // assume the gateway role.  Be sure that this function sets
    // mSynced to true.
    private void receivedResetPacket( ByteBuffer buf )
    {
        //reinitAllMembers();

        byte[] crcBuf = new byte[4];

        crcBuf[3] = buf.get();
        crcBuf[2] = buf.get();
        crcBuf[1] = buf.get();
        crcBuf[0] = buf.get();

        // Now create an int so we can compare it.
        int theirs = (crcBuf[3] <<  24)
                   | (crcBuf[2] <<  16)
                   | (crcBuf[1] <<   8)
                   |  crcBuf[0];

        logger.error( "Their id={}, My id={}", mOperatorIdAsInt, theirs );

        if ( !mHandheldRole ) {
            // Send a reset ack here.
            logger.debug( "  adding reset ack to sender queue." );
            mChannel.addMessageToSenderQueue( createEmptyAckPacket() );
        }

        mSynced = true;
    }



    private void reinitAllMembers()
    {
        logger.debug( "reinitAllMembers()" );

        // Flush out an reinit all variables.
        stopResetTimer();
        stopTokenTimer();

        // FIXME: do we need to tell the Distributor about these packets?
        // For now I'll just punt and flush both queues.
        mSmallQueue.clear();
        mLargeQueue.clear();

        mWaitingToBeAcked.clear();

        mFR = null;
        mCurrentLarge = null;  // FIXME: we may also need to tell the distributor about this.
        mCurrentLargeBaseSequence = 0;
        mNumberOfFragments = 0;

        mAckedPackets = null;

        mMarkedForAck.clear();

        mSynced = false;

        mLastTokenReceived = 0;
        mSequenceNumber = 0;
    }



    // This was used before we the receiving capability was added.  It
    // should be removed eventually.
    private AmmoGatewayMessage createEmptyAckPacket()
    {
        byte[] payload = { (byte) 0x90, (byte) 0x00, (byte) 0x00 };
        return createPacket( payload );
    }


    private AmmoGatewayMessage createAckPacket()
    {
        logger.debug( "createAckPacket()" );

        int size = 1 + 2 + (mMarkedForAck.size() * 2);
        byte[] payload = new byte[ size ];
        ByteBuffer buf = ByteBuffer.wrap( payload );
        buf.order( ByteOrder.LITTLE_ENDIAN );

        if ( mHandheldRole ) {
            buf.put( (byte) 0x90 );  // handheld role
        } else {
            buf.put( (byte) 0x80 );  // gateway role
        }

        int count = mMarkedForAck.size();
        logger.debug( "  count={}", count );

        buf.putShort( (short) count );

        while ( count > 0 ) {
            Integer current = mMarkedForAck.poll();
            logger.debug( "  sequenceNumber={}", current );
            buf.putShort( current.shortValue() );
            --count;
        }

        return createPacket( payload );
    }


    private AmmoGatewayMessage createPacket( byte[] payload )
    {
        AmmoGatewayMessage.CheckSum csum = AmmoGatewayMessage.CheckSum.newInstance( payload );
        long payload_checksum = csum.asLong();

        AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder();
        agmb.version( AmmoGatewayMessage.VERSION_1_SATCOM )
            .payload( payload )
            .size( payload.length )
            .checksum( payload_checksum );

        return agmb.build();
    }


    private void incrementSequenceNumber( int amount )
    {
        mSequenceNumber = (short) ((mSequenceNumber + amount) % Short.MAX_VALUE);
    }


    private static final int MAX_PACKET_SIZE = 1000;

    private static final int FRAGMENTS_PER_TOKEN = 1;

    // Need a queue here, to hold the same things that the SenderQueue holds.
    // Make this one less than the size of mSendQueue, since we'll need room
    // for the token packet.
    private static final int FRAGMENTER_SENDQUEUE_MAX_SIZE = 19;

    private BlockingQueue<AmmoGatewayMessage> mSmallQueue
        = new LinkedBlockingQueue<AmmoGatewayMessage>( FRAGMENTER_SENDQUEUE_MAX_SIZE );

    private BlockingQueue<AmmoGatewayMessage> mLargeQueue
        = new LinkedBlockingQueue<AmmoGatewayMessage>( FRAGMENTER_SENDQUEUE_MAX_SIZE );

    // Token timer-related members.
    private final ScheduledExecutorService mTokenScheduler =
        Executors.newScheduledThreadPool( 1 );
    private volatile ScheduledFuture<?> mTokenTimerHandle = null;


    //
    // Members related to reliable small queue messages
    //

    private class WrappedAgm {
        AmmoGatewayMessage mAgm;
        short mSequenceNumber;
    };

    // Contains a list of WRAPPED AmmoGatewayMessages.  Each item can be resent as-is.
    private List<WrappedAgm> mWaitingToBeAcked = new ArrayList<WrappedAgm>(100);

    //
    // Fragment receiving members
    //

    private class FragmentReceiver {
        byte[] mIncomingBytes;
        int mBaseSequenceNumber;
        int mNumberOfFragments;
        int sizeOfLastFragment;

        BitSet mReceived;
    };

    private FragmentReceiver mFR = null;

    //
    // Fragment sending members
    //

    // true if we're acting as a handheld and controlling the start of token exchange;
    // false if we're acting as a gateway.
    private volatile boolean mHandheldRole = true;

    // Reference to the current large packet we're sending.
    private volatile AmmoGatewayMessage mCurrentLarge = null;
    private volatile int mCurrentLargeBaseSequence = 0;
    private volatile int mNumberOfFragments = 0;

    // BitSet to tell what parts have been acknowledged
    private BitSet mAckedPackets = null;

    // As we receive packets, we put the sequence numbers in here.
    // When we send an ack packet, we flush it.  This is a concurrent queue
    // since both the sender and receiver threads have to access it.
    private ConcurrentLinkedQueue<Integer> mMarkedForAck = new ConcurrentLinkedQueue<Integer>();

    //
    // Other members
    //

    // False when we initially start (and are sending reset packets to the gateway), but
    // True once we have received an ack to a reset packet.
    private volatile boolean mSynced = false;

    private volatile int mLastTokenReceived = 0;


    private volatile short mSequenceNumber = 0;

    private static final byte[] mOperatorIdCrc = new byte[4];
    private int mOperatorIdAsInt = 0;

    private final ScheduledExecutorService mScheduler =
        Executors.newScheduledThreadPool( 1 );
    private volatile ScheduledFuture<?> resetterHandle = null;

    private SerialChannel mChannel;
    private IChannelManager mChannelManager;
    private Context mContext;
}

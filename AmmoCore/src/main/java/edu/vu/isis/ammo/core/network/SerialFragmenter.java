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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


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
    private static final int TOKEN_PACKET_INTERVAL = 5000; // Make this configurable


    public SerialFragmenter( SerialChannel channel, IChannelManager channelManager )
    {
        logger.debug( "Constructor" );

        mChannel = channel;
        mChannelManager = channelManager;

        // String operatorId = mChannelManager.getOperatorId();

        // // CRC this and put the bytes in mOperatorIdCrc.
        // byte bytes[] = operatorId.getBytes();
        // Checksum checksum = new CRC32();
        // checksum.update( bytes, 0, bytes.length );
        // long checksumValue = checksum.getValue();

        // mOperatorIdAsInt = (int) checksumValue;

        // mOperatorIdCrc[0] = (byte) checksumValue;
        // mOperatorIdCrc[1] = (byte) (checksumValue >>> 8);
        // mOperatorIdCrc[2] = (byte) (checksumValue >>> 16);
        // mOperatorIdCrc[3] = (byte) (checksumValue >>> 24);

        //logger.debug( "Created Fragmenter for operatorID={}", operatorId );
    }


    public void destroy()
    {
        stopResetTimer();
        stopTokenTimer();
    }


    public void startSending()
    {
        // We used to just send a token using the above line.
        // mChannel.addMessageToSenderQueue( createResetPacket() );

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
    }


    public void stopResetTimer()
    {
        // We received an acknowledgement for an ack, so disable the timer.
        if ( resetterHandle != null ) {
            resetterHandle.cancel( true );
            resetterHandle = null;

            mSynced = true;
        }
    }


    /**
     *
     */
    public DisposalState putFromDistributor( AmmoGatewayMessage agm )
    {
        logger.debug( "putFromDistributor()" );

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
            mNumberOfFragments = mCurrentLarge.payload.length % MAX_PACKET_SIZE;
            if ( mCurrentLarge.payload.length - (mNumberOfFragments * MAX_PACKET_SIZE) > 0 ) {
                ++mNumberOfFragments;
            }

            mAckedPackets = new BitSet( mNumberOfFragments );
        }
    }


    /**
     * FIXME: Not used; sending an interim build to Brad.
     */
    private synchronized void sendFragments()
    {
        for ( int i = 0; i < FRAGMENTS_PER_TOKEN; ++i ) {
            // Find lowest bit in BitSet that is false
            int index = mAckedPackets.nextClearBit( 0 );
            
            // The last fragment's length may be a fraction of MAX_PACKET_SIZE.
            // Figure out what it should be.
            int lengthOfFragment = MAX_PACKET_SIZE;
            if ( index == mAckedPackets.size() ) {
                lengthOfFragment = mCurrentLarge.payload.length - (mNumberOfFragments * MAX_PACKET_SIZE);
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
            buf.putShort( (short) mAckedPackets.size() );

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
        }
    }


    /**
     *
     */
    private synchronized void processAck( int count, ByteBuffer buf )
    {
        for ( int i = 0; i < count; ++i ) {
            // Unset the bits with the numbers in the ack.
            short sequenceNumber = buf.getShort();

            short index = (short) (sequenceNumber - mCurrentLargeBaseSequence);
            if ( index < 0 ) {
                index += Short.MAX_VALUE;
            }

            mAckedPackets.set( index );
        }

        // Test the BitSet to see if all are true. If so, start
        // sending the next packet if there is one in the queue.
        if ( mAckedPackets.cardinality() == mAckedPackets.size() ) {
            // FIXME: remove teh packet from the queue and tell the distributor.
            // Reset the relevent member variables.


            // FIXME: disabling for sending an interim build for Brad
            // startSendingLargePacket();
        }
    }










    /**

     * Called when the SatcomReceiverThread receives a message from the network
     */
    public boolean deliver( AmmoGatewayMessage agm )
    {
        logger.debug( "  deliver()" );
        boolean result = false;


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

            if ( (count & 0x00008000) != 0 ) {
                type = TOKEN;
            } else {
                type = ACK;
            }
        }

        // For an ack or data packet, disable the token timer if it's enabled.
        // Actually, we probably want to disable it even if we receive a token
        // packet, too.
        if ( mTokenSent == true ) { //&& (type == ACK || type == DATA) ) {
            stopTokenTimer();
        }

        if ( type == DATA ) {
            // Data packets can be either reset packets or things
            // destined for the distributor.  For normal packets, send
            // them up to the distributor.  Process the fragment
            // packets here, though.

            if ( resetPacket ) {
                logger.debug( " received a reset packet" );
                //receivedResetPacket( payloadBuffer );
            } else {
                logger.debug( " delivering to channel manager" );
                result = mChannelManager.deliver( agm );
            }
        } else if ( type == ACK ) {
            // Ack packet
            if ( !mSynced ) {
                logger.debug( "  ack packet received. Stopping the reset packet timer." );
                stopResetTimer();

                // We're connected and we have the token.  Start off the token
                // passing process by sending the token to the other side.
                sendToken();
            } else {
                logger.debug( "  ack packet received.  Doing nothing for now." );
                // Received an ack packet.  That means that the token was received
                // on the other side.

                // FIXME: Disabled for sending an interim build to Brad.
                //processAck( count, payloadBuffer );
            }
        } else if ( type == TOKEN ) {
            logger.debug( "  token packet received." );
            // For ack packets, check off the fragments that were received, if
            // the packets being acked were fragment.

            // When an ack packet is received (which gives us the token):
            //   1. Disable the timer if it is set,
            //   2. Move all the messages in mSmallQueue to
            //   mSenderQueue. (This isn't public, so I may need to add a
            //   method to add some way to let it be put directly in the
            //   queue, rather that going through the normal mechanism.

            // First send an empty ack packet.
            logger.debug( "  adding ack packet to sender queue." );
            mChannel.addMessageToSenderQueue( createEmptyAckPacket() );

            // Now send all the packets in the small queue.
            AmmoGatewayMessage message = mSmallQueue.poll();
            while ( message != null ) {
                logger.debug( "  moving message from small queue to sender queue." );
                logger.debug( "mSmallQueue.size() = {}", mSmallQueue.size() );
                AmmoGatewayMessage wrappedAgm = wrapAgm( message );
                mChannel.addMessageToSenderQueue( wrappedAgm );
                message = mSmallQueue.poll();
            }

            // After that, add a token packet to the queue, too.
            sendToken();
        } else {
            logger.debug( "  Invalid packet type received. Discarding..." );
        }

        return result;
    }


    //
    // Token-related memebers.
    //
    private volatile boolean mTokenSent = false;

    private final ScheduledExecutorService mTokenScheduler =
        Executors.newScheduledThreadPool( 1 );
    private ScheduledFuture<?> mTokenTimerHandle = null;



    private void sendToken()
    {
        logger.debug( "  adding token packet to sender queue." );

        // Note: we are putting the token in the send queue, but since we
        // don't know when it actually gets sent, we have to make the timer
        // duration long enough to take that into account.  We may want to
        // do some sort of callback later on for when the send queue becomes
        // empty.
        mChannel.addMessageToSenderQueue( createTokenPacket() );

        if ( resetterHandle != null ) {
            logger.error( "Tried to create reset timer when we already had one." );
        }
        final Runnable resetter = new Runnable() {
                public void run() {
                    logger.debug( "Sending a token packet" );
                    mChannel.addMessageToSenderQueue( createTokenPacket() );
                }
            };

        mTokenTimerHandle = mScheduler.scheduleAtFixedRate( resetter, 
                                                            0, // initial delay
                                                            TOKEN_PACKET_INTERVAL,
                                                            TimeUnit.MILLISECONDS );
    }


    private void stopTokenTimer()
    {
        // We received an acknowledgement for an ack, so disable the timer.
        if ( mTokenTimerHandle != null ) {
            mTokenTimerHandle.cancel( true );
            mTokenTimerHandle = null;

            mTokenSent = false;
        }
    }


    private short mSequenceNumber = 0;

    private void incrementSequenceNumber( int amount )
    {
        mSequenceNumber = (short) ((mSequenceNumber + amount) % Short.MAX_VALUE);
    }


    /**
     * This function takes an AmmoGatewayMessage that came from the distributor
     * and wraps is with additional SATCOM channel information.
     */
    private AmmoGatewayMessage wrapAgm( AmmoGatewayMessage orig )
    {
        byte[] old_payload = orig.payload; 
        byte[] new_payload = new byte[orig.payload.length + 7];

        ByteBuffer buf = ByteBuffer.wrap( new_payload );
        buf.order( ByteOrder.LITTLE_ENDIAN );

        // For wrapped packets, messageType should be 0x10. (1 byte)
        buf.put( (byte) 0x10 );
        
        // Sequence number (2 bytes)
        buf.putShort( mSequenceNumber );
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

        return agmb.build();
    }


    private AmmoGatewayMessage createTokenPacket()
    {
        byte[] payload = { (byte) 0xB0, (byte) 0x00, (byte) 0x80 };
        return createPacket( payload );
    }


    private AmmoGatewayMessage createResetPacket()
    {
        // The current reset packet is the following:
        // byte[] payload = { (byte) 0x70, (byte) 0x00, (byte) 0x00 };
        // However, to do handheld to handheld, we have to add a 32-bit
        // number to the end, which will be the CRC32 of the operator_id.

        //byte[] payload = new byte[7];
        byte[] payload = new byte[3];

        payload[0] = (byte) 0x70;  // messageType
        payload[1] = (byte) 0x00;  // 2-byte count of acks
        payload[2] = (byte) 0x00;

        // Four-byte crc of operator id
        // payload[3] = mOperatorIdCrc[3];
        // payload[4] = mOperatorIdCrc[2];
        // payload[5] = mOperatorIdCrc[1];
        // payload[6] = mOperatorIdCrc[0];

        return createPacket( payload );
    }


    // private void receivedResetPacket( ByteBuffer buf )
    // {
    //     byte[] crcBuf = new byte[4];

    //     crcBuf[3] = buf.get();
    //     crcBuf[2] = buf.get();
    //     crcBuf[1] = buf.get();
    //     crcBuf[0] = buf.get();

    //     // Now create an int so we can compare it.
    //     int theirs = (crcBuf[3] <<  24)
    //                | (crcBuf[2] <<  16)
    //                | (crcBuf[1] <<   8)
    //                |  crcBuf[0];

    //     logger.error( "Their id={}, My id={}", mOperatorIdAsInt, theirs );
    // }


    private AmmoGatewayMessage createEmptyAckPacket()
    {
        byte[] payload = { (byte) 0x90, (byte) 0x00, (byte) 0x00 };
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

    private static final int MAX_PACKET_SIZE = 1000;

    private static final int FRAGMENTS_PER_TOKEN = 5;

    // Need a queue here, to hold the same things that the SenderQueue holds.
    // Make this one less than the size of mSendQueue, since we'll need room
    // for the token packet.
    private static final int FRAGMENTER_SENDQUEUE_MAX_SIZE = 19;

    private BlockingQueue<AmmoGatewayMessage> mSmallQueue
        = new LinkedBlockingQueue<AmmoGatewayMessage>( FRAGMENTER_SENDQUEUE_MAX_SIZE );

    private BlockingQueue<AmmoGatewayMessage> mLargeQueue
        = new LinkedBlockingQueue<AmmoGatewayMessage>( FRAGMENTER_SENDQUEUE_MAX_SIZE );

    // Reference to the current large packet we're sending.
    private volatile AmmoGatewayMessage mCurrentLarge = null;
    private volatile int mCurrentLargeBaseSequence = 0;
    private volatile int mNumberOfFragments = 0;

    // BitSet to tell what parts have been acknowledged
    private BitSet mAckedPackets = null;

    // False when we initially start (and are sending reset packets to the gateway), but
    // True once we have received an ack to a reset packet.
    private boolean mSynced = false;

    //private static final byte[] mOperatorIdCrc = new byte[4];
    //private int mOperatorIdAsInt = 0;

    private final ScheduledExecutorService mScheduler =
        Executors.newScheduledThreadPool( 1 );
    private ScheduledFuture<?> resetterHandle = null;

    private SerialChannel mChannel;
    private IChannelManager mChannelManager;
}

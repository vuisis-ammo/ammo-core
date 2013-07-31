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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

    public SerialFragmenter( SerialChannel channel, IChannelManager channelManager )
    {
        logger.debug( "Constructor" );

        mChannel = channel;
        mChannelManager = channelManager;
    }


    public void startSending()
    {
        mChannel.addMessageToSenderQueue( createTokenPacket() );
    }


    /**
     *
     */
    public DisposalState putFromDistributor( AmmoGatewayMessage iMessage )
    {
        logger.debug( "putFromDistributor()" );

        // WRT payload size:
        // Put packets larger than a certain size in the large queue,
        // and packets smaller than a certain size in the normal queue.

        // For now, put all packets in the mSmallQueue
        if ( !mSmallQueue.offer( iMessage )) {
            logger.debug( "serial channel not taking messages {}",
                          DisposalState.BUSY );
            return DisposalState.BUSY;
        }

        // We may want to add the busy and low water code here, like we have
        // in the normal DistQueue.  I'm leaving it out for now.

        return DisposalState.QUEUED;
    }


    /**
     * Called when the SatcomReceiverThread receives a message from the network
     */
    public boolean deliver( AmmoGatewayMessage agm )
    {
        boolean result = false;


        // When the token packet is sent, set the timeout.  Upon
        // expiring, we put another token in the queue and set the
        // timeout again.

        byte[] payload = agm.payload; 
        byte messageType = payload[0];

        byte packetType = (byte) (messageType & 0x80);
        boolean resetPacket = (messageType & 0x40) != 0;
        boolean shouldAck = (messageType & 0x20) != 0;
        byte identifier = (byte) (messageType & 0x1F);

        if ( packetType == 0 ) {
            // For normal packets, send them up to the distributor.
            logger.debug( " delivering to channel manager" );
            result = mChannelManager.deliver( agm );
        } else {
            // For ack packets, check off the fragments that were received, if
            // the packets being acked were fragment.

            // When an ack packet is received (which gives us the token):
            //   1. Disable the timer if it is set,
            //   2. Move all the messages in mSmallQueue to
            //   mSenderQueue. (This isn't public, so I may need to add a
            //   method to add some way to let it be put directly in the
            //   queue, rather that going through the normal mechanism.
            AmmoGatewayMessage message = mSmallQueue.poll();
            while ( message != null ) {
                mChannel.addMessageToSenderQueue( message );
                message = mSmallQueue.poll();
            }

            //   3. After that, add a token packet to the queue, too.
            mChannel.addMessageToSenderQueue( createTokenPacket() );
        }

        return result;
    }


    private AmmoGatewayMessage createTokenPacket()
    {
        final int payloadSize = 3;
        byte[] payload = new byte[payloadSize];

        payload[0] = (byte) 0xA0;
        payload[1] = (byte) 0x00;
        payload[2] = (byte) 0x80;

        AmmoGatewayMessage.CheckSum csum = AmmoGatewayMessage.CheckSum.newInstance( payload );
        long payload_checksum = csum.asLong();

        AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder();
        agmb.version( AmmoGatewayMessage.VERSION_1_SATCOM )
            .payload( payload )
            .size( payloadSize )
            .checksum( payload_checksum );

        return agmb.build();
    }

    // Need a queue here, to hold the same things that the SenderQueue holds.
    // Make this one less than the size of mSendQueue, since we'll need room
    // for the token packet.
    private static final int FRAGMENTER_SENDQUEUE_MAX_SIZE = 19;

    private BlockingQueue<AmmoGatewayMessage> mSmallQueue
        = new LinkedBlockingQueue<AmmoGatewayMessage>( FRAGMENTER_SENDQUEUE_MAX_SIZE );

    private SerialChannel mChannel;
    private IChannelManager mChannelManager;
}

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

import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * We can pass in fewer of these arguments to the constructor if we make this
 * a private class of the SerialChannel.  Wait until we see how big this class
 * will get before we decide.
 */
public class SerialRetransmitter
{
    // There are lots of shorts in this code that will need to be made larger
    // if we need to be able to have more than 16 slots.
    private class PacketStats {
        short mExpectToHearFrom;
        short mHeardFrom;
        int mRemainingTimesToSend;

        AmmoGatewayMessage mPacket;
    };

    Map<Short, PacketStats> mTable = new HashMap<Short, PacketStats>();


    // The following members are for keeping track of packets we have received
    // so that we can construct the ack packet.
    private byte [] currentHyperperiod = new byte[16];
    private byte [] previousHyperperiod = new byte[16];

    private int numberOfCurrentHyperperiod;
    private int numberOfPreviousHyperperiod;


    /**
     *
     */
    public SerialRetransmitter( SerialChannel channel, IChannelManager channelManager )
    {
        logger.trace( "SerialRetransmitter::SerialRetransmitter()" );
        mChannel = channel;
        mChannelManager = channelManager;
    }


    /**
     * All messages received are passed to this method. It delivers the message
     * to the channel manager if it is a new message (rather than one older than
     * the current most recent message of this terse topic for the given slot.
     * It also caches it as appropriate.
     */
    public void processReceivedMessage( AmmoGatewayMessage agm,
                                        boolean receiverEnabled,
                                        int hyperperiod,
                                        int mySlotID )
    {
        logger.trace( "SerialRetransmitter::processReceivedMessage(). hyperperiod={}, mySlotID={}",
                      hyperperiod, mySlotID );

        logger.trace( "...received messsage from slotID={}", agm.mSlotID );

        // Collect the ack statistics.
        // First, figure out if they are sending in what the retransmitter
        // thinks is the current slot, or if a new slot has started.








        // First, if it's an ack packet and any bit is set for the byte
        // corresponding to my slot, mark that this slot ID is actively
        // receiving from me.
        if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_ACK ) {
            int theirAckBitsForMe = agm.payload[ mySlotID ];
            if ( theirAckBitsForMe != 0 ) {
                // They are receiving my directly.
                mReceivingMeDirectly |= (0x1 << agm.mSlotID);
            }

            // We also use their ack information to tell which of the
            // packets that we sent in the last hyperperiod were actually
            // received by them.
            //
            // For each 1 bit in their ack byte, construct the UID for that
            // packet and look up the element in the map for that UID.
            // Mark that packet as received by their slotID.
            int index = 0;
            while ( theirAckBitsForMe != 0 ) {
                if ( (theirAckBitsForMe & 0x1) != 0 ) {
                    // They received a packet in the position in the slot
                    // with index "index".  Record this in the map.

                    short uid = createUID( hyperperiod, mySlotID, index );
                    PacketStats stats = mTable.get( uid );
                    if ( stats != null ) {
                        stats.mHeardFrom |= (0x1 << agm.mSlotID);

                        if ( stats.mExpectToHearFrom == stats.mHeardFrom ) {
                            // We have received acks from all of the people
                            // that we thought we were sending to, so we can
                            // now remove the packet from the table.
                            mTable.remove( uid );
                        }
                    } else {
                        logger.info( "Received an ack for a packet that is not in the table!" );
                    }
                }

                theirAckBitsForMe = theirAckBitsForMe >>> 1;
                ++index;
            }

        }




        // We have to do the delivery inside of this method, since if
        // we receive a resent packet that we've already delivered to
        // the distributor, we should discard it.
        if ( receiverEnabled ) {
            mChannel.deliverMessage( agm );
        } else {
            logger.trace( "Receiving disabled, discarding message." );
        }
    }


    /**
     * Called at the end of the 


    /**
     * Construct UID for message.
     */
    private short createUID( int hyperperiod, int slotIndex, int indexInSlot )
    {
        short uid = 0;
        uid |= hyperperiod << 8;
        uid |= slotIndex << 4;
        uid |= indexInSlot;

        return uid;
    }


    /**
     * If an AmmoGatewayMessage is returned, it will be sent in by the
     * SenderThread in the current slot.  If nothing will fit in the
     * bytesThatWillFit bytes that are remaining, return null and nothing
     * will be sent.  This function will be called repeatedly until it returns
     * null.
     */
    public AmmoGatewayMessage createResendPacket( long bytesThatWillFit )
    {
        logger.trace( "SerialRetransmitter::createResendPacket()" );

        AmmoGatewayMessage agm = null;

        // FIXME: Here is where the packet containing the retransmitted
        // packets will be constructed.

        // For all packets that are retransmitted, create a normal header but
        // set the packet type to 01, append the two byte unique identifier,
        // and then the original payload.


        return agm;
    }


    /**
     *
     */
    public AmmoGatewayMessage createAckPacket()
    {
        logger.trace( "SerialRetransmitter::createAckPacket()" );

        AmmoGatewayMessage agm = null;

        return agm;
    }


    /**
     * I'm not sure when to call this.  Decide later.
     */
    public void resetReceivingMeDirectly() { mReceivingMeDirectly = 0; }


    private SerialChannel mChannel;
    private IChannelManager mChannelManager;

    private short mReceivingMeDirectly = 0;

    private static final Logger logger = LoggerFactory.getLogger( "net.serial.retrans" );
}

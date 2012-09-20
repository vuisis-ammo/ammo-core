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
import java.util.zip.CRC32;

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

    private int currentHyperperiodID;
    private int previousHyperperiodID;


    /**
     *
     */
    public SerialRetransmitter( SerialChannel channel, IChannelManager channelManager )
    {
        logger.trace( "SerialRetransmitter::SerialRetransmitter()" );
        mChannel = channel;
        mChannelManager = channelManager;
    }


    synchronized public void swapHyperperiodsIfNeeded( int hyperperiod )
    {
        logger.trace( "...swapHyperperiods(). new hyperperiod={}", hyperperiod );

        if ( hyperperiod != currentHyperperiodID ) {
            logger.trace( "...swapping" );
            // We've entered a new hyperperiod, so make current point to the
            // new one, and discard the previous one.
            final byte [] temp = previousHyperperiod;
            previousHyperperiod = currentHyperperiod;
            currentHyperperiod = temp;

            // Reset the new current to all zeros.
            for ( int i = 0; i < currentHyperperiod.length; ++i )
                currentHyperperiod[ i ] = 0;

            previousHyperperiodID = currentHyperperiodID;
            currentHyperperiodID = hyperperiod;
        }
    }


    /**
     * All messages received are passed to this method. It delivers the message
     * to the channel manager if it is a new message (rather than one older than
     * the current most recent message of this terse topic for the given slot.
     * It also caches it as appropriate.
     */
    synchronized public void processReceivedMessage( AmmoGatewayMessage agm,
                                                     boolean receiverEnabled,
                                                     int hyperperiod,
                                                     int mySlotID )
    {
        logger.trace( "SerialRetransmitter::processReceivedMessage(). hyperperiod={}, mySlotID={}",
                      hyperperiod, mySlotID );

        logger.trace( "...received messsage from slotID={}, type={}",
                      agm.mSlotID, agm.mPacketType );

        //
        // Collect the ack statistics.
        //

        // First, figure out if they are sending in what the retransmitter
        // thinks is the current slot, or if a new slot has started.  If so,
        // save off the current slot stats as previous and start collecting
        // new stats.
        swapHyperperiodsIfNeeded( hyperperiod );

        byte bits = currentHyperperiod[ agm.mSlotID ];
        logger.trace( "...before: bits={}, indexInSlot={}", bits, agm.mIndexInSlot );
        bits |= (0x1 << agm.mIndexInSlot);
        logger.trace( "...after: bits={}", bits );
        currentHyperperiod[ agm.mSlotID ] = bits;




        // First, if it's an ack packet and any bit is set for the byte
        // corresponding to my slot, mark that this slot ID is actively
        // receiving from me.
        if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_ACK ) {
            logger.trace( "Received ack packet. payload={}", agm.payload );

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

        // HACK: We need to do something different about knowing when to pass
        // the packet up, but for now just discard the ack packets.
        if ( receiverEnabled && agm.mPacketType != AmmoGatewayMessage.PACKETTYPE_ACK ) {
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
    synchronized public AmmoGatewayMessage createResendPacket( long bytesThatWillFit )
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
    synchronized public AmmoGatewayMessage createAckPacket( int hyperperiod )
    {
        logger.trace( "SerialRetransmitter::createAckPacket(). hyperperiod={}",
                      hyperperiod );

        try {

            // We only send an ack if the previous hyperperiod was the preceding
            // one.  If the previous hyperperiod was an older one, just don't
            // send anything.
            if ( hyperperiod - 1 != previousHyperperiodID ) {
                logger.trace( "wrong hyperperiod: current={}, previous={}",
                              hyperperiod, previousHyperperiodID );
                return null;
            }

            AmmoGatewayMessage.Builder b = AmmoGatewayMessage.newBuilder();

            b.size( previousHyperperiod.length );
            b.payload( previousHyperperiod );

            CRC32 crc32 = new CRC32();
            crc32.update( previousHyperperiod );
            b.checksum( crc32.getValue() );

            AmmoGatewayMessage agm = b.build();
            logger.trace( "returning ack packet" );
            return agm;

        } catch ( Exception ex ) {
            logger.warn("createAckPacket() threw exception {}", ex.getStackTrace() );
        }

        return null;
    }


    /**
     * I'm not sure when to call this.  Decide later.
     */
    synchronized public void resetReceivingMeDirectly() { mReceivingMeDirectly = 0; }


    private SerialChannel mChannel;
    private IChannelManager mChannelManager;

    private short mReceivingMeDirectly = 0;

    private static final Logger logger = LoggerFactory.getLogger( "net.serial.retrans" );
}

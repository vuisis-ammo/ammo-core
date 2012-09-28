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

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
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
    private static final int TIMES_TO_RESEND = 3;

    // There are lots of shorts in this code that will need to be made larger
    // if we need to be able to have more than 16 slots.
    private class PacketRecord {
        short mExpectToHearFrom;
        short mHeardFrom;
        int mResend;

        AmmoGatewayMessage mPacket;

        PacketRecord( AmmoGatewayMessage agm ) {
            mPacket = agm;
            mExpectToHearFrom = mReceivingMeDirectly;
            mHeardFrom = 0;
            mResend = TIMES_TO_RESEND;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append( mPacket + ", " + Integer.toHexString(mExpectToHearFrom) + ", " );
            result.append( Integer.toHexString(mHeardFrom) + ", " + mResend );
            return result.toString();
        }
    };

    //Map<Integer, PacketRecord> mPool = new HashMap<Integer, PacketRecord>();

    // This class records information about packets sent in a slot and acks
    // received in a slot.  It is retained so that, once we receive acks for
    // our sent packets during the next slot, we can figure out if all
    // intended receivers have received each packet.
    private class SlotRecord {
        public int mHyperperiodID;
        public ArrayList<PacketRecord> mSent = new ArrayList<PacketRecord>(16);

        public int mSendCount;

        // FIXME: magic number - limits max radios in hyperperiod to 16
        // Should we optimize for smaller nets?
        public byte[] mAcks = new byte[16];

        public void SlotRecord() {
            for ( int i = 0; i < 10; ++i )
                mSent.add( null );
        }

        public void reset( int newHyperperiod ) {
            mHyperperiodID = newHyperperiod;
            mSent.clear();
            mSendCount = 0;

            for ( int i = 0; i < mAcks.length; ++i )
                mAcks[ i ] = 0;
        }

        public void setAckBit( int slotID, int indexInSlot ) {
            byte bits = mAcks[ slotID ];
            logger.trace( "...before: bits={}, indexInSlot={}", bits, indexInSlot );
            bits |= (0x1 << indexInSlot);
            logger.trace( "...after: bits={}", bits );
            mAcks[ slotID ] = bits;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append( mHyperperiodID + ", " );
            result.append( mSendCount + ", " );
            result.append( mSent.size() );
            return result.toString();
        }
    };


    Map<Integer, UUID> mUUIDMap = new HashMap<Integer, UUID>();

    private SlotRecord mCurrent = new SlotRecord();
    private SlotRecord mPrevious = new SlotRecord();


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

        if ( hyperperiod != mCurrent.mHyperperiodID ) {
            logger.trace( "...swapping" );
            logger.trace( "before:" );
            logger.trace( "  current: {}", mCurrent );
            logger.trace( "  previous: {}", mPrevious );

            // We've entered a new hyperperiod, so make current point to the
            // new one, and discard the previous one.
            final SlotRecord temp = mPrevious;
            mPrevious = mCurrent;
            mCurrent = temp;

            mCurrent.reset( hyperperiod );
            logger.trace( "after:" );
            logger.trace( "  current: {}", mCurrent );
            logger.trace( "  previous: {}", mPrevious );
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

        if ( hyperperiod != agm.mHyperperiod 


        //
        // Collect the ack statistics.
        //

        // First, figure out if they are sending in what the retransmitter
        // thinks is the current slot, or if a new slot has started.  If so,
        // save off the current slot stats as previous and start collecting
        // new stats.
        swapHyperperiodsIfNeeded( hyperperiod );

        // Set the bit in the current hyperperiod
        mCurrent.setAckBit( agm.mSlotID, agm.mIndexInSlot );


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

                    int uid = createUID( hyperperiod, mySlotID, index );
                    logger.trace( "doing mSent with index={}, mPrevious.mSendCount={}",
                                  index, mPrevious.mSendCount );
                    logger.trace( "mSent.size()={}", mPrevious.mSent.size() );
                    PacketRecord stats = mPrevious.mSent.get( index );
                    if ( stats != null ) {
                        stats.mHeardFrom |= (0x1 << agm.mSlotID);

                        // SKN: do this checking when you are done with all the previous
                        // and are about to discard mPrevious
                        // SKN: any packet not fully acknowledged at that mpoint should be
                        // queed up for resend

                        // if ( stats.mExpectToHearFrom == stats.mHeardFrom ) {
                        // We have received acks from all of the people
                        // that we thought we were sending to, so we can
                        // now remove the packet from the pool.
                        // mPool.remove( uid );
                        // }
                    } else {
                        logger.info( "Received an ack for a packet that is not in the table!" );
                    }
                }

                theirAckBitsForMe = theirAckBitsForMe >>> 1;
                ++index;

                // SKN: should generate an Ack message for up stack to tell them
                // that a packet from them was ack'd

            }

        }
        else if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_NORMAL ) {
            
            // propagate up
             mChannel.deliverMessage( agm );
        }
        else if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_RESEND ) {
            // if already seen then discard

            // else propagate up
             mChannel.deliverMessage( agm );
        }


        // We have to do the delivery inside of this method, since if
        // we receive a resent packet that we've already delivered to
        // the distributor, we should discard it.

        // HACK: We need to do something different about knowing when to pass
        // the packet up, but for now just discard the ack packets.
    }



    /**
     * Each packet that we send out is passed to this method.
     *
     * This method needs to do several things:
     * --Create a PacketRecord and put the AGM in it, setting the appropriate
     *   members in it.
     * --Put the PacketRecord in the SendRecord.
     * --Add the UID and UUID to the map.
     *
     * Note: I haven't decided yet whether resend and ack packets should be
     * passed to this method.  I'm leaning toward thinking that they should,
     * but their mNeedAck will be false.
     */
    synchronized public void sendingAPacket( AmmoGatewayMessage agm,
                                             int hyperperiod,
                                             int slotIndex,
                                             int indexInSlot )
    {
        logger.trace( "SerialRetransmitter::sendingAPacket()" );

        logger.trace( "...needAck={}", agm.mNeedAck );
        logger.trace( "...UUID={}", agm.mUUID );

        // The retransmitter functionality is only required for packet types
        // that require acks.
        //if ( !agm.mNeedAck )
        //    return;

        int uid = createUID( hyperperiod, slotIndex, indexInSlot );
        logger.trace( "...uid={}", uid );
        PacketRecord pr = new PacketRecord( agm );
        logger.trace( "...PacketRecord={}", pr );

        // mPool.put( uid, pr );
        //logger.trace( "...pool size={}", mPool.size() );
        mCurrent.mSent.add( pr );
        mCurrent.mSendCount++;
        logger.trace( "...mCurrent.mSendCount={}", mCurrent.mSendCount );

        mUUIDMap.put( uid, agm.mUUID );
        logger.trace( "...UID/UUID map size={}", mUUIDMap.size() );
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
            if ( hyperperiod - 1 != mPrevious.mHyperperiodID ) {
                logger.trace( "wrong hyperperiod: current={}, previous={}",
                              hyperperiod, mPrevious.mHyperperiodID );
                return null;
            }

            AmmoGatewayMessage.Builder b = AmmoGatewayMessage.newBuilder();

            b.size( mPrevious.mAcks.length );
            b.payload( mPrevious.mAcks );

            CRC32 crc32 = new CRC32();
            crc32.update( mPrevious.mAcks );
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
     * Construct UID for message.
     */
    private int createUID( int hyperperiod, int slotIndex, int indexInSlot )
    {
        int uid = 0;
        uid |= hyperperiod << 16;
        uid |= slotIndex << 8;
        uid |= indexInSlot;

        return uid;
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

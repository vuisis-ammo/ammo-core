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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
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
    private static final int DEFAULT_RESENDS = 3; // original send + N-retries
    private static final int MAX_PACKETS_PERSLOT = 7; // this is bound by the ack - we have 1 byte for each slot which limits to 8 messages, we reserve 1 bit
                                                      // for informing others about receivingDirectlyFromMe
    private static final int MAX_SLOTS = 16;     // There are lots of shorts in this code that will need to be made larger
                                                 // so this constant can not be arbitrarily made larger without changing a lot of shorts

    private static final int MAX_SLOT_HISTORY = 16; // this should be a configurable parameter, this controls how far do we retain the past receive info to dedup
                                                    // for a 16 node net, hyperperiod is 12 sec, and with 16 hyperperiods we get about 192 sec past 
                                                    // this parameter should relate to the retries with N+1 retries, we can expect to receive repeat messages till 96 sec (4*2*12)
                                                    // the relays would stretch this further by 1 hyperperiod - so we get 108 sec

    private class PacketRecord {
        public int mExpectToHearFrom;
        public int mHeardFrom;
        public int mResends;

        public int mUID;	// this is the serial uid computed by or'ing of hyperperiod (2bytes), slot (1byte),  index in slot (1byte)
        public AmmoGatewayMessage mPacket;

        PacketRecord( int uid, AmmoGatewayMessage agm ) {
            mUID = uid;
            mPacket = agm;
            mExpectToHearFrom = mReceivingMeDirectly;
            mHeardFrom = 0;
            mResends = DEFAULT_RESENDS;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append( mPacket ).append( ", " ).append( Integer.toHexString(mExpectToHearFrom) ).append( ", " );
            result.append( Integer.toHexString(mHeardFrom) ).append( ", " ).append( mResends );
            return result.toString();
        }
    };


    // This class records information about packets sent in a slot and acks
    // received in a slot.  It is retained so that, once we receive acks for
    // our sent packets during the next slot, we can figure out if all
    // intended receivers have received each packet.
    private class SlotRecord {
        public int mHyperperiodID;
        public PacketRecord[] mSent = new PacketRecord[MAX_PACKETS_PERSLOT]; // we can only sent and ack atmost 8 packets
        public int mSendCount;

        public byte[] mAcks = new byte[MAX_SLOTS];

        // FIXME: magic number - limits max radios in hyperperiod to 16
        // Should we optimize for smaller nets?
        public void SlotRecord() {
            for ( int i = 0; i < mSent.length; ++i )
                mSent[i] = null; 
        }

        public void reset( int newHyperperiod ) {
            mHyperperiodID = newHyperperiod;
            mSendCount = 0;

            for ( int i = 0; i < mSent.length; ++i )
                mSent[ i ] = null;
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
            result.append( mHyperperiodID ).append( ", " );
            result.append( mSendCount ).append( ", " );
            result.append( mSent.length );
            return result.toString();
        }
    };


    Map<Integer, UUID> mUUIDMap = new HashMap<Integer, UUID>();

    private SlotRecord[] slotRecords = new SlotRecord[MAX_SLOT_HISTORY]; // this is a ring buffer
    private int mCurrentIdx = 0;	// index of current in the slot record buffer

    private Queue<PacketRecord> mResendQueue = new LinkedList<PacketRecord>(); // mResendRelqyQueue really


    /**
     *
     */
    public SerialRetransmitter( SerialChannel channel, IChannelManager channelManager, int mySlot )
    {
        logger.trace( "SerialRetransmitter::SerialRetransmitter()" );
	mySlotNumber = mySlot;
        mChannel = channel;
        mChannelManager = channelManager;
	for(int i=0; i<MAX_SLOT_HISTORY; i++)
	    slotRecords[i] = new SlotRecord();
    }


    synchronized public void swapHyperperiodsIfNeeded( int hyperperiod )
    {
        logger.trace( "...swapHyperperiods(). new hyperperiod={}", hyperperiod );

        if ( hyperperiod != slotRecords[mCurrentIdx].mHyperperiodID ) {
            // We've entered a new hyperperiod, so make current point to the
            // new one, and discard the previous one.
            processPreviousBeforeSwap();

	    mCurrentIdx = (mCurrentIdx + 1) % MAX_SLOT_HISTORY;
            slotRecords[mCurrentIdx].reset( hyperperiod );
        }
    }


    private void processPreviousBeforeSwap()
    {
        // Any packet in mPrevious that has not been acknowledged should be
        // requeued for resending in the resend queue.
	final int mPreviousIdx = mCurrentIdx == 0 ? MAX_SLOT_HISTORY - 1 : mCurrentIdx - 1;
	final SlotRecord mPrevious = slotRecords[mPreviousIdx];

        for ( int i=0; i<mPrevious.mSendCount; i++ ) {
	    PacketRecord pr = mPrevious.mSent[i];
            if ( pr.mExpectToHearFrom == 0 ) {
                logger.trace( "Ack packet or a Normal Packet not requiring ack. deleting PacketRecord:{}", pr );
            } else if ( (pr.mExpectToHearFrom & pr.mHeardFrom) == pr.mExpectToHearFrom ) {
		// We have received acks from all of the people
		// that we thought we were sending to, so we can
		// now remove the packet from the pool.
		logger.debug( "Acked Packet: expected={}, heardFrom={}, deleting PacketRecord: {}",
			new Object[] { pr.mExpectToHearFrom,
				       pr.mHeardFrom,
				       pr } );
            } else {
                logger.trace( "expected={}, heardFrom={}, requeueing PacketRecord: {}",
			new Object[] { pr.mExpectToHearFrom,
				       pr.mHeardFrom,
				       pr } );
                // Puts this in the resend queue to be resent later when possible.
                if ( pr.mResends > 0 ) {
		    logger.debug( "Resending Scheduled for PacketRecord: {}, with remaining tries {}", pr, pr.mResends);
                    mResendQueue.offer( pr );
                }
            }
        }
    }


    /**
     * All messages received are passed to this method. It delivers the message
     * to the channel manager if it is a new message (rather than one older than
     * the current most recent message of this terse topic for the given slot.
     * It also caches it as appropriate.
     */
    synchronized public void processReceivedMessage( AmmoGatewayMessage agm,
                                                     int hyperperiod )
    {
        logger.trace( "SerialRetransmitter::processReceivedMessage(). hyperperiod={}, mySlotNumber={}",
                      hyperperiod, mySlotNumber );

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

        // Set the bit in the current hyperperiod for providing an ack back to sender
        slotRecords[mCurrentIdx].setAckBit( agm.mSlotID, agm.mIndexInSlot );


        // First, if it's an ack packet and any bit is set for the byte
        // corresponding to my slot, mark that this slot ID is actively
        // receiving from me.  Only accept ack packets from others sending
        // in the appropriate hyperperiod.
        if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_ACK ) {
            logger.trace( "Received ack packet. payload={}", agm.payload );
	    int theirAckBitsForMe = agm.payload[ mySlotNumber ];
	    if ( theirAckBitsForMe != 0 ) {
		// They are receiving my directly.
		mReceivingMeDirectly |= (0x1 << agm.mSlotID);
	    }

            if ( hyperperiod == agm.mHyperperiod || hyperperiod == agm.mHyperperiod + 1 ) {


                // We also use their ack information to tell which of the
                // packets that we sent in the last hyperperiod were actually
                // received by them.
                //
                // For each 1 bit in their ack byte, find the PacketRecord
                // for that index in mPrevious.mSent and set the ack bit
                // in mHeardFrom.
                final int mPreviousIdx = (mCurrentIdx == 0) ? (MAX_SLOT_HISTORY - 1) : (mCurrentIdx - 1);
                final SlotRecord mPrevious = slotRecords[mPreviousIdx];

                for ( int index = 0; index < mPrevious.mSendCount; ++index ) {
                    if ( (theirAckBitsForMe & 0x1) != 0 ) {
                        // They received a packet in the position in the slot
                        // with index "index".  Record this in the map.
                        logger.trace( "doing mSent with index={}, mPrevious.mSendCount={}",
                                      index, mPrevious.mSendCount );
                        PacketRecord stats = mPrevious.mSent[index];
                        stats.mHeardFrom |= (0x1 << agm.mSlotID);
                    }

                    theirAckBitsForMe = theirAckBitsForMe >>> 1;

                    // TODO: generate an ack message to send to the distributor to
                    // let them know that a packet we sent out was ack'd.

                }
            } else {
                logger.debug( "Spurious Ack received in hyperperiod={}, sent in hyperperiod={}, Ignoring ...",
			hyperperiod, agm.mHyperperiod );
            }
        }
        else if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_NORMAL ) {
            logger.trace( "Received normal packet. payload={}", agm.payload );
            // propagate up
            // We will need to put these in the retranmit mechanism once that is
            // implemented.
            mChannel.deliverMessage( agm );
        }
        else if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_RESEND ) {
            // If we have already seen the packet before, discard it and don't
            // deliver it to the distributor.
            // NOTE: For Ft. Drum, we will ignore this and send it up multiple times.

            // We have to rejigger the payload and checksum, since the resent
            // packet has a four-byte UID prepended to the payload, and we want
            // to deliver a packet with the original payload.

            // Tweak the agm here.  Everything in the agm should just stay the
            // except payload and checksum.
            logger.debug( "Received resend packet. payload={}", agm.payload );
            try {
		// TODO: check if we have not already received a packet with this uid
		// otherwise do this work ...
                logger.trace( "agm.size={}", agm.size );
                int newSize = agm.size - 4;
                logger.trace( "newSize={}", newSize );
                byte[] newPayload = new byte[ newSize ];

                logger.trace( "agm.payload.length={}", agm.payload.length );

                for ( int i = 0; i < newSize; ++i ) {
                    newPayload[i] = agm.payload[i+4];
                }

                agm.payload = newPayload;
                agm.size = newSize;

                CRC32 crc32 = new CRC32();
                crc32.update( newPayload );
                agm.payload_checksum = crc32.getValue();

                mChannel.deliverMessage( agm );
            } catch ( Exception ex ) {
                logger.warn( "receiver threw an exception {}", ex.getStackTrace() );
            }
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
    synchronized public void sendingPacket( AmmoGatewayMessage agm,
                                            int hyperperiod,
                                            int slotIndex,
                                            int indexInSlot )
    {
        logger.trace( "SerialRetransmitter::sendingAPacket(), needAck={}, UUID={} ", agm.mNeedAck, agm.mUUID );

        // Everything that goes out has to be put into the mSent array and has
        // to have a PacketRecord (even resends and ack packets). When swap
        // happens:
        // Normal packets: if not all acked, get put in resend queue
        // Ack packet: mExpectedToHearFrom is 0, so gets GCed.
        // Resend packet: 


        if ( agm.mPacketType != AmmoGatewayMessage.PACKETTYPE_RESEND ) {
            int uid = createUID( hyperperiod, slotIndex, indexInSlot );
            PacketRecord pr = new PacketRecord( uid, agm );
            logger.trace( "uid = {}, ...PacketRecord={}", new Object[] {uid, pr, indexInSlot} );

	    // The retransmitter functionality is only required for packet types
	    // that require acks.
	    if ( !agm.mNeedAck || agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_ACK ) {
		pr.mExpectToHearFrom = 0;
		pr.mResends = 0;
	    }

            slotRecords[mCurrentIdx].mSent[slotRecords[mCurrentIdx].mSendCount] = pr;
            slotRecords[mCurrentIdx].mSendCount++;
            logger.trace( "...mCurrent.mSendCount={}", slotRecords[mCurrentIdx].mSendCount );
        } else {
            // Resend packets have an existing PacketRecord, which we reuse.
	    // this is already inserted in the mSent when we create a resend packet
	    // see @createResendPacket
            logger.trace( "...resending a packet" );
	}
    }


    /**
     * If an AmmoGatewayMessage is returned, it will be sent in by the
     * SenderThread in the current slot.  If nothing will fit in the
     * bytesThatWillFit bytes that are remaining, return null and nothing
     * will be sent.  This function will be called repeatedly until it returns
     * null.
     */
    synchronized public AmmoGatewayMessage createResendPacket( long bytesAvailable )
    {
        logger.trace( "SerialRetransmitter::createResendPacket(). bytesAvailable={}",
                      bytesAvailable );

        PacketRecord pr = mResendQueue.peek();
        while ( pr != null ) {
            if ( pr.mPacket.payload.length + 20 > bytesAvailable ) {
                break;
            } else {
                // Here is where the resend packet containing the retransmitted
                // packet will be constructed.
                // For all packets that are retransmitted, create a normal header but
                // set the packet type to 01, append the four byte unique identifier,
                // and then the original payload.

                pr = mResendQueue.remove();

                try {
                    // Keep the pr and put it in the mSent.array, while decrementing
                    // mResends.
                    slotRecords[mCurrentIdx].mSent[slotRecords[mCurrentIdx].mSendCount] = pr;
                    slotRecords[mCurrentIdx].mSendCount++;
                    pr.mResends--;

                    int size = pr.mPacket.payload.length + 4;
                    ByteBuffer b = ByteBuffer.allocate( size );
                    b.order( ByteOrder.LITTLE_ENDIAN );

                    b.putInt( pr.mUID );
                    b.put( pr.mPacket.payload );

                    byte[] payload = b.array();

                    // Make a godforsaken Builder here.
                    AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder();
                    agmb.size( size );
                    agmb.payload( payload );

                    CRC32 crc32 = new CRC32();
                    crc32.update( payload );
                    agmb.checksum( crc32.getValue() );

                    agmb.packetType( AmmoGatewayMessage.PACKETTYPE_RESEND );

                    AmmoGatewayMessage agm = agmb.build();
                    logger.trace( "returning resend packet uid: {}. payload length={}", pr.mUID, payload.length );
                    return agm;

                } catch ( Exception ex ) {
                    logger.warn("createResendPacket() threw exception {}", ex.getStackTrace() );
                }
            }

            pr = mResendQueue.peek();
        }

        return null;
    }


    /**
     *
     */
    synchronized public AmmoGatewayMessage createAckPacket( int hyperperiod )
    {
        logger.trace( "SerialRetransmitter::createAckPacket(). hyperperiod={}",
                      hyperperiod );

        try {
	    final int mPreviousIdx = mCurrentIdx == 0 ? MAX_SLOT_HISTORY - 1 : mCurrentIdx - 1;
	    final SlotRecord mPrevious = slotRecords[mPreviousIdx];

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


    private int mySlotNumber;
    private SerialChannel mChannel;
    private IChannelManager mChannelManager;

    private short mReceivingMeDirectly = (short)0x0;

    private static final Logger logger = LoggerFactory.getLogger( "net.serial.retrans" );
}

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
    // original send + N-retries
    private static final int DEFAULT_RESENDS = 3;

    // this is bound by the ack - we have 1 byte for each slot which
    // limits to 8 messages, we reserve 1 bit for informing others
    // about receivingDirectlyFromMe
    private static final int MAX_PACKETS_PERSLOT = 7;

    private static final int MAX_SLOTS = 16;

    // this should be a configurable parameter, this controls how far
    // do we retain the past receive info to dedup for a 16 node net,
    // hyperperiod is 12 sec, and with 16 hyperperiods we get about
    // 192 sec past this parameter should relate to the retries with
    // N+1 retries, we can expect to receive repeat messages till 96
    // sec (4*2*12) the relays would stretch this further by 1
    // hyperperiod - so we get 108 sec
    private static final int MAX_SLOT_HISTORY = 16;

    public static final int DEFAULT_HOP_COUNT = 3;


    private class PacketRecord {
        public int mExpectToHearFrom;
        public int mHeardFrom;
        public int mResends;

        // this is the serial uid computed by or'ing of hyperperiod
        // (2bytes), slot (1byte), index in slot (1byte)
        public int mUID;

        public int mHopCount;

        public AmmoGatewayMessage mPacket;

        PacketRecord( int uid, AmmoGatewayMessage agm ) {
            mUID = uid;
            mPacket = agm;
            mExpectToHearFrom = mConnectivityMatrix[mySlotNumber] & ~(0x1 << mySlotNumber);
            mHeardFrom = 0;
            mResends = DEFAULT_RESENDS;
            mHopCount = agm.mHopCount;
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
            logger.trace( "setting ack bit for indexInSlot={}", indexInSlot );
            byte bits = mAcks[ slotID ];
            //logger.trace( "...before: bits={}, indexInSlot={}", bits, indexInSlot );
            bits |= (0x1 << indexInSlot);
            //logger.trace( "...after: bits={}", bits );
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


    // This is a ring buffer
    private SlotRecord[] slotRecords = new SlotRecord[MAX_SLOT_HISTORY];

    // Index of current in the slot record buffer
    private int mCurrentIdx = 0;

    // This keeps track of who is receiving directly from whom.
    // Updated locally based on received acks OR packets; updated for
    // others based on their disseminated info.
    private int[] mConnectivityMatrix = new int[MAX_SLOTS];

    // Should be called mResendRelayQueue, really.
    private Queue<PacketRecord> mResendQueue = new LinkedList<PacketRecord>();


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
        for(int i=0; i<MAX_SLOTS; i++)
            mConnectivityMatrix[i] = 0x1 << i; // each node can receive from itself
    }


    synchronized public void swapHyperperiodsIfNeeded( int hyperperiod )
    {
        if ( hyperperiod != slotRecords[mCurrentIdx].mHyperperiodID ) {
            logger.trace( "HYPERPERIOD: swapping {}-->{}",
                          slotRecords[mCurrentIdx].mHyperperiodID, hyperperiod );

            // We've entered a new hyperperiod, so make current point to the
            // new one, and discard the previous one.
            processPreviousBeforeSwap();

            mCurrentIdx = (mCurrentIdx + 1) % MAX_SLOT_HISTORY;
            slotRecords[mCurrentIdx].reset( hyperperiod );
        } else {
            logger.trace( "HYPERPERIOD: swap called but same: {}", hyperperiod );
        }
    }


    private void processPreviousBeforeSwap()
    {
        // Any packet in mPrevious that has not been acknowledged should be
        // requeued for resending in the resend queue.
        final int mPreviousIdx = mCurrentIdx == 0 ? MAX_SLOT_HISTORY - 1 : mCurrentIdx - 1;
        final SlotRecord mPrevious = slotRecords[mPreviousIdx];

        logger.trace( "SWAP: begin: previous is ring buffer index={}", mPreviousIdx );

        for ( int i=0; i<mPrevious.mSendCount; i++ ) {
            PacketRecord pr = mPrevious.mSent[i];
            if ( pr.mExpectToHearFrom == 0 ) {
                logger.trace( "Deleting ack packet or a normal packet not requiring ack, PacketRecord: {}", pr );
            } else if ( (pr.mExpectToHearFrom & pr.mHeardFrom) == pr.mExpectToHearFrom ) {
                // We have received acks from all of the people
                // that we thought we were sending to, so we can
                // now remove the packet from the pool.
                logger.debug( "Deleting fully acked Packet: expected={}, heardFrom={}, PacketRecord: {}",
                              new Object[] { bitsToNumberList( pr.mExpectToHearFrom ),
                                             bitsToNumberList( pr.mHeardFrom ),
                                             pr } );
            } else {
                logger.trace( "Requeueing: expected={}, heardFrom={}, PacketRecord: {}",
                              new Object[] { bitsToNumberList( pr.mExpectToHearFrom ),
                                             bitsToNumberList( pr.mHeardFrom ),
                                             pr } );
                // Puts this in the resend queue to be resent later when possible.
                if ( pr.mResends > 0 ) {
                    logger.debug( "Resending Scheduled for PacketRecord: {}, with remaining tries {}", pr, pr.mResends);
                    mResendQueue.offer( pr );
                    logger.trace( "RESENDQUEUE: {} in queue", mResendQueue.size() );
                } else {
                    // If the packet has no more resends left, assume
                    // that the slots who haven't acked it yet have
                    // dropped out, and remove them from the
                    // connectivity matrix.
                    int didntHearFrom = pr.mExpectToHearFrom ^ pr.mHeardFrom;
                    for ( int j = 0; j < MAX_SLOTS; ++j ) {
                        if ( ((didntHearFrom >>> j) & 0x1) != 0 ) {
                            logger.trace( "Resends at 0. Connectivity Matrix: Dropped {}", j );
                            mConnectivityMatrix[mySlotNumber] &= ~(0x1 << j);
                            mConnectivityMatrix[j] = 0;
                        }
                    }
                }
            }
        }
        logger.trace( "SWAP: end" );
    }


    /**
     * Returns as a String a list of numbers denoting the "1" bits in a bitmap.
     * E.g., 00100110 is converted to (1,2,5).
     */
    private String bitsToNumberList( int bits ) {
        boolean firstTime = true;
        StringBuilder sb = new StringBuilder();
        sb.append( "(" );

        int i = 0;
        while ( bits != 0 ) {
            if ( (bits & 0x1) != 0 ) {
                if ( firstTime ) {
                    sb.append( i );
                    firstTime = false;
                } else {
                    sb.append( "," ).append( i );
                }
            }
            bits = bits >>> 1;
            ++i;
        }

        sb.append( ")" );
        return sb.toString();
    }


    /**
     * Returns a string representation of the type of the packet.
     */
    private String packetTypeAsString( int type ) {
        // There is probably some fancy enum way to get Strings, but this will
        // do for now.
        String result = null;
        switch ( type ) {
        case AmmoGatewayMessage.PACKETTYPE_NORMAL:
            result =  "NORMAL";
            break;
        case AmmoGatewayMessage.PACKETTYPE_RESEND:
            result = "RESEND";
            break;
        case AmmoGatewayMessage.PACKETTYPE_ACK:
            result = "ACK";
            break;
        }
        return result;
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
        logger.trace( "Received packet: type={}, hyperperiod={}, my slot={}, their slot={}",
                      new Object[] { packetTypeAsString(agm.mPacketType),
                                     hyperperiod, mySlotNumber, agm.mSlotID } );

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
            logger.trace( "RECEIVED: ack packet. payload={}", agm.payload );

            // Prevent sign extension and mask out 7 bit since it's not really
            // an ack for a packet.
            int theirAckBitsForMe = agm.payload[ mySlotNumber ] & 0x0000007F;
            if ( theirAckBitsForMe != 0 ) {
                // because I am getting an ack for my messages remote is receiving
                // directly from me
                int before = mConnectivityMatrix[mySlotNumber];
                mConnectivityMatrix[mySlotNumber] |= (0x1 << agm.mSlotID);
                if ( before != mConnectivityMatrix[mySlotNumber] ) {
                    logger.trace( "Connectivity Matrix: Added {}", agm.mSlotID );
                }
            }

            for ( int i = 0; i < MAX_SLOTS; i++ ) {
                // remote is receiving directly from slot i, top bit in the ack
                // slot is set for receive info
                if ( (agm.payload[i] & 0x00000080)  == 0x00000080 )
                    mConnectivityMatrix[agm.mSlotID] |= (0x1 << i);
		else
                    mConnectivityMatrix[agm.mSlotID] &= ~(0x1 << i);
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

                logger.trace( "...slot {} acked me, messages: {}", agm.mSlotID,
                              bitsToNumberList( theirAckBitsForMe ));
                for ( int index = 0; index < mPrevious.mSendCount; ++index ) {
                    if ( (theirAckBitsForMe & 0x1) != 0 ) {
                        // They received a packet in the position in the slot
                        // with index "index".  Record this in the map.
                        //logger.trace( "doing mSent with index={}, mPrevious.mSendCount={}",
                        //              index, mPrevious.mSendCount );
                        PacketRecord stats = mPrevious.mSent[index];
                        stats.mHeardFrom |= (0x1 << agm.mSlotID);
                    }

                    theirAckBitsForMe = theirAckBitsForMe >>> 1;

                    // TODO: generate an ack message to send to the distributor to
                    // let them know that a packet we sent out was ack'd.
                }
            } else {
                logger.debug( "Spurious ack received in hyperperiod={}, sent in hyperperiod={}, Ignoring...",
                              hyperperiod, agm.mHyperperiod );
            }
        }
        else if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_NORMAL ) {
            logger.trace( "RECEIVED: normal packet. payload={}", agm.payload );

            // Propagate up --

            // Save it for relaying if the original sender does not have the same
            // connectivity matrix as we do...
            // FIXME: Shouldn't this be a superset of me rather than equal to me?
            logger.trace( "...sender receiving: {}", bitsToNumberList( mConnectivityMatrix[agm.mSlotID] ));
            logger.trace( "...my receiving: {}", bitsToNumberList( mConnectivityMatrix[mySlotNumber] ));
            if ( (mConnectivityMatrix[mySlotNumber] &
                  (mConnectivityMatrix[mySlotNumber] ^ mConnectivityMatrix[agm.mSlotID])) != 0) {
                int uid = createUID( agm.mHyperperiod, agm.mSlotID, agm.mIndexInSlot );
                PacketRecord pr = new PacketRecord(uid, agm);
                --pr.mHopCount;
                logger.trace( "HOPCOUNT: Normal packet decrementing to {}", pr.mHopCount );
                if ( pr.mHopCount > 0 ) {
                    mResendQueue.offer( pr );
                    logger.trace( "Adding message from slot={} for relay pr={}",
                                  agm.mSlotID, pr );
                    logger.trace( "RESENDQUEUE: {} in queue", mResendQueue.size() );
                }
            } else {
                logger.trace( "...not retransmitting because my connectivity already covered" );
            }
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
            logger.debug( "RECEIVED: resend packet. payload={}", agm.payload );
            try {
                // TODO: check if we have not already received a packet with this uid
                // otherwise do this work...
                ByteBuffer b = ByteBuffer.wrap( agm.payload );
                b.order( ByteOrder.LITTLE_ENDIAN );
                int firstFourBytes = b.getInt();
                logger.trace( "resend UID as int: {}", firstFourBytes );

                int b1 = agm.payload[3] & 0x000000FF;
                int b2 = agm.payload[2] & 0x000000FF;
                int originalHP = ( (b1 << 8) | b2 ) & 0x0000FFFF;

                logger.trace( "resend packet originalHP={}, currentHP={}", originalHP, hyperperiod );
                final int hpDelta = hyperperiod - originalHP; // FIXME: have to do modulo difference?

                // If within our dup window
                if ( hpDelta < MAX_SLOT_HISTORY ) {
                    final byte originalSlot = agm.payload[1];
                    final byte originalIdx = agm.payload[0];

                    logger.trace( "resend packet originalSlot={}, originalIdx={}", originalSlot, originalIdx );

                    // find the ack record for that hyperperiod in our history ring buffer
                    int slotIdx = mCurrentIdx - hpDelta;
                    if ( slotIdx < 0 )
                        slotIdx += MAX_SLOT_HISTORY;

                    final byte ackByte = slotRecords[slotIdx].mAcks[originalSlot];
                    // if it's not a packet from me,
                    // and I didn't ack it earlier - then its not a duplicate
                    if ( (originalSlot != mySlotNumber) &&
                         (ackByte & (0x1 << originalIdx)) == 0) {
                        logger.trace( "...packet I haven't seen before" );

                        // we have not seen it before, update our slot record
                        slotRecords[slotIdx].mAcks[originalSlot] |= (0x1 << originalIdx);

                        //logger.trace( "agm.size={}", agm.size );
                        int newSize = agm.size - 4;
                        //logger.trace( "newSize={}", newSize );
                        byte[] newPayload = new byte[ newSize ];


                        logger.trace( "agm.payload.length={}", agm.payload.length );

                        // TODO: optimize this - OR create a new AmmoGatewayMessage rather than modifying the one received
                        for ( int i = 0; i < newSize; ++i ) {
                            newPayload[i] = agm.payload[i+4];
                        }

                        agm.payload = newPayload;
                        agm.size = newSize;

                        CRC32 crc32 = new CRC32();
                        crc32.update( newPayload );
                        agm.payload_checksum = new AmmoGatewayMessage.CheckSum(crc32.getValue());

                        // Add this for further relay if union of connectivity
                        // vector of original sender (originalSlot) and relayer
                        // (agm.mSlotID) is not a superset of my connectivity vector.
                        int union = mConnectivityMatrix[agm.mSlotID] | mConnectivityMatrix[originalSlot];
                        logger.trace( "...union receiving: {}", bitsToNumberList( union ));
                        logger.trace( "...my receiving: {}", bitsToNumberList( mConnectivityMatrix[mySlotNumber] ));
                        if ( (mConnectivityMatrix[mySlotNumber] &
                              (mConnectivityMatrix[mySlotNumber] ^ union)) != 0 )
                        {
                            int uid = createUID( originalHP, originalSlot, originalIdx );
                            PacketRecord pr = new PacketRecord(uid, agm);
                            --pr.mHopCount;
                            logger.trace( "HOPCOUNT: Resend packet decrementing to {}", pr.mHopCount );
                            if ( pr.mHopCount > 0 ) {
                                mResendQueue.offer( pr );
                                logger.trace( "...adding message from slot={} for RE-relay pr={}",
                                              originalSlot, pr );
                                logger.trace( "RESENDQUEUE: {} in queue", mResendQueue.size() );
                            }
                        }

                        mChannel.deliverMessage( agm );
                    } else {
                        logger.trace( "Filtered a duplicate packet origHP = {}, origSlot = {}, origIdx = {}",
                                      new Object[] {originalHP, originalSlot, originalIdx} );

                    }
                }
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
	    if (slotRecords[mCurrentIdx].mSendCount < MAX_PACKETS_PERSLOT) {

		int uid = createUID( hyperperiod, slotIndex, indexInSlot );
		PacketRecord pr = new PacketRecord( uid, agm );

		if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_NORMAL ) {
		    logger.trace( "SEND: normal, uid = {}, ...PacketRecord={}", uid, pr );
		} else if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_ACK ) {
		    logger.trace( "SEND: ack, uid = {}, ...PacketRecord={}", uid, pr );
		}

		// The retransmitter functionality is only required for packet types
		// that require acks.
		if ( !agm.mNeedAck || agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_ACK ) {
		    pr.mExpectToHearFrom = 0;
		    pr.mResends = 0;
		}

		slotRecords[mCurrentIdx].mSent[ slotRecords[mCurrentIdx].mSendCount ] = pr;
		slotRecords[mCurrentIdx].mSendCount++;
		logger.trace( "...packets sent this slot={}", slotRecords[mCurrentIdx].mSendCount );
	    } else {
		logger.error("... number of packets in this slot={} >= MAX_PACKET_PERSLOT .. NOT adding to the slot record", slotRecords[mCurrentIdx].mSendCount);
	    }
        } else {
            // Resend packets have an existing PacketRecord, which we reuse.
            // this is already inserted in the mSent when we create a resend packet
            // see @createResendPacket
            logger.trace( "SEND: resending a packet" );
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
                logger.trace( "Removing packetrecord from resend queue: {}", pr );
                logger.trace( "RESENDQUEUE: {} in queue", mResendQueue.size() );

                try {
                    // Keep the pr and put it in the mSent.array, while decrementing
                    // mResends.
                    slotRecords[mCurrentIdx].mSent[slotRecords[mCurrentIdx].mSendCount] = pr;
                    slotRecords[mCurrentIdx].mSendCount++;
                    pr.mResends--;
                    logger.trace( "...packets sent this slot={}", slotRecords[mCurrentIdx].mSendCount );

                    int size = pr.mPacket.payload.length + 4;
                    ByteBuffer b = ByteBuffer.allocate( size );
                    b.order( ByteOrder.LITTLE_ENDIAN );

                    b.putInt( pr.mUID );
                    logger.trace( "...creating resend packet with UID={}", pr.mUID );
                    byte[] raw = b.array();
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

                    agmb.hopCount( pr.mHopCount );
                    logger.trace( "HOPCOUNT: Resending packet with hop count of {}", pr.mHopCount );

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

            // provide my connectivity info to others ...
            for (int i=0; i<MAX_SLOTS; i++) {
                mPrevious.mAcks[i] |=  ( (mConnectivityMatrix[ mySlotNumber ] >>>  i) & 0x1 ) << 7;
            }
            logger.trace( "CONNECTIVITY: my current: {}",
                          bitsToNumberList( mConnectivityMatrix[mySlotNumber] ));

            AmmoGatewayMessage.Builder b = AmmoGatewayMessage.newBuilder();

            b.size( mPrevious.mAcks.length );
            b.payload( mPrevious.mAcks );

            CRC32 crc32 = new CRC32();
            crc32.update( mPrevious.mAcks );
            b.checksum( crc32.getValue() );

            AmmoGatewayMessage agm = b.build();
            logger.trace( "generating an ack packet" );
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
    synchronized public void resetReceivingMeDirectly() { mConnectivityMatrix[mySlotNumber] = 0x1 << mySlotNumber; }


    private int mySlotNumber;
    private SerialChannel mChannel;
    private IChannelManager mChannelManager;

    private static final Logger logger = LoggerFactory.getLogger( "net.serial.retrans" );
}

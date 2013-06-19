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
import java.util.LinkedList;
import java.util.Queue;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.annotation.Monitored;



/**
 * We can pass in fewer of these arguments to the constructor if we make this
 * a private class of the SerialChannel.  Wait until we see how big this class
 * will get before we decide.
 */
public class SerialRetransmitter
{
    private static final Logger logger = LoggerFactory.getLogger( "net.serial.retrans" );

    //
    // Constants
    //

    // This needs to be public because it needs to be used by the
    // SerialChannel.
    public static final int DEFAULT_HOP_COUNT = 4;

    // original send + N-retries
    private static final int DEFAULT_RESENDS = 3;

    // number of hyperperiods after which to expire an unresponsive node from connmatrix
    private static final int DEFAULT_CONNMATRIX_EXPIRE = 3;

    // this is bound by the ack - we have 1 byte for each slot which
    // limits to 8 messages, we reserve 1 bit for informing others
    // about receivingDirectlyFromMe
    private static final int MAX_PACKETS_PER_SLOT = 7;

    private static final int MAX_SLOTS = 16;

    // this should be a configurable parameter, this controls how far
    // do we retain the past receive info to dedup for a 16 node net,
    // hyperperiod is 12 sec, and with 16 hyperperiods we get about
    // 192 sec past this parameter should relate to the retries with
    // N+1 retries, we can expect to receive repeat messages till 96
    // sec (4*2*12) the relays would stretch this further by 1
    // hyperperiod - so we get 108 sec
    private static final int MAX_SLOT_HISTORY = 16;

    //
    // Member variables
    //

    // Contains packets waiting to be transmitted.  This includes both
    // packets to be resent and also packets being retransmitted.
    private Queue<PacketRecord> mResendQueue = new LinkedList<PacketRecord>();

    private ConnectivityMatrix mConnMatrix;
    private SlotRecords mSlotRecords;

    // Our slot number is constant for the lifetime of this object, so
    // we cache it for efficiency.
    private int mySlotNumber;

    // Only used for delivering messages
    private SerialChannel mChannel;

    @Monitored
    private volatile int mNormalReceived = 0;
    @Monitored
    private volatile int mResendReceived = 0;
    @Monitored
    private volatile int mRelayReceived = 0;

    /**
     * This method provides a way to display the resend/relay stats for field testing.
     */
    public String getSendBitStats() {
        StringBuilder result = new StringBuilder();
        result.append( "Normal rec:" ).append( mNormalReceived );
        return result.toString();
    }


    /**
     * This method provides a way to display the resend/relay stats for field testing.
     */
    public String getReceiveBitStats() {
        StringBuilder result = new StringBuilder();
        result.append( "Resend:" ).append( mResendReceived );
        result.append( ", Relay:" ).append( mRelayReceived );
        return result.toString();
    }


    /**
     *
     */
    public SerialRetransmitter( SerialChannel channel, IChannelManager channelManager, int mySlot )
    {
        logger.trace( "SerialRetransmitter::SerialRetransmitter()" );
        mySlotNumber = mySlot;
        mChannel = channel;

        mConnMatrix = new ConnectivityMatrix( MAX_SLOTS, mySlot, DEFAULT_CONNMATRIX_EXPIRE );
        mSlotRecords = new SlotRecords( MAX_SLOT_HISTORY, MAX_SLOTS, MAX_PACKETS_PER_SLOT );
    }


    synchronized public void switchHyperperiodsIfNeeded( int hyperperiod )
    {
        if ( hyperperiod != mSlotRecords.getCurrentHyperperiod() ) {
            logger.trace( "HYPERPERIOD: swapping {}-->{}",
                          mSlotRecords.getCurrentHyperperiod(), hyperperiod );
            if ( Math.abs( mSlotRecords.getCurrentHyperperiod() - hyperperiod ) != 1 ) {
                logger.error( "ERROR: hyperperiods differ by more than one: {}-->{}",
                              mSlotRecords.getCurrentHyperperiod(), hyperperiod );
            }

            // We've entered a new hyperperiod, so make current point to the
            // new one, and discard the previous one.
            processPreviousBeforeSwap();

            mConnMatrix.repair( hyperperiod );
            mSlotRecords.incrementSlot( hyperperiod );
        } else {
            logger.trace( "HYPERPERIOD: swap called but same: {}", hyperperiod );
        }
    }


    private void processPreviousBeforeSwap()
    {
        // Any packet in mPrevious that has not been acknowledged should be
        // requeued for resending in the resend queue.
        final SlotRecord mPrevious = mSlotRecords.getPreviousSlotRecord();

        logger.trace( "SWAP: begin: previous is ring buffer index={}",
                      mSlotRecords.getPreviousIndex() );

        for ( int i=0; i<mPrevious.mSendCount; i++ ) {
            PacketRecord pr = mPrevious.mSent[i];
            if ( pr.mExpectToHearFrom == 0 ) {
                logger.trace( "Deleting ack packet or a normal packet not requiring ack, PacketRecord: {}", pr );
            } else if ( (pr.mExpectToHearFrom & pr.mHeardFrom) == pr.mExpectToHearFrom ) {
                // We have received acks from all of the people
                // that we thought we were sending to, so we can
                // now remove the packet from the pool.
                logger.debug( "Deleting fully acked Packet: expected={}, heardFrom={}, PacketRecord: {}",
                              new Object[] { Util.bitsToNumberList( pr.mExpectToHearFrom ),
                                             Util.bitsToNumberList( pr.mHeardFrom ),
                                             pr } );
            } else {
                logger.trace( "Requeueing: expected={}, heardFrom={}, PacketRecord: {}",
                              new Object[] { Util.bitsToNumberList( pr.mExpectToHearFrom ),
                                             Util.bitsToNumberList( pr.mHeardFrom ),
                                             pr } );
                // Puts this in the resend queue to be resent later when possible.
                if ( pr.mResends > 0 ) {
                    logger.debug( "Resending Scheduled for PacketRecord: {}, with remaining tries {}", pr, pr.mResends);
                    mResendQueue.offer( pr );
                    logger.trace( "RESENDQUEUE: {} in queue", mResendQueue.size() );
                }
                // else {
                // If the packet has no more resends left, assume
                // that the slots who haven't acked it yet have
                // dropped out, and remove them from the
                // connectivity matrix.
                // int didntHearFrom = pr.mExpectToHearFrom ^ pr.mHeardFrom;
                // for ( int j = 0; j < MAX_SLOTS; ++j ) {
                //     if ( ((didntHearFrom >>> j) & 0x1) != 0 ) {
                //         logger.trace( "Resends at 0. Connectivity Matrix: Dropped {}", j );
                //         mConnMatrix.didNotHearFrom( mySlotNumber, j );
                //     }
                // }
                // }
            }
        }
        logger.trace( "SWAP: end" );
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
                      new Object[] { Util.packetTypeAsString(agm.mPacketType),
                                     hyperperiod, mySlotNumber, agm.mSlotID } );

        //
        // Collect the ack statistics.
        //

        // First, figure out if they are sending in what the retransmitter
        // thinks is the current slot, or if a new slot has started.  If so,
        // save off the current slot stats as previous and start collecting
        // new stats.
        switchHyperperiodsIfNeeded( hyperperiod );

        // Set the bit in the current hyperperiod for providing an ack back to sender
        mSlotRecords.setAckBit( agm.mSlotID, agm.mIndexInSlot );

        if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_ACK ) {
            logger.trace( "RECEIVED: ack packet. payload={}", agm.payload );

            // First, if it's an ack packet and any bit is set for the byte
            // corresponding to my slot, mark that this slot ID is actively
            // receiving from me.  Only accept ack packets from others sending
            // in the appropriate hyperperiod.

            // Prevent sign extension and mask out 7 bit since it's not really
            // an ack for a packet.
            int theirAckBitsForMe = agm.payload[ mySlotNumber ] & 0x0000007F;
            if ( theirAckBitsForMe != 0 ) {
                // because I am getting an ack for my messages remote is receiving
                // directly from me
                mConnMatrix.set( hyperperiod, agm.mSlotID, true );
            }

            mConnMatrix.processAckPacketPayload( agm.mSlotID, agm.payload );

            if ( hyperperiod == agm.mHyperperiod || hyperperiod == agm.mHyperperiod + 1 ) {
                // We also use their ack information to tell which of the
                // packets that we sent in the last hyperperiod were actually
                // received by them.
                //
                // For each 1 bit in their ack byte, find the PacketRecord
                // for that index in mPrevious.mSent and set the ack bit
                // in mHeardFrom.
                final SlotRecord mPrevious = mSlotRecords.getPreviousSlotRecord();

                logger.trace( "...slot {} acked me, messages: {}", agm.mSlotID,
                              Util.bitsToNumberList( theirAckBitsForMe ));
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

            mNormalReceived++;

            // Propagate up --

            // Save it for relaying if the original sender does not have the same
            // connectivity matrix as we do...
            if ( !mConnMatrix.coversMyConnectivity( agm.mSlotID )) {
                int uid = Util.createUID( agm.mHyperperiod, agm.mSlotID, agm.mIndexInSlot );
                PacketRecord pr = new PacketRecord( uid, agm, mConnMatrix.expectToHearFrom(), DEFAULT_RESENDS );
                --pr.mHopCount;
                pr.mPacketType = AmmoGatewayMessage.PACKETTYPE_RELAY;
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
        else if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_RESEND
                  || agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_RELAY ) {
            // If we have already seen the packet before, discard it and don't
            // deliver it to the distributor.
            // NOTE: For Ft. Drum, we will ignore this and send it up multiple times.

            // We have to rejigger the payload and checksum, since the resent
            // packet has a four-byte UID prepended to the payload, and we want
            // to deliver a packet with the original payload.


            // Only increment the counters if we have not received this packet before.
            if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_RESEND ) {
                logger.debug( "RECEIVED: resend packet. payload={}", agm.payload );
            } else if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_RELAY ) {
                logger.debug( "RECEIVED: relay packet. payload={}", agm.payload );
            } else {
                logger.warn( "Invalid packet type in processReceivedMessage(): {}", agm.mPacketType );
            }


            // Tweak the agm here.  Everything in the agm should just stay the same
            // except payload and checksum.
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
                if ( hpDelta < mSlotRecords.getMaxSlotHistory() ) {
                    final byte originalSlot = agm.payload[1];
                    final byte originalIdx = agm.payload[0];

                    logger.trace( "resend packet originalSlot={}, originalIdx={}", originalSlot, originalIdx );

                    // find the ack record for that hyperperiod in our history ring buffer
                    int slotIdx = mSlotRecords.getSlotIndexWithDelta( hpDelta );

                    final int ackByte = mSlotRecords.getAckByte( slotIdx, originalSlot );
                    // if it's not a packet from me,
                    // and I didn't ack it earlier - then its not a duplicate
                    if ( (originalSlot != mySlotNumber) &&
                         (ackByte & (0x1 << originalIdx)) == 0) {
                        logger.trace( "...packet I haven't seen before" );

                        // Only increment the counters if we have not received this packet before.
                        if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_RESEND ) {
                            mResendReceived++;
                        } else if ( agm.mPacketType == AmmoGatewayMessage.PACKETTYPE_RELAY ) {
                            mRelayReceived++;
                        } else {
                            logger.warn( "Invalid packet type in processReceivedMessage(): {}", agm.mPacketType );
                        }

                        // we have not seen it before, update our slot record
                        mSlotRecords.setAckBit( slotIdx, originalSlot, originalIdx );

                        //logger.trace( "agm.size={}", agm.size );
                        int newSize = agm.size - 4;
                        //logger.trace( "newSize={}", newSize );
                        byte[] newPayload = new byte[ newSize ];


                        logger.trace( "agm.payload.length={}", agm.payload.length );

                        // TODO: optimize this - OR create a new
                        // AmmoGatewayMessage rather than modifying
                        // the one received
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
                        if ( !mConnMatrix.unionCoversMyConnectivity( agm.mSlotID, originalSlot )) {
                            int uid = Util.createUID( originalHP, originalSlot, originalIdx );
                            PacketRecord pr = new PacketRecord( uid, agm, mConnMatrix.expectToHearFrom(),
                                                                DEFAULT_RESENDS );
                            --pr.mHopCount;
                            pr.mPacketType = agm.mPacketType;

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
                logger.warn( "receiver threw an exception", ex );
            }
        } else {
            logger.warn( "SerialRetransmitter discarding packet with invalid packetype={}", agm.mPacketType );
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

        if ( agm.mPacketType != AmmoGatewayMessage.PACKETTYPE_RESEND
             && agm.mPacketType != AmmoGatewayMessage.PACKETTYPE_RELAY ) {
            if ( mSlotRecords.getCurrentSendCount() < MAX_PACKETS_PER_SLOT ) {

                int uid = Util.createUID( hyperperiod, slotIndex, indexInSlot );
                PacketRecord pr = new PacketRecord( uid, agm, mConnMatrix.expectToHearFrom(), DEFAULT_RESENDS );

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

                mSlotRecords.addPacketRecord( pr );
                logger.trace( "...packets sent this slot={}", mSlotRecords.getCurrentSendCount() );
            } else {
                logger.error("... number of packets in this slot={} >= MAX_PACKET_PERSLOT .. NOT adding to the slot record", mSlotRecords.getCurrentSendCount() );
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
                    mSlotRecords.addPacketRecord( pr );
                    pr.mResends--;
                    logger.trace( "...packets sent this slot={}", mSlotRecords.getCurrentSendCount() );

                    int size = pr.mPacket.payload.length + 4;
                    ByteBuffer b = ByteBuffer.allocate( size );
                    b.order( ByteOrder.LITTLE_ENDIAN );

                    b.putInt( pr.mUID );

                    if ( pr.mPacketType == AmmoGatewayMessage.PACKETTYPE_RESEND ) {
                        logger.trace( "...creating resend packet with UID={}", pr.mUID );
                    } else if ( pr.mPacketType == AmmoGatewayMessage.PACKETTYPE_RELAY ) {
                        logger.trace( "...creating relay packet with UID={}", pr.mUID );
                    } else {
                        logger.warn( "Invalid packet type in createResendPacket(): {}", pr.mPacketType );
                    }

                    //Byte[] raw = b.array();
                    b.put( pr.mPacket.payload );

                    byte[] payload = b.array();

                    // Make a godforsaken Builder here.
                    AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder();
                    agmb.size( size );
                    agmb.payload( payload );

                    CRC32 crc32 = new CRC32();
                    crc32.update( payload );
                    agmb.checksum( crc32.getValue() );

                    agmb.packetType( pr.mPacketType );

                    agmb.hopCount( pr.mHopCount );
                    logger.trace( "HOPCOUNT: Resending packet with hop count of {}", pr.mHopCount );

                    AmmoGatewayMessage agm = agmb.build();
                    logger.trace( "returning resend/relay packet uid: {}. payload length={}", pr.mUID, payload.length );
                    return agm;

                } catch ( Exception ex ) {
                    logger.warn("createResendPacket() threw exception", ex );
                }
            }

            pr = mResendQueue.peek();
        }

        return null;
    }


    /**
     * Creates an ack packet if possible.  Returns the ack packet if
     * one was created, null otherwise.  We don't create an ack packet
     * if we are not in the correct hyperperiod.
     */
    synchronized public AmmoGatewayMessage createAckPacket( int hyperperiod )
    {
        logger.trace( "SerialRetransmitter::createAckPacket(). hyperperiod={}",
                      hyperperiod );

        try {
            final SlotRecord previous = mSlotRecords.getPreviousSlotRecord();

            // We only send an ack if the previous hyperperiod was the preceding
            // one.  If the previous hyperperiod was an older one, just don't
            // send anything.
            if ( hyperperiod - 1 != previous.mHyperperiodID ) {
                logger.trace( "wrong hyperperiod: current={}, previous={}",
                              hyperperiod, previous.mHyperperiodID );
                return null;
            }

            // Provide my connectivity info to others...
            mConnMatrix.provideAckInfo( previous.mAcks );

            AmmoGatewayMessage.Builder b = AmmoGatewayMessage.newBuilder();

            b.size( previous.mAcks.length );
            b.payload( previous.mAcks );

            CRC32 crc32 = new CRC32();
            crc32.update( previous.mAcks );
            b.checksum( crc32.getValue() );

            AmmoGatewayMessage agm = b.build();
            logger.trace( "generating an ack packet" );
            return agm;

        } catch ( Exception ex ) {
            logger.warn("createAckPacket() threw exception", ex );
        }

        return null;
    }
}


/**
 *
 */
class PacketRecord {
    public int mExpectToHearFrom;
    public int mHeardFrom;
    public int mResends;

    // this is the serial uid computed by or'ing of hyperperiod
    // (2bytes), slot (1byte), index in slot (1byte)
    public int mUID;

    public int mHopCount;

    public int mPacketType = AmmoGatewayMessage.PACKETTYPE_RESEND;
    public AmmoGatewayMessage mPacket;

    PacketRecord( int uid, AmmoGatewayMessage agm, int expectToHearFrom, int resends ) {
        mUID = uid;
        mPacket = agm;
        mExpectToHearFrom = expectToHearFrom;
        mHeardFrom = 0;
        mResends = resends;
        mHopCount = agm.mHopCount;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append( mPacket ).append( ", " )
            .append( Integer.toHexString(mExpectToHearFrom) ).append(", ");
        result.append( Integer.toHexString(mHeardFrom) ).append( ", " )
            .append( mResends );
        return result.toString();
    }
};


/**
 * This class records information about packets sent in a slot and acks 
 * received in a slot.  It is retained so that, once we receive acks for
 * our sent packets during the next slot, we can figure out if all
 * intended receivers have received each packet.
 */
class SlotRecord {
    private static final Logger logger = LoggerFactory.getLogger( "net.serial.retrans" );

    public int mHyperperiodID;

    // we can only sent and ack atmost 8 packets
    public PacketRecord[] mSent;
    public int mSendCount;

    public byte[] mAcks;

    // FIXME: magic number - limits max radios in hyperperiod to 16
    // Should we optimize for smaller nets?
    public SlotRecord( int maxSlots, int maxPacketsPerSlot ) {

        mSent = new PacketRecord[maxPacketsPerSlot];
        for ( int i = 0; i < mSent.length; ++i ) {
            mSent[i] = null;
        }

        mAcks = new byte[maxSlots];
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


/**
 *
 */
class SlotRecords
{
    public SlotRecords( int maxSlotHistory, int maxSlots, int maxPacketsPerSlot ) {
        mMaxSlotHistory = maxSlotHistory;

        mRingBuffer = new SlotRecord[mMaxSlotHistory];
        for( int i = 0; i < mMaxSlotHistory; ++i )
            mRingBuffer[i] = new SlotRecord( maxSlots, maxPacketsPerSlot );
    }

    public void incrementSlot( int hyperperiod ) {
        mCurrentIdx = (mCurrentIdx + 1) % mMaxSlotHistory;
        mRingBuffer[mCurrentIdx].reset( hyperperiod );
    }

    public int getCurrentHyperperiod() {
        return mRingBuffer[mCurrentIdx].mHyperperiodID;
    }

    public int getPreviousIndex() {
        return mCurrentIdx == 0 ? mMaxSlotHistory - 1 : mCurrentIdx - 1;
    }

    public SlotRecord getPreviousSlotRecord() {
        final int mPreviousIdx = mCurrentIdx == 0 ? mMaxSlotHistory - 1 : mCurrentIdx - 1;
        return mRingBuffer[mPreviousIdx];
    }

    public void setAckBit( int slotID, int indexInSlot ) {
        mRingBuffer[mCurrentIdx].setAckBit( slotID, indexInSlot );
    }

    public int getAckByte( int slotIndex, int originalSlot ) {
        final byte ackByte = mRingBuffer[slotIndex].mAcks[originalSlot];
        return ackByte;
    }

    public void setAckBit( int slotIndex, int originalSlot, int originalIndex ) {
        mRingBuffer[slotIndex].mAcks[originalSlot] |= (0x1 << originalIndex);
    }

    public int getCurrentSendCount() {
        return mRingBuffer[mCurrentIdx].mSendCount;
    }

    public void addPacketRecord( PacketRecord pr ) {
        mRingBuffer[mCurrentIdx].mSent[ mRingBuffer[mCurrentIdx].mSendCount ] = pr;
        mRingBuffer[mCurrentIdx].mSendCount++;
    }

    public int getSlotIndexWithDelta( int delta ) {
        int slotIdx = mCurrentIdx - delta;
        if ( slotIdx < 0 )
            slotIdx += mMaxSlotHistory;
        return slotIdx;
    }

    // I should probably refactor some of the code in processReceivedMessage()
    // and put it in this method.
    // public boolean packetAlreadyReceived( int originalHyperperiod,
    //                                       int originalSlotID,
    //                                       int originalIndexInSlot )
    // {
    // }

    public int getMaxSlotHistory() { return mMaxSlotHistory; }

    private int mMaxSlotHistory = 0;

    private SlotRecord[] mRingBuffer;

    // Index of current in the slot record buffer
    private int mCurrentIdx = 0;
};



/**
 *
 */
class ConnectivityMatrix
{
    private static final Logger logger = LoggerFactory.getLogger( "net.serial.retrans" );

    public ConnectivityMatrix( int maxSlots, int mySlotID, int connMatrixExpire )
    {
        mMaxSlots = maxSlots;
        mMySlotID = mySlotID;
        mConnMatrixExpire = connMatrixExpire;

        mConnectivityMatrix = new int[mMaxSlots];
        mConnectivityUpdated = new int[mMaxSlots];

        for ( int i = 0; i < mMaxSlots; ++i )
            mConnectivityMatrix[i] = 0x1 << i; // each node can receive from itself
    }


    public void set( int hyperperiod, int theirSlotID, boolean value )
    {
        int before = mConnectivityMatrix[mMySlotID];

        mConnectivityMatrix[mMySlotID] |= (0x1 << theirSlotID);

        // updating hyperperiod in which we received from remote
        mConnectivityUpdated[theirSlotID] = hyperperiod;

        if ( before != mConnectivityMatrix[mMySlotID] ) {
            logger.trace( "Connectivity Matrix: Added {}", theirSlotID );
        }
    }


    public void processAckPacketPayload( int theirSlotID, byte[] payload )
    {
        for ( int i = 0; i < mMaxSlots; ++i ) {
            // remote is receiving directly from slot i, top bit in the ack
            // slot is set for receive info
            if ( (payload[i] & 0x00000080) == 0x00000080 )
                mConnectivityMatrix[theirSlotID] |= (0x1 << i);
            else
                mConnectivityMatrix[theirSlotID] &= ~(0x1 << i);
        }
    }


    public boolean coversMyConnectivity( int theirSlotID )
    {
        // FIXME: Shouldn't this be a superset of me rather than equal to me?
        logger.trace( "...sender receiving: {}", Util.bitsToNumberList( mConnectivityMatrix[theirSlotID] ));
        logger.trace( "...my receiving: {}", Util.bitsToNumberList( mConnectivityMatrix[mMySlotID] ));
        return (mConnectivityMatrix[mMySlotID] &
                (mConnectivityMatrix[mMySlotID] ^ mConnectivityMatrix[theirSlotID])) == 0;
    }


    public boolean unionCoversMyConnectivity( int theirSlotID, int originalSlotID )
    {
        logger.trace( "...original sender receiving: {}", Util.bitsToNumberList( mConnectivityMatrix[originalSlotID] ));
        logger.trace( "...relayer receiving: {}", Util.bitsToNumberList( mConnectivityMatrix[theirSlotID] ));

        int union = mConnectivityMatrix[theirSlotID] | mConnectivityMatrix[originalSlotID];
        logger.trace( "...union receiving: {}", Util.bitsToNumberList( union ));
        logger.trace( "...my receiving: {}", Util.bitsToNumberList( mConnectivityMatrix[mMySlotID] ));
        return (mConnectivityMatrix[mMySlotID] &
                (mConnectivityMatrix[mMySlotID] ^ union)) == 0;
    }


    public void provideAckInfo( byte[] acks )
    {
        for ( int i = 0; i < mMaxSlots; ++i ) {
            acks[i] |= ((mConnectivityMatrix[mMySlotID] >>> i) & 0x1) << 7;
        }
        logger.trace( "CONNECTIVITY: my current: {}",
                      Util.bitsToNumberList( mConnectivityMatrix[mMySlotID] ));
            
    }


    // Expires entries in the connectivity matrix for which the time
    // limit has been exceeded.
    public void repair( int hyperperiod )
    {
        for( int i = 0; i < mMaxSlots; ++i ) {
            if ( i == mMySlotID )
                continue;
            if ( (hyperperiod - mConnectivityUpdated[i]) > mConnMatrixExpire ) {
                mConnectivityMatrix[mMySlotID] &= ~(0x1 << i);
            }
        }
    }


    public int expectToHearFrom()
    {
        // The connectivity matrix may contain our own slot ID, so unset that before
        // returning a result.
        return mConnectivityMatrix[mMySlotID] & ~(0x1 << mMySlotID);
    }


    public void didNotHearFrom( int theirSlotID )
    {
        mConnectivityMatrix[mMySlotID] &= ~(0x1 << theirSlotID);
        mConnectivityMatrix[theirSlotID] = 0;
    }

        
    private int mMaxSlots;
    private int mMySlotID;
    private int mConnMatrixExpire;

    // This keeps track of who is receiving directly from whom.
    // Updated locally based on received acks OR packets; updated for
    // others based on their disseminated info.
    private int[] mConnectivityMatrix;

    // last hyperperiod in which connectivity was updated for a remote node
    // this is used to decide when to expire a node from connectivity matrix
    private int[] mConnectivityUpdated;
};


class Util {
    /**
     * Returns as a String a list of numbers denoting the "1" bits in a bitmap.
     * E.g., 00100110 is converted to (1,2,5).
     */
    public static String bitsToNumberList( int bits ) {
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
    public static String packetTypeAsString( int type ) {
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
        case AmmoGatewayMessage.PACKETTYPE_RELAY:
            result = "RELAY";
            break;
        }
        return result;
    }


    /**
     * Construct UID for message.
     */
    public static int createUID( int hyperperiod, int slotIndex, int indexInSlot )
    {
        int uid = 0;
        uid |= hyperperiod << 16;
        uid |= slotIndex << 8;
        uid |= indexInSlot;

        return uid;
    }
}

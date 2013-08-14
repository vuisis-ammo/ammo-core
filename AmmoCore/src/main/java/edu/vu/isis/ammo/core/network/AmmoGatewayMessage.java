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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Comparator;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.util.ByteBufferAdapter;
import edu.vu.isis.ammo.util.CRC32;

/**
 * see /opt/ammo/Gateway/AndroidGatewayPlugin/
 * <ul>
 * <li>AndroidServiceHandler.h</li>
 * <li>AndroidServiceHandler.cpp</li>
 * </ul>
 * The total message (non-terse) is of the form...
 * <ul>
 * <li>4 Bytes : magic</li>
 * <li>4 Bytes : message size</li>
 * <li>1 Byte : priority</li>
 * <li>3 Bytes : reserved</li>
 * <li>4 Bytes : payload checksum</li>
 * <li>4 Bytes : header checksum</li>
 * <li>N bytes : payload</li>
 * </ul>
 * <p>
 * The magic is set to 0xfeedbeef.
 * The primary purpose of the magic is assist in detecting the start of messages.
 * <p>
 * The message size is encoded as unsigned little endian,
 * the size of the payload, and only the payload, in bytes.
 * <p>
 * The priority, also appears in the payload and is copied from there.
 * <p>
 * The reserved bytes, for future use.
 * <p>
 * The payload checksum, the CRC32 checksum of the payload, and only the paylod.
 * The header checksum, the CRC32 checksum of the header, not including the payload nor itself.
 * <p>
 */
public class AmmoGatewayMessage implements Comparable<Object> {
    private static final Logger logger = LoggerFactory.getLogger("net.message");

    private static final byte[] MAGIC = {
            (byte) 0xed, (byte) 0xbe, (byte) 0xef
    };
    public static final byte VERSION_1_FULL = (byte) 0xfe;
    public static final byte VERSION_1_TERSE = (byte) 0x01;

    @SuppressWarnings("unused")
    private static final long INT_MASK = 0x0FFFFFFFFL; //
    private static final int BYTE_MASK = 0x0FF;
    private static final long BYTE_MASK_LONG = 0x0FFL;

    public static final int HEADER_DATA_LENGTH =
            4 // magic length
                    + 4 // message size
                    + 1 // priority byte
                    + 3 // reserved bytes
                    + 4; // payload checksum

    public static final int HEADER_LENGTH =
            HEADER_DATA_LENGTH
            + 4; // header checksum

    // public static final int HEADER_DATA_LENGTH_TERSE =
    //       4  // magic (3.5 bytes) and slot number (4 bits)
    //     + 2  // payload size
    //     + 2  // payload checksum
    //     + 4  // timestamp
    //     + 1  //   index in slot: 4 bits
    //          //   <reserved>:    2 bits
    //          //   packet type:   2 bits
    //     + 1  // <reserved>
    //     + 2; // header checksum
    // // ------
    // //   16 bytes

    public static final int HEADER_DATA_LENGTH_TERSE =
          4  // magic (3.5 bytes) and slot number (4 bits)
        + 2  // payload size
        + 2  // payload checksum
        + 4  // UID (info about what we think that we're sending in)
             //   hyperperiod:   2 bytes
             //   slot number:   1 byte
             //   index in slot: 1 byte
        + 1  // packet type
        + 1  // <reserved>
        + 2; // header checksum
    // ------
    //   16 bytes

    // These are equal because the terse form doesn't use a header checksum.
    public static final int HEADER_LENGTH_TERSE = HEADER_DATA_LENGTH;

    public int size;
    public final byte priority;
    public final byte version;
    public CheckSum payload_checksum;
    public ByteBufferAdapter payload;
    public final INetworkService.OnSendMessageHandler handler;

    public final boolean isMulticast;
    public final boolean isSerialChannel;
    public final boolean isGateway;
    public final boolean isHeartbeat;
    public final NetChannel channel;

    public boolean isHeartbeat () {return isHeartbeat;}

    public final long buildTime;
    public long gpsOffset;


    // These values denote the packet type with respect to the resend
    // functionality.  Note: used only in terse encoding
    public static final int PACKETTYPE_NORMAL = 0x01;
    public static final int PACKETTYPE_RESEND = 0x02;
    public static final int PACKETTYPE_ACK    = 0x03;
    public static final int PACKETTYPE_RELAY  = 0x04;

    //
    // The following two members must not be made final, because they need to
    // be set in the SerialChannel after the Builder has run.  The Builder
    // pattern is probably a bad idea in this class.
    // Note: We might want to make both of these shorts.
    //
    public int mPacketType;

    // For received packets, this member records the hyperperiod in which the
    // sender thought he was sending.
    public int mHyperperiod;

    // For received packets, this member records the slot in which the sender
    // thought he was sending.
    public int mSlotID;

    // This denoted the index in the sequence of packets within
    // the current slot.  Note: used only in terse encoding.
    public int mIndexInSlot;

    // Whether this message needs to be acked.  E.g., PLI packets do not need
    // acks, but chat messages do.
    public boolean mNeedAck;

    // The UUID of the message.  This will uniquely identify it to the
    // distributor when we acknowledge it.
    public UUID mUUID;

    // Hop count for Harris 152 resend/retransmit mechanism
    public int mHopCount;




    /**
     * This is used by PriorityBlockingQueue() to prioritize it contents. when
     * no specialized comparator is provided. Specialized comparators such as
     * PriorityOrder should be preferred.
     * <p>
     * The ordering is as follows:
     * <dl>
     * <dt>priority</dt>
     * <dd>larger</dd>
     * <dt>receive time</dt>
     * <dd>earlier</dd>
     * <dt>message size</dt>
     * <dd>smaller</dd>
     * <dt>checksum</dt>
     * <dd>smaller</dd>
     * </dl>
     * 
     * @return <p>
     *         <dl>
     *         <dt>a negative integer</dt>
     *         <dd>if this instance is less than another;</dd>
     *         <dt>a positive integer</dt>
     *         <dd>if this instance is greater than another;</dd>
     *         <dt>0</dt>
     *         <dd>if this instance has the same order as another.</dd>
     *         </dl>
     * @throws ClassCastException
     */
    @Override
    public int compareTo(Object another) {
        if (!(another instanceof AmmoGatewayMessage))
            throw new ClassCastException("does not compare with AmmoGatewayMessage");

        AmmoGatewayMessage that = (AmmoGatewayMessage) another;
        logger.debug("default compare msgs: priority [{}:{}] build time: [{}:{}]",
                new Object[] {
                        this.priority, that.priority,
                        this.buildTime, that.buildTime
                });
        if (this.priority > that.priority)
            return 1;
        if (this.priority < that.priority)
            return -1;

        // within the same priority - preserve send/receive time order
        if (this.buildTime > that.buildTime)
            return 1;
        if (this.buildTime < that.buildTime)
            return -1;

        if (this.size < that.size)
            return 1;
        if (this.size > that.size)
            return -1;
        final int compareCS = CheckSum.compare(this.payload_checksum, that.payload_checksum);
        if (compareCS != 0) return compareCS;
        return 0;
    }

    /**
     * The recommendation is that equals conforms to compareTo.
     */
    @Override
    public boolean equals(Object another) {
        return (this.compareTo(another) == 0);
    }

    /**
     * A functor to be used in cases such as PriorityQueue.
     * <p>
     * This gives a partial ordering, rather than total ordering of the natural
     * order.
     * <p>
     * This overrides the default comparison of the AmmoGatewayMessage. The
     * ordering is as follows: priority : larger receive time : earlier
     */
    public static class PriorityOrder implements Comparator<AmmoGatewayMessage> {
        @Override
        public int compare(AmmoGatewayMessage o1, AmmoGatewayMessage o2) {
            logger.debug("compare msgs: priority [{}:{}] build time: [{}:{}]",
                    new Object[] {
                            o1.priority, o2.priority,
                            o1.buildTime, o2.buildTime
                    });
            if (o1.priority > o2.priority)
                return -1;
            if (o1.priority < o2.priority)
                return 1;
            // if priority is same then process in the time order of arrival
            if (o1.buildTime > o2.buildTime)
                return 1;
            if (o1.buildTime < o2.buildTime)
                return -1;
            return 0;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("addr=[").append(this.hashCode()).append("] ");
        sb.append("size=[").append(this.size).append("] ");
        sb.append("priority=[").append(this.priority).append("] ");
        sb.append("received=[").append(this.buildTime).append("] ");
        sb.append("checksum=[").append(this.payload_checksum.toHexString()).append("]");
        return sb.append("}").toString();
    }

    static public class Builder {
        // the size is the intended size, the actual size is that of the payload
        private int size;

        public int size() {
            return this.size;
        }

        public Builder size(int val) {
            this.size = val;
            return this;
        }

        private byte priority;

        public byte priority() {
            return this.priority;
        }

        public Builder priority(byte val) {
            this.priority = val;
            return this;
        }

        public Builder priority(int val) {
            this.priority = (byte) val;
            return this;
        }

        private byte version;

        public byte version() {
            return this.version;
        }

        public Builder version(byte val) {
            this.version = val;
            return this;
        }

        public Builder version(int val) {
            this.version = (byte) val;
            return this;
        }

        private byte error;

        public byte error() {
            return this.error;
        }

        public Builder error(byte val) {
            this.error = val;
            return this;
        }

        public Builder error(int val) {
            this.error = (byte) val;
            return this;
        }

        private long checksum;

        public long checksum() {
            return this.checksum;
        }

        public Builder checksum(long val) {
            this.checksum = val;
            return this;
        }

        private int mPacketType;
        public int packetType() { return mPacketType; }
        public Builder packetType( int type ) { mPacketType = type; return this; }

        private int mHyperperiod;
        public int hyperperiod() { return mHyperperiod; }
        public Builder hyperperiod( int hyperperiod ) { mHyperperiod = hyperperiod; return this; }

        private int mSlotID;
        public int slotID() { return mSlotID; }
        public Builder slotID( int index ) { mSlotID = index; return this; }

        private int mIndexInSlot;
        public int indexInSlot() { return mIndexInSlot; }
        public Builder indexInSlot( int index ) { mIndexInSlot = index; return this; }

        private boolean mNeedAck;
        public boolean needAck() { return mNeedAck; }
        public Builder needAck( boolean needAck ) { mNeedAck = needAck; return this; }

        private UUID mUUID;
        public UUID uuid() { return mUUID; }
        public Builder uuid( UUID uuid ) { mUUID = uuid; return this; }

        private int mHopCount;
        public int hopCount() { return mHopCount; }
        public Builder hopCount( int hopcount ) { mHopCount = hopcount; return this; }



        private INetworkService.OnSendMessageHandler handler;

        public INetworkService.OnSendMessageHandler handler() {
            return this.handler;
        }

        public Builder handler(INetworkService.OnSendMessageHandler val) {
            this.handler = val;
            return this;
        }

        private boolean isMulticast;

        public boolean isMulticast() {
            return this.isMulticast;
        }

        public Builder isMulticast(boolean val) {
            this.isMulticast = val;
            return this;
        }

        private boolean isSerialChannel;

        public boolean isSerialChannel() {
            return this.isSerialChannel;
        }

        public Builder isSerialChannel(boolean val) {
            this.isSerialChannel = val;
            return this;
        }

        private boolean isGateway;

        public boolean isGateway() {
            return this.isGateway;
        }

        public Builder isGateway(boolean val) {
            this.isGateway = val;
            return this;
        }

        private boolean isHeartbeat = false;
        public boolean isHeartbeat() { return this.isHeartbeat; }
        public Builder isHeartbeat(boolean val) { this.isHeartbeat = val; return this; }
 
       private byte[] payload_serialized;
       private ByteBufferAdapter payload_serializedBuf;
        public byte[] payload() {
            return this.payload_serialized;
        }
        
        public ByteBufferAdapter payloadBuffer() {
        	return payload_serializedBuf;
        }

        public Builder payload(byte[] val) {
            // if (this.size != val.length)
            // throw new IllegalArgumentException("payload size incorrect");
            this.payload_serialized = val;
            return this;
        }
        
        public Builder payload(ByteBufferAdapter val) {
            // if (this.size != val.length)
            // throw new IllegalArgumentException("payload size incorrect");
            this.payload_serializedBuf = val;
            return this;
        }

        private AmmoMessages payload;

        public AmmoMessages payload(Class<?> clazz) {
            return this.payload;
        }

        public Builder payload(AmmoMessages val) {
            this.payload = val;
            return this;
        }

        /**
         * delivery channel
         */
        private NetChannel channel;

        public NetChannel channel() {
            return this.channel;
        }

        public Builder channel(NetChannel val) {
            this.channel = val;
            return this;
        }

        public AmmoGatewayMessage build() {
        	if( this.payload_serialized == null && this.payload_serializedBuf == null ) {
        		throw new IllegalStateException("No payload!");
        	} else if( this.payload_serializedBuf != null ) {
        		if( this.size != this.payload_serializedBuf.remaining() )
        			throw new IllegalStateException("payload size incorrect");
        		return new AmmoGatewayMessage(this, payload_serializedBuf);
        	}
            if (this.size != this.payload_serialized.length)
                throw new IllegalStateException("payload size incorrect");
            return new AmmoGatewayMessage(this, this.payload_serialized);
        }

        /**
         * Describe the message.
         */
        private Builder() {
            this.size = 0;
            this.priority = PriorityLevel.NORMAL.b();
            this.version = VERSION_1_FULL;
            this.checksum = 0;
            this.handler = null;
            mPacketType = PACKETTYPE_NORMAL;
            mHyperperiod = -1;  // default to an invalid hyperperiod
            mSlotID = -1;       // default to an invalid slot
            mIndexInSlot = -1;  // default to an invalid index
            mNeedAck = false;   // default to an invalid index
            mHopCount = 1;
        }
    }

    private AmmoGatewayMessage(Builder builder, ByteBufferAdapter payload) {
        this.size = builder.size;
        if (this.size != payload.limit())
            throw new IllegalArgumentException("payload size incorrect");
        this.priority = builder.priority;
        this.version = builder.version;
        this.payload_checksum = new CheckSum(builder.checksum);
        this.payload = payload;
        this.handler = builder.handler;

        this.isMulticast = builder.isMulticast;
        this.isSerialChannel = builder.isSerialChannel;
        this.isGateway = builder.isGateway;
        this.isHeartbeat = builder.isHeartbeat;
        this.channel = builder.channel;

        mPacketType =   builder.mPacketType;
        mHyperperiod =  builder.mHyperperiod;
        mSlotID =       builder.mSlotID;
        mIndexInSlot =  builder.mIndexInSlot;
        mNeedAck =      builder.mNeedAck;
        mUUID =         builder.mUUID;
        mHopCount =     builder.mHopCount;

        /**
         * record the time when the message is built so we can sort it by time
         * if the priority is same
         */
        this.buildTime = System.currentTimeMillis();    	
    }
    
    private AmmoGatewayMessage(Builder builder, byte[] payload) {
    	this(builder, ByteBufferAdapter.obtain(payload));
    }

    public static AmmoGatewayMessage.Builder newBuilder(AmmoMessages.MessageWrapper.Builder mwb,
            INetworkService.OnSendMessageHandler handler) {
        byte[] payload = mwb.build().toByteArray();

        final CheckSum crc32 = CheckSum.newInstance(payload);

        return AmmoGatewayMessage.newBuilder()
                .size(payload.length)
                .payload(payload)
                .checksum(crc32.asLong())
                .priority(PriorityLevel.NORMAL.v)
                .version(VERSION_1_FULL)
                .handler(handler);
    }

    public static AmmoGatewayMessage.Builder newBuilder() {
        return new AmmoGatewayMessage.Builder();
    }

    public static AmmoGatewayMessage.Builder newBuilder(int size, long checksum, byte priority,
            byte version, INetworkService.OnSendMessageHandler handler) {
        return new AmmoGatewayMessage.Builder()
                .size(size)
                .checksum(checksum)
                .priority(priority)
                .version(version)
                .handler(handler);
    }


    /**
     * Serialize the AmmoMessage for transmission.
     * <p>
     * The byte buffer returned is already flip()ped.
     * 
     * @return
     */
    public ByteBufferAdapter serialize(ByteOrder endian, byte version, byte phone_id) {

        if (version == VERSION_1_FULL) {
            return serializeFull_V1(endian, phone_id,
                    size, this.payload, this.payload_checksum, this.priority);

        } else if (version == VERSION_1_TERSE) {
            return serializeTerse_V1(endian, phone_id,
                    this.size, this.payload, this.payload_checksum, this.gpsOffset);

        } else {
            logger.error("invalid version supplied {}", version);
            return null;
        }
    }

    /**
     * Serialize the AmmoMessage for transmission using the full header
     * encoding.
     * <p>
     * The byte buffer returned is already flip()ped.
     * 
     * @return
     */
    static public ByteBufferAdapter serializeFull_V1(ByteOrder endian, byte phone_id, int size,
            ByteBufferAdapter payload, CheckSum checksum, byte priority)
    {
        int total_length = HEADER_LENGTH + payload.limit();
        ByteBufferAdapter buf = ByteBufferAdapter.obtain(ByteBuffer.allocate(total_length));
        buf.order(endian);

        buf.put(MAGIC[2]);
        buf.put(MAGIC[1]);
        buf.put(MAGIC[0]);
        buf.put(VERSION_1_FULL);

        buf.putInt(size);
        logger.debug("   size={}", size);

        buf.put(priority);
        /** three bytes reserved for future use */
        buf.put((byte) 0x0).put((byte) 0x0).put((byte) 0x0);

        logger.debug("   payload_checksum={}", checksum);
        buf.put(checksum.asByteArray(), 0, 4);

        // checksum of header
        int pos = buf.position();
        byte[] base = buf.array();
        final CheckSum crc32 = CheckSum.newInstance(base, 0, pos);
        buf.put(crc32.asByteArray());

        // payload
        buf.put(payload);
        if (logger.isDebugEnabled()) {

            if (payload.limit() > 450) {
                // ByteBuffer tmp = ByteBuffer.wrap(payload, 0, 450);
                logger.debug("  payload length={} ", payload.limit());
            }
            else
                logger.debug("   payload={}", payload);
        }

        buf.flip();
        return buf;
    }

    /**
     * Serialize the AmmoMessage for transmission using the terse header
     * encoding.
     * <p>
     * The byte buffer returned is already flip()ped.
     * 
     * @return
     */
    public ByteBufferAdapter serializeTerse_V1(ByteOrder endian, byte phone_id,
            int size, ByteBufferAdapter payload, CheckSum checksum, long gpsOffset)
    {
        int total_length = HEADER_LENGTH_TERSE + payload.limit();
        ByteBufferAdapter buf = ByteBufferAdapter.obtain(ByteBuffer.allocate(total_length));
        buf.order(endian);

        buf.put(MAGIC[2]);
        buf.put(MAGIC[1]);
        buf.put(MAGIC[0]);

        // The fourth byte of the magic has its first two bits set to
        // 01 and the next six bits encoding the phone id.
        byte terse_version = (byte) (0x00000040 | phone_id);
        buf.put(terse_version);

        short sizeAsShort = (short) size;
        buf.putShort(sizeAsShort);
        logger.debug("   size={}", sizeAsShort);

        // Only output [0] and [1] of the four byte checksum.
        byte[] payloadCheckSum = checksum.asByteArray();
        logger.debug("   payload_checksum as bytes={}", payloadCheckSum);
        buf.put(payloadCheckSum[0]);
        buf.put(payloadCheckSum[1]);
        // buf.put( convertChecksum(this.payload_checksum), 0, 2 );

        // long nowInMillis = System.currentTimeMillis() - gpsOffset;
        // int nowInMillisInt = (int) (nowInMillis % 1000000000);

        int uid = 0;
        logger.trace( "serializing hyperperiod={}", mHyperperiod );
        uid |= (mHyperperiod << 16);
        uid |= (phone_id << 8);
        uid |= mIndexInSlot;
        buf.putInt( uid );      // 4 bytes

        buf.put( (byte) mPacketType ); // byte

        // <reserved> (1 byte)
        buf.put( (byte) mHopCount ); // byte


        // buf.putLong( nowInMillis );
        // buf.putInt( 0 ); // time will go here.
        // buf.putInt(nowInMillisInt);
        // buf.putShort((short) (gpsOffset));
        // Put two-byte header checksum here. The checksum covers the
        // magic sequence and everything up to and including the six
        // zero bytes just written.
        CheckSum crc32 = CheckSum.newInstance(buf.array(), 0, HEADER_LENGTH_TERSE - 2);
        byte[] headerChecksum = crc32.asByteArray();
        buf.put(headerChecksum[0]);
        buf.put(headerChecksum[1]);

        // payload
        buf.put(payload);
        logger.debug("   payload={}", payload);
        buf.flip();
        return buf;
    }

    /**
     * Must leave the position of drain pointing at the first byte which might
     * be the first byte in a header.
     * 
     * @param drain
     * @return
     * @throws IOException
     */
    static public AmmoGatewayMessage.Builder extractHeader(ByteBuffer drain) throws IOException
    {
        // the mark is used to indicate the position where a header may start
        try {
            while (drain.remaining() > 0) {
                drain.mark();
                int start = drain.arrayOffset() + drain.position();

                // search for the magic
                if (drain.get() != MAGIC[2])
                    continue;
                if (drain.get() != MAGIC[1])
                    continue;
                if (drain.get() != MAGIC[0])
                    continue;

                byte version = drain.get();
                if (version == VERSION_1_FULL) {
                    int size = drain.getInt();

                    int priority = drain.get() & BYTE_MASK;
                    logger.debug("   priority={}", priority);

                    int error = drain.get() & BYTE_MASK;
                    logger.debug("   error={}", error);

                    // reserved bytes
                    drain.getShort();

                    byte[] checkBytes = new byte[4];

                    drain.get(checkBytes, 0, 4);
                    logger.debug("   payload check={}", checkBytes);
                    long payload_checksum = new CheckSum(checkBytes).asLong();

                    drain.get(checkBytes, 0, 4);
                    logger.debug("   header check={}", checkBytes);
                    long header_checksum = new CheckSum(checkBytes).asLong();

                    final CheckSum crc32 = CheckSum.newInstance(drain.array(), start, HEADER_DATA_LENGTH);
                    if (header_checksum != crc32.asLong())
                        continue;

                    return AmmoGatewayMessage.newBuilder()
                            .size(size)
                            .checksum(payload_checksum)
                            .priority(priority)
                            .version(version)
                            .error(error);
                } else if ((version & 0xC0) == 0x40) {
                    byte phone_id = (byte) (version & 0x3F);

                    int size = drain.getShort();

                    byte[] checkPayloadBytes = new byte[4];
                    checkPayloadBytes[0] = drain.get();
                    checkPayloadBytes[1] = drain.get();
                    checkPayloadBytes[2] = 0;
                    checkPayloadBytes[3] = 0;
                    // drain.get( checkBytes, 0, 2 );
                    CheckSum csum = new CheckSum(checkPayloadBytes);
                    long payload_checksum = csum.asLong();
                    logger.debug("   payload check={}, asLong={}", checkPayloadBytes, Long.toHexString(payload_checksum));

                    // UID
                    int uid = drain.getInt();
                    int hyperperiod = (uid >>> 16);
                    logger.trace( "deserialized hyperperiod={}", hyperperiod );
                    @SuppressWarnings("unused")
					int slotID = (uid >>> 8) & 0xFF;
                    int indexInSlot = uid  & 0xFF;

                    // Packet type
                    byte next = drain.get();
                    int packetType = next; // & 0x03;  // Is is wise to mask here?
                    logger.error( "packetType={}", packetType );

                    // <reserved> (1 byte)
                    byte hopCount = drain.get();
                    logger.error( "hopCount={}", hopCount );

                    // Discard six bytes of zero
                    // drain.getInt();
                    // drain.getShort();

                    /**
                     * Hack for CACI test. It's inefficient to have this happen
                     * here, since it will happen for verbose, too. Get the
                     * magic sequence out of this function, since it should be
                     * channel-specific.
                     */
                    CheckSum terseHeaderCrc32 = CheckSum.newInstance(drain.array(), start, HEADER_DATA_LENGTH_TERSE - 2);
                    byte[] computedChecksum = terseHeaderCrc32.asByteArray();

                    // Return null if the header checksum fails.
                    if (drain.get() != computedChecksum[0] || drain.get() != computedChecksum[1]) {
                        logger.warn("Corrupt terse header; packet discarded.");
                        return null;
                    }

                    return AmmoGatewayMessage.newBuilder()
                            .size(size)
                            .version(version)
                            .checksum(payload_checksum)
                        .packetType( packetType )
                        .hyperperiod( hyperperiod )
                        .slotID( phone_id )
                        .indexInSlot( indexInSlot )
                        .hopCount( hopCount );
                } else {
                    logger.error("apparent magic number but version invalid");
                }
            }
        } catch (BufferUnderflowException ex) {
            // the data was looking like a header as far as it went
            drain.reset();
        } catch (Exception ex) {
            /**
             * If we did not have enough data to do the header checksum, we
             * won't get a BufferUnderflowException, but the CRC32 library will
             * throw when we try to compute the checksum. Go ahead and let the
             * function return null, and the SerialChannel will clear the
             * buffer.
             */
        }

        return null;
    }

    /**
     * <dl>
     * <dt>AUTH</dt>
     * <dd>authentication message only</dd>
     * <dt>CTRL</dt>
     * <dd>all control type messages, not data messages: subscribe, pull, heart
     * beat</dd>
     * </dl>
     * <p>
     * No data, push, messages should be allowed to use anything within the AUTH
     * and CTRL span.
     * <p>
     * If a data message tries to use a value in that range it should be
     * degraded to FLASH.
     */
    public enum PriorityLevel {
        AUTH(127), CTRL(112), FLASH(96), URGENT(64), IMPORTANT(32), NORMAL(0), BACKGROUND(-32);
        public int v;

        public byte b() {
            return (byte) this.v;
        }

        private PriorityLevel(int value) {
            this.v = value;
        }
    }

    /**
     * error values for MessageHeader 
     * <p>
     * These error codes are for the reasons why
     * the gateway may subsequently disconnect. If the error code is non-zero,
     * the message size and checksum will be zero. The headerChecksum is present
     * and is calculated normally. The key to deciding whether to process the
     * message should be the message length and not the error code.
     */
    public enum GatewayError {
        NO_ERROR(0),
        INVALID_MAGIC_NUMBER(1),
        INVALID_HEADER_CHECKSUM(2),
        INVALID_MESSAGE_CHECKSUM(3),
        MESSAGE_TOO_LARGE(4),
        INVALID_VERSION_NUMBER(5);

        public int v;

        public byte b() {
            return (byte) this.v;
        }

        private GatewayError(int value) {
            this.v = value;
        }
    }

    /**
     * A worker for dealing with check sums.
     */
    static public class CheckSum {
        final private long cs;

        public CheckSum(byte[] checkBytes) {
            this.cs = convert(checkBytes);
        }
        
        public CheckSum(long checkLong) {
            this.cs = checkLong;
        }
        
        public static int compare(CheckSum lhs, CheckSum rhs) {
            if (lhs.cs == rhs.cs) return 0;
            if (lhs.cs < rhs.cs) return -1;
            return 1;
        }
        
        static public CheckSum newInstance(ByteBufferAdapter adapter) {
        	ByteBuffer buffer = adapter.buffer();
        	if( buffer != null ) {
        		return newInstance(buffer);
        	}
        	byte[] array = adapter.array();
        	if( array != null ) {
        		return newInstance(array);
        	}
        	
        	long crc = 0;
        	byte[] tmp = new byte[1024*4];
        	int currentpos = adapter.position();
        	while( adapter.remaining() > 0 ) {
        		int len = Math.min(adapter.remaining(), tmp.length);
        		adapter.get(tmp, 0, len);
        		CRC32.update(crc, tmp, 0, len);
        	}
        	adapter.position(currentpos);
        	return new CheckSum(crc);
        }
       
        static public CheckSum newInstance(byte[] target) {
            return new CheckSum( CRC32.update(0, target) );
        }
        
        static public CheckSum newInstance(byte[] target, int start, int length) {
            return new CheckSum( CRC32.update(0, target, start, length) );
        }
        
        static public CheckSum newInstance(ByteBuffer buffer) {
            return new CheckSum( CRC32.update(0, buffer) );
        }
        /**
         * The four least significant bytes of a long make the checksum array.
         * 
         * @param cvalue
         * @return
         */
        public byte[] asByteArray() {
            return new byte[] {
                    (byte) this.cs,
                    (byte) (this.cs >>> 8),
                    (byte) (this.cs >>> 16),
                    (byte) (this.cs >>> 24)
            };
        }
        public long asLong() {
            return this.cs;
        }
       
        public boolean equals(long value) {
            return (this.cs == value);
        }
        
        public boolean equals(byte[] checkBytes) {
            return (this.cs == convert(checkBytes));
        }
        
        private long convert(byte[] checkBytes) {
            return  ((BYTE_MASK_LONG & checkBytes[0]) << 0)
                    | ((BYTE_MASK_LONG & checkBytes[1]) << 8)
                    | ((BYTE_MASK_LONG & checkBytes[2]) << 16)
                    | ((BYTE_MASK_LONG & checkBytes[3]) << 24);
        }
        
        @Override 
        public boolean equals(Object obj) {
            if (!(obj instanceof CheckSum)) return false;
            final CheckSum that = (CheckSum)obj;
            return (this.cs == that.cs);
        }
        
        @Override
        public String toString() {
        	return toHexString();
        }
        
        public String toHexString() {
            return Long.toHexString(this.cs);
        }
    }


    /**
     * Verify the checksum. Normal messages examine four byte checksums; terse
     * messages only examine two bytes.
     * 
     * @return
     */
    public boolean hasValidChecksum() {
        final CheckSum crc32 = CheckSum.newInstance(payload);

        if (version == VERSION_1_FULL) {
            if (! crc32.equals(this.payload_checksum)) {
                logger.warn("you have received a bad message, the checksums [{}:{}] did not match",
                        crc32.toHexString(),
                        this.payload_checksum.toHexString());
                return false;
            }

        } else if ((version & 0xC0) == 0x40) {
            // Only use the relevant two bytes of the four byte checksum.

            logger.debug("CRC32 of payload={}", crc32.toHexString());
            logger.debug("payload_checksum={}", this.payload_checksum.toHexString());

            byte[] computed = crc32.asByteArray();
            byte[] fromHeader = this.payload_checksum.asByteArray();

            logger.debug("computed={}, fromHeader={}", computed, fromHeader);

            logger.debug("computed[2]={}, fromHeader[1]={}", computed[2], fromHeader[1]);
            logger.debug("computed[3]={}, fromHeader[0]={}", computed[3], fromHeader[0]);

            if (computed[0] != fromHeader[0] || computed[1] != fromHeader[1]) {
                logger.warn("you have received a bad message, the checksums [{}:{}] did not match",
                        crc32.toHexString(),
                        this.payload_checksum.toHexString());
                return false;
            }
        } else {
            logger.error("attempting to verify payload checksum but version invalid");
        }

        return true;
    }
}

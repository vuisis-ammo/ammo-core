package edu.vu.isis.ammo.core.network;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Comparator;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.pb.AmmoMessages;

/**
 * see /opt/ammo/Gateway/AndroidGatewayPlugin/
 * <ul>
 * <li>AndroidServiceHandler.h</li>
 * <li>AndroidServiceHandler.cpp</li>
 * </ul>
 * 
 * The total message is of the form...
 * 
 * <ul>
 * <li> 4 Bytes : magic</li>
 * <li> 4 Bytes : message size</li>
 * <li> 1 Byte  : priority </li>
 * <li> 3 Bytes : reserved</li>
 * <li> 4 Bytes : payload checksum</li>
 * <li> 4 Bytes : header checksum</li>
 * <li> N bytes : payload</li>
 * </ul>
 * 
 * The magic is set to 0xfeedbeef.
 * The primary purpose of the magic is assist in detecting the start of messages.
 * 
 * The message size is encoded as unsigned little endian, 
 * the size of the payload, and only the payload, in bytes.
 * 
 * The priority, also appears in the payload and is copied from there.
 * 
 * The reserved bytes, for future use.
 * 
 * The payload checksum, the CRC32 checksum of the payload, and only the paylod.
 * The header checksum, the CRC32 checksum of the header, not including the payload nor itself.
 * 
 */
public class AmmoGatewayMessage implements Comparable<Object> {
    private static final Logger logger = LoggerFactory.getLogger(TcpChannel.class);
    
    private static final byte[] MAGIC = {(byte)0xfe, (byte)0xed, (byte)0xbe, (byte)0xef};
    
    @SuppressWarnings("unused")
    private static final long INT_MASK = 0x0FFFFFFFFL; // 
    private static final int BYTE_MASK = 0x0FF;
    private static final long BYTE_MASK_LONG = 0x0FFL;
    
    public static final int HEADER_DATA_LENGTH = 
          4 // magic
        + 4 // message size
        + 1 // priority byte
        + 3 // reserved bytes
        + 4; // payload checksum
    
    public static final int HEADER_LENGTH = 
        HEADER_DATA_LENGTH
      + 4; // header checksum
    
    public final int size;
    public final byte priority;
    public final long payload_checksum;
    public final byte[] payload;
    public final INetworkService.OnSendMessageHandler handler;

    public final boolean isMulticast;
    public final boolean isGateway;

    /**
     * @return
     * a negative integer if this instance is less than another; 
     * a positive integer if this instance is greater than another; 
     * 0 if this instance has the same order as another.
     * 
     * priority first
     * smaller message have higher priority.
     * 
     * @throws
     * ClassCastException
     */
    @Override
    public int compareTo(Object another) {
        if (another instanceof AmmoGatewayMessage) 
            throw new ClassCastException("does not compare with AmmoGatewayMessage");
        
        AmmoGatewayMessage that = (AmmoGatewayMessage) another;
        if (this.priority > that.priority) return 1;
        if (this.priority < that.priority) return -1;
        if (this.size < that.size) return 1;
        if (this.size > that.size) return -1;
        if (this.payload_checksum < that.payload_checksum) return 1;
        if (this.payload_checksum > that.payload_checksum) return -1;
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
     * This gives a partial ordering, rather than total ordering of the natural order.
     */
    public static class PriorityOrder implements Comparator<AmmoGatewayMessage> {
        @Override
        public int compare(AmmoGatewayMessage o1, AmmoGatewayMessage o2) {
             if (o1.priority > o2.priority) return 1;
             if (o1.priority < o2.priority) return -1;
             return 0;
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("size").append(this.size).append(" ");
        sb.append("priority").append(this.priority).append(" ");
        sb.append("payload checksum").append(Long.toHexString(this.payload_checksum)).append(" ");
        return sb.toString();
    }

    static public class Builder {
        // the size is the intended size, the actual size is that of the payload
        private int size;
        public int size() { return this.size; }
        public Builder size(int val) { this.size = val; return this; }
    
        private byte priority;
        public byte priority() { return this.priority; }
        public Builder priority(byte val) { this.priority = val; return this; }
        public Builder priority(int val) { this.priority = (byte)val; return this; }
        
        private long checksum;
        public long checksum() { return this.checksum; }
        public Builder checksum(long val) { this.checksum = val; return this; }
        
        private INetworkService.OnSendMessageHandler handler;
        public INetworkService.OnSendMessageHandler handler() { return this.handler; }
        public Builder handler(INetworkService.OnSendMessageHandler val) { this.handler = val; return this; }
        
        private boolean isMulticast;
        public boolean isMulticast() { return this.isMulticast; }
        public Builder isMulticast(boolean val) { this.isMulticast = val; return this; }
        
        private boolean isGateway;
        public boolean isGateway() { return this.isGateway; }
        public Builder isGateway(boolean val) { this.isGateway = val; return this; }
        
        public AmmoGatewayMessage payload(byte[] val) { 
            if (this.size != val.length)
                throw new IllegalArgumentException("payload size incorrect");
            return new AmmoGatewayMessage(this, val);
        }
        
        /**
         * Describe the message.
         * 
         */
        private Builder() {
            this.size = 0;
            this.priority = PriorityLevel.NORMAL.b();
            this.checksum = 0;
            this.handler = null;
        }
    }
   
    private AmmoGatewayMessage(Builder builder, byte[] payload) {    
        this.size = builder.size;
        if (this.size != payload.length)
            throw new IllegalArgumentException("payload size incorrect");
        this.priority = builder.priority;
        this.payload_checksum = builder.checksum;
        this.payload = payload;
        this.handler = builder.handler;
        
        this.isMulticast = builder.isMulticast;
        this.isGateway = builder.isGateway;
    }
    public static AmmoGatewayMessage newInstance( AmmoMessages.MessageWrapper.Builder mwb,
            INetworkService.OnSendMessageHandler handler) {
        byte[] payload = mwb.build().toByteArray();
    
        CRC32 crc32 = new CRC32();
        crc32.update(payload);
         
        AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder()
            .size(payload.length)
            .checksum(crc32.getValue())
            .priority(PriorityLevel.NORMAL.v)
            .handler(handler);
        return agmb.payload(payload);
    }
    
    public static AmmoGatewayMessage.Builder newBuilder() {
        return new AmmoGatewayMessage.Builder();
    }
    public static AmmoGatewayMessage.Builder newBuilder(int size, long checksum, byte priority,
            INetworkService.OnSendMessageHandler handler) {
        return new AmmoGatewayMessage.Builder()
             .size(size)
             .checksum(checksum)
             .priority(priority)
             .handler(handler);
    }
    
   
    
    /**
     * Serialize the AmmoMessage for transmission to the gateway.
     * @return
     */
    public ByteBuffer serialize(ByteOrder endian) {
         int total_length = HEADER_LENGTH + this.payload.length;
         ByteBuffer buf = ByteBuffer.allocate( total_length );
         buf.order( endian );
         
         buf.put(MAGIC[3]);
         buf.put(MAGIC[2]);
         buf.put(MAGIC[1]);
         buf.put(MAGIC[0]);
         
         buf.putInt( this.size );
         logger.debug( "   size={}", this.size );
         
         buf.put( this.priority );
         buf.put((byte) 0x0).put((byte) 0x0).put((byte) 0x0); // three reserved bytes
         
         logger.debug( "   payload_checksum={}", this.payload_checksum );
         buf.put( convertChecksum(this.payload_checksum), 0, 4 );
         
         // checksum of header
         int pos = buf.position();
         byte[] base = buf.array();
         CRC32 crc32 = new CRC32();
         crc32.update(base, 0, pos);
         buf.put( convertChecksum(crc32.getValue()) );
        
         // payload
         buf.put( this.payload );
         logger.debug( "   payload={}", this.payload );
         buf.flip();
         return buf;
    }
   
    /**
     *  Must leave the position of drain pointing at the first byte 
     *  which might be the first byte in a header.
     *  
     * @param drain
     * @return
     * @throws IOException
     */
    static public AmmoGatewayMessage.Builder extractHeader(ByteBuffer drain) throws IOException
    {
        // the mark is used to indicate the position where a header may start
        try {
            while( drain.remaining() > 0 ) {
                drain.mark();
                int start = drain.arrayOffset() + drain.position();
                // search for the magic
                if (drain.get() != MAGIC[3]) continue;
                if (drain.get() != MAGIC[2]) continue;
                if (drain.get() != MAGIC[1]) continue;
                if (drain.get() != MAGIC[0]) continue;
                
                int size = drain.getInt();
                
                int priority = drain.get() & BYTE_MASK;
                logger.debug( "   priority={}", priority );
                
                // reserved bytes
                drain.get();  
                drain.getShort();
                
                byte[] checkBytes = new byte[ 4 ];
                
                drain.get( checkBytes, 0, 4 );
                logger.debug( "   payload check={}", checkBytes );
                long payload_checksum = convertChecksum(checkBytes);
                
                drain.get( checkBytes, 0, 4 );
                logger.debug( "   header check={}", checkBytes );
                long header_checksum = convertChecksum(checkBytes);
                
                CRC32 crc32 = new CRC32();
                crc32.update(drain.array(), start, HEADER_DATA_LENGTH);
                if (header_checksum != crc32.getValue()) 
                    continue;
    
                return AmmoGatewayMessage.newBuilder(size, payload_checksum, (byte)priority, null);
            }
        } catch (BufferUnderflowException ex) {
            // the data was looking like a header as far as it went
            drain.reset(); 
        }
        return null;
    }
    
    /**
     * AUTH : authentication message only
     * CTRL : all control type messages, not data messages: subscribe, pull, heart beat
     * No data, push, messages should be allowed to use anything within the AUTH and CTRL span.
     * If a data message tries to use a value in that range it should be degraded to FLASH.
     *
     */
    public enum PriorityLevel {
        AUTH(127), CTRL(112), FLASH(96), URGENT(64), IMPORTANT(32), NORMAL(0), BACKGROUND(-32);
        public int v;
        public byte b() { return (byte) this.v; }
        private PriorityLevel(int value) {
            this.v = value;
        }
    }
    
    /**
     * The four least significant bytes of a long 
     * make the checksum array.
     * 
     * @param cvalue
     * @return
     */
    private static byte[] convertChecksum(long cvalue) {
        byte[] checksum = new byte[]
        {
            (byte) cvalue,
            (byte) (cvalue >>> 8),
            (byte) (cvalue >>> 16),
            (byte) (cvalue >>> 24)
        };
        return checksum;
    }
    
    /**
     *  The four bytes of the payload_checksum go into the 
     *  least significant four bytes of a long.
     *  
     * @param checkBytes
     * @return
     */
    private static long convertChecksum(byte[] checkBytes) {
        long checksum = ( 
              ((BYTE_MASK_LONG & checkBytes[0]) << 0)
            | ((BYTE_MASK_LONG & checkBytes[1]) << 8)
            | ((BYTE_MASK_LONG & checkBytes[2]) << 16)
            | ((BYTE_MASK_LONG & checkBytes[3]) << 24) );
        return checksum;
    }


}

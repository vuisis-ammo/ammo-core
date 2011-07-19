package edu.vu.isis.ammo.core.network;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
public class AmmoGatewayMessage {
    private static final Logger logger = LoggerFactory.getLogger(TcpChannel.class);
    
    private static final int MAGIC = 0xfeedbeef; // {(byte)0xfe, (byte)0xed, (byte)0xbe, (byte)0xef};
    
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

    static public class Builder {
        public final int size;
        public final byte priority;
        public final long payload_checksum;
        public final INetworkService.OnSendMessageHandler handler;
        
        public AmmoGatewayMessage payload(byte[] val) { 
            if (this.size != val.length)
                throw new IllegalArgumentException("payload size incorrect");
            return new AmmoGatewayMessage(this.size, this.payload_checksum, this.priority, val, this.handler);
        }
        
        private Builder(int size, long checksum, byte priority, 
                INetworkService.OnSendMessageHandler handler) {    
            this.size = size;
            this.priority = priority;
            this.payload_checksum = checksum;
            this.handler = handler;
        }
    }
   
    public static AmmoGatewayMessage getInstance(AmmoMessages.MessageWrapper.Builder amb, 
            INetworkService.OnSendMessageHandler handler) {
         AmmoMessages.MessageWrapper amw = amb.build();
         
         byte[] payload = amw.toByteArray();
         
         byte priority = (byte) amw.getMessagePriority();
        
        CRC32 crc32 = new CRC32();
        crc32.update(payload);
        return new AmmoGatewayMessage(payload.length, crc32.getValue(), (byte) priority, payload, handler);
    }

    private AmmoGatewayMessage(int size, long checksum, byte priority, byte[] payload, 
            INetworkService.OnSendMessageHandler handler) {    
        this.size = size;
        if (this.size != payload.length)
            throw new IllegalArgumentException("payload size incorrect");
        this.priority = priority;
        this.payload_checksum = checksum;
        this.payload = payload;
        this.handler = handler;
    }
    
    public static AmmoGatewayMessage.Builder getBuilder(int size, long checksum, byte priority,
            INetworkService.OnSendMessageHandler handler) {
        return new AmmoGatewayMessage.Builder(size, checksum, (byte) priority, handler);
    }
    
   
    
    /**
     * Serialize the AmmoMessage for transmission to the gateway.
     * @return
     */
    public ByteBuffer serialize(ByteOrder endian) {
         int total_length = HEADER_LENGTH + this.payload.length;
         ByteBuffer buf = ByteBuffer.allocate( total_length );
         buf.order( endian );
         
         buf.putInt(MAGIC);
         
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
                if (drain.getInt() != MAGIC) continue;
                //if (drain.get() != MAGIC[0]) continue;
                //if (drain.get() != MAGIC[1]) continue;
                //if (drain.get() != MAGIC[2]) continue;
                //if (drain.get() != MAGIC[3]) continue;
                
                int size = drain.getInt();
                
                int priority = drain.get() & BYTE_MASK;
                logger.debug( "   priority={}", priority );
                
                byte[] checkBytes = new byte[ 4 ];
                drain.get( checkBytes, 0, 4 );
                logger.debug( "   payload check={}", checkBytes );
                long payload_checksum = convertChecksum(checkBytes);
                
                checkBytes = new byte[ 4 ];
                drain.get( checkBytes, 0, 4 );
                logger.debug( "   header check={}", checkBytes );
                long header_checksum = convertChecksum(checkBytes);
                
                CRC32 crc32 = new CRC32();
                crc32.update(drain.array(), start, HEADER_DATA_LENGTH);
                if (header_checksum != crc32.getValue()) 
                    continue;
    
                return AmmoGatewayMessage.getBuilder(size, payload_checksum, (byte)priority, null);
            }
        } catch (BufferUnderflowException ex) {
            // the data was looking like a header as far as it went
            drain.reset(); 
        }
        return null;
    }
    
    /**
     * AUTH : authentication message only
     * CTRL : all control type messages, not data messages: subscribe, pull, heartbeat
     * No data, push, messages should be allowed to use anthing within the AUTH and CTRL span.
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

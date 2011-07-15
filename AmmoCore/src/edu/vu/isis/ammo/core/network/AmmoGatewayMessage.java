package edu.vu.isis.ammo.core.network;

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
    
    private static final byte[] MAGIC = {(byte)0xfe, (byte)0xed, (byte)0xbe, (byte)0xef};
    private static final int HEADER_LENGTH = 
          4 // magic
        + 4 // message size
        + 1 // priority byte
        + 3 // reserved bytes
        + 4 // payload checksum
        + 4; // header checksum
    
    public final int size;
    public final byte priority;
    public final long payload_checksum;
    public final byte[] payload;
    public final INetworkService.OnSendMessageHandler handler;
    

    public static AmmoGatewayMessage getInstance(AmmoMessages.MessageWrapper.Builder amb, INetworkService.OnSendMessageHandler handler) {
    	byte[] payload = amb.build().toByteArray();
    	byte priority = (byte) amb.getMessagePriority();
        CRC32 crc32 = new CRC32();
        crc32.update(payload);
        return new AmmoGatewayMessage(payload.length, crc32.getValue(), (byte) priority, payload, handler);
    }


    private AmmoGatewayMessage(int size, long checksum, byte priority, byte[] payload, INetworkService.OnSendMessageHandler handler) {    
        this.size = size;
        this.priority = priority;
        this.payload_checksum = checksum;
        this.payload = payload;
        this.handler = handler;
    }
    
    
    /**
     * Serialize the AmmoMessage for transmission to the gateway.
     * @return
     */
    public ByteBuffer serialize(ByteOrder endian) {
    	 int total_length = HEADER_LENGTH + this.payload.length;
    	 ByteBuffer buf = ByteBuffer.allocate( total_length );
         buf.order( endian );
         
         buf.put(MAGIC, 0, 4);
         
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
    
    private static final long INT_MASK = 0x0FFFFFFFFL; // 
    /**
     *  If the header is corrupted, things could go horribly wrong. 
     *  We need better error detection and error handling 
     *  when we're reading stuff in off the network.
     *  We look for the magic bytes, we presume the start of a message.
     *  We roll bytes from the magic back to the start of the buffer.
     *  
     *  Verify the checksum for the header.
     *  
     *  If that message size (plus header) is less than the number 
     *  of bytes in the buffer, we have a complete packet.
     */
    static public boolean bufferContainsAMessage(ByteBuffer buf, ByteOrder endian)
    {
    	// there must be at least as many bytes as constitute a header
    	if (buf.position() < HEADER_LENGTH) return false;
    	
    	// work with a duplicate so the original can remain unchanged
    	ByteBuffer wip = buf.duplicate();
        wip.flip(); // Switches to draining mode
        wip.order(endian);
        // search for the magic and checksum
        while( wip.hasRemaining() ) {
        	if (wip.remaining() < HEADER_LENGTH) break;
        	wip.mark();
        	if (wip.get() != MAGIC[0]) continue;
        	if (wip.get() != MAGIC[1]) continue;
        	if (wip.get() != MAGIC[2]) continue;
        	if (wip.get() != MAGIC[3]) continue;
        	wip.reset();
        	
        	CRC32 crc32 = new CRC32();
        	crc32.update(wip.array(), wip.position(), (HEADER_LENGTH-4));
        	long header_checksum = wip.getInt(wip.position()+HEADER_LENGTH-4) & INT_MASK;
        	if (header_checksum != crc32.getValue()) continue;
        	
        	int message_size = wip.getInt();
            return (message_size + HEADER_LENGTH) >= buf.remaining();
        }
        // no message begins with an byte currently in the buffer.
        wip.compact();
        buf = wip;
    	return false;
    }


    /**
     * The method does not check for the presence of a message.
     * The presence of a message is presumed.
     * Only called while in draining mode (after calling flip().
     * Returns true if we process a message successfully and others may be available. 
     * Returns false if there are not further messages available.
     * 
     * @param buf
     * @return
     * @throws InterruptedException
     */
    static public AmmoGatewayMessage processAMessage(ByteBuffer buf) throws InterruptedException
    {
        buf.getInt(); // pass the magic
        
        int messageSize = buf.getInt();

        logger.debug( "Receiving message:" );
        logger.debug( "   message size={}", messageSize );
        
        int priority = buf.get() & 0x0FF;
        logger.debug( "   priority={}", priority );
        
        byte[] checkBytes = new byte[ 4 ];
        buf.get( checkBytes, 0, 4 );
        logger.debug( "   checkBytes={}", checkBytes );
        long payload_checksum = convertChecksum(checkBytes);
        logger.debug( "   payload_checksum={}", Long.toHexString(payload_checksum) );
        buf.getInt(); // bypass header checksum, previously validated.
        
        byte[] message = new byte[messageSize];
        buf.get( message, 0, messageSize );
        logger.debug( "   message={}", message );
        // validating checksum on payload is done on the distributor thread, not here
        
        return new AmmoGatewayMessage(messageSize, payload_checksum, (byte)priority, message, null);
    }
    
    public enum PriorityLevel {
    	FLASH(128), URGENT(64), IMPORTANT(32), NORMAL(16), BACKGROUND(8);
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
              ((0x0FFL & checkBytes[0]) << 0)
            | ((0x0FFL & checkBytes[1]) << 8)
            | ((0x0FFL & checkBytes[2]) << 16)
            | ((0x0FFL & checkBytes[3]) << 24) );
        return checksum;
    }


}

package edu.vu.isis.ammo.util;

import java.nio.ByteBuffer;

/**
 * Simple wrapper around zlib's crc32 function.  Unlike 
 * {@link java.util.zip.CRC32} this will properly handle 
 * direct and heap {@link ByteBuffer}s 
 * 
 * @author mriley
 */
public final class CRC32 {

	/**
	 * Update the crc with the data in the given {@link ByteBuffer}
	 * from the buffer's current position to the buffer's limit. 
	 * 
	 * @param crc the current crc
	 * @param buf the data
	 * @return the updated crc
	 */
	public static long update( long crc, ByteBuffer buf ) {
		if( buf.isDirect() ) {
			return updateBuffer(crc, buf.position(), buf.limit(), buf);
		} else {
			return update(crc, buf.array(), buf.position(), buf.limit());
		}
	}

	/**
	 * Update the crc with the data in the given byte[]
	 * 
	 * @param crc the current crc
	 * @param buf the data
	 * @return the updated crc
	 */
	public static long update( long crc, byte[] buf ) {
		return update(crc, buf, 0, buf.length);
	}
	
	/**
	 * Update the crc with the data in the given byte[] from the given
	 * offset for the given length
	 * 
	 * @param crc the current crc
	 * @param buf the data
	 * @param off the offset
	 * @param len the length 
	 * @return the updated crc
	 */
	public static long update( long crc, byte[] buf, int off, int len ) {
		if( buf == null ) {
			throw new IllegalArgumentException("Array is null");
		}
		if( off < 0 || off >= buf.length || len <= 0 || len > buf.length - off ) {
			throw new ArrayIndexOutOfBoundsException("Bad offset(" + 
					off + ") or length(" + len + ") provided for array(" + buf.length + ")");	
		}
		return updateBytes(crc, off, len, buf);
	}
	
	private static native long updateBuffer( long crc, int off, int len, ByteBuffer buf );
	private static native long updateBytes( long crc, int off, int len, byte[] buf );
	
	private CRC32() {}
}

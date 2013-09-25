package edu.vu.isis.ammo.util;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * Simple wrapper around java's crc32 function.  Unlike 
 * {@link ZlibCRC32} this doesn't handle direct and heap 
 * {@link ByteBuffer}s as efficiently.  I'm only really
 * using this for testing right now.
 * 
 * @author mriley
 */
class ZipCRC32 extends CheckSum {

	private final CRC32 crc = new CRC32();
	
	@Override
	public CheckSum update(byte[] buffer, int off, int len) {
		crc.update(buffer, off, len);
		return this;
	}

	@Override
	public CheckSum update(ByteBuffer buffer) {
		if( buffer.hasArray() ) {
			update(buffer.array(), buffer.position(), buffer.limit() - buffer.position());
		} else {
			byte[] tmp = new byte[4*1024];
			int position = buffer.position();
			while( buffer.remaining() > 0 ) {
				int count = Math.min(tmp.length, buffer.remaining());
				buffer.get(tmp, 0, count);
				update(tmp, 0, count);
			}
			buffer.position(position);
		}
		return this;
	}
	
	@Override
	public long getValue() {
		return crc.getValue();
	}
}

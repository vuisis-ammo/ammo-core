package edu.vu.isis.ammo.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class StringChannel implements ReadableByteChannel, WritableByteChannel {

	private final StringBuilder buf = new StringBuilder();
	
	public StringChannel() {
	}
	
	public StringChannel(String data) {
		buf.append(data);
	}
	
	@Override
	public void close() throws IOException {
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public int write(ByteBuffer buffer) throws IOException {
		byte[] tmp = new byte[Math.min(buffer.remaining(),1024)];
		int count = 0;
		while( buffer.remaining() > 0 ) {
			int c = Math.min(tmp.length, buffer.remaining());
			buffer.get(tmp, 0, c);
			buf.append(new String(tmp,0,c));
			count += c;
		}
		return count;
	}

	@Override
	public int read(ByteBuffer buffer) throws IOException {
		byte[] d = buf.toString().getBytes();
		int count = Math.min(d.length, buffer.remaining());
		buffer.put(d, 0, count);
		return count;
	}
	
	@Override
	public String toString() {
		return buf.toString();
	}
}

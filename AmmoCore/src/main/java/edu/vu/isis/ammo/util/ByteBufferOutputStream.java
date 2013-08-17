package edu.vu.isis.ammo.util;

import java.io.IOException;
import java.io.OutputStream;

public class ByteBufferOutputStream extends OutputStream {

	private final ByteBufferAdapter buffer;
	
	public ByteBufferOutputStream(ByteBufferAdapter buffer) {
		this.buffer = buffer;
	}
	
	@Override
	public void write(int oneByte) throws IOException {
		buffer.put((byte) oneByte);
	}
	
	@Override
	public void write(byte[] buffer) throws IOException {
		write(buffer, 0, buffer.length);
	}
	
	public void write(byte[] buffer, int offset, int count) throws IOException {
		this.buffer.put(buffer, offset, count);
	}
}

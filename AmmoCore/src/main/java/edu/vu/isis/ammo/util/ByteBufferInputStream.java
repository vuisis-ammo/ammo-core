package edu.vu.isis.ammo.util;

import java.io.IOException;
import java.io.InputStream;

public final class ByteBufferInputStream extends InputStream {

	private final ByteBufferAdapter byteBuffer;
	
	public ByteBufferInputStream(ByteBufferAdapter byteBuffer) {
		this.byteBuffer = byteBuffer;
	}
	
	@Override
	public synchronized void reset() throws IOException {
		byteBuffer.reset();
	}
	
	@Override
	public synchronized void mark(int readlimit) {
		byteBuffer.mark();
	}
	
	@Override
	public boolean markSupported() {
		return true;
	}
	
	@Override
	public long skip(long n) throws IOException {
		if( n <= 0 ) return 0;
		int num = (int) Math.min(n, byteBuffer.remaining());
		byteBuffer.position(byteBuffer.position()+num);
		return num;
	}
	
	@Override
	public int available() throws IOException {
		return byteBuffer.remaining();
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		len = Math.min(len, byteBuffer.remaining());
		if( len <= 0 )
			return -1;
		byteBuffer.get(b, off, len);
		return len;
	}

	@Override
	public int read() throws IOException {
		if( byteBuffer.remaining() <= 0 )
			return -1;
		int b = byteBuffer.get();
		return b & 0xff;
	}
}
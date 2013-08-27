package edu.vu.isis.ammo.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MMapByteBufferAdapter extends NioByteBufferAdapter {

	private static final Logger logger = LoggerFactory.getLogger("util.bufferadapter.mmap");
	
	/**
	 * The MappedByteBufferAdapter on some android versions is junk.  It doesn't
	 * implement calls like get(BII) so we get things like get() called
	 * thousands and thousands of times.  However, the buffer that it wraps
	 * implements this method.
	 * 
	 * @param buffer
	 * @return
	 */
	private static ByteBuffer unwrap( ByteBuffer buffer ) {
		try {
			Field declaredField = buffer.getClass().getSuperclass().getDeclaredField("wrapped");
			declaredField.setAccessible(true);
			return (ByteBuffer) declaredField.get(buffer);
		} catch (Exception e) {
			logger.error("I guess this isn't a MappedByteBufferAdapter " + buffer.getClass().getName() + ".  " + e);
		}
		return buffer;
	}
	
	private File file;
	private RandomAccessFile raf;

	public MMapByteBufferAdapter( RandomAccessFile raf, File file, int size ) throws IOException {
		super(unwrap(raf.getChannel().map(MapMode.READ_WRITE, 0, size)));
		this.raf = raf;
		this.file = file;
	}
	
	@Override
	public boolean isPoolable() {
		return true;
	}
	
	@Override
	public boolean free() {
		if( raf != null ) {
			try {
				super.free();
			} catch ( Exception ignored ) {}
			try {
				raf.close();
				raf = null;
				file.delete();
				file = null;
				return true;
			} catch ( Exception ignored ) {}
		}
		return false;
	}
}

package edu.vu.isis.ammo.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel.MapMode;

public class MMapByteBufferAdapter extends NioByteBufferAdapter {

	private File file;
	private RandomAccessFile raf;

	public MMapByteBufferAdapter( RandomAccessFile raf, File file, int size ) throws IOException {
		super(raf.getChannel().map(MapMode.READ_WRITE, 0, size));
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

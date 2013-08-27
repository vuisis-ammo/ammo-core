package edu.vu.isis.ammo.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * You might be asking, "Why is this a copy of {@link java.nio.ByteBuffer}?".  Well, I'll tell
 * you.  The problem with ByteBuffer is the same problem we find with many sun designed
 * APIs...they are completely non-extensible.  It would've been trivial for sun to make
 * the {@link java.nio.Buffer} hierarchy extensible.  In fact, it is extensible...so long as 
 * you work at sun.  We could add our extensions to a java.nio package but we shouldn't
 * have to do that.
 * <br/><br/>
 * With that said, the {@link java.nio.Buffer} interface is really nice.  So here we make our own
 * slight modifications to a copy of the interface.  BLERG.
 * 
 * @author mriley
 */
public abstract class ByteBufferAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger("util.bufferadapter");

	// ===========================================================
	// Create methods
	// ===========================================================
	
	/**
	 * Get a {@link ExpandoByteBufferAdapter} to contain data of unknown size.
	 * It's cool to call {@link #release()} when you are finished.
	 * @return
	 */
	public static ExpandoByteBufferAdapter obtain() {
		return new ExpandoByteBufferAdapter();
	}
	
	/**
	 * Get a {@link ByteBufferAdapter}.  It's cool to call {@link #release()}
	 * when you are finished.
	 * @param size
	 * @return
	 */
	public static ByteBufferAdapter obtain( int size ) {
		ByteBufferAdapter adapter = ByteBufferPool.getInstance().allocate(size);
		adapter.initAllocStack();
		return adapter;
	}
	
	/**
	 * Get a {@link ByteBufferAdapter} that wraps the give data.  It's cool 
	 * to call {@link #release()} when you are finished.
	 * @param data
	 * @return
	 */
	public static ByteBufferAdapter obtain( byte[] data ) {
		return obtain(ByteBuffer.wrap(data));
	}
	
	/**
	 * Get a {@link ByteBufferAdapter} that wraps the give data.  It's cool 
	 * to call {@link #release()} when you are finished.
	 * @param data
	 * @return
	 */
	public static ByteBufferAdapter obtain( ByteBuffer data ) {
		return new NioByteBufferAdapter(data);
	}
	
	// ===========================================================
	// Members
	// ===========================================================
	
	private String allocStack;
	
	public ByteBufferAdapter() {
		initAllocStack();
	}

	private final void initAllocStack() {
		StringWriter stack = new StringWriter();
		new Exception().printStackTrace(new PrintWriter(stack));
		allocStack = stack.toString();
	}
	
	
	// ===========================================================
	// Normal byte buffer things
	// ===========================================================
		
	/**
	 * @see ByteBuffer#array()
	 */
	public abstract byte[] array();
	/**
	 * @see ByteBuffer#arrayOffset()
	 */
	public abstract int arrayOffset();
	/**
	 * @see ByteBuffer#capacity()
	 */
	public abstract int capacity();
	/**
	 * @see ByteBuffer#clear()
	 */
	public abstract ByteBufferAdapter clear();
	/**
	 * @see ByteBuffer#compact()
	 */
	public abstract ByteBufferAdapter compact();
	/**
	 * @see ByteBuffer#flip()
	 */
	public abstract ByteBufferAdapter flip();
	/**
	 * @see ByteBuffer#duplicate()
	 */
	public abstract ByteBufferAdapter duplicate();
	/**
	 * @see ByteBuffer#limit()
	 */
	public abstract int limit();
	/**
	 * @see ByteBuffer#limit(int)
	 */
	public abstract ByteBufferAdapter limit(int newLimit);
	/**
	 * @see ByteBuffer#mark()
	 */
	public abstract ByteBufferAdapter mark();
	/**
	 * @see ByteBuffer#order()
	 */
	public abstract ByteOrder order();
	/**
	 * @see ByteBuffer#order(ByteOrder)
	 */
	public abstract ByteBufferAdapter order(ByteOrder byteOrder);
	/**
	 * @see ByteBuffer#position()
	 */
	public abstract int position();
	/**
	 * @see ByteBuffer#position(int)
	 */
	public abstract ByteBufferAdapter position(int newPosition);
	/**
	 * @see ByteBuffer#remaining()
	 */
	public abstract int remaining();
	/**
	 * @see ByteBuffer#reset()
	 */
	public abstract ByteBufferAdapter reset();
	/**
	 * @see ByteBuffer#rewind()
	 */
	public abstract ByteBufferAdapter rewind();
	/**
	 * @see ByteBuffer#slice()
	 */
	public abstract ByteBufferAdapter slice();
	
	/**
	 * @see ByteBuffer#hasArray()
	 */
	public abstract boolean hasArray();
	/**
	 * @see ByteBuffer#hasRemaining()
	 */
	public abstract boolean hasRemaining();	
	/**
	 * @see ByteBuffer#isDirect()
	 */
	public abstract boolean isDirect();
	/**
	 * @see ByteBuffer#isReadOnly()
	 */
	public abstract boolean isReadOnly();
	
	/**
	 * @see ByteBuffer#get()
	 */
	public abstract byte get();
	/**
	 * @see ByteBuffer#get(byte[], int, int)
	 */
	public abstract ByteBufferAdapter get(byte[] dst, int dstOffset, int byteCount);
	/**
	 * @see ByteBuffer#get(byte[])
	 */
	public abstract ByteBufferAdapter get(byte[] dst);
	/**
	 * @see ByteBuffer#get(int)
	 */
	public abstract byte get(int index);
	/**
	 * @see ByteBuffer#getChar()
	 */
	public abstract char getChar();
	/**
	 * @see ByteBuffer#getChar(int)
	 */
	public abstract char getChar(int index);
	/**
	 * @see ByteBuffer#getDouble()
	 */
	public abstract double getDouble();
	/**
	 * @see ByteBuffer#getDouble(int)
	 */
	public abstract double getDouble(int index);
	/**
	 * @see ByteBuffer#getFloat()
	 */
	public abstract float getFloat();
	/**
	 * @see ByteBuffer#getFloat(int)
	 */
	public abstract float getFloat(int index);
	/**
	 * @see ByteBuffer#getInt()
	 */
	public abstract int getInt();
	/**
	 * @see ByteBuffer#getInt(int)
	 */
	public abstract int getInt(int index);
	/**
	 * @see ByteBuffer#getLong()
	 */
	public abstract long getLong();
	/**
	 * @see ByteBuffer#getLong(int)
	 */
	public abstract long getLong(int index);
	/**
	 * @see ByteBuffer#getShort()
	 */
	public abstract short getShort();
	/**
	 * @see ByteBuffer#getShort(int)
	 */
	public abstract short getShort(int index);


	/**
	 * @see ByteBuffer#put(byte)
	 */
	public abstract ByteBufferAdapter put(byte b);
	/**
	 * @see ByteBuffer#put(byte[], int, int)
	 */
	public abstract ByteBufferAdapter put(byte[] src, int srcOffset, int byteCount);
	/**
	 * @see ByteBuffer#put(byte[])
	 */
	public abstract ByteBufferAdapter put(byte[] src);
	/**
	 * @see ByteBuffer#put(ByteBuffer)
	 */
	public abstract ByteBufferAdapter put(ByteBuffer src);
	/**
	 * @see ByteBuffer#put(ByteBuffer)
	 */
	public abstract ByteBufferAdapter put(ByteBufferAdapter src);
	/**
	 * @see ByteBuffer#put(int, byte)
	 */
	public abstract ByteBufferAdapter put(int index, byte b);
	/**
	 * @see ByteBuffer#putChar(char)
	 */
	public abstract ByteBufferAdapter putChar(char value);
	/**
	 * @see ByteBuffer#putChar(int, char)
	 */
	public abstract ByteBufferAdapter putChar(int index, char value);
	/**
	 * @see ByteBuffer#putDouble(double)
	 */
	public abstract ByteBufferAdapter putDouble(double value);
	/**
	 * @see ByteBuffer#putDouble(int, double)
	 */
	public abstract ByteBufferAdapter putDouble(int index, double value);
	/**
	 * @see ByteBuffer#putFloat(float)
	 */
	public abstract ByteBufferAdapter putFloat(float value);
	/**
	 * @see ByteBuffer#putFloat(int, float)
	 */
	public abstract ByteBufferAdapter putFloat(int index, float value);
	/**
	 * @see ByteBuffer#putInt(int, int)
	 */
	public abstract ByteBufferAdapter putInt(int index, int value);
	/**
	 * @see ByteBuffer#putInt(int)
	 */
	public abstract ByteBufferAdapter putInt(int value);
	/**
	 * @see ByteBuffer#putLong(int, long)
	 */
	public abstract ByteBufferAdapter putLong(int index, long value);
	/**
	 * @see ByteBuffer#putLong(long)
	 */
	public abstract ByteBufferAdapter putLong(long value);
	/**
	 * @see ByteBuffer#putShort(int, short)
	 */
	public abstract ByteBufferAdapter putShort(int index, short value);
	/**
	 * @see ByteBuffer#putShort(short)
	 */
	public abstract ByteBufferAdapter putShort(short value);

	// ===========================================================
	// Utils
	// ===========================================================
	
	/**
	 * @return A checksum for this data
	 */
	public CheckSum checksum() {
		return checksum(CheckSum.newInstance());
	}
	
	/**
	 * Update the checksum with data from this 
	 * @param checksum
	 * @return this
	 */
	public abstract CheckSum checksum( CheckSum checksum );
	
	// ===========================================================
	// nio buffer access
	// ===========================================================
	/**
	 * @return true if there is a backing buffer
	 */
	public abstract boolean hasBuffer();
	/**
	 * @return The backing {@link ByteBuffer} if there is one
	 */
	public abstract ByteBuffer buffer();
	
	// ===========================================================
	// read/write channel stuff
	// ===========================================================
	/**
	 * @see ReadableByteChannel#read(ByteBuffer)
	 */
	public abstract int read( ReadableByteChannel channel ) throws IOException;
	/**
	 * @see WritableByteChannel#write(ByteBuffer)
	 */
	public abstract int write( WritableByteChannel channel ) throws IOException;
	
	
	// ===========================================================
	// pool stuff
	// ===========================================================
	
	/**
	 * @return True if this guy may be pooled
	 */
	public boolean isPoolable() {
		return isDirect();
	}
	
	/**
	 * Release this buffer back into the pool if pooling is enabled and this is
	 * a poolable kind of {@link ByteBufferAdapter}.  See the {@link ByteBufferPool}
	 * if you needs to know what kind of things are pooled.
	 * @return true if this was released into the pool
	 */
	public boolean release() {
		return ByteBufferPool.getInstance().release(this);
	}
	
	/**
	 * Free any system resources that this buffer may have allocated.  If you have
	 * released this buffer do not free it!
	 * @return true if something was freed
	 */
	public boolean free() {
		return false;
	}
	
	@Override
	protected void finalize() throws Throwable {
		if( free() ) {
			logger.warn("WARNING: Buffer allocated but never freed!");
			logger.warn("Alloc trace: " + allocStack);
		}
		super.finalize();
	}
}

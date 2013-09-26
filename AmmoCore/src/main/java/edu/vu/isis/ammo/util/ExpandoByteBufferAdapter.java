package edu.vu.isis.ammo.util;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Manages an internal collection of byte buffer adapters to support two things:
 * <ol>
 * <li>Variable length byte buffers</li>
 * <li>A byte buffer of variable length byte buffers.</li>
 * </ol>
 * 
 * These are useful in two situations:
 * <ol>
 * <li>When you don't know the data size</li>
 * <li>When you need a composite adapter allows adapters to be added without
 * copying all of the bytes</li>
 * </ol>
 * 
 * @author mriley
 */
public class ExpandoByteBufferAdapter extends ByteBufferAdapter {

	// ===========================================================
	// Constants
	// ===========================================================
	
	private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
	private static final int SIZE_BYTE = Byte.SIZE / 8;
	private static final int SIZE_SHORT = Short.SIZE / 8;
	private static final int SIZE_INT = Integer.SIZE / 8;
	private static final int SIZE_LONG = Long.SIZE / 8;
	
	// ===========================================================
	// Public inner classes
	// ===========================================================
	
	/**
	 * Used with {@link ExpandoByteBufferAdapter#visit(Visitor, Object)}
	 * 
	 * @author mriley
	 */
	public static interface Visitor<T> {
		/**
		 * Visit the segment.  The segment will never be an
		 * {@link ExpandoByteBufferAdapter}
		 * 
		 * @param segment
		 * @param t The result of the previous visit
		 * @return anything you want
		 */
		public T visit( ByteBufferAdapter segment, T t );
	}
		
	// ===========================================================
	// Members
	// ===========================================================
	/**
	 * The size each dynamic chunk should be
	 */
	private final int chunkSize;
	/**
	 * The current chunk node
	 */
	private Chunk currentChunk;
	/**
	 * The head of my chunk chain
	 */
	private Chunk headChunk;
	/**
	 * The limit of this buffer
	 */
	private int limit;
	/**
	 * The current position of this buffer
	 */
	private int position;
	/**
	 * The capacity of this buffer
	 */
	private int capacity;
	/**
	 * The byte order of this buffer
	 */
	private ByteOrder order = ByteOrder.nativeOrder();
	
	
	// ===========================================================
	// CTORs
	// ===========================================================
	
	public ExpandoByteBufferAdapter() {
		this(DEFAULT_CHUNK_SIZE);
	}
	
	public ExpandoByteBufferAdapter(int chunkSize) {
		this.chunkSize = chunkSize;
	}
	
	// ===========================================================
	// Useful public things
	// ===========================================================
	
	/**
	 * Add the {@link ByteBufferAdapter} to then end of the collection
	 * increasing the capacity by the adapter's limit and position +=
	 * the position of the buffer.
	 * <p>The adapter is now owned by this so you should not {@link #free()} or {@link #release()}.  
	 * <p>This method behaves similar to a {@link #put(ByteBufferAdapter)} in the way that it modifies
	 * the adapter.  As such, you'll likely want to {@link #flip()} before you add if you've written
	 * to the adapter.
	 * 
	 * @param adapter
	 * @return
	 */
	public ExpandoByteBufferAdapter add( ByteBufferAdapter adapter ) {
		if( currentChunk == null ) {
			if( adapter instanceof ExpandoByteBufferAdapter ) {
				ExpandoByteBufferAdapter expando = (ExpandoByteBufferAdapter)adapter;
				expando.unflip();
				headChunk = expando.headChunk;
				currentChunk = headChunk.tail();
				expando.headChunk = null;
				expando.currentChunk = null;
			} else {
				headChunk = currentChunk = new Chunk(adapter);
			}
		} else {
			if( adapter instanceof ExpandoByteBufferAdapter ) {
				ExpandoByteBufferAdapter expando = (ExpandoByteBufferAdapter)adapter;
				expando.unflip();
				currentChunk.append(expando.headChunk);
				currentChunk = headChunk.tail();
				expando.headChunk = null;
				expando.currentChunk = null;
			} else {
				currentChunk = currentChunk.append(new Chunk(adapter));
			}
		}
		position += adapter.position();
		capacity = limit = headChunk.capacity();
		return this;
	}
	
	/**
	 * Visit each segment of the chunk chain optionally returning a
	 * value of type <T>.  Each visit will receive the result of the
	 * previous visit.
	 * 
	 * @param visitor The {@link Visitor}
	 * @param t The initial value of <T>
	 * @return The result
	 */
	public <T> T visit( Visitor<T> visitor, T t ) {
		if( headChunk != null ) {
			return headChunk.visit( visitor, t );
		}
		return null;
	}
	
	/**
	 * Tries to do the opposite of {@link #flip()}
	 * @return
	 */
	public ExpandoByteBufferAdapter unflip() {
		if( headChunk != null ) 
			headChunk.unflip();
		currentChunk = headChunk.tail();
		position = limit;
		return this;
	}
	
	// ===========================================================
	// Byte buffer adapter impl
	// ===========================================================
	
	@Override
	public boolean hasBuffer() {
		return false;
	}
	
	@Override
	public byte[] array() {
		return null;
	}

	@Override
	public int arrayOffset() {
		return 0;
	}

	@Override
	public int capacity() {
		return capacity;
	}

	@Override
	public ByteBufferAdapter clear() {
		if( headChunk != null ) 
			headChunk.clear();
		currentChunk = headChunk;
		position = 0;
		limit = capacity();
		return this;
	}

	@Override
	public ByteBufferAdapter compact() {
		return this;
	}

	@Override
	public ByteBufferAdapter flip() {
		if( headChunk != null ) 
			headChunk.flip();
		currentChunk = headChunk;
		limit = position;
		position = 0;
		return this;
	}

	@Override
	public ByteBufferAdapter duplicate() {
		return this;
	}

	@Override
	public int limit() {
		return limit;
	}

	@Override
	public ByteBufferAdapter limit(int newLimit) {
		this.limit = newLimit;
		return this;
	}

	@Override
	public ByteBufferAdapter mark() {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public ByteOrder order() {
		return order;
	}

	@Override
	public ByteBufferAdapter order(ByteOrder byteOrder) {
		if( headChunk != null ) 
			headChunk.order(byteOrder);
		this.order = byteOrder;
		return this;
	}

	@Override
	public int position() {
		return position;
	}

	@Override
	public ByteBufferAdapter position(int newPosition) {
		position = newPosition;
		return this;
	}

	@Override
	public int remaining() {
		return limit - position;
	}

	@Override
	public ByteBufferAdapter reset() {
		return this;
	}

	@Override
	public ByteBufferAdapter rewind() {
		if( headChunk != null ) 
			headChunk.rewind();
		currentChunk = headChunk;
		position = 0;
		return this;
	}

	@Override
	public ByteBufferAdapter slice() {
		return this;
	}

	@Override
	public boolean hasArray() {
		return false;
	}

	@Override
	public boolean hasRemaining() {
		return true;
	}

	@Override
	public boolean isDirect() {
		return false;
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	public byte get() {
		return chunk(SIZE_BYTE, false).get();
	}

	@Override
	public ByteBufferAdapter get(byte[] dst, int dstOffset, int byteCount) {
		while( byteCount > 0 ) {			
			ByteBufferAdapter b = chunk(false);
			int count = Math.min(b.remaining(), byteCount);
			chunk(count, false);
			b.get(dst, dstOffset, count);
			byteCount -= count;
			dstOffset += count;
		}
		return this;
	}

	@Override
	public ByteBufferAdapter get(byte[] dst) {
		return get(dst, 0, dst.length);
	}

	@Override
	public byte get(int index) {
		Chunk chunk = chunkAt(index);
		return chunk.buf.get(index - chunk.offset);
	}

	@Override
	public char getChar() {
		return (char) getShort();
	}

	@Override
	public char getChar(int index) {
		return (char) getShort(index);
	}

	@Override
	public double getDouble() {
		return Double.longBitsToDouble(getLong());
	}

	@Override
	public double getDouble(int index) {
		return Double.longBitsToDouble(getLong(index));
	}

    @Override
    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    @Override
    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }


	@Override
	public int getInt() {
		return peekInt(position(SIZE_INT, false));
	}

	@Override
	public int getInt(int index) {
		return peekInt(index);
	}

	@Override
	public long getLong() {
		return peekLong(position(SIZE_LONG, false));
	}

	@Override
	public long getLong(int index) {
		return peekLong(index);
	}

	@Override
	public short getShort() {
		return peekShort(position(SIZE_SHORT, false));
	}

	@Override
	public short getShort(int index) {
		return peekShort(index);
	}

	@Override
	public ByteBufferAdapter put(byte b) {
		chunk(SIZE_BYTE, true).put(b);
		return this;
	}

	@Override
	public ByteBufferAdapter put(byte[] src, int srcOffset, int byteCount) {
		while( byteCount > 0 ) {
			ByteBufferAdapter b = chunk(true);
			int count = Math.min(b.remaining(), byteCount);
			chunk(count, true);
			b.put(src, srcOffset, count);
			byteCount -= count;
			srcOffset += count;
		}
		return this;
	}

	@Override
	public ByteBufferAdapter put(byte[] src) {
		put(src, 0, src.length);
		return this;
	}

	@Override
	public ByteBufferAdapter put(ByteBuffer src) {
		for( int srem = src.remaining(); srem > 0; srem = src.remaining() ) {
			ByteBufferAdapter b = chunk(true);
			int drem = b.remaining();
			if( drem < srem ) {
				int limit = src.limit();
				src.limit(src.position() + drem);
				b.put(src);
				src.limit(limit);
				chunk(drem, true);
			} else {
				b.put(src);
				chunk(srem, true);
			}
		}
		return this;
	}

	@Override
	public ByteBufferAdapter put(ByteBufferAdapter src) {
		for( int srem = src.remaining(); srem > 0; srem = src.remaining() ) {
			ByteBufferAdapter b = chunk(true);
			int drem = b.remaining();
			if( drem < srem ) {
				int limit = src.limit();
				src.limit(src.position() + drem);
				b.put(src);
				src.limit(limit);
				chunk(drem, true);
			} else {
				b.put(src);
				chunk(srem, true);
			}
		}
		return this;
	}

	@Override
	public ByteBufferAdapter put(int index, byte bt) {
		Chunk chunk = chunkAt(index);
		chunk.buf.put(index - chunk.offset, bt);
		return this;
	}

	@Override
	public ByteBufferAdapter putChar(char value) {
		pokeShort(position, (short) value);
		chunk(SIZE_SHORT, true);
		return this;
	}

	@Override
	public ByteBufferAdapter putChar(int index, char value) {
		pokeShort(index, (short) value);
		return this;
	}

	@Override
	public ByteBufferAdapter putDouble(double value) {
		return putLong(Double.doubleToRawLongBits(value));
	}

	@Override
	public ByteBufferAdapter putDouble(int index, double value) {
		return putLong(index, Double.doubleToRawLongBits(value));
	}

	@Override
	public ByteBufferAdapter putFloat(float value) {
		return putInt(Float.floatToRawIntBits(value));
	}

	@Override
	public ByteBufferAdapter putFloat(int index, float value) {
		return putInt(Float.floatToRawIntBits(value));
	}

	@Override
	public ByteBufferAdapter putInt(int index, int value) {
		pokeInt(index, value);
		return this;
	}

	@Override
	public ByteBufferAdapter putInt(int value) {
		pokeInt(position, value);
		chunk(SIZE_INT, true);
		return this;
	}

	@Override
	public ByteBufferAdapter putLong(int index, long value) {
		pokeLong(index, value);
		return this;
	}

	@Override
	public ByteBufferAdapter putLong(long value) {
		pokeLong(position, value);
		chunk(SIZE_INT, true);
		return this;
	}

	@Override
	public ByteBufferAdapter putShort(int index, short value) {
		pokeShort(index, value);
		return this;
	}

	@Override
	public ByteBufferAdapter putShort(short value) {
		pokeShort(position, value);
		chunk(SIZE_SHORT, true);
		return this;
	}

	@Override
	public ByteBuffer buffer() {
		return null;
	}

	@Override
	public int read(ReadableByteChannel channel) throws IOException {
		int bytes = 0;
		for( ;; ) {
			ByteBufferAdapter chunk = chunk(true);
			int b = chunk.read(channel);
			if( b < 0 ) {
				break;
			}
			bytes += b;
			position += b;
		}
		return bytes;		
	}

	@Override
	public int write(WritableByteChannel channel) throws IOException {
		int bytes = 0;
		while( position < limit ) {
			ByteBufferAdapter chunk = chunk(false);
			int b = chunk.write(channel);
			position += b;
			bytes += b;
		}
		return bytes;
	}
	
	@Override
	public boolean release() {
		boolean ret = headChunk == null ? false : headChunk.release();
		headChunk = null;
		currentChunk = null;
		return ret;
	}
	
	@Override
	public boolean free() {
		boolean ret = headChunk == null ? false : headChunk.free();
		headChunk = null;
		currentChunk = null;
		return ret;
	}
	
	@Override
	public boolean isPoolable() {
		return false;
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + 
				"[pos="+position+",lim="+limit+",chunks=("+headChunk+")]";
	}
	
	@Override
	public CheckSum checksum(CheckSum checksum) {
		if( headChunk != null )
			headChunk.checksum(checksum);
		return checksum;
	}
	
	
	// ===========================================================
	// Utils
	// ===========================================================
	
	private void grow(int byAtLeast) {
		int chunkSize = Math.max(this.chunkSize, byAtLeast);
		add(ByteBufferAdapter.obtain(chunkSize));
		currentChunk.buf.position(0);
		position -= chunkSize;
	}

	private ByteBufferAdapter chunk( boolean grow ) {
		return chunk( 0, grow );
	}
	
	private ByteBufferAdapter chunk( int space, boolean grow ) {
		if( currentChunk == null ) {
			tryGrow(space, grow);
		}
		
		int remaining = currentChunk.buf.remaining();
		while( remaining == 0  || remaining < space ) {
			if( currentChunk.next != null ) {
				currentChunk = currentChunk.next;
			} else {
				tryGrow(space, grow);
			}
			remaining = currentChunk.buf.remaining();
		}
		position+=space;
		return currentChunk.buf;
	}

	private void tryGrow(int space, boolean grow) {
		if( grow ) {
			grow(space);
		} else {
			throw new BufferOverflowException();		
		}
	}
	
	private int position( int space, boolean grow ) {
		int pos = position;
		chunk(space, grow);
		return pos;
	}
	
	private Chunk chunkAt( int position ) {
		Chunk chunk = currentChunk.chunkWith(position);
		if( chunk == null ) {
			throw new BufferOverflowException(); // ??
		}
		return chunk;
	}
	
	private final int peekInt(int offset) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (((get(offset++) & 0xff) << 24) |
                    ((get(offset++) & 0xff) << 16) |
                    ((get(offset++) & 0xff) <<  8) |
                    ((get(offset  ) & 0xff) <<  0));
        } else {
            return (((get(offset++) & 0xff) <<  0) |
                    ((get(offset++) & 0xff) <<  8) |
                    ((get(offset++) & 0xff) << 16) |
                    ((get(offset  ) & 0xff) << 24));
        }
    }

    private final long peekLong(int offset) {
        if (order == ByteOrder.BIG_ENDIAN) {
            int h = ((get(offset++) & 0xff) << 24) |
                    ((get(offset++) & 0xff) << 16) |
                    ((get(offset++) & 0xff) <<  8) |
                    ((get(offset++) & 0xff) <<  0);
            int l = ((get(offset++) & 0xff) << 24) |
                    ((get(offset++) & 0xff) << 16) |
                    ((get(offset++) & 0xff) <<  8) |
                    ((get(offset  ) & 0xff) <<  0);
            return (((long) h) << 32L) | ((long) l) & 0xffffffffL;
        } else {
            int l = ((get(offset++) & 0xff) <<  0) |
                    ((get(offset++) & 0xff) <<  8) |
                    ((get(offset++) & 0xff) << 16) |
                    ((get(offset++) & 0xff) << 24);
            int h = ((get(offset++) & 0xff) <<  0) |
                    ((get(offset++) & 0xff) <<  8) |
                    ((get(offset++) & 0xff) << 16) |
                    ((get(offset  ) & 0xff) << 24);
            return (((long) h) << 32L) | ((long) l) & 0xffffffffL;
        }
    }

    private final short peekShort(int offset) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (short) ((get(offset) << 8) | (get(offset + 1) & 0xff));
        } else {
            return (short) ((get(offset + 1) << 8) | (get(offset) & 0xff));
        }
    }
    
    private final void pokeInt(int offset, int value) {
        if (order == ByteOrder.BIG_ENDIAN) {
            put(offset++, (byte) ((value >> 24) & 0xff));
            put(offset++, (byte) ((value >> 16) & 0xff));
            put(offset++, (byte) ((value >>  8) & 0xff));
            put(offset, (byte) ((value >>  0) & 0xff));
        } else {
            put(offset++, (byte) ((value >>  0) & 0xff));
            put(offset++, (byte) ((value >>  8) & 0xff));
            put(offset++, (byte) ((value >> 16) & 0xff));
            put(offset, (byte) ((value >> 24) & 0xff));
        }
    }

    private final void pokeLong(int offset, long value) {
        if (order == ByteOrder.BIG_ENDIAN) {
            int i = (int) (value >> 32);
            put(offset++, (byte) ((i >> 24) & 0xff));
            put(offset++, (byte) ((i >> 16) & 0xff));
            put(offset++, (byte) ((i >>  8) & 0xff));
            put(offset++, (byte) ((i >>  0) & 0xff));
            i = (int) value;
            put(offset++, (byte) ((i >> 24) & 0xff));
            put(offset++, (byte) ((i >> 16) & 0xff));
            put(offset++, (byte) ((i >>  8) & 0xff));
            put(offset, (byte) ((i >>  0) & 0xff));
        } else {
            int i = (int) value;
            put(offset++, (byte) ((i >>  0) & 0xff));
            put(offset++, (byte) ((i >>  8) & 0xff));
            put(offset++, (byte) ((i >> 16) & 0xff));
            put(offset++, (byte) ((i >> 24) & 0xff));
            i = (int) (value >> 32);
            put(offset++, (byte) ((i >>  0) & 0xff));
            put(offset++, (byte) ((i >>  8) & 0xff));
            put(offset++, (byte) ((i >> 16) & 0xff));
            put(offset, (byte) ((i >> 24) & 0xff));
        }
    }

    private final void pokeShort(int offset, short value) {
        if (order == ByteOrder.BIG_ENDIAN) {
            put(offset++, (byte) ((value >> 8) & 0xff));
            put(offset, (byte) ((value >> 0) & 0xff));
        } else {
            put(offset++, (byte) ((value >> 0) & 0xff));
            put(offset, (byte) ((value >> 8) & 0xff));
        }
    }

    private static final class Chunk {
    	final ByteBufferAdapter buf;
    	Chunk prev;
    	Chunk next;
    	int offset = -1;
    	
    	public Chunk(ByteBufferAdapter adapter) {
    		this.buf = adapter;
    		// make sure to advance the position
    		// as if we were writing the data
    		buf.position(buf.limit());
    	}
    	
    	public void checksum( CheckSum checksum ) {
    		buf.checksum(checksum);
    		if( next != null )
    			next.checksum(checksum);
    	}

		public <T> T visit(Visitor<T> visitor, T t ) {
			t = visitor.visit(buf, t);
			if( next != null )
				t = next.visit(visitor, t);
			return t;
		}

		public Chunk tail() {
			return next == null ? this : next.tail();
		}

		public int capacity() {
			return length() + (next == null ? 0 : next.capacity());
		}
    	
    	public int index() {
    		if( prev == null ) {
    			return 0;
    		} else {
    			return prev.index() + 1;
    		}
    	}
    	
    	public Chunk append( Chunk chunk ) {
    		if( next == null ) {
    			next = chunk;
    			chunk.prev = this;
    			
    			// when we add a buffer after this we
    			// assume that this has all of the data
    			// its going to get
    			buf.limit(buf.position());
    			
    			return chunk;
    		} else {
    			return next.append(chunk);
    		}
    	}
    	
    	public Chunk prepend( Chunk chunk ) {
    		if( prev == null ) {
    			prev = chunk;
    			chunk.next = this;
    			chunk.buf.limit(chunk.buf.position());
    			chunk.recalcOffset();
    			return chunk;
    		} else {
    			return prev.prepend(chunk);
    		}
    	}
    	
    	@SuppressWarnings("unused")
		public Chunk insert( Chunk chunk, int index ) {
    		int myIndex = index();
    		if( myIndex == index ) {
    			prev = chunk;
    			chunk.prev = prev;
    			chunk.next = this;
    			// recalc next offsets
    			chunk.recalcOffset();
    			return chunk;
    		} else if( myIndex < index ) {
    			if( next != null ) {
    				return next.insert(chunk, myIndex);
    			} else {
    				return append(chunk);
    			}
    		} else {
    			if( prev != null ) {
    				return prev.insert(chunk, myIndex);
    			} else {
    				return prepend(chunk);
    			}
    		}
    	}
    	
    	public void recalcOffset() {
    		offset = -1;
    		offset();
    		if( next != null ) {
    			next.recalcOffset();
    		}
    	}
    	
    	public int offset() {
    		if( offset == -1 ) {
    			if( prev != null ) {
    				offset = prev.offset() + prev.length();
    			} else {
    				offset = 0;
    			}
    		}
    		return offset;
    	}
    	
    	public int length() {
    		return buf.limit();
    	}
    	
    	public void clear() {
    		buf.clear();
    		if( next != null )
    			next.clear();
    	}
    	
    	public void flip() {
    		buf.flip();
    		if( next != null )
    			next.flip();
    	}
    	
    	public void unflip() {
    		buf.position(buf.limit());
    		if( next != null )
    			next.unflip();
    	}
    	
    	public void order(ByteOrder byteOrder) {
    		buf.order(byteOrder);
    		if( next != null )
    			next.order(byteOrder);
    	}
    	
    	public void rewind() {
    		buf.rewind();
    		if( next != null )
    			next.rewind();
    	}
    	
    	public boolean release() {
    		boolean r = buf.release();
    		if( next != null )
    			r |= next.release();
    		return r;
    	}
    	
    	public boolean free() {
    		boolean r = buf.free();
    		if( next != null )
    			r |= next.free();
    		return r;
    	}
    	
    	public Chunk chunkWith(int position) {
    		int offset = offset();
    		int length = length();
    		if( position >= (offset + length) ) {
    			if( next != null ) {
    				return next.chunkWith(position);
    			} else {
    				return null;
    			}
    		} else if( position < offset ) {
    			if( prev != null ) {
    				return prev.chunkWith(position);
    			} else {
    				return null;
    			}
    		} else {
    			return this;
    		}
    	}
    	
    	@Override
    	public String toString() {
    		return getClass().getSimpleName() + 
    				"[off="+offset()+",len="+length()+",buf="+buf+"]," + next;
    	}
    }
}

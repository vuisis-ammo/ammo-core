package edu.vu.isis.ammo.util;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import android.os.Build;

public class NioByteBufferAdapter extends ByteBufferAdapter {

	private final ByteBuffer adaptee;

	public NioByteBufferAdapter(ByteBuffer adaptee) {
		this.adaptee = adaptee;
	}

	@Override
	public ByteBuffer buffer() {
		return adaptee;
	}

	public final byte[] array() {
		return adaptee.array();
	}

	public final int arrayOffset() {
		return adaptee.arrayOffset();
	}

	public CharBuffer asCharBuffer() {
		return adaptee.asCharBuffer();
	}

	public DoubleBuffer asDoubleBuffer() {
		return adaptee.asDoubleBuffer();
	}

	public FloatBuffer asFloatBuffer() {
		return adaptee.asFloatBuffer();
	}

	public IntBuffer asIntBuffer() {
		return adaptee.asIntBuffer();
	}

	public LongBuffer asLongBuffer() {
		return adaptee.asLongBuffer();
	}

	public ByteBuffer asReadOnlyBuffer() {
		return adaptee.asReadOnlyBuffer();
	}

	public ShortBuffer asShortBuffer() {
		return adaptee.asShortBuffer();
	}

	public final int capacity() {
		return adaptee.capacity();
	}

	public final ByteBufferAdapter clear() {
		adaptee.clear();
		return this;
	}

	public ByteBufferAdapter compact() {
		adaptee.compact();
		return this;
	}

	public ByteBufferAdapter duplicate() {
		return new NioByteBufferAdapter(adaptee.duplicate());
	}

	public final ByteBufferAdapter flip() {
		// the flip impl doesn't work in some cases on android
		int pos = adaptee.position();
		adaptee.clear();
		adaptee.position(0);
		adaptee.limit(pos);
		return this;
	}

	public byte get() {
		return adaptee.get();
	}

	public ByteBufferAdapter get(byte[] dst, int dstOffset, int byteCount) {
		adaptee.get(dst, dstOffset, byteCount);
		return this;
	}

	public ByteBufferAdapter get(byte[] dst) {
		adaptee.get(dst);
		return this;
	}

	public byte get(int index) {
		return adaptee.get(index);
	}

	public char getChar() {
		return adaptee.getChar();
	}

	public char getChar(int index) {
		return adaptee.getChar(index);
	}

	public double getDouble() {
		return adaptee.getDouble();
	}

	public double getDouble(int index) {
		return adaptee.getDouble(index);
	}

	public float getFloat() {
		return adaptee.getFloat();
	}

	public float getFloat(int index) {
		return adaptee.getFloat(index);
	}

	public int getInt() {
		return adaptee.getInt();
	}

	public int getInt(int index) {
		return adaptee.getInt(index);
	}

	public long getLong() {
		return adaptee.getLong();
	}

	public long getLong(int index) {
		return adaptee.getLong(index);
	}

	public short getShort() {
		return adaptee.getShort();
	}

	public short getShort(int index) {
		return adaptee.getShort(index);
	}

	public final boolean hasArray() {
		return adaptee.hasArray();
	}

	public final boolean hasRemaining() {
		return adaptee.hasRemaining();
	}

	public int hashCode() {
		return adaptee.hashCode();
	}

	public boolean isDirect() {
		return adaptee.isDirect();
	}

	public boolean isReadOnly() {
		return adaptee.isReadOnly();
	}

	public final int limit() {
		return adaptee.limit();
	}

	public final ByteBufferAdapter limit(int newLimit) {
		adaptee.limit(newLimit);
		return this;
	}

	public final ByteBufferAdapter mark() {
		adaptee.mark();
		return this;
	}

	public final ByteOrder order() {
		return adaptee.order();
	}

	public final ByteBufferAdapter order(ByteOrder byteOrder) {
		adaptee.order(byteOrder);
		return this;
	}

	public final int position() {
		return adaptee.position();
	}

	public final ByteBufferAdapter position(int newPosition) {
		adaptee.position(newPosition);
		return this;
	}

	public ByteBufferAdapter put(byte b) {
		adaptee.put(b);
		return this;
	}

	public ByteBufferAdapter put(byte[] src, int srcOffset, int byteCount) {
		adaptee.put(src, srcOffset, byteCount);
		return this;
	}

	public final ByteBufferAdapter put(byte[] src) {
		adaptee.put(src);
		return this;
	}

	@Override
	public ByteBufferAdapter put(ByteBuffer src) {
		// froyo and maybe honeycomb put(ByteBuffer) is crappy so I jacked this
		// code from newer androidsses
		if( Build.VERSION.SDK_INT < 14 ) {
	        int srcByteCount = src.remaining();
	        if (srcByteCount > remaining()) {
	            throw new BufferOverflowException();
	        } else if( srcByteCount <= 0 ) {
	        	return this;
	        }

	        Object srcObject = src.isDirect() ? src : src.array();
	        int srcOffset = src.position();
	        if (!src.isDirect()) {
	            srcOffset += src.arrayOffset();
	        }

	        ByteBuffer dst = adaptee;
	        Object dstObject = dst.isDirect() ? dst : adaptee.array();
	        int dstOffset = dst.position();
	        if (!dst.isDirect()) {
	            dstOffset += adaptee.arrayOffset();
	        }
	        
	        Memory.memmove(dstObject, dstOffset, srcObject, srcOffset, srcByteCount);
	        src.position(src.limit());
	        dst.position(dst.position() + srcByteCount);
		} else {
			adaptee.put(src);
		}
		return this;
	}

	public ByteBufferAdapter put(ByteBufferAdapter src) {
		if( src instanceof NioByteBufferAdapter ) {
			put(((NioByteBufferAdapter)src).adaptee);
		} else {
			throw new RuntimeException("Not done yet");
		}
		return this;
	}

	public ByteBufferAdapter put(int index, byte b) {
		adaptee.put(index, b);
		return this;
	}

	public ByteBufferAdapter putChar(char value) {
		adaptee.putChar(value);
		return this;
	}

	public ByteBufferAdapter putChar(int index, char value) {
		adaptee.putChar(index, value);
		return this;
	}

	public ByteBufferAdapter putDouble(double value) {
		adaptee.putDouble(value);
		return this;
	}

	public ByteBufferAdapter putDouble(int index, double value) {
		adaptee.putDouble(index, value);
		return this;
	}

	public ByteBufferAdapter putFloat(float value) {
		adaptee.putFloat(value);
		return this;
	}

	public ByteBufferAdapter putFloat(int index, float value) {
		adaptee.putFloat(index, value);
		return this;
	}

	public ByteBufferAdapter putInt(int index, int value) {
		adaptee.putInt(index, value);
		return this;
	}

	public ByteBufferAdapter putInt(int value) {
		adaptee.putInt(value);
		return this;
	}

	public ByteBufferAdapter putLong(int index, long value) {
		adaptee.putLong(index, value);
		return this;
	}

	public ByteBufferAdapter putLong(long value) {
		adaptee.putLong(value);
		return this;
	}

	public ByteBufferAdapter putShort(int index, short value) {
		adaptee.putShort(index, value);
		return this;
	}

	public ByteBufferAdapter putShort(short value) {
		adaptee.putShort(value);
		return this;
	}

	public final int remaining() {
		return adaptee.remaining();
	}

	public final ByteBufferAdapter reset() {
		adaptee.reset();
		return this;
	}

	public final ByteBufferAdapter rewind() {
		adaptee.rewind();
		return this;
	}

	public ByteBufferAdapter slice() {
		return new NioByteBufferAdapter(adaptee.slice());
	}

	@Override
	public int read(ReadableByteChannel channel) throws IOException {
		return channel.read(adaptee);
	}

	@Override
	public int write(WritableByteChannel channel) throws IOException {
		return channel.write(adaptee);
	}

	public String toString() {
		return adaptee.toString();
	}
}

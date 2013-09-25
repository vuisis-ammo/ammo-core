package edu.vu.isis.ammo.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import android.os.MemoryFile;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;

/**
 * This is meant to be an adapter for android's {@link MemoryFile}.
 * We may want to implement this in case there is a need to pass
 * a {@link ByteBufferAdapter} across process boundaries.  The only
 * case where we might want to pass buffers across processes is with
 * the {@link Encoding#CUSTOM} type.  Right now I'm pretty sure  
 * nobody has written a custom encoding type and, as far as I can tell,
 * support for custom encodings is not implemented fully. 
 * 
 * @author mriley
 */
public class AshMemByteBufferAdapter extends ByteBufferAdapter {

	@Override
	public boolean hasBuffer() {
		return false;
	}
	
	@Override
	public byte[] array() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int arrayOffset() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int capacity() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ByteBufferAdapter clear() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter compact() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter flip() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter duplicate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int limit() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ByteBufferAdapter limit(int newLimit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter mark() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteOrder order() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter order(ByteOrder byteOrder) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int position() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ByteBufferAdapter position(int newPosition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int remaining() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ByteBufferAdapter reset() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter rewind() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter slice() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasArray() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasRemaining() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isDirect() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isReadOnly() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public byte get() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ByteBufferAdapter get(byte[] dst, int dstOffset, int byteCount) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter get(byte[] dst) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte get(int index) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public char getChar() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public char getChar(int index) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getDouble() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getDouble(int index) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat(int index) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt(int index) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong(int index) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public short getShort() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public short getShort(int index) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ByteBufferAdapter put(byte b) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter put(byte[] src, int srcOffset, int byteCount) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter put(byte[] src) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter put(ByteBuffer src) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter put(ByteBufferAdapter src) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter put(int index, byte b) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putChar(char value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putChar(int index, char value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putDouble(double value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putDouble(int index, double value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putFloat(float value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putFloat(int index, float value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putInt(int index, int value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putInt(int value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putLong(int index, long value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putLong(long value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putShort(int index, short value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBufferAdapter putShort(short value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ByteBuffer buffer() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public CheckSum checksum(CheckSum checksum) {
		return checksum;
	}

	@Override
	public int read(ReadableByteChannel channel) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int write(WritableByteChannel channel) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

}

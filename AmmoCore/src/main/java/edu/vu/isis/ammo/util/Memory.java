package edu.vu.isis.ammo.util;

/**
 * Jacked from android > honeycomb to work on froYOOOOOO
 * 
 * @author mriley
 */
public final class Memory {
	
	static {
		System.loadLibrary( "ammocore" );
		nativeInit();
	}
	
	private static native void nativeInit();
	
	/**
	 * Copies 'byteCount' bytes from the source to the destination. The objects are either
	 * instances of DirectByteBuffer or byte[]. The offsets in the byte[] case must include
	 * the Buffer.arrayOffset if the array came from a Buffer.array call. We could make this
	 * private and provide the four type-safe variants, but then ByteBuffer.put(ByteBuffer)
	 * would need to work out which to call based on whether the source and destination buffers
	 * are direct or not.
	 */
	public static native void memmove(Object dstObject, int dstOffset, Object srcObject, int srcOffset, long byteCount);
}


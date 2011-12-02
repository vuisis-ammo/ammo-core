package edu.vu.isis.ammo.util;

import java.lang.reflect.Array;

/**
 * Helper methods when you don't have java 1.6 working correctly.
 *
 */
public class Arrays6 {
	public static <U> U[] copyOfRange(U[] original, int from, int to) {
	    @SuppressWarnings("unchecked")
		Class<? extends U[]> newType = (Class<? extends U[]>) original.getClass();
	    int newLength = to - from;
	    if (newLength < 0) {
	        throw new IllegalArgumentException(from + " > " + to);
	    }
	    @SuppressWarnings("unchecked")
		U[] copy = ((Object) newType == (Object)Object[].class)
	        ? (U[]) new Object[newLength]
	        : (U[]) Array.newInstance(newType.getComponentType(), newLength);
	    System.arraycopy(original, from, copy, 0,
	                     Math.min(original.length - from, newLength));
	    return copy;
	}
	
	public static byte[] copyOfRange(byte[] original, int from, int to) {
		int newLength = to - from;
	    if (newLength < 0) {
	        throw new IllegalArgumentException(from + " > " + to);
	    }
		byte[] copy = new byte[newLength];
	    System.arraycopy(original, from, copy, 0,
	                     Math.min(original.length - from, newLength));
	    return copy;
	}
	public static byte[] copyOf(byte[] original, int to) {
		return Arrays6.copyOfRange(original, 0, to);
	}
}

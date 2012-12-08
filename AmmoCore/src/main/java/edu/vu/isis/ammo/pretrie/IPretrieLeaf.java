package edu.vu.isis.ammo.pretrie;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public interface IPretrieLeaf<V> {

	V getValue();

	V putValue(final V value);

	/**
	 * Prefix wraps java byte arrays (byte[]) to allow byte arrays to be used as
	 * keys/prefix in the IPretrieve.
	 * 
	 * This class also allows the triple of array, offset and length to be
	 * carried around as a single object.
	 */
	public static final class Prefix {

		private final byte[] array;
		private final int offset;
		private final int length;

		/**
		 * Create an instance of this class that wraps the given array. This
		 * class does not make a copy of the array, it just saves the reference.
		 */
		public Prefix(byte[] array, int offset, int length) {
			this.array = array;
			this.offset = offset;
			this.length = length;
		}

		public Prefix(byte[] array) {
			this(array, 0, array.length);
		}

		/**
		 * Value equality for byte arrays.
		 */
		public boolean equals(Object other) {
			if (other instanceof Prefix) {
				Prefix ob = (Prefix) other;
				return Prefix.equals(array, offset, length, ob.array,
						ob.offset, ob.length);
			}
			return false;
		}

		/**
		  */
		public int hashCode() {

			byte[] larray = array;

			int hash = length;
			for (int i = 0; i < length; i++) {
				hash += larray[i + offset];
			}
			return hash;
		}

		public final byte[] getArray() {
			return array;
		}

		public final int getOffset() {
			return offset;
		}

		public final int getLength() {
			return length;
		}

		/**
		 * Read this object from a stream of stored objects.
		 * 
		 * @param in
		 *            read this.
		 * 
		 * @exception IOException
		 *                thrown on error
		 */
		public Prefix readExternal(ObjectInput in) throws IOException {
			final int len = in.readInt();
			final int offset = 0;
			final byte[] array = new byte[len];

			in.readFully(array, 0, len);
			return new Prefix(array, offset, len);
		}

		/**
		 * Write the byte array out w/o compression
		 * 
		 * @param out
		 *            write bytes here.
		 * 
		 * @exception IOException
		 *                thrown on error
		 */
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(length);
			out.write(array, offset, length);
		}

		/**
		 * Compare two byte arrays using value equality. Two byte arrays are
		 * equal if their length is identical and their contents are identical.
		 * 
		 * @param a
		 * @param aOffset
		 * @param aLength
		 * @param b
		 * @param bOffset
		 * @param bLength
		 * @return
		 */
		private static boolean equals(byte[] a, int aOffset, int aLength,
				byte[] b, int bOffset, int bLength) {

			if (aLength != bLength)
				return false;

			for (int i = 0; i < aLength; i++) {
				if (a[i + aOffset] != b[i + bOffset])
					return false;
			}
			return true;
		}

		/**
		 * Get the byte currently under the offset.
		 * 
		 * @return
		 */
		public int getCurrentByte() {
			return this.array[this.offset];
		}

		public Prefix increment() {
			return new Prefix(this.array, this.offset + 1, this.length);
		}

		/**
		 * return the offset of the first non-matching byte.
		 * 
		 * @param that
		 * @return <0 indicates a complete match
		 */
		public int matchOffset(final Prefix that) {
			int offset = 0;
			int thisOffset = this.offset;
			int thatOffset = that.offset;
			int limit = (this.length < that.length) ? this.length : that.length;
			for (; offset < limit; offset++, thisOffset++, thatOffset++) {
				if (this.array[thisOffset] == that.array[thatOffset]) {
					continue;
				}
				return offset;
			}
			return -1;
		}

		/**
		 * Trim the end off the prefix by shortening the length.
		 * 
		 * @param offset
		 * @return
		 */
		public Prefix trimOffEnd(int offset) {
			return new Prefix(this.array, this.offset, this.length - offset);
		}

		/**
		 * Trim the front of the prefix by advancing the offset and shortening
		 * the length.
		 * 
		 * @param offset
		 * @return
		 */
		public Prefix trimOffStart(int offset) {
			return new Prefix(this.array, this.offset + offset, this.length
					- offset);
		}
	}

}

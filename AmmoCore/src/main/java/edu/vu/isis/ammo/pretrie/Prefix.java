package edu.vu.isis.ammo.pretrie;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.pretrie.IPretrie.IKey;

public class Prefix implements IPretrie.IKey {
	private static final Logger logger = LoggerFactory.getLogger(Prefix.class);
	private static final boolean LOG_ON = false;

	private final byte[] array;
	private final int offset;
	private final int length;

	/**
	 * Create an instance of this class that wraps the given array. This class
	 * does not make a copy of the array, it just saves the reference.
	 */
	private Prefix(final byte[] array, final int offset, final int length) {
		if (LOG_ON)
			logger.trace("consructor {} {} {}", array.length, offset, length);
		if (array.length < (offset + length)) {
			logger.error("array too small: [{}] < {} + {}", array.length,
					offset, length);
			throw new IllegalArgumentException("endpoint exceeds parameters");
		}
		if (array.length < offset) {
			logger.error("offset too small: {}", offset);
			throw new IllegalArgumentException("offset too small");
		}
		if (offset < 0) {
			logger.error("offset too small: {}", offset);
			throw new IllegalArgumentException("offset too small");
		}
		if (length < 0) {
			logger.error("offset too small: {}", offset);
			throw new IllegalArgumentException("offset too small");
		}
		this.array = array;
		this.offset = offset;
		this.length = length;
	}

	private Prefix(final byte[] array) {
		this(array, 0, array.length);
	}

	public static Prefix newInstance(final byte[] array) {
		if (array == null) {
			return new Prefix(new byte[0], 0, 0);
		}
		return new Prefix(array, 0, array.length);
	}

	public static Prefix newInstance(final String key) {
		try {
			return new Prefix(key.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException ex) {
			logger.error("unsupported encoding", ex);
		}
		return null;
	}

	/**
	 * Value equality for byte arrays.
	 */
	public boolean equals(Object other) {
		if (LOG_ON)
			logger.trace("equals");
		if (!(other instanceof Prefix)) {
			return false;
		}
		final Prefix that = (Prefix) other;

		if (this.length != that.length)
			return false;

		int endLength = this.offset + this.length;
		for (int ix = 0; ix < endLength; ix++) {
			if (this.array[ix] != that.array[ix])
				return false;
		}
		return true;
	}

	/**
	*/
	public int hashCode() {
		if (LOG_ON)
			logger.trace("hash code");
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
		logger.trace("read external");
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
	 * Get the byte currently under the offset. If there are no bytes in the
	 * prefix then the current byte is undefined. As the array is of bytes any
	 * integer which is not a valid byte may be returned, typically this will be
	 * -1;
	 * 
	 * @return
	 */
	public int getCurrentByte() {
		if (LOG_ON)
			logger.trace("get current byte {} {}", this.array.length,
					this.offset);
		if (this.length < 1)
			return -1;
		return this.array[this.offset];
	}

	public Prefix increment() {
		return new Prefix(this.array, this.offset + 1, this.length);
	}

	/**
	 * Get the offset of the end of the prefix.
	 * 
	 * @return
	 */
	public int getEndOffset() {
		return this.offset + this.length;
	}

	/**
	 * Determine if this is a proper prefix of key.
	 * 
	 * @param key
	 * @return
	 */
	public boolean isPrefixOf(Prefix key) throws IllegalArgumentException {
		if (this.offset != key.offset) {
			throw new IllegalArgumentException("offsets do not match");
		}
		if (key.length < this.length) {
			return false;
		}
		int sharedOffset = this.offset;
		for (int sharedLength = 0; sharedLength < this.length; sharedLength++, sharedOffset++) {
			if (this.array[sharedOffset] != key.array[sharedOffset]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Trim the end off the prefix by shortening the length.
	 * 
	 * @param offset
	 * @return
	 */
	public Prefix trimLength(int endOffset) {
		return new Prefix(this.array, this.offset, endOffset - this.offset);
	}

	/**
	 * Trim the front of the prefix by advancing the offset and shortening the
	 * length. Returns null if the new offset is beyond the total length
	 * 
	 * @param offset
	 * @return
	 */
	public Prefix trimOffset(int endOffset) {
		if (this.offset + this.length <= endOffset) {
			return null;
		}
		return new Prefix(this.array, endOffset, this.length
				- (endOffset - this.offset));
	}

	@Override
	public String toString() {
		return this.toStringBuilder(new StringBuilder()).toString();
	}

	public StringBuilder toStringBuilder(final StringBuilder sb) {
		int ix = 0;
		final int length = this.array.length;
		sb.append('[');
		for (; ix < length; ix++) {
			if (this.offset <= ix)
				break;
			sb.append(' ');
			sb.append(this.array[ix]);
		}
		sb.append(" (");
		final int last = this.offset + this.length;
		for (; ix < length; ix++) {
			if (last <= ix)
				break;
			sb.append(' ');
			sb.append(this.array[ix]);
		}
		sb.append(" )");
		for (; ix < length; ix++) {
			sb.append(' ');
			sb.append(this.array[ix]);
		}
		sb.append(" ]");
		return sb;
	}

	/**
	 * Check to see if that Prefix is a prefix of this Prefix. If it is the
	 * offset of the first non-matching byte.
	 * 
	 * @param that
	 * @return <0 indicates a complete match
	 * @throws Exception
	 */
	public Match match(final Prefix that) throws IllegalArgumentException {
		if (this == that) {
			return new Match(Type.A_EQ_B, this.offset + this.length - 1);
		}
		if (this.offset != that.offset) {
			logger.error("offsets do not match {} {}", this, that);
			throw new IllegalArgumentException("offsets do not match");
		}
		if (this.array[0] != that.array[0]) {
			return new Match(Type.A_NOT_B, 0);
		}
		int sharedOffset = this.offset;
		if (this.length == that.length) {
			for (int sharedLength = 0; sharedLength < this.length - 2; sharedLength++, sharedOffset++) {
				if (this.array[sharedOffset] != that.array[sharedOffset]) {
					return new Match(Type.A_B, sharedOffset);
				}
			}
			sharedOffset++;
			if (this.array[sharedOffset] == that.array[sharedOffset]) {
				return new Match(Type.A_EQ_B, sharedOffset);
			}
			return new Match(Type.A_B, sharedOffset);
		}
		final Type candidateType;
		final Prefix a;
		final Prefix b;
		if (this.length < that.length) {
			candidateType = Type.A_LT_B;
			a = this;
			b = that;
		} else {
			candidateType = Type.A_GT_B;
			a = that;
			b = this;
		}
		for (int sharedLength = 0; sharedLength < a.length; sharedLength++, sharedOffset++) {
			if (a.array[sharedOffset] != b.array[sharedOffset]) {
				return new Match(Type.A_B, sharedOffset);
			}
		}
		return new Match(candidateType, sharedOffset);
	}

	/**
	 * <p>
	 * The relationships of the two prefixes A & B </ul>
	 */
	public enum Type {
		/** prefix is not set */
		NULL("null"),
		/** A equals B (they are proper prefixes of each other) : replace value */
		A_EQ_B("A = B"),
		/** A prefixes B before last byte */
		A_LT_B("A < B"),
		/** B prefixes A before last byte */
		A_GT_B("A > B"),
		/** A and B share a common prefix before last byte */
		A_B("A >< B"),
		/** A and B do not share a common prefix */
		A_NOT_B("A ! B");

		final public String text;

		private Type(final String text) {
			this.text = text;
		}
	}

	public static class Match {
		public final Type type;
		public final int offset;

		public Match(final Type type, final int offset) {
			this.type = type;
			this.offset = offset;
		}

		public String toString() {
			return new StringBuilder().append(type).append(":")
					.append(this.offset).toString();
		}
	}

	@Override
	public int get() {
		return this.getCurrentByte();
	}

	@Override
	public int size() {
		return this.offset + this.length;
	}

	@Override
	public int compareItem(int ix, IKey obj) {
		if (!(obj instanceof Prefix))
			return -1;
		final Prefix that = (Prefix) obj;

		if (this.array[ix] == that.array[ix])
			return 0;
		return (this.array[ix] < that.array[ix]) ? -1 : 1;
	}

	@Override
	public byte[] asBytes() {
		return this.array;
	}

	public boolean isEmpty() {
		if (this.array.length < 1) return true;
		if (this.offset + this.length < 1) return true;
		return false;
	}
}
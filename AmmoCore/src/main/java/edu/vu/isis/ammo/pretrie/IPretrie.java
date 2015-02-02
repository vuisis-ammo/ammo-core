package edu.vu.isis.ammo.pretrie;

import java.io.UnsupportedEncodingException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an efficient tr
 * 
 */
public interface IPretrie<V> {
	public static final Logger logger = LoggerFactory.getLogger("util.iptrie");

	public void insert(final String prefix, final V value)
			throws UnsupportedEncodingException;

	/**
	 * Check if this key is a prefix for the supplied key.
	 * <dl>
	 * <dt>null</dt>
	 * <dd>this Trie is not a prefix to the key.</dd>
	 * <dt>this</dt>
	 * <dd>it is a prefix match to the key.</dd>
	 * </dl>
	 * 
	 * @param key
	 * @return the node having/being the longest matching prefix
	 */
	public V longestPrefix(IKey key);

	/**
	 * Produce a list of all the values stored in the pretrie.
	 */
	public List<V> values();
	
	/**
	 * How many prefix values are in the set.
	 */
	public int size();

	public interface IKey {

		/** get the byte at the current location */
		public int get();

		/** how many bytes in the prefix/key */
		public int size();

		/**
		 * compare a specific byte in the two keys, by index.
		 */
		public int compareItem(int ix, IKey that);
		
		/** represent the key as an array of bytes */
		public byte[] asBytes();

	}
}

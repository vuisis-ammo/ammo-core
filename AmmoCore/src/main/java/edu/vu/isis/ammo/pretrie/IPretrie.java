/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


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

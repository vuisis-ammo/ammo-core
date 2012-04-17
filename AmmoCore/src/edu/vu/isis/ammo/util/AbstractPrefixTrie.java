/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This is a Trie which uses bits on bytes as potential branches.
 */
abstract public class AbstractPrefixTrie<V> implements IPrefixTrie<V> {
	private static final Logger logger = LoggerFactory.getLogger("util.aptrie");

	public void insert(String key, V value) {
		this.insert(new Node( new Key(key.getBytes()), value));
	}

	/**
	 * Check if this key is a prefix for the supplied key.
	 * null : this Trie is not a prefix to the key.
	 * this : it is a prefix match to the key.
	 * 
	 * @param key
	 * @return 
	 */
	public V longestPrefix(String key) {
		@SuppressWarnings("unchecked")
		final Node node = (Node) this.longestPrefix(new Key(key.getBytes()));
		if (node == null) {
			logger.error("no matching node {}", key);
			return null;
		}
		return node.value;
	}

	public class Key implements IPrefixTrie.IKey {
		final private byte[] k;
		private int position;
		private int mark;

		public Key(byte[] k) {
			this.k = k;
			this.position = 0;
		}
		

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append(new String(this.k));
			return sb.toString();
		}

		@Override
		public int get() {
			return (int) this.k[this.position];
		}

		@Override
		public int size() {
			return this.k.length;
		}

		@Override
		public void offset(int length) {
			this.position += length;
		}

		@Override
		public int compareItem(int ix, IKey that) {
			@SuppressWarnings("unchecked")
			final Key key = (Key) that;
			
			if (this.k[ix] == key.k[ix]) return 0;
			return (this.k[ix] < key.k[ix]) ? -1 : 1;
		}
		/**
		 * 
		 * @param frag
		 * @return the number of frag bytes matched.
		 */
		@Override
		public int match(IKey key) {
			@SuppressWarnings("unchecked")
			final Key that = (Key) key;
			return this.match(that.k);
		}
		@Override
		public int match(byte[] frag) {
			int kx = Math.min(frag.length, this.remaining());

			int ix = 0;
			int jx = this.position;
			for (; ix < kx; ++ix, ++jx) {
				if (frag[ix] != this.k[jx]) return ix;
			}
			return ix;
		}

		public int burst(int length) {
			this.position += length;
			return (int) this.k[this.position];
		}

		public byte[] extract() {
			final byte[] result = new byte[this.position - this.mark];
			int jx = this.mark;
			for (int ix=0; jx < this.position; ++ix, ++jx) {
				result[ix] = this.k[jx];
			}
			return result;
		}

		public int remaining() {
			return this.k.length - this.position;
		}

		public void mark() {
			this.mark = this.position;
		}

		/**
		 * if this is lexicographically before that return -1
		 * if equal return 0
		 * otherwise return 1
		 */
		@Override
		public int compareTo(IKey key) {
			@SuppressWarnings("unchecked")
			final Key that = (Key) key;
			for (int ix=0; ix < Math.min(this.k.length, that.k.length); ++ix) {
				if (this.k[ix] == that.k[ix]) continue;
				return (this.k[ix] < that.k[ix]) ?  -1 : 1;
			}
			if (this.k.length == that.k.length) return 0;
			return (this.k.length < that.k.length) ?  -1 : 1;
		}

	}

	public class Node implements INode {
		final V value;
		final Key key;
		
		public Node( Key key, V value) {
			this.key = key;
			this.value = value;
		}
		@Override
		public int compareTo(INode node) {
			@SuppressWarnings("unchecked")
			final Node that = (Node) node;
			return this.key.compareTo(that.key);
		}
		
		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("key ").append(this.key);
			sb.append(this.value);
			return sb.toString();
		}
		
	}
	


}

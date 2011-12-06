package edu.vu.isis.ammo.util;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This is a Trie which uses bits on bytes as potential branches.
 */
public class AbstractPrefixTrie<V> {
	private static final Logger logger = LoggerFactory.getLogger("ammo-pretrie");

	public void insert(String key, V value) {
		this.insert(new Node( new Key(key.getBytes()), value));
	}
	protected void insert(Node node) {
		logger.error("insert not implmented");
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
		final Node node = this.longestPrefix(new Key(key.getBytes()));
		if (node == null) {
			logger.error("no matching node {}", key);
			return null;
		}
		return node.value;
	}

	protected Node longestPrefix(Key key) {
		logger.error("insert not implmented");
		return null;
	}
	
	public class Key implements Comparable<Key> {
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

		public int get() {
			return (int) this.k[this.position];
		}

		public int size() {
			return this.k.length;
		}

		public void offset(int length) {
			this.position += length;
		}

		public int compareItem(int ix, Key that) {
			if (this.k[ix] == that.k[ix]) return 0;
			return (this.k[ix] < that.k[ix]) ? -1 : 1;
		}
		/**
		 * 
		 * @param frag
		 * @return the number of frag bytes matched.
		 */
		public int match(Key that) {
			return this.match(that.k);
		}
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
		public int compareTo(Key that) {
			for (int ix=0; ix < Math.min(this.k.length, that.k.length); ++ix) {
				if (this.k[ix] == that.k[ix]) continue;
				return (this.k[ix] < that.k[ix]) ?  -1 : 1;
			}
			if (this.k.length == that.k.length) return 0;
			return (this.k.length < that.k.length) ?  -1 : 1;
		}
	}

	public V[] values() {
		return null;
	}


	public class Node implements Comparable<Node> {
		final V value;
		final Key key;
		public Node( Key key, V value) {
			this.key = key;
			this.value = value;
		}
		@Override
		public int compareTo(Node that) {
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

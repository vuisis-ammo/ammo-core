package edu.vu.isis.ammo.util;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This is a Trie which uses bits on bytes as potential branches.
 */
public interface IPrefixTrie<V> {
	public static final Logger logger = LoggerFactory.getLogger("ammo-pretrie");

	public void insert(INode node);

	/**
	 * Check if this key is a prefix for the supplied key.
	 * null : this Trie is not a prefix to the key.
	 * this : it is a prefix match to the key.
	 * 
	 * @param key
	 * @return 
	 */
	public INode longestPrefix(IKey key);
	
	public List<V> values();
	
	public interface INode extends Comparable<INode> {}
	
	public interface IKey extends Comparable<IKey> {
		
		public int get();

		public int size();

		public void offset(int length);

		public int compareItem(int ix, IKey that);
		/**
		 * 
		 * @param frag
		 * @return the number of frag bytes matched.
		 */
		public int match(IKey that);
		public int match(byte[] frag);

		public int burst(int length);

		public byte[] extract();

		public int remaining();

		public void mark();

		/**
		 * if this is lexicographically before that return -1
		 * if equal return 0
		 * otherwise return 1
		 */
		@Override
		public int compareTo(IKey that);
	}
}

package edu.vu.isis.ammo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This is a Trie which uses bits on bytes as potential branches.
 */
public class ATrie<V> {
	private static final Logger logger = LoggerFactory.getLogger("ammo-trie");
	
    private byte[] frag;
	private ATrie<V>[] burst;
	V value;
	
	@SuppressWarnings("unchecked")
	public ATrie(Key key, V value, ATrie<V>[] burst) {
		this.frag = key.extract();
		this.value = value;
		this.burst = (burst == null) ? new ATrie[256] : burst;
	}
	
	/**
	 * The root of the trie
	 */
	public ATrie() {
		this(null, null, null);
	}
	
	public void insert(String key, V value) {
		this.insert(new Key(key.getBytes()), value);
	}
	
	@SuppressWarnings("unchecked")
	public void insert(Key key, V value) {
		final int matchCnt = key.match(this.frag);
		if (matchCnt == key.remaining()) {
			logger.error("duplicate topic type {}", value);
			return;
		}
		if (matchCnt == this.frag.length) {
			key.offset( this.frag.length );
			final ATrie<V> child = this.burst[key.get()];
			key.mark();
			child.insert(key, value);
    		return;
		}
    	// split trie
		final byte[] parentFrag = Arrays6.copyOf(this.frag, matchCnt);
		final int childIx = this.frag[matchCnt];
		final byte[] childFrag = Arrays6.copyOfRange(this.frag, matchCnt, this.frag.length); 
		final ATrie<V> child = new ATrie<V>(null, this.value, this.burst);
		child.frag = childFrag;
		this.frag = parentFrag;
		this.value = value;
		this.burst =  new ATrie[256];
    	this.burst[childIx] = child;
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
		 return this.longestPrefix(new Key(key.getBytes()));
	 }
	 
    public V longestPrefix(Key key) {	
    	if (key.match(this.frag) < this.frag.length) // different keys
    		return null; 
    	
    	if (key.remaining() == this.frag.length) // identical keys
    		return this.value;
    	
    	// matching but continued
		key.offset( this.frag.length );
		final ATrie<V> child = this.burst[key.get()];
		key.mark();
		final V result = child.longestPrefix(key);
		if (result != null) return result;
    	return this.value;
	}
    
    public class Key {
    	final private byte[] k;
    	private int position;
    	private int mark;
    	
    	public Key(byte[] k) {
    		this.k = k;
    		this.position = 0;
    	}
    	
    	public int get() {
			return (int) this.k[this.position];
		}

		public void offset(int length) {
			this.position += length;
		}

		/**
    	 * 
    	 * @param frag
    	 * @return the number of frag bytes matched.
    	 */
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
    }

	public V[] values() {
		return null;
	}

}

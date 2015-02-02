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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a Trie which uses bits on bytes as potential branches.
 */
public class PrefixList<V> implements IPretrie<V> {
    private static final Logger logger = LoggerFactory.getLogger("util.plist");

    final private List<Node> nodes;
    final private Map<Key, Node> prefixMap;
    private boolean isDirty;

    public PrefixList() {
        this.nodes = new ArrayList<Node>(5);
        this.prefixMap = new HashMap<Key, Node>(5);
        this.isDirty = false;
    }

    @Override
    public void insert(final String key, final V value) {
        final Node node = new Node(new Key(key.getBytes()), value);
        this.nodes.add(node);
        this.isDirty = true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("prefix list [").append(this.nodes.size()).append("] \n");
        for (Node node : this.nodes) {
            sb.append(node).append("\n");
        }
        return sb.toString();
    }

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
     * @return
     */
    @SuppressWarnings("unchecked")
	@Override
    public V longestPrefix(IKey val) {
        final Key key = (Key) val;
        if (this.isDirty) {
            Collections.sort(this.nodes);
            this.prefixMap.clear();
        }
        if (this.prefixMap.containsKey(key)) {
            return this.prefixMap.get(key).value;
        }
        int bestScore = -1;
        int lastScore = -1;
        Node bestNode = null;
        for (Node node : this.nodes) {
            final Key currentKey = node.key;
            final int score = key.match(currentKey);
            if (score < lastScore)
                break;
            lastScore = score;
            if (score == currentKey.size()) {
                if (score > bestScore) {
                    bestScore = score;
                    bestNode = node;
                }
            }
            if (score == key.size())
                break;
        }
        logger.debug("match {} {}", key, bestNode);
        this.prefixMap.put(key, bestNode);
        if (bestNode == null) return null;
        return bestNode.value;
    }

    @Override
    public List<V> values() {
        final List<V> vals = new ArrayList<V>(this.nodes.size());
        for (Node node : this.nodes) {
            vals.add(node.value);
        }
        return vals;
    }

	public int size() {
		return this.nodes.size();
	}

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
	     * @return best matching node
	     */
	    public V longestPrefix(String key) {
	        final V value = this.longestPrefix(new Key(key.getBytes()));
	        if (value == null) {
	            logger.error("no matching node {}", key);
	            return null;
	        }
	        return value;
	    }

	    public class Key implements IPretrie.IKey {
	        final private byte[] key;
	        private int position;
	        private int mark;

	        public Key(byte[] key) {
	            this.key = key;
	            this.position = 0;
	        }

	        @Override
	        public String toString() {
	            final StringBuilder sb = new StringBuilder();
	            sb.append(new String(this.key));
	            return sb.toString();
	        }

	        @Override
	        public int get() {
	            return (int) this.key[this.position];
	        }

	        @Override
	        public int size() {
	            return this.key.length;
	        }

	        @Override
	        public int compareItem(int ix, IKey that) {
	            @SuppressWarnings("unchecked")
	            final Key key = (Key) that;

	            if (this.key[ix] == key.key[ix])
	                return 0;
	            return (this.key[ix] < key.key[ix]) ? -1 : 1;
	        }

	        /**
	         * @param key
	         * @return the number of frag bytes matched.
	         */
	        public int match(IKey key) {
	            @SuppressWarnings("unchecked")
	            final Key that = (Key) key;
	            return this.match(that.key);
	        }

	        public int match(byte[] frag) {
	            int kx = Math.min(frag.length, this.remaining());

	            int ix = 0;
	            int jx = this.position;
	            for (; ix < kx; ++ix, ++jx) {
	                if (frag[ix] != this.key[jx])
	                    return ix;
	            }
	            return ix;
	        }

	        public int burst(int length) {
	            this.position += length;
	            return (int) this.key[this.position];
	        }

	        public byte[] extract() {
	            final byte[] result = new byte[this.position - this.mark];
	            int jx = this.mark;
	            for (int ix = 0; jx < this.position; ++ix, ++jx) {
	                result[ix] = this.key[jx];
	            }
	            return result;
	        }

	        public int remaining() {
	            return this.key.length - this.position;
	        }

	        public void mark() {
	            this.mark = this.position;
	        }

	        /**
	         * if this is lexicographically before that return -1 if equal return 0
	         * otherwise return 1
	         */
	        public int compareTo(IKey key) {
	            @SuppressWarnings("unchecked")
	            final Key that = (Key) key;
	            for (int ix = 0; ix < Math.min(this.key.length, that.key.length); ++ix) {
	                if (this.key[ix] == that.key[ix])
	                    continue;
	                return (this.key[ix] < that.key[ix]) ? -1 : 1;
	            }
	            if (this.key.length == that.key.length)
	                return 0;
	            return (this.key.length < that.key.length) ? -1 : 1;
	        }
	        
	        public byte[] asBytes() {
	        	return this.key;
	        }

	    }

	    @SuppressWarnings("rawtypes")
		public class Node implements Comparable {
	        final V value;
	        final Key key;

	        public Node(Key key, V value) {
	            this.key = key;
	            this.value = value;
	        }

	        @Override
	        public int compareTo(Object node) {
	        	if (!(node instanceof PrefixList.Node)) {
	        		return 0;
	        	}
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


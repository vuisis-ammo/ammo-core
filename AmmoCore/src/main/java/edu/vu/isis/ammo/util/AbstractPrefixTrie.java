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

/**
 * This is a Trie which uses bits on bytes as potential branches.
 */
abstract public class AbstractPrefixTrie<V> implements IPrefixTrie<V> {
    private static final Logger logger = LoggerFactory.getLogger("util.aptrie");

    public void insert(String key, V value) {
        this.insert(new Node(new Key(key.getBytes()), value));
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
        @SuppressWarnings("unchecked")
        final Node node = (Node) this.longestPrefix(new Key(key.getBytes()));
        if (node == null) {
            logger.error("no matching node {}", key);
            return null;
        }
        return node.value;
    }

    public class Key implements IPrefixTrie.IKey {
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
        public void offset(int length) {
            this.position += length;
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
        @Override
        public int match(IKey key) {
            @SuppressWarnings("unchecked")
            final Key that = (Key) key;
            return this.match(that.key);
        }

        @Override
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
        @Override
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

    }

    public class Node implements INode {
        final V value;
        final Key key;

        public Node(Key key, V value) {
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

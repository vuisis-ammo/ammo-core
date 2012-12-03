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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This is a Trie which uses bits on bytes as potential branches.
 */
public interface IPrefixTrie<V> {
    public static final Logger logger = LoggerFactory.getLogger("util.iptrie");

    public void insert(INode node);

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
    public INode longestPrefix(IKey key);

    public List<V> values();

    public interface INode extends Comparable<INode> {
    }

    public interface IKey extends Comparable<IKey> {

        public int get();

        public int size();

        public void offset(int length);

        public int compareItem(int ix, IKey that);

        /**
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
         * <p>
         * <ul>
         * <li>if equal return 0
         * <li>otherwise return 1
         * </ul>
         */
        @Override
        public int compareTo(IKey that);
    }
}
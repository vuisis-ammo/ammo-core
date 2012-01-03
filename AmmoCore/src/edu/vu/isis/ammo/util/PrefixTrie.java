/*
Copyright(c) 2010-2012

This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under contract [contract citation, subcontract and prime contract]. 
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.

 */

package edu.vu.isis.ammo.util;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This is a Trie which uses bits on bytes as potential branches.
 * 
 * THIS IS NON-FUNCTIONING CODE
 * 
 * TODO This should be an implementation of burst tries.
 * "Burst Tries: A Fast, Efficient Data Structure for String Keys (2002)"
 * Steffen Heinz , Justin Zobel , Hugh E. Williams
 * 
 * http://www.cs.mu.oz.au/~jz/fulltext/acmtois02.pdf
 */
public class PrefixTrie<V> extends AbstractPrefixTrie<V> {
	private static final Logger logger = LoggerFactory.getLogger("ammo-trie");
	
    private byte[] frag;
	private List< PrefixTrie<V> > burst;
	private V value;
	
	public PrefixTrie(Key key, V value, List< PrefixTrie<V> >burst) {
		this.frag = key.extract();
		this.value = value;
		if (this.burst == null) {
			this.burst = new ArrayList< PrefixTrie<V> >(256);
		} else {
			this.burst = burst;
		}
	}
	
	/**
	 * The root of the trie
	 */
	public PrefixTrie() {
		this(null, null, null);
	}
	
	@Override
	public void insert(INode val) {
		@SuppressWarnings("unchecked")
		final Node node = (Node) val;
		
		final int matchCnt = node.key.match(this.frag);
		if (matchCnt == node.key.remaining()) {
			logger.error("duplicate topic type {}", value);
			return;
		}
		if (matchCnt == this.frag.length) {
			node.key.offset( this.frag.length );
			final PrefixTrie<V> child = this.burst.get(node.key.get());
			node.key.mark();
			child.insert(node);
    		return;
		}
    	// split trie
		//final byte[] parentFrag = Arrays.copyOf(this.frag, matchCnt);
		final int childIx = this.frag[matchCnt];
		//final byte[] childFrag = Arrays.copyOfRange(this.frag, matchCnt, this.frag.length); 
		final PrefixTrie<V> child = new PrefixTrie<V>(null, this.value, this.burst);
		//child.frag = childFrag;
		//this.frag = parentFrag;
		//this.node.value = value;
		this.burst = new ArrayList< PrefixTrie<V> >(256);
    	this.burst.add(childIx, child);
	}
	
	/**
	 * Check if this key is a prefix for the supplied key.
	 * null : this Trie is not a prefix to the key.
	 * this : it is a prefix match to the key.
	 * 
	 * @param key
	 * @return 
	 */
	
	@Override
	public Node longestPrefix(IKey val) {
		@SuppressWarnings("unchecked")
		final Key key = (Key) val;
		
    	if (key.match(this.frag) < this.frag.length) // different keys
    		return null; 
    	
    	//if (key.remaining() == this.frag.length) // identical keyspublic
    	//	return this.value;
    	
    	// matching but continued
		//key.offset( this.frag.length );
		//final PrefixTrie<V> child = this.burst.get(key.get());
		//key.mark();public
		//V result = child.longestPrefix(key);
		//if (result != null) return result;
    	//return this.value;
    	return null;
	}
	
	@Override
	public List<V> values() {
		logger.error("values not implemented");
		return null;
	}
    

}

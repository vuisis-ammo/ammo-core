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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This is a Trie which uses bits on bytes as potential branches.
 */
public class PrefixList<V> extends AbstractPrefixTrie<V> {
	private static final Logger logger = LoggerFactory.getLogger("PrefixList");

	final private List< Node > nodes;
	final private Map< Key, Node > prefixMap;
	private boolean isDirty;

	public PrefixList() {
		this.nodes = new ArrayList< Node >(5);
		this.prefixMap = new HashMap< Key, Node >(5);
		this.isDirty = false;
	}

	@Override
	public void insert(INode val) {
		@SuppressWarnings("unchecked")
		final Node node = (Node) val;
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
		if (this.isDirty) {
			Collections.sort(this.nodes);
			this.prefixMap.clear();
		}
		if (this.prefixMap.containsKey(key)) {
			return this.prefixMap.get(key);
		}
		int bestScore = -1;
		int lastScore = -1;
		Node bestNode = null;
		for (Node node : this.nodes){
			final Key currentKey = node.key;
			final int score = key.match(currentKey);
			if (score < lastScore) break;
			lastScore = score;
			if (score == currentKey.size()) {
				if (score > bestScore) {
					bestScore = score;
					bestNode = node;
				}
			}
			if (score == key.size()) break;
		}
		logger.debug("match {} {}", key, bestNode);
		this.prefixMap.put(key, bestNode);
		return bestNode;
	}
	
	@Override
	public List<V> values() {
		final List<V> vals = new ArrayList<V>(this.nodes.size());
		for (Node node : this.nodes) {
			vals.add(node.value);
		}
		return vals;
	}

}




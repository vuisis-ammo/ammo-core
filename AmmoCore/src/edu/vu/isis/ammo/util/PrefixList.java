package edu.vu.isis.ammo.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This is a Trie which uses bits on bytes as potential branches.
 */
public class PrefixList<V> extends AbstractPrefixTrie<V> {
	private static final Logger logger = LoggerFactory.getLogger("ammo-plist");

	final private List< Node > nodes;
	private boolean isDirty;

	public PrefixList() {
		this.nodes = new ArrayList< Node >(5);
		this.isDirty = false;
	}

	@Override
	protected void insert(Node node) {
		this.nodes.add(node);
		this.isDirty = true;
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
	protected Node longestPrefix(Key key) {
		if (this.isDirty) {
			Collections.sort(this.nodes);
		}
		int lower = 0;
		int upper = 1;
		for (; upper < this.nodes.size(); upper = upper << 1) {}
		for (int delta = upper >> 1; delta > 0; delta = delta >> 1) {
			final int current = lower + delta;

			if (current > this.nodes.size()) {
				continue;
			}
			final Key currentKey = this.nodes.get(current).key;
			int score = key.match(currentKey);	
			if (score == key.size()) {
				upper = current;
			} else if (score == currentKey.size()) {
				lower = current;
			} else {

				final int comparison = key.compareItem(score, currentKey);
				if (comparison < 0) {
                    upper = current;
				} else {
					lower = current;
				}
			}
		}
		return this.nodes.get(lower);
	}
}




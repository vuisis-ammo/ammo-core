package edu.vu.isis.ammo.pretrie;

import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.pretrie.Prefix.Match;
import edu.vu.isis.ammo.pretrie.Prefix.Type;

/**
 * The Pretrie interface provides for a node based collection. As with many node
 * based collections (e.g. TreeTwig) each node represents a subtree. A subtree
 * need contain only itself in which case it is a leaf. A pretrie node will have
 * both leaves and branches.
 * <p>
 * The pretrie is based on the trie data structure. All implementation's
 * operations should be O(n) or better. The basic trie, upon which this is
 * based, uses a 256 reference array, this is fast but it uses quite a bit of
 * space.
 * <p>
 * The pretrie is a key:value store where its keys are prefixes. The value is
 * typicall a function object.
 * <p>
 * The pretrie is composed of:
 * <dl>
 * <dt>Branch</dt>
 * <dd>nodes with children and values</dd>
 * <dt>Twig</dt>
 * <dd>nodes with children</dd>
 * <dt>Leaf</dt>
 * <dd>a node with a value</dd>
 * <dt>Stem</dt>
 * <dd>carries an extended prefix</dd>
 * </dl>
 * Generally the methods mimic those of java.util.Collection<E>.
 * 
 * @param <V>
 *            the type of the values stored in the node.
 */

public class Pretrie<V> {
	private static final Logger logger = LoggerFactory.getLogger(Pretrie.class);
	private static final boolean LOG_ON = false;

	/**
	 * The node is split using the following constants The primary array
	 * prevents most of this waste by only loading up when needed. Essentially
	 * the length 256 array of partial prefixes is broken up into 32 x 8 = 256
	 * where in sparse situations the secondary arrays are not allocated.
	 */
	static private final int MINOR_BITMASK;
	static private final int MAJOR_SHIFT;
	static private final int MINOR_SIZE;
	static private final int MAJOR_SIZE;
	static {
		MINOR_BITMASK = 0x07;
		MAJOR_SHIFT = 3;
		MINOR_SIZE = 8;
		MAJOR_SIZE = 32;
	}
	final private Branch<V> trunk;
	private V bud;

	public Pretrie(final V base) {
		this.trunk = new Branch<V>(null);
		this.bud = base;
	}

	private final static String stringEncoding = "UTF-8";

	/**
	 * The insert method is deprecated and is provided for compatibility with
	 * the earlier PrefixList.
	 * 
	 * @deprecated
	 * @param prefix
	 * @param value
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public void insert(final String prefix, final V value)
			throws UnsupportedEncodingException {
		this.put(prefix.getBytes(stringEncoding), value);
	}

	/**
	 * The longestPrefix method is deprecated and is provided for compatibility
	 * with the earlier PrefixList. The get() method should be used instead.
	 * 
	 * @deprecated
	 * @param key
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public V longestPrefix(final String key)
			throws UnsupportedEncodingException {
		return this.get(key.getBytes(stringEncoding));
	}

	/**
	 * Different methods for inserting values into the pretrie.
	 * 
	 * @param prefix
	 * @param value
	 * @return
	 */
	public void put(final Prefix prefix, final V value) {
		if (prefix == null) {
			this.bud = value;
		}
		this.trunk.put(prefix, value);
		logger.debug("put prefix {} complete", this);
	}

	public void put(final byte[] prefix, final V value) {
		logger.trace("put w/ byte");
		this.put(Prefix.newInstance(prefix), value);
	}

	public void putAll(Branch<? extends V> sub) {
		logger.trace("put all from sub");
	}

	/**
	 * The preferred methods for retrieving values from the pretrie.
	 * 
	 * @param key
	 * @return
	 */
	public V get(final Prefix key) {
		logger.trace("get by prefix key {}", key);
		return this.trunk.get(key);
	}

	public V get(final byte[] key) {
		return this.get(Prefix.newInstance(key));
	}

	public V get(final String key) {
		logger.trace("get by string key {}", key);
		return this.get(Prefix.newInstance(key));
	}

	@Override
	public String toString() {
		return this.toString(new StringBuilder()).toString();
	}

	public StringBuilder toString(final StringBuilder sb) {
		if (this.bud != null) {
			sb.append('\n').append("[ () ] {").append(this.bud).append('}');
		}
		return this.trunk.toStringBuilder(sb);
	}

	/**
	 * Is there a match for this prefix.
	 * 
	 * @param key
	 *            the key for which the best match is sought
	 * @return the best matching value
	 */
	public boolean containsKey(final byte[] key) {
		logger.trace("contains key");
		return this.trunk.get(Prefix.newInstance(key)) != null;
	}

	public V remove(final Prefix prefix) {
		logger.trace("remove w/ prefix");
		return this.trunk.remove(prefix);
	}

	public V remove(final byte[] prefix) {
		logger.trace("remove w/ prefix");
		return this.trunk.remove(Prefix.newInstance(prefix));
	}

	public void putValue(final Prefix prefix, final V value) {
		this.trunk.put(prefix, value);
	}

	/**
	 * The pretrie branch and twig are used to select the next stem.
	 * 
	 * 
	 * @param <V>
	 */
	private static class Branch<V> {
		private static final Logger logger = LoggerFactory
				.getLogger(Branch.class);

		/** The prefix to this map */
		private final Stem<V> parent;
		private final Twig<V>[] twigSet;

		/**
		 * Get the stem which references this branch.
		 * 
		 * @return
		 */
		public Stem<V> getStem() {
			return this.parent;
		}

		/**
		 * The base object is a branch object. It is expected to have multiple
		 * prefixes and values.
		 * 
		 * <p>
		 * 
		 * 
		 * @param parent
		 */

		@SuppressWarnings("unchecked")
		public Branch(final Stem<V> parent) {
			logger.trace("constructor : 1");
			this.parent = parent;
			this.twigSet = new Twig[MAJOR_SIZE];
		}

		/**
		 * Get the value associated with the key.
		 * 
		 * @param key
		 * @return
		 */
		public V get(final Prefix key) {
			final Twig<V> twig = this.acquireTwig(key, false);
			if (twig == null) {
				return null;
			}
			return twig.get(key);
		}

		/**
		 * Insert a new prefix and its value. The previous value of the value
		 * corresponding to the prefix is returned. If the value was not set
		 * null is returned.
		 * 
		 * @param prefix
		 * @param value
		 * @return did the insert succeed
		 */
		public Branch<V> put(final Prefix prefix, final V value) {
			logger.trace("put w/ prefix {}", prefix);
			final Twig<V> twig = this.acquireTwig(prefix, true);
			twig.put(prefix, value);
			return this;
		}

		public Branch<V> put(final Stem<V> stem) {
			logger.trace("put w/ prefix {}", stem.prefix);
			final Twig<V> twig = this.acquireTwig(stem.prefix, true);
			twig.put(stem);
			return this;
		}

		/**
		 * Gets the twig if it is already there, and creates it if it is not.
		 * 
		 * @param prefix
		 * @return
		 */
		private Twig<V> acquireTwig(final Prefix prefix,
				final boolean shouldAllocate) {
			final int currentByte = prefix.getCurrentByte();
			if (currentByte < 0)
				return null;
			final int index = currentByte >>> MAJOR_SHIFT;

			if (shouldAllocate && this.twigSet[index] == null) {
				final Twig<V> twig = new Twig<V>(this);
				this.twigSet[index] = twig;
				return twig;
			}
			return this.twigSet[index];
		}
		
		void updateParentage(final Stem<V> stem) {
			for (Twig<V> twig : this.twigSet) {
				if (twig == null) continue;
				logger.trace("stem of branch {} {}", twig.parent.parent.hashCode(), stem.hashCode());
				twig.updateParentage(stem);
			}
		}

		public V remove(final Prefix prefix) {
			logger.trace("remove w/ prefix");
			return null;
		}

		@Override
		public String toString() {
			return this.toStringBuilder(new StringBuilder()).toString();
		}

		public StringBuilder toStringBuilder(final StringBuilder sb) {
			if (LOG_ON)
				logger.trace("toString: branch {}", this.hashCode());

			for (Twig<V> twig : this.twigSet) {
				if (twig == null)
					continue;
				twig.toStringBuilder(sb).append('\n');
			}
			if (LOG_ON)
				logger.trace("toString: branch {} exit", this.hashCode());
			return sb;
		}

	}

	/**
	 * A graft extends a branch by MINOR_SIZE times;
	 * 
	 * @param <V>
	 */
	private static class Twig<V> {
		private static final Logger logger = LoggerFactory
				.getLogger(Twig.class);
		private final Branch<V> parent;
		private final Stem<V>[] stemSet;

		@SuppressWarnings("unchecked")
		public Twig(final Branch<V> branch) {
			logger.trace("constructor");
			this.parent = branch;
			this.stemSet = (Stem<V>[]) new Stem[MINOR_SIZE];
		}

		/**
		 * Either get the indicated stem or (optionally) allocate a new stem.
		 * 
		 * @param prefix
		 * @param shouldAllocate
		 * @return
		 */
		Stem<V> acquireStem(final Prefix prefix, final boolean shouldAllocate) {
			final int currentByte = prefix.getCurrentByte();
			if (currentByte < 0)
				return null;
			final int index = currentByte & MINOR_BITMASK;

			if (shouldAllocate && this.stemSet[index] == null) {
				final Stem<V> stem = new Stem<V>(this.parent.getStem(), prefix);
				this.stemSet[index] = stem;
				return stem;
			}
			return this.stemSet[index];
		}

		void updateParentage(final Stem<V> parent) {
			for (Stem<V> child : this.stemSet) {
				if (child == null) continue;
				logger.trace("stem of branch {} {}", child.parent.hashCode(), parent.hashCode());
				child.parent = parent;
			}
		}

		/**
		 * Get the value associated with the key.
		 * 
		 * @param key
		 * @return
		 */
		public V get(final Prefix key) {
			final Stem<V> stem = this.acquireStem(key, false);
			return stem.get(key);
		}

		/**
		 * Insert a new prefix and its value. The previous value of the value
		 * corresponding to the prefix is returned. If the value was not set
		 * null is returned.
		 */
		Stem<V> put(final Prefix prefix, final V value) {
			logger.trace("put w/ prefix {}", prefix);
			final Stem<V> stem = this.acquireStem(prefix, true);
			return stem.put(prefix, value);
		}

		Stem<V> put(final Stem<V> stem) {
			logger.trace("put stem {}", stem.prefix);
			final int currentByte = stem.prefix.getCurrentByte();
			if (currentByte < 0) {
				return null;
			}
			final int index = currentByte & MINOR_BITMASK;
			this.stemSet[index] = stem;
			return stem;
		}

		@Override
		public String toString() {
			return this.toStringBuilder(new StringBuilder()).toString();
		}

		public StringBuilder toStringBuilder(final StringBuilder sb) {
			if (LOG_ON)
				logger.trace("toString: twig {}", this.hashCode());
			for (Stem<V> stem : this.stemSet) {
				if (stem == null)
					continue;
				stem.toStringBuilder(sb);
			}
			if (LOG_ON)
				logger.trace("toString: twig {} exit", this.hashCode());
			return sb;
		}
	}

	/**
	 * An twig extends the prefix on a graft and points to an Stem. The twig
	 * carries a Prefix which spans its contribution. The offset for the prefix
	 * indicates the byte used by the branch and graft.
	 * 
	 * @param <V>
	 */
	public static class Stem<V> {
		private static final Logger logger = LoggerFactory
				.getLogger(Stem.class);
		private Prefix prefix;
		/** the prefix stem which this stem extends */
		private Stem<V> parent;
		/** the branch leading to other descendant stems */
		private Branch<V> branch;
		/** the value stored at the node */
		private V value;

		public Stem(final Stem<V> parent, final Prefix prefix) {
			logger.trace("constructor");
			this.parent = parent;
			this.prefix = prefix;
			this.branch = null;
			this.value = null;
		}

		public void resetParentStem() {
			this.parent = this.parent.parent;
		}

		public Prefix getPrefix() {
			return this.prefix;
		}

		public int getOffset() {
			return this.prefix.getOffset();
		}

		/**
		 * Ascend the pretrie until you find a node with a value.
		 * 
		 * @return
		 */
		public V getValue() {
			if (this.value != null)
				return value;
			if (this.parent == null)
				return null;
			return this.parent.getValue();
		}

		/**
		 * Descend the pretrie looking for the longest match.
		 * 
		 * @param key
		 * @return
		 */
		V get(final Prefix key) {
			logger.trace("get {}", key);
			if (!this.prefix.isPrefixOf(key)) {
				if (this.parent == null) {
					return null;
				}
				return this.parent.getValue();
			}
			if (this.prefix.equals(key)) {
				return this.getValue();
			}
			if (this.branch == null)
				return null;
			final int endOffset = this.prefix.getEndOffset();
			final Prefix newKey = key.trimOffset(endOffset);
			return this.branch.get(newKey);
		}

		/**
		 * Look for a place where there is no match. Split the stem. At the
		 * split the left side gets the old (prefix/value)s and the right side
		 * gets the new prefix/value.
		 * 
		 * 
		 * @param that_prefix
		 * @param offset
		 * @param value
		 * @return
		 */
		Stem<V> put(final Prefix that_prefix, final V value) {
			logger.trace("put value w/ prefix {}", that_prefix);
			final Prefix.Match match;
			try {
				match = (this.prefix == null) ? new Match(Type.NULL, 0)
						: this.prefix.match(that_prefix);
			} catch (IllegalArgumentException e) {
				logger.error("bad match");
				return null;
			}

			logger.trace("prefix match {}", match);
			switch (match.type) {
			case NULL:
				this.prefix = that_prefix;
				this.parent = null;
				this.branch = null;
				this.value = value;
				return this;
			case A_EQ_B:
				this.value = value;
				return this;
			case A_LT_B: {
				final Prefix rightPrefix = that_prefix.trimOffset(match.offset);
				if (this.branch == null) {
					this.branch = new Branch<V>(this);
				}
				this.branch.put(rightPrefix, value);
				return this;
			}
			case A_GT_B: {
				final Prefix beforePrefix = this.prefix
						.trimLength(match.offset);
				final Prefix leftPrefix = this.prefix.trimOffset(match.offset);

				final Stem<V> oldParent = this.parent;
				final Stem<V> newParent = this;

				final Branch<V> oldBranch = this.branch;
				final Branch<V> newBranch = new Branch<V>(this);

				final V oldValue = this.value;
				final V newValue = value;

				this.prefix = beforePrefix;
				this.parent = oldParent;
				this.branch = newBranch;
				this.value = oldValue;

				final Stem<V> newStem = new Stem<V>(this, leftPrefix);
				newStem.prefix = leftPrefix;
				newStem.parent = newParent;
				newStem.branch = oldBranch;
				newStem.value = oldValue;
				newBranch.put(newStem);
				if (oldBranch != null) {
					oldBranch.updateParentage(newStem);
				}
				this.value = newValue;
				return this;
			}
			case A_B: {
				final Prefix beforePrefix = this.prefix
						.trimLength(match.offset);
				final Prefix leftPrefix = this.prefix.trimOffset(match.offset);
				final Prefix rightPrefix = that_prefix.trimOffset(match.offset);

				final Stem<V> oldParent = this.parent;
				final Stem<V> newParent = this;

				final Branch<V> oldBranch = this.branch;
				final Branch<V> newBranch = new Branch<V>(this);

				final V oldValue = this.value;
				final V newValue = value;

				this.prefix = beforePrefix;
				this.parent = oldParent;
				this.branch = newBranch;
				this.value = null;

				final Stem<V> newStem = new Stem<V>(this, leftPrefix);
				newStem.prefix = leftPrefix;
				newStem.parent = newParent;
				newStem.branch = oldBranch;
				newStem.value = oldValue;
				newBranch.put(newStem);
				if (oldBranch != null) {
					oldBranch.updateParentage(newStem);
				}
				newBranch.put(rightPrefix, newValue);
				return this;
			}
			case A_NOT_B:
				logger.error("you should never see A not B");
				return null;
			default:
				return null;
			}
		}

		public V remove(final Prefix prefix) {
			logger.trace("remove w/ prefix");
			if (this.branch != null)
				return null;
			return this.branch.remove(prefix);
		}

		@Override
		public String toString() {
			return this.toStringBuilder(new StringBuilder()).toString();
		}

		public StringBuilder toStringBuilder(final StringBuilder sb) {
			sb.append('\n').append('[').append(this.hashCode()).append(']');

			if (this.prefix != null) {
				this.prefix.toStringBuilder(sb);
			} else {
				sb.append("no prefix");
			}
			if (this.value != null) {
				sb.append('{').append(value).append('}');
			}
			if (this.parent != null) {
				sb.append(" -> ");
				sb.append(this.parent.hashCode());
				// this.parent.toStringBuilder(sb);
			}

			if (this.branch != null) {
				this.branch.toStringBuilder(sb);
			}
			return sb;
		}

	}

}

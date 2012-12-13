package edu.vu.isis.ammo.pretrie;

import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private final Stem<V> trunk;

	public Pretrie() {
		this.trunk = new Stem<V>((Stem<V>) null, (Prefix) null,
				(Branch<V>) null);
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
	public V insert(final String prefix, final V value)
			throws UnsupportedEncodingException {
		return this.put(prefix.getBytes(stringEncoding), value);
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
	 * @param key
	 * @param value
	 * @return
	 */
	public V put(final Prefix key, final V value) {
		return this.trunk.put(key, value);
	}

	public V put(final byte[] prefix, final V value) {
		logger.trace("put w/ byte");
		final V result = this.put(Prefix.newInstance(prefix), value);
		logger.debug("toString: trunk [{}]", this.trunk);
		return result;
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
		return this.trunk.get(key);
	}

	public V get(final byte[] key) {
		logger.trace("get w/ bytes");
		return this.get(Prefix.newInstance(key));
	}

	public V get(final String key) {
		logger.trace("get w/ bytes");
		return this.get(Prefix.newInstance(key));
	}

	@Override
	public String toString() {
		return this.toString(new StringBuilder()).toString();
	}

	public StringBuilder toString(final StringBuilder sb) {
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

	public V getValue() {
		return this.trunk.getValue();
	}

	public V putValue(final Prefix prefix, final V value) {
		return this.trunk.put(prefix, value);
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
		 * Insert a new prefix and its value. The previous value of the value
		 * corresponding to the prefix is returned. If the value was not set
		 * null is returned.
		 * 
		 * @param prefix
		 * @param value
		 * @return did the insert succeed
		 */
		public V put(final Prefix prefix, final V value) {
			logger.trace("put w/ prefix");
			final Twig<V> twig = this.acquireTwig(prefix, true);
			return twig.put(prefix, value);
		}

		/**
		 * 
		 * @param prefix
		 * @param branch
		 */
		public void put(final Prefix prefix, final Stem<V> stem) {
			logger.trace("put w/ prefix");
			final Twig<V> twig = this.acquireTwig(prefix, true);
			twig.put(prefix, stem);
		}

		/**
		 * Gets the twig if it is already there, and creates it if it is not.
		 * 
		 * @param prefix
		 * @return
		 */
		private Twig<V> acquireTwig(final Prefix prefix,
				final boolean shouldAllocate) {
			final int index = ((int) prefix.getCurrentByte()) >>> MAJOR_SHIFT;

			if (shouldAllocate && this.twigSet[index] == null) {
				final Twig<V> twig = new Twig<V>(this);
				this.twigSet[index] = twig;
				return twig;
			}
			return this.twigSet[index];
		}

		public V remove(final Prefix prefix) {
			logger.trace("remove w/ prefix");
			return null;
		}

		@Override
		public String toString() {
			return this.toStringBiulder(new StringBuilder()).toString();
		}

		public StringBuilder toStringBiulder(final StringBuilder sb) {
			if (LOG_ON) logger.trace("toString: branch {}", this.hashCode());
			for (Twig<V> twig : this.twigSet) {
				if (twig == null)
					continue;
				twig.toStringBuilder(sb).append('\n');
			}
			if (LOG_ON) logger.trace("toString: branch {} exit", this.hashCode());
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
		private Stem<V> stem;

		@SuppressWarnings("unchecked")
		public Twig(final Branch<V> branch) {
			logger.trace("constructor");
			this.parent = branch;
			this.stem = branch.getStem();
			this.stemSet = (Stem<V>[]) new Stem[MINOR_SIZE];
		}

		Stem<V> acquireStem(final Prefix prefix, final boolean shouldAllocate) {
			final int index = ((int) prefix.getCurrentByte()) & MINOR_BITMASK;

			if (shouldAllocate && this.stemSet[index] == null) {
				final Stem<V> stem = new Stem<V>(this.stem, prefix, null);
				this.stemSet[index] = stem;
				return stem;
			}
			return this.stemSet[index];
		}

		V put(final Prefix prefix, final V value) {
			logger.trace("put w/ value");
			final Stem<V> stem = this.acquireStem(prefix, true);
			return stem.put(prefix, value);
		}

		void put(final Prefix prefix, final Stem<V> oldStem) {
			logger.trace("put w/ value");
			final Stem<V> stem = this.acquireStem(prefix, true);
			stem.put(prefix, oldStem);
		}

		@Override
		public String toString() {
			return this.toStringBuilder(new StringBuilder()).toString();
		}

		public StringBuilder toStringBuilder(final StringBuilder sb) {
			if (LOG_ON) logger.trace("toString: twig {}", this.hashCode());
			for (Stem<V> stem : this.stemSet) {
				if (stem == null)
					continue;
				stem.toStringBuilder(sb);
			}
			if (LOG_ON) logger.trace("toString: twig {} exit", this.hashCode());
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
		private Stem<V> parent;
		private Branch<V> branch;
		private V value;

		public Stem(final Stem<V> parent, final Prefix prefix,
				final Branch<V> branch) {
			logger.trace("constructor");
			this.parent = parent;
			this.prefix = prefix;
			this.branch = branch;
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

		V get(final Prefix key) {
			logger.trace("get");
			if (this.prefix.equals(key)) {
				return this.getValue();
			}
			return null;
		}

		/**
		 * Look for a place where there is no match. Split the stem. At the
		 * split the left side gets the old (prefix/value)s and the right side
		 * gets the new prefix/value.
		 * <p>
		 * The relationships of the two prefixes A & B are:
		 * <ul>
		 * <li>prefix A has not been set</li>
		 * <li>A equals B (they are proper prefixes of each other)</li>
		 * <li>A is a proper prefix of B</li>
		 * <li>B is a proper prefix of A</li>
		 * <li>A and B do not share a common prefix</li>
		 * <li>A and B share a common prefix</li>
		 * </ul>
		 * 
		 * @param that_prefix
		 * @param position
		 * @param value
		 * @return
		 */
		V put(final Prefix that_prefix, final V value) {
			logger.trace("put value w/ prefix {}", that_prefix);
			if (this.prefix == null) {
				/** prefix A has not been set */
				this.prefix = that_prefix;
				this.value = value;
				this.branch = null;
				return null;
			}
			final int branchOffset;
			try {
				branchOffset = this.prefix.partialMatchOffset(that_prefix);
			} catch (IllegalArgumentException e) {
				logger.error("bad match");
				return null;
			}
			if (branchOffset >= this.prefix.getEndOffset()) {
				if (branchOffset >= that_prefix.getEndOffset()) {
					/**
					 * this.prefix = that_prefix 
					 */
					final V oldValue = this.value;
					this.value = value;
					return oldValue;
				}
				/** this.prefix < that_prefix */
				final Prefix before = this.prefix.trimLength(branchOffset);
				final Prefix rightPrefix = that_prefix.trimOffset(branchOffset);

				this.prefix = before;
				final Branch<V> wipBranch = new Branch<V>(this);
				wipBranch.put(rightPrefix, value);
				this.branch = wipBranch;
				return null;
			}
			if (branchOffset >= that_prefix.getEndOffset()) {
				/**  this.prefix > that_prefix */
				final Prefix before = this.prefix.trimLength(branchOffset);
				final Prefix leftPrefix = this.prefix.trimOffset(branchOffset);

				this.prefix = before;
				final Branch<V> wipBranch = new Branch<V>(this);
				wipBranch.put(leftPrefix, this);
				this.branch = wipBranch;

				this.value = value;
				return null;
			}

			/** this.prefix and that_prefix share a common prefix */
			final Prefix before = this.prefix.trimLength(branchOffset);
			final Prefix leftPrefix = this.prefix.trimOffset(branchOffset);
			final Prefix rightPrefix = that_prefix.trimOffset(branchOffset);

			this.prefix = before;
			final Branch<V> wipBranch = new Branch<V>(this);
			wipBranch.put(leftPrefix, this);
			this.value = null;
			wipBranch.put(rightPrefix, value);
			this.branch = wipBranch;
			return null;
		}

		void put(final Prefix prefix, final Stem<V> that) {
			logger.trace("put w/ leaf");
			this.prefix = prefix;
			this.branch = that.branch;
			this.value = that.value;
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
			if (LOG_ON)
			logger.trace("toString: stem {} [{}] -> \"{}\"", this.hashCode(),
					this.prefix, this.value);
			sb.append('\n');
			if (this.prefix != null) {
				this.prefix.toStringBuilder(sb);
			} else {
				sb.append("no prefix");
			}
			if (this.value != null) {
				sb.append('{').append(value).append('}');
			}

			if (this.branch != null) {
				this.branch.toStringBiulder(sb);
			}
			if (LOG_ON)
			logger.trace("toString: stem {} exit", this.hashCode());
			return sb;
		}

	}

}

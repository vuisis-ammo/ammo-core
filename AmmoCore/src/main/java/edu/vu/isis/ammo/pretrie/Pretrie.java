package edu.vu.isis.ammo.pretrie;

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
 * <dt>Trunk</dt>
 * <dd>carries an extended prefix</dd>
 * </dl>
 * Generally the methods mimic those of java.util.Collection<E>.
 * 
 * @param <V>
 *            the type of the values stored in the node.
 */

public class Pretrie<V> {
	private static final Logger logger = LoggerFactory.getLogger(Pretrie.class);
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
	private final Trunk<V> root;

	public Pretrie() {
		this.root = new Trunk<V>((Trunk<V>) null, (Prefix) null,
				(Branch<V>) null);
	}

	/**
	 * This method is for adding an existing
	 * 
	 * @param prefix
	 * @param node
	 */
	public Pretrie(Trunk<V> node) {
		throw new UnsupportedOperationException("pretrie branch from node");
	}

	public V get(final Prefix key) {
		return this.root.get(key);
	}

	public V put(final Prefix key, final V value) {
		return this.root.put(key, value);
	}

	public V put(final byte[] prefix, final V value) {
		logger.trace("put w/ byte");
		return this.put(Prefix.newInstance(prefix), value);
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
		return this.root.toStringBuilder(sb);
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
		return this.root.get(Prefix.newInstance(key)) != null;
	}

	public void putAll(Branch<? extends V> sub) {
		logger.trace("put all from sub");
	}

	public V remove(final Prefix prefix) {
		logger.trace("remove w/ prefix");
		return this.root.remove(prefix);
	}

	public V remove(final byte[] prefix) {
		logger.trace("remove w/ prefix");
		return this.root.remove(Prefix.newInstance(prefix));
	}

	public V getValue() {
		return this.root.getValue();
	}

	public V putValue(final Prefix prefix, final V value) {
		return this.root.put(prefix, value);
	}

	/**
	 * The pretrie branch and twig are used to select the next trunk.
	 * 
	 * 
	 * @param <V>
	 */
	private static class Branch<V> {
		private static final Logger logger = LoggerFactory
				.getLogger(Branch.class);

		/** The prefix to this map */
		private final Trunk<V> parent;
		private final Twig<V>[] twigSet;

		public Trunk<V> getTrunk() {
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
		public Branch(final Trunk<V> parent) {
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
		public void put(final Prefix prefix, final Trunk<V> trunk) {
			logger.trace("put w/ prefix");
			final Twig<V> twig = this.acquireTwig(prefix, true);
			twig.put(prefix, trunk);
		}

		/**
		 * Gets the twig if it is already there, and creates it if it is not.
		 * 
		 * @param prefix
		 * @return
		 */
		private Twig<V> acquireTwig(final Prefix prefix, final boolean shouldAllocate) {
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
			for (Twig<V> twig : this.twigSet) {
				if (twig == null)
					continue;
				twig.toStringBuilder(sb).append('\n');
			}
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
		private final Trunk<V>[] trunkSet;
		private Trunk<V> trunk;

		@SuppressWarnings("unchecked")
		public Twig(final Branch<V> branch) {
			logger.trace("constructor");
			this.parent = branch;
			this.trunk = branch.getTrunk();
			this.trunkSet = (Trunk<V>[]) new Trunk[MINOR_SIZE];
		}

		Trunk<V> acquireTrunk(final Prefix prefix, final boolean shouldAllocate) {
			final int index = ((int) prefix.getCurrentByte()) & MINOR_BITMASK;
			
			if (shouldAllocate && this.trunkSet[index] == null) {
				final Trunk<V> trunk = new Trunk<V>(this.trunk, prefix, this.parent);
				this.trunkSet[index] = trunk;
				return trunk;
			} 
			return this.trunkSet[index];
		}

		V put(final Prefix prefix, final V value) {
			logger.trace("put w/ value");
			final Trunk<V> trunk = this.acquireTrunk(prefix, true);
			return trunk.put(prefix, value);
		}

		void put(final Prefix prefix, final Trunk<V> oldTrunk) {
			logger.trace("put w/ value");
			final Trunk<V> trunk = this.acquireTrunk(prefix, true);
			trunk.put(prefix, oldTrunk);
		}

		@Override
		public String toString() {
			return this.toStringBuilder(new StringBuilder()).toString();
		}

		public StringBuilder toStringBuilder(final StringBuilder sb) {
			for (Trunk<V> trunk : this.trunkSet) {
				if (trunk == null)
					continue;
				trunk.toStringBuilder(sb).append('\n');
			}
			return sb;
		}
	}

	/**
	 * An twig extends the prefix on a graft and points to an Trunk. The twig
	 * carries a Prefix which spans its contribution. The offset for the prefix
	 * indicates the byte used by the branch and graft.
	 * 
	 * @param <V>
	 */
	public static class Trunk<V> {
		private static final Logger logger = LoggerFactory
				.getLogger(Trunk.class);
		private Prefix prefix;
		private Trunk<V> parent;
		private Branch<V> branch;
		private V value;

		public Trunk(final Trunk<V> parent, final Prefix prefix,
				final Branch<V> branch) {
			logger.trace("constructor");
			this.parent = parent;
			this.prefix = prefix;
			this.branch = branch;
			this.value = null;
		}

		public void resetParentTrunk() {
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
			if (this.value != null) return value;
			if (this.parent == null) return null;
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
		 * Look for a place where there is no match. Split the trunk. At the
		 * split the left side gets the old (prefix/value)s and the right side
		 * gets the new prefix/value.
		 * 
		 * @param prefix
		 * @param position
		 * @param value
		 * @return
		 */
		V put(final Prefix prefix, final V value) {
			logger.trace("put value w/ prefix {}", prefix);
			if (this.prefix == null) {
				this.prefix = prefix;
				this.value = value;
				return null;
			}
			final int offset;
			try {
				offset = this.prefix.partialMatchOffset(prefix);
			} catch (Exception e) {
				return null;
			}
			if (offset < 0) {
				final V oldValue = this.value;
				this.value = value;
				return oldValue;
			}
			final Prefix before = this.prefix.trimLength(offset);
			final Prefix leftPrefix = this.prefix.trimOffset(offset);
			final Prefix rightPrefix = prefix.trimOffset(offset);

			this.prefix = before;
			final Branch<V> wipBranch = new Branch<V>(this);
			wipBranch.put(leftPrefix, this);
			wipBranch.put(rightPrefix, value);
			this.branch = wipBranch;

			return null;
		}

		void put(final Prefix prefix, final Trunk<V> that) {
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
			if (this.prefix != null)
				this.prefix.toStringBuilder(sb);
			if (this.branch != null)
				this.branch.toStringBiulder(sb);
			sb.append('{').append(value).append('}');
			return sb;
		}

	}

}

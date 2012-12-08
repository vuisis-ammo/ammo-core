package edu.vu.isis.ammo.pretrie;

/**
 * The Pretrie interface provides for a node based collection. As with many node
 * based collections (e.g. TreeNode) each node represents a subtree. A subtree
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
 * Generally the methods mimic those of java.util.Collection<E>.
 * 
 * @param <V>
 *            the type of the values stored in the node.
 */

public class Pretrie<V> implements IPretrieLeaf<V> {

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
		MINOR_BITMASK = ~0x07;
		MAJOR_SHIFT = 3;
		MINOR_SIZE = 8;
		MAJOR_SIZE = 32;
	}

	/** The prefix to this map */
	private final Pretrie<V> parent;
	private final Node<V>[] primary;
	private V leaf;

	/**
	 * The base object is a branch object. It is expected to have multiple
	 * prefixes and values.
	 * 
	 * <p>
	 * 
	 * 
	 * @param parent
	 */
	public Pretrie() {
		this(null);
	}

	@SuppressWarnings("unchecked")
	public Pretrie(final Pretrie<V> parent) {
		this.parent = parent;
		this.primary = new Node[MAJOR_SIZE];
		this.leaf = null;
	}

	public Pretrie(Prefix prefix, IPretrieLeaf<V> leaf) {
		this(null);
		final int primaryIndex = ((int) prefix.getCurrentByte()) >>> MAJOR_SHIFT;
		final Node<V> node = new Node<V>(this);
		node.put(prefix, leaf);
		this.primary[primaryIndex] = node;
	}

	/**
	 * Recursively ascend to get the best matching leaf.
	 */
	@Override
	public V getValue() {
		if (this.leaf != null) {
			return this.leaf;
		}
		if (this.parent == null) {
			return null;
		}
		return this.parent.getValue();
	}

	@Override
	public V putValue(final V value) {
		final V prior = this.leaf;
		this.leaf = value;
		return prior;
	}

	/**
	 * When there is only one and no branch is required.
	 * 
	 * @param <V>
	 */
	private static class Leaf<V> implements IPretrieLeaf<V> {
		private V leaf;

		public Leaf(final V leaf) {
			this.leaf = leaf;
		}

		@Override
		public V getValue() {
			return this.leaf;
		}

		@Override
		public V putValue(final V value) {
			final V prior = this.leaf;
			this.leaf = value;
			return prior;
		}
	}

	/**
	 * A node extends each primary prefix by 8 times;
	 * 
	 * @param <V>
	 */
	private static class Node<V> {
		private final Pretrie<V> parent;
		private final Element<V>[] element;

		@SuppressWarnings("unchecked")
		public Node(final Pretrie<V> parent) {
			this.parent = parent;
			this.element = (Element<V>[]) new Element[MINOR_SIZE];
		}

		V get(final Prefix key) {
			final int secondaryIndex = ((int) key.getCurrentByte())
					& MINOR_BITMASK;
			final Element<V> element = this.element[secondaryIndex];
			if (this.element[secondaryIndex] == null) {
				return this.parent.getValue();
			}
			final V match = element.get(key.increment());
			return (match == null) ? this.parent.getValue() : match;
		}

		V put(final Prefix prefix, final V value) {
			final int secondaryIndex = ((int) prefix.getCurrentByte())
					& MINOR_BITMASK;

			final Element<V> element;
			if (this.element[secondaryIndex] == null) {
				element = new Element<V>(prefix, this.parent);
				this.element[secondaryIndex] = element;
			} else {
				element = (Element<V>) this.element[secondaryIndex];
			}
			return element.put(prefix, value);
		}

		V put(final Prefix prefix, final IPretrieLeaf<V> leaf) {
			final int secondaryIndex = ((int) prefix.getCurrentByte())
					& MINOR_BITMASK;

			final Element<V> element = new Element<V>(prefix, this.parent);
			this.element[secondaryIndex] = element;

			return element.put(prefix, leaf);
		}
	}

	/**
	 * An element consists of the continuation of the prefix
	 * 
	 * @param <V>
	 */
	public static class Element<V> {
		private Prefix prefix;
		private IPretrieLeaf<V> leaf;

		public Element(final Prefix prefix, final Pretrie<V> value) {
			this.prefix = prefix;
			this.leaf = value;
		}

		V get(final Prefix key) {
			if (this.prefix.equals(key)) {
				return this.leaf.getValue();
			}
			return null;
		}

		/**
		 * Look for a place where there is no match. Split the element.
		 * 
		 * @param prefix
		 * @param position
		 * @param value
		 * @return
		 */
		V put(final Prefix prefix, final V value) {
			final int offset = this.prefix.matchOffset(prefix);
			if (offset < 0) {
				return this.leaf.getValue();
			}
			final Prefix replacement = this.prefix.trimOffEnd(offset);
			final Prefix right = this.prefix.trimOffStart(offset);
			final Prefix left = prefix.trimOffStart(offset);

			this.prefix = replacement;
			final Pretrie<V> branch = new Pretrie<V>(right, this.leaf);
			branch.put(left, value);
			this.leaf = branch;
			return null;
		}

		V put(final Prefix prefix, final IPretrieLeaf<V> leaf) {
			final int offset = this.prefix.matchOffset(prefix);
			if (offset < 0) {
				return this.leaf.getValue();
			}
			final Prefix replacement = this.prefix.trimOffEnd(offset);
			final Prefix right = this.prefix.trimOffStart(offset);
			final Prefix left = prefix.trimOffStart(offset);

			this.prefix = replacement;
			final Pretrie<V> branch = new Pretrie<V>(right, this.leaf);
			branch.put(left, leaf);
			this.leaf = branch;
			return null;
		}
	}

	/**
	 * Retrieve the match with the best prefix.
	 * 
	 * @param key
	 *            the key for which the best match is sought
	 * @return the best matching value
	 */
	public V get(final byte[] key) {
		return this.get(new Prefix(key));
	}

	private V get(final Prefix key) {
		final int primaryIndex = ((int) key.getCurrentByte()) >>> MAJOR_SHIFT;
		if (this.primary[primaryIndex] != null) {
			return this.primary[primaryIndex].get(key);
		}
		return this.getValue();
	}

	/**
	 * Is there a match for this prefix.
	 * 
	 * @param key
	 *            the key for which the best match is sought
	 * @return the best matching value
	 */
	public boolean containsKey(final byte[] key) {
		return this.get(key) != null;
	}

	/**
	 * Insert a new prefix and its value. The previous value of the value
	 * corresponding to the prefix is returned. If the value was not set null is
	 * returned.
	 * 
	 * @param prefix
	 * @param value
	 * @return did the insert succeed
	 */
	public V put(final byte[] prefix, final V value) {
		return this.put(new Prefix(prefix), value);
	}

	public V put(final Prefix prefix, final V value) {
		if (prefix.getLength() < 1) {
			this.leaf = value;
			return null;
		}
		final int primaryIndex = ((int) prefix.getCurrentByte()) >>> MAJOR_SHIFT;
		final Node<V> node;
		if (this.primary[primaryIndex] == null) {
			node = new Node<V>(this);
			this.primary[primaryIndex] = node;
		} else {
			node = this.primary[primaryIndex];
		}
		return node.put(prefix, value);
	}
	
	public V put(final Prefix prefix, final IPretrieLeaf<V> leaf) {
		return null;
	}

	public void putAll(Pretrie<? extends V> map) {

	}

	public V remove(final byte[] prefix) {
		return null;
	}

}

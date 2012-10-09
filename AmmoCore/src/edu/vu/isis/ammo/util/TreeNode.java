
package edu.vu.isis.ammo.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This tree implementation strives to be immutable.
 * 
 * @param <T>
 */
public class TreeNode<T> {

    final private TreeNode<T> parent;
    final private T data;
    final private List<TreeNode<T>> leaves;

    /**
     * The parent tree is supplied
     * 
     * @param parent
     * @param data
     */
    private TreeNode(TreeNode<T> parent, T data) {
        this.parent = parent;
        this.data = data;
        this.leaves = new ArrayList<TreeNode<T>>(5);
    }

    /**
     * This special tree class is used.
     * 
     * @param data
     * @return
     */
    public static <T> TreeNode<T> newTree(T data) {
        return new TreeNode<T>(null, data);
    }

    /**
     * Add a leaf to the current tree node's leaf set.
     * 
     * @param data
     * @return
     */
    public TreeNode<T> addLeaf(T data) {
        final TreeNode<T> leaf = new TreeNode<T>(this, data);
        this.leaves.add(leaf);
        return leaf;
    }

    /**
     * Get the object held by this tree node
     * 
     * @return
     */
    public T getData() {
        return this.data;
    }

    public TreeNode<T> getParent() {
        return this.parent;
    }

    public List<TreeNode<T>> getLeaves() {
        return this.leaves;
    }
    
    /**
     * Count the number of nodes in the tree.
     * 
     * @return
     */
    public int size() {
        return this.size(0);
    }
    private int size(int size) {
        for (TreeNode<T> leaf : this.leaves) {
            size = leaf.size(size);
        }
        size++;
        return size;
    }

    /**
     * Walk the tree transforming each node as indicated by the visitor.
     * 
     * @param tree
     * @return
     */

    public String toString(Vistor<T> visitor) {
        final StringBuilder builder = new StringBuilder();
        return this.toString(builder, visitor).toString();
    }

    private StringBuilder toString(final StringBuilder builder, Vistor<T> visitor) {
        if (visitor == null) { 
            builder.append(this.data.toString()); 
            if (this.leaves.size() > 0) {
                builder.append("/");
                for (TreeNode<T> leaf : this.leaves) {
                    leaf.toString(builder, visitor);
                }
                builder.append("\\");
            }
            return builder;
        }
        
        visitor.reform(builder, this.data);
        if (this.leaves.size() > 0) {
            visitor.up(builder);

            for (TreeNode<T> leaf : this.leaves) {
                leaf.toString(builder, visitor);
            }
            visitor.down(builder);
        }
        return builder;
    }

    /**
     * As the nodes are visited the string builder is updated
     * based on the tree navigation.
     * <p>
     * <code>
     * </code>
     *
     * @param <T>
     */
    public interface Vistor<T> {
        StringBuilder up(StringBuilder builder);

        StringBuilder down(StringBuilder builder);

        StringBuilder reform(StringBuilder builder, T data);
    }

}

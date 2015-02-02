/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */



package edu.vu.isis.ammo.util;

import java.util.ArrayList;
import java.util.List;

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

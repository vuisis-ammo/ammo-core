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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Genealogist is a collection of functions which can be used to find the
 * ancestry of a particular object. In general these functions should only be
 * used during logging or debugging.
 */
public class Genealogist {

    static final private Logger logger = LoggerFactory.getLogger("class.genealogist");

    /**
     * Given an object all of its ancestor classes are identified. At each level
     * all of the implemented interfaces are also identified.
     * 
     * @param object the object whose ancestors are sought
     * @return a list (as a tree) of all ancestors
     */
    public static TreeNode<Class<?>> getAncestry(Object object) {
        logger.trace("get ancestry for object {}", object.getClass().getName());
        return getAncestry(object.getClass());
    }

    public static TreeNode<Class<?>> getAncestry(Class<?> classObject) {
        logger.trace("get ancestry for class {}", classObject.getName());
        final TreeNode<Class<?>> tree = TreeNode.<Class<?>> newTree(classObject);
        return getAncestryAux(classObject, tree);
    }

    /**
     * First add a leaf with the parent class (if any). Then add all the
     * interfaces implemented by this class.
     * 
     * @param classObject
     * @param target
     * @return a list (as a tree) of ancestors
     */
    private static TreeNode<Class<?>> getAncestryAux(Class<?> classObject, TreeNode<Class<?>> target) {
        logger.trace("get ancestry for class {}", classObject.getName());
        final Class<?> superClass = classObject.getSuperclass();
        if (superClass != null) {
            final TreeNode<Class<?>> classBranch = target.addLeaf(superClass);
            getAncestryAux(superClass, classBranch);
        }

        final Class<?>[] superInterfaceArray = classObject.getInterfaces();
        for (Class<?> superInterface : superInterfaceArray) {
            final TreeNode<Class<?>> interfaceBranch = target.addLeaf(superInterface);
            getAncestryAux(superInterface, interfaceBranch);
        }
        return target;
    }

}


package edu.vu.isis.ammo.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * @param object
     * @return
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
     * @return
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

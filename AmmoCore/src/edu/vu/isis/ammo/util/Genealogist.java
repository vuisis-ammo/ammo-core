package edu.vu.isis.ammo.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Genealogist is a collection of functions which
 * can be used to find the ancestry of a particular object.
 * 
 * In general these functions should only be used 
 * during logging or debugging.
 *
 */
public class Genealogist {

    public static List<String> getAncestry(Object object) {
        final Set<Class<?>> ancestry = getAncestry(object.getClass());
        final List<String> ancestorNames = new ArrayList<String>(ancestry.size());
        for (Class<?> clazz : ancestry) {
            ancestorNames.add(clazz.getName());
        }
        return ancestorNames;
    }
    
    
    public static Set<Class<?>> getAncestry(Class<?> classObject) {
        final Set<Class<?>> ancestry = new HashSet<Class<?>>();
        ancestry.add(classObject);
        final Class<?> superClass = classObject.getSuperclass();
        if (superClass != null) {
            ancestry.addAll(getAncestry(superClass));
        }
        final Class<?>[] superInterfaceArray = classObject.getInterfaces();
        for (Class<?> superInterface : superInterfaceArray) {
            ancestry.addAll(getAncestry(superInterface));
        }
        return ancestry;
    }
}

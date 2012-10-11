
package edu.vu.isis.ammo.util;

public class ArrayUtils {
    static public int indexOf(byte[] data, byte match) {
        for (int ix = 0; ix < data.length; ix++) {
            if (data[ix] == match)
                return ix;
        }
        return -1;
    }
}

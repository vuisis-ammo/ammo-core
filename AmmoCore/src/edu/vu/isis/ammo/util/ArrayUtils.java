
package edu.vu.isis.ammo.util;

public class ArrayUtils {
    /**
     * Typically the indexOf method returns a -1 if 
     * the match byte is not found.
     * This method finds the end of the sub-array by a delimiter.
     * 
     * @param data
     * @param delimiter
     * @return
     */
    static public int indexOfDelimiter(byte[] data, byte delimiter) {
        for (int ix = 0; ix < data.length; ix++) {
            if (data[ix] == delimiter)
                return ix;
        }
        return data.length;
    }
}

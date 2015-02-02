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

import java.util.Arrays;

/**
 * This is a helper method for constructing hash codes. On its initial
 * invocation the work should be set to 0.
 * <p>
 * For example <code>
 *   work = incrementHash(0, part1.hashCode());
 *   work = incrementHash(work, part2.hashCode();
 *   work = incrementHash(work, part3.hashCode();
 *   ...
 *   work = incrementHash(work, part_N.hashCode();
 * </code>
 * <p>
 * This class is based on "Effective Java" Joshua Bloch Item 9 : Always override
 * hashcode when you override equals.
 * 
 * @param work the hash code in progress.
 * @param increment the hash code of the next element.
 * @return the candidate hash code
 */
public class HashBuilder {
    private int hashcode;

    /**
     * This method compares two object to see if they differ. null values are
     * allowed.
     * 
     * @param left
     * @param right
     * @return are the left and right objects different?
     */
    public static boolean differ(Object left, Object right) {
        if (left == right)
            return false;

        if (left == null)
            return true;
        if (left.equals(right))
            return false;

        return true;
    }

    private HashBuilder(int basecode) {
        this.hashcode = basecode;
    }

    public static HashBuilder newBuilder() {
        return new HashBuilder(17);
    }

    /**
     * Arrays are special.
     * 
     * @param nextcode
     * @return the modifed HashBuilder
     */
    public HashBuilder increment(Object nextcode) {
        this.hashcode *= 31;
        if (nextcode != null) {
            this.hashcode += nextcode.hashCode();
        }
        return this;
    }

    public HashBuilder increment(Object[] array) {
        return this.increment(Arrays.hashCode(array));
    }

    public HashBuilder increment(int nextcode) {
        this.hashcode *= 31;
        this.hashcode += nextcode;
        return this;
    }

    public HashBuilder increment(int[] array) {
        return this.increment(Arrays.hashCode(array));
    }

    public HashBuilder increment(boolean nextcode) {
        return this.increment((int) (nextcode ? 1 : 0));
    }

    public HashBuilder increment(boolean[] array) {
        return this.increment(Arrays.hashCode(array));
    }

    public HashBuilder increment(byte nextcode) {
        return this.increment((int) nextcode);
    }

    public HashBuilder increment(byte[] array) {
        return this.increment(Arrays.hashCode(array));
    }

    public HashBuilder increment(char nextcode) {
        return this.increment((int) nextcode);
    }

    public HashBuilder increment(char[] array) {
        return this.increment(Arrays.hashCode(array));
    }

    public HashBuilder increment(short nextcode) {
        return this.increment((int) nextcode);
    }

    public HashBuilder increment(short[] array) {
        return this.increment(Arrays.hashCode(array));
    }

    public HashBuilder increment(long nextcode) {
        return this.increment((int) (nextcode ^ (nextcode >>> 32)));
    }

    public HashBuilder increment(long[] array) {
        return this.increment(Arrays.hashCode(array));
    }

    public HashBuilder increment(float nextcode) {
        return this.increment((int) (Float.floatToIntBits(nextcode)));
    }

    public HashBuilder increment(float[] array) {
        return this.increment(Arrays.hashCode(array));
    }

    public HashBuilder increment(double nextcode) {
        return this.increment((long) (Double.doubleToLongBits(nextcode)));
    }

    public HashBuilder increment(double[] array) {
        return this.increment(Arrays.hashCode(array));
    }

    @Override
    public int hashCode() {
        return this.hashcode;
    }
}

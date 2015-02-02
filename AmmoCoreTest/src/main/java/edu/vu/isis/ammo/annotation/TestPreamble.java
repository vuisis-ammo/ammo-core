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


/**
 * 
 */

package edu.vu.isis.ammo.annotation;

/**
 * Used by the testing framework to determine which tests should be run and
 * when.
 * <p>
 * <code>
 * @TestPreamble ( triggers = {"smoke","full"}, </code>
 * <code>conception = "1.6.2" activate =
 *               "1.6.3", expire = "unlimited", units = {""} ) </code>
 *               <p>
 *               on* attributes indicate what triggers the test:
 */
public @interface TestPreamble {

    /** for any change to the system */
    boolean onSmoke() default true;

    /** for any change to one of the indicated units */
    String[] onUnit();

    /** for any change to one of the indicated components */
    String[] onComponent();

    /**
     * Indicates when the test is relevant. The test is not required to pass at
     * this point but is made available for developers to work against. In the
     * case of test driven development this indicates when
     */
    String conception() default "0.0.0";

    /**
     * Indicates when the test should become active. The test is expected to
     * pass starting at this version.
     */
    String activate() default "0.0.0";
    
    /**
     * Indicates when the item being tested is no longer expected to run.
     * Generally the test would fail if run
     */
    String deprecate() default "9999.0.0";
    
    /**
     * Indicates when the test no longer be invoked.
     * Generally the test would fail if run
     */
    String expire() default "9999.0.0";
}

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

package edu.vu.isis.ammo.core.distributor;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.AmmoService;
import android.content.Intent;
import android.os.IBinder;

/**
 */
abstract public class AmmoServiceTestLogger extends android.test.ServiceTestCase<AmmoService> {
    static final private Logger logger = LoggerFactory.getLogger("test.service.lifecycle");

    public AmmoServiceTestLogger(Class<AmmoService> serviceClass) {
        super(serviceClass);
    }

    @Override
    protected IBinder bindService(Intent intent) {
        logger.info("bind service {}", intent);
        return super.bindService(intent);
    }

    @Override
    protected void startService(Intent intent) {
        logger.info("start service {}", intent);
        super.startService(intent);
    }

    protected void setupService() {
        logger.info("setup service ");
        super.setupService();
    }

    // The following are implemented in JUnit 4.x
    /**
     * Asserts that two byte arrays are equal. If they are not, an
     * java.lang.AssertionError is thrown.
     * 
     * @param message the identifying message for the java.lang.AssertionError
     * @param expecteds byte array with expected values.
     * @param actuals byte array with actual values
     */

    public static void assertArrayEquals(String message, byte[] expected, byte[] actuals)
            throws AssertionError
    {
        if (expected == actuals) return;
        if (Arrays.equals(expected, actuals)) {
            return;
        }
        final StringBuilder sb = new StringBuilder(message);
        sb.append('\n').append(" expected={").append(Arrays.toString(expected)).append("}");
        sb.append('\n').append(" actuals={").append(Arrays.toString(actuals)).append("}");
        throw new AssertionError(sb.toString());
    }


}

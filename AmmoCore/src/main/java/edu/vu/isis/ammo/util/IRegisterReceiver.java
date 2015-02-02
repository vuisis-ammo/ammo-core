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

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * This is just a simple class which may be used to pass context around.
 * This can easily be done as a closure.
 * 
 * For example:
 * final IRegisterReceiver registerReceiver = new IRegisterReceiver() {
 *   @Override
 *   public Intent registerReceiver(final BroadcastReceiver aReceiver, final IntentFilter aFilter) {
 *     return applicationContext.registerReceiver(aReceiver, aFilter);
 *   }
 *   @Override
 *   public void unregisterReceiver(final BroadcastReceiver aReceiver) {
 *     applicationContext.unregisterReceiver(aReceiver);
 *   }
 * };
 * 
 * @author phreed
 *
 */
public interface IRegisterReceiver {
		public Intent registerReceiver(final BroadcastReceiver aReceiver, final IntentFilter aFilter);
		public void unregisterReceiver(final BroadcastReceiver aReceiver);
}

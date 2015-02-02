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

package edu.vu.isis.ammo.core.distributor;

import android.net.Uri;

/**
 * An exception to be raised when the content provider being used
 * does not conform to the ammo service expectations.
 *
 */
public class NonConformingAmmoContentProvider extends Exception {
	private static final long serialVersionUID = -1917539810288091287L;
	public final Uri providerUri;
	
	public NonConformingAmmoContentProvider(Uri providerUri) {
		super();
		this.providerUri = providerUri;
	}

	public NonConformingAmmoContentProvider(String detailMessage, Uri providerUri) {
		super(detailMessage);
		this.providerUri = providerUri;
	}

	public NonConformingAmmoContentProvider(Throwable throwable, Uri providerUri) {
		super(throwable);
		this.providerUri = providerUri;
	}

	public NonConformingAmmoContentProvider(String detailMessage, Throwable throwable, Uri providerUri) {
		super(detailMessage, throwable);
		this.providerUri = providerUri;
	}

}

/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
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

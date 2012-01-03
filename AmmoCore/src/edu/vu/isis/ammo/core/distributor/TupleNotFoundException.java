/*
Copyright(c) 2010-2012

This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under contract [contract citation, subcontract and prime contract]. 
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.

 */

package edu.vu.isis.ammo.core.distributor;

import android.net.Uri;

public class TupleNotFoundException extends Exception {
	private static final long serialVersionUID = 8194436089325314341L;
	public final Uri missingTupleUri;
	
	public TupleNotFoundException(String message, Uri missingTupleUri) {
		super(message);
		this.missingTupleUri = missingTupleUri;
	}
}

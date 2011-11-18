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

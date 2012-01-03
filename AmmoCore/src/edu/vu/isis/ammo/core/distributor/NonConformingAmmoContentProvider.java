package edu.vu.isis.ammo.core.distributor;

import android.net.Uri;

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

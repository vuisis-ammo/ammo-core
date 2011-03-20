package edu.vu.isis.ammo.core;

import android.view.View;

public interface OnStatusChangeListener {
	public boolean onStatusChange(View view, int status);
	public boolean onStatusChange(View view, int connStatus, int sendStatus, int recvStatus);
	public boolean onStatusChange(String itemName, int connStatus, int sendStatus, int recvStatus);
}

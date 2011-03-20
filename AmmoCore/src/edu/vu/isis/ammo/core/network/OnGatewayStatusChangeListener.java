package edu.vu.isis.ammo.core.network;

public interface OnGatewayStatusChangeListener {
	public void onStatusChanged(int connector, int sender, int receiver);
}

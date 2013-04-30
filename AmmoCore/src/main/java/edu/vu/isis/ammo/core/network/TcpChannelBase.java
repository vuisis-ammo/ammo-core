package edu.vu.isis.ammo.core.network;

import android.content.Context;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;

public abstract class TcpChannelBase extends NetChannel {

	protected TcpChannelBase(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}

	@Override
	public abstract void reset();

	@Override
	public abstract boolean isConnected();

	@Override
	public abstract void enable();

	@Override
	public abstract void disable();

	@Override
	public abstract boolean isBusy();

	@Override
	public abstract DisposalState sendRequest(AmmoGatewayMessage agm);

	@Override
	public abstract void init(Context context);

	@Override
	public abstract void toLog(String context);

	@Override
	public abstract void linkUp(String name);

	@Override
	public abstract void linkDown(String name);

	// needed for the TcpSecurityObject
	public abstract void authorizationSucceeded(AmmoGatewayMessage agm);

	public abstract void finishedPuttingFromSecurityObject();

	public abstract void putFromSecurityObject(AmmoGatewayMessage build);

}

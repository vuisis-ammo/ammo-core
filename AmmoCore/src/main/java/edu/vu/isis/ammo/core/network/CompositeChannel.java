package edu.vu.isis.ammo.core.network;

import android.content.Context;
import edu.vu.isis.ammo.core.distributor.Dispersal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;

/**
 * Channel that distributes calls to multiple channels.  This is useful
 * if you want to load balance across multiple channels of the same type.
 * TODO: Should this be built in to {@link Dispersal}?  I don't see a
 * way to do this easily as dispersal doesn't seem to allow variable or
 * match type terms (usb* or usb[0-4] or something)
 * 
 * @author mriley
 */
public class CompositeChannel<T extends INetChannel> implements INetChannel {
	
	public interface ChannelOp<T> {
		void perform( T channel );
	}

	private final T[] channels;
	private final String name;

	public CompositeChannel(String name, T ... channels) {
		if( name == null )
			throw new NullPointerException("Composite channel needs a name!");
		if( channels == null )
			throw new NullPointerException("Composite channel needs channels!");
		this.channels = channels;
		this.name = name;
	}
	
	public void perform( ChannelOp<T> op ) {
		for(T c : channels) op.perform(c);
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	public T[] getChannels() {
		return channels;
	}
	
	@Override
	public void reset() {
		for(INetChannel c : channels) 
			c.reset();
	}

	@Override
	public boolean isConnected() {
		for(INetChannel c : channels) {
			if( c.isConnected() ) 
				return true;
		}
		return false;
	}

	@Override
	public void enable() {
		for(INetChannel c : channels) 
			c.enable();
	}

	@Override
	public void disable() {
		for(INetChannel c : channels) 
			c.disable();		
	}

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public DisposalState sendRequest(AmmoGatewayMessage agm) {
		for(INetChannel c : channels) { 
			if( !c.isBusy() && c.isConnected() )
				return c.sendRequest(agm);
		}
		for(INetChannel c : channels) { 
			if( c.isConnected() )
				return c.sendRequest(agm);
		}
		return DisposalState.REJECTED;
	}

	@Override
	public boolean isAuthenticatingChannel() {
		for(INetChannel c : channels) 
			if( c.isAuthenticatingChannel() )
				return true;
		return false;
	}

	@Override
	public void init(Context context) {
		for(INetChannel c : channels) 
			c.init(context);
	}

	@Override
	public void toLog(String context) {
		for(INetChannel c : channels) 
			c.toLog(context);
	}

	@Override
	public String getSendReceiveStats() {
		StringBuilder s = new StringBuilder();
		for(INetChannel c : channels) { 
			String stats = c.getSendReceiveStats();
			if( stats != null ) {
				s.append(stats).append(" ");
			}
		}
		return s.toString();
	}

	@Override
	public String getSendBitStats() {
		StringBuilder s = new StringBuilder();
		for(INetChannel c : channels) { 
			String stats = c.getSendBitStats();
			if( stats != null ) {
				s.append(stats).append(" ");
			}
		}
		return s.toString();
	}

	@Override
	public String getReceiveBitStats() {
		StringBuilder s = new StringBuilder();
		for(INetChannel c : channels) { 
			String stats = c.getReceiveBitStats();
			if( stats != null ) {
				s.append(stats).append(" ");
			}
		}
		return s.toString();
	}

	@Override
	public void linkUp(String name) {
		for(INetChannel c : channels) 
			c.linkUp(name);
	}

	@Override
	public void linkDown(String name) {
		for(INetChannel c : channels) 
			c.linkDown(name);
	}
}

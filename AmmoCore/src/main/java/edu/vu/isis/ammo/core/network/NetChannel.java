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
package edu.vu.isis.ammo.core.network;

import edu.vu.isis.ammo.api.IAmmo;

public abstract class NetChannel implements INetChannel {

	protected static final boolean HEARTBEAT_ENABLED = true;

	protected static final int CONNECTION_RETRY_DELAY = 20 * 1000; // 20 seconds

    /*
     * The values that we are translating here could be made into an enum and
     * the translation to strings would be handled for us. However, using enums
     * introduces a large amount of overhead for packing states into parcels
     * since they have to be serialized.
     */
	public static String showState(int state) {

		switch (state) {
		case IAmmo.NetChannelState.PENDING:
			return "PENDING";
		case IAmmo.NetChannelState.EXCEPTION:
			return "EXCEPTION";

		case IAmmo.NetChannelState.CONNECTING:
			return "CONNECTING";
		case IAmmo.NetChannelState.CONNECTED:
			return "CONNECTED";

		case IAmmo.NetChannelState.BUSY:
			return "BUSY";
		case IAmmo.NetChannelState.READY:
			return "READY";

		case IAmmo.NetChannelState.DISCONNECTED:
			return "DISCONNECTED";
		case IAmmo.NetChannelState.STALE:
			return "STALE";
		case IAmmo.NetChannelState.LINK_WAIT:
			return "LINK_WAIT";

		case IAmmo.NetChannelState.WAIT_CONNECT:
			return "WAIT CONNECT";
		case IAmmo.NetChannelState.SENDING:
			return "SENDING";
		case IAmmo.NetChannelState.TAKING:
			return "TAKING";
		case IAmmo.NetChannelState.INTERRUPTED:
			return "INTERRUPTED";

		case IAmmo.NetChannelState.SHUTDOWN:
			return "SHUTDOWN";
		case IAmmo.NetChannelState.START:
			return "START";
		case IAmmo.NetChannelState.RESTART:
			return "RESTART";
		case IAmmo.NetChannelState.WAIT_RECONNECT:
			return "WAIT_RECONNECT";
		case IAmmo.NetChannelState.STARTED:
			return "STARTED";
		case IAmmo.NetChannelState.SIZED:
			return "SIZED";
		case IAmmo.NetChannelState.CHECKED:
			return "CHECKED";
		case IAmmo.NetChannelState.DELIVER:
			return "DELIVER";
		case IAmmo.NetChannelState.DISABLED:
			return "DISABLED";
		default:
			return "Undefined State [" + state + "]";
		}
	}

	// a string uniquely naming the channel
	final public String name;

	protected NetChannel(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return this.name;
	}

	@Override
	public boolean isAuthenticatingChannel() {
		return false;
	}

	@Override
	public String getSendReceiveStats() {
		return "";
	}


	protected int lastConnState = IAmmo.NetChannelState.PENDING;
	protected int lastSenderState = IAmmo.NetChannelState.PENDING;
	protected int lastReceiverState = IAmmo.NetChannelState.PENDING;

    protected volatile long mBytesSent = 0;
    protected volatile long mBytesRead = 0;

    protected volatile long mLastBytesSent = 0;
    protected volatile long mLastBytesRead = 0;

    protected static final int BPS_STATS_UPDATE_INTERVAL = 60; // seconds
    protected volatile long mBpsSent = 0;
    protected volatile long mBpsRead = 0;


	@Override
    public String getSendBitStats() {
        StringBuilder result = new StringBuilder();
        result.append( "S: " ).append( humanReadableByteCount(mBytesSent, true) );
        result.append( ", BPS:" ).append( mBpsSent );
        return result.toString();
    }
	
	public int getConnState() {
	    return lastConnState;
	}
	
	public int getSenderState() {
	    return lastSenderState;
	}
	
	public int getReceiverState() {
	    return lastReceiverState;
	}

	@Override
    public String getReceiveBitStats() {
        StringBuilder result = new StringBuilder();
        result.append( "R: " ).append( humanReadableByteCount(mBytesRead, true) );
        result.append( ", BPS:" ).append( mBpsRead );
        return result.toString();
    }


    private static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.2f %sB", bytes / Math.pow(unit, exp), pre);
    }
}

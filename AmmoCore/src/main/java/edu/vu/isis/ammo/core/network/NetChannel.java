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

public abstract class NetChannel implements INetChannel {

	protected static final boolean HEARTBEAT_ENABLED = true;

	protected static final int CONNECTION_RETRY_DELAY = 20 * 1000; // 20 seconds

	// The values in the INetChannel that we are translating here could
	// probably be made into an enum and the translation to strings
	// would be handled for us.
	public static String showState(INetChannel.State state) {

		switch (state) {
		case PENDING:
			return "PENDING";
		case EXCEPTION:
			return "EXCEPTION";

		case CONNECTING:
			return "CONNECTING";
		case CONNECTED:
			return "CONNECTED";

		case BUSY:
			return "BUSY";
		case READY:
			return "READY";

		case DISCONNECTED:
			return "DISCONNECTED";
		case STALE:
			return "STALE";
		case LINK_WAIT:
			return "LINK_WAIT";

		case WAIT_CONNECT:
			return "WAIT CONNECT";
		case SENDING:
			return "SENDING";
		case TAKING:
			return "TAKING";
		case INTERRUPTED:
			return "INTERRUPTED";

		case SHUTDOWN:
			return "SHUTDOWN";
		case START:
			return "START";
		case RESTART:
			return "RESTART";
		case WAIT_RECONNECT:
			return "WAIT_RECONNECT";
		case STARTED:
			return "STARTED";
		case SIZED:
			return "SIZED";
		case CHECKED:
			return "CHECKED";
		case DELIVER:
			return "DELIVER";
		case DISABLED:
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


	protected INetChannel.State lastConnState = INetChannel.State.PENDING;
	protected INetChannel.State lastSenderState = INetChannel.State.PENDING;
	protected INetChannel.State lastReceiverState =  INetChannel.State.PENDING;

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

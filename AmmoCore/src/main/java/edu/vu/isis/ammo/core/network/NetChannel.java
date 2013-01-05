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

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.api.AmmoIntents;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;

public abstract class NetChannel implements INetChannel {
	private static final Logger logger = LoggerFactory
			.getLogger("net.channel.base");

	protected static final boolean HEARTBEAT_ENABLED = true;

	protected static final int CONNECTION_RETRY_DELAY = 20 * 1000; // 20 seconds

	// The values in the INetChannel that we are translating here could
	// probably be made into an enum and the translation to strings
	// would be handled for us.
	public static String showState(int state) {

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

	protected int lastConnState = INetChannel.PENDING;
	protected int lastSenderState = INetChannel.PENDING;
	protected int lastReceiverState = INetChannel.PENDING;

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
		result.append("S: ").append(humanReadableByteCount(mBytesSent, true));
		result.append(", BPS:").append(mBpsSent);
		return result.toString();
	}

	@Override
	public String getReceiveBitStats() {
		StringBuilder result = new StringBuilder();
		result.append("R: ").append(humanReadableByteCount(mBytesRead, true));
		result.append(", BPS:").append(mBpsRead);
		return result.toString();
	}

	private static String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1)
				+ (si ? "" : "i");
		return String.format("%.2f %sB", bytes / Math.pow(unit, exp), pre);
	}

	protected NetworkInterface interfaceName = null;

	public void setNetworkInterfaceType(final Socket socket) {
		final InetAddress sourceAddr = socket.getLocalAddress();
		try {
			this.interfaceName = NetworkInterface.getByInetAddress(sourceAddr);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Check that the address specified belongs to this channel.
	 * 
	 * @param ip
	 * @return
	 */
	public boolean isMyLink(final InetAddress ip) {
		try {
			return (this.interfaceName.equals(NetworkInterface
					.getByInetAddress(ip)));
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Handle the intent to see if the channel needs to be updated.
	 * 
	 * @param context
	 * @param action
	 * @param aIntent
	 */
	public abstract void handleNetworkBroadcastIntent(final Context context,
			final String action, final Intent aIntent);

	public void handleNetworkBroadcastIntentImpl(final Context context,
			String action, Intent aIntent) {

		if (AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE.equals(action)) {
			logger.trace("Ether Link state changed");
			int state = aIntent.getIntExtra("state", 0);

			// Should we be doing this here?
			// It's not parallel with the wifi and 3G below.
			if (state != 0) {
				switch (state) {
				case AmmoIntents.LINK_UP:
					logger.trace("onReceive: Link UP {}", action);
					this.linkUp(null);
					break;
				case AmmoIntents.LINK_DOWN:
					logger.trace("onReceive: Link DOWN {}", action);
					this.linkDown(null);
					break;
				}
			}
		} else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {

		} else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
		} else if (WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION
				.equals(action)) {
			final WifiManager wm = (WifiManager) context
					.getSystemService(Context.WIFI_SERVICE);
			final WifiInfo wi = wm.getConnectionInfo();
			final int ipa = wi.getIpAddress();
			final byte[] iparray = BigInteger.valueOf(ipa).toByteArray();
			try {
				final InetAddress addr = InetAddress.getByAddress(iparray);
				if (this.isMyLink(addr)) {

					if (aIntent.getBooleanExtra(
							WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
						this.linkUp(action);
					} else {
						this.linkDown(action);
					}
				}
			} catch (UnknownHostException e) {
				logger.warn("bad host", e);
			}
			
		} else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
			logger.trace("WIFI state changed");

		} else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
			logger.trace("3G state changed");

		}
	}

}

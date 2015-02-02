/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.vu.isis.ammo.core.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import edu.vu.isis.ammo.INetDerivedKeys;

public class WifiNetlink extends Netlink {
	private WifiManager mWifiManager = null;
	private ConnectivityManager mConnManager = null;
	private Context mContext = null;

	private WifiNetlink(Context context) {
		super(context, "Wifi Netlink", "wifi");
		this.mContext = context;

		mWifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		mConnManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		updateStatus();
	}

	public static Netlink getInstance(Context context) {
		// initialize the gateway from the shared preferences
		return new WifiNetlink(context);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		// if ( key.equals(INetDerivedKeys.WIFI_PREF_DISABLED) )
		{
			// shouldUse(prefs);
		}
	}

	@Override
	public void updateStatus() {
		final int[] state = new int[1];

		if (!mWifiManager.isWifiEnabled()) {
			state[0] = NETLINK_DISCONNECTED;
			setLinkUp(false);
		} else {
			final NetworkInfo info = mConnManager
					.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

			switch (info.getDetailedState()) {
			case DISCONNECTED:
				state[0] = NETLINK_DISCONNECTED;
				setLinkUp(false);
				break;
			case IDLE:
				state[0] = NETLINK_IDLE;
				setLinkUp(false);
				break;
			case SCANNING:
				state[0] = NETLINK_SCANNING;
				setLinkUp(false);
				break;
			case CONNECTING:
				state[0] = NETLINK_CONNECTING;
				setLinkUp(false);
				break;
			case AUTHENTICATING:
				state[0] = NETLINK_AUTHENTICATING;
				setLinkUp(true);
				break;
			case OBTAINING_IPADDR:
				state[0] = NETLINK_OBTAINING_IPADDR;
				setLinkUp(true);
				break;
			case FAILED:
				state[0] = NETLINK_FAILED;
				setLinkUp(false);
				break;
			case CONNECTED:
				state[0] = NETLINK_CONNECTED;
				setLinkUp(true);
				break;
			/** not handled */
			case DISCONNECTING:
				break;
			case SUSPENDED:
				break;
			default:
				break;
			}
		}
		Editor editor = PreferenceManager.getDefaultSharedPreferences(
				this.mContext).edit();
		editor.putInt(INetDerivedKeys.WIFI_PREF_IS_ACTIVE, state[0]).commit();

		logger.info("Wifi: updating status to {}", state);
		setStatus(state);
	}

	public void initialize() {
	}

	public void teardown() {
	}
}

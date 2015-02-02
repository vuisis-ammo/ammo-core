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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.view.View;
import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.ui.util.ActivityEx;


public abstract class Netlink implements OnSharedPreferenceChangeListener
{
    public static final Logger logger = LoggerFactory.getLogger("ui.netlink");

    public static final int NETLINK_UP = 1;
    public static final int NETLINK_DOWN = 2;
    public static final int NETLINK_DISABLED = 3;
    public static final int NETLINK_DISCONNECTED = 4;
    public static final int NETLINK_IDLE = 5;
    public static final int NETLINK_SCANNING = 6;
    public static final int NETLINK_CONNECTING = 7;
    public static final int NETLINK_AUTHENTICATING = 8;
    public static final int NETLINK_OBTAINING_IPADDR = 9;
    public static final int NETLINK_FAILED = 10;
    public static final int NETLINK_CONNECTED = 11;
    public static final int NETLINK_SUSPENDED = 12;

    // does the operator wish to use this gateway?
    private boolean election;

    private void setElection(boolean pred) {
        this.election = pred;
        Editor editor = this.prefs.edit();
        editor.putBoolean(INetDerivedKeys.NET_CONN_PREF_SHOULD_USE, this.election);
        editor.commit();
    }
    public void enable() { this.setElection(true); }
    public void disable() { this.setElection(false); }
    public void toggle() { this.setElection(!this.election); }
    public boolean isEnabled() { return this.election; }

    // the user selected familiar name
    private final String name;
    public String getName() { return this.name; }

    private final String type;
    public String getType() { return this.type; }

    private int[] mStatus = new int[] {57}; // bogus default will be obvious if code is not working
    public synchronized int[] getStatus() { return mStatus; }
    public synchronized void setStatus( int[] status ) { mStatus = status; }

    public abstract void updateStatus();

    private boolean mIsLinkUp = false;
    public synchronized boolean isLinkUp() { return mIsLinkUp; }
    public synchronized void setLinkUp( boolean value ) { mIsLinkUp = value; }

    protected final SharedPreferences prefs;
    protected Context context;

    protected Netlink(Context context, String name, String type) {
        this.context = context;
        this.name = name;
        this.type = type;
        this.election = true;

        this.prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.election = this.prefs.getBoolean(INetPrefKeys.GATEWAY_DISABLED,
                                              INetPrefKeys.DEFAULT_GATEWAY_ENABLED);

        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public static Netlink getInstance(ActivityEx context) {
        logger.error("get instance not implemented");
        return null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.type).append(" ").append(this.election);
        return sb.toString();
    }

    protected View statusView;

    public abstract void initialize();
    public abstract void teardown();
}

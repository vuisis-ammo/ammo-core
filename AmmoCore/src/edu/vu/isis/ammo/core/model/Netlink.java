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
                                              INetPrefKeys.DEFAULT_GATEWAY_DISABLED);

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

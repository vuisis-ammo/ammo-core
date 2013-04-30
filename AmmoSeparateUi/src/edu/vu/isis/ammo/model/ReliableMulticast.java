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

package edu.vu.isis.ammo.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.network.NetChannel;

public class ReliableMulticast extends ModelChannel {

    public static String KEY = "ReliableMulticastUiChannel";
    boolean election = false;
    int[] status = null;
    String formalIP = "formal ip";
    String port = "port";

    protected ReliableMulticast(Context context, String name, NetChannel channel) {
        super(context, name);
        this.formalIP = this.prefs.getString(INetPrefKeys.RELIABLE_MULTICAST_HOST,
                INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_HOST);
        this.port = this.prefs.getString(INetPrefKeys.RELIABLE_MULTICAST_PORT,
                String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_PORT));
        this.election = !this.prefs.getBoolean(INetPrefKeys.RELIABLE_MULTICAST_DISABLED,
                INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_DISABLED);

        mNetChannel = channel;
    }

    private static ReliableMulticast instance = null;

    public static ReliableMulticast getInstance(Context context, NetChannel channel)
    {
        if (instance == null)
            instance = new ReliableMulticast(context, "ReliableMulticast Channel", channel);
        return instance;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_HOST)) {
            this.formalIP = this.prefs.getString(INetPrefKeys.RELIABLE_MULTICAST_HOST,
                    INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_HOST);
            callOnNameChange();
        } else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_PORT)) {
            this.port = this.prefs.getString(INetPrefKeys.RELIABLE_MULTICAST_PORT,
                    String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_PORT));
            callOnNameChange();
        }
    }

    @Override
    public void enable() {
        this.setElection(true);
    }

    @Override
    public void disable() {
        this.setElection(false);
    }

    @Override
    public void toggle() {
        this.setElection(!this.election);
    }

    // FIXME : Should this view only user interface be writing this value?
    private void setElection(boolean b)
    {
        this.election = b;
        Editor editor = this.prefs.edit();
        editor.putBoolean(INetPrefKeys.RELIABLE_MULTICAST_DISABLED, !this.election);
        editor.commit();
    }

    @Override
    public boolean isEnabled() {
        return this.election;
    }

    @Override
    public View getView(View row, LayoutInflater inflater) {

        row = inflater.inflate(edu.vu.isis.ammo.core.R.layout.reliable_multicast_item, null);

        TextView formal = (TextView) row.findViewById(R.id.reliable_multicast_formal);
        TextView name = (TextView) row.findViewById(R.id.reliable_multicast_name);
        ((TextView) row.findViewById(R.id.channel_type)).setText(ReliableMulticast.KEY);

        name.setText(this.name);
        formal.setText(this.formalIP + ":" + this.port);
        // set vals
        return row;
    }

    @Override
    public int[] getStatus() {
        return this.status;
    }

    @Override
    public void setStatus(int[] statusCode) {
        this.status = statusCode;
    }
}

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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.network.NetChannel;

public class Multicast extends ModelChannel {

    public static String KEY = "MulticastUiChannel";
    boolean election = false;
    int[] status = null;
    String formalIP = "formal ip";
    String port = "port";

    protected Multicast(Context context, String name, NetChannel channel) {
        super(context, name);
        this.formalIP = this.prefs.getString(INetPrefKeys.MULTICAST_HOST,
                INetPrefKeys.DEFAULT_MULTICAST_HOST);
        this.port = this.prefs.getString(INetPrefKeys.MULTICAST_PORT,
                String.valueOf(INetPrefKeys.DEFAULT_MULTICAST_PORT));
        this.election = !this.prefs.getBoolean(INetPrefKeys.MULTICAST_DISABLED,
                INetPrefKeys.DEFAULT_MULTICAST_ENABLED);

        mNetChannel = channel;
    }

    private static Multicast instance = null;

    public static Multicast getInstance(Context context, NetChannel channel) {
        if (instance == null)
            instance = new Multicast(context, "Multicast Channel", channel);
        return instance;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(INetPrefKeys.MULTICAST_HOST)) {
            this.formalIP = this.prefs.getString(INetPrefKeys.MULTICAST_HOST,
                    INetPrefKeys.DEFAULT_MULTICAST_HOST);
            callOnNameChange();
        } else if (key.equals(INetPrefKeys.MULTICAST_PORT)) {
            this.port = this.prefs.getString(INetPrefKeys.MULTICAST_PORT,
                    String.valueOf(INetPrefKeys.DEFAULT_MULTICAST_PORT));
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
        editor.putBoolean(INetPrefKeys.MULTICAST_DISABLED, !this.election);
        editor.commit();
    }

    @Override
    public boolean isEnabled() {
        return this.election;
    }

    @Override
    public View getView(View row, LayoutInflater inflater) {

        row = inflater.inflate(edu.vu.isis.ammo.core.R.layout.multicast_item, null);

        TextView formal = (TextView) row.findViewById(R.id.multicast_formal);
        TextView name = (TextView) row.findViewById(R.id.multicast_name);
        ((TextView) row.findViewById(R.id.channel_type)).setText(Multicast.KEY);

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

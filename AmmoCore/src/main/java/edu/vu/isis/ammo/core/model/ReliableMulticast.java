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

public class ReliableMulticast extends ModelChannel {

    public static String KEY = "ReliableMulticastUiChannel";
    boolean election = false;
    int[] status = null;
    String formalIP = "formal ip";
    String port = "port";
    private boolean isMediaChannel = false;
    private String media_port;

    public boolean isMediaChannel() {
      return isMediaChannel;
    }
    public void setMediaChannel(boolean isMediaChannel) {
      this.isMediaChannel = isMediaChannel;
    }
    
    protected ReliableMulticast(Context context, String name, NetChannel channel) {
        super(context, name);
        this.formalIP = this.prefs.getString(INetPrefKeys.RELIABLE_MULTICAST_HOST,
                INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_HOST);
        this.port = this.prefs.getString(INetPrefKeys.RELIABLE_MULTICAST_PORT,
                String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_PORT));
        this.media_port = this.prefs.getString(INetPrefKeys.RELIABLE_MULTICAST_MEDIA_PORT,
                String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_MEDIA_PORT));
        this.election = !this.prefs.getBoolean(INetPrefKeys.RELIABLE_MULTICAST_DISABLED,
                INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_ENABLED);

        mNetChannel = channel;
    }

    private static ReliableMulticast instance = null;

    public static ReliableMulticast getInstance(Context context, NetChannel channel)
    {
        if (instance == null)
            instance = new ReliableMulticast(context, "ReliableMulticast Channel", channel);
        return instance;
    }
    public static ReliableMulticast getMediaInstance(Context context, NetChannel channel) {
      // turn on the media channel flag 
      // initialize the gateway media from the shared preferences
      
      ReliableMulticast rm = new ReliableMulticast(context, "Reliable Multicast Media Channel", channel);
      rm.setMediaChannel(true);
      return rm;
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
        } else if (key.equals(INetPrefKeys.RELIABLE_MULTICAST_MEDIA_PORT)) {
            this.media_port = this.prefs.getString(INetPrefKeys.RELIABLE_MULTICAST_MEDIA_PORT,
                String.valueOf(INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_MEDIA_PORT));
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
        
        if (isMediaChannel) 
          formal.setText(this.formalIP + ":" + this.media_port);
        else 
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

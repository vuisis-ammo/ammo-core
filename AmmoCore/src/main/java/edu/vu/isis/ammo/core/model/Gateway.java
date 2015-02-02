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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.network.NetChannel;

/**
 * The Ammo core is responsible for distributing typed messages to gateways over
 * multiple links. The message may be sent to a single gateway from an ordered
 * set or to multiple gateways. Each gateway may be reachable over several
 * links. The core makes these choices based on policy. In a typical case the
 * policy for message types to gateways and gateways to links is established
 * during provisioning. In a future version this policy may be updated by an
 * existing trusted gateway publishing a new policy. The relationship of
 * applications to message types is established by the application. It will
 * certainly register its subscriptions and publications. From these it should
 * be possible to infer and register the data types which it may post or pull.
 * With this information in hand the core should be able to determine how
 * effectively the core can satiate the application's data hunger.
 * 
 */
public class Gateway extends ModelChannel {
    public static final Logger logger = LoggerFactory.getLogger("model.gateway");
    // does the operator wish to use this gateway?
    public static String KEY = "GatewayUiChannel";
    private boolean election;

    // FIXME : Should this view only user interface be writing this value?
    private void setElection(boolean pred) {
        this.election = pred;
        Editor editor = this.prefs.edit();
        editor.putBoolean(INetPrefKeys.GATEWAY_DISABLED, !this.election);
        editor.commit();
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

    @Override
    public boolean isEnabled() {
        return this.election;
    }

    // the formal name for this gateway,
    // in the case of a socket it is the "ip:<host ip>:<port>"

    private String host;
    private int port;

    public String getFormal() {
        StringBuilder sb = new StringBuilder();
        sb.append("ip:").append(this.host).append(":").append(this.port);
        return sb.toString();
    }

    private int[] mStatus = null;

    @Override
    public synchronized int[] getStatus() {
        return mStatus;
    }

    public synchronized void setStatus(int[] status) {
        mStatus = status;
    }

    private Gateway(Context context, String name, NetChannel channel) {
        super(context, name);

        this.host = this.prefs.getString(INetPrefKeys.GATEWAY_HOST,
                INetPrefKeys.DEFAULT_GATEWAY_HOST);
        this.port = Integer.valueOf(this.prefs.getString(INetPrefKeys.GATEWAY_PORT,
                String.valueOf(INetPrefKeys.DEFAULT_GATEWAY_PORT)));
        this.election = !this.prefs.getBoolean(INetPrefKeys.GATEWAY_DISABLED,
                INetPrefKeys.DEFAULT_GATEWAY_ENABLED);
        logger.trace("Gateway constructed with following from prefs: host={} port={} election={}",
                new Object[] {
                        host, port, election
                });

        mNetChannel = channel;
    }

    public static Gateway getInstance(Context context, NetChannel channel) {
        // initialize the gateway from the shared preferences
        logger.trace("{} asked for a new Gateway instance", new Throwable().getStackTrace()[1]);
        return new Gateway(context, "Gateway Channel", channel);
    }
    
    public static Gateway getMediaInstance(Context context, NetChannel channel) {
        // initialize the gateway media from the shared preferences
        logger.trace("{} asked for a new Gateway Media instance", new Throwable().getStackTrace()[1]);
        return new Gateway(context, "Gateway Media Channel", channel);
    }    

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.name).append(" = ").append(this.getFormal()).append(" ")
                .append(this.election);
        return sb.toString();
    }

    /**
     * When the name changes update the local variable and any user interface.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        logger.trace("Gateway onSharedPreferenceChanged called with key={}", key);
        if (key.equals(INetPrefKeys.GATEWAY_HOST)) {
            this.host = this.prefs.getString(INetPrefKeys.GATEWAY_HOST,
                    INetPrefKeys.DEFAULT_GATEWAY_HOST);
            callOnNameChange();
            logger.trace("Gateway host updated to {}", host);
            logger.trace("New gateway formal: {}", getFormal());
        } else if (key.equals(INetPrefKeys.GATEWAY_PORT)) {
            this.port = Integer.valueOf(this.prefs.getString(INetPrefKeys.GATEWAY_PORT,
                    String.valueOf(INetPrefKeys.DEFAULT_GATEWAY_PORT)));
            callOnNameChange();
            logger.trace("Gateway port updated to {}", port);
            logger.trace("New gateway formal: {}", getFormal());
        }
    }

    public View getView(View row, LayoutInflater inflater) {
        row = inflater.inflate(R.layout.gateway_item, null);

        TextView gateway_name = ((TextView) row.findViewById(R.id.gateway_name));
        ((TextView) row.findViewById(R.id.channel_type)).setText(Gateway.KEY);
        gateway_name.setText(this.getName());
        ((TextView) row.findViewById(R.id.gateway_formal)).setText(this.getFormal());
        return row;
    }
}

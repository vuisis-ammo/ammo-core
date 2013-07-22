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

public class Usb extends ModelChannel {
    public static final Logger logger = LoggerFactory.getLogger("model.usb");
    // does the operator wish to use this gateway?
    public static String KEY = "UsbUiChannel";
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

    private int port;

    public String getFormal() {
        StringBuilder sb = new StringBuilder();
        sb.append("port:").append(this.port);
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

    private Usb(Context context, String name, NetChannel channel) {
        super(context, name);
        this.port = Integer.valueOf(this.prefs.getString(INetPrefKeys.GATEWAY_PORT,
                String.valueOf(INetPrefKeys.DEFAULT_GATEWAY_PORT)));
        this.election = !this.prefs.getBoolean(INetPrefKeys.GATEWAY_DISABLED,
                INetPrefKeys.DEFAULT_GATEWAY_ENABLED);
        logger.trace("USB constructed with following from prefs: port={} election={}",
                new Object[] { port, election });

        mNetChannel = channel;
    }

    public static Usb getInstance(Context context, NetChannel channel) {
        // initialize the gateway from the shared preferences
        logger.trace("{} asked for a new Gateway instance", new Throwable().getStackTrace()[1]);
        return new Usb(context, "USB Channel", channel);
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
        if (key.equals(INetPrefKeys.GATEWAY_PORT)) {
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

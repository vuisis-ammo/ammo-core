package edu.vu.isis.ammo.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.network.NetworkService;

/**
 * The Ammo core is responsible for distributing
 * typed messages to gateways over multiple links.
 * The message may be sent to a single gateway
 * from an ordered set or to multiple gateways.
 * Each gateway may be reachable over several links.
 * The core makes these choices based on policy.
 *
 * In a typical case the policy for message types
 * to gateways and gateways to links is
 * established during provisioning.
 * In a future version this policy may be updated
 * by an existing trusted gateway publishing
 * a new policy.
 *
 * The relationship of applications to message
 * types is established by the application.
 * It will certainly register its subscriptions
 * and publications.
 * From these it should be possible to infer and
 * register the data types which it may post or pull.
 *
 * With this information in hand the core should
 * be able to determine how effectively the core
 * can satiate the application's data hunger.
 *
 * @author phreed
 */
public class Gateway extends Channel implements OnSharedPreferenceChangeListener {
    public static final Logger logger = LoggerFactory.getLogger(Gateway.class);
    // does the operator wish to use this gateway?
    public static String KEY = "GatewayUiChannel";
    private boolean election;

    private void setElection(boolean pred) {
        this.election = pred;
        Editor editor = this.prefs.edit();
        editor.putBoolean(INetPrefKeys.GATEWAY_SHOULD_USE, this.election);
        editor.commit();
    }
    
    @Override
    public void enable() { this.setElection(true); }
    
    @Override
    public void disable() { this.setElection(false); }
    
    @Override
    public void toggle() { this.setElection(!this.election); }
    
    @Override
    public boolean isEnabled() { return this.election; }


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
    public synchronized int[] getStatus() { return mStatus; }
    public synchronized void setStatus( int[] status ) { mStatus = status; }

    private Gateway(Context context, String name) {
    	super(context, name);
    	
        this.host = this.prefs.getString(INetPrefKeys.CORE_IP_ADDR, NetworkService.DEFAULT_GATEWAY_HOST);
        this.port = Integer.valueOf(this.prefs.getString(INetPrefKeys.CORE_IP_PORT,
                String.valueOf(NetworkService.DEFAULT_GATEWAY_PORT)));
        this.election = this.prefs.getBoolean(INetPrefKeys.GATEWAY_SHOULD_USE, true);
    }


    public static Gateway getInstance(Context context) {
        // initialize the gateway from the shared preferences
        return new Gateway(context, "Default Gateway");
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.name).append(" = ").append(this.getFormal()).append(" ").append(this.election);
        return sb.toString();
    }

    private OnNameChangeListener nameListener;
    private View nameView;

    public void setOnNameChangeListener(OnNameChangeListener listener, View view) {
        logger.trace("set on name change listener");
        this.nameListener = listener;
        this.nameView = view;
    }

    /**
     * When the name changes update the local variable and any user interface.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        logger.trace("pref change: {}", key);
        if (key.equals(INetPrefKeys.CORE_IP_ADDR)) {
            if (this.nameView == null) return;
            this.host = this.prefs.getString(INetPrefKeys.CORE_IP_ADDR, NetworkService.DEFAULT_GATEWAY_HOST);
            this.nameListener.onFormalChange(this.nameView, this.getFormal());
            return;
        }
        if (key.equals(INetPrefKeys.CORE_IP_PORT)) {
            if (this.nameView == null) return;
            this.port = Integer.valueOf(this.prefs.getString(INetPrefKeys.CORE_IP_PORT,
                    String.valueOf(NetworkService.DEFAULT_GATEWAY_PORT)));
            this.nameListener.onFormalChange(this.nameView, this.getFormal());
            return;
        }
    }
    
    public View getView(View row, LayoutInflater inflater)
    {
        
        row = inflater.inflate(R.layout.gateway_item, null);
        
        TextView gateway_name = ((TextView)row.findViewById(R.id.gateway_name));
        ((TextView)row.findViewById(R.id.channel_type)).setText(Gateway.KEY);
        gateway_name.setText(this.getName());
        ((TextView)row.findViewById(R.id.gateway_formal)).setText(this.getFormal());
		return row;
    	
    }
}


package edu.vu.isis.ammoui;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammoui.util.DefaultPrefs;

public class GatewayPreferenceActivity extends AmmoPreferenceActivity {

    private static final String IP_FRAG_TAG = "ipFrag";

    private static final String PORT_FRAG_TAG = "portFrag";

    private static final String NET_TIMEOUT_FRAG_TAG = "netTimeoutFrag";

    private static final String CONNECTION_IDLE_FRAG_TAG = "connectionIdleFrag";

    private static final String TIME_TO_LIVE_FRAG_TAG = "timeToLiveFrag";

    private static final String ENABLE_FRAG_TAG = "enableFrag";

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        FragmentManager fm = getSupportFragmentManager();

        if (args == null) {
            AmmoPreferenceFragment<String> ipFrag;
            AmmoPreferenceFragment<Integer> portFrag, netTimeoutFrag, connectionIdleFrag, timeToLiveFrag;
            BooleanPreferenceFragment enableFrag = BooleanPreferenceFragment.newInstance("Gateway",
                    INetPrefKeys.GATEWAY_DISABLED, false);
            ipFrag = AmmoPreferenceFragment.newInstance("IP Address", INetPrefKeys.GATEWAY_HOST,
                    DefaultPrefs.GATEWAY_IP);
            portFrag = AmmoPreferenceFragment.newInstance("Port", INetPrefKeys.GATEWAY_PORT, DefaultPrefs.GATEWAY_PORT);

            // XXX: Check on these pref keys, they don't match the names of the
            // prefs
            netTimeoutFrag = AmmoPreferenceFragment.newInstance("Network Connection Timeout (s)",
                    INetPrefKeys.GATEWAY_TIMEOUT, DefaultPrefs.GATEWAY_NET_CONN_TIMEOUT);
            connectionIdleFrag = AmmoPreferenceFragment.newInstance("Connection Idle Timeout (s)",
                    INetPrefKeys.GATEWAY_FLAT_LINE_TIME, DefaultPrefs.GATEWAY_CONN_IDLE_TIMEOUT);

            ipFrag.setRetainInstance(true);
            portFrag.setRetainInstance(true);
            netTimeoutFrag.setRetainInstance(true);
            connectionIdleFrag.setRetainInstance(true);
            enableFrag.setRetainInstance(true);

            ipFrag.setSendToPP(true);

            fm.beginTransaction().add(R.id.ammo_pref_container, enableFrag, ENABLE_FRAG_TAG)
                    .add(R.id.ammo_pref_container, ipFrag, IP_FRAG_TAG)
                    .add(R.id.ammo_pref_container, portFrag, PORT_FRAG_TAG)
                    .add(R.id.ammo_pref_container, netTimeoutFrag, NET_TIMEOUT_FRAG_TAG)
                    .add(R.id.ammo_pref_container, connectionIdleFrag, CONNECTION_IDLE_FRAG_TAG)
                    .commit();
        }
    }

}

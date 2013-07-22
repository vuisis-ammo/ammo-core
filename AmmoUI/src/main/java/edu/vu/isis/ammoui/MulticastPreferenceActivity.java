
package edu.vu.isis.ammoui;

import edu.vu.isis.ammo.INetPrefKeys;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;

public class MulticastPreferenceActivity extends AmmoPreferenceActivity {

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
            BooleanPreferenceFragment enableFrag = BooleanPreferenceFragment.newInstance(
                    "Multicast", INetPrefKeys.MULTICAST_DISABLED, false);
            ipFrag = AmmoPreferenceFragment.newInstance("IP Address", INetPrefKeys.MULTICAST_HOST,
                    "Unknown");
            portFrag = AmmoPreferenceFragment.newInstance("Port", INetPrefKeys.MULTICAST_PORT, -1);
            netTimeoutFrag = AmmoPreferenceFragment.newInstance("Network Connection Timeout (s)",
                    INetPrefKeys.MULTICAST_NET_CONN_TIMEOUT, -1);
            connectionIdleFrag = AmmoPreferenceFragment.newInstance("Connection Idle Timeout (s)",
                    INetPrefKeys.MULTICAST_CONN_IDLE_TIMEOUT, -1);
            timeToLiveFrag = AmmoPreferenceFragment.newInstance("Time To Live (s)",
                    INetPrefKeys.MULTICAST_TTL, -1);

            ipFrag.setRetainInstance(true);
            portFrag.setRetainInstance(true);
            netTimeoutFrag.setRetainInstance(true);
            connectionIdleFrag.setRetainInstance(true);
            timeToLiveFrag.setRetainInstance(true);
            enableFrag.setRetainInstance(true);
            
            ipFrag.setSendToPP(true);

            fm.beginTransaction().add(R.id.ammo_pref_container, enableFrag, ENABLE_FRAG_TAG)
                    .add(R.id.ammo_pref_container, ipFrag, IP_FRAG_TAG)
                    .add(R.id.ammo_pref_container, portFrag, PORT_FRAG_TAG)
                    .add(R.id.ammo_pref_container, netTimeoutFrag, NET_TIMEOUT_FRAG_TAG)
                    .add(R.id.ammo_pref_container, connectionIdleFrag, CONNECTION_IDLE_FRAG_TAG)
                    .add(R.id.ammo_pref_container, timeToLiveFrag, TIME_TO_LIVE_FRAG_TAG).commit();
        }
    }
}

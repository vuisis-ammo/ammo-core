package edu.vu.isis.ammoui;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.View.OnClickListener;
import edu.vu.isis.ammo.AmmoPreferenceReadOnlyAccess;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoPreference;


public class SerialPreferenceActivity extends AmmoPreferenceActivity {
    
    private static final String IP_FRAG_TAG = "ipFrag";

    private static final String PORT_FRAG_TAG = "portFrag";

    private static final String NET_TIMEOUT_FRAG_TAG = "netTimeoutFrag";

    private static final String CONNECTION_IDLE_FRAG_TAG = "connectionIdleFrag";

    private static final String TIME_TO_LIVE_FRAG_TAG = "timeToLiveFrag";

    private static final String ENABLE_FRAG_TAG = "enableFrag";

    private static final String FRAGMENTATION_DELAY_FRAG_TAG = "fragDelayFrag";

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        FragmentManager fm = getSupportFragmentManager();

        if (args == null) {
            AmmoPreferenceFragment<String> deviceFrag;
            AmmoPreferenceFragment<Integer> baudRateFrag, slotNumberFrag, connectionIdleFrag, slotDurationFrag, transmitDurationFrag;
            BooleanPreferenceFragment enableFrag = BooleanPreferenceFragment.newInstance(
                    "Serial", INetPrefKeys.SERIAL_DISABLED, false);
            deviceFrag = AmmoPreferenceFragment.newInstance("Device",
                    INetPrefKeys.SERIAL_DEVICE, "Unknown");
            baudRateFrag = AmmoPreferenceFragment.newInstance("Baud Rate",
                    INetPrefKeys.SERIAL_BAUD_RATE, -1);
            slotNumberFrag = AmmoPreferenceFragment.newInstance("Slot Number",
                    INetPrefKeys.SERIAL_SLOT_NUMBER, -1);
            connectionIdleFrag = AmmoPreferenceFragment.newInstance("Radios in Group",
                    INetPrefKeys.SERIAL_RADIOS_IN_GROUP, -1);
            slotDurationFrag = AmmoPreferenceFragment.newInstance("Slot Duration (ms)",
                    INetPrefKeys.SERIAL_SLOT_DURATION, -1);
            transmitDurationFrag = AmmoPreferenceFragment.newInstance("Transmit Duration (ms)",
                    INetPrefKeys.SERIAL_TRANSMIT_DURATION, -1);

            deviceFrag.setRetainInstance(true);
            baudRateFrag.setRetainInstance(true);
            slotNumberFrag.setRetainInstance(true);
            connectionIdleFrag.setRetainInstance(true);
            slotDurationFrag.setRetainInstance(true);
            enableFrag.setRetainInstance(true);
            transmitDurationFrag.setRetainInstance(true);

            deviceFrag.setSendToPP(true);
            slotDurationFrag.setSendToPP(true);
            transmitDurationFrag.setSendToPP(true);
            
            enableFrag.setOnClickListener(new OnClickListener() {
                
                @Override
                public void onClick(View v) {
                    
                }
            });

            fm.beginTransaction().add(R.id.ammo_pref_container, enableFrag, ENABLE_FRAG_TAG)
                    .add(R.id.ammo_pref_container, deviceFrag, IP_FRAG_TAG)
                    .add(R.id.ammo_pref_container, baudRateFrag, PORT_FRAG_TAG)
                    .add(R.id.ammo_pref_container, slotNumberFrag, NET_TIMEOUT_FRAG_TAG)
                    .add(R.id.ammo_pref_container, connectionIdleFrag, CONNECTION_IDLE_FRAG_TAG)
                    .add(R.id.ammo_pref_container, slotDurationFrag, TIME_TO_LIVE_FRAG_TAG)
                    .add(R.id.ammo_pref_container, transmitDurationFrag, FRAGMENTATION_DELAY_FRAG_TAG).commit();
        }
    }

}

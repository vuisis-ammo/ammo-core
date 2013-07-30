package edu.vu.isis.ammoui;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammoui.util.DefaultPrefs;


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
            SerialEnableFragment enableFrag = SerialEnableFragment.newInstance();
            deviceFrag = AmmoPreferenceFragment.newInstance("Device",
                    INetPrefKeys.SERIAL_DEVICE, DefaultPrefs.SERIAL_DEVICE);
            baudRateFrag = AmmoPreferenceFragment.newInstance("Baud Rate",
                    INetPrefKeys.SERIAL_BAUD_RATE, DefaultPrefs.SERIAL_BAUD_RATE);
            slotNumberFrag = AmmoPreferenceFragment.newInstance("Slot Number",
                    INetPrefKeys.SERIAL_SLOT_NUMBER, DefaultPrefs.SERIAL_SLOT_NUMBER);
            connectionIdleFrag = AmmoPreferenceFragment.newInstance("Radios in Group",
                    INetPrefKeys.SERIAL_RADIOS_IN_GROUP, DefaultPrefs.SERIAL_RADIOS_IN_GROUP);
            slotDurationFrag = AmmoPreferenceFragment.newInstance("Slot Duration (ms)",
                    INetPrefKeys.SERIAL_SLOT_DURATION, DefaultPrefs.SERIAL_SLOT_DURATION);
            transmitDurationFrag = AmmoPreferenceFragment.newInstance("Transmit Duration (ms)",
                    INetPrefKeys.SERIAL_TRANSMIT_DURATION, DefaultPrefs.SERIAL_TRANSMIT_DURATION);

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

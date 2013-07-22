
package edu.vu.isis.ammoui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import edu.vu.isis.ammo.AmmoPreferenceReadOnlyAccess;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoPreference;

public class SerialEnableFragment extends BooleanPreferenceFragment {

    private static final Logger logger = LoggerFactory.getLogger("fragment.pref.boolean");

    public static SerialEnableFragment newInstance(String prefName, String prefKey,
            boolean defaultVal) {
        if (prefKey == null) {
            throw new IllegalArgumentException("You must provide a key");
        }
        SerialEnableFragment f = new SerialEnableFragment();
        Bundle args = new Bundle();
        args.putString(PREF_KEY_KEY, prefKey);
        args.putString(PREF_NAME_KEY, prefName);
        f.setArguments(args);
        f.setSendToPP(true);
        f.mDefault = defaultVal;
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
        View view = super.onCreateView(inflater, container, icicle);
        this.setPrefHint("Tap to toggle");
        this.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                AmmoPreference pref = AmmoPreference.getInstance(getActivity());
                boolean setting = !pref.getBoolean(INetPrefKeys.SERIAL_DISABLED, true);
                try {
                    pref.putBoolean(INetPrefKeys.SERIAL_DISABLED, setting);
                } catch (AmmoPreferenceReadOnlyAccess e) {
                    e.printStackTrace();
                }
                setViews(setting);
            }
        });

        return view;
    }
}

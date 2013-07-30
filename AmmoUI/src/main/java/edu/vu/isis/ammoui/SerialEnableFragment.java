
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

    private static final Logger logger = LoggerFactory.getLogger("fragment.pref.serialenable");

    public static SerialEnableFragment newInstance() {
        SerialEnableFragment f = new SerialEnableFragment();
        Bundle args = new Bundle();
        args.putString(PREF_KEY_KEY, INetPrefKeys.SERIAL_DISABLED);
        args.putString(PREF_NAME_KEY, "Serial");
        f.setArguments(args);
        f.mDefault = false;
        f.mTrueName = "Serial is enabled.";
        f.mFalseName = "Serial is disabled.";
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
        View view = super.onCreateView(inflater, container, icicle);
        this.setPrefHint("Tap to toggle");
        this.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                boolean setting = !mAmmoPref.getBoolean(INetPrefKeys.SERIAL_DISABLED, true);
                try {
                    mAmmoPref.putBoolean(INetPrefKeys.SERIAL_DISABLED, setting);
                } catch (AmmoPreferenceReadOnlyAccess e) {
                    e.printStackTrace();
                }
                readPref();
                refreshViews();
            }
        });

        return view;
    }
}

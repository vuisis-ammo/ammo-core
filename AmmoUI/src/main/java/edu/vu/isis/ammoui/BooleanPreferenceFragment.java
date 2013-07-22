package edu.vu.isis.ammoui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

public class BooleanPreferenceFragment extends AmmoPreferenceFragment<Boolean> {
    
    private static final Logger logger = LoggerFactory.getLogger("fragment.pref.boolean");
    
    public static BooleanPreferenceFragment newInstance(String prefName, String prefKey, boolean defaultVal) {
        if (prefKey == null) {
            throw new IllegalArgumentException("You must provide a key");
        }
        BooleanPreferenceFragment f = new BooleanPreferenceFragment();
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
        this.setPrefHint("Tap to change in Panthr Prefs");
        setViews(!mValue);
        return view;
    }
    
    protected void setViews(boolean value) {
        // This has to be negated because the preference stores whether
        // the channel is disabled, so when the preference is true, the 
        // channel is disabled.
        if (!mValue) {
            this.setPrefName(mPrefName + " is enabled.");
            this.setImageResource(R.drawable.green_dot);
        } else {
            this.setPrefName(mPrefName + " is disabled.");
            this.setImageResource(R.drawable.red_dot);
        }
    }

}

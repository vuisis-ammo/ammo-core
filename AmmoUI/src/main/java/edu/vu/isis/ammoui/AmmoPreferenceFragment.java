
package edu.vu.isis.ammoui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.api.AmmoPreference;
import edu.vu.isis.ammoui.util.SendToPanthrPrefsListener;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

public class AmmoPreferenceFragment<T> extends Fragment {
    private static final Logger logger = LoggerFactory.getLogger("fragment.pref.ammo");

    // Bundle argument keys
    protected static final String PREF_NAME_KEY = "prefName";

    protected static final String PREF_KEY_KEY = "prefKey";

    protected String mPrefName, mPrefKey;

    protected T mDefault;

    /** The current value of the preference */
    protected T mValue;

    protected Integer mImageResId;

    protected View mView;

    protected TextView mPrefNameTv, mPrefHintTv;

    protected ImageView mPrefIv;

    /**
     * Whether this preference fragment should send the user to Panthr Prefs
     * when clicked
     */
    protected boolean mSendToPP = false;

    protected View.OnClickListener mClickListener;

    public AmmoPreferenceFragment() {
    }

    public static <T> AmmoPreferenceFragment<T> newInstance(String prefName, String prefKey,
            T defaultVal) {
        if (prefKey == null) {
            throw new IllegalArgumentException("You must provide a key");
        }
        AmmoPreferenceFragment<T> f = new AmmoPreferenceFragment<T>();
        Bundle args = new Bundle();
        args.putString(PREF_NAME_KEY, prefName);
        args.putString(PREF_KEY_KEY, prefKey);
        f.setArguments(args);

        f.mDefault = defaultVal;

        return f;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Bundle args = getArguments();
        mPrefName = args.getString(PREF_NAME_KEY);
        mPrefKey = args.getString(PREF_KEY_KEY);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
        mView = inflater.inflate(R.layout.ammo_preference_fragment, container, false);
        logger.trace("mView: {}", mView);

        mPrefHintTv = (TextView)mView.findViewById(R.id.preference_hint_tv);
        mPrefNameTv = (TextView)mView.findViewById(R.id.preference_name_tv);
        mPrefIv = (ImageView)mView.findViewById(R.id.preference_image);

        final AmmoPreference ammoPref = AmmoPreference.getInstance(getActivity());
        readPref(ammoPref);

        mPrefNameTv.setText(mPrefName);
        if (mImageResId != null) {
            mPrefIv.setImageResource(mImageResId);
        }

        if (mClickListener != null) {
            mView.setOnClickListener(mClickListener);
        } else if (mSendToPP) {
            mView.setOnClickListener(new SendToPanthrPrefsListener(getActivity()));
        } else {
            // Use default click listener that pops up a dialog for a value
            mView.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    final EditText input = new EditText(getActivity());
                    input.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                    
                    AlertDialog.Builder bldr = new AlertDialog.Builder(getActivity());
                    bldr.setMessage("Enter a value for this preference").setTitle(mPrefName);
                    bldr.setView(input);
                    bldr.setPositiveButton("OK", new OnClickListener() {
                        
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            setPref(input.getText().toString(), ammoPref);
                            readPref(ammoPref);
                            dialog.dismiss();
                        }
                    });
                    bldr.setNegativeButton("Cancel", new OnClickListener() {
                        
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // close the dialog
                            dialog.cancel();
                        }
                    });
                    
                    bldr.create().show();
                }
            });
        }
        return mView;
    }

    @Override
    public void onActivityCreated(Bundle icicle) {
        super.onActivityCreated(icicle);
    }

    public void setOnClickListener(View.OnClickListener ocl) {
        mClickListener = ocl;
        if (mView != null) {
            mView.setOnClickListener(ocl);
        }
    }

    public void setImageResource(int resId) {
        mImageResId = resId;
        if (mPrefIv != null) {
            mPrefIv.setImageResource(mImageResId);
        }
    }

    public void setPrefName(String name) {
        mPrefName = name;
        if (mPrefNameTv != null) {
            mPrefNameTv.setText(name);
        }
    }

    public void setPrefHint(String hint) {
        if (mPrefHintTv != null) {
            mPrefHintTv.setText(hint);
        }
    }

    public void disable() {
        mView.setEnabled(false);
    }

    public void enable() {
        mView.setEnabled(true);
    }
    
    public void setSendToPP(boolean val) {
        mSendToPP = val;
    }

    @SuppressWarnings("unchecked")
    private void readPref(AmmoPreference ammoPref) {
        if (mDefault instanceof String) {
            mValue = (T)ammoPref.getString(mPrefKey, (String)mDefault);
        } else if (mDefault instanceof Boolean) {
            mValue = (T)Boolean.valueOf(ammoPref.getBoolean(mPrefKey, (Boolean)mDefault));
        } else if (mDefault instanceof Long) {
            mValue = (T)Long.valueOf(ammoPref.getLong(mPrefKey, (Long)mDefault));
        } else if (mDefault instanceof Integer) {
            mValue = (T)Integer.valueOf(ammoPref.getInt(mPrefKey, (Integer)mDefault));
        } else if (mDefault instanceof Float) {
            mValue = (T)Float.valueOf(ammoPref.getFloat(mPrefKey, (Float)mDefault));
        } else {
            throw new IllegalArgumentException(
                    "Default value must be a String, Boolean, Long, Integer, or Float");
        }
        mPrefHintTv.setText(mValue.toString() + (mSendToPP ? "  (Set in Panthr Prefs)" : ""));
    }
    
    private void setPref(String prefVal, AmmoPreference ammoPref) {}

}

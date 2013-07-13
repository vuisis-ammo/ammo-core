package edu.vu.isis.ammoui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class AmmoPreferenceFragment extends Fragment {
        private static final Logger logger = LoggerFactory.getLogger("fragment.pref");
        
        // Bundle argument keys
        private static final String PREF_NAME_KEY = "prefName";
        private static final String PREF_HINT_KEY = "prefHint";

        private String mPrefName, mPrefHint;
        private Integer mImageResId;
        private View mView;
        private TextView mPrefNameTv, mPrefHintTv;
        private ImageView mPrefIv;
        private View.OnClickListener mClickListener;
        
        public AmmoPreferenceFragment() { }

        public static AmmoPreferenceFragment newInstance(String prefName, String prefHint) {
            AmmoPreferenceFragment f = new AmmoPreferenceFragment();
            Bundle args = new Bundle();
            args.putString(PREF_NAME_KEY, prefName);
            args.putString(PREF_HINT_KEY, prefHint);
            f.setArguments(args);
            return f;
        }

        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            Bundle args = getArguments();
            mPrefName = args.getString(PREF_NAME_KEY);
            mPrefHint = args.getString(PREF_HINT_KEY);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
            View mView = inflater.inflate(R.layout.ammo_preference_fragment, container, false);
            
            mPrefHintTv = (TextView) mView.findViewById(R.id.preference_hint_tv);
            mPrefNameTv = (TextView) mView.findViewById(R.id.preference_name_tv);
            mPrefIv = (ImageView) mView.findViewById(R.id.preference_image);
            
            mPrefHintTv.setText(mPrefHint);
            mPrefNameTv.setText(mPrefName);
            if (mImageResId != null) {
                mPrefIv.setImageResource(mImageResId);
            }
            
            if (mClickListener != null) {
                mView.setOnClickListener(mClickListener);
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
        }
        
        public void setPrefHint(String hint) {
            mPrefHint = hint;
            if (mPrefHintTv != null) {
                mPrefHintTv.setText(hint);
            }
        }

}

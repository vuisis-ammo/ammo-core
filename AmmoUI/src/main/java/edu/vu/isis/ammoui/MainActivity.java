
package edu.vu.isis.ammoui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.provider.ChannelSchema;
import edu.vu.isis.ammoui.util.UiUtils;

public class MainActivity extends FragmentActivity implements ChannelSchema {

    private static final Logger logger = LoggerFactory.getLogger("ui.MainActivity");

    private static final int MULTICAST_LOADER_ID = 1;
    private static final int RELIABLE_MULTICAST_LOADER_ID = 2;
    private static final int GATEWAY_LOADER_ID = 3;
    private static final int GATEWAY_MEDIA_LOADER_ID = 4;
    private static final int SERIAL_LOADER_ID = 5;

    private static final String MULTICAST_FRAGMENT_TAG = "MulticastFrag";
    private static final String RELIABLE_MULTICAST_FRAGMENT_TAG = "ReliableMulticastFrag";
    private static final String GATEWAY_FRAGMENT_TAG = "GatewayFrag";
    private static final String GATEWAY_MEDIA_FRAGMENT_TAG = "GatewayMediaFrag";
    private static final String SERIAL_FRAGMENT_TAG = "SerialFrag";
    
    private TextView mOperatorTv;
    private Button mViewTablesButton, mDebuggingToolsButton, mLoggingToolsButton,
                    mHardResetButton, mHelpAboutButton;
    
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ammo_activity);

        ChannelFragment multicastFrag = ChannelFragment.newInstance(
                MULTICAST_URI.toString(), MULTICAST_LOADER_ID, MULTICAST_FRAGMENT_TAG);
        ChannelFragment relMulticastFrag = ChannelFragment.newInstance(
                RELIABLE_MULTICAST_URI.toString(), RELIABLE_MULTICAST_LOADER_ID,
                RELIABLE_MULTICAST_FRAGMENT_TAG);
        ChannelFragment gatewayFrag = ChannelFragment.newInstance(
                GATEWAY_URI.toString(), GATEWAY_LOADER_ID, GATEWAY_FRAGMENT_TAG);
        ChannelFragment gatewayMediaFrag = ChannelFragment.newInstance(
                GATEWAY_MEDIA_URI.toString(), GATEWAY_MEDIA_LOADER_ID, GATEWAY_MEDIA_FRAGMENT_TAG);
        ChannelFragment serialFrag = ChannelFragment.newInstance(SERIAL_URI.toString(),
                SERIAL_LOADER_ID, SERIAL_FRAGMENT_TAG);

        // Add the fragments to our view hierarchy
        FragmentManager fm =
                getSupportFragmentManager();
        fm.beginTransaction().add(R.id.channel_container, multicastFrag, MULTICAST_FRAGMENT_TAG)
                .add(R.id.channel_container, relMulticastFrag, RELIABLE_MULTICAST_FRAGMENT_TAG)
                .add(R.id.channel_container, gatewayFrag, GATEWAY_FRAGMENT_TAG)
                .add(R.id.channel_container, gatewayMediaFrag, GATEWAY_MEDIA_FRAGMENT_TAG)
                .add(R.id.channel_container, serialFrag, SERIAL_FRAGMENT_TAG)
                .commit();
        
        // Get view references
        mOperatorTv = (TextView) findViewById(R.id.operator_id_tv_ref);
        mViewTablesButton = (Button) findViewById(R.id.view_tables_button);
        mDebuggingToolsButton = (Button) findViewById(R.id.debugging_tools_button);
        mLoggingToolsButton = (Button) findViewById(R.id.loggers_button);
        mHardResetButton = (Button) findViewById(R.id.hard_reset_button);
        mHelpAboutButton = (Button) findViewById(R.id.help_button);
        
        mPrefs = getSharedPreferences("edu.vu.isis.ammo.core_preferences", MODE_PRIVATE);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        String operatorId = mPrefs.getString(INetPrefKeys.CORE_OPERATOR_ID, "operator");
        mOperatorTv.setText("Operator ID: " + (operatorId == null ? "Unknown" : operatorId));
    }

    public static class ChannelFragment extends Fragment implements
            LoaderManager.LoaderCallbacks<Cursor> {

        /**
         * How often, at most, we will request for new data (in milliseconds)
         */
        private static final int UPDATE_INTERVAL_MS = 500;
        private static final Logger logger = LoggerFactory.getLogger("fragment.cloader");

        // Bundle keys
        private static final String BUN_CHANNEL_URI_KEY = "channelUri";
        private static final String BUN_LOADER_ID_KEY = "loaderId";
        private static final String BUN_LOG_ID_KEY = "logId";

        Uri mChannelUri;
        String mLogId;
        TextView mNameTv;
        TextView mFormalTv;
        TextView mCountTv;
        TextView mStatusTv;

        public ChannelFragment() {
        }

        public static ChannelFragment newInstance(String channelUri, int loaderId,
                String logId) {
            logger.trace(
                    "Instantiating ChannelFragment with args: channelUri={}, loaderId={}, logId={}",
                    channelUri, loaderId, logId);
            ChannelFragment f = new ChannelFragment();

            Bundle args = new Bundle();
            args.putString(BUN_CHANNEL_URI_KEY, channelUri);
            args.putInt(BUN_LOADER_ID_KEY, loaderId);
            args.putString(BUN_LOG_ID_KEY, logId);
            f.setArguments(args);

            return f;
        }

        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            Bundle args = getArguments();
            // Maybe this isn't quite the "proper" exception to throw here
            if (args == null)
                throw new IllegalArgumentException("Fragment created without proper arguments");
            mChannelUri = Uri.parse(args.getString(BUN_CHANNEL_URI_KEY));
            mLogId = args.getString(BUN_LOG_ID_KEY);
            getLoaderManager().initLoader(args.getInt(BUN_LOADER_ID_KEY), null, this);
            logger.trace("{} created", mLogId);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
            View layout = inflater.inflate(R.layout.gateway_item, container, false);
            mFormalTv = (TextView) layout.findViewById(R.id.gateway_formal);
            mCountTv = (TextView) layout.findViewById(R.id.gateway_send_receive);
            mNameTv = (TextView) layout.findViewById(R.id.gateway_name);
            mStatusTv = (TextView) layout.findViewById(R.id.gateway_status);
            return layout;
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            logger.trace("{} creating loader for Uri {}", mLogId, mChannelUri.toString());
            CursorLoader cl = new CursorLoader(getActivity(), mChannelUri,
                    null, null, null, null);
            cl.setUpdateThrottle(UPDATE_INTERVAL_MS);
            return cl;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            if (logger.isTraceEnabled()) {
                logger.trace("{} finished loading: {}", mLogId,
                        DatabaseUtils.dumpCursorToString(cursor));
            }
            
            if(!cursor.moveToFirst()) {
                logger.error("Received a cursor with no rows");
                mStatusTv.setText("Display Error");
            }
            
            int nameIx, formalIx, cStateIx, sStateIx, rStateIx, sendReceiveIx;

            try {
                nameIx = cursor.getColumnIndexOrThrow(ChannelColumns.NAME);
                formalIx = cursor.getColumnIndexOrThrow(ChannelColumns.FORMAL_IP);
                cStateIx = cursor.getColumnIndexOrThrow(ChannelColumns.CONNECTION_STATE);
                sStateIx = cursor.getColumnIndexOrThrow(ChannelColumns.SENDER_STATE);
                rStateIx = cursor.getColumnIndexOrThrow(ChannelColumns.RECEIVER_STATE);
                sendReceiveIx = cursor.getColumnIndexOrThrow(ChannelColumns.SEND_RECEIVE_COUNTS);
            } catch (IllegalArgumentException e) {
                logger.error("{} received a cursor missing an index", mLogId);
                logger.error("{}", e);
                mStatusTv.setText("Display Error");
                return;
            }

            int cState = cursor.getInt(cStateIx);
            int sState = cursor.getInt(sStateIx);
            int rState = cursor.getInt(rStateIx);
            
            ChannelState effectiveState = UiUtils.getEffectiveChannelState(cState, sState, rState);
            Resources res = getActivity().getResources();
            
            mNameTv.setText(cursor.getString(nameIx));
            mFormalTv.setText(cursor.getString(formalIx));
            mCountTv.setText(cursor.getString(sendReceiveIx));
            mStatusTv.setText(res.getString(effectiveState.getStringResId()));
            
            int color = res.getColor(effectiveState.getColorResId());
            mCountTv.setTextColor(color);
            mStatusTv.setTextColor(color);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            logger.trace("{} loader reset");
        }

    }

}

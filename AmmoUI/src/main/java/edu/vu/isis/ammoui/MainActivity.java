
package edu.vu.isis.ammoui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.provider.ChannelSchema;
import edu.vu.isis.ammo.core.provider.PreferenceSchema;
import edu.vu.isis.ammoui.distributor.ui.DistributorTabActivity;
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
    private ChannelFragment mMulticastFrag, mRelMulticastFrag, mGatewayFrag, mGatewayMediaFrag,
            mSerialFrag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ammo_activity);

        FragmentManager fm =
                getSupportFragmentManager();
        if (savedInstanceState == null) {
            mMulticastFrag = ChannelFragment.newInstance(
                    MULTICAST_URI.toString(), MULTICAST_LOADER_ID, MULTICAST_FRAGMENT_TAG);
            mRelMulticastFrag = ChannelFragment.newInstance(
                    RELIABLE_MULTICAST_URI.toString(), RELIABLE_MULTICAST_LOADER_ID,
                    RELIABLE_MULTICAST_FRAGMENT_TAG);
            mGatewayFrag = ChannelFragment.newInstance(
                    GATEWAY_URI.toString(), GATEWAY_LOADER_ID, GATEWAY_FRAGMENT_TAG);
            mGatewayMediaFrag = ChannelFragment.newInstance(
                    GATEWAY_MEDIA_URI.toString(), GATEWAY_MEDIA_LOADER_ID,
                    GATEWAY_MEDIA_FRAGMENT_TAG);
            mSerialFrag = ChannelFragment.newInstance(SERIAL_URI.toString(),
                    SERIAL_LOADER_ID, SERIAL_FRAGMENT_TAG);
            
            mMulticastFrag.setTargetClass(MulticastPreferenceActivity.class);
            mRelMulticastFrag.setTargetClass(ReliableMulticastPreferenceActivity.class);
            mGatewayFrag.setTargetClass(GatewayPreferenceActivity.class);
            mGatewayMediaFrag.setTargetClass(GatewayPreferenceActivity.class);
            mSerialFrag.setTargetClass(SerialPreferenceActivity.class);

            mMulticastFrag.setRetainInstance(true);
            mRelMulticastFrag.setRetainInstance(true);
            mGatewayFrag.setRetainInstance(true);
            mGatewayMediaFrag.setRetainInstance(true);
            mSerialFrag.setRetainInstance(true);
            

            // Add the fragments to our view hierarchy
            fm.beginTransaction()
                    .add(R.id.channel_container, mMulticastFrag, MULTICAST_FRAGMENT_TAG)
                    .add(R.id.channel_container, mRelMulticastFrag, RELIABLE_MULTICAST_FRAGMENT_TAG)
                    .add(R.id.channel_container, mGatewayFrag, GATEWAY_FRAGMENT_TAG)
                    .add(R.id.channel_container, mGatewayMediaFrag, GATEWAY_MEDIA_FRAGMENT_TAG)
                    .add(R.id.channel_container, mSerialFrag, SERIAL_FRAGMENT_TAG)
                    .commit();
        } else {
            mMulticastFrag = (ChannelFragment) fm.findFragmentByTag(MULTICAST_FRAGMENT_TAG);
            mRelMulticastFrag = (ChannelFragment) fm
                    .findFragmentByTag(RELIABLE_MULTICAST_FRAGMENT_TAG);
            mGatewayFrag = (ChannelFragment) fm.findFragmentByTag(GATEWAY_FRAGMENT_TAG);
            mGatewayMediaFrag = (ChannelFragment) fm.findFragmentByTag(GATEWAY_MEDIA_FRAGMENT_TAG);
            mSerialFrag = (ChannelFragment) fm.findFragmentByTag(SERIAL_FRAGMENT_TAG);
        }

        // Get view references
        mOperatorTv = (TextView) findViewById(R.id.operator_id_tv_ref);

    }

    @Override
    public void onResume() {
        super.onResume();
        
        // TODO: This should be asynchronous
        Cursor cursor = getContentResolver().query(PreferenceSchema.CONTENT_URI, new String[] {
            INetPrefKeys.CORE_OPERATOR_ID
        }, PreferenceSchema.AMMO_PREF_TYPE_STRING, new String[] {
            "Unknown"
        }, null);
        cursor.moveToFirst();
        String operatorId = cursor.getString(0);
        mOperatorTv.setText("Operator ID: " + operatorId);
    }

    public void viewTablesClick(View v) {
        startActivity(new Intent().setClass(this, DistributorTabActivity.class));
    }

    public void debugModeClick(View v) {
        String[] tools = {
                "Logcat Viewer", "Shell Command Buttons"
        };
        OnClickListener dialogListener = new OnClickListener() {
            private final int LOGCAT = 0;
            private final int AUTOBOT = 1;

            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent();
                switch (which) {
                    case LOGCAT:
                        // intent.setClass(MainActivity.this,
                        // LogcatLogViewer.class);
                        // TODO: Start LAUI log viewer
                        break;
                    case AUTOBOT:
                        intent.setAction("edu.vu.isis.tools.autobot.action.LAUNCH_AUTOBOT");
                        break;
                    default:
                        logger.warn("Invalid choice selected in debugging tools dialog");
                }
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "Tool not found on this device",
                            Toast.LENGTH_LONG).show();
                    logger.warn("Activity not found for debugging tools", e);
                }
            }
        };

        AlertDialog.Builder bldr = new AlertDialog.Builder(this);
        bldr.setTitle("Select a Tool").setItems(tools, dialogListener);
        bldr.create().show();
    };

    public void loggingToolsClick(View v) {
        // startActivity(new Intent().setClass(this, LoggerEditor.class));
        // TODO: Start LAUI
    }

    public void hardResetClick(View v) {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        /*
                         * Intent intent = new Intent();
                         * intent.setAction("edu.vu.isis.ammo.AMMO_HARD_RESET");
                         * intent.setClass(MainActivity.this,
                         * AmmoService.class); startService(intent);
                         */
                        // TODO: Restart the Service (AIDL call?)
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }

            }
        };
        AlertDialog.Builder bldr = new AlertDialog.Builder(this);
        bldr.setMessage("Are you sure you want to reset the service?")
                .setPositiveButton("Yes", listener)
                .setNegativeButton("No", listener).show();
    }

    public void helpClick(View v) {
        startActivity(new Intent().setClass(this, AboutActivity.class));
    }

    public void operatorIdClick(View v) {
        startActivity(new Intent()
                .setComponent(new ComponentName("transapps.settings",
                        "transapps.settings.SettingsActivity")));
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
        TextView mNameTv, mFormalTv, mCountTv, mStatusTv, mSendStatsTv, mReceiveStatsTv;
        RelativeLayout mRelativeLayout;
        Class<?> mTargetClass;
        int mLoaderId;

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
            mLoaderId = args.getInt(BUN_LOADER_ID_KEY);
            getLoaderManager().initLoader(mLoaderId, null, this);
            logger.trace("{} created", mLogId);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
            View layout = inflater.inflate(R.layout.gateway_item, container, false);
            
            mFormalTv = (TextView) layout.findViewById(R.id.gateway_formal);
            mCountTv = (TextView) layout.findViewById(R.id.gateway_send_receive);
            mNameTv = (TextView) layout.findViewById(R.id.gateway_name);
            mStatusTv = (TextView) layout.findViewById(R.id.gateway_status);
            mSendStatsTv = (TextView) layout.findViewById(R.id.gateway_send_stats);
            mReceiveStatsTv = (TextView) layout.findViewById(R.id.gateway_receive_stats);
            mRelativeLayout = (RelativeLayout) layout.findViewById(R.id.gateway_layout);
            
            layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mTargetClass != null) {
                        startActivity(new Intent().setClass(getActivity(), mTargetClass));
                    }
                }
            });
            
            
            return layout;
        }

        @Override
        public void onActivityCreated(Bundle icicle) {
            super.onActivityCreated(icicle);
            getLoaderManager().restartLoader(mLoaderId, null, this);
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

            if (!cursor.moveToFirst()) {
                logger.error("Received a cursor with no rows");
                mStatusTv.setText("Display Error");
            }

            int nameIx, formalIx, cStateIx, sStateIx, rStateIx, sendReceiveIx, sendStatsIx, receiveStatsIx;

            try {
                nameIx = cursor.getColumnIndexOrThrow(ChannelColumns.NAME);
                formalIx = cursor.getColumnIndexOrThrow(ChannelColumns.FORMAL_IP);
                cStateIx = cursor.getColumnIndexOrThrow(ChannelColumns.CONNECTION_STATE);
                sStateIx = cursor.getColumnIndexOrThrow(ChannelColumns.SENDER_STATE);
                rStateIx = cursor.getColumnIndexOrThrow(ChannelColumns.RECEIVER_STATE);
                sendReceiveIx = cursor.getColumnIndexOrThrow(ChannelColumns.SEND_RECEIVE_COUNTS);
                sendStatsIx = cursor.getColumnIndexOrThrow(ChannelColumns.SEND_BIT_STATS);
                receiveStatsIx = cursor.getColumnIndexOrThrow(ChannelColumns.RECEIVE_BIT_STATS);
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
            mSendStatsTv.setText(cursor.getString(sendStatsIx));
            mReceiveStatsTv.setText(cursor.getString(receiveStatsIx));

            int color = res.getColor(effectiveState.getColorResId());
            mCountTv.setTextColor(color);
            mStatusTv.setTextColor(color);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            logger.trace("{} loader reset");
        }
        
        public void setTargetClass(Class<?> targetClass) {
            mTargetClass = targetClass;
        }

    }

}

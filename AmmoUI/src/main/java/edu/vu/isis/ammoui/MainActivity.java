
package edu.vu.isis.ammoui;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
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
import android.view.Menu;

import edu.vu.isis.ammo.core.provider.ChannelSchema;
import edu.vu.isis.ammoui.R;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        fm.beginTransaction().add(multicastFrag, MULTICAST_FRAGMENT_TAG)
                .add(relMulticastFrag, RELIABLE_MULTICAST_FRAGMENT_TAG)
                .add(gatewayFrag, GATEWAY_FRAGMENT_TAG)
                .add(gatewayMediaFrag, GATEWAY_MEDIA_FRAGMENT_TAG)
                .add(serialFrag, SERIAL_FRAGMENT_TAG)
                .commit();
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
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            logger.trace("{} creating loader for Uri {}", mChannelUri.toString());
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
            if (cursor == null) {
                getActivity().getContentResolver().notifyChange(mChannelUri, null);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            logger.trace("{} loader reset");
        }

    }

}

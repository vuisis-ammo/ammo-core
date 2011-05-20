package edu.vu.isis.ammo.core.model;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.ui.util.TabActivityEx;


public class WiredNetlink extends Netlink {

    private WiredReceiver mReceiver;

	private WiredNetlink(TabActivityEx context) {
		super(context, "Wired Netlink", "wired");
	}
	
	public static Netlink getInstance(TabActivityEx context) {
		// initialize the gateway from the shared preferences
		return new WiredNetlink(context);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(INetPrefKeys.WIRED_PREF_SHOULD_USE)) {
		      //shouldUse(prefs);
		}
	}

    /**
     * Each time we start this activity, we need to update the status message
     * for each connection since it may have changed since this activity was
     * last loaded.
     */
    private void setWiredStatus( final int[] state ) {
        final Activity self = this.context;
        final Thread wiredThread = new Thread() {
            public void run() {
                self.runOnUiThread(new Runnable() {
                    public void run() {
                		logger.info( "Calling onStatusChange() for wired. Thread=<{}>", Thread.currentThread().getId() );
                    	statusListener.onStatusChange(statusView, state);
                    }});
            }
        };
        wiredThread.start();
    }

    
	/**
	 * The application should have the current status.
	 */
	public void initialize() {
		this.statusListener.onStatusChange(this.statusView, this.application.getWiredNetlinkState() );

        mReceiver = new WiredReceiver();

        final IntentFilter wiredFilter = new IntentFilter();
        wiredFilter.addAction( AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE );
        this.context.registerReceiver( mReceiver, wiredFilter );
	}

	@Override
	public void teardown()
	{
        try {
            this.context.unregisterReceiver( mReceiver );
        } catch(IllegalArgumentException ex) {
            logger.trace("tearing down the wired netlink object");
        }
    }


    // ===========================================================
    // Inner Classes
    // ===========================================================

    
    private class WiredReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //logger.info( "WiredReceiver::onReceive" );
            int state = intent.getIntExtra( "state", 0 );

            if (state != 0) {
                switch (state) {
                case AmmoIntents.LINK_UP:
                    logger.info( "onReceive: Link UP " );
                    setWiredStatus( new int[] {Netlink.NETLINK_UP} );
                    break;
                case AmmoIntents.LINK_DOWN:
                    logger.info( "onReceive: Link DOWN " );
                    setWiredStatus( new int[] {Netlink.NETLINK_DOWN} );
                    break;
                default:
                	logger.info( "onReceive: Unknown State" );
                	break;
                }
            }
        }
    }
}

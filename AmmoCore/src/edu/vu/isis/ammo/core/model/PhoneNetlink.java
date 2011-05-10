package edu.vu.isis.ammo.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.ui.util.TabActivityEx;


public class PhoneNetlink extends Netlink {
	private static final Logger connectionLogger = LoggerFactory.getLogger("scenario.network.link");

    private PhoneReceiver phoneReceiver;
    private PhoneStateListener mListener;
    
    private PhoneNetlink(TabActivityEx context) {
        super(context, "3G Netlink", "3G");   
    }


    public static Netlink getInstance(TabActivityEx context) {
        // initialize the gateway from the shared preferences
        return new PhoneNetlink(context);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(INetPrefKeys.PHONE_PREF_SHOULD_USE)) {
              //shouldUse(prefs);
        }
    }


    /**
     * Each time we start this activity, we need to update the status message
     * for each connection since it may have changed since this activity was
     * last loaded.
     */
    private void setPhoneStatus() {
        final Activity self = this.context;
        final Thread phoneThread = new Thread() {
            public void run() {
                final int[] state = PhoneNetlink.getState(PhoneNetlink.this.context);
                self.runOnUiThread(new Runnable() {
                    public void run() {
                		connectionLogger.info( "Calling onStatusChange(). Thread=<{}>", Thread.currentThread().getId() );
                    	statusListener.onStatusChange(statusView, state);
                    }});
            }
        };
        phoneThread.start();
    }


    static public int[] getState(Context context) {
        logger.trace("::getPhoneStatus");
        //final ConnectivityManager connManager =
        //    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        //final NetworkInfo info = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final TelephonyManager tm = (TelephonyManager) context.getSystemService( Context.TELEPHONY_SERVICE );

        final int[] state = new int[1];
        switch( tm.getDataState() )
        {
        case TelephonyManager.DATA_DISCONNECTED: state[0] = NETLINK_DISCONNECTED; break;
        case TelephonyManager.DATA_CONNECTING:   state[0] = NETLINK_CONNECTING;   break;
        case TelephonyManager.DATA_CONNECTED:    state[0] = NETLINK_CONNECTED;    break;
        case TelephonyManager.DATA_SUSPENDED:    state[0] = NETLINK_SUSPENDED;    break;
        }

        return state;
    }


    // ===========================================================
    // UI Management
    // ===========================================================

    public void initialize() {
        // get the starting value
        logger.trace("PhoneThread::run");
        connectionLogger.trace("PhoneThread::run");
        mListener = new PhoneStateListener()
        {
        	public void onDataConnectionStateChanged( int state )
        	{
                connectionLogger.info( "PhoneReceiver::onCallStateChanged()" );
                setPhoneStatus();       		
        	}
        };
        final TelephonyManager tm = (TelephonyManager) context.getSystemService( Context.TELEPHONY_SERVICE );
        tm.listen( mListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE );
        
        setPhoneStatus();

        // get updates as they happen
        this.phoneReceiver = new PhoneReceiver();
        IntentFilter phoneFilter = new IntentFilter( TelephonyManager.ACTION_PHONE_STATE_CHANGED );
        this.context.registerReceiver(this.phoneReceiver, phoneFilter);
    }


    public void teardown() {
        try {
            final TelephonyManager tm = (TelephonyManager) context.getSystemService( Context.TELEPHONY_SERVICE );
        	tm.listen( mListener, PhoneStateListener.LISTEN_NONE );
            this.context.unregisterReceiver(this.phoneReceiver);
        } catch(IllegalArgumentException ex) {
            logger.trace("tearing down the 3G netlink object");
        }
    }


    // ===========================================================
    // Inner Classes
    // ===========================================================

    
    private class PhoneReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            connectionLogger.info( "PhoneReceiver::onReceive" );
            setPhoneStatus();
        }
    }
}

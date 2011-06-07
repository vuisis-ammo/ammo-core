package edu.vu.isis.ammo.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.ui.util.TabActivityEx;


public class PhoneNetlink extends Netlink
{
    private static final Logger connectionLogger = LoggerFactory.getLogger( "scenario.network.link" );

    private TelephonyManager mTelephonyManager = null;
    private ConnectivityManager mConnManager = null;


    private PhoneNetlink(Context context)
    {
        super( context, "3G Netlink", "3G" );

        mTelephonyManager = (TelephonyManager) context.getSystemService( Context.TELEPHONY_SERVICE );
        mConnManager =
            (ConnectivityManager) context.getSystemService( Context.CONNECTIVITY_SERVICE );

        updateStatus();
    }


    public static Netlink getInstance(Context context)
    {
        // initialize the gateway from the shared preferences
        return new PhoneNetlink( context );
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if ( key.equals(INetPrefKeys.PHONE_PREF_SHOULD_USE) )
        {
              //shouldUse(prefs);
        }
    }


    public void updateStatus()
    {
        logger.error( "::updateStatus" );

        final int[] state = new int[1];

        final int status = mTelephonyManager.getDataState();
        logger.error( "network state={}", status );
        switch ( status )
        {
        case TelephonyManager.DATA_DISCONNECTED:
            state[0] = NETLINK_DISCONNECTED;
            setLinkUp( false );
            break;
        case TelephonyManager.DATA_CONNECTING:
            state[0] = NETLINK_CONNECTING;
            setLinkUp( false );
            break;
        case TelephonyManager.DATA_CONNECTED:
            state[0] = NETLINK_CONNECTED;
            setLinkUp( true );
            break;
        case TelephonyManager.DATA_SUSPENDED:
            state[0] = NETLINK_SUSPENDED;
            setLinkUp( false );
            break;
        }

        setStatus( state );
    }


    @Override
    public void initialize() {}


    @Override
    public void teardown() {}
}

package edu.vu.isis.ammo.core.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import edu.vu.isis.ammo.INetPrefKeys;


public class WifiNetlink extends Netlink
{
    private WifiManager mWifiManager = null;
    private ConnectivityManager mConnManager = null;
    private Context mContext = null;

    private WifiNetlink( Context context )
    {
        super( context, "Wifi Netlink", "wifi" );
        this.mContext = context;

        mWifiManager = (WifiManager) context.getSystemService( Context.WIFI_SERVICE );
        mConnManager =
            (ConnectivityManager) context.getSystemService( Context.CONNECTIVITY_SERVICE );

        updateStatus();
    }


    public static Netlink getInstance(Context context)
    {
        // initialize the gateway from the shared preferences
        return new WifiNetlink( context );
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if ( key.equals(INetPrefKeys.WIFI_PREF_SHOULD_USE) )
        {
              //shouldUse(prefs);
        }
    }


    @Override
    public void updateStatus()
    {
        final int[] state = new int[1];

        if ( !mWifiManager.isWifiEnabled() )
        {
            state[0] = NETLINK_DISCONNECTED;
            setLinkUp( false );
        }
        else
        {
            final NetworkInfo info = mConnManager.getNetworkInfo( ConnectivityManager.TYPE_WIFI );

            switch( info.getDetailedState() )
            {
            case DISCONNECTED:
                state[0] = NETLINK_DISCONNECTED;
                setLinkUp( false );
                break;
            case IDLE:
                state[0] = NETLINK_IDLE;
                setLinkUp( false );
                break;
            case SCANNING:
                state[0] = NETLINK_SCANNING;
                setLinkUp( false );
                break;
            case CONNECTING:
                state[0] = NETLINK_CONNECTING;
                setLinkUp( false );
                break;
            case AUTHENTICATING:
                state[0] = NETLINK_AUTHENTICATING;
                setLinkUp( true );
                break;
            case OBTAINING_IPADDR:
                state[0] = NETLINK_OBTAINING_IPADDR;
                setLinkUp( true );
                break;
            case FAILED:
                state[0] = NETLINK_FAILED;
                setLinkUp( false );
                break;
            case CONNECTED:
                state[0] = NETLINK_CONNECTED;
                setLinkUp( true );
                break;
            }
        }
        Editor editor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        editor.putInt(INetPrefKeys.WIFI_PREF_IS_ACTIVE, state[0]).commit();    
        
        logger.error( "Wifi: updating status to {}", state );
        setStatus( state );
    }


    public void initialize() {}


    public void teardown() {}
}

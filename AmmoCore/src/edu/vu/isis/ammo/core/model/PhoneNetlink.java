/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import edu.vu.isis.ammo.INetDerivedKeys;


public class PhoneNetlink extends Netlink
{
    private static final Logger logger = LoggerFactory.getLogger("class.PhoneNetlink");

    private TelephonyManager mTelephonyManager = null;
    private Context mContext = null;

    private PhoneNetlink(Context context)
    {
        super( context, "3G Netlink", "3G" );

        this.mContext = context;

        mTelephonyManager = (TelephonyManager) context.getSystemService( Context.TELEPHONY_SERVICE );
        
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
        if ( key.equals(INetDerivedKeys.PHONE_PREF_SHOULD_USE) )
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
        Editor editor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        editor.putInt(INetDerivedKeys.PHONE_PREF_IS_ACTIVE, state[0]).commit();    
        
        setStatus( state );
    }


    @Override
    public void initialize() {}


    @Override
    public void teardown() {}
}

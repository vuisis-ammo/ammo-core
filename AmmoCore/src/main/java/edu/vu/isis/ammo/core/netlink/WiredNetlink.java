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
package edu.vu.isis.ammo.core.netlink;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.IBinder;
import android.preference.PreferenceManager;
import edu.vu.isis.ammo.INetDerivedKeys;
import edu.vu.isis.ammo.core.ethertracker.EthTrackSvc;


public class WiredNetlink extends Netlink
{
    private Context mContext = null;


    private WiredNetlink( Context context )
    {
        super( context, "Wired Netlink", "wired" );
        mContext = context;

        // Get a reference to the EthTrackSvc.
        Intent ethernetServiceIntent = new Intent( mContext, EthTrackSvc.class );
        boolean result = mContext.bindService( ethernetServiceIntent,
                                               ethernetServiceConnection,
                                               Context.BIND_AUTO_CREATE );
        if ( !result )
            logger.error( "WiredNetlink failed to bind to the EthTrackSvc!" );
        else
            logger.error( "WiredNetlink binding to EthTrackSvc!" );
    }


    public static Netlink getInstance( Context context )
    {
        // initialize the gateway from the shared preferences
        return new WiredNetlink( context );
    }


    @Override
    public void onSharedPreferenceChanged( SharedPreferences sharedPreferences, String key )
    {
        if ( key.equals(INetDerivedKeys.WIRED_PREF_SHOULD_USE) )
        {
              //shouldUse( prefs );
        }
    }


    private EthTrackSvc ethernetServiceBinder;

    private ServiceConnection ethernetServiceConnection = new ServiceConnection()
    {
        public void onServiceConnected( ComponentName name, IBinder service )
        {
            logger.error( "::onServiceConnected - Ethernet tracking service" );
            ethernetServiceBinder = ((EthTrackSvc.MyBinder) service).getService();
            updateStatus();
        }

        public void onServiceDisconnected( ComponentName name )
        {
            logger.trace( "::onServiceDisconnected - Ethernet tracking service" );
            ethernetServiceBinder = null;
        }
    };


    public void updateStatus()
    {
        logger.error( "::updateStatus" );

        if ( ethernetServiceBinder == null )
            return;

        final int[] state = new int[1];

        final boolean status = ethernetServiceBinder.isLinkUp();
        logger.error( "wired state={}", status );

        state[0] = (status) ?  Netlink.NETLINK_UP : Netlink.NETLINK_DOWN;
        setLinkUp( status );
        
        Editor editor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        editor.putInt(INetDerivedKeys.PHYSICAL_LINK_PREF_IS_ACTIVE, state[0]).commit();   
        
        setStatus( state );
    }


    @Override
    public void initialize() {}


    @Override
    public void teardown()
    {
        try
        {
            mContext.unbindService( ethernetServiceConnection );
        }
        catch(IllegalArgumentException ex)
        {
            logger.trace( "tearing down the wired netlink object" );
        }
    }
}

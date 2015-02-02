/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.vu.isis.ammo.core.model;

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
            logger.info( "WiredNetlink binding to EthTrackSvc!" );
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
            logger.info( "::onServiceConnected - Ethernet tracking service" );
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
        logger.info( "::updateStatus" );

        if ( ethernetServiceBinder == null )
            return;

        final int[] state = new int[1];

        final boolean status = ethernetServiceBinder.isLinkUp();
        logger.info( "wired state={}", status );

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

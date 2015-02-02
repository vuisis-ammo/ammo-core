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
    private static final Logger logger = LoggerFactory.getLogger("ui.3Glink");

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

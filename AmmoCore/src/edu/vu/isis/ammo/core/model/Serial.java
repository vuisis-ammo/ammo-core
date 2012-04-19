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


import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.network.SerialChannel;


public class Serial extends Channel
{
	public static String KEY = "SerialUiChannel";

    final public SerialChannel mChannel;

	public static Serial getInstance( Context context, SerialChannel channel )
    {
		if ( mInstance == null )
			mInstance = new Serial( context, "Serial Channel" , channel );
		return mInstance;
	}


	@Override
	public void enable() { setElection( true ); }

	@Override
	public void disable() { setElection( false ); }

	@Override
	public void toggle() { setElection( !mElection ); }

	@Override
	public boolean isEnabled() { return mElection; }


	@Override
	public View getView( View row, LayoutInflater inflater )
    {
		row = inflater.inflate( edu.vu.isis.ammo.core.R.layout.serial_item, null );

		TextView name = (TextView) row.findViewById( R.id.serial_name );
		TextView device = (TextView) row.findViewById( R.id.serial_device );
		((TextView) row.findViewById( R.id.channel_type )).setText( Serial.KEY );

		name.setText( this.name );
		StringBuilder sb = new StringBuilder();
		sb.append( this.prefs.getString( INetPrefKeys.SERIAL_DEVICE, "def device" ));
		sb.append( "@" );
		sb.append( this.prefs.getString( INetPrefKeys.SERIAL_BAUD_RATE, "9600" ));
		device.setText( sb.toString() );
		return row;
	}


	@Override
	public int[] getStatus() { return mStatus; }

	@Override
	public void setStatus( int[] statusCode ) { mStatus = statusCode; }


	@Override
	public void onSharedPreferenceChanged( SharedPreferences sharedPreferences,
                                           String key )
    {
		// if ( key.equals(INetPrefKeys.SERIAL_BAUD_RATE) ) {
		// } else if ( key.equals(INetPrefKeys.SERIAL_SLOT_NUMBER) ) {
		// } else if ( key.equals(INetPrefKeys.SERIAL_DEVICE) ) {
		// }
	}


	@Override
	public void setOnNameChangeListener( OnNameChangeListener listener, View view )
    {
		// TODO Auto-generated method stub
	}


    ///////////////////////////////////////////////////////////////////////////
    //
    // Private members
    //

	private Serial( Context context, String name, SerialChannel channel )
    {
		super( context, name );

        mChannel = channel;
		mElection = ! this.prefs.getBoolean( INetPrefKeys.SERIAL_DISABLED,
                                             INetPrefKeys.DEFAULT_SERIAL_DISABLED );
	}


    // FIXME : Should this view, which is only user interface, be writing this value?
	private void setElection( boolean b )
    {
		mElection = b;
        Editor editor = this.prefs.edit();
        editor.putBoolean( INetPrefKeys.SERIAL_DISABLED, !mElection );
        editor.commit();
	}


	private static Serial mInstance;

	private boolean mElection = false;
	private int[] mStatus = null;
}

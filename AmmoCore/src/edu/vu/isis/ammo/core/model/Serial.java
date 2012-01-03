/*
Copyright(c) 2010-2012

This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under contract [contract citation, subcontract and prime contract]. 
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

public class Serial extends Channel
{
	public static String KEY = "SerialUiChannel";

	private static Serial instance;

	public static Serial getInstance(Context context)
    {
		if(instance == null)
			instance = new Serial(context, "Serial Channel");
		return instance;
	}

	private boolean election = false;
	private int[] status = null;

	private Serial(Context context, String name)
    {
		super(context, name);

		election = this.prefs.getBoolean(INetPrefKeys.SERIAL_SHOULD_USE, false);
	}


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
	public void enable()
    {
		this.setElection(true);
	}


	private void setElection(boolean b)
    {
		this.election = b;
        Editor editor = this.prefs.edit();
        editor.putBoolean(INetPrefKeys.SERIAL_SHOULD_USE, this.election);
        editor.commit();
	}


	@Override
	public void disable()
    {
		this.setElection(false);
	}


	@Override
	public void toggle()
    {
		this.setElection(!this.election);
	}


	@Override
	public boolean isEnabled()
    {
		return this.election;
	}


	@Override
	public View getView(View row, LayoutInflater inflater)
    {
		row = inflater.inflate(edu.vu.isis.ammo.core.R.layout.serial_item, null);

		TextView name = (TextView) row.findViewById(R.id.serial_name);
		TextView device = (TextView) row.findViewById(R.id.serial_device);
		((TextView) row.findViewById( R.id.channel_type )).setText( Serial.KEY );

		name.setText(this.name);
		StringBuilder sb = new StringBuilder();
		sb.append(this.prefs.getString(INetPrefKeys.SERIAL_DEVICE, "def device"));
		sb.append("@");
		sb.append(this.prefs.getString(INetPrefKeys.SERIAL_BAUD_RATE, "9600"));
		device.setText(sb.toString());
		return row;
	}


	@Override
	public int[] getStatus()
    {
		return this.status;
	}


	@Override
	public void setOnNameChangeListener(OnNameChangeListener listener, View view)
    {
		// TODO Auto-generated method stub
	}


	@Override
	public void setStatus(int[] statusCode)
    {
		this.status = statusCode;
	}
}

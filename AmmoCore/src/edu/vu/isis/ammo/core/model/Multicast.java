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

public class Multicast extends Channel {

	public static String KEY = "MulticastUiChannel";
	boolean election = false;
	int [] status = null;
	String formalIP = "formal ip";
	String port = "port";
	protected Multicast(Context context, String name) {
		super(context, name);
		this.formalIP = this.prefs.getString(INetPrefKeys.MULTICAST_HOST, 
		                                     INetPrefKeys.DEFAULT_MULTICAST_HOST);
		this.port = this.prefs.getString(INetPrefKeys.MULTICAST_PORT, 
		                                 INetPrefKeys.DEFAULT_MULTICAST_PORT);
		this.election = this.prefs.getBoolean(INetPrefKeys.MULTICAST_DISABLED,
		                                      INetPrefKeys.DEFAULT_MULTICAST_DISABLED);
	}

	private static Multicast instance = null;
	public static Multicast getInstance(Context context)
	{
		if(instance == null)
			instance = new Multicast(context, "Multicast Channel");
		return instance;
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if(key.equals(INetPrefKeys.MULTICAST_HOST))
		{
			this.formalIP = this.prefs.getString(INetPrefKeys.MULTICAST_HOST, 
			                                     INetPrefKeys.DEFAULT_MULTICAST_HOST);
			
		}
		
		if(key.equals(INetPrefKeys.MULTICAST_PORT))
		{
			this.port = this.prefs.getString(INetPrefKeys.MULTICAST_PORT,
			                                 INetPrefKeys.DEFAULT_MULTICAST_PORT);
		}

	}

	@Override
	public void enable() {
		this.setElection(true);

	}

	@Override
	public void disable() {
		this.setElection(false);

	}

	@Override
	public void toggle() {
		this.setElection(!this.election);

	}

	private void setElection(boolean b)
	{
        this.election = b;
        Editor editor = this.prefs.edit();
        editor.putBoolean(INetPrefKeys.MULTICAST_DISABLED, this.election);
        editor.commit();
	}
	
	@Override
	public boolean isEnabled() {
		return this.election;
	}

	@Override
	public View getView(View row, LayoutInflater inflater) {

		row = inflater.inflate(edu.vu.isis.ammo.core.R.layout.multicast_item, null);
		
		TextView formal = (TextView) row.findViewById(R.id.multicast_formal);
		TextView name = (TextView) row.findViewById(R.id.multicast_name);
		((TextView)row.findViewById(R.id.channel_type)).setText(Multicast.KEY);
		

		name.setText(this.name);
		formal.setText(this.formalIP + ":" + this.port);
		//set vals
		return row;
	}

	@Override
	public int[] getStatus() {
		return this.status;
	}

	@Override
	public void setOnNameChangeListener(OnNameChangeListener listener, View view) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setStatus(int[] statusCode) {
		this.status = statusCode;

	}


}

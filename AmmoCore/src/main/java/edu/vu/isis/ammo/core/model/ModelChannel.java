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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.network.INetChannel;

public abstract class ModelChannel implements OnSharedPreferenceChangeListener{

    private static final Logger logger = LoggerFactory.getLogger("model.channel");
    
    protected OnNameChangeListener mOnNameChangeListener; 
    
	protected Context context = null;
	protected String name = "";
	protected SharedPreferences prefs = null;
	
	protected INetChannel mNetChannel = null;
	
	protected ModelChannel(Context context, String name)
	{
		this.context = context;
		this.name = name;
		this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
		this.prefs.registerOnSharedPreferenceChangeListener(this);
		logger.trace("Channel {} constructed", name);
	}
	
	public INetChannel getNetChannel () {return mNetChannel;}
	
	public void setName(String newName)
	{
		this.name = newName;
	}
	public String getName()
	{
		return this.name;
	}
	
	public abstract void enable();
	public abstract void disable();
	public abstract void toggle();
	public abstract boolean isEnabled();
	public abstract View getView(View row, LayoutInflater inflater);
	public abstract int[] getStatus();
	public void setOnNameChangeListener(OnNameChangeListener listener) {
	    mOnNameChangeListener = listener;
	}
	protected void callOnNameChange() {
	    if(mOnNameChangeListener != null) {
            mOnNameChangeListener.onNameChange();
        }
	}
	public abstract void setStatus(int [] statusCode);
}

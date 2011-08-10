package edu.vu.isis.ammo.core.model;

import edu.vu.isis.ammo.core.OnNameChangeListener;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;

public abstract class Channel implements OnSharedPreferenceChangeListener{

	protected Context context = null;
	protected String name = "";
	protected SharedPreferences prefs = null;
	protected Channel(Context context, String name)
	{
		this.context = context;
		this.name = name;
		this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
		this.prefs.registerOnSharedPreferenceChangeListener(this);
	}
	
	
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
	public abstract void setOnNameChangeListener(OnNameChangeListener listener, View view);
	public abstract void setStatus(int [] statusCode);
}

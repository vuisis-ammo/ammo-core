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
package edu.vu.isis.ammo.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class MyListPreference extends ListPreference {

	List<String> items = new ArrayList<String>();
	public MyListPreference(Context context) {
		super(context);
		this.refreshList();
	}
	
	public MyListPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.refreshList();
	}
	
	public void refreshList() {
		File f = new File("/dev/");
		
		String[] l = f.list();
		items.clear();
		for(int i = 0; i < l.length; ++i) {
			if(l[i].substring(0, 3).equals("tty")) {
				items.add("/dev/" + l[i]);
			}
		}
		
		String [] s = this.items.toArray(new String [1]);
		this.setEntries(s);
		this.setEntryValues(s);
	}

}

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

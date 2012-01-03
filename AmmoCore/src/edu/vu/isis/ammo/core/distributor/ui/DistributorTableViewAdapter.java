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

package edu.vu.isis.ammo.core.distributor.ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.widget.ResourceCursorAdapter;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RequestDisposal;



public abstract class DistributorTableViewAdapter extends ResourceCursorAdapter {

	public static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z");

	static protected HashMap<Integer, String> dispositionStateMap;
	static {
		dispositionStateMap = new HashMap<Integer, String>();
		dispositionStateMap.put(RequestDisposal.EXPIRED.o, "Request Expired");
		dispositionStateMap.put(RequestDisposal.COMPLETE.o, "Request Complete");
		dispositionStateMap.put(RequestDisposal.INCOMPLETE.o, "Request Incomplete");
		dispositionStateMap.put(RequestDisposal.DISTRIBUTE.o, "Request In-progress");
		dispositionStateMap.put(RequestDisposal.NEW.o, "Request New");
		dispositionStateMap.put(RequestDisposal.FAILED.o, "Request Failed");
	}
	static protected HashMap<Integer, Integer> dispositionColorMap;
	static {
		dispositionColorMap = new HashMap<Integer, Integer>();
		dispositionColorMap.put(RequestDisposal.EXPIRED.o, Color.RED);
		dispositionColorMap.put(RequestDisposal.COMPLETE.o, Color.GREEN);
		dispositionColorMap.put(RequestDisposal.INCOMPLETE.o, Color.RED);
		dispositionColorMap.put(RequestDisposal.DISTRIBUTE.o, Color.LTGRAY);
		dispositionColorMap.put(RequestDisposal.NEW.o, Color.CYAN);
		dispositionColorMap.put(RequestDisposal.FAILED.o, Color.RED);
	}

	final protected Calendar expiration;

	public DistributorTableViewAdapter(Context context, int layout, Cursor cursor) {
		super(context, layout, cursor);

		this.expiration = Calendar.getInstance();
	}

}

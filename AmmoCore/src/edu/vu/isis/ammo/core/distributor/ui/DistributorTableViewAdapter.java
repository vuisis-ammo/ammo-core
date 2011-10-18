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
	}
	static protected HashMap<Integer, Integer> dispositionColorMap;
	static {
		dispositionColorMap = new HashMap<Integer, Integer>();
		dispositionColorMap.put(RequestDisposal.EXPIRED.o, Color.RED);
		dispositionColorMap.put(RequestDisposal.COMPLETE.o, Color.GREEN);
		dispositionColorMap.put(RequestDisposal.INCOMPLETE.o, Color.RED);
		dispositionColorMap.put(RequestDisposal.DISTRIBUTE.o, Color.LTGRAY);
		dispositionColorMap.put(RequestDisposal.NEW.o, Color.CYAN);
	}

	final protected Calendar expiration;

	public DistributorTableViewAdapter(Context context, int layout, Cursor cursor) {
		super(context, layout, cursor);

		this.expiration = Calendar.getInstance();
	}

}

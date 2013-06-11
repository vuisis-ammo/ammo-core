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

package edu.vu.isis.ammo.core.distributor.ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.widget.ResourceCursorAdapter;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTotalState;

public abstract class DistributorTableViewAdapter extends ResourceCursorAdapter {

    public static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z");

    static protected HashMap<Integer, String> dispositionStateMap;
    static {
        dispositionStateMap = new HashMap<Integer, String>();
        dispositionStateMap.put(DisposalTotalState.EXPIRED.o, "Request Expired");
        dispositionStateMap.put(DisposalTotalState.COMPLETE.o, "Request Complete");
        dispositionStateMap.put(DisposalTotalState.INCOMPLETE.o, "Request Incomplete");
        dispositionStateMap.put(DisposalTotalState.DISTRIBUTE.o, "Request In-progress");
        dispositionStateMap.put(DisposalTotalState.NEW.o, "Request New");
        dispositionStateMap.put(DisposalTotalState.FAILED.o, "Request Failed");
    }
    static protected HashMap<Integer, Integer> dispositionColorMap;
    static {
        dispositionColorMap = new HashMap<Integer, Integer>();
        dispositionColorMap.put(DisposalTotalState.EXPIRED.o, Color.RED);
        dispositionColorMap.put(DisposalTotalState.COMPLETE.o, Color.GREEN);
        dispositionColorMap.put(DisposalTotalState.INCOMPLETE.o, Color.RED);
        dispositionColorMap.put(DisposalTotalState.DISTRIBUTE.o, Color.LTGRAY);
        dispositionColorMap.put(DisposalTotalState.NEW.o, Color.CYAN);
        dispositionColorMap.put(DisposalTotalState.FAILED.o, Color.RED);
    }

    final protected Calendar expiration;

    public DistributorTableViewAdapter(Context context, int layout, Cursor cursor) {
        super(context, layout, cursor);

        this.expiration = Calendar.getInstance();
    }

}

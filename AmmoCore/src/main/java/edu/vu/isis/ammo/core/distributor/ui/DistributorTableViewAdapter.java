/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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

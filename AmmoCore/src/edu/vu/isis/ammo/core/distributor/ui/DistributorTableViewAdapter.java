package edu.vu.isis.ammo.core.distributor.ui;

import android.content.Context;
import android.database.Cursor;
import android.widget.SimpleCursorAdapter;



public class DistributorTableViewAdapter extends SimpleCursorAdapter {

    public DistributorTableViewAdapter(Context context, int layout, Cursor c,
                                       String[] from, int[] to) {
        super(context, layout, c, from, to);
    }

}

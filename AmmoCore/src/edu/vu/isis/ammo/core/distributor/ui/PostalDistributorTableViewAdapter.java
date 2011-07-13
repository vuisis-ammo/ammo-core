package edu.vu.isis.ammo.core.distributor.ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Disposition;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;

/**
 * CursorAdapter used by AmmoCore to display its tables in a human-readable format.
 * Each row of the table view is formatted a certain way based on the disposition
 * of the corresponding row in the content provider's table.
 * @author demetri
 *
 */
public class PostalDistributorTableViewAdapter extends DistributorTableViewAdapter {
    public static final Logger logger = LoggerFactory.getLogger(PostalDistributorTableViewAdapter.class);

    static private HashMap<Integer, String> dispositionStateMap = new HashMap<Integer, String>();
    static private HashMap<Integer, Integer> dispositionColorMap = new HashMap<Integer, Integer>();

    final private Calendar expiration;
    Context context = null;
    public PostalDistributorTableViewAdapter(Context context, int layout, Cursor cursor,
            String[] from, int[] to) {
        super(context, layout, cursor, from, to);
        this.context = context;
        // Setup hashmap.
        dispositionStateMap.put(Disposition.EXPIRED.o, "Disposition Expired");
        dispositionStateMap.put(Disposition.FAIL.o, "Disposition Failed");
        dispositionStateMap.put(Disposition.JOURNAL.o, "Disposition Journal");
        dispositionStateMap.put(Disposition.PENDING.o, "Disposition Pending");
        dispositionStateMap.put(Disposition.QUEUED.o, "Disposition Queued");
        dispositionStateMap.put(Disposition.SENT.o, "Disposition Sent");

        dispositionColorMap.put(Disposition.EXPIRED.o, Color.LTGRAY);
        dispositionColorMap.put(Disposition.FAIL.o, Color.RED);
        dispositionColorMap.put(Disposition.JOURNAL.o, Color.MAGENTA);
        dispositionColorMap.put(Disposition.PENDING.o, Color.rgb(255, 149, 28));
        dispositionColorMap.put(Disposition.QUEUED.o, Color.CYAN);
        dispositionColorMap.put(Disposition.SENT.o, Color.GREEN);

        this.expiration = Calendar.getInstance();
    }

    // ===========================================================
    // UI Management
    // ===========================================================
    // Override this to set disposition field.

    @Override
    public void bindView(View v, Context context, Cursor cursor) {
        super.bindView(v, context, cursor);

        // deal with the displaying of the disposition

        TextView tv = (TextView)v.findViewById(R.id.distributor_table_view_item_disposition);
        int disposition = cursor.getInt(cursor.getColumnIndex(PostalTableSchema.DISPOSITION.n));
        if (dispositionStateMap.containsKey(disposition)) {
            tv.setText(dispositionStateMap.get(disposition));
            tv.setTextColor(dispositionColorMap.get(disposition));
        } else {
            tv.setText("Unknown Disposition");
        }

        // deal with the displaying of the timestamp
        TextView ttv = (TextView)v.findViewById(R.id.distributor_table_view_item_timestamp);
        long timestamp = cursor.getLong(cursor.getColumnIndex(PostalTableSchema.CREATED.n));
        this.expiration.clear();
        this.expiration.setTimeInMillis(timestamp);
        String timed = sdf.format(this.expiration.getTime());
        logger.debug("tuple timestamp {}",timed);
        ttv.setText(timed);

        // set the mime-type / topic
        TextView tttv = (TextView)v.findViewById(R.id.distributor_table_view_item_topic);
        tttv.setText(cursor.getString(cursor.getColumnIndex(PostalTableSchema.CP_TYPE.n)));

    }

    static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z");
}

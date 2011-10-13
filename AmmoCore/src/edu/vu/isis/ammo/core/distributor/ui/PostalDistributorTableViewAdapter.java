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
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RequestDisposal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;

/**
 * CursorAdapter used by AmmoCore to display its tables in a human-readable format.
 * Each row of the table view is formatted a certain way based on the disposition 
 * of the corresponding row in the content provider's table.
 *
 */
public class PostalDistributorTableViewAdapter extends DistributorTableViewAdapter 
{
	public static final Logger logger = LoggerFactory.getLogger(PostalDistributorTableViewAdapter.class);
	
	static private HashMap<Integer, String> dispositionStateMap = new HashMap<Integer, String>();
	static private HashMap<Integer, Integer> dispositionColorMap = new HashMap<Integer, Integer>();
	
	final private Calendar expiration;
	Context context = null;
	public PostalDistributorTableViewAdapter(Context context, int layout, Cursor cursor,
			String[] from, int[] to) 
	{
		super(context, layout, cursor, from, to);
		this.context = context;
		// Setup hashmap.
		dispositionStateMap.put(RequestDisposal.EXPIRED.o, "Request Expired");
		dispositionStateMap.put(RequestDisposal.COMPLETE.o, "Request Complete");
		dispositionStateMap.put(RequestDisposal.INCOMPLETE.o, "Request Incomplete");
		dispositionStateMap.put(RequestDisposal.DISTRIBUTE.o, "Request In-progress");
		dispositionStateMap.put(RequestDisposal.NEW.o, "Request New");
		
		dispositionColorMap.put(RequestDisposal.EXPIRED.o, Color.RED);
		dispositionColorMap.put(RequestDisposal.COMPLETE.o, Color.LTGRAY);
		dispositionColorMap.put(RequestDisposal.INCOMPLETE.o, Color.RED);
		dispositionColorMap.put(RequestDisposal.DISTRIBUTE.o, Color.GREEN);
		dispositionColorMap.put(RequestDisposal.NEW.o, Color.CYAN);
		
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
		int disposition = cursor.getInt(cursor.getColumnIndex(SubscribeTableSchema.DISPOSITION.n));
		if (dispositionStateMap.containsKey(disposition)) {
			tv.setText(dispositionStateMap.get(disposition));
			tv.setTextColor(dispositionColorMap.get(disposition));
		} else {
			tv.setText("Unknown Disposition");
		}
		
		// deal with the displaying of the timestamp
		TextView ttv = (TextView)v.findViewById(R.id.distributor_table_view_item_timestamp);
		long timestamp = cursor.getLong(cursor.getColumnIndex(SubscribeTableSchema.CREATED.n));
		this.expiration.clear();
		this.expiration.setTimeInMillis(timestamp);
		String timed = sdf.format(this.expiration.getTime());
		logger.debug("tuple timestamp {}",timed);
		ttv.setText(timed);
		
		// set the mime-type / topic
		TextView tttv = (TextView)v.findViewById(R.id.distributor_table_view_item_topic);
		tttv.setText(cursor.getString(cursor.getColumnIndex(PostalTableSchema.TOPIC.n)));
		
	}
	
    static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z");
}

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
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

public class DistributorTableViewAdapter extends SimpleCursorAdapter 
{
	public static final Logger logger = LoggerFactory.getLogger(DistributorTableViewAdapter.class);
	
	static private HashMap<Integer, String> dispositionStateMap = new HashMap<Integer, String>();
	static private HashMap<Integer, Integer> dispositionColorMap = new HashMap<Integer, Integer>();
	
	final private Calendar expiration;
	
	public DistributorTableViewAdapter(Context context, int layout, Cursor cursor,
			String[] from, int[] to) 
	{
		super(context, layout, cursor, from, to);

		// Setup hashmap.
		dispositionStateMap.put(SubscriptionTableSchema.DISPOSITION_EXPIRED, "Disposition Expired");
		dispositionStateMap.put(SubscriptionTableSchema.DISPOSITION_FAIL, "Disposition Failed");
		dispositionStateMap.put(SubscriptionTableSchema.DISPOSITION_JOURNAL, "Disposition Journal");
		dispositionStateMap.put(SubscriptionTableSchema.DISPOSITION_PENDING, "Disposition Pending");
		dispositionStateMap.put(SubscriptionTableSchema.DISPOSITION_QUEUED, "Disposition Queued");
		dispositionStateMap.put(SubscriptionTableSchema.DISPOSITION_SENT, "Disposition Sent");
		
		dispositionColorMap.put(SubscriptionTableSchema.DISPOSITION_EXPIRED, Color.LTGRAY);
		dispositionColorMap.put(SubscriptionTableSchema.DISPOSITION_FAIL, Color.RED);
		dispositionColorMap.put(SubscriptionTableSchema.DISPOSITION_JOURNAL, Color.MAGENTA);
		dispositionColorMap.put(SubscriptionTableSchema.DISPOSITION_PENDING, Color.rgb(255, 149, 28));
		dispositionColorMap.put(SubscriptionTableSchema.DISPOSITION_QUEUED, Color.CYAN);
		dispositionColorMap.put(SubscriptionTableSchema.DISPOSITION_SENT, Color.GREEN);
		
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
		int disposition = cursor.getInt(cursor.getColumnIndex(SubscriptionTableSchema.DISPOSITION));
		if (dispositionStateMap.containsKey(disposition)) {
			tv.setText(dispositionStateMap.get(disposition));
			tv.setTextColor(dispositionColorMap.get(disposition));
		} else {
			tv.setText("Unknown Disposition");
		}
		
		// deal with the displaying of the timestamp
		TextView ttv = (TextView)v.findViewById(R.id.distributor_table_view_item_timestamp);
		long timestamp = cursor.getLong(cursor.getColumnIndex(SubscriptionTableSchema.CREATED_DATE));
		this.expiration.clear();
		this.expiration.setTimeInMillis(timestamp);
		String timed = sdf.format(this.expiration.getTime());
		logger.debug("tuple timestamp {}",timed);
		ttv.setText(timed);
	}
	
    static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z");
}

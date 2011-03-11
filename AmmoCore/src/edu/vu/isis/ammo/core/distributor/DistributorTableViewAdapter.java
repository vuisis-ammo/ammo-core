package edu.vu.isis.ammo.core.distributor;

import java.util.HashMap;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.view.View;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

public class DistributorTableViewAdapter extends SimpleCursorAdapter {
	static private HashMap<Integer, String> dispositionMap = new HashMap<Integer, String>();
	static private HashMap<Integer, Integer> dispositionColor = new HashMap<Integer, Integer>();
	
	public DistributorTableViewAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to) {
		super(context, layout, c, from, to);

		// Setup hashmap.
		dispositionMap.put(SubscriptionTableSchema.DISPOSITION_EXPIRED, "Disposition Expired");
		dispositionMap.put(SubscriptionTableSchema.DISPOSITION_FAIL, "Disposition Failed");
		dispositionMap.put(SubscriptionTableSchema.DISPOSITION_JOURNAL, "Disposition Journal");
		dispositionMap.put(SubscriptionTableSchema.DISPOSITION_PENDING, "Disposition Pending");
		dispositionMap.put(SubscriptionTableSchema.DISPOSITION_QUEUED, "Disposition Queued");
		dispositionMap.put(SubscriptionTableSchema.DISPOSITION_SENT, "Disposition Sent");
		
		dispositionColor.put(SubscriptionTableSchema.DISPOSITION_EXPIRED, Color.LTGRAY);
		dispositionColor.put(SubscriptionTableSchema.DISPOSITION_FAIL, Color.RED);
		dispositionColor.put(SubscriptionTableSchema.DISPOSITION_JOURNAL, Color.MAGENTA);
		dispositionColor.put(SubscriptionTableSchema.DISPOSITION_PENDING, Color.rgb(255, 149, 28));
		dispositionColor.put(SubscriptionTableSchema.DISPOSITION_QUEUED, Color.CYAN);
		dispositionColor.put(SubscriptionTableSchema.DISPOSITION_SENT, Color.GREEN);}

	// ===========================================================
	// UI Management
	// ===========================================================
	// Override this to set disposition field.
	@Override
	public void bindView(View v, Context context, Cursor cursor) {
		super.bindView(v, context, cursor);
		this.bindDispositionToView(v, context, cursor);
	}
	
	
	private void bindDispositionToView(View v, Context context, Cursor cursor) {
		TextView tv = (TextView)v.findViewById(R.id.distributor_table_view_item_disposition);
		int disposition = cursor.getInt(cursor.getColumnIndex(SubscriptionTableSchema.DISPOSITION));
		if (dispositionMap.containsKey(disposition)) {
			tv.setText(dispositionMap.get(disposition));
			tv.setTextColor(dispositionColor.get(disposition));
		} else {
			tv.setText("Unknown Disposition");
		}
	}
}

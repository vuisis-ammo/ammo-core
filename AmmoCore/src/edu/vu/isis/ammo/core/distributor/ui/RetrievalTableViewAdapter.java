package edu.vu.isis.ammo.core.distributor.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;

/**
 * CursorAdapter used by AmmoCore to display its tables in a human-readable format.
 * Each row of the table view is formatted a certain way based on the disposition 
 * of the corresponding row in the content provider's table.
 */
public class RetrievalTableViewAdapter extends DistributorTableViewAdapter
{
	public static final Logger logger = LoggerFactory.getLogger(RetrievalTableViewAdapter.class);

	public RetrievalTableViewAdapter(Context context, int layout, Cursor cursor) 
	{
		super(context, layout, cursor);
	}

	// ===========================================================
	// UI Management
	// ===========================================================
	// Override this to set disposition field.

	@Override
	public void bindView(View v, Context context, Cursor cursor) {
		// super.bindView(v, context, cursor);

		// deal with the displaying of the disposition
		{
			final TextView tv = (TextView)v.findViewById(R.id.distributor_table_view_item_disposition);
			int disposition = cursor.getInt(cursor.getColumnIndex(RetrievalTableSchema.DISPOSITION.n));
			if (dispositionStateMap.containsKey(disposition)) {
				tv.setText(dispositionStateMap.get(disposition));
				tv.setTextColor(dispositionColorMap.get(disposition));
			} else {
				tv.setText("Unknown Disposition");
			}
		}

		// deal with the displaying of the timestamp
		{
			final TextView tv = (TextView)v.findViewById(R.id.distributor_table_view_item_timestamp);
			long timestamp = cursor.getLong(cursor.getColumnIndex(RetrievalTableSchema.CREATED.n));
			this.expiration.clear();
			this.expiration.setTimeInMillis(timestamp);
			String timed = SDF.format(this.expiration.getTime());
			logger.debug("tuple timestamp {}",timed);
			tv.setText(timed);
		}

		// set the mime-type / topic
		{
			final TextView tv = (TextView)v.findViewById(R.id.distributor_table_view_item_topic);
			tv.setText(cursor.getString(cursor.getColumnIndex(RetrievalTableSchema.TOPIC.n)));
		}

	}

}

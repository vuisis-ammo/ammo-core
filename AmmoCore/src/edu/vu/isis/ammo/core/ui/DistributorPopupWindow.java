package edu.vu.isis.ammo.core.ui;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.database.Cursor;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TableRow;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RequestDisposal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;



public class DistributorPopupWindow extends PopupWindow {

	private static final Logger logger = LoggerFactory.getLogger("DistributorPopupWindow");

	private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z");

	private enum FieldType { TXT, TIME, DISP; };

	private static class FieldProperty {
		public int id;
		public FieldType type;
		public FieldProperty(int id, FieldType type) {
			this.id = id;
			this.type = type;
		}
	};

	private final static HashMap<String, FieldProperty> postalMap;
	static {
		postalMap = new HashMap<String, FieldProperty>();
		postalMap.put(PostalTableSchema.PROVIDER.n, new FieldProperty(R.id.dist_detail_provider, FieldType.TXT));
		postalMap.put(PostalTableSchema.TOPIC.n, new FieldProperty(R.id.dist_detail_topic, FieldType.TXT));
		postalMap.put(PostalTableSchema.PAYLOAD.n, new FieldProperty(R.id.dist_detail_payload, FieldType.TXT));
		postalMap.put(PostalTableSchema.MODIFIED.n, new FieldProperty(R.id.dist_detail_modified, FieldType.TIME));
		postalMap.put(PostalTableSchema.CREATED.n, new FieldProperty(R.id.dist_detail_created, FieldType.TIME));
		postalMap.put(PostalTableSchema.PRIORITY.n, new FieldProperty(R.id.dist_detail_priority, FieldType.TXT));
		postalMap.put(PostalTableSchema.EXPIRATION.n, new FieldProperty(R.id.dist_detail_expiration, FieldType.TIME));
		postalMap.put(PostalTableSchema.DISPOSITION.n, new FieldProperty(R.id.dist_detail_disposal, FieldType.DISP));
	}

	private final static HashMap<String, FieldProperty> subscribeMap;
	static {
		subscribeMap = new HashMap<String, FieldProperty>();
		subscribeMap.put(SubscribeTableSchema.PROVIDER.n, new FieldProperty(R.id.dist_detail_provider, FieldType.TXT));
		subscribeMap.put(SubscribeTableSchema.TOPIC.n, new FieldProperty(R.id.dist_detail_topic, FieldType.TXT));
		subscribeMap.put(SubscribeTableSchema.SELECTION.n, new FieldProperty(R.id.dist_detail_selection, FieldType.TXT));
		subscribeMap.put(SubscribeTableSchema.MODIFIED.n, new FieldProperty(R.id.dist_detail_modified, FieldType.TIME));
		subscribeMap.put(SubscribeTableSchema.CREATED.n, new FieldProperty(R.id.dist_detail_created, FieldType.TIME));
		subscribeMap.put(SubscribeTableSchema.PRIORITY.n, new FieldProperty(R.id.dist_detail_priority, FieldType.TXT));
		subscribeMap.put(SubscribeTableSchema.EXPIRATION.n, new FieldProperty(R.id.dist_detail_expiration, FieldType.TIME));
		subscribeMap.put(SubscribeTableSchema.DISPOSITION.n, new FieldProperty(R.id.dist_detail_disposal, FieldType.DISP));
	}

	private final static HashMap<String, FieldProperty> retrievalMap;
	static {
		retrievalMap = new HashMap<String, FieldProperty>();
		retrievalMap.put(RetrievalTableSchema.PROVIDER.n, new FieldProperty(R.id.dist_detail_provider, FieldType.TXT));
		retrievalMap.put(RetrievalTableSchema.TOPIC.n, new FieldProperty(R.id.dist_detail_topic, FieldType.TXT));
		retrievalMap.put(RetrievalTableSchema.PROJECTION.n, new FieldProperty(R.id.dist_detail_projection, FieldType.TXT));
		retrievalMap.put(RetrievalTableSchema.SELECTION.n, new FieldProperty(R.id.dist_detail_selection, FieldType.TXT));
		retrievalMap.put(RetrievalTableSchema.ARGS.n, new FieldProperty(R.id.dist_detail_select_args, FieldType.TXT));
		retrievalMap.put(RetrievalTableSchema.MODIFIED.n, new FieldProperty(R.id.dist_detail_modified, FieldType.TIME));
		retrievalMap.put(RetrievalTableSchema.CREATED.n, new FieldProperty(R.id.dist_detail_created, FieldType.TIME));
		retrievalMap.put(RetrievalTableSchema.PRIORITY.n, new FieldProperty(R.id.dist_detail_priority, FieldType.TXT));
		retrievalMap.put(RetrievalTableSchema.EXPIRATION.n, new FieldProperty(R.id.dist_detail_expiration, FieldType.TIME));
		retrievalMap.put(RetrievalTableSchema.DISPOSITION.n, new FieldProperty(R.id.dist_detail_disposal, FieldType.DISP));
	}

	public boolean onKeyDown(int keyCode, KeyEvent event)  {
		logger.trace("BACK KEY PRESSED 2");
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			return true;
		}
		return false;
	}

	public DistributorPopupWindow(LayoutInflater inflater, int position, Cursor c, Tables table)
	{
		super(inflater.inflate(R.layout.dist_table_item_detail_view, null, false),600,400,true);
		c.moveToFirst();
		c.move(position);
		logger.trace("popup window {}", Arrays.asList(c.getColumnNames()) );

		final Map<String, FieldProperty> fieldMap;
		switch (table) {
		case POSTAL: fieldMap = postalMap; break;
		case SUBSCRIBE: fieldMap = subscribeMap; break;
		case RETRIEVAL: fieldMap = retrievalMap; break;
		default: 
			logger.error("invalid table {}", table);
			return;
		}
		for(final String colName : c.getColumnNames() ) {
			try {
				if (! fieldMap.containsKey(colName)) {
					logger.warn("missing field {}", colName);
					continue;
				}	
				final FieldProperty fp = fieldMap.get(colName);

				final TextView cell = ((TextView)this.getContentView().findViewById(fp.id));
				switch (fp.type){
				case TXT:
					cell.setText( c.getString(c.getColumnIndex(colName)));
					break;
				case TIME:
					final long timestamp = c.getLong(c.getColumnIndex(colName));
					if (timestamp < 1) {				
						continue; // an invalid time stamp, skip the field
					}
					final Calendar cal = Calendar.getInstance();
					cal.setTimeInMillis(timestamp);
					final String timed = SDF.format(cal.getTime());
					cell.setText(timed);
					break;
				case DISP:
					final int id = c.getInt(c.getColumnIndex(colName));
					final RequestDisposal disp = RequestDisposal.getInstanceById(id);
					cell.setText(disp.t);
					break;
				}
				final TableRow row = (TableRow) cell.getParent();
				row.setVisibility(View.VISIBLE);
			}
			catch(Exception e)
			{
				//((TextView)this.getContentView().findViewById(ids[i])).setText("");	
			}
		}
		// Now handle the channel disposition

	}


}

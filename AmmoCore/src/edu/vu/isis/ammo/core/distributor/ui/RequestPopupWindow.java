/*
Copyright(c) 2010-2012

This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under contract [contract citation, subcontract and prime contract]. 
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.

 */

package edu.vu.isis.ammo.core.distributor.ui;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TableRow;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PriorityType;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RequestDisposal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;
import edu.vu.isis.ammo.core.provider.DistributorSchema;



public class RequestPopupWindow extends PopupWindow {

	private static final Logger logger = LoggerFactory.getLogger(RequestPopupWindow.class);

	private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z");

	private enum FieldType { TEXT, TIMESTAMP, DISPOSITION, PRIORITY; };

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
		postalMap.put(PostalTableSchema.PROVIDER.n, new FieldProperty(R.id.dist_detail_provider, FieldType.TEXT));
		postalMap.put(PostalTableSchema.TOPIC.n, new FieldProperty(R.id.dist_detail_topic, FieldType.TEXT));
		postalMap.put(PostalTableSchema.PAYLOAD.n, new FieldProperty(R.id.dist_detail_payload, FieldType.TEXT));
		postalMap.put(PostalTableSchema.MODIFIED.n, new FieldProperty(R.id.dist_detail_modified, FieldType.TIMESTAMP));
		postalMap.put(PostalTableSchema.CREATED.n, new FieldProperty(R.id.dist_detail_created, FieldType.TIMESTAMP));
		postalMap.put(PostalTableSchema.PRIORITY.n, new FieldProperty(R.id.dist_detail_priority, FieldType.PRIORITY));
		postalMap.put(PostalTableSchema.EXPIRATION.n, new FieldProperty(R.id.dist_detail_expiration, FieldType.TIMESTAMP));
		postalMap.put(PostalTableSchema.DISPOSITION.n, new FieldProperty(R.id.dist_detail_disposal, FieldType.DISPOSITION));
	}

	private final static HashMap<String, FieldProperty> subscribeMap;
	static {
		subscribeMap = new HashMap<String, FieldProperty>();
		subscribeMap.put(SubscribeTableSchema.PROVIDER.n, new FieldProperty(R.id.dist_detail_provider, FieldType.TEXT));
		subscribeMap.put(SubscribeTableSchema.TOPIC.n, new FieldProperty(R.id.dist_detail_topic, FieldType.TEXT));
		subscribeMap.put(SubscribeTableSchema.SELECTION.n, new FieldProperty(R.id.
				dist_detail_selection, FieldType.TEXT));
		subscribeMap.put(SubscribeTableSchema.MODIFIED.n, new FieldProperty(R.id.dist_detail_modified, FieldType.TIMESTAMP));
		subscribeMap.put(SubscribeTableSchema.CREATED.n, new FieldProperty(R.id.dist_detail_created, FieldType.TIMESTAMP));
		subscribeMap.put(SubscribeTableSchema.PRIORITY.n, new FieldProperty(R.id.dist_detail_priority, FieldType.PRIORITY));
		subscribeMap.put(SubscribeTableSchema.EXPIRATION.n, new FieldProperty(R.id.dist_detail_expiration, FieldType.TIMESTAMP));
		subscribeMap.put(SubscribeTableSchema.DISPOSITION.n, new FieldProperty(R.id.dist_detail_disposal, FieldType.DISPOSITION));
	}

	private final static HashMap<String, FieldProperty> retrievalMap;
	static {
		retrievalMap = new HashMap<String, FieldProperty>();
		retrievalMap.put(RetrievalTableSchema.PROVIDER.n, new FieldProperty(R.id.dist_detail_provider, FieldType.TEXT));
		retrievalMap.put(RetrievalTableSchema.TOPIC.n, new FieldProperty(R.id.dist_detail_topic, FieldType.TEXT));
		retrievalMap.put(RetrievalTableSchema.PROJECTION.n, new FieldProperty(R.id.dist_detail_projection, FieldType.TEXT));
		retrievalMap.put(RetrievalTableSchema.SELECTION.n, new FieldProperty(R.id.dist_detail_selection, FieldType.TEXT));
		retrievalMap.put(RetrievalTableSchema.ARGS.n, new FieldProperty(R.id.dist_detail_select_args, FieldType.TEXT));
		retrievalMap.put(RetrievalTableSchema.MODIFIED.n, new FieldProperty(R.id.dist_detail_modified, FieldType.TIMESTAMP));
		retrievalMap.put(RetrievalTableSchema.CREATED.n, new FieldProperty(R.id.dist_detail_created, FieldType.TIMESTAMP));
		retrievalMap.put(RetrievalTableSchema.PRIORITY.n, new FieldProperty(R.id.dist_detail_priority, FieldType.PRIORITY));
		retrievalMap.put(RetrievalTableSchema.EXPIRATION.n, new FieldProperty(R.id.dist_detail_expiration, FieldType.TIMESTAMP));
		retrievalMap.put(RetrievalTableSchema.DISPOSITION.n, new FieldProperty(R.id.dist_detail_disposal, FieldType.DISPOSITION));
	}

	public boolean onKeyDown(int keyCode, KeyEvent event)  {
		logger.trace("BACK KEY PRESSED 2");
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			return true;
		}
		return false;
	}

	public RequestPopupWindow(final Activity activity, final LayoutInflater inflater, final int popupWidth, final int popupHeight, int position, 
			final Cursor requestCursor, Tables table)
	{
		super(inflater.inflate(R.layout.dist_table_item_detail_view, null, false),popupWidth,popupHeight,true);
		requestCursor.moveToFirst();
		requestCursor.move(position);
		logger.trace("popup window {}", Arrays.asList(requestCursor.getColumnNames()) );

		final Map<String, FieldProperty> fieldMap;
		switch (table) {
		case POSTAL: 
			fieldMap = postalMap; 
			break;
		case SUBSCRIBE: 
			fieldMap = subscribeMap; 
			break;
		case RETRIEVAL: 
			fieldMap = retrievalMap; 
			break;
		default: 
			logger.error("invalid table {}", table);
			return;
		}
		for(final String colName : requestCursor.getColumnNames() ) {
			if (! fieldMap.containsKey(colName)) {
				logger.warn("missing field {}", colName);
				continue;
			}	
			final FieldProperty fp = fieldMap.get(colName);

			final TextView cell = ((TextView)this.getContentView().findViewById(fp.id));
			switch (fp.type){
			case TEXT:
				final String text = requestCursor.getString(requestCursor.getColumnIndex(colName));
				if (text == null || text.length() < 1) {
					continue; // an empty text string, skip the field
				}
				cell.setText(text);
				break;
			case TIMESTAMP:
				final long timestamp = requestCursor.getLong(requestCursor.getColumnIndex(colName));
				if (timestamp < 1) {				
					continue; // an invalid time stamp, skip the field
				}
				final Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(timestamp);
				final String timed = SDF.format(cal.getTime());
				cell.setText(timed);
				break;
			case DISPOSITION:
				final int dispId = requestCursor.getInt(requestCursor.getColumnIndex(colName));
				final RequestDisposal disp = RequestDisposal.getInstanceById(dispId);
				cell.setText(disp.t);
				break;
			case PRIORITY:
				final int priorityId = requestCursor.getInt(requestCursor.getColumnIndex(colName));
				final PriorityType priority = PriorityType.getInstanceById(priorityId);
				cell.setText(priority.toString(priorityId));
				break;
			}
			final TableRow row = (TableRow) cell.getParent();
			row.setVisibility(View.VISIBLE);
		}
		// Now handle the channel disposition
		final Uri channelUri = DistributorSchema.CONTENT_URI.get(DistributorDataStore.Tables.DISPOSAL.n);
		final int id = requestCursor.getInt(requestCursor.getColumnIndex(BaseColumns._ID));
		// final Uri disposalUri = ContentUris.withAppendedId(channelUri, id);

		final Cursor channelCursor = activity.managedQuery(channelUri, null, 
				CHANNEL_SELECTION, new String[]{ String.valueOf(table.o), String.valueOf(id) }, 
				null);

		final ListView list = ((ListView)this.getContentView().findViewById(R.id.dist_channel_content));
		final CursorAdapter channelDetailAdaptor = new DisposalTableAdapter(activity, 
				R.layout.dist_channel_item, channelCursor); 
		list.setAdapter(channelDetailAdaptor);

	}
	static final private String CHANNEL_SELECTION = new StringBuilder()
	.append(DisposalTableSchema.TYPE.q()).append("=?")
	.append(" AND ").append(DisposalTableSchema.PARENT.q()).append("=?")
	.toString();



}

package edu.vu.isis.ammo.core.distributor;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import edu.vu.isis.ammo.IAmmoActivitySetup;
import edu.vu.isis.ammo.collector.provider.IncidentSchema.EventTableSchema;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.SubscriptionStatus;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

public class DistributorTableViewer extends ListActivity implements
		IAmmoActivitySetup {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final int MENU_PURGE = 0;
	public static final int MENU_CONTEXT_DELETE = 1;

	static private String[] fromItemLayout = new String[] {
			SubscriptionTableSchema.URI,
			// SubscriptionTableSchema.PROJECTION ,
			// SubscriptionTableSchema.SELECTION ,
			// SubscriptionTableSchema.ARGS ,
			// SubscriptionTableSchema.ORDER ,
			// SubscriptionTableSchema.EXPIRATION ,
			// SubscriptionTableSchema.CREATED_DATE ,
			SubscriptionTableSchema.CREATED_DATE };

	static private int[] toItemLayout = new int[] {
			R.id.distributor_table_view_item_uri,
			R.id.distributor_table_view_item_timestamp };
	// ===========================================================
	// Fields
	// ===========================================================
	Logger logger = LoggerFactory.getLogger(SubscriptionStatus.class);
	Uri uri;
	private TextView tvLabel;
	private DistributorTableViewAdapter adapter;

	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		setContentView(R.layout.distributor_table_viewer);
		Intent i = getIntent();
		uri = (Uri) i.getParcelableExtra("uri");
		if (uri == null) {
			logger.error("no uri provided...exiting");
			return;
		}

		String[] projection = {SubscriptionTableSchema._ID, 
				SubscriptionTableSchema.DISPOSITION,
				SubscriptionTableSchema.URI, 
				SubscriptionTableSchema.CREATED_DATE};
		Cursor cursor = this.managedQuery(uri, projection, null, null, null);
		adapter = new DistributorTableViewAdapter(this,
				R.layout.distributor_table_view_item, cursor, fromItemLayout,
				toItemLayout);
		setListAdapter(adapter);
		registerForContextMenu(this.getListView());
	}

	// ===========================================================
	// Menus
	// ===========================================================
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		logger.trace("::onCreateOptionsMenu");
		menu.add(Menu.NONE, MENU_PURGE, Menu.NONE, "Purge");
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		logger.trace("::onOptionsItemSelected");
		switch (item.getItemId()) {
		case MENU_PURGE:
			// Delete everything.
			int count = getContentResolver().delete(uri, "_id > -1", null);
			logger.debug("Deleted " + count + "subscriptions");
		}
		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(ContextMenu.NONE, MENU_CONTEXT_DELETE, Menu.NONE, "Delete");
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
		switch (item.getItemId()) {
		case MENU_CONTEXT_DELETE:
			removeMenuItem(item);
			adapter.notifyDataSetChanged();
			break;

		default:

		}
		return true;
	}

	// ===========================================================
	// List Management
	// ===========================================================
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
	}
	
	public void removeMenuItem(MenuItem item) {
		// Get the row id and uri of the selected item.
		AdapterContextMenuInfo acmi = (AdapterContextMenuInfo)item.getMenuInfo();
		int rowId = (int)acmi.id;
		int count = getContentResolver().delete(uri, SubscriptionTableSchema._ID + "=" + String.valueOf(rowId), null);
		Toast.makeText(this, "Removed " + String.valueOf(count) + " entry", Toast.LENGTH_SHORT).show();

	}

	// ===========================================================
	// Activity setup
	// ===========================================================

	@Override
	public void setOnClickListeners() {

	}

	@Override
	public void setViewAttributes() {

		if (uri.equals(PostalTableSchema.CONTENT_URI)) {
			tvLabel.setText("Postal Table");
		} else if (uri.equals(RetrievalTableSchema.CONTENT_URI)) {
			tvLabel.setText("Retrieval Table");
		} else if (uri.equals(SubscriptionTableSchema.CONTENT_URI)) {
			tvLabel.setText("Subscription Table");
		} else {
			tvLabel.setText("Unknown");
		}
	}

	@Override
	public void setViewReferences() {
		tvLabel = (TextView) findViewById(R.id.distributor_table_viewer_label);
	}

}

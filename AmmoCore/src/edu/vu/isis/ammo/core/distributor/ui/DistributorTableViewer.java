package edu.vu.isis.ammo.core.distributor.ui;

import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ListActivity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import edu.vu.isis.ammo.IAmmoActivitySetup;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

/**
 * ListActivity class used in viewing the distributor's tables.
 * @author Fred Eisele
 *
 */
public abstract class DistributorTableViewer extends ListActivity 
implements IAmmoActivitySetup 
{
	// ===========================================================
	// Constants
	// ===========================================================
	static private final Logger logger = LoggerFactory.getLogger(DistributorTableViewer.class);
	
	private static final int MENU_PURGE = 1;
	private static final int MENU_GARBAGE = 2;
	
	public static final int MENU_CONTEXT_DELETE = 1;

	static private final String[] fromItemLayout = new String[] {
			SubscriptionTableSchema.URI,
			// SubscriptionTableSchema.PROJECTION ,
			// SubscriptionTableSchema.SELECTION ,
			// SubscriptionTableSchema.ARGS ,
			// SubscriptionTableSchema.ORDER ,
			// SubscriptionTableSchema.EXPIRATION ,
			// SubscriptionTableSchema.CREATED_DATE ,
			SubscriptionTableSchema.CREATED_DATE };

	static private final int[] toItemLayout = new int[] {
			R.id.distributor_table_view_item_uri,
			R.id.distributor_table_view_item_timestamp };
	// ===========================================================
	// Fields
	// ===========================================================
	
	protected Uri uri;
	private DistributorTableViewAdapter adapter;

	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		setContentView(R.layout.distributor_table_viewer);
		
		if (this.uri == null) {
			logger.error("no uri provided...exiting");
			return;
		}

		String[] projection = {SubscriptionTableSchema._ID, 
				SubscriptionTableSchema.DISPOSITION,
				SubscriptionTableSchema.URI, 
				SubscriptionTableSchema.CREATED_DATE};
		Cursor cursor = this.managedQuery(this.uri, projection, null, null, null);
		this.adapter = new DistributorTableViewAdapter(this,
				R.layout.distributor_table_view_item, cursor, fromItemLayout,
				toItemLayout);
		this.setListAdapter(adapter);
		this.registerForContextMenu(this.getListView());
	}

	// ===========================================================
	// Menus
	// ===========================================================
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		logger.trace("::onCreateOptionsMenu");
		menu.add(Menu.NONE, MENU_PURGE, Menu.NONE, "Purge");
		menu.add(Menu.NONE, MENU_GARBAGE, Menu.NONE+1, "Garbage");
		return true;
	}

	protected String completeDisp = null;
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		logger.trace("::onOptionsItemSelected");
		int count;
		switch (item.getItemId()) {
		case MENU_PURGE:
			// Delete everything.
			count = getContentResolver().delete(this.uri, "_id > -1", null);
			logger.debug("Deleted " + count + "subscriptions");
			break;
		case MENU_GARBAGE:
			// Delete things which are outdated or complete everything.
			StringBuilder sb = new StringBuilder();
			sb.append("_id > -1");
			sb.append(" AND ");
			sb.append(" expiration < '").append(Calendar.getInstance().getTimeInMillis()).append("'");
			if (this.completeDisp != null)
				sb.append(" AND ").append(" disposition IN ").append(this.completeDisp);
			
			count = getContentResolver().delete(this.uri, sb.toString(), null);
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
		int count = getContentResolver().delete(this.uri, SubscriptionTableSchema._ID + "=" + String.valueOf(rowId), null);
		Toast.makeText(this, "Removed " + String.valueOf(count) + " entry", Toast.LENGTH_SHORT).show();
	}

	// ===========================================================
	// Activity setup
	// ===========================================================

	@Override
	public void setOnClickListeners() {

	}

}

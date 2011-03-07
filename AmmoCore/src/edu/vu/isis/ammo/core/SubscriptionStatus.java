/**
 * 
 */
package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

/**
 * This activity shows a list of the items and their subscription status.
 * Eventually it will allow the operator to perform various management operations.
 * These management operations will be available via content menus.
 * 
 * @author phreed
 */
public class SubscriptionStatus extends ListActivity {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final String LAUNCH = "edu.vu.isis.ammo.core.SubscriptionStatus.LAUNCH";
	public static final int MENU_PURGE_SUBSCRIPTIONS = Menu.FIRST;
	
	// ===========================================================
	// Fields
	// ===========================================================
	Logger logger = LoggerFactory.getLogger(SubscriptionStatus.class);
	
	
	/**
	 * 
	 */
	static private String[] fromItemLayout = new String[] {
		 SubscriptionTableSchema.URI ,
		 //SubscriptionTableSchema.PROJECTION ,
		 //SubscriptionTableSchema.SELECTION ,
		 //SubscriptionTableSchema.ARGS ,
		 //SubscriptionTableSchema.ORDER ,
		 //SubscriptionTableSchema.EXPIRATION ,
		 //SubscriptionTableSchema.CREATED_DATE ,
		 SubscriptionTableSchema.MODIFIED_DATE 
	};
	
	static private int[] toItemLayout = new int[] {
			R.id.subscription_status_uri,
			R.id.subscription_status_timestamp
	};
	
	@Override
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		setContentView(R.layout.subscription_status_view);
		
		//statusList = (ListView) findViewById(R.id.subscription_status_list);
		final Cursor cursor = this.managedQuery(SubscriptionTableSchema.CONTENT_URI, null, null, null, null);
		final ListAdapter adapter = new SimpleCursorAdapter(this,
				R.layout.subscription_status_item,
				cursor, fromItemLayout, toItemLayout);
		
		setListAdapter(adapter);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		logger.trace("::onCreateOptionsMenu");
		menu.add(Menu.NONE, MENU_PURGE_SUBSCRIPTIONS, Menu.NONE, "Purge Subscriptions");
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		logger.trace("::onOptionsItemSelected");
		switch(item.getItemId()) {
		case MENU_PURGE_SUBSCRIPTIONS:
			int count = getContentResolver().delete(SubscriptionTableSchema.CONTENT_URI, SubscriptionTableSchema._ID + ">" + String.valueOf(-1), null);
			logger.debug("Deleted " + count + "subscriptions");
		}
		return true;
	}
	
	@Override 
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Toast.makeText(SubscriptionStatus.this, "no on click behavior available", Toast.LENGTH_SHORT).show();
	}

}

/**
 * 
 */
package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.ui.util.ListActivityEx;

/**
 * This activity shows a list of the items and their subscription status.
 * Eventually it will allow the operator to perform various management operations.
 * These management operations will be available via content menus.
 * 
 * @author phreed
 */
public class SubscriptionStatus extends ListActivityEx {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger(SubscriptionStatus.class);
	
	public static final String LAUNCH = "edu.vu.isis.ammo.core.SubscriptionStatus.LAUNCH";
	public static final int MENU_PURGE_SUBSCRIPTIONS = Menu.FIRST;
	
	// ===========================================================
	// Fields
	// ===========================================================
	
	/**
	 * 
	 */
	static private String[] fromItemLayout = new String[] {
		 SubscribeTableSchema.PROVIDER.n ,
		 //SubscribeTableSchema.PROJECTION ,
		 //SubscribeTableSchema.SELECTION ,
		 //SubscribeTableSchema.ARGS ,
		 //SubscribeTableSchema.ORDER ,
		 //SubscribeTableSchema.EXPIRATION ,
		 //SubscribeTableSchema.CREATED_DATE ,
		 SubscribeTableSchema.MODIFIED.n
	};
	
	static private int[] toItemLayout = new int[] {
			R.id.distributor_table_view_item_uri,
			R.id.distributor_table_view_item_timestamp
	};
	
	@Override
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		setContentView(R.layout.dist_table_viewer_activity);
		
		//statusList = (ListView) findViewById(R.id.subscription_status_list);
		/*
		final Cursor cursor = this.managedQuery(SubscribeTableSchema.CONTENT_URI, null, null, null, null);
		final ListAdapter adapter = new SimpleCursorAdapter(this,
				R.layout.distributor_table_view_item,
				cursor, fromItemLayout, toItemLayout);
		
		setListAdapter(adapter);
		*/
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
			/*
			int count = getContentResolver().delete(SubscribeTableSchema.CONTENT_URI, SubscribeTableSchema._ID + ">" + String.valueOf(-1), null);
			logger.debug("Deleted " + count + "subscriptions");
			*/
		}
		return true;
	}
	
	@Override 
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Toast.makeText(SubscriptionStatus.this, "no on click behavior available", Toast.LENGTH_SHORT).show();
	}

}

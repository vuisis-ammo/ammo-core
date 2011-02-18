/**
 * 
 */
package edu.vu.isis.ammo.core;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
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
	
	// ===========================================================
	// Fields
	// ===========================================================
	
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
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Toast.makeText(SubscriptionStatus.this, "no on click behavior available", Toast.LENGTH_SHORT).show();
	}

}

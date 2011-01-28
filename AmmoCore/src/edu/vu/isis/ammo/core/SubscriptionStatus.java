/**
 * 
 */
package edu.vu.isis.ammo.core;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
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
public class SubscriptionStatus extends Activity {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final String LAUNCH = "edu.vu.isis.ammo.core.SubscriptionStatus.LAUNCH";
	
	// ===========================================================
	// Fields
	// ===========================================================
	private ListView statusList;
	
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
		
		statusList = (ListView) findViewById(R.id.subscription_status_list);
		final Cursor cursor = this.managedQuery(SubscriptionTableSchema.CONTENT_URI, null, null, null, null);
		
		final SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
				R.layout.subscription_status_item,
				cursor, fromItemLayout, toItemLayout);
		
		statusList.setAdapter(adapter);
		
		statusList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
				Toast.makeText(SubscriptionStatus.this, "no on click behavior available", Toast.LENGTH_SHORT).show();	
			}
		});

	}

}

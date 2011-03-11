package edu.vu.isis.ammo.core.distributor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.SubscriptionStatus;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

public class DistributorTableViewer extends ListActivity {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final int MENU_PURGE = 0;
	
	// ===========================================================
	// Fields
	// ===========================================================
	Logger logger = LoggerFactory.getLogger(SubscriptionStatus.class);
	Uri uri;
	
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
		 SubscriptionTableSchema.CREATED_DATE 
	};
	
	static private int[] toItemLayout = new int[] {
			R.id.subscription_status_uri,
			R.id.subscription_status_timestamp
	};
	
	@Override
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		setContentView(R.layout.distributor_table_viewer);
		
		Intent i = getIntent();
		uri = (Uri)i.getParcelableExtra("uri");
		if (uri == null) {
			logger.error("no uri provided...exiting");
			return;
		}
		
		TextView tv = (TextView)findViewById(R.id.distributor_table_viewer_label);
		if (uri.equals(PostalTableSchema.CONTENT_URI)) {
			tv.setText("Postal Table");
		} else if (uri.equals(RetrievalTableSchema.CONTENT_URI)) {
			tv.setText("Retrieval Table");
		} else if (uri.equals(SubscriptionTableSchema.CONTENT_URI)) {
			tv.setText("Subscription Table");
		} else {
			tv.setText("Unknown");
		}
		
		
		//statusList = (ListView) findViewById(R.id.subscription_status_list);
		final Cursor cursor = this.managedQuery(uri, null, null, null, null);
		final ListAdapter adapter = new SimpleCursorAdapter(this,
				R.layout.subscription_status_item,
				cursor, fromItemLayout, toItemLayout);
		
		setListAdapter(adapter);
	}
	
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
		switch(item.getItemId()) {
		case MENU_PURGE:
			// Delete everything.
			int count = getContentResolver().delete(uri, "_id > -1", null);
			logger.debug("Deleted " + count + "subscriptions");
		}
		return true;
	}
	
	@Override 
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
	}

}

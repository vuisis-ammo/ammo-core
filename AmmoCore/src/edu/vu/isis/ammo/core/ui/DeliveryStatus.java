/**
 * 
 */
package edu.vu.isis.ammo.core.ui;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.R.id;
import edu.vu.isis.ammo.core.R.layout;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;

/**
 * This activity shows a list of the items and their delivery status.
 * Eventually it will allow the operator to perform various management operations.
 * 
 * @author phreed
 */
public class DeliveryStatus extends Activity {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final String LAUNCH = "edu.vu.isis.ammo.core.DeliveryStatus.LAUNCH";
	
	// ===========================================================
	// Fields
	// ===========================================================
	private ListView statusList;
	
	/**
	 * 
	 */
	static private String[] fromItemLayout = new String[] {
			//PostalTableSchema.CP_TYPE ,
			PostalTableSchema.URI ,
			PostalTableSchema.DISPOSITION ,
			//PostalTableSchema.EXPIRATION ,
			//PostalTableSchema.UNIT ,
			//PostalTableSchema.VALUE ,
			//PostalTableSchema.CREATED_DATE ,
			PostalTableSchema.MODIFIED_DATE 
	};
	
	static private int[] toItemLayout = new int[] {
			R.id.delivery_status_uri,
			R.id.delivery_status_value,
			R.id.delivery_status_timestamp
	};
	
	@Override
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		setContentView(R.layout.delivery_status_view);
		
		statusList = (ListView) findViewById(R.id.delivery_status_list);
		final Cursor cursor = this.managedQuery(PostalTableSchema.CONTENT_URI, null, null, null, null);
		
		final SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
				R.layout.delivery_status_item,
				cursor, fromItemLayout, toItemLayout);
		
		statusList.setAdapter(adapter);
		
		statusList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
				Toast.makeText(DeliveryStatus.this, "no on click behavior available", Toast.LENGTH_SHORT).show();	
			}
		});

	}

}

package edu.vu.isis.ammo.core.distributor.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;
import edu.vu.isis.ammo.core.provider.DistributorSchema;

public class SubscribeTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	static protected final String[] fromItemLayout = new String[] {
		SubscribeTableSchema.PROVIDER.n,
		// SubscribeTableSchema.PROJECTION ,
		// SubscribeTableSchema.SELECTION ,
		// SubscribeTableSchema.ARGS ,
		// SubscribeTableSchema.ORDER ,
		// SubscribeTableSchema.EXPIRATION ,
		// SubscribeTableSchema.MODIFIED ,
		SubscribeTableSchema.CREATED.n };

	public SubscribeTableViewer() {
		super(Tables.SUBSCRIBE);
	}
	
	@Override 
	public void onCreate(Bundle bun) {
		this.uri = DistributorSchema.CONTENT_URI.get(DistributorDataStore.Tables.SUBSCRIBE.n);
		
		final Cursor cursor = this.managedQuery(this.uri, null, null, null, 
                SubscribeTableSchema._ID + " DESC");
		
		this.adapter = new SubscribeDistributorTableViewAdapter(this,
				R.layout.dist_table_view_item, cursor, 
				fromItemLayout, toItemLayout);
		
		super.onCreate(bun);
	}

	@Override
	public void setViewAttributes() {
		tvLabel.setText("Subscription Table");
	}
	

	@Override
	public void setViewReferences() {
		tvLabel = (TextView) findViewById(R.id.distributor_table_viewer_label);
	}


}

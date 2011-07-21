package edu.vu.isis.ammo.core.distributor.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

public class SubscribeTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	static protected final String[] fromItemLayout = new String[] {
		SubscriptionTableSchema.URI,
		// SubscriptionTableSchema.PROJECTION ,
		// SubscriptionTableSchema.SELECTION ,
		// SubscriptionTableSchema.ARGS ,
		// SubscriptionTableSchema.ORDER ,
		// SubscriptionTableSchema.EXPIRATION ,
		// SubscriptionTableSchema.CREATED_DATE ,
		SubscriptionTableSchema.CREATED_DATE };

	@Override 
	public void onCreate(Bundle bun) {
		this.uri = SubscriptionTableSchema.CONTENT_URI;
		
		@SuppressWarnings("unused")
		String[] projection = {SubscriptionTableSchema._ID, 
				SubscriptionTableSchema.DISPOSITION,
				SubscriptionTableSchema.URI, 
				SubscriptionTableSchema.CREATED_DATE,
				SubscriptionTableSchema.MIME};
		
		Cursor cursor = this.managedQuery(this.uri, null, null, null, 
                SubscriptionTableSchema._ID + " DESC");
		
		
		this.adapter = new SubscribeDistributorTableViewAdapter(this,
				R.layout.distributor_table_view_item, cursor, 
				fromItemLayout, toItemLayout);
		
		super.onCreate(bun);
		
		StringBuilder sb = new StringBuilder();
		sb.append("(").append(SubscriptionTableSchema.DISPOSITION_EXPIRED);
		sb.append(",").append(SubscriptionTableSchema.DISPOSITION_FAIL).append(")");
	    this.completeDisp = sb.toString();
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

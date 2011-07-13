package edu.vu.isis.ammo.core.distributor.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Disposition;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;

public class SubscribeTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	static protected final String[] fromItemLayout = new String[] {
		SubscribeTableSchema.PROVIDER.n,
		// SubscribeTableSchema.PROJECTION ,
		// SubscribeTableSchema.SELECTION ,
		// SubscribeTableSchema.ARGS ,
		// SubscribeTableSchema.ORDER ,
		// SubscribeTableSchema.EXPIRATION ,
		// SubscribeTableSchema.CREATED_DATE ,
		SubscribeTableSchema.CREATED.n };

	@Override 
	public void onCreate(Bundle bun) {
		this.ds.openRead();
		
		final String[] projection = {SubscribeTableSchema._ID.n, 
				SubscribeTableSchema.DISPOSITION.n,
				SubscribeTableSchema.PROVIDER.n, 
				SubscribeTableSchema.CREATED.n,
				SubscribeTableSchema.DATA_TYPE.n};
		
		Cursor cursor = this.ds.querySubscribe(projection, null, null, 
                SubscribeTableSchema._ID.n + " DESC");
		
		
		this.adapter = new SubscribeDistributorTableViewAdapter(this,
				R.layout.distributor_table_view_item, cursor, 
				fromItemLayout, toItemLayout);
		
		super.onCreate(bun);
		
		StringBuilder sb = new StringBuilder();
		sb.append("(").append(Disposition.EXPIRED.t);
		sb.append(",").append(Disposition.FAIL.t).append(")");
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

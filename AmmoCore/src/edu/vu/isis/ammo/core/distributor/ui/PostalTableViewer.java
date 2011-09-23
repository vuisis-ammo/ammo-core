package edu.vu.isis.ammo.core.distributor.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema;

public class PostalTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	
	static protected final String[] fromItemLayout = new String[] {
		PostalTableSchema.PROVIDER.n,
		// PostalTableSchema.PROJECTION ,
		// PostalTableSchema.SELECTION ,
		// PostalTableSchema.ARGS ,
		// PostalTableSchema.ORDER ,
		// PostalTableSchema.EXPIRATION ,
		// PostalTableSchema.CREATED_DATE ,
		PostalTableSchema.CREATED.n };
	
	@Override 
	public void onCreate(Bundle bun) {
		this.uri = DistributorSchema.CONTENT_URI.get(DistributorDataStore.Tables.POSTAL.n);
		
		Cursor cursor = this.managedQuery(this.uri, null, null, null, 
                PostalTableSchema._ID + " DESC");

		this.adapter = new PostalDistributorTableViewAdapter(this,
				R.layout.distributor_table_view_item, cursor, 
				fromItemLayout, toItemLayout);
		
		super.onCreate(bun);
		/*
		StringBuilder sb = new StringBuilder();
		sb.append("(").append(PostalTableSchema.DISPOSITION_SATISFIED);
		sb.append(",").append(PostalTableSchema.DISPOSITION_EXPIRED);
		sb.append(",").append(PostalTableSchema.DISPOSITION_FAIL).append(")");
	    this.completeDisp = sb.toString();
	    */
	}
	@Override
	public void setViewAttributes() {
		tvLabel.setText("Postal Table");
	}
	

	@Override
	public void setViewReferences() {
		tvLabel = (TextView) findViewById(R.id.distributor_table_viewer_label);
	}


}

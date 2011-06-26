package edu.vu.isis.ammo.core.distributor.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;

public class RetrievalTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	
	static protected final String[] fromItemLayout = new String[] {
		RetrievalTableSchema.URI,
		// RetrievalTableSchema.PROJECTION ,
		// RetrievalTableSchema.SELECTION ,
		// RetrievalTableSchema.ARGS ,
		// RetrievalTableSchema.ORDER ,
		// RetrievalTableSchema.EXPIRATION ,
		// RetrievalTableSchema.CREATED_DATE ,
		RetrievalTableSchema.CREATED_DATE };
	
	@Override 
	public void onCreate(Bundle bun) {
		this.uri = RetrievalTableSchema.CONTENT_URI;
		
		String[] projection = {RetrievalTableSchema._ID, 
				RetrievalTableSchema.DISPOSITION,
				RetrievalTableSchema.URI, 
				RetrievalTableSchema.CREATED_DATE};
		
		Cursor cursor = this.managedQuery(this.uri, projection, null, null, 
                RetrievalTableSchema._ID + " DESC");
		
		this.adapter = new RetrievalDistributorTableViewAdapter(this,
				R.layout.distributor_table_view_item, cursor, 
				fromItemLayout, toItemLayout);
		
		super.onCreate(bun);
		
		StringBuilder sb = new StringBuilder();
		sb.append("(").append(RetrievalTableSchema.DISPOSITION_SATISFIED);
		sb.append(",").append(RetrievalTableSchema.DISPOSITION_EXPIRED);
		sb.append(",").append(RetrievalTableSchema.DISPOSITION_FAIL).append(")");
	    this.completeDisp = sb.toString();
	}
	
	@Override
	public void setViewAttributes() {
		tvLabel.setText("Retrieval Table");
	}
	

	@Override
	public void setViewReferences() {
		tvLabel = (TextView) findViewById(R.id.distributor_table_viewer_label);
	}


}

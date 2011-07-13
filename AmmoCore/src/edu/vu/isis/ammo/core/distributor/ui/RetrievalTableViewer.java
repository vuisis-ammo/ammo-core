package edu.vu.isis.ammo.core.distributor.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Disposition;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;

public class RetrievalTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	
	static protected final String[] fromItemLayout = new String[] {
		RetrievalTableSchema.PROVIDER.n,
		// RetrievalTableSchema.PROJECTION ,
		// RetrievalTableSchema.SELECTION ,
		// RetrievalTableSchema.ARGS ,
		// RetrievalTableSchema.ORDER ,
		// RetrievalTableSchema.EXPIRATION ,
		// RetrievalTableSchema.CREATED_DATE ,
		RetrievalTableSchema.CREATED.n };
	
	@Override 
	public void onCreate(Bundle bun) {
		this.ds.openRead();
		
		final String[] projection = {RetrievalTableSchema._ID.n, 
				RetrievalTableSchema.DISPOSITION.n,
				RetrievalTableSchema.PROVIDER.n, 
				RetrievalTableSchema.CREATED.n};
		
		Cursor cursor = this.ds.queryRetrieval(projection, null, null, 
                RetrievalTableSchema._ID + " DESC");

		
		this.adapter = new RetrievalDistributorTableViewAdapter(this,
				R.layout.distributor_table_view_item, cursor, 
				fromItemLayout, toItemLayout);
		
		super.onCreate(bun);
		
		StringBuilder sb = new StringBuilder();
		sb.append("(").append(Disposition.SATISFIED.o);
		sb.append(",").append(Disposition.EXPIRED.o);
		sb.append(",").append(Disposition.FAIL.o).append(")");
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

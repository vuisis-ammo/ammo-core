package edu.vu.isis.ammo.core.distributor.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RetrievalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;
import edu.vu.isis.ammo.core.provider.DistributorSchema;

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
	
	public RetrievalTableViewer() {
		super(Tables.RETRIEVAL);
	}
	
	@Override 
	public void onCreate(Bundle bun) {
		this.uri = DistributorSchema.CONTENT_URI.get(DistributorDataStore.Tables.RETRIEVAL.n);
		
		final Cursor cursor = this.managedQuery(this.uri, null, null, null, 
                RetrievalTableSchema._ID + " DESC");

		this.adapter = new RetrievalDistributorTableViewAdapter(this,
				R.layout.dist_table_view_item, cursor, 
				fromItemLayout, toItemLayout);
		
		super.onCreate(bun);
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

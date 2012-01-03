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
	
	public RetrievalTableViewer() {
		super(Tables.RETRIEVAL);
	}
	
	@Override 
	public void onCreate(Bundle bun) {
		this.uri = DistributorSchema.CONTENT_URI.get(DistributorDataStore.Tables.RETRIEVAL.n);
		
		final Cursor cursor = this.managedQuery(this.uri, null, null, null, 
                RetrievalTableSchema._ID + " DESC");

		this.adapter = new RetrievalTableViewAdapter(this, R.layout.dist_table_view_item, cursor);
		
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

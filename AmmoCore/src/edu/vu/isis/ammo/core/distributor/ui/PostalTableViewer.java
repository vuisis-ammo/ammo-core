package edu.vu.isis.ammo.core.distributor.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.PostalTableSchema;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;
import edu.vu.isis.ammo.core.provider.DistributorSchema;

public class PostalTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	
	public PostalTableViewer() {
		super(Tables.POSTAL);
	}
	
	@Override 
	public void onCreate(Bundle bun) {
		this.uri = DistributorSchema.CONTENT_URI.get(DistributorDataStore.Tables.POSTAL.n);
		
		final Cursor cursor = this.managedQuery(this.uri, null, null, null, 
                PostalTableSchema._ID + " DESC");

		this.adapter = new PostalTableViewAdapter(this, R.layout.dist_table_view_item, cursor);
		
		super.onCreate(bun);
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

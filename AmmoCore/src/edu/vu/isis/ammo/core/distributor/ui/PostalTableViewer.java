package edu.vu.isis.ammo.core.distributor.ui;

import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;

public class PostalTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	
	@Override 
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		this.uri = PostalTableSchema.CONTENT_URI;
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

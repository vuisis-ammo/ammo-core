package edu.vu.isis.ammo.core.distributor.ui;

import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

public class SubscribeTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	
	@Override 
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		this.uri = SubscriptionTableSchema.CONTENT_URI;
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

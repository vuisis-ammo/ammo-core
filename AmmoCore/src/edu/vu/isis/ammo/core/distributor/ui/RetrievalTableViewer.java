package edu.vu.isis.ammo.core.distributor.ui;

import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;

public class RetrievalTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	
	@Override 
	public void onCreate(Bundle bun) {
		this.uri = RetrievalTableSchema.CONTENT_URI;
		super.onCreate(bun);
		
		StringBuilder sb = new StringBuilder();
		sb.append("{").append(RetrievalTableSchema.DISPOSITION_SATISFIED);
		sb.append(",").append(RetrievalTableSchema.DISPOSITION_EXPIRED);
		sb.append(",").append(RetrievalTableSchema.DISPOSITION_FAIL).append("}");
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

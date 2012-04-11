/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.core.distributor.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema;
import edu.vu.isis.ammo.core.store.DistributorDataStore.RequestField;
import edu.vu.isis.ammo.core.store.Tables;

public class PostalTableViewer extends DistributorTableViewer {

	private TextView tvLabel;
	
	public PostalTableViewer() {
		super(Tables.POSTAL);
	}
	
	@Override 
	public void onCreate(Bundle bun) {
		this.uri = DistributorSchema.CONTENT_URI.get(Tables.POSTAL.n);
		
		final Cursor cursor = this.managedQuery(this.uri, null, null, null, 
                RequestField._ID + " DESC");

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

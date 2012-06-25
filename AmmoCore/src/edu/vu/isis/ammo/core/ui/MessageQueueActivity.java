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
package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.store.Relations;
import edu.vu.isis.ammo.core.provider.DistributorSchema;

public class MessageQueueActivity extends Activity {
	public static final Logger logger = LoggerFactory.getLogger("ui.messagequeue");

	// ===============================================
	// Activity Lifecycle
	// ===============================================
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message_queue_activity);
        setupView();
        setOnClickListeners();
        
        @SuppressWarnings("unused")
		String tableName = Relations.DISPOSAL.n;
        @SuppressWarnings("unused")
		Uri uri = DistributorSchema.CONTENT_URI.get(Relations.CHANNEL.n);
        Cursor c = this.getContentResolver().query(DistributorSchema.CONTENT_URI.get(Relations.DISPOSAL.n), null, null, null, null);
        logger.trace("message{}", c);
        
        
        while(c.moveToNext()) {
        	String channelName = c.getString(c.getColumnIndex(DistributorDataStore.DisposalTableSchema.CHANNEL.n));
        	logger.debug(channelName);
        }
	}
	
	// ===============================================
	// UIInit interface
	// ===============================================
	public void setupView() {
		
	}
	
	public void setOnClickListeners() {
		
	}
}

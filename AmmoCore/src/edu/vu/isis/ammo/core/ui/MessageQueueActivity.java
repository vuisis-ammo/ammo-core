package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.provider.DistributorSchema;

public class MessageQueueActivity extends Activity {
	public static final Logger logger = LoggerFactory.getLogger("Ammo-MQA");

	// ===============================================
	// Activity Lifecycle
	// ===============================================
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message_queue_activity);
        setupView();
        setOnClickListeners();
        
        String tableName = DistributorDataStore.Tables.DISPOSAL.n;
        Uri uri = DistributorSchema.CONTENT_URI.get(DistributorDataStore.Tables.CHANNEL.n);
        Cursor c = this.getContentResolver().query(DistributorSchema.CONTENT_URI.get(DistributorDataStore.Tables.DISPOSAL.n), null, null, null, null);
        logger.info("message{}", c);
        
        
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

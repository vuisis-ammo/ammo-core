/**
 * 
 */
package edu.vu.isis.ammo.core.ui;

import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import edu.vu.isis.ammo.IPrefKeys;
import edu.vu.isis.ammo.api.AmmoDispatch;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.SubscribeTableSchema;
import edu.vu.isis.ammo.core.ui.util.ActivityEx;

/**
 * This activity provides the operator a direct way of subscribing to content of interest.
 * He enters the subscription fields directly.
 * 
 * This is to be used primarily for testing (move to AmmoCoreTestDummy?)
 */
public class Subscribe extends ActivityEx implements OnClickListener {
	private static final Logger logger = LoggerFactory.getLogger("ammo:api-d");
	
	// ===========================================================
	// Constants
	// ===========================================================
	public static final String LAUNCH = "edu.vu.isis.ammo.core.Subscribe.LAUNCH";
	public static final String MIME_OBJECT = "ammo/edu.vu.isis.ammo.map.object";
	
	// ===========================================================
	// Fields
	// ===========================================================
	private Spinner interestSpinner;
	private AmmoDispatch ad = null;
	private MyOnItemSelectedListener selectionListener = null;
	private Button btnSubscribe;
	private String uid;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.subscribe);
	    this.ad = AmmoDispatch.newInstance(this);

	    interestSpinner = (Spinner) findViewById(R.id.subscribe_uri);
	    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
	            this, R.array.content_of_interest_array, android.R.layout.simple_spinner_item);
	    
	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    interestSpinner.setAdapter(adapter);
	    
	    selectionListener = new MyOnItemSelectedListener();
	    	
	    interestSpinner.setOnItemSelectedListener(selectionListener);
	    
	    btnSubscribe = (Button) findViewById(R.id.submit_content);
        btnSubscribe.setOnClickListener(this);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        this.uid = prefs.getString(IPrefKeys.CORE_OPERATOR_ID, "operator1");
	}  
	    
	@Override
	public void onClick(View v) {
		if (v.equals(btnSubscribe)) {
			Uri selectedUri = selectionListener.getLastSelectedUri();
			String selectedMime = selectionListener.getMime();
        	if (selectedUri == null) {
        		Toast.makeText(Subscribe.this, "No content selected", Toast.LENGTH_SHORT).show();
        		return;
        	}
        	
            // Add item to subscription content provider.
        	ContentResolver cr = this.getContentResolver();
        	if (entryDoesNotExist(cr, selectedUri)) {
        		ContentValues values = new ContentValues();
            	values.put(SubscribeTableSchema.PROVIDER.cv(), selectedUri.toString());
            	values.put(SubscribeTableSchema.TOPIC.cv(), selectedMime);
            	values.put(SubscribeTableSchema.DISPOSITION.cv(), DisposalState.PENDING.cv());
            	// cr.insert(SubscribeTableSchema.CONTENT_URI, values);
            	
            	Toast.makeText(Subscribe.this, "Subscribed to content " + selectedUri.toString(), Toast.LENGTH_SHORT).show();
            	try {
					ad.subscribe(selectedUri, selectedMime, Calendar.MINUTE, 500, 10.0, ":event");
				} catch (RemoteException ex) {
					logger.warn("ammo distributor not yet active {}",
							ex.getLocalizedMessage());
				}	
        	} else {
        		Toast.makeText(Subscribe.this, "Already subscribed to this content", Toast.LENGTH_SHORT).show();
        	}
		}
	}
	
	private boolean entryDoesNotExist(ContentResolver cr, Uri selectedUri) {
		// String[] projection = {SubscribeTableSchema.PROVIDER.n, SubscribeTableSchema._ID};
		String selection = SubscribeTableSchema.PROVIDER.q() + " LIKE \"" + selectedUri.toString() + "\"";
		// Cursor c = cr.query(SubscribeTableSchema.CONTENT_URI, projection, selection, null, null);
		//return (c.getCount() == 0);
		return false;
	}
	
	private class MyOnItemSelectedListener implements OnItemSelectedListener {
	    public Uri uri = null;
    	public String mime = null;
    	
    	
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        	uri = Uri.parse(parent.getItemAtPosition(pos).toString());  	
        	mime = MIME_OBJECT + "_" + uid;
	        Toast.makeText(parent.getContext(), 
	    		  "The content uri is " + uri, 
	    		  Toast.LENGTH_SHORT).show();
	    }

	    public void onNothingSelected(AdapterView<?> parent) {
	        uri = null;
	    }
	    
	    public Uri getLastSelectedUri() {
	    	return uri;
	    }
	    
	    public String getMime() {
	    	return mime;
	    }
	};

}

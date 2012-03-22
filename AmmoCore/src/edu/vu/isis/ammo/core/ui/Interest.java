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
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.TimeInterval;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RequestField;
import edu.vu.isis.ammo.core.ui.util.ActivityEx;

/**
 * This activity provides the operator a direct way of subscribing to content of interest.
 * He enters the subscription fields directly.
 * 
 * This is to be used primarily for testing (move to AmmoCoreTestDummy?)
 */
public class Interest extends ActivityEx implements OnClickListener {
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
	private AmmoRequest.Builder ab = null;
	private MyOnItemSelectedListener selectionListener = null;
	private Button btnSubscribe;
	private String uid;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.subscribe);
	    this.ab = AmmoRequest.newBuilder(this);

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
        this.uid = prefs.getString(INetPrefKeys.CORE_OPERATOR_ID, 
        		INetPrefKeys.DEFAULT_CORE_OPERATOR_ID);
	}  

	@Override
	protected void onDestroy() {
		this.ab.releaseInstance();
		super.onDestroy();
	}

	    
	@Override
	public void onClick(View v) {
		if (v.equals(btnSubscribe)) {
			Uri selectedUri = selectionListener.getLastSelectedUri();
			String selectedMime = selectionListener.getMime();
        	if (selectedUri == null) {
        		Toast.makeText(Interest.this, "No content selected", Toast.LENGTH_SHORT).show();
        		return;
        	}
        	
            // Add item to subscription content provider.
        	ContentResolver cr = this.getContentResolver();
        	if (entryDoesNotExist(cr, selectedUri)) {
        		ContentValues values = new ContentValues();
            	values.put(RequestField.PROVIDER.cv(), selectedUri.toString());
            	values.put(RequestField.TOPIC.cv(), selectedMime);
            	values.put(RequestField.DISPOSITION.cv(), DisposalState.PENDING.cv());
            	// cr.insert(RequestField.CONTENT_URI, values);
            	
            	Toast.makeText(Interest.this, 
            			       "Subscribed to content " + selectedUri.toString(), 
            			       Toast.LENGTH_SHORT)
            	     .show();
            	try {
					ab.provider(selectedUri)
					  .topic(selectedMime)
					  .expire(new TimeInterval(TimeInterval.Unit.HOUR, 9))
					  .worth(10)
					  .filter(":event")
					  .subscribe();
				} catch (RemoteException ex) {
					logger.warn("ammo distributor not yet active {}",
							ex.getLocalizedMessage());
				}	
        	} else {
        		Toast.makeText(Interest.this, "Already subscribed to this content", Toast.LENGTH_SHORT).show();
        	}
		}
	}
	
	private boolean entryDoesNotExist(ContentResolver cr, Uri selectedUri) {
		// String[] projection = {RequestField.PROVIDER.n, RequestField._ID};
		// String selection = RequestField.PROVIDER.q() + " LIKE \"" + selectedUri.toString() + "\"";
		// Cursor c = cr.query(RequestField.CONTENT_URI, projection, selection, null, null);
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

/**
 * 
 */
package edu.vu.isis.ammo.core.ui;

import java.util.Calendar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import edu.vu.isis.ammo.IPrefKeys;
import edu.vu.isis.ammo.api.AmmoDispatcher;
import edu.vu.isis.ammo.dash.provider.IncidentSchema;
import edu.vu.isis.ammo.dash.provider.IncidentSchema.EventTableSchema;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;
import edu.vu.isis.ammo.core.ui.util.ActivityEx;

/**
 * This activity provides the operator a direct way of subscribing to content of interest.
 * He enters the subscription fields directly.
 * 
 * @author phreed
 *
 */
public class Subscribe extends ActivityEx implements OnClickListener {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final String LAUNCH = "edu.vu.isis.ammo.core.Subscribe.LAUNCH";
	public static final String MIME_OBJECT = "application/vnd.edu.vu.isis.ammo.map.object";
	
	// ===========================================================
	// Fields
	// ===========================================================
	private Spinner interestSpinner;
	private AmmoDispatcher ad = null;
	private MyOnItemSelectedListener selectionListener = null;
	private Button btnSubscribe;
	private String uid;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.subscribe);
	    ad = AmmoDispatcher.getInstance(this);

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
            	values.put(SubscriptionTableSchema.URI, selectedUri.toString());
            	values.put(SubscriptionTableSchema.MIME, selectedMime);
            	values.put(SubscriptionTableSchema.DISPOSITION, SubscriptionTableSchema.DISPOSITION_PENDING);
            	cr.insert(SubscriptionTableSchema.CONTENT_URI, values);
            	
            	Toast.makeText(Subscribe.this, "Subscribed to content " + selectedUri.toString(), Toast.LENGTH_SHORT).show();
            	ad.subscribe(selectedUri, selectedMime, Calendar.MINUTE, 500, 10.0, ":event");	
        	} else {
        		Toast.makeText(Subscribe.this, "Already subscribed to this content", Toast.LENGTH_SHORT).show();
        	}
		}
	}
	
	private boolean entryDoesNotExist(ContentResolver cr, Uri selectedUri) {
		String[] projection = {SubscriptionTableSchema.URI, SubscriptionTableSchema._ID};
		String selection = SubscriptionTableSchema.URI + " LIKE \"" + selectedUri.toString() + "\"";
		Cursor c = cr.query(SubscriptionTableSchema.CONTENT_URI, projection, selection, null, null);
		return (c.getCount() == 0);
	}
	
	private class MyOnItemSelectedListener implements OnItemSelectedListener {
	    public Uri uri = null;
    	public String mime = null;
    	
    	
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        	uri = Uri.parse(parent.getItemAtPosition(pos).toString());
        	
        	if (uri.equals(IncidentSchema.EventTableSchema.CONTENT_URI)) {
        		mime = EventTableSchema.CONTENT_TOPIC + "_" + uid;
        	} else {
        		mime = MIME_OBJECT + "_" + uid;
        	}
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

/**
 * 
 */
package edu.vu.isis.ammo.core;

import java.util.Calendar;

import edu.vu.isis.ammo.api.AmmoDispatcher;
import edu.vu.isis.ammo.collector.provider.IncidentSchema.EventTableSchema;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.net.Uri;

/**
 * This activity provides the operator a direct way of subscribing to content of interest.
 * He enters the subscription fields directly.
 * 
 * @author phreed
 *
 */
public class Subscribe extends Activity {
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
	    
	    final Button button = (Button) findViewById(R.id.submit_content);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	MyOnItemSelectedListener selectedItem = Subscribe.this.selectionListener;
            	if (selectedItem.uri == null) {
            		Toast.makeText(Subscribe.this, "subscribe to content", Toast.LENGTH_SHORT).show();
            		return;
            	}
            	
                // add item to subscription content provider
            	Toast.makeText(Subscribe.this, "subscribe to content", Toast.LENGTH_SHORT).show();
            	Subscribe.this.ad.pull(selectedItem.uri, selectedItem.mime, Calendar.MINUTE, 500, 10.0, ":event");
            }
        });
	}  
	    
	private class MyOnItemSelectedListener implements OnItemSelectedListener {
	    public Uri uri = null;
    	public String mime = null;
    	
    	
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        	uri = Uri.parse(parent.getItemAtPosition(pos).toString());
        	mime = MIME_OBJECT;
	        Toast.makeText(parent.getContext(), 
	    		  "The content uri is " + uri, 
	    		  Toast.LENGTH_LONG).show();
	    }

	    public void onNothingSelected(AdapterView parent) {
	        uri = null;
	    }
	};

}

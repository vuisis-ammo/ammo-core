package edu.vu.isis.ammo.core.distributor;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.DistributorSchema.PostalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

/**
 * View that presents buttons users may select to view different tables
 * in distributor.
 * @author Demetri Miller
 *
 */
public class DistributorViewerSwitch extends Activity implements OnClickListener {
	public static final String LAUNCH = "edu.vu.isis.ammo.core.distributor.DistributorViewerSwitch.LAUNCH";
	
	private String selection;
	private Button btnPostal, btnRetrieval, btnSubscription;
	
	@Override 
	public void onCreate(Bundle b) {
		super.onCreate(b);
		setContentView(R.layout.distributor_viewer_switch);
		setViewReferences();
		setOnClickListeners();
	}
	
	private void setViewReferences() {
		btnPostal = (Button)findViewById(R.id.distributor_viewer_switch_postal);
		btnRetrieval = (Button)findViewById(R.id.distributor_viewer_switch_retrieval);
		btnSubscription = (Button)findViewById(R.id.distributor_viewer_switch_subscription);
		
	}
	
	private void setOnClickListeners() {
		btnPostal.setOnClickListener(this);
		btnRetrieval.setOnClickListener(this);
		btnSubscription.setOnClickListener(this);
	}
	
	@Override
	public void onClick(View v) {
		Uri uri;
		if (v.equals(btnPostal)) {
			uri = PostalTableSchema.CONTENT_URI;
		} else if (v.equals(btnRetrieval)) {
			uri = RetrievalTableSchema.CONTENT_URI;
		} else if (v.equals(btnSubscription)) {
			uri = SubscriptionTableSchema.CONTENT_URI;
		} else {
			// Default to postal because I'm tired and I feel like it.
			uri = PostalTableSchema.CONTENT_URI;
		}
		
		Intent i = new Intent("edu.vu.isis.ammo.core.distributor.DistributorTableViewer.LAUNCH");
		i.putExtra("uri", uri);
		startActivity(i);
		
	}
}

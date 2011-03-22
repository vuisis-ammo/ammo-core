package edu.vu.isis.ammo.core.distributor.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TabHost;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ui.util.TabActivityEx;

/**
 * View that presents buttons users may select to view different tables
 * in distributor.
 * @author Demetri Miller
 *
 */
public class DistributorViewerSwitch extends TabActivityEx  {
		public static final Logger logger = LoggerFactory.getLogger(DistributorViewerSwitch.class);
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			logger.trace("::onCreate");
			this.setContentView(R.layout.distributor_list_activity);
			
			Intent intent = new Intent();
			
			// setup tabs
			Resources res = getResources(); // Resource object to get Drawables
		    TabHost tabHost = getTabHost();  // The activity TabHost
		    TabHost.TabSpec spec;  // Reusable TabSpec for each tab
			
			intent = new Intent().setClass(this, RetrievalTableViewer.class);
			spec = tabHost.newTabSpec("retrieval");
			spec.setIndicator("Retrival", res.getDrawable(R.drawable.retrieval_32));
			spec.setContent(intent);
			tabHost.addTab(spec);
			
			intent = new Intent().setClass(this, SubscribeTableViewer.class);
			spec = tabHost.newTabSpec("subscribe");
			spec.setIndicator("Subscribe", res.getDrawable(R.drawable.subscribe_32));
			spec.setContent(intent);
			tabHost.addTab(spec);
			
			intent = new Intent().setClass(this, PostalTableViewer.class);
			spec = tabHost.newTabSpec("postal");
			spec.setIndicator("Postal", res.getDrawable(R.drawable.postal_32));
			spec.setContent(intent);
			tabHost.addTab(spec);
			
			tabHost.setCurrentTab(0);
		}
}
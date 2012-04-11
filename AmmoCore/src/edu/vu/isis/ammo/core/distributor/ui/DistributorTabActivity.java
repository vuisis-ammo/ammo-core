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
 * @author Fred Eisele
 *
 */
public class DistributorTabActivity extends TabActivityEx  {
		public static final Logger logger = LoggerFactory.getLogger("class.DistributorTabActivity");
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			logger.trace("::onCreate");
			this.setContentView(R.layout.dist_list_activity);
			
			Intent intent = new Intent();
			
			// setup tabs
			Resources res = getResources(); // Resource object to get Drawables
		    TabHost tabHost = getTabHost();  // The activity TabHost
		    TabHost.TabSpec spec;  // Reusable TabSpec for each tab
			
			intent = new Intent().setClass(this, RetrievalTableViewer.class);
			spec = tabHost.newTabSpec("retrieval");
			spec.setIndicator("Retrieval", res.getDrawable(R.drawable.retrieval_32));
			spec.setContent(intent);
			tabHost.addTab(spec);
			
			intent = new Intent().setClass(this, InterestTableViewer.class);
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

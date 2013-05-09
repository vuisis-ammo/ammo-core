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
package edu.vu.isis.ammo.coreui.distributor.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.vanderbilt.isis.ammo.ui.R;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TabHost;
import edu.vu.isis.ammo.coreui.distributor.ui.CapabilityTableViewer;
import edu.vu.isis.ammo.coreui.distributor.ui.PostalTableViewer;
import edu.vu.isis.ammo.coreui.distributor.ui.PresenceTableViewer;
import edu.vu.isis.ammo.coreui.distributor.ui.RetrievalTableViewer;
import edu.vu.isis.ammo.coreui.distributor.ui.SubscribeTableViewer;
import edu.vu.isis.ammo.core.ui.util.TabActivityEx;

/**
 * View that presents buttons users may select to view different tables
 * in distributor.
 *
 */
public class DistributorTabActivity extends TabActivityEx  {
	public static final Logger logger = LoggerFactory.getLogger("ui.dist.tab");

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.trace("::onCreate");
		this.setContentView(R.layout.sep_dist_relation_activity);

		// setup tabs
		final Resources res = getResources(); // Resource object to get Drawables
		final TabHost tabHost = getTabHost();  // The activity TabHost
		{
			final TabHost.TabSpec spec = tabHost.newTabSpec("retrieval");
			spec.setIndicator(res.getText(R.string.retrieval_label), res.getDrawable(R.drawable.retrieval_32));
			spec.setContent(new Intent().setClass(this, RetrievalTableViewer.class));
			tabHost.addTab(spec);
		}
		{
			final TabHost.TabSpec spec = tabHost.newTabSpec("subscribe");
			spec.setIndicator(res.getText(R.string.subscribe_label), res.getDrawable(R.drawable.subscribe_32));
			spec.setContent(new Intent().setClass(this, SubscribeTableViewer.class));
			tabHost.addTab(spec);
		}
		{
			final TabHost.TabSpec spec = tabHost.newTabSpec("postal");
			spec.setIndicator(res.getText(R.string.postal_label), res.getDrawable(R.drawable.postal_32));
			spec.setContent(new Intent().setClass(this, PostalTableViewer.class));
			tabHost.addTab(spec);
		}
		{
			final TabHost.TabSpec spec = tabHost.newTabSpec("presence");
			spec.setIndicator(res.getText(R.string.presence_label), res.getDrawable(R.drawable.presence_32));
			spec.setContent(new Intent().setClass(this, PresenceTableViewer.class));
			tabHost.addTab(spec);
		}
		{
			final TabHost.TabSpec spec = tabHost.newTabSpec("capablity");
			spec.setIndicator(res.getText(R.string.capability_label), res.getDrawable(R.drawable.capability_32));
			spec.setContent(new Intent().setClass(this, CapabilityTableViewer.class));
			tabHost.addTab(spec);
		}
		tabHost.setCurrentTab(0);
	}
}

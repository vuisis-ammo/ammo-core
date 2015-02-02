/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
 *
 */
public class DistributorTabActivity extends TabActivityEx  {
	public static final Logger logger = LoggerFactory.getLogger("ui.dist.tab");

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.trace("::onCreate");
		this.setContentView(R.layout.dist_relation_activity);

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

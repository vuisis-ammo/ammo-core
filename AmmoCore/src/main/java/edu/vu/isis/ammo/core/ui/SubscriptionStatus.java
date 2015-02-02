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

package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ui.util.ListActivityEx;

/**
 * This activity shows a list of the items and their subscription status.
 * Eventually it will allow the operator to perform various management operations.
 * These management operations will be available via content menus.
 */
public class SubscriptionStatus extends ListActivityEx {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger("ui.subscribe.status");
	
	public static final String LAUNCH = "edu.vu.isis.ammo.core.SubscriptionStatus.LAUNCH";
	public static final int MENU_PURGE_SUBSCRIPTIONS = Menu.FIRST;
	
	// ===========================================================
	// Fields
	// ===========================================================
	
	@Override
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		setContentView(R.layout.dist_table_viewer_activity);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		logger.trace("::onCreateOptionsMenu");
		menu.add(Menu.NONE, MENU_PURGE_SUBSCRIPTIONS, Menu.NONE, "Purge Subscriptions");
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		logger.trace("::onOptionsItemSelected");
		switch(item.getItemId()) {
		case MENU_PURGE_SUBSCRIPTIONS:
			/*
			int count = getContentResolver().delete(SubscribeTableSchema.CONTENT_URI, SubscribeTableSchema._ID + ">" + String.valueOf(-1), null);
			logger.debug("Deleted " + count + "subscriptions");
			*/
		}
		return true;
	}
	
	@Override 
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Toast.makeText(SubscriptionStatus.this, "no on click behavior available", Toast.LENGTH_SHORT).show();
	}

}

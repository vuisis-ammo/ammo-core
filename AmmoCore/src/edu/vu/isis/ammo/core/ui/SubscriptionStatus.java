/*
Copyright(c) 2010-2012

This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under contract [contract citation, subcontract and prime contract]. 
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.

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
	public static final Logger logger = LoggerFactory.getLogger(SubscriptionStatus.class);
	
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

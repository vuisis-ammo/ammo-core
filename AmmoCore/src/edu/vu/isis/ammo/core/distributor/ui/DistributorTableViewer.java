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

import android.app.ListActivity;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Toast;
import edu.vu.isis.ammo.IAmmoActivitySetup;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.Relations;

/**
 * ListActivity class used in viewing the distributor's tables.
 */
public abstract class DistributorTableViewer extends ListActivity 
implements IAmmoActivitySetup 
{
	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger("ui.dist.tab.view");
	
	/*
	private static final int MENU_PURGE = 1;
	private static final int MENU_GARBAGE = 2;
	*/
	
	public static final int MENU_CONTEXT_DELETE = 1;
	
	// ===========================================================
	// Fields
	// ===========================================================
	
	protected Uri uri;
	protected DistributorTableViewAdapter adapter;
	protected PopupWindow pw;

	// ===========================================================
	// Lifecycle
	// ===========================================================
	@Override
	public void onCreate(Bundle bun) {
		super.onCreate(bun);
		setContentView(R.layout.dist_table_viewer);
		if (this.uri == null) {
			logger.error("no uri provided...exiting");
			return;
		}
		
		this.setListAdapter(this.adapter);
		this.registerForContextMenu(this.getListView());
	}

	// ===========================================================
	// Menus
	// ===========================================================
	// disabled the options menu in lieu of new buttons

	// @Override
	// public boolean onCreateOptionsMenu(Menu menu) {
	// super.onCreateOptionsMenu(menu);
	// logger.trace("::onCreateOptionsMenu");
	// menu.add(Menu.NONE, MENU_PURGE, Menu.NONE, "Purge");
	// menu.add(Menu.NONE, MENU_GARBAGE, Menu.NONE+1, "Garbage");
	// return true;
	// }

	protected String[] completeDisp = null;
	
	// Disabled, as options menu will not be showing

	// @Override
	// public boolean onOptionsItemSelected(MenuItem item) {
	// logger.trace("::onOptionsItemSelected");
	// int count;
	// switch (item.getItemId()) {
	// case MENU_PURGE:
	// // Delete everything.
	// count = getContentResolver().delete(this.uri, "_id > -1", null);
	// logger.debug("Deleted " + count + "subscriptions");
	// break;
	// case MENU_GARBAGE:
	// // Delete requests channel dispositions which are in a terminated state.
	// count = getContentResolver().delete(Uri.withAppendedPath(this.uri,
	// "garbage"), null, null);
	// logger.trace("Deleted {} requests",count);
	// }
	// return true;
	// }

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(ContextMenu.NONE, MENU_CONTEXT_DELETE, Menu.NONE, "Delete");
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
		switch (item.getItemId()) {
		case MENU_CONTEXT_DELETE:
			removeMenuItem(item);
			adapter.notifyDataSetChanged();
			break;

		default:

		}
		return true;
	}

	
	// ===========================================================
	// List Management
	// ===========================================================
	private final Relations table;

	public DistributorTableViewer(Relations table) {
		super();
		this.table = table;
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		final LayoutInflater inflater = (LayoutInflater) this
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		// Get the display dimensions and use them for figuring out the
		// dimensions of our popover.
		Display display = getWindowManager().getDefaultDisplay();
		int popoverWidth = (int) (display.getWidth()*0.9);
		int popoverHeight = (int) (display.getHeight()*0.75);
		
		pw = new RequestPopupWindow(this, inflater, popoverWidth,
				popoverHeight, position, this.adapter.getCursor(), this.table);
	    pw.setBackgroundDrawable(new BitmapDrawable());
	    pw.showAtLocation(this.getListView(), Gravity.CENTER, 0, 0); 
	}
	
	public void removeMenuItem(MenuItem item) {
		// Get the row id and uri of the selected item.
		final AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) item
				.getMenuInfo();
		@SuppressWarnings("unused")
		int rowId = (int)acmi.id;
		// int count = getContentResolver().delete(this.uri,
		// SubscriptionTableSchema._ID + "=" + String.valueOf(rowId), null);
		// Toast.makeText(this, "Removed " + String.valueOf(count) + " entry",
		// Toast.LENGTH_SHORT).show();
	}

	// ===========================================================
	// Activity setup
	// ===========================================================

	@Override
	public void setOnClickListeners() {

	}
	
	public void closePopup(View v) {
		if(pw == null)
			return;
		
		if(!pw.isShowing())
			return;
		
		pw.dismiss();
		
	}

	// ===========================================================
	// UI Management
	// ===========================================================

	//Added methods for new buttons on View Table layout
	
	public void purgeClick(View v) {

		// Delete everything.
		int count;
		count = getContentResolver().delete(this.uri, "_id > -1", null);
		logger.debug("Deleted " + count + "subscriptions");
		Toast.makeText(this, "You purged stuff", 1000);
	}

	public void garbageClick(View v) {
		int count;
		// Delete requests channel dispositions which are in a terminated state.
		count = getContentResolver().delete(
				Uri.withAppendedPath(this.uri, "garbage"), null, null);
		logger.trace("Deleted {} requests", count);
		Toast.makeText(this, "Garbage!", 1000);
	}

}

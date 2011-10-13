package edu.vu.isis.ammo.core.distributor.ui;

import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ListActivity;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.PopupWindow;
import edu.vu.isis.ammo.IAmmoActivitySetup;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ui.DistributorPopupWindow;

/**
 * ListActivity class used in viewing the distributor's tables.
 * @author Fred Eisele
 *
 */
public abstract class DistributorTableViewer extends ListActivity 
implements IAmmoActivitySetup 
{
	// ===========================================================
	// Constants
	// ===========================================================
	public static final Logger logger = LoggerFactory.getLogger(DistributorTableViewer.class);
	
	private static final int MENU_PURGE = 1;
	private static final int MENU_GARBAGE = 2;
	
	public static final int MENU_CONTEXT_DELETE = 1;


	static protected final int[] toItemLayout = new int[] {
			R.id.distributor_table_view_item_uri,
			R.id.distributor_table_view_item_timestamp };
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
		setContentView(R.layout.distributor_table_viewer);
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
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		logger.trace("::onCreateOptionsMenu");
		menu.add(Menu.NONE, MENU_PURGE, Menu.NONE, "Purge");
		menu.add(Menu.NONE, MENU_GARBAGE, Menu.NONE+1, "Garbage");
		return true;
	}

	protected String completeDisp = null;
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		logger.trace("::onOptionsItemSelected");
		int count;
		switch (item.getItemId()) {
		case MENU_PURGE:
			// Delete everything.
			count = getContentResolver().delete(this.uri, "_id > -1", null);
			logger.debug("Deleted " + count + "subscriptions");
			break;
		case MENU_GARBAGE:
			// Delete things which are outdated or complete everything.
			StringBuilder sb = new StringBuilder();
			sb.append("_id > -1");
			sb.append(" AND ");
			sb.append(" expiration < '").append(Calendar.getInstance().getTimeInMillis()).append("'");
			if (this.completeDisp != null)
				sb.append(" AND ").append(" disposition IN ").append(this.completeDisp);
			
			count = getContentResolver().delete(this.uri, sb.toString(), null);
			logger.debug("Deleted " + count + "subscriptions");
		}
		return true;
	}

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
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		LayoutInflater inflater = (LayoutInflater)
	       this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
	    pw = new DistributorPopupWindow(inflater, position, this.adapter.getCursor());
	    
	    pw.setBackgroundDrawable(new BitmapDrawable());
	    pw.showAtLocation(this.getListView(), Gravity.CENTER, 0, 0); 
	  
	    
	}
	
	public void removeMenuItem(MenuItem item) {
		// Get the row id and uri of the selected item.
		AdapterContextMenuInfo acmi = (AdapterContextMenuInfo)item.getMenuInfo();
		int rowId = (int)acmi.id;
		//int count = getContentResolver().delete(this.uri, SubscriptionTableSchema._ID + "=" + String.valueOf(rowId), null);
		//Toast.makeText(this, "Removed " + String.valueOf(count) + " entry", Toast.LENGTH_SHORT).show();
	}

	// ===========================================================
	// Activity setup
	// ===========================================================

	@Override
	public void setOnClickListeners() {

	}
	
	public void closePopup(View v)
	{
		if(pw == null)
			return;
		
		if(!pw.isShowing())
			return;
		
		pw.dismiss();
		
	}


}

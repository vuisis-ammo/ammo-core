package edu.vu.isis.ammo.core.ui;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.model.JournalNetlink;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.model.WifiNetlink;
import edu.vu.isis.ammo.core.model.WiredNetlink;

/**
 * The principle activity for the ammo core application.
 * Provides a means for...
 * ...changing the user preferences.
 * ...checking delivery status of various messages.
 * ...registering/unregistering content interest requests.
 * 
 * @author phreed
 *
 */
public class NetlinkActivity extends ActivityEx {
	public static final Logger logger = LoggerFactory.getLogger(NetlinkActivity.class);
	
	private static final int PREFERENCES_MENU = Menu.NONE + 0;
		
	// ===========================================================
	// Fields
	// ===========================================================
	
	private List<Netlink> model = new ArrayList<Netlink>();
	private NetlinkAdapter adapter = null;
	
	// ===========================================================
	// Views
	// ===========================================================
	
	private ListView list;
	
	/**
	 * @Cateogry Lifecycle
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.trace("::onCreate");
		this.setContentView(R.layout.netlink_activity);
		
		// set view references
		this.list = (ListView)this.findViewById(R.id.netlink_list);
		this.adapter = new NetlinkAdapter(this, model);
		list.setAdapter(adapter);
		
		// set listeners
		
		// register receivers
		
		this.setNetlink(WifiNetlink.getInstance(this));
		this.setNetlink(WiredNetlink.getInstance(this));
		this.setNetlink(JournalNetlink.getInstance(this));
	}
	
	public void setNetlink(Netlink nl) {
		adapter.add(nl);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		logger.trace("::onStart");
	}
	
	@Override
	public void onStop() {
		super.onStop();
	}

	// Create a menu which contains a preferences button.
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) 
	{
			super.onCreateContextMenu(menu, v, menuInfo);
			logger.trace("::onCreateContextMenu");
			
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		logger.trace("::onCreateOptionsMenu");
		
		menu.add(Menu.NONE, PREFERENCES_MENU, Menu.NONE, getResources().getString(R.string.pref_label));
		return true;
	}
	
	@Override 
	public boolean onPrepareOptionsMenu(Menu menu) {
		logger.trace("::onPrepareOptionsMenu");
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		logger.trace("::onOptionsItemSelected");
		
		Intent intent = new Intent();
		switch (item.getItemId()) {
		case PREFERENCES_MENU:
			intent.setAction(CorePreferenceActivity.LAUNCH);
			this.startActivity(intent);
			return true;
		}
		return false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		logger.trace("::onDestroy");
	}
	
	public void onSettingsButtonClick(View view) {
		logger.trace("::onClick");

		Intent settingIntent = new Intent();
		settingIntent.setClass(this, CorePreferenceActivity.class);
		this.startActivity(settingIntent);
	}
	
	public void onGatewayButtonClick(View view) {
		logger.trace("::onClick");

		Intent settingIntent = new Intent();
		settingIntent.setClass(this, GatewayActivity.class);
		this.startActivity(settingIntent);
	}
	
	// ===========================================================
	// UI Management
	// ===========================================================
	
	private class NetlinkAdapter extends ArrayAdapter<Netlink> 
	implements OnClickListener, OnFocusChangeListener, OnTouchListener, 
		OnStatusChangeListenerByView
	{
		NetlinkAdapter(NetlinkActivity parent, List<Netlink> model) {
			super(parent,
					android.R.layout.simple_list_item_1,
					model);
		}
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.netlink_item, null);
				row.setOnClickListener(this);
				row.setOnFocusChangeListener(this);
				row.setOnTouchListener(this);
			}
			Netlink nl = model.get(position);
			((TextView)row.findViewById(R.id.netlink_name)).setText(nl.getType());
			
			ToggleButton icon = (ToggleButton)row.findViewById(R.id.netlink_status);
			// set button icon
			icon.setChecked(nl.isEnabled());
			nl.setOnStatusChangeListener(this, parent);
			
			return row;
		}
		@Override
		public void onClick(View item) {
			//item.setBackgroundColor(Color.GREEN);
		}
		@Override
		public void onFocusChange(View item, boolean hasFocus) {
			if (hasFocus) {
			   item.setBackgroundColor(Color.RED);
			} else {
			   item.setBackgroundColor(Color.TRANSPARENT);
			}
		}
		
		 @Override
         public boolean onTouch(View view, MotionEvent event) {
             // Only perform this transform on image buttons for now.
			 if (view.getClass() != RelativeLayout.class) return false;

			 RelativeLayout item = (RelativeLayout) view;
			 int action = event.getAction();

			 switch (action) {
			 case MotionEvent.ACTION_DOWN:
			 case MotionEvent.ACTION_MOVE:
				 item.setBackgroundResource(R.drawable.select_gradient);
				 //item.setBackgroundColor(Color.GREEN);
				 break;

			 default:
				 item.setBackgroundColor(Color.TRANSPARENT);
			 }

			 return false;
         }
		@Override
		public boolean onStatusChange(View item, int[] status) {
			View row = item;
			ToggleButton icon = (ToggleButton)row.findViewById(R.id.netlink_status);
			switch (status[0]) {
			case Netlink.ACTIVE: 
				icon.setTextColor(R.color.status_active); 
				break;
			case Netlink.INACTIVE: 
				icon.setTextColor(R.color.status_inactive); 
				break;
			case Netlink.DISABLED: 
				icon.setTextColor(R.color.status_disabled); 
				break;
			default:
				icon.setTextColor(R.color.status_inactive); 
				return false;
			}
			item.refreshDrawableState(); 
			return true;
		}
		
	}
}

package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.network.INetworkBinder;
import edu.vu.isis.ammo.util.UniqueIdentifiers;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Gallery;
import android.widget.Toast;

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
public class MainActivity extends Activity implements OnClickListener {

	public static final Logger logger = LoggerFactory.getLogger(MainActivity.class);
	
	private static final int PREFERENCES_MENU = Menu.NONE + 0;
	private static final int DELIVERY_STATUS_MENU = Menu.NONE + 1;
	private static final int SUBSCRIPTION_MENU = Menu.NONE + 2;
	private static final int SUBSCRIBE_MENU = Menu.NONE + 3;
	
	private Button disconnectButton = null;
	private Button reconnectButton = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		
		this.disconnectButton = (Button) findViewById(R.id.disconnect_button);
		disconnectButton.setOnClickListener(this);
		this.reconnectButton = (Button) findViewById(R.id.reconnect_button);
		reconnectButton.setOnClickListener(this);
		
		String deviceId = UniqueIdentifiers.device(this);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(CorePreferences.PREF_DEVICE_ID, deviceId).commit();
		
		this.startService(ICoreService.CORE_APPLICATION_LAUNCH_SERVICE_INTENT);
	}

	// Create a menu which contains a preferences button.
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
			super.onCreateContextMenu(menu, v, menuInfo);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(Menu.NONE, PREFERENCES_MENU, Menu.NONE, getResources().getString(R.string.pref_label));
		menu.add(Menu.NONE, DELIVERY_STATUS_MENU, Menu.NONE, getResources().getString(R.string.delivery_status_label));
		menu.add(Menu.NONE, SUBSCRIPTION_MENU, Menu.NONE, getResources().getString(R.string.subscription_label));
		menu.add(Menu.NONE, SUBSCRIBE_MENU, Menu.NONE, getResources().getString(R.string.subscribe_label));
		return true;
	}
	
	@Override 
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = new Intent();
		switch (item.getItemId()) {
		case PREFERENCES_MENU:
			intent.setAction(CorePreferences.LAUNCH);
			this.startActivity(intent);
			return true;
		case DELIVERY_STATUS_MENU:
			intent.setAction(DeliveryStatus.LAUNCH);
			this.startActivity(intent);
			return true;
		case SUBSCRIPTION_MENU:
			intent.setAction(SubscriptionStatus.LAUNCH);
			this.startActivity(intent);
			return true;
		case SUBSCRIBE_MENU:
			intent.setAction(Subscribe.LAUNCH);
			this.startActivity(intent);
			return true;
		}
		return false;
	}

	@Override
	public void onClick(View view) {
		if (view.equals(this.disconnectButton)) {
			logger.debug("disconnect button clicked");
			Intent intent = new Intent(INetworkBinder.ACTION_DISCONNECT);
			this.sendBroadcast(intent);
			return;
		}
		if (view.equals(this.reconnectButton)) {
			logger.debug("reconnect button clicked");
			Intent intent = new Intent(INetworkBinder.ACTION_RECONNECT);
			this.sendBroadcast(intent);
			return;
		}
		
	}
	
}

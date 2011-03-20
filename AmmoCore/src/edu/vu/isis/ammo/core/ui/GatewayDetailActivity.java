package edu.vu.isis.ammo.core.ui;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.AmmoPreferenceChangedReceiver;
import edu.vu.isis.ammo.AmmoPreferenceReadOnlyAccess;
import edu.vu.isis.ammo.IAmmoPreferenceChangedListener;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoPreference;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.R.color;
import edu.vu.isis.ammo.core.R.drawable;
import edu.vu.isis.ammo.core.R.id;
import edu.vu.isis.ammo.core.R.layout;
import edu.vu.isis.ammo.core.R.string;
import edu.vu.isis.ammo.core.distributor.DistributorViewerSwitch;
import edu.vu.isis.ammo.core.provider.PreferenceSchema;
import edu.vu.isis.ammo.core.receiver.StartUpReceiver;

/**
 * Show details about the gateway.
 * Mostly explanatory information about the status.
 * 
 * @author phreed
 *
 */
public class GatewayDetailActivity extends ActivityEx
{
	public static final Logger logger = LoggerFactory.getLogger(GatewayDetailActivity.class);
	
	// ===========================================================
	// Fields
	// ===========================================================
	
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
		this.setContentView(R.layout.gateway_detail_activity);
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

	@Override
	public void onDestroy() {
		super.onDestroy();
		logger.trace("::onDestroy");
	}
	
}

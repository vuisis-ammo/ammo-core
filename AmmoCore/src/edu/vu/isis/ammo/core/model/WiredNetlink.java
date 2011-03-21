package edu.vu.isis.ammo.core.model;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.ui.ActivityEx;
import edu.vu.isis.ammo.core.ui.TabActivityEx;
import android.content.SharedPreferences;
import android.view.View;


public class WiredNetlink extends Netlink {

	private WiredNetlink(TabActivityEx context) {
		super(context, "Wired Netlink", "wired");
	}
	
	public static Netlink getInstance(TabActivityEx context) {
		// initialize the gateway from the shared preferences
		return new WiredNetlink(context);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(INetPrefKeys.WIRED_PREF_SHOULD_USE)) {
		      //shouldUse(prefs);
		}
	}
	
	public void setOnStatusChangeListener(OnStatusChangeListenerByView listener, View view) {
		super.setOnStatusChangeListener(listener, view);
		// initialize the status indicators
		this.statusListener.onStatusChange(this.statusView, this.application.getWiredNetlinkState() );
	}

}

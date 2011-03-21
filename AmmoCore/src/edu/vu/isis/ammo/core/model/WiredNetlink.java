package edu.vu.isis.ammo.core.model;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.ui.ActivityEx;
import android.content.SharedPreferences;
import android.view.View;


public class WiredNetlink extends Netlink {

	private WiredNetlink(ActivityEx context, String type) {
		super(context, type);
	}
	
	public static Netlink getInstance(ActivityEx context) {
		// initialize the gateway from the shared preferences
		return new WiredNetlink(context, "Wired Netlink");
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
		this.statusListener.onStatusChange(this.statusView, this.application.getNetlinkState() );
	}

}

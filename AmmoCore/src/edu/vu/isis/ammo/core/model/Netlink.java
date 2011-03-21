package edu.vu.isis.ammo.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.view.View;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.ApplicationEx;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.ui.ActivityEx;
import edu.vu.isis.ammo.core.ui.TabActivityEx;

public abstract class Netlink implements OnSharedPreferenceChangeListener {
	public static final Logger logger = LoggerFactory.getLogger(Netlink.class);
	
	public static final int NETLINK_UP = 1;
	public static final int NETLINK_DOWN = 2;
	public static final int NETLINK_DISABLED = 3;
	
	// does the operator wish to use this gateway?
	private boolean election; 
	
	private void setElection(boolean pred) { 
		this.election = pred; 
		Editor editor = this.prefs.edit();
		editor.putBoolean(INetPrefKeys.NET_CONN_PREF_SHOULD_USE, this.election);
		editor.commit();
	}
	public void enable() { this.setElection(true); }
	public void disable() { this.setElection(false); }
	public void toggle() { this.setElection(!this.election); }
	public boolean isEnabled() { return this.election; }
	
	// the user selected familiar name 
	private final String name; 
	public String getName() { return this.name; }
	
	private final String type; 
	public String getType() { return this.type; }
	
	protected final SharedPreferences prefs;
	protected TabActivityEx context;
	protected ApplicationEx application;
	
	protected Netlink(TabActivityEx context, String name, String type) {
		this.context = context;
		this.name = name;
		this.type = type;
		this.election = true;

		this.prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
		this.election = this.prefs.getBoolean(INetPrefKeys.GATEWAY_SHOULD_USE, true);
		
	    prefs.registerOnSharedPreferenceChangeListener(this);
	    this.application = (ApplicationEx) this.context.getApplication();
	}
	
	public static Netlink getInstance(ActivityEx context) {
		logger.error("get instance not implemented");
		return null;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.type).append(" ").append(this.election);
		return sb.toString();
	}

	protected OnStatusChangeListenerByView statusListener;
	protected View statusView;
	
	public void setOnStatusChangeListener(OnStatusChangeListenerByView listener, View view) {
		this.statusListener = listener;
		this.statusView = view;
	}
	
	public void onStatusChange(int[] status) {
		if (this.statusListener == null) return;
		this.statusListener.onStatusChange(this.statusView, status);
	}
	
}

package edu.vu.isis.ammo.core.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.R;

public class Serial extends Channel {

	private static Serial instance;
	public static Serial getInstance(Context context) {
		if(instance == null)
			instance = new Serial(context, "Serial Channel");
		return instance;
	}

	private boolean election;
	private int[] status;
	private Serial(Context context, String name) {
		super(context, name);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if(key.equals(INetPrefKeys.SERIAL_BAUD_RATE)) {
			
		} else
		
		if(key.equals(INetPrefKeys.SERIAL_SLOT_NUMBER)) {
			
		} else 
			
		if(key.equals(INetPrefKeys.SERIAL_DEVICE)) {
			
		}
		

	}

	@Override
	public void enable() {
		this.setElection(true);

	}

	private void setElection(boolean b) {
		this.election = b;
		this.prefs.edit().putBoolean(INetPrefKeys.SERIAL_SHOULD_USE, this.election).commit();
		
		
	}
	@Override
	public void disable() {
		this.setElection(false);

	}

	@Override
	public void toggle() {
		this.setElection(!this.election);

	}

	@Override
	public boolean isEnabled() {
		return this.election;
	}

	@Override
	public View getView(View row, LayoutInflater inflater) {
		row = inflater.inflate(edu.vu.isis.ammo.core.R.layout.serial_item, null);
		
		TextView name = (TextView) row.findViewById(R.id.serial_name);
		TextView device = (TextView) row.findViewById(R.id.serial_device);
		
		name.setText(this.name);
		StringBuilder sb = new StringBuilder();
		sb.append(this.prefs.getString(INetPrefKeys.SERIAL_DEVICE, "def device"));
		sb.append("@");
		sb.append(this.prefs.getString(INetPrefKeys.SERIAL_BAUD_RATE, "9600"));
		device.setText(sb.toString());
		return row;
	}

	@Override
	public int[] getStatus() {
		return this.status;
	}

	@Override
	public void setOnNameChangeListener(OnNameChangeListener listener, View view) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setStatus(int[] statusCode) {
		this.status = statusCode;

	}

}

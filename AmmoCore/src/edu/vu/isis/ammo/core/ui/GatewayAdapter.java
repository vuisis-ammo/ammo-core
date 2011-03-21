package edu.vu.isis.ammo.core.ui;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByName;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.network.INetChannel;

public class GatewayAdapter extends ArrayAdapter<Gateway> 
implements OnTouchListener, OnNameChangeListener, 
OnStatusChangeListenerByView, OnStatusChangeListenerByName
{
	public static final Logger logger = LoggerFactory.getLogger(AmmoActivity.class);
	private final AmmoActivity parent;
	private final Resources res;
	private final List<Gateway> model;

	public GatewayAdapter(AmmoActivity parent, List<Gateway> model) {
		super(parent,
				android.R.layout.simple_list_item_1,
				model);
		this.parent = parent;
		this.res = this.parent.getResources();
		this.model = model;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View row = convertView;
		if (row == null) {
			LayoutInflater inflater = this.parent.getLayoutInflater();
			row = inflater.inflate(R.layout.gateway_item, null);
			row.setOnTouchListener(this);
		}
		Gateway gw = this.model.get(position);
		((TextView)row.findViewById(R.id.gateway_name)).setText(gw.getName());
		((TextView)row.findViewById(R.id.gateway_formal)).setText(gw.getFormal());

		ToggleButton icon = (ToggleButton)row.findViewById(R.id.gateway_status);
		// set button icon
		icon.setChecked(gw.isEnabled());
		gw.setOnNameChangeListener(this, parent);
		gw.setOnStatusChangeListener(this, parent);

		return row;
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
			logger.trace("::onClick");

			Intent gatewayIntent = new Intent();
			gatewayIntent.setClass(this.parent, GatewayDetailActivity.class);
			this.parent.startActivity(gatewayIntent);
			break;

		default:
			item.setBackgroundColor(Color.TRANSPARENT);
		}

		return false;
	}
	@Override
	public boolean onStatusChange(View item, int[] status) {
		if (status == null) return false;
		if (status.length < 1) return false;

		View row = item;
		ToggleButton icon = (ToggleButton)row.findViewById(R.id.gateway_status);
		TextView text = (TextView)row.findViewById(R.id.gateway_status_text);
		int color;
		if (text == null) {
			logger.error("text field is null");
			return false;
		}
		if (icon == null) {
			logger.error("icon field is null");
			return false;
		}

		switch (status[0]) {
		case INetChannel.CONNECTED:
			color = this.res.getColor(R.color.status_active);
			text.setText(R.string.status_active);
			break;
		case INetChannel.DISCONNECTED: 
			color = this.res.getColor(R.color.status_inactive);
			text.setText(R.string.status_inactive);
			break;
		default:
			color = this.res.getColor(R.color.status_disabled);
			text.setText(R.string.status_disabled);
			return false;
		}
		icon.setTextColor(color); 
		text.setTextColor(color);

		item.refreshDrawableState(); 
		return true;
	}

	@Override
	public boolean onNameChange(View item, String name) {
		((TextView)item.findViewById(R.id.gateway_name)).setText(name);
		item.refreshDrawableState(); 
		return false;
	}
	@Override
	public boolean onFormalChange(View item, String formal) {
		((TextView)item.findViewById(R.id.gateway_formal)).setText(formal);
		item.refreshDrawableState(); 
		return false;
	}
	@Override
	public boolean onStatusChange(String itemName, int[] status) {
		for (int ix=0; ix < this.model.size(); ix++) {
			Gateway item = this.model.get(ix);
			if (! item.getName().equalsIgnoreCase(itemName)) continue;
			item.onStatusChanged(status);
			return true;
		}
		return false;
	}
}


package edu.vu.isis.ammo.core.ui;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.network.INetChannel;

public class GatewayAdapter extends ArrayAdapter<Gateway> 
implements OnTouchListener, OnNameChangeListener, 
OnStatusChangeListenerByView
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
		    // NOTE: Do nothing here for now, since no functionality
		    // is implemented behind a click action... Do not display
		    // "this page intentionally left blank" in a major release.
		    
		    //item.setBackgroundResource(R.drawable.select_gradient);
		    //logger.trace("::onClick");
		    //Intent gatewayIntent = new Intent();
		    //gatewayIntent.setClass(this.parent, GatewayDetailActivity.class);
		    //this.parent.startActivity(gatewayIntent);
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

		final View row = item;
		final ToggleButton icon = (ToggleButton)row.findViewById(R.id.gateway_status);
		final TextView text_one = (TextView)row.findViewById(R.id.gateway_status_text_one);
		final TextView text_two = (TextView)row.findViewById(R.id.gateway_status_text_two);
		TextView text = null;
		
		if (text_one == null) {
			logger.error("text field is null");
			return false;
		}
		if (icon == null) {
			logger.error("icon field is null");
			return false;
		}
		if (text_two != null) text_two.setVisibility(TextView.INVISIBLE);

		text = text_one;
		switch (status[0]) {
		case INetChannel.PENDING:
			setColor(icon, text, R.color.status_pending);
			text.setText(R.string.status_pending);
			break;
		case INetChannel.EXCEPTION:
			setColor(icon, text, R.color.status_exception);
			text.setText(R.string.status_exception);
			break;
		case INetChannel.CONNECTING:
			setColor(icon, text, R.color.status_connecting);
			text.setText(R.string.status_connecting);
			break;
		case INetChannel.CONNECTED:
			
		    if (status.length < 1) break;
			switch (status[1]) {
			case INetChannel.SENDING:
				setColor(icon, text, R.color.status_sending);
				text.setText(R.string.status_sending);
				break;
			case INetChannel.TAKING:
				setColor(icon, text, R.color.status_taking);
				text.setText(R.string.status_taking);
				break;
			case INetChannel.WAIT_CONNECT:
			case INetChannel.WAIT_RECONNECT:
				setColor(icon, text, R.color.status_waiting_conn);
				text.setText(R.string.status_waiting);
				break;
			default:
				logger.error("missing sender status handling {}", status[1]);
				setColor(icon, text, R.color.status_unknown);
				text.setText(R.string.status_unknown);
			}
			
			if (status.length < 2) break;
			text = text_two;
			text.setVisibility(TextView.VISIBLE);
			
			switch (status[2]) {
			case INetChannel.SIZED: 
				setColor(icon, text, R.color.status_sized);
				text.setText(R.string.status_sized);
				break;
			case INetChannel.CHECKED: 
				setColor(icon, text, R.color.status_checked);
				text.setText(R.string.status_checked);
				break;
			case INetChannel.DELIVER: 
				setColor(icon, text, R.color.status_deliver);
				text.setText(R.string.status_deliver);
				break;
			case INetChannel.WAIT_CONNECT:
			case INetChannel.WAIT_RECONNECT:
				setColor(icon, text, R.color.status_waiting_recv);
				text.setText(R.string.status_waiting);
				break;
			case INetChannel.START: 
			case INetChannel.RESTART: 
				setColor(icon, text, R.color.status_start);
				text.setText(R.string.status_start);
				break;
			default:
				logger.error("missing receiver status handling {}", status[2]);
				setColor(icon, text, R.color.status_unknown);
				text.setText(R.string.status_unknown);
			}
			break;
		case INetChannel.DISCONNECTED:
			setColor(icon, text, R.color.status_disconnected);
			text.setText(R.string.status_disconnected);
			break;
		case INetChannel.STALE:
			setColor(icon, text, R.color.status_stale);
			text.setText(R.string.status_stale);
			break;
		case INetChannel.LINK_WAIT:
			setColor(icon, text, R.color.status_link_wait);
			text.setText(R.string.status_link_wait);
			break;
		case INetChannel.WAIT_CONNECT:
		case INetChannel.WAIT_RECONNECT:
			setColor(icon, text, R.color.status_waiting_conn);
			text.setText(R.string.status_waiting);
			break;
		
		case INetChannel.INTERRUPTED:
			setColor(icon, text, R.color.status_interrupted);
			text.setText(R.string.status_interrupted);
			break;
		case INetChannel.SHUTDOWN:
			setColor(icon, text, R.color.status_shutdown);
			text.setText(R.string.status_shutdown);
			break;
		case INetChannel.START: 
		case INetChannel.RESTART: 
			setColor(icon, text, R.color.status_start);
			text.setText(R.string.status_start);
			break;
		case INetChannel.STARTED: 
			setColor(icon, text, R.color.status_started);
			text.setText(R.string.status_started);
			break;

		default:
			setColor(icon, text, R.color.status_unknown);
			text.setText(R.string.status_unknown);
		}
		
		item.refreshDrawableState(); 
		return true;
	}
	private void setColor(ToggleButton icon, TextView text, int resColor) {
		int color = this.res.getColor(resColor);
	    if (icon != null) icon.setTextColor(color); 
	    if (text != null) text.setTextColor(color);
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

	public Gateway getItemByName(String name) {
		for (int ix=0; ix < this.model.size(); ix++) {
			Gateway item = this.model.get(ix);
			if (! item.getName().equalsIgnoreCase(name)) continue;
			return item;
		}
		return null;
	}

}


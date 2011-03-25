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
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.model.Netlink;

public class NetlinkAdapter extends ArrayAdapter<Netlink> 
  implements OnClickListener, OnFocusChangeListener, OnTouchListener, 
	OnStatusChangeListenerByView
{
	public static final Logger logger = LoggerFactory.getLogger(AmmoActivity.class);
	private final AmmoActivity parent;
	private final Resources res;
	private final List<Netlink> model;
	
	public NetlinkAdapter(AmmoActivity parent, List<Netlink> model) {
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
			row = inflater.inflate(R.layout.netlink_item, null);
			row.setOnClickListener(this);
			row.setOnFocusChangeListener(this);
			row.setOnTouchListener(this);
		}
		Netlink nl = this.model.get(position);
		((TextView)row.findViewById(R.id.netlink_name)).setText(nl.getName());
		
		ToggleButton icon = (ToggleButton)row.findViewById(R.id.netlink_status);
		// set button icon
		icon.setChecked(nl.isEnabled());
		nl.setOnStatusChangeListener(this, row); 
		nl.initialize();
		
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
		if (status == null) return false;
		if (status.length < 1) return false;
		
		View row = item;
		ToggleButton icon = (ToggleButton)row.findViewById(R.id.netlink_status);
		TextView text = (TextView)row.findViewById(R.id.netlink_status_text);
		if (text == null) {
			logger.error("text field is null");
			return false;
		}
		if (icon == null) {
			logger.error("icon field is null");
			return false;
		}
		int color;
		
		switch (status[0]) {
		case Netlink.NETLINK_UP: 
			color = this.res.getColor(R.color.status_up);
			text.setText(R.string.status_up);
			break;
		case Netlink.NETLINK_DOWN: 
			color = this.res.getColor(R.color.status_down);
			text.setText(R.string.status_down);
			break;
		case Netlink.NETLINK_DISABLED: 
			color = this.res.getColor(R.color.status_disabled);
			text.setText(R.string.status_disabled);
			break;
		case Netlink.NETLINK_DISCONNECTED: 
			color = this.res.getColor(R.color.status_disconnected);
			text.setText(R.string.status_disconnected);
			break;
		case Netlink.NETLINK_IDLE: 
			color = this.res.getColor(R.color.status_idle);
			text.setText(R.string.status_idle);
			break;
		case Netlink.NETLINK_SCANNING: 
			color = this.res.getColor(R.color.status_scanning);
			text.setText(R.string.status_scanning);
			break;
		case Netlink.NETLINK_CONNECTING: 
			color = this.res.getColor(R.color.status_connecting);
			text.setText(R.string.status_connecting);
			break;
		case Netlink.NETLINK_AUTHENTICATING: 
			color = this.res.getColor(R.color.status_authenticating);
			text.setText(R.string.status_authenticating);
			break;
		case Netlink.NETLINK_OBTAINING_IPADDR: 
			color = this.res.getColor(R.color.status_obtaining_ipaddr);
			text.setText(R.string.status_obtaining_ipaddr);
			break;
		case Netlink.NETLINK_FAILED: 
			color = this.res.getColor(R.color.status_failed);
			text.setText(R.string.status_failed);
			break;
		default:
			color = this.res.getColor(R.color.status_unknown);
			text.setText(R.string.status_unknown);
		}
		icon.setTextColor(color); 
		text.setTextColor(color);
		
		item.refreshDrawableState(); 
		return true;
	}
	
    public Netlink getItemByType(String type) {
    	for (int ix=0; ix < this.model.size(); ix++) {
			Netlink item = this.model.get(ix);
			if (! item.getType().equalsIgnoreCase(type)) continue;
			return item;
		}
    	return null;
    }
	
}
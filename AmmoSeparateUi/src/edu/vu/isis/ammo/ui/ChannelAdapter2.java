/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.ui;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import edu.vanderbilt.isis.ammo.ui.R;
import edu.vu.isis.ammo.core.OnNameChangeListener;


/**
 * An adapter for the channels.
 *
 */
public class ChannelAdapter2 extends ArrayAdapter<AmmoListItem>
    implements OnTouchListener, OnNameChangeListener
{
        public static final Logger logger = LoggerFactory.getLogger("ui.channel");

        private final AmmoCore parent;
        private final Resources res;
        private final List<AmmoListItem> model;
        
	public ChannelAdapter2( AmmoCore parent, List<AmmoListItem> model )
    {
        super(parent, R.layout.multicast_item, model);
        this.parent = parent;
        this.res = this.parent.getResources();
        this.model = model;
    }

	
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null || convertView.getId()!=R.id.gateway_layout){
			convertView = this.parent.getLayoutInflater().inflate(R.layout.ui_list_item, null);
		}
    	((TextView)convertView.findViewById(R.id.item_name)).setText(model.get(position).getName());
    	((TextView)convertView.findViewById(R.id.item_formal)).setText(model.get(position).getFormal());
    	((TextView)convertView.findViewById(R.id.item_status_one)).setText(model.get(position).getStatusOne());
    	((TextView)convertView.findViewById(R.id.item_status_two)).setText(model.get(position).getStatusTwo());
    	((TextView)convertView.findViewById(R.id.item_send_stats)).setText(model.get(position).getSendStats());
    	((TextView)convertView.findViewById(R.id.item_receive_stats)).setText(model.get(position).getReceiveStats());
    	return convertView;
    }

	@Override
	public boolean onFormalChange(View arg0, String arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onNameChange(View arg0, String arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		// TODO Auto-generated method stub
		return false;
	}

}


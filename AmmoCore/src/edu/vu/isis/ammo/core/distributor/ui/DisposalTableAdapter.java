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
package edu.vu.isis.ammo.core.distributor.ui;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.store.DistributorDataStore.DisposalField;
import edu.vu.isis.ammo.core.store.DistributorDataStore.DisposalState;

public class DisposalTableAdapter extends ResourceCursorAdapter {
	private static final Logger logger = LoggerFactory.getLogger("class.DisposalTableAdapter");
	
	static protected HashMap<Integer, String> dispositionStateMap;
	static {
		dispositionStateMap = new HashMap<Integer, String>();
		dispositionStateMap.put(DisposalState.NEW.o, "new");
		dispositionStateMap.put(DisposalState.DELIVERED.o, "delivered");
		dispositionStateMap.put(DisposalState.REJECTED.o, "failed");
		dispositionStateMap.put(DisposalState.BAD.o, "bad");
		dispositionStateMap.put(DisposalState.BUSY.o, "busy");
		dispositionStateMap.put(DisposalState.PENDING.o, "pending");
		dispositionStateMap.put(DisposalState.QUEUED.o, "queued");
		dispositionStateMap.put(DisposalState.SENT.o, "sent");
		dispositionStateMap.put(DisposalState.TOLD.o, "told");
	}
	static protected HashMap<Integer, Integer> dispositionColorMap;
	static {
		dispositionColorMap = new HashMap<Integer, Integer>();
		dispositionColorMap.put(DisposalState.NEW.o, Color.CYAN);
		dispositionColorMap.put(DisposalState.DELIVERED.o, Color.GREEN);
		dispositionColorMap.put(DisposalState.REJECTED.o, Color.RED);
		dispositionColorMap.put(DisposalState.BAD.o, Color.DKGRAY);
		dispositionColorMap.put(DisposalState.BUSY.o, Color.BLUE);
		dispositionColorMap.put(DisposalState.PENDING.o, Color.LTGRAY);
		dispositionColorMap.put(DisposalState.QUEUED.o, Color.LTGRAY);
		dispositionColorMap.put(DisposalState.SENT.o, Color.GREEN);
		dispositionColorMap.put(DisposalState.TOLD.o, Color.LTGRAY);
	}
	
	public DisposalTableAdapter(Context context, int layout, Cursor c) {
		super(context, layout, c);
		
	}


	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		final String channel;
		{ 
			final TextView nameTv = (TextView)view.findViewById(R.id.dist_channel_name);
			channel = cursor.getString(cursor.getColumnIndex(DisposalField.CHANNEL.n()));
			nameTv.setText(channel);
		}
		final String disposition;
		{ 
			final TextView dispTv = (TextView)view.findViewById(R.id.dist_channel_state);
			final int dispositionId = cursor.getInt(cursor.getColumnIndex(DisposalField.STATE.n()));
		
			if (dispositionStateMap.containsKey(dispositionId)) {
				disposition = dispositionStateMap.get(dispositionId);
				dispTv.setTextColor(dispositionColorMap.get(dispositionId));
			} else {
				disposition = "Unknown Disposition";
			}
			dispTv.setText(disposition);
		}
		logger.trace("bind disposal {} {}", channel, disposition);
	}
}

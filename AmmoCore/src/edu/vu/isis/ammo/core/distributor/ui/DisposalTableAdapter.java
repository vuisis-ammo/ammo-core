/*
Copyright(c) 2010-2012

This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under contract [contract citation, subcontract and prime contract]. 
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
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelDisposal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTableSchema;

public class DisposalTableAdapter extends ResourceCursorAdapter {
	private static final Logger logger = LoggerFactory.getLogger("ammo-ca");
	
	static protected HashMap<Integer, String> dispositionStateMap;
	static {
		dispositionStateMap = new HashMap<Integer, String>();
		dispositionStateMap.put(ChannelDisposal.NEW.o, "new");
		dispositionStateMap.put(ChannelDisposal.DELIVERED.o, "delivered");
		dispositionStateMap.put(ChannelDisposal.REJECTED.o, "failed");
		dispositionStateMap.put(ChannelDisposal.BAD.o, "bad");
		dispositionStateMap.put(ChannelDisposal.BUSY.o, "busy");
		dispositionStateMap.put(ChannelDisposal.PENDING.o, "pending");
		dispositionStateMap.put(ChannelDisposal.QUEUED.o, "queued");
		dispositionStateMap.put(ChannelDisposal.SENT.o, "sent");
		dispositionStateMap.put(ChannelDisposal.TOLD.o, "told");
	}
	static protected HashMap<Integer, Integer> dispositionColorMap;
	static {
		dispositionColorMap = new HashMap<Integer, Integer>();
		dispositionColorMap.put(ChannelDisposal.NEW.o, Color.CYAN);
		dispositionColorMap.put(ChannelDisposal.DELIVERED.o, Color.GREEN);
		dispositionColorMap.put(ChannelDisposal.REJECTED.o, Color.RED);
		dispositionColorMap.put(ChannelDisposal.BAD.o, Color.DKGRAY);
		dispositionColorMap.put(ChannelDisposal.BUSY.o, Color.BLUE);
		dispositionColorMap.put(ChannelDisposal.PENDING.o, Color.LTGRAY);
		dispositionColorMap.put(ChannelDisposal.QUEUED.o, Color.LTGRAY);
		dispositionColorMap.put(ChannelDisposal.SENT.o, Color.GREEN);
		dispositionColorMap.put(ChannelDisposal.TOLD.o, Color.LTGRAY);
	}
	
	public DisposalTableAdapter(Context context, int layout, Cursor c) {
		super(context, layout, c);
		
	}


	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		final String channel;
		{ 
			final TextView nameTv = (TextView)view.findViewById(R.id.dist_channel_name);
			channel = cursor.getString(cursor.getColumnIndex(DisposalTableSchema.CHANNEL.n));
			nameTv.setText(channel);
		}
		final String disposition;
		{ 
			final TextView dispTv = (TextView)view.findViewById(R.id.dist_channel_state);
			final int dispositionId = cursor.getInt(cursor.getColumnIndex(DisposalTableSchema.STATE.n));
		
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

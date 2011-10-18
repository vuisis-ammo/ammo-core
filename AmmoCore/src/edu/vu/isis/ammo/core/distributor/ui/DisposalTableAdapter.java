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
		dispositionStateMap.put(ChannelDisposal.FAILED.o, "failed");
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
		dispositionColorMap.put(ChannelDisposal.FAILED.o, Color.RED);
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
		logger.info("bind disposal {} {}", channel, disposition);
	}
}

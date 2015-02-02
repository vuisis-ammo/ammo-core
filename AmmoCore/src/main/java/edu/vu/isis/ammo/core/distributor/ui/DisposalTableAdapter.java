/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTableSchema;

public class DisposalTableAdapter extends ResourceCursorAdapter {
	private static final Logger logger = LoggerFactory.getLogger("ui.dist.disposal");
	
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

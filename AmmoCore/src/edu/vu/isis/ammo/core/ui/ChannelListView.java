package edu.vu.isis.ammo.core.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;
public class ChannelListView extends ListView {

	AmmoActivity activity = null;
	public ChannelListView(Context context) {
		super(context);
	}
	
	public ChannelListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public ChannelListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
}

package edu.vu.isis.ammo.core.ui;

import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.model.Channel;
import edu.vu.isis.ammo.core.model.Gateway;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
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

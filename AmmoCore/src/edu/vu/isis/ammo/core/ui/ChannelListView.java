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

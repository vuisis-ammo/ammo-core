package edu.vu.isis.ammo.core.ui;

import java.util.List;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class LogElementAdapter extends ArrayAdapter<LogElement> {

	private Context mContext;
	
	public LogElementAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
		this.mContext = context;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		View view = super.getView(position, convertView, parent);
		
		final TextView tv = (TextView) view;
		final LogElement element = super.getItem(position);
		final LogLevel level = element.getLogLevel();

		tv.setText(element.getMessage());
		tv.setTextColor(level.getColor(mContext));
		return view;
		
	}
	
	public void addAll(List<LogElement> elemList) {
		synchronized(elemList) {
			for(LogElement e : elemList) {
				super.add(e);
			}
		}
	}
	
	
	
}

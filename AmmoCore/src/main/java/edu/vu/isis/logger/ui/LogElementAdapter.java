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


package edu.vu.isis.logger.ui;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class LogElementAdapter extends ArrayAdapter<LogElement> {

	private Logger logger = LoggerFactory.getLogger("ui.logger.adapter");
	
	private Context mContext;
	private int maxNumLines = 0; // 0 means unlimited lines
	
	
	public LogElementAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
		this.mContext = context;
	}
	
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		logger.trace("Adding view for position {}", position);
		
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
				
				if(this.maxNumLines != 0 && this.maxNumLines < super.getCount()) {
					// Remove the first item in the list if we have exceeded the
					// max number of lines allowed
					super.remove(super.getItem(0));
				}
				
			}
			
		}
	}
	
	
	/**
	 * Sets the max number of elements that will be held by this adapter.
	 * The adapter will remove all elements that exceed the new max
	 * automatically.
	 * @param newMax
	 */
	public void setMaxLines(int newMax) {
		this.maxNumLines = newMax;
		reduceCacheSizeToNewMax();
	}
	
	private void reduceCacheSizeToNewMax() {
		
		if(!(this.maxNumLines < super.getCount())) return;
		if(this.maxNumLines == 0) return;
		
		while(this.maxNumLines < super.getCount()) {
			super.remove(super.getItem(0));
		}
		
	}
	
	
	
}

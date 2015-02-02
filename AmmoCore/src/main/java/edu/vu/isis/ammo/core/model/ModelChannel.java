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

package edu.vu.isis.ammo.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.network.NetChannel;

public abstract class ModelChannel implements OnSharedPreferenceChangeListener{

    private static final Logger logger = LoggerFactory.getLogger("model.channel");
    
    protected OnNameChangeListener mOnNameChangeListener; 
    
	protected Context context = null;
	protected String name = "";
	protected SharedPreferences prefs = null;
	
	protected NetChannel mNetChannel = null;
	
	protected ModelChannel(Context context, String name)
	{
		this.context = context;
		this.name = name;
		this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
		this.prefs.registerOnSharedPreferenceChangeListener(this);
		logger.trace("Channel {} constructed", name);
	}
	
	public NetChannel getNetChannel () {return mNetChannel;}
	
	public void setName(String newName)
	{
		this.name = newName;
	}
	public String getName()
	{
		return this.name;
	}
	
	public abstract void enable();
	public abstract void disable();
	public abstract void toggle();
	public abstract boolean isEnabled();
	public abstract View getView(View row, LayoutInflater inflater);
	public abstract int[] getStatus();
	public void setOnNameChangeListener(OnNameChangeListener listener) {
	    mOnNameChangeListener = listener;
	}
	protected void callOnNameChange() {
	    if(mOnNameChangeListener != null) {
            mOnNameChangeListener.onNameChange();
        }
	}
	public abstract void setStatus(int [] statusCode);
}

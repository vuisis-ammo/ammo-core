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

package edu.vu.isis.ammo.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class MyListPreference extends ListPreference {

	List<String> items = new ArrayList<String>();
	public MyListPreference(Context context) {
		super(context);
		this.refreshList();
	}
	
	public MyListPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.refreshList();
	}
	
	public void refreshList() {
		File f = new File("/dev/");
		
		String[] l = f.list();
		items.clear();
		for(int i = 0; i < l.length; ++i) {
			if(l[i].substring(0, 3).equals("tty")) {
				items.add("/dev/" + l[i]);
			}
		}
		
		String [] s = this.items.toArray(new String [1]);
		this.setEntries(s);
		this.setEntryValues(s);
	}

}

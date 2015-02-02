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

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.widget.Spinner;

/**
 * This class extends a Spinner to support an OnSpinnerDialogClickListener.
 * The purpose of this is that the usual method of setting a Spinner listener
 * with an OnItemSelectedListener can produce unexpected results.  For instance,
 * programmatically calling setSelection on a Spinner to change its current
 * selection also causes the OnItemSelectedListener to be called.  Additionally,
 * if the currently selected item on a Spinner is selected a second time, the
 * OnItemSelectedListener will not be called.  By using an 
 * OnSpinnerDialogClickListener instead, the callback will be made iff the user
 * manually selects an item from the Spinner's AlertDialog popup.
 * 
 * @author Nick King
 *
 */
public class WellBehavedSpinner extends Spinner {
	
	private OnSpinnerDialogClickListener mListener;
	
	public WellBehavedSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
	
	public void setOnSpinnerDialogClickListener(OnSpinnerDialogClickListener l) {
		this.mListener = l;
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
        
		super.onClick(dialog, which);
        mListener.onSpinnerDialogClick(which);
        
    }
	
	
}

package edu.vu.isis.ammo.core.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.widget.Spinner;

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

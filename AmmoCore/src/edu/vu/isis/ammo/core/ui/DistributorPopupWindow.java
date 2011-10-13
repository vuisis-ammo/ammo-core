package edu.vu.isis.ammo.core.ui;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.database.Cursor;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.widget.PopupWindow;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;



public class DistributorPopupWindow extends PopupWindow {
	int [] ids = {R.id.distributor_detail_view_text0,
					R.id.distributor_detail_view_text1,
					R.id.distributor_detail_view_text2,
					R.id.distributor_detail_view_text3,
					R.id.distributor_detail_view_text4,
					R.id.distributor_detail_view_text5,
					R.id.distributor_detail_view_text6,
					R.id.distributor_detail_view_text7,
					R.id.distributor_detail_view_text8,
					R.id.distributor_detail_view_text9,
					R.id.distributor_detail_view_textA,
					R.id.distributor_detail_view_textB,
					R.id.distributor_detail_view_textC,
					R.id.distributor_detail_view_textD,
					R.id.distributor_detail_view_textE,
					R.id.distributor_detail_view_textF };
			  
	
	private static final Logger logger = LoggerFactory.getLogger("DistributorPopupWindow");
	

	
	public boolean onKeyDown(int keyCode, KeyEvent event)  {
		logger.trace("BACK KEY PRESSED 2");
	    if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
	        
	        return true;
	    }
	    
	    return false;
	}
	public DistributorPopupWindow(LayoutInflater inflater, int position, Cursor c)
	{
		super(inflater.inflate(R.layout.distributor_table_item_detail_view, null, false),600,400,true);
		c.moveToFirst();
		c.move(position);
		logger.trace("popup window {}", Arrays.asList(c.getColumnNames()) );
		String [] cols = c.getColumnNames();
		for(int i = 0; i < cols.length; ++i)
		{
			try
			{
			((TextView)this.getContentView().findViewById(ids[i]))
			  .setText( new StringBuilder()
			               .append(cols[i])
			               .append(": ")
			               .append(c.getString(c.getColumnIndex(cols[i]))));
			}
			catch(Exception e)
			{
				//((TextView)this.getContentView().findViewById(ids[i])).setText("");	
			}
		}
		
	}


}

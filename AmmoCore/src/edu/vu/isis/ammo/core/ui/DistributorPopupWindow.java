package edu.vu.isis.ammo.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.R;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;



public class DistributorPopupWindow extends PopupWindow {

	Logger logger = LoggerFactory.getLogger("DistributorPopupWindow");
	public DistributorPopupWindow(LayoutInflater inflater, int position, Cursor c)
	{
		super(inflater.inflate(R.layout.distributor_table_item_detail_view, null, false),500,300,true);
		c.moveToFirst();
		c.move(position);
		logger.info(c.getColumnNames().toString());
		StringBuilder sb = new StringBuilder();
		//ID
		((TextView)this.getContentView().findViewById(R.id.distributor_detail_view_text1)).setText(sb.append("ID: ").append(c.getString(c.getColumnIndex("_id"))));
		sb = new StringBuilder();
		
		//DISPOSITION
		((TextView)this.getContentView().findViewById(R.id.distributor_detail_view_text2)).setText(sb.append("Disposition: ").append(c.getString(c.getColumnIndex("disposition"))));
		sb = new StringBuilder();
		
		//URI
		((TextView)this.getContentView().findViewById(R.id.distributor_detail_view_text3)).setText(sb.append("URI: ").append(c.getString(c.getColumnIndex("uri"))));
		sb = new StringBuilder();
				
		//CREATED_DATE
		((TextView)this.getContentView().findViewById(R.id.distributor_detail_view_text4)).setText(sb.append("Created Date: ").append(c.getString(c.getColumnIndex("created_date"))));
		sb = new StringBuilder();
		
		//MIME
		try
		{
		((TextView)this.getContentView().findViewById(R.id.distributor_detail_view_text5)).setText(sb.append("Type: ").append(c.getString(c.getColumnIndex("mime"))));
		sb = new StringBuilder();
		}
		catch(Exception e) {};
	}


}

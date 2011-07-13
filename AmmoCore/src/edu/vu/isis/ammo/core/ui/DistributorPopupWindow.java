package edu.vu.isis.ammo.core.ui;

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
                  R.id.distributor_detail_view_textF
                 };


    Logger logger = LoggerFactory.getLogger("DistributorPopupWindow");



    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        logger.info("BACK KEY PRESSED 2");
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {

            return true;
        }

        return false;
    }
    public DistributorPopupWindow(LayoutInflater inflater, int position, Cursor c) {
        super(inflater.inflate(R.layout.distributor_table_item_detail_view, null, false),600,400,true);
        c.moveToFirst();
        c.move(position);
        logger.info(c.getColumnNames().toString());
        StringBuilder sb = new StringBuilder();
        String [] cols = c.getColumnNames();
        for(int i = 0; i < cols.length; ++i) {
            try {

                ((TextView)this.getContentView().findViewById(ids[i])).setText(sb.append(cols[i]).append(": ").append(c.getString(c.getColumnIndex(cols[i]))));
            } catch(Exception e) {
                //((TextView)this.getContentView().findViewById(ids[i])).setText("");
            }
            sb = new StringBuilder();
        }
        /*
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
        */
    }


}

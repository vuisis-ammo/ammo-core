package edu.vu.isis.ammo.test.ics;

import java.io.File;
import java.sql.Date;
import java.text.SimpleDateFormat;

import edu.vu.isis.ammo.test.ics.provider.AmmotestdriverSchema.BaselineTableSchema;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class SendMsgCursorAdapter extends SimpleCursorAdapter {

	private static final String TAG = "SendMsgCursorAdapter";

	private SimpleDateFormat mTimeFormat;
	
	public SendMsgCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
		super(context, layout, c, from, to, flags);
		Log.d(TAG, "constructor");
		mTimeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
	}
	
	private void populateViewFromCursor(View v, Cursor c) {
		// get stuff from cursor
		int idx = c.getInt(c.getColumnIndex(BaselineTableSchema.TEST_ITEM));
		String name = c.getString(c.getColumnIndex(BaselineTableSchema.TEST_NAME));
		//String textVal = arg2.getString(arg2.getColumnIndex(BaselineTableSchema.TEXT_VALUE));
		long timeStamp = c.getLong(c.getColumnIndex(BaselineTableSchema.TIMESTAMP_VALUE));
		//long recvTime = arg2.getLong(arg2.getColumnIndex(BaselineTableSchema._RECEIVED_DATE));
		String fileName = c.getString(c.getColumnIndex(BaselineTableSchema.A_FILE));
		String fileType = c.getString(c.getColumnIndex(BaselineTableSchema.A_FILE_TYPE));
		
		TextView nameDisplay = (TextView) v.findViewById(R.id.recv_testname_display);
		if (nameDisplay != null) {
			nameDisplay.setText(name);
		}
				
		TextView idxDisplay = (TextView) v.findViewById(R.id.recv_testidx_display);
		if (idxDisplay != null) {
			idxDisplay.setText("   " + String.valueOf(idx));
		}
				
		// Display the sent time (in the "received time" field in the view)
		TextView intDisplay = (TextView) v.findViewById(R.id.recv_time_display);
		if (intDisplay != null) {
			// Formatted time
			Date d = new Date(timeStamp);
			intDisplay.setText(mTimeFormat.format(d));
		}
		        
		// "latency" doesn't mean anything for the sender, so don't do this here
		/*
		TextView latDisplay = (TextView) v.findViewById(R.id.recv_latency_display);
		if(latDisplay != null) {
		        	latDisplay.setText("latency: " + String.valueOf(recvTime - timeStamp) + " ms");
		}
		 */
		        
		TextView sizeDisplay = (TextView) v.findViewById(R.id.recv_msgsize_display);
		if(sizeDisplay != null) {
			sizeDisplay.setText("(size TODO bytes)" );
		}
		        
		//TextView txtDisplay = (TextView) v.findViewById(R.id.recv_text_display);
		//if(txtDisplay != null) {
		//	txtDisplay.setText("Text: " + textVal);
		//}
		        
		// Set icon
		ImageView msgIcon = (ImageView) v.findViewById(R.id.recv_msg_icon);
		if (msgIcon != null) {
			msgIcon.setImageResource(R.drawable.green_icon);
		}

		// Set attachment image, if applicable
		/*
		if (fileName != null) {
			
			//if (!fileName.isEmpty()) {
			//if (fileName.trim().length() > 0) {
			
			String path = fileName.trim();
			File imgFile = new File(path);
			if(imgFile.exists()) {
				ImageView msgPicture = (ImageView) v.findViewById(R.id.msg_pic_attach);
				if (msgPicture != null) { 
				
				// If it's an image filetype, fill in the ImageView with the picture.
				// Otherwise put an icon
					
					if (fileType.contains("image")) {
						msgPicture.setImageURI(Uri.fromFile(imgFile));
					} else {
						// generic "attachment" icon
						msgPicture.setImageResource(R.drawable.file_icon);
					}
				}
			}
			//}
		}
		*/
	}
	
	@Override
	public void bindView(View arg0, Context arg1, Cursor arg2) {
		//Log.d(TAG, "bindView");
		populateViewFromCursor(arg0, arg2);
	}

	@Override
	public View newView(Context arg0, Cursor arg1, ViewGroup arg2) {
		//Log.d(TAG, "newView");
		
		LayoutInflater vi = (LayoutInflater)arg0.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = vi.inflate(R.layout.recv_list_item, null);
		
		populateViewFromCursor(v, arg1);
        
		return v;
		
	}

}

package edu.vu.isis.ammo.test.ics;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.sql.Date;
import java.text.SimpleDateFormat;

import edu.vu.isis.ammo.test.ics.provider.AmmotestdriverSchema.BaselineTableSchema;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class RecvMsgCursorAdapter extends SimpleCursorAdapter {

	private static final String TAG = "RecvMsgCursorAdapter";

	private SimpleDateFormat mTimeFormat;
	private Context mContext;
	
	public RecvMsgCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
		super(context, layout, c, from, to, flags);
		Log.d(TAG, "constructor");
		mTimeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
		mContext = context;
	}
	
	private void populateViewFromCursor(View v, Cursor c) {
		// get stuff from cursor
		int idx = c.getInt(c.getColumnIndex(BaselineTableSchema.TEST_ITEM));
		String name = c.getString(c.getColumnIndex(BaselineTableSchema.TEST_NAME));
		//String textVal = arg2.getString(arg2.getColumnIndex(BaselineTableSchema.TEXT_VALUE));
		long timeStamp = c.getLong(c.getColumnIndex(BaselineTableSchema.TIMESTAMP_VALUE));
		long recvTime = c.getLong(c.getColumnIndex(BaselineTableSchema._RECEIVED_DATE));
		long checkSum = c.getLong(c.getColumnIndex(BaselineTableSchema.CONTENT_CHECKSUM));
		int msgSize = c.getInt(c.getColumnIndex(BaselineTableSchema.FOREIGN_KEY_REF));
		String fileName = c.getString(c.getColumnIndex(BaselineTableSchema.A_FILE));
		int rowId = c.getInt(c.getColumnIndex(BaselineTableSchema._ID));
		
		FileDescriptor ff = null; 
		
		
				
		//---
		//View v = arg0;
		
		TextView nameDisplay = (TextView) v.findViewById(R.id.recv_testname_display);
		if (nameDisplay != null) {
			nameDisplay.setText(name);
		}
				
		TextView idxDisplay = (TextView) v.findViewById(R.id.recv_testidx_display);
		if (idxDisplay != null) {
			idxDisplay.setText("   " + String.valueOf(idx));
		}
				
		TextView intDisplay = (TextView) v.findViewById(R.id.recv_time_display);
		if (intDisplay != null) {
			// raw system time value
			//intDisplay.setText(String.valueOf(recvTime));
			
			// Formatted time
			Date d = new Date(recvTime);
			intDisplay.setText(mTimeFormat.format(d));
		}
		        
		TextView latDisplay = (TextView) v.findViewById(R.id.recv_latency_display);
		if(latDisplay != null) {
			latDisplay.setText("latency: " + String.valueOf(recvTime - timeStamp) + " ms");
			//latDisplay.setText(textVal);
		}
		
		TextView sizeDisplay = (TextView) v.findViewById(R.id.recv_msgsize_display);
		if(sizeDisplay != null) {
			sizeDisplay.setText("(size " + String.valueOf(msgSize) + " bytes)" );
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
		
		if (fileName != null) {
					
			ImageView msgPicture = (ImageView) v.findViewById(R.id.msg_pic_attach);
			if (msgPicture != null) {
					
				// If it's an image filetype, fill in the ImageView with the picture.
				// Otherwise put an icon
				String fileType = c.getString(c.getColumnIndex(BaselineTableSchema.A_FILE_TYPE));
				if (fileType.contains("image")) {
					
					// Forget about FUBAR ammo file handling for now, just use a generic icon
					
					Uri imgUri = Uri.withAppendedPath(Uri.withAppendedPath(BaselineTableSchema.CONTENT_URI, 
								String.valueOf(rowId)), 
								BaselineTableSchema.A_FILE);
					Log.d(TAG, "img uri = " + imgUri.toString());
					
					//msgPicture.setImageURI(imgUri);
					try {
						msgPicture.setImageBitmap(BitmapFactory.decodeFileDescriptor(mContext.getContentResolver().openFileDescriptor(imgUri, "r").getFileDescriptor()));
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (NullPointerException e) {
						e.printStackTrace();
					}
					
					//msgPicture.setImageResource(R.drawable.imagefile_icon);
				} else {
					// Use a generic "attachment" icon
					msgPicture.setImageResource(R.drawable.file_icon);
				}
			}
			
		}
		
	}
	
	@Override
	public void bindView(View arg0, Context arg1, Cursor arg2) {
		//Log.d(TAG, "bindView");
		
		populateViewFromCursor(arg0, arg2);
		
		/*
		// get stuff from cursor
		int idx = arg2.getInt(arg2.getColumnIndex(BaselineTableSchema.TEST_ITEM));
		String name = arg2.getString(arg2.getColumnIndex(BaselineTableSchema.TEST_NAME));
		//String textVal = arg2.getString(arg2.getColumnIndex(BaselineTableSchema.TEXT_VALUE));
		long timeStamp = arg2.getLong(arg2.getColumnIndex(BaselineTableSchema.TIMESTAMP_VALUE));
		long recvTime = arg2.getLong(arg2.getColumnIndex(BaselineTableSchema._RECEIVED_DATE));
		long checkSum = arg2.getLong(arg2.getColumnIndex(BaselineTableSchema.CONTENT_CHECKSUM));
		int msgSize = arg2.getInt(arg2.getColumnIndex(BaselineTableSchema.FOREIGN_KEY_REF));
		
		//---
		View v = arg0;
		
		TextView nameDisplay = (TextView) v.findViewById(R.id.recv_testname_display);
		if (nameDisplay != null) {
			nameDisplay.setText(name);
        }
		
		TextView idxDisplay = (TextView) v.findViewById(R.id.recv_testidx_display);
		if (idxDisplay != null) {
			idxDisplay.setText("   " + String.valueOf(idx));
        }
		
		TextView intDisplay = (TextView) v.findViewById(R.id.recv_time_display);
        if (intDisplay != null) {
        	// raw system time value
        	//intDisplay.setText(String.valueOf(recvTime));
        	
        	// Formatted time
        	Date d = new Date(recvTime);
        	intDisplay.setText(mTimeFormat.format(d));
        }
        
        TextView latDisplay = (TextView) v.findViewById(R.id.recv_latency_display);
        if(latDisplay != null) {
        	latDisplay.setText("latency: " + String.valueOf(recvTime - timeStamp) + " ms");
        	//latDisplay.setText(textVal);
        }
        
        TextView sizeDisplay = (TextView) v.findViewById(R.id.recv_msgsize_display);
        if(sizeDisplay != null) {
        	sizeDisplay.setText("(size " + String.valueOf(msgSize) + " bytes)" );
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
        */
	}

	@Override
	public View newView(Context arg0, Cursor arg1, ViewGroup arg2) {
		//Log.d(TAG, "newView");
		
		LayoutInflater vi = (LayoutInflater)arg0.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = vi.inflate(R.layout.recv_list_item, null);
		
		populateViewFromCursor(v, arg1);
		
		/*
		// get stuff from cursor
		int idx = arg1.getInt(arg1.getColumnIndex(BaselineTableSchema.TEST_ITEM));
		String name = arg1.getString(arg1.getColumnIndex(BaselineTableSchema.TEST_NAME));
		//String textVal = arg1.getString(arg1.getColumnIndex(BaselineTableSchema.TEXT_VALUE));
		long timeStamp = arg1.getLong(arg1.getColumnIndex(BaselineTableSchema.TIMESTAMP_VALUE));
		long recvTime = arg1.getLong(arg1.getColumnIndex(BaselineTableSchema._RECEIVED_DATE));
		long checkSum = arg1.getLong(arg1.getColumnIndex(BaselineTableSchema.CONTENT_CHECKSUM));
		int msgSize = arg1.getInt(arg1.getColumnIndex(BaselineTableSchema.FOREIGN_KEY_REF));
		
		LayoutInflater vi = (LayoutInflater)arg0.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = vi.inflate(R.layout.recv_list_item, null);
		
		TextView nameDisplay = (TextView) v.findViewById(R.id.recv_testname_display);
		if (nameDisplay != null) {
			nameDisplay.setText(name);
        }
		
		TextView idxDisplay = (TextView) v.findViewById(R.id.recv_testidx_display);
		if (idxDisplay != null) {
			idxDisplay.setText("   " + String.valueOf(idx));
        }
		
		TextView intDisplay = (TextView) v.findViewById(R.id.recv_time_display);
        if (intDisplay != null) {
        	// raw system time value
        	//intDisplay.setText(String.valueOf(recvTime));
        	
        	// Formatted time
        	Date d = new Date(recvTime);
        	intDisplay.setText(mTimeFormat.format(d));
        }
        
        // Compute difference between sent time (stored in timestamp field of message) and
        // received time, call this the message latency and display it
        TextView latDisplay = (TextView) v.findViewById(R.id.recv_latency_display);
        if(latDisplay != null) {
        	latDisplay.setText("latency: " + String.valueOf(recvTime - timeStamp) + " ms");
        }
        
        TextView sizeDisplay = (TextView) v.findViewById(R.id.recv_msgsize_display);
        if(sizeDisplay != null) {
        	sizeDisplay.setText("(size " + String.valueOf(msgSize) + " bytes)" );
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
		*/
		
		return v;
		
	}

}

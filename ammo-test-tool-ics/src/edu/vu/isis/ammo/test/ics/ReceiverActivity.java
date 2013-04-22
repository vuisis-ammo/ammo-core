package edu.vu.isis.ammo.test.ics;

import edu.vu.isis.ammo.test.ics.provider.AmmotestdriverSchema.BaselineTableSchema;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.app.Activity;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;

public class ReceiverActivity extends Activity implements LoaderCallbacks<Cursor> {

	private static final String TAG = "ReceiverActivity";
	private int LOADER_ID = 0;
	private ListView mList;
	public RecvMsgCursorAdapter mAdapter;
	
	private LoaderManager.LoaderCallbacks<Cursor> mCallbacks;

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(TAG, "onCreate");
		
		setContentView(R.layout.activity_receiver);
		
		mCallbacks = this;
		getLoaderManager().initLoader(LOADER_ID, null, mCallbacks);
		
		//mAdapter = new RecvMsgCursorAdapter(this, null, 0);
		mAdapter = new RecvMsgCursorAdapter(this, 0, null, null, null, 0);

		mAdapter.notifyDataSetChanged();

		mList = (ListView)this.findViewById(R.id.listView1);
		if (mList == null) {
			Log.d(TAG, "listview reference is null!");
		}
		mList.setAdapter(mAdapter);
	}

	/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}
	 */
	
	// LoaderCallbacks interface method
	public CursorLoader onCreateLoader(int id, Bundle args) {
		Log.d(TAG, "onCreateLoader");
		
		/*
		String[] projection = {BaselineTableSchema.FOREIGN_KEY_REF, 
				BaselineTableSchema.INTEGER, 
				BaselineTableSchema.TEXT_VALUE,
				BaselineTableSchema.TIMESTAMP_VALUE,
				BaselineTableSchema._RECEIVED_DATE,
				BaselineTableSchema.CONTENT_CHECKSUM};
		*/
		String[] projection = null;
		String selection = null; //BaselineTableSchema._DISPOSITION;
		String[] selectionArgs = null;  //{"REMOTE.gateway"};
		String sortOrder = null;  // sorty by rec_time
				
		CursorLoader loader = new CursorLoader(
				this,
				BaselineTableSchema.CONTENT_URI, 
				projection, 
				selection, 
				selectionArgs, 
				sortOrder);
		return loader;
				
			}
			
	// LoaderCallbacks interface method
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		Log.d(TAG, "onLoadFinished");

		if (data != null) {
			Log.d(TAG, "  count: " + String.valueOf(data.getCount()));
		}
		
		try {
			mAdapter.swapCursor(data);
		} catch (NullPointerException e) {
			Log.e(TAG, "onLoadFinished - something happened!");
		}
		
		// scroll ListView to bottom
		mList.setSelection(data.getCount() -1);
		
	}
			
	// LoaderCallbacks interface method
	public void onLoaderReset(Loader<Cursor> loader) {
		Log.d(TAG, "onLoaderReset");
		mAdapter.swapCursor(null);
	}
}

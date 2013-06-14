package edu.vu.isis.ammo.test.ics;


import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.test.ics.provider.AmmotestdriverSchema.BaselineTableSchema;

/*
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
*/

public class MainActivity extends FragmentActivity implements
		ActionBar.TabListener {

	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;

	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	ViewPager mViewPager;

	
	private static final String TAG = "AmmoTestMain";

    private static final int MENU_ABOUT = 1;
    private static final int MENU_OPTION_SETTINGS = 2;
    
    private static final int MENU_OPTION_SENT_CLEARALL = 3;
    private static final int MENU_OPTION_RECD_CLEARALL = 4;

	public AmmoRequest.Builder ab;

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Set up the action bar.
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						actionBar.setSelectedNavigationItem(position);
					}
				});

		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by
			// the adapter. Also specify this Activity object, which implements
			// the TabListener interface, as the callback (listener) for when
			// this tab is selected.
			actionBar.addTab(actionBar.newTab()
					.setText(mSectionsPagerAdapter.getPageTitle(i))
					.setTabListener(this));
		}
		
		// Ammo setup
		// Ammo builder object
		try {
		    this.ab = AmmoRequest.newBuilder(this);
		} catch (Throwable e) {
		    Log.e(TAG, "no ammo today: " + e.toString());
		    Toast.makeText(this, "Ammo initialization failed", Toast.LENGTH_SHORT).show();
		}

		// Ammo subscription
		try {
		    this.ab.provider(BaselineTableSchema.CONTENT_URI)
			.topic(BaselineTableSchema.CONTENT_TOPIC)
			.subscribe();
		} catch (RemoteException e) {
		    Log.e(TAG, "error creating subscription: " + e);
		}
	}


	@Override
    protected void onDestroy() {

		if (this.ab != null) {
			this.ab.releaseInstance();
		}

		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ABOUT, 0, "About");
        menu.add(0, MENU_OPTION_SETTINGS, Menu.NONE, "Settings");
		return true;
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		Log.d(TAG, "menu item " + String.valueOf(item.getItemId()));
		switch (item.getItemId()) {
			case MENU_OPTION_SETTINGS:
				launchSettingsActivity();
				break;
			case MENU_ABOUT:
				launchAboutActivity();
				break;
			case MENU_OPTION_SENT_CLEARALL:
				clearSentMessages();
				break;
			case MENU_OPTION_RECD_CLEARALL:
				clearRecdMessages();
				break;
		}
		return true;
	}
	
	@Override
	public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab,FragmentTransaction fragmentTransaction) {
		
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
		
	}

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			// getItem is called to instantiate the fragment for the given page.
			// Return a DummySectionFragment (defined as a static inner class
			// below) with the page number as its lone argument.
			/*
			Fragment fragment = new DummySectionFragment();
			Bundle args = new Bundle();
			args.putInt(DummySectionFragment.ARG_SECTION_NUMBER, position + 1);
			fragment.setArguments(args);
			return fragment;
			*/
			
			Fragment fragment = null;
			Bundle args = new Bundle();
			
			switch (position) {
				case 0:
					Log.d(TAG, Integer.toString(position));
					fragment = new SetupSectionFragment();
					args.putInt(SetupSectionFragment.ARG_SECTION_NUMBER, position);
					fragment.setArguments(args);
					break;
				case 1:
					Log.d(TAG, Integer.toString(position));
					
					fragment = new RecvMsgSectionFragment();
					args.putInt(RecvMsgSectionFragment.ARG_SECTION_NUMBER, position);
					fragment.setArguments(args);
					
					/*
					ListFragment f = new RecvMsgSectionFragment();
					args.putInt(RecvMsgSectionFragment.ARG_SECTION_NUMBER, position);
					f.setArguments(args);
					return f;
					*/
					break;
				case 2:
					Log.d(TAG, Integer.toString(position));
					fragment = new SendMsgSectionFragment();
					args.putInt(SendMsgSectionFragment.ARG_SECTION_NUMBER, position);
					fragment.setArguments(args);
					break;
			}
			
			return fragment;
		}

		@Override
		public int getCount() {
			// Show 3 total pages.
			return 3;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
			case 0:
				return getString(R.string.title_section1).toUpperCase();
			case 1:
				return getString(R.string.title_section2).toUpperCase();
			case 2:
				return getString(R.string.title_section3).toUpperCase();
			}
			return null;
		}
	}

	public static class RecvMsgSectionFragment extends Fragment implements LoaderCallbacks<Cursor> {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";

		private static final String TAG = "RecvMsgSectionFragment";
		private int LOADER_ID = 0;
		private ListView mList;
		private RecvMsgCursorAdapter mAdapter;
		
		private LoaderManager.LoaderCallbacks<Cursor> mCallbacks;

		
		public RecvMsgSectionFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) 
		{
			//int section = getArguments().getInt(ARG_SECTION_NUMBER);
			return inflater.inflate(R.layout.tab_incoming_msg, null);
		}
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			
			// enable fragment to add menu items
			this.setHasOptionsMenu(true);
						
			// cursor loader / adapter machinery
			mCallbacks = this;
			getActivity().getLoaderManager().initLoader(LOADER_ID, null, mCallbacks);
			mAdapter = new RecvMsgCursorAdapter(getActivity(), 0, null, null, null, 0);
		}
		
		@Override
	    public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			
			try {
				mList = (ListView)getView().findViewById(R.id.listRecvMsg);
			} catch (NullPointerException e) { 
				Log.e(TAG, "problem getting view for listview");
			}
			
			if (mList == null) {
				Log.e(TAG, "listview reference is null!");
			} 
			mList.setAdapter(mAdapter);
		}
		
		@Override
		public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		    super.onCreateOptionsMenu(menu, inflater);
		    menu.add(0, MENU_OPTION_RECD_CLEARALL, Menu.NONE, "Clear Rec'd Messages");
		}
		
		// LoaderCallbacks interface method
		public CursorLoader onCreateLoader(int id, Bundle args) {
			//return null;
			
			/*
			String[] projection = {BaselineTableSchema.FOREIGN_KEY_REF, 
					BaselineTableSchema.INTEGER, 
					BaselineTableSchema.TEXT_VALUE,
					BaselineTableSchema.TIMESTAMP_VALUE,
					BaselineTableSchema._RECEIVED_DATE,
					BaselineTableSchema.CONTENT_CHECKSUM};
					*/
			String[] projection = null;
			//String selection = BaselineTableSchema._DISPOSITION + "=?";
			//String[] selectionArgs = {"REMOTE.gateway"};
			String selection = BaselineTableSchema._DISPOSITION + " like '%REMOTE%'";
			String[] selectionArgs = null;   //{"REMOTE"};
			String sortOrder = null;
			
			CursorLoader loader = new CursorLoader(
					this.getActivity(),
					BaselineTableSchema.CONTENT_URI, 
					projection, 
					selection, 
					selectionArgs, 
					sortOrder);
			return loader;
			
		}
		
		// LoaderCallbacks interface method
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
			if (data != null) {
				Log.d(TAG, "  count: " + String.valueOf(data.getCount()));
			}
			
			// swap cursor
			try {
				mAdapter.swapCursor(data);
			} catch (NullPointerException e) {
				Log.e(TAG, "onLoadFinished - something happened! (null pointer) ");
				return;
			} catch (IllegalArgumentException e) {
				Log.e(TAG, "onLoadFinished - something happened! (illegal argument) ");
				return;
			}
			
			// scroll ListView to bottom
			try {
				mList.setSelection(data.getCount() -1);
			} catch (NullPointerException e) {
				Log.e(TAG, "onLoadFinished - listview is null");
				return;
			}
		}
		
		// LoaderCallbacks interface method
		public void onLoaderReset(Loader<Cursor> loader) {
			mAdapter.swapCursor(null);
		}
	}
	
	
	public static class SetupSectionFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";

		private static final String TAG = "SetupSectionFragment";
		
		
		public SetupSectionFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			// Create a new TextView and set its text to the fragment's section
			// number argument value.
			/*
			TextView textView = new TextView(getActivity());
			textView.setGravity(Gravity.CENTER);
			textView.setText(Integer.toString(getArguments().getInt(
					ARG_SECTION_NUMBER)));
			return textView;
			*/
			int section = getArguments().getInt(ARG_SECTION_NUMBER);
			
			return inflater.inflate(R.layout.tab_test_setup, null);
		}
	}
	
	public static class SendMsgSectionFragment extends Fragment implements LoaderCallbacks<Cursor>  {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";

		private static final String TAG = "SendMsgSectionFragment";
		private int LOADER_ID = 1;
		private ListView mList;
		private SendMsgCursorAdapter mAdapter; 
		private LoaderManager.LoaderCallbacks<Cursor> mCallbacks;

		public SendMsgSectionFragment() {
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			
			// enable fragment to add menu items
			this.setHasOptionsMenu(true);
			
			// cursor loader / adapter machinery
			mCallbacks = this;
			getActivity().getLoaderManager().initLoader(LOADER_ID, null, mCallbacks);
			mAdapter = new SendMsgCursorAdapter(getActivity(), 0, null, null, null, 0);			
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) 
		{
			//int section = getArguments().getInt(ARG_SECTION_NUMBER);
			return inflater.inflate(R.layout.tab_outgoing_msg, null);
		}
		
		@Override
	    public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			
			try {
				mList = (ListView)getView().findViewById(R.id.listSentMsg);
			} catch (NullPointerException e) { 
				Log.e(TAG, "problem getting view for listview");
			}
			
			if (mList == null) {
				Log.e(TAG, "listview reference is null!");
			} 
			mList.setAdapter(mAdapter);
		}
		
		@Override
		public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		    super.onCreateOptionsMenu(menu, inflater);
		    menu.add(0, MENU_OPTION_SENT_CLEARALL, Menu.NONE, "Clear Sent Messages");
		}
		
		// LoaderCallbacks interface method
		public CursorLoader onCreateLoader(int id, Bundle args) {
			//return null;
			
			String[] projection = null;
			String selection = BaselineTableSchema._DISPOSITION + "=?";
			String[] selectionArgs = {"LOCAL"};
			String sortOrder = null;
			
			CursorLoader loader = new CursorLoader(
					this.getActivity(),
					BaselineTableSchema.CONTENT_URI, 
					projection, 
					selection, 
					selectionArgs, 
					sortOrder);
			return loader;
		}
				
		// LoaderCallbacks interface method
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
			if (data != null) {
				Log.d(TAG, "  count: " + String.valueOf(data.getCount()));
			}
					
			// swap cursor
			try {
				mAdapter.swapCursor(data);
			} catch (NullPointerException e) {
				Log.e(TAG, "onLoadFinished - something happened! (null pointer) ");
				//e.printStackTrace();
				//return;
			} catch (IllegalArgumentException e) {
				Log.e(TAG, "onLoadFinished - something happened! (illegal argument) ");
				//e.printStackTrace();
				//return;
			}
					
			// scroll ListView to bottom
			try {
				mList.setSelection(data.getCount() -1);
			} catch (NullPointerException e) {
				Log.e(TAG, "onLoadFinished - listview is null");
				return;
			}
		}
				
		// LoaderCallbacks interface method
		public void onLoaderReset(Loader<Cursor> loader) {
			mAdapter.swapCursor(null);
		}
	}
	
	/**
	 * A dummy fragment representing a section of the app, but that simply
	 * displays dummy text.
	 */
	public static class DummySectionFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";

		public DummySectionFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			// Create a new TextView and set its text to the fragment's section
			// number argument value.
			TextView textView = new TextView(getActivity());
			textView.setGravity(Gravity.CENTER);
			textView.setText(Integer.toString(getArguments().getInt(
					ARG_SECTION_NUMBER)));
			return textView;
		}
	}

	private void launchSettingsActivity() {
        Log.d(TAG, "showing settings");
        Intent intent = new Intent(this, GlobalSettingsActivity.class);
        startActivity(intent);
    }

    private void launchAboutActivity() {
        Log.d(TAG, "showing about");
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }

    private void clearRecdMessages() {
    	Log.d(TAG, "clearRecdMessages");
		
    	String selection = BaselineTableSchema._DISPOSITION + "=?";
		String[] selectionArgs = {"REMOTE.gateway"};
		Uri uri = BaselineTableSchema.CONTENT_URI;
		
		deleteWithContentResolver(uri, selection, selectionArgs);
	}

	private void clearSentMessages() {
		Log.d(TAG, "clearSentMessages");
		
		String selection = BaselineTableSchema._DISPOSITION + "=?";
		String[] selectionArgs = {"LOCAL"};
		Uri uri = BaselineTableSchema.CONTENT_URI;
		
		deleteWithContentResolver(uri, selection, selectionArgs);
	}

	private void deleteWithContentResolver(Uri uri, String selection, String[] selectionArgs) {
		Log.d(TAG, "deleteWithContentResolver");
		String msg = "";
		
		// Display Y/N confirmation dialogue
		/*
		{
			new AlertDialog.Builder(this)
	        .setTitle("Confirm delete")
	        .setMessage("Really delete?")
	        .setPositiveButton("Yes", null)
	        .setNegativeButton("No", null)
	        .show();
		}
		*/
		
		// Delete selected rows and display confirmation message to user
		try {
			int numDel = getContentResolver().delete(uri, selection, selectionArgs);
			
			if (numDel > 0) {
				msg = "Successfully deleted " + String.valueOf(numDel) + " rows";
			} else {
				msg = "No rows found to delete";
			}
			
		} catch (NullPointerException e) {
			Log.e(TAG, "caught a null pointer?");
			msg = "Error deleting messages";
		}
		
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
	}
	
    public void setupTestOptions(View view) {
    	Log.d(TAG, "test options");
    	Intent intent = new Intent(this, TestSetupActivity2.class);
        startActivity(intent);
    }
    
    // (presently unused) start a separate activity which does the same thing as RecvMsgSectionFragment
    @SuppressWarnings("unused")
	private void startReceiverActivity(View view) {
    	Log.d(TAG, "startReceiverActivity");
    	Intent intent = new Intent(this, ReceiverActivity.class);
        startActivity(intent);
    }
    
    public void startCurrentTest(View view) {
    	Log.d(TAG, "start test");
    	beginTest();
    }
    
    public void buttonSendQuick(View view) {
    	Log.d(TAG, "button quick");
    	sendQuickAmmoMsg();
    }
    
    private void sendQuickAmmoMsg() {
        Log.d(TAG, "launch quick test");
        
        // Populate message content values
        ContentValues cv = new ContentValues();
        cv.put(BaselineTableSchema.TEST_ITEM, 1);
        cv.put(BaselineTableSchema.TEST_NAME, "Quick message send");
        cv.put(BaselineTableSchema.FOREIGN_KEY_REF, 1);
        cv.put(BaselineTableSchema.INTEGER, TestUtils.TEST_INTEGER ); 
        cv.put(BaselineTableSchema.LONG_INTEGER, TestUtils.TEST_LONG_INTEGER);
        cv.put(BaselineTableSchema.SHORT_INTEGER, TestUtils.TEST_SHORT_INTEGER);
        cv.put(BaselineTableSchema.BOOLEAN, TestUtils.TEST_BOOLEAN);
        cv.put(BaselineTableSchema.FLOATING_POINT_NUMBER, TestUtils.TEST_FLOAT);
        cv.put(BaselineTableSchema.GLOBALLY_UNIQUE_ID, TestUtils.randomGuidAsString()); //TestUtils.TEST_GUID_STR);
        cv.put(BaselineTableSchema.TEXT_VALUE, TestUtils.TEST_FIXED_STRING);  //TestUtils.randomText(20));
        cv.put(BaselineTableSchema.TIMESTAMP_VALUE, System.currentTimeMillis());
        cv.put(BaselineTableSchema.EXCLUSIVE_ENUMERATION, BaselineTableSchema.EXCLUSIVE_ENUMERATION_HIGH);
        cv.put(BaselineTableSchema.INCLUSIVE_ENUMERATION, BaselineTableSchema.INCLUSIVE_ENUMERATION_APPLE);
        cv.put(BaselineTableSchema.CONTENT_CHECKSUM, new Long("9223372036854775806").longValue()); 

        // TMP: file
        cv.put(BaselineTableSchema.A_FILE, "/sdcard/wolverine.png");
        cv.put(BaselineTableSchema.A_FILE_TYPE, "image/png");
        
        Long cksum = BaselineSchemaMessageUtils.computeChecksum(cv);
		Log.d(TAG, "checksum: " + String.valueOf(cksum));
		if (cksum > 1L) {
			cv.put(BaselineTableSchema.CONTENT_CHECKSUM, cksum);
		}
		
        // Insert values to provider and post to Ammo
        final Uri thisUri = getContentResolver().insert(BaselineTableSchema.CONTENT_URI, cv);
        try {
        	this.ab.provider(thisUri).topicFromProvider().post();
        	Log.d(TAG, "posted to Ammo with Uri: " + thisUri.toString());
        	Toast.makeText(this, "Quick 1 message sent", Toast.LENGTH_SHORT).show();
        } catch (RemoteException ex) {
        	Log.e(TAG, "post incident failed: " + ex.toString());
        }
    }
    
    private void beginTest() {
        Log.d(TAG, "begin test");
        
        //TMP
        //SharedPreferences prefs = getSharedPreferences()
        
        IndividualTest test = new IndividualTest(this);
        if (test.set()) {
        	Thread t = new Thread(test);
        	t.start();
        	//Intent intent = new Intent(this, TestProgressActivity.class);
            //startActivity(intent);
        } else {
        	Log.e(TAG, "problem starting test");
        	Toast.makeText(this, "problem starting test", Toast.LENGTH_SHORT).show();
        	return;
        }
        
        //Intent intent = new Intent(this, TestProgressActivity.class);
        //Intent intent = new Intent(this, TabActivity.class);
        //startActivity(intent);
        
    }
    
    public void stopRunningTest(View view) {
    	Log.d(TAG, "halt running test");
    }
}

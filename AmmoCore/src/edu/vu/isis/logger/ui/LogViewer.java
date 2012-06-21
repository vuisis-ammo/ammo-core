package edu.vu.isis.logger.ui;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;
import android.widget.Toast;
import edu.vu.isis.ammo.core.R;

public class LogViewer extends ListActivity {
	
	private LogElementAdapter mAdapter;
	private ListView mListView;
	private ProgressDialog mProgDialog;
	private final AtomicBoolean isPaused = new AtomicBoolean(false);
	private final AtomicBoolean isAutoJump = new AtomicBoolean(true);
	
	/* Menu constants */
	private static final int TOGGLE_MENU = Menu.NONE + 0;
	private static final int JUMP_TOP_MENU = Menu.NONE + 1;
	private static final int JUMP_BOTTOM_MENU = Menu.NONE + 2;
	private static final int OPEN_PREFS_MENU = Menu.NONE + 3;
	
	private final Logger logger = LoggerFactory.getLogger("ui.logger.logviewer");
	private LogReader mLogReader;
	private String[] logSrcArr;

	public static final int NEW_DATA_MSG = 0;
	public static final int START_PROG_DIALOG = 1;
	public static final int DISMISS_PROG_DIALOG = 2;
	
	public static final String EXTRA_NAME = "source";
	
	public Handler mHandler = new Handler() {
		
		LogViewer parent = LogViewer.this;
		
		@Override
		public void handleMessage(Message msg) {
			
			switch(msg.what) {
			case NEW_DATA_MSG:
				if(msg.obj != null) {
					@SuppressWarnings("unchecked")
					final List<LogElement> elemList = (List<LogElement>) msg.obj;
					refreshList(elemList);
				}
				break;
			case START_PROG_DIALOG:
				parent.mProgDialog.setIndeterminate(true);
				parent.mProgDialog.setMessage("Loading log...");
				parent.mProgDialog.show();
				break;
			case DISMISS_PROG_DIALOG:
				parent.mProgDialog.dismiss();
				break;
			default:
				parent.logger.error("Handler received malformed message");
			}
			
		}

	};
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.log_viewer);
		
		this.mProgDialog = new ProgressDialog(this);

		this.mAdapter = new LogElementAdapter(this, R.layout.log_display_row);
		this.mListView = getListView();
		setListAdapter(this.mAdapter);
		this.mListView.setDivider(null);
		
		this.mListView.setOnScrollListener(getOnScrollListener());
		this.mListView.setOnTouchListener(getOnTouchListener());

		processIntent();
		
	}
	
	
	@Override
	public void onResume() {
		super.onResume();
		initializeLogReader();
		adjustMaxLines();
		this.mLogReader.start();
	}
	
	
	@Override
	public void onPause() {
		super.onPause();
		this.mLogReader.terminate();
		this.mLogReader = null;
		this.mAdapter.clear();
	}
	
	
	private void processIntent() {
		
		final String logSource = this.getIntent().getStringExtra(EXTRA_NAME);
		
		if(logSource == null) {
			this.logger.error("Intent had no extra indicating log source");
			return;
		}
		
		this.logSrcArr = logSource.split(" ");
		
		if(this.logSrcArr.length == 0) {
			this.logger.error("Intent had empty log source");
		}
		
	}
	
	private void initializeLogReader() {
		
		if (this.logSrcArr[0].equals("logcat")) {
			
			try {
				this.mLogReader = new LogcatLogReader(this, mHandler);
			} catch (IOException e) {
				final String logcatError = "Could not read from Logcat";
				this.logger.error(logcatError);
				Toast.makeText(this, logcatError, Toast.LENGTH_LONG);
				e.printStackTrace();
				return;
			}
			
		} else if (this.logSrcArr[0].equals("file") && this.logSrcArr.length == 2) {
			
			final String filepath = logSrcArr[1];
			try {
				this.mLogReader = new FileLogReader(this, this.mHandler, filepath);
			} catch (FileNotFoundException e) {
				this.logger.error("Could not find the specified file: {}",
						filepath);
				Toast.makeText(this, "Could not find file: " + filepath, Toast.LENGTH_LONG);
				e.printStackTrace();
				return;
			}
			
		} else {
			this.logger.error("Intent had malformed log source");
			return;
		}
		
	}

	
	private void adjustMaxLines() {
		
		SharedPreferences prefs = getSharedPreferences("edu.vu.isis.ammo.core_preferences", MODE_PRIVATE);
		if(this.mLogReader instanceof LogcatLogReader) {
			this.mAdapter.setMaxLines(Math.abs(Integer.parseInt(prefs.getString("logcat_max_lines", "0"))));
		} else if(this.mLogReader instanceof FileLogReader) {
			this.mAdapter.setMaxLines(Math.abs(Integer.parseInt(prefs.getString("file_max_lines", "0"))));
		}
		
	}
	
	
	private OnScrollListener getOnScrollListener() {
		return new OnScrollListener() {

			private LogViewer parent = LogViewer.this;
			
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				if(view.getLastVisiblePosition()-1 == view.getAdapter().getCount()) {
					parent.isAutoJump.set(true);
				}
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
				final boolean atEndOfList = (visibleItemCount + firstVisibleItem == totalItemCount);
				if(atEndOfList) {
					parent.isAutoJump.set(true);
				}
			}
			
		};
	}

	
	private OnTouchListener getOnTouchListener() {
		return new OnTouchListener() {
			
			private LogViewer parent = LogViewer.this;
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				parent.isAutoJump.set(false);
				return false;
			}
			
		};
	}
	
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		
		menu.clear();
		
        menu.add(Menu.NONE, TOGGLE_MENU, Menu.NONE,
        		(this.isPaused.get() ? "Play" : "Pause"));
        menu.add(Menu.NONE, JUMP_BOTTOM_MENU, Menu.NONE, "Go to bottom");
        menu.add(Menu.NONE, JUMP_TOP_MENU, Menu.NONE, "Go to top");
        menu.add(Menu.NONE, OPEN_PREFS_MENU, Menu.NONE, "Open preferences");
        
        return super.onPrepareOptionsMenu(menu);
        
    }
	
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		boolean returnValue = true;
        switch (item.getItemId()) {
        case TOGGLE_MENU:
            if(isPaused.get()) {
            	play();
            } else {
            	pause();
            }
            break;
        case JUMP_BOTTOM_MENU:
            setScrollToBottom();
            break;
        case JUMP_TOP_MENU:	
        	setScrollToTop();
        	break;
        case OPEN_PREFS_MENU:
        	final Intent intent = new Intent().setClass(this, LogViewerPreferences.class);
        	startActivityForResult(intent, 0);
        default:
        	returnValue = false;
        }
        return returnValue;
    }
	
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		super.onActivityResult(requestCode, resultCode, data);
		adjustMaxLines();
		
	}

	
	private void play() {
		this.isPaused.set(false);
		this.mLogReader.resumeUpdating();
		this.mLogReader.forceUpdate();
	}

	
	private void pause() {
		this.isPaused.set(true);
		this.mLogReader.stopUpdating();
	}

	
	private void setScrollToTop() {
		this.mListView.setSelection(0);
		this.isAutoJump.set(false);
	}

	
	private void setScrollToBottom() {
		this.mListView.setSelection(this.mAdapter.getCount()-1);
		this.isAutoJump.set(true);
	}

	
	private void refreshList(List<LogElement> elemList) {
		
		updateAdapter(elemList);
		if(isAutoJump.get()) {
			setScrollToBottom();
		}
		
	}

	
	private void updateAdapter(List<LogElement> elemList) {
		this.mAdapter.addAll(elemList);
	}
	
	
}

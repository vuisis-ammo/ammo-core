package edu.vu.isis.ammo.core.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Environment;
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
import edu.vu.isis.ammo.R;

public class LogViewer extends ListActivity {
	
	// TODO: Allow filepath to vary
//	private final String filepath = Environment.getExternalStorageDirectory()
//			.getAbsolutePath() + "/ammo-perf.log";
	
	private LogElementAdapter mAdapter;
	private ListView mListView;
	private ProgressDialog mProgDialog;
	private final AtomicBoolean isPaused = new AtomicBoolean(false);
	private final AtomicBoolean isAutoJump = new AtomicBoolean(true);

	/* Simultaneous log entry test thread */
	private boolean DEBUG_MODE = false;
	private Thread testThread;
	private final AtomicBoolean isDebugRunning = new AtomicBoolean(false);
	
	/* Menu constants */
	private static final int TOGGLE_MENU = Menu.NONE + 0;
	private static final int JUMP_TOP_MENU = Menu.NONE + 1;
	private static final int JUMP_BOTTOM_MENU = Menu.NONE + 2;
	
	private final Logger logger = LoggerFactory.getLogger("ui.logger.logviewer");
	private LogReader mLogReader;

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
				parent.mProgDialog.setMessage("Loading file...");
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
		
		this.testThread = getDebugThread();
		this.mProgDialog = new ProgressDialog(this);

		this.mAdapter = new LogElementAdapter(this, R.layout.log_display_row);
		this.mListView = getListView();
		setListAdapter(this.mAdapter);
		this.mListView.setDivider(null);
		
		this.mListView.setOnScrollListener(getOnScrollListener());
		this.mListView.setOnTouchListener(getOnTouchListener());

		processIntent();
		
		this.isDebugRunning.set(DEBUG_MODE);
		this.testThread.start();
		this.mLogReader.start();
		
	}
	
	private void processIntent() {
		
		final String logSource = this.getIntent().getStringExtra(EXTRA_NAME);
		
		if(logSource == null) {
			this.logger.error("Intent had no extra indicating log source");
			return;
		}
		
		final String[] logSrcArr = logSource.split(" ");
		
		if(logSrcArr.length == 0) {
			this.logger.error("Intent had empty log source");
		}

		if (logSrcArr[0].equals("logcat")) {
			try {
				this.mLogReader = new LogcatLogReader(this, mHandler);
			} catch (IOException e) {
				this.logger.error("Could not read from Logcat");
				e.printStackTrace();
				return;
			}
		} else if (logSrcArr[0].equals("file") && logSrcArr.length == 2) {
			final String filepath = logSrcArr[1];
			try {
				this.mLogReader = new FileLogReader(this, mHandler, filepath);
			} catch (FileNotFoundException e) {
				this.logger.error("Could not find the specified file: {}",
						filepath);
				e.printStackTrace();
			}
		} else {
			this.logger.error("Intent had malformed log source");
			return;
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
        default:
        	returnValue = false;
        }
        return returnValue;
    }

	private void play() {
		this.isPaused.set(false);
		this.mLogReader.resumeUpdating();
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

	private Thread getDebugThread() {
		
		return new Thread() {

			@Override
			public void run() {
				try {
					while (isDebugRunning.get()) {
						logger.error("Time: " + System.currentTimeMillis());
						Thread.sleep(1000);
					}
				} catch (Throwable t) {
					// Do nothing
				}
			}

		};
	}
	
	private void clearFileAndList() {
		
		// TODO: Implement
		
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

	@Override
	public void onResume() {
		super.onResume();
		this.mLogReader.resumeUpdating();
		this.isDebugRunning.set(DEBUG_MODE);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		this.mLogReader.stopUpdating();
		this.isDebugRunning.set(false);
	}
	
	
}
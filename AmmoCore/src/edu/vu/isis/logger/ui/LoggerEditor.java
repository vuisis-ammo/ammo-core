package edu.vu.isis.logger.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import edu.vu.isis.ammo.R;

/**
 * This class provides a user interface to edit the Level of all Logger objects
 * active in the application.
 * @author Nick King
 *
 */

public class LoggerEditor extends ListActivity {


	private static final int READ_MENU = Menu.NONE + 0;
	private static final int TOGGLE_APPTXT_MENU = Menu.NONE + 1;
	private static final int RESET_APP_MENU = Menu.NONE + 2;

	private Logger selectedLogger;
	private View selectedView;
	private Tree<Logger> loggerTree;

	// We use this logger to log for this Activity
	private final Logger personalLogger = Loggers.getLoggerByName("ui.logger.editor");

	private TextView selectionText;
	private WellBehavedSpinner levelSpinner;
	private MyOnSpinnerDialogClickListener spinnerListener;
	private ListView mListView;
	private LoggerIconAdapter mAdapter;

	private final List<Appender<ILoggingEvent>> availableAppenders = AppenderStore
			.getInstance().getAppenders();
//	Loggers
//	.getConfiguredAppenders(Loggers.findLogbackConfigFile(this),
//			this.personalLogger,
//			(ch.qos.logback.core.Context) LoggerFactory
//					.getILoggerFactory());

	private final String[] appenderNames = new String[availableAppenders.size()];

	private AtomicBoolean showAppenderText = new AtomicBoolean(true);

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.logger_editor);

		// LoggerContext provides access to a List of all active loggers
		final LoggerContext lc = (LoggerContext)LoggerFactory.getILoggerFactory();
		final List<Logger> loggerList = lc.getLoggerList();
		this.loggerTree = makeTree(loggerList);
		initAppenderNames();

		this.mListView = super.getListView();

		this.mAdapter = new LoggerIconAdapter(loggerTree, this,
				R.layout.logger_row, R.id.logger_text);
		this.setListAdapter(mAdapter);

		this.selectionText = (TextView) findViewById(R.id.selection_text);
		this.levelSpinner = (WellBehavedSpinner) findViewById(R.id.level_spinner);

		final ArrayAdapter<CharSequence> spinAdapter = ArrayAdapter.createFromResource(
				this, R.array.level_options, 
				android.R.layout.simple_spinner_item);
		spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		this.levelSpinner.setAdapter(spinAdapter);
		this.spinnerListener = new MyOnSpinnerDialogClickListener();
		this.levelSpinner.setOnSpinnerDialogClickListener(this.spinnerListener);
		this.levelSpinner.setSelection(CLEAR_IX);

		// Set the selection text to indicate nothing is selected
		this.updateSelText(null);

		if(savedInstanceState == null) return;

		// Set the list back to its previous position
		final int savedVisiblePosition = savedInstanceState.getInt("savedVisiblePosition");
		this.mListView.setSelection(savedVisiblePosition);

		this.selectedView = null;
		this.selectedLogger = null;

		final boolean wasLoggerSelected = savedInstanceState
				.getBoolean("wasLoggerSelected");
		if (wasLoggerSelected) {
			Toast.makeText(this, "Please reselect logger.", Toast.LENGTH_LONG)
			.show();
		}

		final boolean showAppText = savedInstanceState.getBoolean("showAppText");
		this.showAppenderText.set(showAppText);


	}


	@Override
	public void onSaveInstanceState(Bundle outState) {

		final int savedVisiblePosition = this.mListView
				.getFirstVisiblePosition();
		outState.putInt("savedVisiblePosition", savedVisiblePosition);

		final boolean wasLoggerSelected = (selectedLogger != null);
		outState.putBoolean("wasLoggerSelected", wasLoggerSelected);

		final boolean showAppText = this.showAppenderText.get();
		outState.putBoolean("showAppText", showAppText);

	}



	private Tree<Logger> makeTree(List<Logger> list) {

		final Tree<Logger> mTree = new Tree<Logger>(Loggers.ROOT_LOGGER);

		for(final Logger logger : list) {			
			if (logger.equals(Loggers.ROOT_LOGGER)) {
				continue;
			}
			final String loggerName = logger.getName();
			safelyAddLeaf(mTree, logger, loggerName);
		}

		return mTree;

	}




	/**
	 * Adds a leaf to the tree with only the assumption that the Root logger is
	 * at the top of the tree.  The order in which leaves are added does not
	 * matter because the algorithm always checks if the parent leaves
	 * have been added to the tree before adding a child leaf.  For example, if
	 * a Logger named "edu.foo.bar" were given, then we would first check if
	 * "edu.foo" and "edu" had been added to the tree, and if not, we would 
	 * add them before adding "edu.foo.bar"
	 * @param mTree -- the Tree to which leaves are added
	 * @param aLogger -- the Logger to be added to the Tree
	 * @param loggerName -- the name of the Logger
	 */
	private void safelyAddLeaf(Tree<Logger> mTree, Logger aLogger,
			String loggerName) {

		if (mTree.contains(aLogger))
			return;
		final String parentLoggerName = Loggers
				.getParentLoggerName(aLogger);

		// We can use == here because the getParentLoggerName method
		// returns to us a static reference of this String object.
		// We get a minor performance boost from this.
		if (parentLoggerName == Logger.ROOT_LOGGER_NAME) {
			mTree.addLeaf(Loggers.ROOT_LOGGER, aLogger);
			return;
		}

		final Logger parentLogger = Loggers
				.getLoggerByName(parentLoggerName);

		safelyAddLeaf(mTree, parentLogger, parentLoggerName);
		mTree.addLeaf(parentLogger, aLogger);
		return;
	}
	
		
	private void initAppenderNames() {
		for(int i=0; i<this.availableAppenders.size(); i++) {
			this.appenderNames[i] = this.availableAppenders.get(i).getName();
		}
	}


	@Override
	public void onListItemClick(ListView parent, View row, int position, long id) {

		final Logger nextSelectedLogger = (Logger)parent.getItemAtPosition(position);
		final Level effective = nextSelectedLogger.getEffectiveLevel();

		updateSelText(nextSelectedLogger.getName());

		if (this.selectedLogger == null) {
			this.spinnerListener.updateSpinner(effective, this.levelSpinner);
		} else if (nextSelectedLogger.equals(this.selectedLogger)) {
			return;
		} else if ((! effective.equals(selectedLogger.getEffectiveLevel())) || 
				this.levelSpinner.getSelectedItemPosition() == CLEAR_IX) {
			this.spinnerListener.updateSpinner(effective, this.levelSpinner);
		}
		this.selectedLogger = nextSelectedLogger;
		this.selectedView = row;

		refreshList();
	}



	private void refreshList() {
		this.mListView.invalidateViews();
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		super.onCreateOptionsMenu(menu);
		menu.add(Menu.NONE, READ_MENU, Menu.NONE, "Read logs");
		menu.add(Menu.NONE, TOGGLE_APPTXT_MENU, Menu.NONE, "Toggle Appender text");
		menu.add(Menu.NONE, RESET_APP_MENU, Menu.NONE, "Reset Appenders to ROOT configuration");

		return true;

	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		boolean returnValue = true;
		switch (item.getItemId()) {
		case READ_MENU:
			createReaderSelectorDialog();
			break;
		case TOGGLE_APPTXT_MENU:
			this.showAppenderText.set(!this.showAppenderText.get());
			refreshList();
			break;
		case RESET_APP_MENU:
			Loggers.copyHeadAppenderSettings(loggerTree);
			refreshList();
			break;
		default:
			returnValue = false;
		}

		return returnValue;
	}


	private void createReaderSelectorDialog() {

		final AlertDialog.Builder bldr = new AlertDialog.Builder(this);
		
		

		final OnClickListener dlgListener = new OnClickListener() {

			LoggerEditor parent = LoggerEditor.this;

			@Override
			public void onClick(DialogInterface dialog, int which) {

				final Appender<ILoggingEvent> whichAppender = availableAppenders.get(which);

				if(whichAppender == null) {
					final String errMsg = "Could not find Appender";
					parent.personalLogger.error(errMsg);
					Toast.makeText(parent, errMsg, Toast.LENGTH_LONG).show();
					return;
				}

				String cmd = new String();

				if (whichAppender instanceof LogcatAppender) {
					cmd = "logcat";
				} else if (whichAppender instanceof FileAppender) {
					final FileAppender<ILoggingEvent> fileAppender = (FileAppender<ILoggingEvent>) whichAppender;
					final String filename = fileAppender.getFile();
					cmd = "file " + filename;
				} else {
					Toast.makeText(parent,
							"No reader is available for that Appender",
							Toast.LENGTH_LONG).show();
					return;
				}

				final Intent intent = parent.getIntent();
				parent.personalLogger.debug("Putting extra in Intent for LogViewer: {}", cmd);
				intent.putExtra(LogViewer.EXTRA_NAME, cmd);
				intent.setClass(parent, LogViewer.class);
				parent.startActivity(intent);

			}

		};

		bldr.setItems(appenderNames, dlgListener)
				.setTitle("Select an Appender to view its logs:").show();

	}


	private void updateIcon(Level lvl, View row) {
		final ImageView iv = (ImageView) (row.findViewById(R.id.logger_icon));
		final String loggerName = (String) ((TextView) row
				.findViewById(R.id.logger_text)).getText();
		if(Loggers.isInheritingLevel(Loggers.getLoggerByName(loggerName))) {
			setEffectiveIcon(lvl, iv);
		} else {
			setActualIcon(lvl, iv);
		}
		refreshList();
	}
	

	private void setEffectiveIcon(Level lvl, ImageView iv) {
		switch (lvl.levelInt) {
		case Level.TRACE_INT:
			iv.setImageResource(R.drawable.effective_trace_level_icon);
			break;
		case Level.DEBUG_INT:
			iv.setImageResource(R.drawable.effective_debug_level_icon);
			break;
		case Level.INFO_INT:
			iv.setImageResource(R.drawable.effective_info_level_icon);
			break;
		case Level.WARN_INT:
			iv.setImageResource(R.drawable.effective_warn_level_icon);
			break;
		case Level.ERROR_INT:
			iv.setImageResource(R.drawable.effective_error_level_icon);
			break;
		case Level.OFF_INT:
		default:
			iv.setImageResource(R.drawable.effective_off_level_icon);
		}
	}
	

	private void setActualIcon(Level lvl, ImageView iv) {
		switch (lvl.levelInt) {
		case Level.TRACE_INT:
			iv.setImageResource(R.drawable.actual_trace_level_icon);
			break;
		case Level.DEBUG_INT:
			iv.setImageResource(R.drawable.actual_debug_level_icon);
			break;
		case Level.INFO_INT:
			iv.setImageResource(R.drawable.actual_info_level_icon);
			break;
		case Level.WARN_INT:
			iv.setImageResource(R.drawable.actual_warn_level_icon);
			break;
		case Level.ERROR_INT:
			iv.setImageResource(R.drawable.actual_error_level_icon);
			break;
		case Level.OFF_INT:
		default:
			iv.setImageResource(R.drawable.actual_off_level_icon);
		}
	}


	private void updateSelText(String selection) {
		selectionText.setText((selection == null) ? "None selected" : selection);
	}


	static final int TRACE_IX = 0;
	static final int DEBUG_IX = 1;
	static final int INFO_IX = 2;
	static final int WARN_IX = 3;
	static final int ERROR_IX = 4;
	static final int OFF_IX = 5;
	static final int CLEAR_IX = 6;


	public class LoggerIconAdapter extends TreeAdapter<Logger> {
		final private LoggerEditor parent = LoggerEditor.this;

		private int tvId;

		public LoggerIconAdapter(Tree<Logger> objects, Context context, int resource,
				int textViewResourceId) {
			super(objects, context, resource, textViewResourceId);
			this.tvId = textViewResourceId;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup group) {

			final View row = super.getView(position, convertView, group);

			final TextView tv = (TextView)row.findViewById(this.tvId);
			final ImageView levelIV = (ImageView)(row.findViewById(R.id.logger_icon));
			final ImageView appenderIV = (ImageView)(row.findViewById(R.id.appender_icon));

			final Logger aLogger = super.getItem(position);

			final StringBuilder txtBldr = new StringBuilder(aLogger.getName());
			if(parent.showAppenderText.get()) {
				txtBldr.append("  ").append(getAllAppenderString(aLogger));
			}

			tv.setText(txtBldr.toString());

			if (Loggers.isInheritingLevel(aLogger)) {
				tv.setTextAppearance(parent, R.style.unselected_logger_font);
				parent.setEffectiveIcon(aLogger.getEffectiveLevel(), levelIV);
			} else {
				tv.setTextAppearance(parent, R.style.selected_logger_font);
				parent.setActualIcon(aLogger.getEffectiveLevel(), levelIV);
			}

			if(aLogger == Loggers.ROOT_LOGGER) {
				appenderIV.setImageResource(R.drawable.appender_attached_icon);
			} else if(! aLogger.isAdditive()) {
				appenderIV.setImageResource(R.drawable.appender_attached_icon);
			} else {
				appenderIV.setImageBitmap(null);
			}

			final int viewColor = (aLogger.equals(parent.selectedLogger)) 
					? parent.getResources().getColor(R.color.selected_logger_bg)
							: parent.getResources().getColor(R.color.unselected_logger_bg);

					parent.setViewColor(row, viewColor);

					return row;
		}


		/**
		 * Returns a String assuming that all Loggers do not have additivity
		 * enabled for Appenders.  This means that there are no longer two
		 * different categories of Appenders (attached and effective), so
		 * the String can express the Appenders affecting a Logger in a 
		 * more terse way.
		 */
		@SuppressWarnings("unused")
		private String getTerseAppenderString(Logger aLogger) {

			final List<Appender<ILoggingEvent>> attachedList = Loggers.getAttachedAppenders(aLogger);
			StringBuilder nameBldr = new StringBuilder("[ Appenders: ");

			if(attachedList.isEmpty()) {
				nameBldr.append("none ");
			} else {
				for(Appender<ILoggingEvent> app : attachedList) {
					nameBldr.append(app.getName()).append(" ");
				}
			}

			nameBldr.append(']');
			return nameBldr.toString();

		}


		/**
		 * Makes a String indicating both the attached and
		 * inherited Appenders. 
		 */
		private String getAllAppenderString(Logger aLogger) {

			StringBuilder nameBldr = new StringBuilder();
			/*
			final List<Appender<ILoggingEvent>> attachedList = Loggers.getAttachedAppenders(aLogger);
			nameBldr.append("[ Attached Appenders: ");

			if(attachedList.isEmpty()) {
				nameBldr.append("none ");
			} else {
				for(Appender<ILoggingEvent> app : attachedList) {
					nameBldr.append(app.getName()).append(" ");
				}
			}
           	nameBldr.append(" |  Effective Appenders: ");
			 */
			nameBldr.append(" [ ");
			final List<Appender<ILoggingEvent>> effectiveList = Loggers.getEffectiveAppenders(aLogger);

			if(effectiveList.isEmpty()) {
				nameBldr.append("none ");
			} else {
				for(Appender<ILoggingEvent> app : effectiveList) {
					nameBldr.append(app.getName()).append(" ");
				}
			}

			return nameBldr.append(" ]").toString();

		}

	}


	private void setViewColor(View row, int color) {
		row.setBackgroundColor(color);
	}


	/**
	 * Get the appenders for the selected logger (matches current view)
	 * The selected logger is passed to the AppenderSelector.
	 * 
	 * @param v unused
	 */
	public void configureAppenders(View v) {

		if (this.selectedLogger != null) {
			final Intent intent = new Intent().putExtra(
					"edu.vu.isis.ammo.core.ui.LoggerEditor.selectedLogger",
					selectedLogger).setClass(this, AppenderSelector.class);

			startActivityForResult(intent, 0);
		} else {
			Toast.makeText(this,
					"Pick a logger before trying to configure its appenders.",
					0).show();
		}

	}

	/**
	 * Determine if appenders match the parent.
	 * Additivity is used as the "effective v. actual" indicator.
	 * 
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent outputIntent) {
		if (this.selectedLogger == Loggers.ROOT_LOGGER) {
			refreshList();
			return;
		}

		if(requestCode == 0) {
			if (Loggers.hasSameAppendersAsParent(this.selectedLogger)) {
				this.selectedLogger.setAdditive(true);
				Loggers.clearAppenders(this.selectedLogger);
			} else {
				this.selectedLogger.setAdditive(false);
			}
			refreshList();
		}
	}


	/**
	 * the spinner makes use of this listener.
	 */
	public class MyOnSpinnerDialogClickListener implements OnSpinnerDialogClickListener {

		final LoggerEditor parent = LoggerEditor.this;

		/**
		 * Sets the current text on the Spinner to match the given Level
		 * @param lvl
		 */
		public void updateSpinner(final Level lvl, final Spinner spinner) {

			switch (lvl.levelInt) {
			case Level.TRACE_INT:
				spinner.setSelection(TRACE_IX);
				break;
			case Level.DEBUG_INT:
				spinner.setSelection(DEBUG_IX);
				break;
			case Level.INFO_INT:
				spinner.setSelection(INFO_IX);
				break;
			case Level.WARN_INT:
				spinner.setSelection(WARN_IX);
				break;
			case Level.ERROR_INT:
				spinner.setSelection(ERROR_IX);
				break;
			case Level.OFF_INT:
			default:	
				spinner.setSelection(OFF_IX);
			}
		}

		/**
		 * Updates the logger and the icon in its row based on the selected
		 * level.
		 */
		public void onSpinnerDialogClick(int which) {

			if (parent.selectedLogger == null) {
				Toast.makeText(parent, "Please select a logger.", Toast.LENGTH_SHORT).show();
				return;
			}

			if (parent.selectedView == null) return;

			final Level nextLevel;

			switch (which) {
			case TRACE_IX:
				nextLevel = Level.TRACE;
				break;
			case DEBUG_IX:
				nextLevel = Level.DEBUG;
				break;
			case INFO_IX:
				nextLevel = Level.INFO;
				break;
			case WARN_IX:
				nextLevel = Level.WARN;
				break;
			case ERROR_IX:
				nextLevel = Level.ERROR;
				break;
			case OFF_IX:
				nextLevel = Level.OFF;
				break;
			case CLEAR_IX:
			default:
				if (selectedLogger.equals(Loggers.ROOT_LOGGER)) {
					Toast.makeText(parent, "Clearing the root logger is not allowed", Toast.LENGTH_LONG).show();
					return;
				}
				nextLevel = null;
			}

			parent.selectedLogger.setLevel(nextLevel);

			// We want to use the effective level for the icon if the
			// Logger's level is null
			updateIcon((nextLevel == null) ? parent.selectedLogger.getEffectiveLevel()
					: nextLevel, parent.selectedView);

		}

	}

}

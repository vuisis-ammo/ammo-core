package edu.vu.isis.ammo.core.ui;

import java.util.List;

import org.slf4j.LoggerFactory;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
import edu.vu.isis.ammo.R;
import edu.vu.isis.ammo.util.Tree;

/**
 * This class provides a user interface to edit the Level of all Logger objects
 * active in the application.
 * @author Nick King
 *
 */

public class LoggerEditor extends ListActivity {

	public static final String DUMMY_LOGGER_NAME = "dummyref";
	
	private Logger rootLogger;
	private Logger selectedLogger;
	
	@SuppressWarnings("unused")
	private Logger personalLogger;
	
	private TextView selectionText;
	private WellBehavedSpinner levelSpinner;
	private MyOnSpinnerDialogClickListener spinnerListener;
	private ListView mListView;
	private LoggerIconAdapter mAdapter;

	private View selectedView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logger_editor);
		this.personalLogger = getLoggerByName("ui.logger.editor");

		// LoggerContext provides access to a List of all active loggers
		final LoggerContext lc = (LoggerContext)LoggerFactory.getILoggerFactory();
		final List<Logger> loggerList = lc.getLoggerList();
		final Tree<Logger> loggerTree = makeTree(loggerList);

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
		

	}
	
	
	@Override
	public void onSaveInstanceState(Bundle outState) {

		final int savedVisiblePosition = this.mListView
				.getFirstVisiblePosition();
		outState.putInt("savedVisiblePosition", savedVisiblePosition);
		
		final boolean wasLoggerSelected = (selectedLogger != null);
		outState.putBoolean("wasLoggerSelected", wasLoggerSelected);
		
	}
	
	
	
	private Tree<Logger> makeTree(List<Logger> list) {

		this.rootLogger = this.getLoggerByName(Logger.ROOT_LOGGER_NAME);
		final Logger dummyLogger = getLoggerByName(DUMMY_LOGGER_NAME);
		final Tree<Logger> mTree = new Tree<Logger>(rootLogger);

		for(final Logger logger : list) {			
			if (logger.equals(this.rootLogger)
					|| logger.equals(dummyLogger)) {
				continue;
			}
			final String loggerName = logger.getName();
			safelyAddLeaf(mTree, logger, loggerName);
		}

		return mTree;

	}

	private Logger getLoggerByName(final String name) {
		return (Logger) LoggerFactory.getLogger(name);
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
	private void safelyAddLeaf(Tree<Logger> mTree,
			Logger aLogger, String loggerName) {

		if(mTree.contains(aLogger)) return;

		final int lastDotIndex = loggerName.lastIndexOf('.');

		if(lastDotIndex == -1) {
			mTree.addLeaf(this.rootLogger, aLogger);
			return;
		} 
		final String parentLoggerName = loggerName.substring(0, lastDotIndex);
		final Logger parentLogger = this.getLoggerByName(parentLoggerName);
		final Logger childLogger = this.getLoggerByName(loggerName);

		safelyAddLeaf(mTree, parentLogger, parentLoggerName);
		mTree.addLeaf(parentLogger, childLogger);
		return;
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
	

	private void updateIcon(Level lvl, View row) {
		final ImageView iv = (ImageView) (row.findViewById(R.id.logger_icon));
		final String loggerName = (String) ((TextView) row
				.findViewById(R.id.logger_text)).getText();
		if(isInheritingLevel(getLoggerByName(loggerName))) {
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
			tv.setText(aLogger.getName());

			if (isInheritingLevel(aLogger)) {
				//tv.setTextColor(getResources().getColor(R.color.effective_level));
				tv.setTextAppearance(parent, R.style.unselected_logger_font);
				parent.setEffectiveIcon(aLogger.getEffectiveLevel(), levelIV);
			} else {
				//tv.setTextColor(getResources().getColor(R.color.actual_level));
				tv.setTextAppearance(parent, R.style.selected_logger_font);
				parent.setActualIcon(aLogger.getEffectiveLevel(), levelIV);
			}
			
			if(hasAttachedAppender(aLogger)) {
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

	}
	
	private void setViewColor(View row, int color) {
		row.setBackgroundColor(color);
	}
	
	private boolean isInheritingLevel(Logger lgr) {
		return lgr.getLevel() == null;
	}
	
	private boolean hasAttachedAppender(Logger logger) {
		return logger.iteratorForAppenders().hasNext();
	}
	
	public void configureAppenders(View v) {

		final Intent intent = new Intent().putExtra(
				"edu.vu.isis.ammo.core.ui.LoggerEditor.selectedLogger",
				selectedLogger).setClass(this, AppenderSelector.class);
		startActivityForResult(intent, 0);
		
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent outputIntent) {
		if(requestCode == 0) {
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
			}
			
			if (parent.selectedView == null) return;

			switch (which) {
			case TRACE_IX:
				selectedLogger.setLevel(Level.TRACE);
				updateIcon(Level.TRACE, selectedView);
				break;
			case DEBUG_IX:
				selectedLogger.setLevel(Level.DEBUG);
				updateIcon(Level.DEBUG, selectedView);
				break;
			case INFO_IX:
				selectedLogger.setLevel(Level.INFO);
				updateIcon(Level.INFO, selectedView);
				break;
			case WARN_IX:
				selectedLogger.setLevel(Level.WARN);
				updateIcon(Level.WARN, selectedView);
				break;
			case ERROR_IX:
				selectedLogger.setLevel(Level.ERROR);
				updateIcon(Level.ERROR, selectedView);
				break;
			case OFF_IX:
				selectedLogger.setLevel(Level.OFF);
				updateIcon(Level.OFF, selectedView);
				break;
			case CLEAR_IX:
			default:
				if (selectedLogger.equals(parent.rootLogger)) {
					Toast.makeText(parent, "Clearing the root logger is not allowed", Toast.LENGTH_LONG).show();
					return;
				}
				selectedLogger.setLevel(null);
				final Level effective = selectedLogger.getEffectiveLevel();
				updateIcon(effective, selectedView);
			}
			
		}
		
	}

}

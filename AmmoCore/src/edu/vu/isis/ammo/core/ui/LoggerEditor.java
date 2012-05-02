package edu.vu.isis.ammo.core.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.LoggerFactory;

import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
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
 * @author nick
 *
 */

public class LoggerEditor extends ListActivity {

	private Logger rootLogger;
	private Logger selectedLogger;
	private TextView selectionText;
	private Spinner levelSpinner;
	private MyOnItemSelectedListener spinnerListener = new MyOnItemSelectedListener();
	private ListView listView;

	private View lastSelectedView;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.logger_editor);

		// LoggerContext provides access to a List of all active loggers
		final LoggerContext lc = (LoggerContext)LoggerFactory.getILoggerFactory();
		final List<Logger> loggerList = lc.getLoggerList();
		final Tree<Logger> loggerTree = makeTree(loggerList);

		this.setListAdapter(new LoggerAdapter(loggerTree, this,
				R.layout.logger_row, R.id.logger_text));

		this.listView = super.getListView();

		this.selectionText = (TextView) findViewById(R.id.selection_text);
		this.levelSpinner = (Spinner) findViewById(R.id.level_spinner);

		final ArrayAdapter<CharSequence> spinAdapter = ArrayAdapter.createFromResource(
				this, R.array.level_options, 
				android.R.layout.simple_spinner_item);
		spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		this.levelSpinner.setAdapter(spinAdapter);
		this.spinnerListener = new MyOnItemSelectedListener();
		this.levelSpinner.setOnItemSelectedListener(this.spinnerListener);
		this.levelSpinner.setSelection(CLEAR_IX);

		// Set the selection text to indicate nothing is selected
		this.updateSelText(null);
		
		if(savedInstanceState == null) return;
		
		// Get the saved logger
		final String selectedLoggerName = (String) savedInstanceState.get("selectedLoggerName");
		if(selectedLoggerName != null) {
			this.selectedLogger = getLoggerByName(selectedLoggerName);
			this.updateSelText(selectedLoggerName);
		}
		
		// Set the list back to its previous position
		final int savedVisiblePosition = savedInstanceState.getInt("savedVisiblePosition");
		this.listView.setSelection(savedVisiblePosition);

	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		
		final String selectedLoggerName = this.selectedLogger.getName();
		outState.putString("selectedLoggerName", selectedLoggerName);
		
		final int savedVisiblePosition = this.listView.getFirstVisiblePosition();
		outState.putInt("savedVisiblePosition", savedVisiblePosition);
		
	}
	
	
	
	private Tree<Logger> makeTree(List<Logger> list) {

		this.rootLogger = this.getLoggerByName(Logger.ROOT_LOGGER_NAME);
		final Tree<Logger> mTree = new Tree<Logger>(rootLogger);

		for(final Logger logger : list) {			
			if(logger.equals(this.rootLogger)) {
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
		this.lastSelectedView = row;

		refreshList();
	}

	private void refreshList() {
		this.listView.invalidateViews();
	}
	

	private void updateIcon(Level lvl, View row) {
		final ImageView iv =(ImageView)(row.findViewById(R.id.logger_icon));
		setIcon(lvl, iv);
		refreshList();
	}

	private void setIcon(Level lvl, ImageView iv) {
		switch (lvl.levelInt) {
		case Level.TRACE_INT:
			iv.setImageResource(R.drawable.trace_level_icon);
			break;
		case Level.DEBUG_INT:
			iv.setImageResource(R.drawable.debug_level_icon);
			break;
		case Level.INFO_INT:
			iv.setImageResource(R.drawable.info_level_icon);
			break;
		case Level.WARN_INT:
			iv.setImageResource(R.drawable.warn_level_icon);
			break;
		case Level.ERROR_INT:
			iv.setImageResource(R.drawable.error_level_icon);
			break;
		case Level.OFF_INT:
		default:
			iv.setImageResource(R.drawable.off_level_icon);
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


	public class LoggerAdapter extends TreeAdapter<Logger> {
		final private LoggerEditor parent = LoggerEditor.this;

		private int tvId;

		public LoggerAdapter(Tree<Logger> objects, Context context, int resource,
				int textViewResourceId) {
			super(objects, context, resource, textViewResourceId);
			this.tvId = textViewResourceId;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup group) {
			
			final View row = super.getView(position, convertView, group);
			
			final TextView tv = (TextView)row.findViewById(this.tvId);

			final Logger aLogger = super.getItem(position);
			tv.setText(aLogger.getName());

			if (aLogger.getLevel() == null) {
				tv.setTextColor(getResources().getColor(R.color.effective_level));
			} else {
				tv.setTextColor(getResources().getColor(R.color.actual_level));
			}
			
			final ImageView iv = (ImageView)(row.findViewById(R.id.logger_icon));
			parent.setIcon(aLogger.getEffectiveLevel(), iv);
			
			final int viewColor = (aLogger.equals(parent.selectedLogger)) 
					? parent.getResources().getColor(R.color.selected_logger)
					: parent.getResources().getColor(R.color.unselected_logger);
					
			parent.setViewColor(row, viewColor);
				
			return row;
		}

	}
	
	private void setViewColor(View row, int color) {
		row.setBackgroundColor(color);
	}

	/**
	 * the spinner makes use of this listener.
	 */

	public class MyOnItemSelectedListener implements OnItemSelectedListener {

		final LoggerEditor parent = LoggerEditor.this;

		final public AtomicBoolean isUpdateAllowed = new AtomicBoolean(false);
		
		/**
		 * Sets the current text on the Spinner to match the given Level
		 * @param lvl
		 */
		public void updateSpinner(final Level lvl, final Spinner spinner) {
			this.isUpdateAllowed.set(false);
			
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
		 * When a log level is selected from the level list this method
		 * is not allowed to actually change the logger's state.
		 * When selected via the spinner the update is allowed.
		 * The logger is updated as is the icon based on the level.
		 */
		public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id) {

			if (! this.isUpdateAllowed.getAndSet(true)) {
				return;
			}

			if (parent.selectedLogger == null) {
				Toast.makeText(parent, "Please select a logger.", Toast.LENGTH_SHORT).show();
				
			}
			
			if (parent.lastSelectedView == null) return;

			switch (pos) {
			case TRACE_IX:
				selectedLogger.setLevel(Level.TRACE);
				updateIcon(Level.TRACE, lastSelectedView);
				break;
			case DEBUG_IX:
				selectedLogger.setLevel(Level.DEBUG);
				updateIcon(Level.DEBUG, lastSelectedView);
				break;
			case INFO_IX:
				selectedLogger.setLevel(Level.INFO);
				updateIcon(Level.INFO, lastSelectedView);
				break;
			case WARN_IX:
				selectedLogger.setLevel(Level.WARN);
				updateIcon(Level.WARN, lastSelectedView);
				break;
			case ERROR_IX:
				selectedLogger.setLevel(Level.ERROR);
				updateIcon(Level.ERROR, lastSelectedView);
				break;
			case OFF_IX:
				selectedLogger.setLevel(Level.OFF);
				updateIcon(Level.OFF, lastSelectedView);
				break;
			case CLEAR_IX:
			default:
				if (selectedLogger.equals(parent.rootLogger)) {
					Toast.makeText(parent, "Clearing the root logger is not allowed", Toast.LENGTH_LONG).show();
					return;
				}
				selectedLogger.setLevel(null);
				final Level effective = selectedLogger.getEffectiveLevel();
				updateIcon(effective, lastSelectedView);
			}
		}

		public void onNothingSelected(AdapterView<?> parent) {
			// Do nothing.
		}
	}

}

package edu.vu.isis.ammo.core.ui;

import java.util.ArrayList;
import java.util.HashSet;

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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.util.Tree;

/**
 * This class provides a user interface to edit the Level of all Logger objects
 * active in the application.
 * @author nick
 *
 */

public class LoggerEditor extends ListActivity {

	private Logger selectedLogger;
	private TextView selectionText;
	private Spinner levelSpinner;
	private ListView listView;
	private ArrayList<Logger> loggerList;
	private Tree<Logger> loggerTree;
	@SuppressWarnings("unused")
	private HashSet<Logger> editedLoggers;
	private View lastSelected;
	private int lastSelectedPosition;
	private boolean falseCallback;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logger_editor);
		
		// LoggerContext provides access to a List of all active loggers
		LoggerContext lc = (LoggerContext)LoggerFactory.getILoggerFactory();
		loggerList = (ArrayList<Logger>)lc.getLoggerList();
		loggerTree = makeTree(loggerList);
		this.setListAdapter(new LoggerAdapter(loggerTree, this,
				R.layout.logger_row, R.id.logger_text));
		
		editedLoggers = new HashSet<Logger>();
		
		selectionText = (TextView) findViewById(R.id.selection_text);
		levelSpinner = (Spinner) findViewById(R.id.level_spinner);
	
		ArrayAdapter<CharSequence> spinAdapter = ArrayAdapter.createFromResource(
	            this, R.array.level_options, 
	            android.R.layout.simple_spinner_item);
	    spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    levelSpinner.setAdapter(spinAdapter);
	    levelSpinner.setOnItemSelectedListener(new MyOnItemSelectedListener());
	    listView = super.getListView();

		// Set the selection text to indicate nothing is selected
		updateSelText(null);
		
	}
		
	
	private Tree<Logger> makeTree(ArrayList<Logger> list) {
		
		Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		Tree<Logger> mTree = new Tree<Logger>(rootLogger);
		
		for(int i=0; i<list.size(); i++) {
			
			Logger aLogger = list.get(i);
			
			if(aLogger.equals(rootLogger)) {
				continue;
			} else {
				String loggerName = aLogger.getName();
				safelyAddLeaf(mTree, rootLogger, aLogger, loggerName);
			}
		}
		
		return mTree;
		
	}
	
	
	
	private void safelyAddLeaf(Tree<Logger> mTree, Logger rootLogger,
			Logger aLogger, String loggerName) {
		
		if(mTree.contains((Logger)aLogger)) return;
		
		int lastDotIndex = loggerName.lastIndexOf('.');
		
		if(lastDotIndex == -1) {
			mTree.addLeaf(rootLogger, aLogger);
			return;
		} else {
			String parentLoggerName = loggerName.substring(0, lastDotIndex);
			//String childLoggerName = loggerName.substring(lastDotIndex+1);
			Logger parentLogger = (Logger) LoggerFactory.getLogger(parentLoggerName);
			Logger childLogger = (Logger) LoggerFactory.getLogger(loggerName);
			
			safelyAddLeaf(mTree, rootLogger, parentLogger, parentLoggerName);
			mTree.addLeaf(parentLogger, childLogger);
			return;
		}
	}
	
	
	@Override
	public void onListItemClick(ListView parent, View v, int position, long id) {
		
		Logger nextSelectedLogger = (Logger)parent.getItemAtPosition(position);
		Level lvl = nextSelectedLogger.getEffectiveLevel();
		updateSelText(nextSelectedLogger.getName());
		if (selectedLogger == null
				|| !nextSelectedLogger.getEffectiveLevel().equals(
						selectedLogger.getEffectiveLevel())) {
		updateSpinner(lvl);
		}
		selectedLogger = nextSelectedLogger;
		lastSelected = v;
		lastSelectedPosition = listView.getFirstVisiblePosition();
		
	}

	
	private void updateIcon(Level lvl, View v, int pos) {
		
		setIcon(lvl, v);
		super.onContentChanged();
		listView.setSelection(pos);
		
	}
	
	private void setIcon(Level lvl, View v) {
		ImageView iv =(ImageView)(v.findViewById(R.id.logger_icon));
		
		if(lvl.equals(Level.TRACE)) {
			iv.setImageResource(R.drawable.trace_level_icon);
		} else if(lvl.equals(Level.DEBUG)) {
			iv.setImageResource(R.drawable.debug_level_icon);
		} else if(lvl.equals(Level.INFO)) {
			iv.setImageResource(R.drawable.info_level_icon);
		} else if(lvl.equals(Level.WARN)) {
			iv.setImageResource(R.drawable.warn_level_icon);
		} else if(lvl.equals(Level.ERROR)) {
			iv.setImageResource(R.drawable.error_level_icon);
		} else {
			iv.setImageResource(R.drawable.off_level_icon);
		}
		
	}
	
	
	private void updateSelText(String selection) {
		if(selection == null) selectionText.setText("None selected");
		else selectionText.setText(selection);
	}
	
	
	// Sets the current text on the Spinner to match the given Level
	private void updateSpinner(Level l) {
		
		falseCallback = true;
		if(l.equals(Level.TRACE)) {
			levelSpinner.setSelection(0);
		} else if(l.equals(Level.DEBUG)) {
			levelSpinner.setSelection(1);
		} else if(l.equals(Level.INFO)) {
			levelSpinner.setSelection(2);
		} else if(l.equals(Level.WARN)) {
			levelSpinner.setSelection(3);
		} else if(l.equals(Level.ERROR)) {
			levelSpinner.setSelection(4);
		} else {
			levelSpinner.setSelection(5);
		}
		
	}
	
	
	public class LoggerAdapter extends TreeAdapter<Logger> {
		
		private int tvId;
    	
    	public LoggerAdapter(Tree<Logger> objects, Context context, int resource,
    			int textViewResourceId) {
    		super(objects, context, resource, textViewResourceId);
    		tvId = textViewResourceId;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			View row = super.getView(position, convertView, parent);
			
			TextView tv = (TextView)row.findViewById(tvId);
			
			Logger aLogger = super.getItem(position);
			tv.setText(aLogger.getName());
			
//			if(editedLoggers.contains(aLogger)) {
//				tv.setTextColor(Color.RED);
//			} else {
//				tv.setTextColor(Color.WHITE);
//			}
			
			Level lvl = aLogger.getEffectiveLevel();
			
			setIcon(lvl, row);
			
			return row;
		}
		
	}

	
	public class MyOnItemSelectedListener implements OnItemSelectedListener {

		public void onItemSelected(AdapterView<?> parent, View view, int pos,
				long id) {

			if (selectedLogger != null && lastSelected != null && !falseCallback) {

				String nextLevel = parent.getItemAtPosition(pos).toString();

				if (nextLevel.equals("Trace")) {
					selectedLogger.setLevel(Level.TRACE);
					updateIcon(Level.TRACE, lastSelected, lastSelectedPosition);
				} else if (nextLevel.equals("Debug")) {
					selectedLogger.setLevel(Level.DEBUG);
					updateIcon(Level.DEBUG, lastSelected, lastSelectedPosition);
				} else if (nextLevel.equals("Info")) {
					selectedLogger.setLevel(Level.INFO);
					updateIcon(Level.INFO, lastSelected, lastSelectedPosition);
				} else if (nextLevel.equals("Warn")) {
					selectedLogger.setLevel(Level.WARN);
					updateIcon(Level.WARN, lastSelected, lastSelectedPosition);
				} else if (nextLevel.equals("Error")) {
					selectedLogger.setLevel(Level.ERROR);
					updateIcon(Level.ERROR, lastSelected, lastSelectedPosition);
				} else if (nextLevel.equals("Off")) {
					selectedLogger.setLevel(Level.OFF);
					updateIcon(Level.OFF, lastSelected, lastSelectedPosition);
				}
				
				//editedLoggers.add(selectedLogger);

			}
			falseCallback = false;

		}

		public void onNothingSelected(AdapterView<?> parent) {
			// Do nothing.
		}
	}

}
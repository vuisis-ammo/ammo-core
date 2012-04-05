package edu.vu.isis.ammo.core.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import android.app.ListActivity;
import android.graphics.Color;
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
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.R;

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
	private ArrayList<Logger> loggerList;
	private HashSet<Logger> editedLoggers;
	private View lastSelected;
//	private String[] levelOptions;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logger_editor);
		
		// LoggerContext provides access to a List of all active loggers
		LoggerContext lc = (LoggerContext)LoggerFactory.getILoggerFactory();
		loggerList = (ArrayList<Logger>)lc.getLoggerList();
		setupList(loggerList);
		
		editedLoggers = new HashSet<Logger>();
		
		selectionText = (TextView) findViewById(R.id.selection_text);
		levelSpinner = (Spinner) findViewById(R.id.level_spinner);
//		levelOptions = (String[]) getResources()
//				.getStringArray(edu.vu.isis.ammo.core.R.array.level_options);
		
		ArrayAdapter<CharSequence> spinAdapter = ArrayAdapter.createFromResource(
	            this, R.array.level_options, 
	            android.R.layout.simple_spinner_item);
	    spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    levelSpinner.setAdapter(spinAdapter);
	    levelSpinner.setOnItemSelectedListener(new MyOnItemSelectedListener());

		// Set the selection text to indicate nothing is selected
		updateSelText(null);
		
	}
		
	
	// Sets up the adapter for the Loggers in the ListView
	private void setupList(ArrayList<Logger> loggerList) {
		LoggerAdapter adapter = new LoggerAdapter();
		setListAdapter(adapter);
	}
	
	
	@Override
	public void onListItemClick(ListView parent, View v, int position, long id) {
		
		selectedLogger = (Logger)parent.getItemAtPosition(position);
		Level lvl = selectedLogger.getEffectiveLevel();
		updateSelText(selectedLogger.getName());
		updateSpinner(lvl);
		lastSelected = v;
		
	}

	
	private void updateIcon(Level lvl, View v) {
		
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
	
	
	public class LoggerAdapter extends ArrayAdapter<Logger> {
		
		LoggerAdapter() {
			super(LoggerEditor.this, R.layout.logger_row,
					R.id.logger_text, loggerList);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			View row = super.getView(position, convertView, parent);
			
			TextView tv = (TextView)row.findViewById(R.id.logger_text);
			
			Logger aLogger = loggerList.get(position);
			tv.setText(aLogger.getName());
			
//			if(editedLoggers.contains(aLogger)) {
//				tv.setTextColor(Color.RED);
//			} else {
//				tv.setTextColor(Color.WHITE);
//			}
			
			Level lvl = aLogger.getEffectiveLevel();
			
			updateIcon(lvl, row);
			
			return row;
		}
		
	}

	
	public class MyOnItemSelectedListener implements OnItemSelectedListener {

		public void onItemSelected(AdapterView<?> parent, View view, int pos,
				long id) {

			if (selectedLogger != null && lastSelected != null) {

				String nextLevel = parent.getItemAtPosition(pos).toString();

				if (nextLevel.equals("Trace")) {
					selectedLogger.setLevel(Level.TRACE);
					updateIcon(Level.TRACE, lastSelected);
				} else if (nextLevel.equals("Debug")) {
					selectedLogger.setLevel(Level.DEBUG);
					updateIcon(Level.DEBUG, lastSelected);
				} else if (nextLevel.equals("Info")) {
					selectedLogger.setLevel(Level.INFO);
					updateIcon(Level.INFO, lastSelected);
				} else if (nextLevel.equals("Warn")) {
					selectedLogger.setLevel(Level.WARN);
					updateIcon(Level.WARN, lastSelected);
				} else if (nextLevel.equals("Error")) {
					selectedLogger.setLevel(Level.ERROR);
					updateIcon(Level.ERROR, lastSelected);
				} else if (nextLevel.equals("Off")) {
					selectedLogger.setLevel(Level.OFF);
					updateIcon(Level.OFF, lastSelected);
				}
				
				editedLoggers.add(selectedLogger);

			}

		}

		public void onNothingSelected(AdapterView parent) {
			// Do nothing.
		}
	}

}

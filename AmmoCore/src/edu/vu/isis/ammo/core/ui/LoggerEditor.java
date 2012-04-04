package edu.vu.isis.ammo.core.ui;

import java.util.ArrayList;

import android.R;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

public class LoggerEditor extends ListActivity {
	
//	private AlertDialog levelSelector;
//	private static final CharSequence[] LEVEL_NAMES = { "Trace", "Debug", "Info", "Warn",
//			"Error", "Off"
//	};
//	private static enum LevelEnum {
//		TRACE, DEBUG, INFO, WARN, ERROR, OFF
//	};
//	private DialogInterface.OnClickListener dlgListener;
	private Logger selectedLogger;
	private TextView selectionText;
	private Spinner levelSpinner;
	private String[] levelOptions;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(edu.vu.isis.ammo.core.R.layout.logger_editor);
		
		LoggerContext lc = (LoggerContext)LoggerFactory.getILoggerFactory();
		ArrayList<Logger> loggerList = (ArrayList<Logger>)lc.getLoggerList();
		
		setupList(loggerList);
		
		selectionText = (TextView) findViewById(edu.vu.isis.ammo.core.
				R.id.selection_text);
		levelSpinner = (Spinner) findViewById(edu.vu.isis.ammo.core.
				R.id.level_spinner);
		levelOptions = (String[]) getResources()
				.getStringArray(edu.vu.isis.ammo.core.R.array.level_options);
		
		ArrayAdapter<CharSequence> spinAdapter = ArrayAdapter.createFromResource(
	            this, edu.vu.isis.ammo.core.R.array.level_options, 
	            android.R.layout.simple_spinner_item);
	    spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    levelSpinner.setAdapter(spinAdapter);
	    levelSpinner.setOnItemSelectedListener(new MyOnItemSelectedListener());

		
		updateSelText(null);
		
	}
		
	
	
	private void setupList(ArrayList<Logger> loggerList) {
		ArrayAdapter<Logger> adapter = new ArrayAdapter<Logger>(this, 
				android.R.layout.simple_list_item_1,
				loggerList);
		setListAdapter(adapter);
	}
	
	
	@Override
	public void onListItemClick(ListView parent, View v, int position, long id) {
		
		selectedLogger = (Logger)parent.getItemAtPosition(position);
		updateSelText(selectedLogger.getName());
		updateSpinner(selectedLogger.getEffectiveLevel());
		
		
		
	}
	
	
//	private void createLoggerDialog(Logger logger) {
//		
//		int defaultChoice = getDefaultChoice(logger);
//		AlertDialog.Builder builder = new AlertDialog.Builder(this);
//		builder.setTitle("Select Logger level:");
//		builder.setSingleChoiceItems(LEVEL_NAMES, defaultChoice, dlgListener);
//		
//		levelSelector = builder.create();
//		
//	}
//	
//	
//	/**
//	 * Find the default choice for the AlertDialog list when a Logger
//	 * is selected.
//	 * @param logger -- the level of this Logger will be evaluated
//	 * @return  the matching dialog choice
//	 */
//	private int getDefaultChoice(Logger logger) {
//		Level lvl = logger.getLevel();
//		if(lvl.equals(Level.TRACE)) return 0;
//		else if(lvl.equals(Level.DEBUG)) return 1;
//		else if(lvl.equals(Level.INFO)) return 2;
//		else if(lvl.equals(Level.WARN)) return 3;
//		else if(lvl.equals(Level.ERROR)) return 4;
//		else if(lvl.equals(Level.OFF)) return 5;
//		else return -1;
//	}
	
//	private Level findLoggerLevel(Logger log) {
//		
//		log.get
//		
//	}
	
	private void updateSelText(String selection) {
		if(selection == null) selectionText.setText("None selected");
		else selectionText.setText(selection);
	}
	
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
	
	public class MyOnItemSelectedListener implements OnItemSelectedListener {

	    public void onItemSelected(AdapterView<?> parent,
	        View view, int pos, long id) {
		    
	    	if(selectedLogger != null) {
		    		
			    String nextLevel = parent.getItemAtPosition(pos).toString();
			      
			      if(nextLevel.equals("Trace")) {
			    	  selectedLogger.setLevel(Level.TRACE);
			      } else if(nextLevel.equals("Debug")) {
			    	  selectedLogger.setLevel(Level.DEBUG);
			      } else if(nextLevel.equals("Info")) {
			    	  selectedLogger.setLevel(Level.INFO);
			      } else if(nextLevel.equals("Warn")) {
			    	  selectedLogger.setLevel(Level.WARN);
			      } else if(nextLevel.equals("Error")) {
			    	  selectedLogger.setLevel(Level.ERROR);
			      } else if(nextLevel.equals("Off")) {
			    	  selectedLogger.setLevel(Level.OFF);
			      }
		      
	    	}
		      
	    }

	    public void onNothingSelected(AdapterView parent) {
	      // Do nothing.
	    }
	}
	

}

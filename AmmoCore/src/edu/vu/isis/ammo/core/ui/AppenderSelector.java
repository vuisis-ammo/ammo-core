package edu.vu.isis.ammo.core.ui;

import java.util.Iterator;

import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import edu.vu.isis.ammo.core.R;

/**
 * This Activity creates a CheckBox for each Appender on a dummy Logger.
 * It is used by the LoggerEditor class to configure the Appenders on a
 * specified Logger.
 * @author Nick King
 *
 */

public class AppenderSelector extends Activity {

	private Logger selectedLogger;
	private OnCheckedChangeListener myOnCheckedChangeListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.appender_selector);

		selectedLogger = (Logger) getIntent().getSerializableExtra(
				"edu.vu.isis.ammo.core.ui.LoggerEditor.selectedLogger");
		final Logger dummyLogger = (Logger) LoggerFactory
				.getLogger(LoggerEditor.DUMMY_LOGGER_NAME);
		final LinearLayout ll = (LinearLayout) findViewById(R.id.appender_layout);
		
		myOnCheckedChangeListener = new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {

				if (isChecked) {
					selectedLogger
							.addAppender((Appender<ILoggingEvent>) buttonView
									.getTag());
				} else {
					selectedLogger
							.detachAppender(((Appender<ILoggingEvent>) buttonView
									.getTag()));
				}

			}

		};

		// Loop over the active appenders and create check boxes for them
		final Iterator<Appender<ILoggingEvent>> it = dummyLogger
				.iteratorForAppenders();

		while (it.hasNext()) {

			final Appender<ILoggingEvent> nextApp = it.next();
			addCheckBoxToLayout(ll, nextApp.getName(), nextApp);

		}

	}

	private void addCheckBoxToLayout(LinearLayout ll, String text,
			Appender<ILoggingEvent> app) {

		CheckBox ckBox = new CheckBox(this);
		ckBox.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
		ckBox.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
		ckBox.setText(text);
		ckBox.setTag(app);
		ckBox.setChecked(selectedLogger.isAttached(app));

		ckBox.setOnCheckedChangeListener(myOnCheckedChangeListener);

		ll.addView(ckBox);

	}

}

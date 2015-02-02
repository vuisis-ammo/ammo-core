/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package edu.vu.isis.logger.ui;

import java.util.Map;

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
	@SuppressWarnings("unchecked")
	private final Map<String, Appender<ILoggingEvent>> availableAppenders = AppenderStore.getAppenderMap();

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.appender_selector);

		selectedLogger = (Logger) getIntent().getSerializableExtra(
				"edu.vu.isis.ammo.core.ui.LoggerEditor.selectedLogger");
		final LinearLayout ll = (LinearLayout) findViewById(R.id.appender_layout);

		myOnCheckedChangeListener = new OnCheckedChangeListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {

				final Appender<ILoggingEvent> checkApp = (Appender<ILoggingEvent>) buttonView
						.getTag();

				if (isChecked) {
					selectedLogger.addAppender(checkApp);
				} else {
					selectedLogger.detachAppender(checkApp);
				}

			}

		};


//		for (Appender<ILoggingEvent> appender : this.availableAppenders) {
//			addCheckBoxToLayout(ll, appender.getName(), appender);
//			if (Loggers.isAttachedEffective(selectedLogger, appender)) {
//				selectedLogger.addAppender(appender);
//			}
//		}

	}

	private void addCheckBoxToLayout(LinearLayout ll, String text,
			Appender<ILoggingEvent> app) {

		CheckBox ckBox = new CheckBox(this);
		ckBox.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
		ckBox.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
		ckBox.setText(text);
		ckBox.setTag(app);
		ckBox.setChecked(Loggers.isAttachedEffective(selectedLogger, app));

		ckBox.setOnCheckedChangeListener(myOnCheckedChangeListener);

		ll.addView(ckBox);

	}

}

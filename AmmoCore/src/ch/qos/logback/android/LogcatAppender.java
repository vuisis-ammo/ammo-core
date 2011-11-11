/**
 * The appender wraps the native android logging mechanism.
 * 
 */
package ch.qos.logback.android;

import java.io.IOException;

import android.util.Log;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;


public class LogcatAppender extends AppenderBase<ILoggingEvent> {

	private PatternLayoutEncoder encoder;

	public LogcatAppender() {
	}

	@Override
	public void start() {
		if (this.encoder == null) {
			addError("No layout set for the appender named ["+ name +"].");
			return;
		}

		try {
			this.encoder.init(System.out);
		} catch (IOException e) {
		}
		super.start();
	}

	public void append(ILoggingEvent event) {
		// output the events as formatted by our layout
		final String tag = event.getLoggerName();
		switch (event.getLevel().levelInt) {
		case Level.TRACE_INT: 
			if (! Log.isLoggable(tag, Log.VERBOSE) ) return;
			Log.v(tag, event.getFormattedMessage());
			return;
		case Level.DEBUG_INT:
			if (! Log.isLoggable(tag, Log.DEBUG) ) return;
			Log.d(tag, event.getFormattedMessage());
			return;
		case Level.INFO_INT:
			if (! Log.isLoggable(tag, Log.INFO) ) return;
			Log.i(tag, event.getFormattedMessage());
			return;
		case Level.WARN_INT:
			if (! Log.isLoggable(tag, Log.WARN) ) return;
			Log.w(tag, event.getFormattedMessage());
			return;
		case Level.ERROR_INT:
			if (! Log.isLoggable(tag, Log.ERROR) ) return;
			Log.d(tag, event.getFormattedMessage());
			return;
		case Level.OFF_INT:
		default:
			return;
		}
	}

	public PatternLayoutEncoder getEncoder() {
		return this.encoder;
	}

	public void setEncoder(PatternLayoutEncoder encoder) {
		this.encoder = encoder;
	}

}
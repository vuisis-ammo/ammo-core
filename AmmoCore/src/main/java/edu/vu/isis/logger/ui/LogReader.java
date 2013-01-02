
package edu.vu.isis.logger.ui;

import java.util.concurrent.atomic.AtomicBoolean;

import net.jcip.annotations.ThreadSafe;
import android.content.Context;
import android.os.Handler;

@ThreadSafe
public abstract class LogReader {

    /** the handler to which Messages are sent */
    protected Handler mHandler;

    /** the Context which is using this LogReader */
    protected Context mContext;

    /** Whether this LogReader has been paused or resumed */
    protected AtomicBoolean isPaused = new AtomicBoolean(true);

    /**
     * Tells this LogReader to start itself
     */
    public abstract void start();

    /**
     * Tells this LogReader to halt all reading and close its streams. This
     * LogReader may not be used after this method has been called.
     */
    public abstract void terminate();

    /**
     * Tells this LogReader to temporarily pause reading
     */
    public synchronized void pause() {
        this.isPaused.set(true);
    }

    /**
     * Tells this LogReader to resume reading
     */
    public synchronized void resume() {
        this.isPaused.set(false);
    }

    /**
     * Attaches a Handler to this LogReader.
     * 
     * @param handler -- the Handler to which Messages will be sent
     */
    public synchronized void setHandler(Handler handler) {
        this.mHandler = handler;
    }

    /**
     * Parses a String to get LogLevel that corresponds to that String. The
     * default behavior of this method is that the first char in the String will
     * be used to determine the LogLevel.
     * <p>
     * The characters and their corresponding levels are: <list>
     * <li>V: Verbose
     * <li>T: Trace
     * <li>D: Debug
     * <li>I: Info
     * <li>W: Warn
     * <li>E: Error
     * <li>F: Fail
     * <li>All others: None </list>
     * <p>
     * The characters are case sensitive.
     * 
     * @param str -- the String to parse
     * @return the corresponding LogLevel
     */
    public static LogLevel getCorrespondingLevel(String str) {
        if (str == null)
            return LogLevel.None;
        if (str.length() == 0)
            return LogLevel.None;

        final char firstChar = str.charAt(0);
        switch (firstChar) {
            case 'V':
                return LogLevel.Verbose;
            case 'T':
                return LogLevel.Trace;
            case 'D':
                return LogLevel.Debug;
            case 'I':
                return LogLevel.Info;
            case 'W':
                return LogLevel.Warn;
            case 'E':
                return LogLevel.Error;
            case 'F':
                return LogLevel.Fail;
            default:
                return LogLevel.None;
        }

    }

}

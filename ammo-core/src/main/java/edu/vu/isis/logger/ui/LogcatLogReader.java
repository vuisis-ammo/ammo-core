
package edu.vu.isis.logger.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

public class LogcatLogReader extends LogReader {

    private static final int BUFFER_SIZE = 1024;
    private static final long SEND_DELAY = 10;

    private BufferedReader mReader;
    private final ArrayList<LogElement> mLogCache = new ArrayList<LogElement>();
    private Pattern mPattern;
    private boolean mShowTimestamps = false;
    private Logger logger = LoggerFactory.getLogger("ui.logger.logcatreader");

    private final ScheduledExecutorService scheduler = Executors
            .newSingleThreadScheduledExecutor();

    public LogcatLogReader(Context context, Handler handler) {

        this.mContext = context;
        this.mHandler = handler;
        setRegex("");

    }
    
    public LogcatLogReader(Context context, Handler handler, String regex) {

        this.mContext = context;
        this.mHandler = handler;
        setRegex(regex);

    }
    
    public LogcatLogReader(Context context, Handler handler, String regex, boolean showTimestamps) {

        this.mContext = context;
        this.mHandler = handler;
        setRegex(regex);
        mShowTimestamps = showTimestamps;

    }

    /**
     * Sends the current log cache and clears it if sending has not been paused
     * and the log cache is not empty
     */
    private void sendCacheAndClear() {
        // checkValidState();
        // both methods check the state
        if (!this.mLogCache.isEmpty()) {
            sendCacheMsg();
            clearCache();
        }
    }

    /**
     * Sends the log cache to the attached Handler
     */
    private void sendCacheMsg() {

        if (!this.isPaused.get()) {
            final Message msg = Message.obtain();
            msg.what = LogcatLogViewer.CONCAT_DATA_MSG;

            synchronized (this.mLogCache) {
                msg.obj = this.mLogCache.clone();
            }

            msg.setTarget(mHandler);
            msg.sendToTarget();
        }

    }

    @Override
    public synchronized void start() {

        String command = "logcat";
        if (mShowTimestamps) {
            command += " -v time";
        }
        
        try {
            Process logcatProcess = Runtime.getRuntime().exec(command);
            this.mReader = new BufferedReader(new InputStreamReader(
                    logcatProcess.getInputStream()), BUFFER_SIZE);
            this.myReadThread = new ReadThread();
            this.myReadThread.start();
            this.scheduler.scheduleWithFixedDelay(this.updateRunnable, SEND_DELAY,
                    SEND_DELAY, TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            logger.error("Could not read from Logcat process!");
            return;
        }
        
        resume();


    }

    @Override
    public synchronized void terminate() {
        
        // These could be null if we got an exception when starting
        // the Logcat process
        if(myReadThread != null && mReader != null) {
            this.myReadThread.cancel();
            try {
                this.mReader.close();
            } catch (IOException e) {
                logger.warn("Couldn't close BufferedReader");
            }
        }
        
        this.scheduler.shutdown();
        this.mLogCache.clear();
    }

    // TODO: Ensure that synchronization is actually necessary for pause()
    // and resume(). Current thoughts: what happens if pause() and resume()
    // are called at nearly the same time by different threads? Could the
    // object end up in a state in which it is paused but still reading or
    // not reading but unpaused?

    @Override
    public synchronized void pause() {
        super.pause();
        stopReading();
    }

    @Override
    public synchronized void resume() {
        super.resume();
        resumeReading();
    }

    /**
     * Pauses reading from LogCat, but has no effect on whether cache messages
     * are sent.
     */
    private void stopReading() {
        this.isReading.set(false);
    }

    /**
     * Resumes reading from LogCat, but has no effect on whether cache messages
     * are sent.
     */
    private void resumeReading() {
        this.isReading.set(true);
    }

    /**
     * Clears the log cache
     */
    private void clearCache() {
        synchronized (mLogCache) {
            mLogCache.clear();
        }
    }

    public void setRegex(String newRegex) {
        try {
            mPattern = Pattern.compile(newRegex);
        } catch (PatternSyntaxException pe) {
            mPattern = Pattern.compile("");
            mHandler.sendEmptyMessage(LogcatLogViewer.NOTIFY_INVALID_REGEX_MSG);
        }
    }

    private final AtomicBoolean isReading = new AtomicBoolean(false);

    private ReadThread myReadThread;

    private class ReadThread extends Thread {

        @Override
        public void run() {

            while (!Thread.currentThread().isInterrupted()) {
                if (isReading.get()) {
                    try {
                        String nextLine = mReader.readLine();
                        Matcher matcher = mPattern.matcher(nextLine);
                        if (!matcher.find())
                            continue;
                        final LogLevel level;
                        if (mShowTimestamps) {
                            // XXX: This is a bad way to find where the timestamps end
                            int spaceIx = -1;
                            for (int i = 0; i < 2; i++) {
                                spaceIx = nextLine.indexOf(' ', spaceIx+1);
                            }
                            level = getCorrespondingLevel(nextLine.substring(spaceIx+1));
                        } else {
                            level = getCorrespondingLevel(nextLine);
                        }
                        synchronized (mLogCache) {
                            mLogCache.add(new LogElement(level, nextLine));
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        public void cancel() {
            Thread.currentThread().interrupt();
        }

    };

    private Runnable updateRunnable = new Runnable() {

        private LogcatLogReader parent = LogcatLogReader.this;

        @Override
        public void run() {

            if (!parent.isPaused.get()) {
                parent.sendCacheAndClear();
            }

        }

    };

}

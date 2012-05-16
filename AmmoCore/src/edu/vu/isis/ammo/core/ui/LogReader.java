package edu.vu.isis.ammo.core.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

public abstract class LogReader {
	
	/** the log cache */
	protected ArrayList<LogElement> mLogCache = new ArrayList<LogElement>();
	
	/** BufferedReader to use for reading input streams */
	protected BufferedReader mReader;
	
	/** the handler to which Messages are sent */
	protected Handler mHandler;
	
	/** the Context which is using this LogReader */
	protected Context mContext;
	
	/** whether or not sending new cache updates is paused */
	protected AtomicBoolean isSendingPaused = new AtomicBoolean(false);
	
	/**
	 * Responsible for reading in new data to the cache. By default, simply
	 * reads lines from the BufferedReader until no lines are left.
	 * 
	 * @return whether new data was actually read
	 */
	protected boolean bufferNewData() {
		
		try {
			
			String nextLine = this.mReader.readLine();
			if(nextLine == null) return false;
			
				while (nextLine != null) {
					
					// Try to send an update every 100 entries
					// Probably inefficient, but good to let user know
					// that data is being loaded
					if(mLogCache.size() % 101 == 100) {
						sendCacheAndClear();
					}
					
					final LogLevel level = getCorrespondingLevel(nextLine);
					this.mLogCache.add(new LogElement(level, nextLine));
					
					nextLine = this.mReader.readLine();
					
				}
				
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return true;
		
	}
	
	
	/**
	 * Clears the log cache
	 */
	protected void clearCache() {
		synchronized(mLogCache) {
			mLogCache.clear();
		}
	}
	
	
	/**
	 * Tells this LogReader to start its initial read.
	 */
	public abstract void start();
	
	
	/**
	 * Tells this LogReader to begin periodically reporting its cache again.
	 * Its initial report will include all log messages cached since the
	 * last pause.
	 */
	public void resumeUpdating() {
		this.isSendingPaused.set(false);
	}
	
	
	/**
	 * Tells this LogReader to stop periodically reporting its cache.  
	 * However, it will continue to cache new log messages as they become
	 * available.
	 */
	public void stopUpdating() {
		this.isSendingPaused.set(true);
	}
	
	
	/**
	 * Attaches a Handler to this LogReader.
	 * @param handler -- the Handler to which Messages will be sent
	 */
	public void setHandler(Handler handler) {
		this.mHandler = handler;
	}
	
	
	/**
	 * Forces this LogReader to send its cache regardless of its state
	 */
	public synchronized void forceUpdate() {
		sendCacheAndClear();
	}
	
	
	/**
	 * Sends the current log cache and clears it if sending has not been
	 * paused and the log cache is not empty
	 */
	protected void sendCacheAndClear() {
		if(!this.isSendingPaused.get() && !this.mLogCache.isEmpty()) {
			sendCacheMsg();
			clearCache();
		}
	}
	
	
	/**
	 * Sends the log cache to the attached Handler
	 */
	protected void sendCacheMsg() {
		
		if (!this.isSendingPaused.get()) {
			final Message msg = Message.obtain();
			msg.what = LogViewer.NEW_DATA_MSG;
			
			synchronized(this.mLogCache) {
				msg.obj = this.mLogCache.clone();
			}

			msg.setTarget(mHandler);
			msg.sendToTarget();
		}
		
	}
	
	
	/**
	 * Sends a message to the attached Handler, telling the Handler to
	 * display a progress dialog that will let the user know that data
	 * is currently being loaded.
	 */
	protected void sendStartProgressDlgMsg() {
		
		final Message msg = Message.obtain();
		msg.what = LogViewer.START_PROG_DIALOG;
		
		msg.setTarget(mHandler);
		msg.sendToTarget();
		
	}
	
	
	/**
	 * Sends a message to the attached Handler, telling the Handler to
	 * display a progress dialog that will let the user know that data
	 * is currently being loaded.
	 */
	protected void sendDismissProgressDlgMsg() {
		
		final Message msg = Message.obtain();
		msg.what = LogViewer.DISMISS_PROG_DIALOG;
		
		msg.setTarget(mHandler);
		msg.sendToTarget();
		
	}
	
	
	/**
	 * Parses a String to get LogLevel that corresponds to that String.
	 * The default behavior of this method is that the first char
	 * in the String will be used to determine the LogLevel.
	 * <p>
	 * The characters and their corresponding levels are:
	 * <list>
	 * <li> V: Verbose
	 * <li> T: Trace
	 * <li> D: Debug
	 * <li> I: Info
	 * <li> W: Warn
	 * <li> E: Error
	 * <li> F: Fail
	 * <li> All others: None
	 * </list>
	 * <p>
	 * The characters are case sensitive.
	 * @param str -- the String to parse
	 * @return the corresponding LogLevel
	 */
	public static LogLevel getCorrespondingLevel(String str) {
		
		if(str.length() == 0) return LogLevel.None;
		
		final char firstChar = str.charAt(0);
		switch(firstChar) {
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

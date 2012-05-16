package edu.vu.isis.ammo.core.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.os.Handler;

public class LogcatLogReader extends LogReader {

	private static final int BUFFER_SIZE = 1024;
	
	private Process mLogcatProcess;
	
	public LogcatLogReader(Context context, Handler handler) throws IOException {

		this.mContext = context;
		this.mHandler = handler;
		this.mLogcatProcess = Runtime.getRuntime().exec("logcat");
		this.mReader = new BufferedReader(new InputStreamReader(
				this.mLogcatProcess.getInputStream()), BUFFER_SIZE);
		
	}
	

	@Override
	public void start() {
		resumeReading();
		this.readThread.start();
		this.updateThread.start();
	}
	
	/**
	 * Pauses reading from LogCat, but has no effect on whether cache
	 * messages are sent.
	 */
	public void stopReading() {
		this.isReading.set(false);
	}
	
	/**
	 * Resumes reading from LogCat, but has no effect on whether cache
	 * messages are sent.
	 */
	public void resumeReading() {
		this.isReading.set(true);
	}
	
	@Override
	public void resumeUpdating() {
		this.isUpdating.set(true);
	}
	
	@Override
	public void stopUpdating() {
		this.isUpdating.set(false);
	}
	
	private AtomicBoolean isReading = new AtomicBoolean(false);
	private AtomicBoolean isUpdating = new AtomicBoolean(false);
	
	private Thread readThread = new Thread() {
		
		private LogcatLogReader parent = LogcatLogReader.this;
		
		@Override
		public void run() {
			
			try {
				
				String nextLine = parent.mReader.readLine();
				
					while (nextLine != null) {
						
						final LogLevel level = getCorrespondingLevel(nextLine);
						
						synchronized(parent.mLogCache) {
							parent.mLogCache.add(new LogElement(level, nextLine));
						}
						
						nextLine = parent.mReader.readLine();
						
					}
					
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
	};
	
	private Thread updateThread = new Thread() {

		private static final long SLEEP_MS = 10;
		private LogcatLogReader parent = LogcatLogReader.this;
		
		@Override
		public void run() {
			
			while(true) {
				if(parent.isUpdating.get()) {
					parent.sendCacheAndClear();
				}
				try {
					sleep(SLEEP_MS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		}
		
	};

}

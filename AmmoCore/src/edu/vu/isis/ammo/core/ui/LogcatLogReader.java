package edu.vu.isis.ammo.core.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.os.Handler;

public class LogcatLogReader extends LogReader {

	private static final int BUFFER_SIZE = 1024;
	private static final long SEND_DELAY = 10;
	
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	
	public LogcatLogReader(Context context, Handler handler) throws IOException {

		this.mContext = context;
		this.mHandler = handler;
		final Process logcatProcess = Runtime.getRuntime().exec("logcat");
		this.mReader = new BufferedReader(new InputStreamReader(
				logcatProcess.getInputStream()), BUFFER_SIZE);
		
	}
	

	@Override
	public void start() {
		resumeReading();
		resumeUpdating();
		this.myReadThread = new ReadThread();
		this.myReadThread.start();
		this.scheduler.scheduleWithFixedDelay(this.updateRunnable, SEND_DELAY, SEND_DELAY, TimeUnit.MILLISECONDS);
	}
	
	
	@Override
	public void terminate() {
		this.myReadThread.cancel();
		this.myReadThread = null;
		this.scheduler.shutdown();
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
	
	private final AtomicBoolean isReading = new AtomicBoolean(false);
	private final AtomicBoolean isUpdating = new AtomicBoolean(false);
    private ReadThread myReadThread;
	
	private class ReadThread extends Thread {

		private LogcatLogReader parent = LogcatLogReader.this;

		@Override
		public void run() {

			while (!Thread.currentThread().isInterrupted()) {
				if (parent.isReading.get()) {
					try {

						String nextLine = parent.mReader.readLine();
						final LogLevel level = getCorrespondingLevel(nextLine);
						synchronized (parent.mLogCache) {
							parent.mLogCache
									.add(new LogElement(level, nextLine));
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
			
				if(parent.isUpdating.get()) {
					parent.sendCacheAndClear();
				}
			
		}
		
	};

}

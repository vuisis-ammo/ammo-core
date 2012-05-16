package edu.vu.isis.ammo.core.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

import android.content.Context;
import android.os.FileObserver;
import android.os.Handler;

public class FileLogReader extends LogReader {
	
	private static final int BUFFER_SIZE = 1024;
	
	private final MyFileObserver observer;
	
	public FileLogReader(Context context, Handler handler, String filepath)
			throws FileNotFoundException {

		final File inFile = new File(filepath);
		this.mReader = new BufferedReader(new InputStreamReader(
				new FileInputStream(inFile)), BUFFER_SIZE);
		this.mContext = context;
		this.mHandler = handler;
		this.observer = new MyFileObserver(filepath, FileObserver.MODIFY);

	}

	@Override
	public void start() {
		initThread.run();
	}

	private Thread initThread = new Thread() {
		
		private FileLogReader parent = FileLogReader.this;
		
		@Override
		public void run() {
			parent.sendStartProgressDlgMsg();
			parent.bufferNewData();
			parent.sendDismissProgressDlgMsg();
			parent.sendCacheAndClear();
			parent.observer.startWatching();
		}
		
	};
	
	
	/**
	 * Private inner class to notify us of file events
	 */
	private class MyFileObserver extends FileObserver {

		public MyFileObserver(String path) {
			super(path);
		}

		public MyFileObserver(String path, int mask) {
			super(path, mask);
		}

		@Override
		public void onEvent(int event, String path) {
			bufferNewData();
			sendCacheAndClear();
		}

	}

}

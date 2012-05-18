package edu.vu.isis.ammo.core.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

import android.content.Context;
import android.os.AsyncTask;
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
		new ReadFileTask().execute();
	}
	
	@Override
	public void terminate() {
		// AsyncTask handles this for us
	}

	class ReadFileTask extends AsyncTask<Void, Void, Void> {

		private FileLogReader parent = FileLogReader.this;
		
		@Override
		protected Void doInBackground(Void... unused) {
			
			parent.bufferNewData();
			parent.sendCacheAndClear();
			parent.observer.startWatching();
			
			return null;
		}
		
	}
	
	
	/**
	 * Private inner class to notify us of file events
	 */
	private class MyFileObserver extends FileObserver {
		
		private FileLogReader parent = FileLogReader.this;
		
		public MyFileObserver(String path) {
			super(path);
		}

		public MyFileObserver(String path, int mask) {
			super(path, mask);
		}

		@Override
		public void onEvent(int event, String path) {
			parent.bufferNewData();
			parent.sendCacheAndClear();
		}

	}

}

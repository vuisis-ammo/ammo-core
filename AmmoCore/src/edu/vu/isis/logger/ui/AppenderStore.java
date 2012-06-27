package edu.vu.isis.logger.ui;

import java.util.Map;

public class AppenderStore {

	private final Map appenderBag;
	private static AppenderStore instance = null;

	private AppenderStore() { 
		appenderBag = null; 
		throw new AssertionError("This constructor should never be called");
	}
	
	private AppenderStore(Map map) {
		appenderBag = map;
	}
	
	/**
	 * Stores a reference to the map of appenders created by Joran.  This method
	 * may only be called once per classloader.
	 * @param map The map of appenders
	 * @throws IllegalStateException if this method has already been called.
	 */
	public static synchronized void storeReference(Map map) {
		if (instance != null) {
			throw new IllegalStateException(
					"A reference has already been stored.");
		}
		instance = new AppenderStore(map);
	}

	/**
	 * Gets the map of appenders created by Joran.
	 * @return the map of appenders
	 * @throws IllegalStateException if no reference to the map has yet been stored
	 */
	public static synchronized Map getAppenderMap() {
		if (instance == null) {
			throw new IllegalStateException(
					"No reference has yet been stored.");
		}
		return instance.getAppenderBag();
	}

	private Map getAppenderBag() {
		return appenderBag;
	}

}

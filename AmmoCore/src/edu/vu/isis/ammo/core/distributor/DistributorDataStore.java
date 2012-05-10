/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
 */
package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.store.Capability;
import edu.vu.isis.ammo.core.distributor.store.Channel;
import edu.vu.isis.ammo.core.distributor.store.Postal;
import edu.vu.isis.ammo.core.distributor.store.Presence;
import edu.vu.isis.ammo.core.distributor.store.Retrieval;
import edu.vu.isis.ammo.core.distributor.store.Subscribe;
import edu.vu.isis.ammo.core.distributor.store.Tables;

/**
 * The Distributor Store Object is managed by the distributor thread.
 *
 * The distributor thread manages two queues.
 * <ul>
 * <li>coming from the AIDL calls from clients</li>
 * <li>coming from the gateway</li>
 * </ul>
 * 
 * The bulk of the implementation for this class in in 
 * the ...distributor.store package
 *
 */
public class DistributorDataStore {
	// ===========================================================
	// Constants
	// ===========================================================
	private final static Logger logger = LoggerFactory.getLogger("class.store");
	public static final int VERSION = 41;

	/**
	 * To create a file backed data store set the name to...
	 * distributor.db
	 * to create in memory backed store set the name to...
	 * null
	 */
	public static final String NAME = /* null */ "distributor.db" ; 
	static final public String ROWID_CLAUSE = "_rowid_=?";


	// ===========================================================
	// Fields
	// ===========================================================
	private final Context context;
	public SQLiteDatabase db;
	public final DataStoreHelper helper;
	

	// ===========================================================
	// Methods
	// ===========================================================

	public DistributorDataStore(Context context) {
		this.context = context;
		this.helper = new DataStoreHelper(
				this.context, 
				DistributorDataStore.NAME, 
				null, 
				DistributorDataStore.VERSION); 

		// ========= INITIALIZE CONSTANTS ========
		this.applDir = context.getDir("support", Context.MODE_PRIVATE);

		if (! this.applDir.mkdirs()) {
			logger.error("cannot create files check permissions in manifest : {}",
					this.applDir.toString());
		}

		this.applCacheDir = new File(this.applDir, "cache");
		this.applCacheDir.mkdir();

		this.applCachePostalDir = new File(this.applCacheDir, "postal");
		this.applCachePostalDir.mkdir();

		this.applCacheRetrievalDir = new File(this.applCacheDir, "retrieval");
		this.applCacheRetrievalDir.mkdir();

		this.applCacheSubscribeDir = new File(this.applCacheDir, "subscribe");
		this.applCacheSubscribeDir.mkdir();

		this.applTempDir = new File(this.applDir, "tmp");
		this.applTempDir.mkdir();

		this.openWrite();
	}


	/**
	 * It is possible for the distributor database to become corrupted.
	 * While this is not a common behavior it does happen from time to time.
	 * It is believed that this is caused by abrupt shutdown of the service.
	 * 
	 */
	public synchronized DistributorDataStore openRead() {
		if (this.db != null && this.db.isReadOnly()) return this;

		this.db = this.helper.getReadableDatabase();
		return this;
	}

	public synchronized DistributorDataStore openWrite() {
		if (this.db != null && this.db.isOpen() && ! this.db.isReadOnly()) this.db.close();

		this.db = this.helper.getWritableDatabase();
		return this;
	}

	public synchronized void close() {
		this.db.close();
	}


	// ======== HELPER ============
	public static String[] getRelativeExpirationTime(long delay) {
		final long absTime = System.currentTimeMillis() - (delay * 1000);
		return new String[]{String.valueOf(absTime)}; 
	}

	public static class SelectArgsBuilder {
		final private List<String> args;
		public SelectArgsBuilder() {
			this.args = new ArrayList<String>();
		}
		public SelectArgsBuilder append(String arg) { 
			this.args.add(arg);
			return this;
		}
		public String[] toArgs() {
			return this.args.toArray(new String[this.args.size()]);
		}
	}


	public final File applDir;
	public final File applCacheDir;
	public final File applCachePostalDir;
	public final File applCacheRetrievalDir;
	public final File applCacheSubscribeDir;
	public final File applTempDir;


	protected File blobFile(String table, String tuple, String field)
			throws IOException {
		File tupleCacheDir = blobDir(table, tuple);
		File cacheFile = new File(tupleCacheDir, field + ".blob");
		if (cacheFile.exists())
			return cacheFile;

		cacheFile.createNewFile();
		return cacheFile;
	}

	protected File blobDir(String table, String tuple) throws IOException {
		File tableCacheDir = new File(applCacheDir, table);
		File tupleCacheDir = new File(tableCacheDir, tuple);
		if (!tupleCacheDir.exists())
			tupleCacheDir.mkdirs();
		return tupleCacheDir;
	}

	protected File tempFilePath(String table) throws IOException {
		return File.createTempFile(table, ".tmp", applTempDir);
	}

	protected void clearBlobCache(String table, String tuple) {
		if (table == null) {
			if (applCacheDir.isDirectory()) {
				for (File child : applCacheDir.listFiles()) {
					recursiveDelete(child);
				}
				return;
			}
		}
		File tableCacheDir = new File(applCacheDir, table);
		if (tuple == null) {
			if (tableCacheDir.isDirectory()) {
				for (File child : tableCacheDir.listFiles()) {
					recursiveDelete(child);
				}
				return;
			}
		}
		File tupleCacheDir = new File(tableCacheDir, tuple);
		if (tupleCacheDir.isDirectory()) {
			for (File child : tupleCacheDir.listFiles()) {
				recursiveDelete(child);
			}
		}
	}

	/**
	 * Recursively delete all children of this directory and the directory
	 * itself.
	 *
	 * @param dir
	 */
	protected void recursiveDelete(File dir) {
		if (!dir.exists())
			return;

		if (dir.isFile()) {
			dir.delete();
			return;
		}
		if (dir.isDirectory()) {
			for (File child : dir.listFiles()) {
				recursiveDelete(child);
			}
			dir.delete();
			return;
		}
	}



	public class DataStoreHelper extends SQLiteOpenHelper {
		// ===========================================================
		// Constants
		// ===========================================================
		private final Logger logger = LoggerFactory.getLogger("class.DataStoreHelper");

		// ===========================================================
		// Fields
		// ===========================================================

		// ===========================================================
		// Constructors
		// ===========================================================
		public DataStoreHelper(Context context, 
				String name, CursorFactory factory, int version) 
		{
			super(context, name, factory, version);
		}

		// ===========================================================
		// SQLiteOpenHelper Methods
		// ===========================================================

		@Override
		public synchronized void onCreate(SQLiteDatabase db) {
			logger.trace("bootstrapping database");
			PLogger.STORE_DDL.debug("creating data store {}", db.getPath());

			db.beginTransaction();
			try {
				Channel.onCreate(db);
				Presence.onCreate(db);
				Capability.onCreate(db);
				Postal.onCreate(db);
				Retrieval.onCreate(db);
				Subscribe.onCreate(db);
				
				db.setTransactionSuccessful();

			} finally {
				db.endTransaction();
			}
			PLogger.STORE_DDL.trace("database definition complete");
		}

		@Override
		public synchronized void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			logger.warn("Upgrading database from version {} to {} which will destroy all old data",
					oldVersion, newVersion);
			this.dropAll(db);
			this.onCreate(db);
		}

		/**
		 * Called when the database has been opened. 
		 * The implementation checks isReadOnly() before updating the database.
		 */
		@Override
		public void onOpen (SQLiteDatabase db) {
			if (!db.isReadOnly()) {
				// Enable foreign key constraints
				db.execSQL("PRAGMA foreign_keys=ON;");
			}
		}

		@Override
		public synchronized SQLiteDatabase getWritableDatabase() {
			try {
				return super.getWritableDatabase();

			} catch (SQLiteDiskIOException ex) {
				logger.error("corrupted database {}", ex.getLocalizedMessage());
			}
			try {
				this.archive();
				return super.getReadableDatabase();
			} catch (SQLiteException ex) {
				logger.error("unrecoverablly corrupted database {}", ex.getLocalizedMessage());
			}
			return null;
		}

		@Override
		public synchronized SQLiteDatabase getReadableDatabase() {
			try {
				return super.getReadableDatabase();
			} catch (SQLiteDiskIOException ex) {
				logger.error("corrupted database {}", ex.getLocalizedMessage());		
			}
			try {
				this.archive();
				return super.getReadableDatabase();
			} catch (SQLiteException ex) {
				logger.error("unrecoverablly corrupted database {}", ex.getLocalizedMessage());
			}
			return null;
		}

		/**
		 * drop all tables.
		 * 
		 * @param db
		 */
		public synchronized void dropAll(SQLiteDatabase db) {
			PLogger.STORE_DDL.trace("dropping all tables");
			db.beginTransaction();
			try {
				for (Tables table : Tables.values()) {
					try {
						db.execSQL( new StringBuilder()
						.append("DROP TABLE ")
						.append(table.q())
						.append(";")
						.toString() );

					} catch (SQLiteException ex) {
						logger.warn("defective table {} being dropped {}", 
								table, ex.getLocalizedMessage());
					}
				}
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
		}

		public synchronized boolean archive() {
			logger.info("archival corrupt database");
			if (db == null) {
				logger.warn("missing database");
				return false;
			} 
			db.close();
			db.releaseReference();

			final File backup = context.getDatabasePath("corrupted.db");  
			// new File(Environment.getExternalStorageDirectory(), Tables.NAME);
			if (backup.exists()) backup.delete();

			final File original = context.getDatabasePath(DistributorDataStore.NAME);
			logger.info("backup of database {} -> {}", original, backup);
			if (original.renameTo(backup)) {
				logger.info("archival succeeded");
				return true;
			}
			if (! context.deleteDatabase(DistributorDataStore.NAME)) {
				logger.warn("file should have been renamed, deleted instead"); 
			}
			return false;
		}
	}


}

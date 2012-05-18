package edu.vu.isis.ammo.core.distributor.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.text.TextUtils;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalState;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableField;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableFieldState;

public class Channel {
	private static final Logger logger = LoggerFactory.getLogger("class.store.channel");

	public enum ChannelState {
		ACTIVE(1), INACTIVE(2);

		public int o; // ordinal

		private ChannelState(int o) {
			this.o = o;
		}
		public int cv() {
			return this.o;
		}
		static public ChannelState getInstance(int ordinal) {
			return ChannelState.values()[ordinal];
		}
		public String q() {
			return new StringBuilder().append("'").append(this.o).append("'").toString();
		}
	}
	

	/**
	 * The states of a request over a particular channel.
	 * The DISTRIBUTE RequestDisposal indicates that the
	 * total state is an aggregate of the distribution
	 * of the request across the relevant channels.
	 */
	public enum ChannelStatus {
		READY    (0x0001, "ready"),  // channel is ready to receive requests
		EMPTY    (0x0002, "empty"),  // channel queue is empty
		DOWN     (0x0004, "down"),   // channel is temporarily down
		FULL     (0x0008, "busy"),   // channel queue is full 
		;

		final public int o;
		final public String t;

		private ChannelStatus(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}
		public String q() {
			return new StringBuilder().append("'").append(this.o).append("'").toString();
		}

		public DisposalState inferDisposal() {
			switch (this) {
			case DOWN: return DisposalState.REJECTED;
			case FULL: return DisposalState.BUSY;
			default:
				logger.warn("don't call with {}", this);
				throw new IllegalArgumentException();
			}
		}

	};


	static public void onCreate(SQLiteDatabase db) {
		String sqlCreateRef = null; 

		/**
		 *  ===== CHANNEL
		 */
		try {
			final Tables table = Tables.CHANNEL;

			final StringBuilder createSql = new StringBuilder()
			.append(" CREATE TABLE ")
			.append(table.q())
			.append(" ( ").append(TableHelper.ddl(ChannelField.values())).append(')')
			.append(';');

			sqlCreateRef = createSql.toString();
			PLogger.STORE_DDL.trace("{}", sqlCreateRef);
			db.execSQL(sqlCreateRef);

		} catch (SQLException ex) {
			logger.error("create CHANNEL {} {}",
					sqlCreateRef, ex.getLocalizedMessage());
			return;
		}
	}


	/**
	 * ===================
	 *  CHANNEL
	 * ===================
	 * The channel table is for holding current channel status.
	 * This could be done with a concurrent hash map but that
	 * would put more logic in the java code and less in sqlite.
	 */

	public enum ChannelField  implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		NAME("name", "TEXT UNIQUE"),
		// The name of the channel, must match policy channel name

		STATE("state", "INTEGER");
		// The channel state (active inactive)

		final public TableFieldState impl;

		private ChannelField(String name, String type) {
			this.impl = TableFieldState.getInstance(name,type);
		}

		/**
		 * required by TableField interface
		 */
		public String q(String tableRef) { return this.impl.quoted(tableRef); }
		public String cv() { return this.impl.cvQuoted(); }
		public String n() { return this.impl.n; }
		public String t() { return this.impl.t; }
		public String ddl() { return this.impl.ddl(); }
	}



	public static interface ChannelConstants extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public static final String FOREIGN_KEY = null;

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (ChannelField field : ChannelField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (ChannelField field : ChannelField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	};


	public static ChannelWorker getChannelWorker(final DistributorDataStore store) {
		return new ChannelWorker(store);
	}
	/** 
	 * Store access class
	 */
	public static class ChannelWorker {
		protected final DistributorDataStore store;
		protected final SQLiteDatabase db;

		private ChannelWorker(final DistributorDataStore store) {
			this.store = store;
			this.db = this.store.db;
		}

		/**
		 *
		 */
		public void upsert() {
			synchronized(this.store) {	

			}
		}

	}
	/**
	 * Query
	 */

	public static Cursor queryChannel(final DistributorDataStore store, String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		synchronized (store) {
			try {
				final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

				qb.setTables(Tables.CHANNEL.n);
				qb.setProjectionMap(ChannelConstants.PROJECTION_MAP);

				// Get the database and run the query.
				return qb.query(store.db, projection, whereClause, whereArgs, null, null,
						(!TextUtils.isEmpty(sortOrder)) ? sortOrder
								: ChannelConstants.DEFAULT_SORT_ORDER);
			} catch (IllegalArgumentException ex) {
				logger.error("query channel {} {}", whereClause, whereArgs);
			}
			return null;
		}
	}

	/**
	 * Upsert
	 */

	public static long upsertChannelByName(final DistributorDataStore store, String channel, ChannelState status) {
		synchronized (store) {
			try {
				final ContentValues cv = new ContentValues();		
				cv.put(ChannelField.STATE.cv(), status.cv());
				PLogger.STORE_CHANNEL_DML.trace("upsert channel: {} @ {}",
						channel, status);

				final int updateCount = store.db.update(Tables.CHANNEL.n, cv, 
						CHANNEL_UPDATE_CLAUSE, new String[]{ channel } );
				if (updateCount > 0) return 0;

				cv.put(ChannelField.NAME.cv(), channel);
				store.db.insert(Tables.CHANNEL.n, ChannelField.NAME.n(), cv);
			} catch (IllegalArgumentException ex) {
				logger.error("upsert channel {} {}", channel, status);
			}
			return 0;
		}
	}
	static final private String CHANNEL_UPDATE_CLAUSE = new StringBuilder()
	.append(ChannelField.NAME.q(null)).append("=?").toString();


	/**
	 * Update
	 */
	/**
	 * Delete
	 */

}

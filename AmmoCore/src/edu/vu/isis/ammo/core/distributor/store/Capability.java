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
import android.provider.BaseColumns;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.IAmmoRequest;
import edu.vu.isis.ammo.api.type.TimeTrigger;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.store.PersistenceHelper.TableField;
import edu.vu.isis.ammo.core.distributor.store.PersistenceHelper.TableFieldState;

public class Capability {
	private final static Logger logger = LoggerFactory.getLogger("class.store.capability");


	static public void onCreate(SQLiteDatabase db) {
		String sqlCreateRef = null; 
		try {	
			final Relations table = Relations.CAPABILITY;

			final StringBuilder createSql = new StringBuilder()
			.append(" CREATE TABLE ")
			.append(table.q())
			.append(" ( ")
			.append(PersistenceHelper.ddl(CapabilityField.values())).append(')')
			.append(';');

			sqlCreateRef = createSql.toString();
			PLogger.STORE_DDL.trace("{}", sqlCreateRef);
			db.execSQL(sqlCreateRef);

		} catch (SQLException ex) {
			logger.error("failed create CAPABILITY {} {}",
					sqlCreateRef, ex.getLocalizedMessage());
			return;
		}
	}


	/**
	 * The capability table is for holding information about current subscriptions.
	 *
	 */
	public enum CapabilityField  implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		ORIGIN("origin","TEXT"),
		// The device identifier, this must be present
		// required

		TOPIC("topic", "TEXT"),
		// This along with the cost is used to decide how to deliver the specific object.

		SUBTOPIC("subtopic", "TEXT"),
		// This is used in conjunction with topic. 
		// It can be used to identify a recipient, a group, a target, etc.

		OPERATOR("operator", "TEXT"),
		// The name of the operator using the channel
		// optional

		EXPIRATION("expiration", "INTEGER"),
		// Time-stamp at which point the request 
		// becomes stale and can be discarded.

		FIRST("first", "INTEGER"),
		// When the operator first used this channel
		// The first field indicates the first time the peer was observed.

		LATEST("latest", "INTEGER"),
		// When the operator was last seen "speaking" on the channel
		// The latest field indicates the last time the peer was observed.

		COUNT("count", "INTEGER"),
		// How many times the peer has been seen since FIRST
		// Each time LATEST is changed this COUNT should be incremented

		FILTER("filter", "TEXT");
		// The rows/tuples wanted.


		// TODO : what about message rates?


		final public TableFieldState impl;

		private CapabilityField(String name, String type) {
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

	public static interface CapabilityConstants extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public static final String FOREIGN_KEY = null;

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (CapabilityField field : CapabilityField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();

				for (CapabilityField field : CapabilityField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	};


	/**
	 * CapabilityWorker
	 * An actor for updating the PRESENCE component of the store.
	 * 
	 * @param deviceId
	 * @return
	 */
	public static CapabilityWorker getWorker(final DistributorDataStore parent, final IAmmoRequest ar, final AmmoService svc,
			String device, String operator) {
		return new CapabilityWorker(parent, (AmmoRequest) ar, svc, device, operator);
	}
	/** 
	 * Capability store access class
	 */
	public static class CapabilityWorker {
		public final String device;
		public final String operator;
		public final String topic;	
		public final String subtopic;
		public final TimeTrigger expire;

		final private DistributorDataStore parent;
		final private Object db;

		private CapabilityWorker(final DistributorDataStore parent, 
				final AmmoRequest ar, final AmmoService svc, 
				final String device, final String operator) {

			this.parent = parent;
			this.db = null;
			this.device = device;
			this.operator = operator;
			this.topic = ar.topic.asString();
			this.subtopic = (ar.subtopic == null) ? "" : ar.subtopic.asString();

			this.expire = ar.expire;
		}

		private CapabilityWorker(final DistributorDataStore parent, final Cursor pending, final AmmoService svc) {
			this.parent = parent;
			this.db = null;

			this.device = pending.getString(pending.getColumnIndex(CapabilityField.ORIGIN.n()));
			this.operator = pending.getString(pending.getColumnIndex(CapabilityField.OPERATOR.n()));
			this.topic = pending.getString(pending.getColumnIndex(CapabilityField.TOPIC.n()));
			this.subtopic = pending.getString(pending.getColumnIndex(CapabilityField.SUBTOPIC.n()));

			final long expireEnc = pending.getLong(pending.getColumnIndex(CapabilityField.EXPIRATION.n()));
			this.expire = null; // new TimeTrigger(expireEnc);
		}

		/**
		 * returns the number of rows affected.
		 * 
		 * @param deviceId
		 * @return
		 */
		public long upsert() {
			PLogger.STORE_CAPABILITY_DML.trace("upsert capability: device=[{}] @ {}",
					this.device, this);
			synchronized(this.parent) {	
				final ContentValues cv = new ContentValues();
				final Long now = Long.valueOf(System.currentTimeMillis());

				cv.put(CapabilityField.LATEST.cv(), now);
				if (this.operator != null) cv.put(CapabilityField.OPERATOR.cv(), this.operator);

				final String whereClause = CAPABILITY_KEY_CLAUSE;
				final String[] whereArgs = new String[]{ this.device, this.topic, this.subtopic };

				// this.db.beginTransaction();
				try {
					int updated = 0; // this.db.update(Relations.CAPABILITY.n, cv, whereClause, whereArgs);
					if (updated > 0) {
						PLogger.STORE_CAPABILITY_DML.debug("updated cnt=[{}] cv=[{}]", 
								updated, cv);
						// this.db.setTransactionSuccessful();
						return updated;
					} 
					cv.put(CapabilityField.ORIGIN.cv(), this.device);
					cv.put(CapabilityField.TOPIC.cv(), this.topic);
					cv.put(CapabilityField.SUBTOPIC.cv(), this.subtopic);

					cv.put(CapabilityField.FIRST.cv(), now);

					long row = 1; // this.db.insert(Relations.CAPABILITY.n, CapabilityField._ID.n(), cv);
					PLogger.STORE_CAPABILITY_DML.debug("inserted row=[{}] cv=[{}]", 
							row, cv);

					// this.db.setTransactionSuccessful();
					return 1;
				} catch (IllegalArgumentException ex) {
					logger.error("update capablity: ex=[{}]", ex.getLocalizedMessage());
				} finally {
					// this.db.endTransaction();
				}
				return -1;
			}
		}

		public int delete(String tupleId) {
			final String whereClause = new StringBuilder()
			.append(CapabilityField._ID.q(null)).append("=?")
			.toString();

			final String[] whereArgs = new String[] {tupleId};

			try {
				final int count = 0; // this.db.delete(Relations.CAPABILITY.n, whereClause, whereArgs);

				logger.trace("Capability delete count: [{}]", count);
				return count;
			} catch (IllegalArgumentException ex) {
				logger.error("delete capablity {} {}", whereClause, whereArgs);
			}
			return 0;
		}

		/**
		 * @return
		 */
		public int delete() {
			final String whereClause = CAPABILITY_KEY_CLAUSE;
			final String[] whereArgs = new String[] {this.device, this.topic, this.subtopic};

			try {
				final int count = 0; // this.db.delete(Relations.CAPABILITY.n, whereClause, whereArgs);

				logger.trace("Capability delete count: [{}]", count);
				return count;
			} catch (IllegalArgumentException ex) {
				logger.error("delete capablity {} {}", whereClause, whereArgs);
			}
			return 0;
		}
	}

	private static final String CAPABILITY_KEY_CLAUSE = new StringBuilder()
	.append(CapabilityField.ORIGIN.q(null)).append("=?")
	.append(" AND ")
	.append(CapabilityField.TOPIC.q(null)).append("=?")
	.append(" AND ")
	.append(CapabilityField.SUBTOPIC.q(null)).append("=?")
	.toString();


}

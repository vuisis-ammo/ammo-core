package edu.vu.isis.ammo.core.distributor.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Presence {
	private final static Logger logger = LoggerFactory.getLogger("class.store.presence");

	/**
	 * The presence table is for holding information about visible peers.
	 * A peer is a particular device over a specific channel.
	 * 
	 */
	/*
	public enum PresenceField implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		ORIGIN("origin","TEXT"),
		// The device identifier detected
		// required

		OPERATOR("operator", "TEXT"),
		// The name of the operator using the channel
		// optional

		EXPIRATION("expiration", "INTEGER"),
		// Time-stamp at which point the presence 
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

		ENABLE("enable", "INTEGER"),
		// 0 : intentionally disabled, 
		// >0 (1) : best knowledge is enabled

		CHANNEL("channel", "TEXT"),
		// The channel type

		ADDRESS("address", "TEXT");
		// The address for the channel type
		// For IP networks, sockets, this is the IP address, and port
		// For TDMA this is the slot number

		final public TableFieldState impl;

		private PresenceField(String name, String type) {
			this.impl = TableFieldState.getInstance(name,type);
		}
		*/

		/**
		 * required by TableField interface
		 */
	/*
		public String q(String tableRef) { return this.impl.quoted(tableRef); }
		public String cv() { return this.impl.cvQuoted(); }
		public String n() { return this.impl.n; }
		public String t() { return this.impl.t; }
		public String ddl() { return this.impl.ddl(); }
	};

	public static interface PresenceConstants extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public static final String FOREIGN_KEY = null;

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (PresenceField field : PresenceField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (PresenceField field : PresenceField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	};
	*/

	/**
	 * PresenceWorker
	 * An actor for updating the PRESENCE component of the store.
	 * 
	 * @param deviceId
	 * @return
	 */
	/*
	public static PresenceWorker getWorker(final DistributorDataStore parent, 
			final String deviceId, final String operator) {
		return new PresenceWorker(parent, deviceId, operator);
	}
	*/
	/** 
	 * Postal store access class
	 */
	/*
	public static class PresenceWorker {
		public final String deviceId;
		public final String operator;

		final DistributorDataStore parent;
		final Object db;

		private PresenceWorker(final DistributorDataStore parent, final String deviceId, final String operator) {
			this.parent = parent;
			this.db = null; // parent.db;
			this.deviceId = deviceId;
			this.operator = operator;
		}

		@Override
		public String toString() {
			return new StringBuilder()
			.append(" device=[").append(deviceId).append(']')
			.append(" operator=[").append(operator).append(']')
			.toString();
		}
		*/

		/**
		 * Update device presence information for a specified device.
		 *
		 * @param deviceId - String - the device id whose presence information to update
		 */
	/*
		public void upsert() {
			PLogger.STORE_PRESENCE_DML.trace("upsert presence: {}", this);
			synchronized(parent) {	
				if (this.deviceId == null || this.deviceId.length() == 0) {
					logger.warn("no device to record");
					return;
				}
				logger.trace("Updating device presence for device: {}", this.deviceId);

				// final String whereClause = PRESENCE_KEY_CLAUSE;
				// final String[] whereArgs = new String[]{ deviceId };

				final Long now = Long.valueOf(System.currentTimeMillis());

				final ContentValues cv = new ContentValues();
				cv.put(CapabilityField.LATEST.cv(), now);
				if (this.operator != null) cv.put(CapabilityField.OPERATOR.cv(), this.operator);

				// this.db.beginTransaction();
				try {
					int updated = 0; // this.db.update(Relations.PRESENCE.n, cv, whereClause, whereArgs);
					if (updated > 0) {
						PLogger.STORE_PRESENCE_DML.debug("updated cnt=[{}] cv=[{}]", 
								updated, cv);
						// this.db.setTransactionSuccessful();
						return;
					} 

					cv.put(CapabilityField.ORIGIN.cv(), this.deviceId);
					cv.put(CapabilityField.FIRST.cv(), now);

					long row = 0; // this.db.insert(Relations.PRESENCE.n, PresenceField._ID.n(), cv);
					PLogger.STORE_PRESENCE_DML.debug("inserted row=[{}] cv=[{}]", 
							row, cv);
					// this.db.setTransactionSuccessful();

				} catch (IllegalArgumentException ex) {
					logger.error("updateDevicePresence problem");
				} finally {
					// this.db.endTransaction();
				}
				return;
			}
		}

	}

	@SuppressWarnings("unused")
	private static final String PRESENCE_KEY_CLAUSE = new StringBuilder()
	.append(PresenceField.ORIGIN.q(null)).append("=?").toString();
*/

}

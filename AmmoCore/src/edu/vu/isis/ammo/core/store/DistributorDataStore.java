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
package edu.vu.isis.ammo.core.store;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.text.TextUtils;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.IAmmoRequest;
import edu.vu.isis.ammo.api.type.SerialMoment;
import edu.vu.isis.ammo.api.type.Notice;
import edu.vu.isis.ammo.api.type.Notice.Threshold;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.api.type.TimeTrigger;
import edu.vu.isis.ammo.api.type.Topic;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy;
import edu.vu.isis.ammo.core.distributor.DistributorState;
import edu.vu.isis.ammo.util.FullTopic;

/**
 * The Distributor Store Object is managed by the distributor thread.
 *
 * The distributor thread manages two queues.
 * <ul>
 * <li>coming from the AIDL calls from clients</li>
 * <li>coming from the gateway</li>
 * </ul>
 *
 */
public class DistributorDataStore {
	// ===========================================================
	// Constants
	// ===========================================================
	private final static Logger logger = LoggerFactory.getLogger("class.DistributorDataStore");
	public static final int VERSION = 29;

	public static final String NAME = 
			"distributor.db";   // to create .../databases/distributor.db
	// null;            // to create in memory database



	// ===========================================================
	// Fields
	// ===========================================================
	private final Context context;
	private SQLiteDatabase db;
	private final DataStoreHelper helper;
	// ===========================================================
	// Schema
	// ===========================================================



	/**
	 * Interface for defining sets of fields.
	 */

	public interface TableField {	
		// public List<TableFieldState> getState();

		/**
		 * Get the quoted field name.
		 * If the table ref is provided then prefix
		 * the quoted string with it.
		 * e.g. if tableRef is "r" and the name is "foo"
		 * then the returned value is 'r."foo"'
		 * 
		 * @param tableRef
		 * @return the field name enclosed in double quotes.
		 */
		public String q(String tableRef); 

		/**
		 * Get the field name suitable for using in ContentValues
		 * 
		 * @return the same as n() but returns "null" as a string
		 *          when the name is null.
		 */
		public String cv();

		/**
		 * Get the name from the implementation.
		 *
		 * @return the unquoted/unmodified field name.
		 */
		public String n(); 

		/**
		 * Get the type from the implementation.
		 * 
		 * @return the unquoted type name.
		 */
		public String t(); 

		/**
		 * form the field clause suitable for use in sql create
		 * @return
		 */
		public String ddl();
	}

	/**
	 * A holder class for the functions implementing the
	 * methods of the TableField interface.
	 */
	static public class TableFieldState {
		final public String n;
		final public String t;

		private TableFieldState(String name, String type) {	
			this.n = name;
			this.t = type;
		}

		public String quoted(String tableRef) {
			if (tableRef == null) {
				return new StringBuilder()
				.append('"').append(this.n).append('"')
				.toString();
			}
			return new StringBuilder()
			.append(tableRef).append('.')
			.append('"').append(this.n).append('"')
			.toString();
		}

		public String cvQuoted() {
			return String.valueOf(this.n);
		}

		/**
		 * Produce string of the form...
		 * "<field-name>" <field-type>
		 * e.g.
		 * "dog" TEXT
		 * suitable for use in the table creation.
		 */
		public String ddl() {
			return new StringBuilder()
			.append('"').append(this.n).append('"').append(' ').append(this.t)
			.toString();
		}
	}

	public String ddl(TableField[] values) {
		final List<TableField> fields = Arrays.asList(values);
		final StringBuilder sb = new StringBuilder();
		if (fields.size() < 1) return "";

		final TableField first = fields.get(0);
		sb.append(first.ddl());

		for (TableField field : fields.subList(1, fields.size()) ) {
			sb.append(",").append(field.ddl());
		}
		return sb.toString();
	}

	/**
	 * The capability table is for holding information about current subscriptions.
	 *
	 */
	public enum CapabilityField  implements TableField {

		FILTER("filter", "TEXT"),
		// The rows/tuples wanted.

		FIRST("first", "INTEGER"),
		// When the operator first used this channel

		LATEST("latest", "INTEGER"),
		// When the operator was last seen "speaking" on the channel

		ORIGIN("origin", "TEXT");
		// where did the request originate, device id


		// TODO : what about message rates?


		final public TableFieldState impl;

		private CapabilityField(String name, String type) {
			this.impl = new TableFieldState(name,type);
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

		public static final String PARENT_KEY_REF = null;

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
	public CapabilityWorker getCapabilityWorker(final IAmmoRequest ar, final AmmoService svc) {
		return new CapabilityWorker((AmmoRequest) ar, svc);
	}
	/** 
	 * Capability store access class
	 */
	public class CapabilityWorker {
		public final UUID uuid;
		public final String auid;
		public final String topic;	
		public final String subtopic;
		public final Provider provider;
		public final TimeTrigger expire;

		public Payload payload = null;

		private CapabilityWorker(final AmmoRequest ar, final AmmoService svc) {
			this.uuid = UUID.fromString(ar.uuid); 
			this.auid = ar.uid;
			this.topic = ar.topic.asString();
			this.subtopic = (ar.subtopic == null) ? "" : ar.subtopic.asString();
			this.provider = ar.provider;

			this.expire = ar.expire;
		}

		private CapabilityWorker(final Cursor pending, final AmmoService svc) {
			this.provider = new Provider(pending.getString(pending.getColumnIndex(RequestField.PROVIDER.n())));
			this.payload = new Payload(pending.getString(pending.getColumnIndex(PostalField.PAYLOAD.n())));
			this.topic = pending.getString(pending.getColumnIndex(RequestField.TOPIC.n()));
			this.subtopic = pending.getString(pending.getColumnIndex(RequestField.SUBTOPIC.n()));
			this.uuid = UUID.fromString(pending.getString(pending.getColumnIndex(RequestField.UUID.n())));
			this.auid = pending.getString(pending.getColumnIndex(RequestField.AUID.n()));

			final long expireEnc = pending.getLong(pending.getColumnIndex(RequestField.EXPIRATION.n()));
			this.expire = new TimeTrigger(expireEnc);
		}

		public long upsert(final String device) {
			PLogger.STORE_CAPABILITY_DML.trace("upsert capability: {} @ {}",
					device, this);
			synchronized(DistributorDataStore.this) {	
				final ContentValues rqstValues = new ContentValues();
				rqstValues.put(RequestField.UUID.cv(), this.uuid.toString());
				rqstValues.put(RequestField.AUID.cv(), this.auid);
				rqstValues.put(RequestField.TOPIC.cv(), this.topic);
				rqstValues.put(RequestField.SUBTOPIC.cv(), this.subtopic);
				rqstValues.put(RequestField.PROVIDER.cv(), this.provider.cv());
				rqstValues.put(RequestField.EXPIRATION.cv(), this.expire.cv());

				rqstValues.put(RequestField.CREATED.cv(), System.currentTimeMillis());

				rqstValues.put(CapabilityField.ORIGIN.cv(), device);

				return -1;
			}
		}

		public int delete(String tupleId) {
			final String whereClause = new StringBuilder()
			.append(RequestField.TOPIC.q(null)).append("=?")
			.append(" AND ")
			.append(RequestField.SUBTOPIC.q(null)).append("=?")
			.toString();

			final String[] whereArgs = new String[] {tupleId, this.topic, this.subtopic};

			try {
				final SQLiteDatabase db = DistributorDataStore.this.helper.getWritableDatabase();
				final int count = db.delete(Tables.CAPABILITY.n, whereClause, whereArgs);

				logger.trace("Capability delete {}", count);
				return count;
			} catch (IllegalArgumentException ex) {
				logger.error("delete capablity {} {}", whereClause, whereArgs);
			}
			return 0;
		}
	}


	/**
	 * The presence table is for holding information about visible peers.
	 * A peer is a particular device over a specific channel.
	 * 
	 */
	public enum PresenceField implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		DEVICE("device","TEXT"),
		// The device identifier, this must be present
		// required

		OPERATOR("operator", "TEXT"),
		// The name of the operator using the channel
		// optional

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

		private PresenceField(String n, String t) {
			this.impl = new TableFieldState(n,t);
		}

		/**
		 * required by TableField interface
		 */
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

		public static final String PARENT_KEY_REF = null;

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

	/**
	 * PresenceWorker
	 * An actor for updating the PRESENCE component of the store.
	 * 
	 * @param deviceId
	 * @return
	 */
	public PresenceWorker getPresenceWorker(final String deviceId) {
		return new PresenceWorker(deviceId);
	}
	/** 
	 * Postal store access class
	 */
	public class PresenceWorker {
		public final String deviceId;

		private PresenceWorker(final String deviceId) {
			this.deviceId = deviceId;
		}

		/**
		 * Update device presence information for a specified device.
		 *
		 * @param deviceId - String - the device id whose presence information to update
		 */
		public void upsert() {
			final DistributorDataStore parent = DistributorDataStore.this;
			PLogger.STORE_PRESENCE_DML.trace("upsert presence: {}", this);
			synchronized(parent) {	
				if (this.deviceId == null || this.deviceId.length() == 0) {
					return;
				}
				logger.trace("Updating device presence for device: {}", this.deviceId);
				Cursor cursor = null;
				try {
					final SQLiteDatabase db = parent.helper.getWritableDatabase();
					final String whereClause = PRESENCE_KEY_QUERY;
					final String[] whereArgs = new String[]{ deviceId };
					db.beginTransaction();
					cursor = db.query(Tables.PRESENCE.n, null, 
							whereClause, whereArgs, null, null, null);

					final ContentValues values = new ContentValues();
					values.put(PresenceField.DEVICE.n(), deviceId);
					// values needs some more content

					if (cursor.getCount() < 1) {
						db.insert(Tables.PRESENCE.n, PresenceField._ID.n(), values);
					} else {
						// values needs some more content
						db.update(Tables.PRESENCE.n, values, whereClause, whereArgs);
					}
					cursor.close();
					db.endTransaction();
				} catch (IllegalArgumentException ex) {
					logger.error("updateDevicePresence problem");
				} finally {
					if (cursor != null) cursor.close();
				}
				return;
			}
		}

	}

	private static final String PRESENCE_KEY_QUERY = new StringBuilder()
	.append(PresenceField.DEVICE.q(null)).append("=?").toString();



	/**
	 * ===========================
	 *  REQUEST
	 * ===========================
	 */

	/**
	 * The Request table holds application requests.
	 * These requests are to express interest in data of certain types 
	 * or to announce the presence of information of a certain type.
	 * The request can apply to the past or the future.
	 *
	 */

	public ContentValues initializeRequestDefaults(ContentValues values) {
		final Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(RequestField.TOPIC.n())) {
			values.put(RequestField.TOPIC.n(),"unknown");
		}
		if (!values.containsKey(RequestField.SUBTOPIC.n())) {
			values.put(RequestField.SUBTOPIC.n(),"");
		}
		if (!values.containsKey(RequestField.PROVIDER.n())) {
			values.put(RequestField.PROVIDER.n(),"unknown");
		}
		if (!values.containsKey(RequestField.DISPOSITION.n())) {
			values.put(RequestField.DISPOSITION.n(),
					DisposalState.PENDING.o);
		}

		if (!values.containsKey(RequestField.PRIORITY.n())) {
			values.put(RequestField.PRIORITY.n(), PriorityType.NORMAL.o);
		}

		if (!values.containsKey(RequestField.SERIAL_MOMENT.n())) {
			values.put(RequestField.SERIAL_MOMENT.n(),
					SerialMoment.EAGER.cv());
		}

		if (!values.containsKey(RequestField.EXPIRATION.n())) {
			values.put(RequestField.EXPIRATION.n(), now);
		}
		if (!values.containsKey(RequestField.CREATED.n())) {
			values.put(RequestField.CREATED.n(), now);
		}
		if (!values.containsKey(RequestField.MODIFIED.n())) {
			values.put(RequestField.MODIFIED.n(), now);
		}

		if (!values.containsKey(RequestField.NOTICE.n())) {
			values.put(RequestField.NOTICE.n(), 0);
		}
		return values;
	}


	public enum RequestField implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		FK("request", "INTEGER"), // Foreign key

		UUID("uuid", "TEXT"),
		// This is a unique identifier for the request
		// It is used to look up the appropriate provider

		TYPE("type", "INTEGER"),
		// Meaning the parent type: interest, retrieval, postal

		CREATED("created", "INTEGER"),
		// When the request was made

		MODIFIED("modified", "INTEGER"),
		// When the request was last modified

		COUNT("count", "INTEGER"),
		// How many times the tuple has been modified since CREATED.
		// Each time MODIFIED is changed this COUNT should be incremented.

		TOPIC("topic", "TEXT"),
		// This along with the cost is used to decide how to deliver the specific object.

		SUBTOPIC("subtopic", "TEXT"),
		// This is used in conjunction with topic. 
		// It can be used to identify a recipient, a group, a target, etc.

		PRESENCE("presence", "INTEGER"),
		// The rowid for the originator of this request
		// 0 is reserved for the local operator
		// <0 (-1) : indicates that the operator is unknown.

		AUID("auid", "TEXT"),
		// (optional) The appplication specific unique identifier
		// This is used in notice intents so the application can relate.

		PROVIDER("provider", "TEXT"),
		// The uri of the content provider

		DISPOSITION("disposition", "INTEGER"),
		// The current best guess of the status of the request.

		PRIORITY("priority", "INTEGER"),
		// With what priority should this message be sent. 
		// Negative priorities indicated less than normal.

		SERIAL_MOMENT("serial_event", "INTEGER"),
		// When the serialization happens. {APRIORI, EAGER, LAZY}

		EXPIRATION("expiration", "INTEGER"),
		// Time-stamp at which point the request 
		// becomes stale and can be discarded.

		NOTICE("notice", "INTEGER");
		// indicates which thresholds are to be noticed
		// see Notice.java for detail


		final public TableFieldState impl;

		private RequestField(String n, String t) {
			this.impl = new TableFieldState(n,t);
		}

		/**
		 * required by TableField interface
		 */
		public String q(String tableRef) { return this.impl.quoted(tableRef); }
		public String cv() { return this.impl.cvQuoted(); }
		public String n() { return this.impl.n; }
		public String t() { return this.impl.t; }
		public String ddl() { return this.impl.ddl(); }
	};

	public static interface RequestConstants {

		public static final String DEFAULT_SORT_ORDER = 
				new StringBuilder().append(RequestField.MODIFIED.n()).append(" DESC ").toString();
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public static final String PARENT_KEY_REF = null;

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (RequestField field : RequestField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (RequestField field : RequestField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	};


	public RequestWorker getPostalRequestWorker() {
		return new RequestWorker(db, Tables.POSTAL);
	}

	public RequestWorker getRetrievalRequestWorker() {
		return new RequestWorker(db, Tables.RETRIEVAL);
	}

	public RequestWorker getInterestRequestWorker() {
		return new RequestWorker(db, Tables.INTEREST);
	}

	/** 
	 * Store access class
	 */
	public class RequestWorker {

		private final SQLiteDatabase db;
		private final Tables table;

		private RequestWorker(final SQLiteDatabase db, final Tables table) {
			this.db = db;
			this.table = table;
		}

		private DisposalWorker getDispose() {
			switch(this.table){
			case POSTAL: return DistributorDataStore.this.getPostalDisposalWorker();
			case RETRIEVAL: return DistributorDataStore.this.getRetrievalDisposalWorker();
			case INTEREST: return DistributorDataStore.this.getInterestDisposalWorker();
			}
			return null;
		}

		/**
		 * @param cv
		 * @param status
		 * @param viewName
		 * @param table
		 * @return
		 */
		public long upsert(ContentValues cv, DistributorState status) {
			synchronized (DistributorDataStore.this) {
				Cursor cursor  = null;
				try {
					final String uuid = cv.getAsString(RequestField.UUID.cv());
					final String topic = cv.getAsString(RequestField.TOPIC.cv());
					final String subtopic = cv.getAsString(RequestField.SUBTOPIC.cv());
					final String provider = cv.getAsString(RequestField.PROVIDER.cv());

					final long rowid;
					final String whereClause;
					final String[] whereArgs;
					if (uuid != null) {
						whereClause = REQUEST_UPDATE_CLAUSE_KEY;
						whereArgs = new String[]{ uuid };
					} else {
						whereClause = REQUEST_UPDATE_CLAUSE;
						whereArgs = new String[]{ topic, subtopic, provider };
					}
					cursor = this.db.query(this.table.n, 
							new String[] {RequestField._ID.q(null)}, 
							whereClause, whereArgs, null, null, null);
					if (cursor.getCount() > 0) {
						rowid = cursor.getLong(cursor.getColumnIndex(RequestField._ID.q(null)));
						final String[] rowid_arg = new String[]{ Long.toString(rowid) };
						this.db.update(table.n, cv, ROWID_CLAUSE, rowid_arg );
						cursor.close();
					} else {
						rowid = this.db.insert(this.table.n, RequestField.CREATED.n(), cv);
					}
					this.getDispose().upsertByRequest(rowid, status);
					return rowid;
				} catch (IllegalArgumentException ex) {
					logger.error("upsert {} {}", cv, status);
				} finally {
					if (cursor != null) cursor.close();
				}
				return -1;
			}
		}


		/**
		 * Update
		 */
		/** an object represented in the database.
		 * Any reasonable update will need to know how to select an existing object.
		 */
		private long updateById(long requestId, ContentValues cv, DistributorState state) {
			synchronized (DistributorDataStore.this) {
				if (state == null && cv == null) return -1;

				if (cv == null) cv = new ContentValues();

				if (state != null) {
					this.getDispose().upsertByRequest(requestId, state);
					cv.put(RequestField.DISPOSITION.n(), state.aggregate().cv());
				}	
				try {
					final String whereClause = new StringBuilder()
					.append(RequestField._ID.q(null)).append("=?")
					.toString();

					final String[] whereArgs = new String[]{ String.valueOf(requestId) };
					return this.db.update(this.table.n, cv, whereClause, whereArgs); 

				} catch (IllegalArgumentException ex) {
					logger.error("updateRequestById {} {}", requestId, cv);
				}
				return 0;
			}
		}

	}

	private static String RequestStatusQuery(Tables request, Tables disposal) {
		return new StringBuilder()
		.append(" SELECT ").append(" * ")
		.append(" FROM ")
		.append(request.q()).append(" AS r ")
		.append(" WHERE ")
		.append(" EXISTS (SELECT * ")
		.append(" FROM ").append(disposal.q()).append(" AS d ")
		.append(" INNER JOIN ").append(Tables.CHANNEL.q()).append(" AS c ")
		.append(" ON ").append(DisposalField.CHANNEL.q("d")).append("=").append(ChannelField.NAME.q("c"))
		.append(" WHERE ").append(RequestField._ID.q("r")).append("=").append(DisposalField.REQUEST.q("d"))
		.append("   AND ").append(ChannelField.STATE.q("c")).append('=').append(ChannelState.ACTIVE.q())
		.append("   AND ").append(DisposalField.STATE.q("d"))
		.append(" IN (").append(DisposalState.PENDING.q()).append(')')
		.append(')') // close exists clause	
		.append(" ORDER BY ")
		.append(RequestField.PRIORITY.q("r")).append(" DESC ").append(", ")
		.append(RequestField._ID.q("r")).append(" ASC ")	
		.toString();
	}

	private synchronized Cursor queryRequest(String rel, 
			String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		try {
			this.openRead();
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(rel);
			qb.setProjectionMap(RequestConstants.PROJECTION_MAP);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, whereClause, whereArgs, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: RequestConstants.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query request {} {}", whereClause, whereArgs);
		}
		return null;
	}

	private synchronized Cursor queryRequestByUuid(String[] projection, String uuid, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Tables.REQUEST.n);
			//qb.setProjectionMap(projection);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, REQUEST_UUID_QUERY, new String[]{ uuid }, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: RetrievalConstants.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query retrieval by key {} {} {}", new Object[]{ projection, uuid });
		}
		return null;
	}
	static private final String REQUEST_UUID_QUERY = new StringBuilder()
	.append(RequestField.UUID.q(null)).append("=?")
	.toString();

	private synchronized Cursor queryRequestByTopic(String rel, String[] projection,
			final String topic, final String subtopic, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(rel);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, REQUEST_TOPIC_QUERY, new String[]{topic, subtopic}, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: InterestConstants.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query interest by key {} [{}:{}]", new Object[]{ projection, topic, subtopic });
		}
		return null;
	}
	static private final String REQUEST_TOPIC_QUERY = new StringBuilder()
	.append(RequestField.TOPIC.q(null)).append("=?")
	.append(" AND ")
	.append(RequestField.SUBTOPIC.q(null)).append("=?")
	.toString();


	/**
	 * ===========================
	 *  POSTAL
	 * ===========================
	 */

	/** Insert method helper */
	public ContentValues initializePostalDefaults(ContentValues values) {

		initializeRequestDefaults(values);

		if (!values.containsKey(PostalField.DATA.n())) {
			values.put(PostalField.DATA.n(), "");
		}
		return values;
	}

	public PostalWorker getPostalWorker(final AmmoRequest ar, final AmmoService svc) {
		return new PostalWorker(ar, svc);
	}
	public PostalWorker getPostalWorker(final Cursor pending, final AmmoService svc) {
		return new PostalWorker(pending, svc);
	}
	public PostalWorker getPostalWorkerByKey(String uid) {
		Cursor cursor = null;
		try {
			cursor = queryRequest(Tables.POSTAL.q(), null, 
					SELECT_POSTAL_BY_KEY, new String[]{ uid }, null);
			final PostalWorker worker = getPostalWorker(cursor, null);
			cursor.close();
			return worker;
		} finally {
			if (cursor != null) cursor.close();
		}		
	}

	private static String SELECT_POSTAL_BY_KEY = new StringBuilder()
	.append(RequestField.UUID.cv()).append("=?").toString();




	/**
	 * The postal table is for holding retrieval requests.
	 */
	public enum PostalField  implements TableField {

		PAYLOAD("payload", "TEXT"),
		// The payload instead of content provider

		DATA("data", "TEXT"),
		// If null then the data file corresponding to the
		// column name and record id should be used. 
		// This is done when the data
		// size is larger than that allowed for a field contents.

		NOTICE_SENT("notice_sent", "INTEGER"),
		NOTICE_DELIVERY("notice_delivery", "INTEGER"),
		NOTICE_RECEIPT("notice_receipt", "INTEGER"),
		NOTICE_GATE_IN("notice_gate_in", "INTEGER"),
		NOTICE_GATE_OUT("notice_gate_out", "INTEGER");
		/**
		 * These notices represent the action to be taken 
		 * as the message crosses the threshold specified.
		 * 0x00 or null indicate that no action is to be taken.
		 * Otherwise the integer represents a list of bits.
		 */

		final public TableFieldState impl;

		private PostalField(String n, String t) {
			this.impl = new TableFieldState(n,t);
		}

		/**
		 * required by TableField interface
		 */
		public String q(String tableRef) { return this.impl.quoted(tableRef); }
		public String cv() { return this.impl.cvQuoted(); }
		public String n() { return this.impl.n; }
		public String t() { return this.impl.t; }
		public String ddl() { return this.impl.ddl(); }
	};

	public static interface PostalConstants extends RequestConstants {
		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (RequestField field : RequestField.values()) {
					columns.add(field.n());
				}
				for (PostalField field : PostalField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (RequestField field : RequestField.values()) {
					projection.put(field.n(), field.n());
				}
				for (PostalField field : PostalField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	};


	/** 
	 * Postal store access class
	 */
	public class PostalWorker {
		public final UUID uuid;
		public final String auid;
		public final String topic;	
		public final String subtopic;
		public final Provider provider;
		public final DistributorPolicy.Topic policy;
		public final SerialMoment serialMoment; 
		public final int priority;
		public final TimeTrigger expire;
		public final Notice notice;

		public DisposalTotalState totalState = null;
		public DistributorState status = null;
		public Payload payload = null;

		private PostalWorker(final AmmoRequest ar, final AmmoService svc) {
			this.uuid = UUID.fromString(ar.uuid); //UUID.randomUUID();
			this.auid = ar.uid;
			this.topic = ar.topic.asString();
			this.subtopic = (ar.subtopic == null) ? "" : ar.subtopic.asString();
			this.provider = ar.provider;
			this.policy = svc.policy().matchPostal(topic);
			this.serialMoment = ar.moment;
			this.notice = ar.notice;

			this.priority = policy.routing.priority+ar.priority;
			this.expire = ar.expire;

			this.status = policy.makeRouteMap();
			this.totalState = DisposalTotalState.NEW;
		}

		private PostalWorker(final Cursor pending, final AmmoService svc) {
			this.provider = new Provider(pending.getString(pending.getColumnIndex(RequestField.PROVIDER.n())));
			this.payload = new Payload(pending.getString(pending.getColumnIndex(PostalField.PAYLOAD.n())));
			this.topic = pending.getString(pending.getColumnIndex(RequestField.TOPIC.n()));
			this.subtopic = pending.getString(pending.getColumnIndex(RequestField.SUBTOPIC.n()));
			this.uuid = UUID.fromString(pending.getString(pending.getColumnIndex(RequestField.UUID.n())));
			this.auid = pending.getString(pending.getColumnIndex(RequestField.AUID.n()));
			this.serialMoment = new SerialMoment(pending.getInt(pending.getColumnIndex(RequestField.SERIAL_MOMENT.n())));
			this.policy = (svc == null) ? null : svc.policy().matchPostal(topic);

			this.priority = pending.getInt(pending.getColumnIndex(RequestField.PRIORITY.n()));
			final long expireEnc = pending.getLong(pending.getColumnIndex(RequestField.EXPIRATION.n()));
			this.expire = new TimeTrigger(expireEnc);

			this.notice = Notice.newInstance(); 
			this.notice.setItem(Threshold.SENT, pending.getInt(pending.getColumnIndex(PostalField.NOTICE_SENT.n())));
			this.notice.setItem(Threshold.DELIVERED, pending.getInt(pending.getColumnIndex(PostalField.NOTICE_DELIVERY.n())));
			this.notice.setItem(Threshold.RECEIVED, pending.getInt(pending.getColumnIndex(PostalField.NOTICE_RECEIPT.n())));
			this.notice.setItem(Threshold.GATE_IN, pending.getInt(pending.getColumnIndex(PostalField.NOTICE_GATE_IN.n())));
			this.notice.setItem(Threshold.GATE_OUT, pending.getInt(pending.getColumnIndex(PostalField.NOTICE_GATE_OUT.n())));
		}

		public long upsert(final DisposalTotalState totalState, final byte[] payload) {
			synchronized(DistributorDataStore.this) {	
				final ContentValues rqstValues = new ContentValues();
				rqstValues.put(RequestField.UUID.cv(), this.uuid.toString());
				rqstValues.put(RequestField.AUID.cv(), this.auid);
				rqstValues.put(RequestField.TOPIC.cv(), this.topic);
				rqstValues.put(RequestField.SUBTOPIC.cv(), this.subtopic);
				rqstValues.put(RequestField.PROVIDER.cv(), this.provider.cv());

				rqstValues.put(RequestField.SERIAL_MOMENT.cv(), this.serialMoment.cv());
				rqstValues.put(RequestField.PRIORITY.cv(), this.policy.routing.priority+this.priority);
				rqstValues.put(RequestField.EXPIRATION.cv(), this.expire.cv());

				rqstValues.put(RequestField.CREATED.cv(), System.currentTimeMillis());				
				rqstValues.put(RequestField.DISPOSITION.cv(), totalState.cv());
				if (payload != null) rqstValues.put(PostalField.PAYLOAD.cv(), payload);

				rqstValues.put(PostalField.NOTICE_SENT.cv(), this.notice.atSend.via.v);	
				rqstValues.put(PostalField.NOTICE_DELIVERY.cv(), this.notice.atDelivery.via.v);	
				rqstValues.put(PostalField.NOTICE_RECEIPT.cv(), this.notice.atReceipt.via.v);	
				rqstValues.put(PostalField.NOTICE_GATE_IN.cv(), this.notice.atGateIn.via.v);	
				rqstValues.put(PostalField.NOTICE_GATE_OUT.cv(), this.notice.atGateOut.via.v);	

				// values.put(PostalTableSchema.UNIT.cv(), 50);
				PLogger.STORE_POSTAL_DML.trace("upsert postal: {} @ {}",
						totalState, rqstValues);
				final RequestWorker requestor = DistributorDataStore.this.getPostalRequestWorker();
				return requestor.upsert(rqstValues, status);
			}
		}

		public int delete(String providerId) {
			final String whereClause = new StringBuilder()	
			.append(RequestField.TOPIC.q(null)).append("=?")
			.append(" AND ")
			.append(RequestField.SUBTOPIC.q(null)).append("=?")		
			.append(" AND ")
			.append(RequestField.PROVIDER.q(null)).append("=?")
			.toString();

			final String[] whereArgs = new String[] {this.topic, this.subtopic, providerId};

			try {
				final SQLiteDatabase db = DistributorDataStore.this.helper.getWritableDatabase();
				final int count = db.delete(Tables.POSTAL.n, whereClause, whereArgs);
				// proper use of foreign keys can help the following...
				final int disposalCount = db.delete(Tables.POSTAL_DISPOSAL.n, 
						DISPOSAL_POSTAL_ORPHAN_CONDITION, null);
				logger.trace("Postal delete {} {}", count, disposalCount);
				return count;
			} catch (IllegalArgumentException ex) {
				logger.error("delete postal {} {}", whereClause, whereArgs);
			}
			return 0;
		}

		public String getType() {
			return FullTopic.fromTopic(topic, subtopic).aggregate;
		}
	}

	/**
	 * Query
	 */
	/**
	 * Nearly direct access to the postal data store.
	 * Use sparingly, prefer the PostalWorker.
	 * 
	 * @param whereClause
	 * @param whereArgs
	 * @return
	 */

	public synchronized Cursor queryPostal(String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		return queryRequest(Tables.POSTAL.n, projection, whereClause, whereArgs, sortOrder);
	}

	public synchronized Cursor queryPostalReady() {
		this.openRead();
		try {
			return db.rawQuery(POSTAL_STATUS_QUERY, null);
		} catch(SQLiteException ex) {
			logger.error("sql error {}", ex.getLocalizedMessage());
		}
		return null;
	}
	private static final String POSTAL_STATUS_QUERY = 
			RequestStatusQuery(Tables.POSTAL, Tables.POSTAL_DISPOSAL);

	/**
	 * Update
	 */

	/**
	 * @param requestId
	 * @param cv
	 * @param state
	 * @return
	 */
	public synchronized long updatePostalByKey(long requestId, ContentValues cv, DistributorState state) {
		final RequestWorker requestor = this.getPostalRequestWorker();
		return requestor.updateById(requestId, cv, state);
	}
	public synchronized long updatePostalByKey(long requestId, String channel, final DisposalState state) {
		final DisposalWorker worker = this.getPostalDisposalWorker();
		return worker.upsertByRequest(requestId, channel, state);
	}


	/**
	 * Delete
	 */

	/**
	 * @param whereClause
	 * @param whereArgs
	 * @return
	 */
	public synchronized int deletePostal(String whereClause, String[] whereArgs) {
		try {
			final int count = this.db.delete(Tables.POSTAL.n, whereClause, whereArgs);
			final int disposalCount = db.delete(
					Tables.POSTAL_DISPOSAL.n, DISPOSAL_POSTAL_ORPHAN_CONDITION, null);
			logger.trace("Postal delete {} {}", count, disposalCount);
			return count;
		} catch (IllegalArgumentException ex) {
			logger.error("delete postal {} {}", whereClause, whereArgs);
		}
		return 0;
	}

	public synchronized int deletePostalGarbage() {
		try {
			final int expireCount = this.db.delete(Tables.POSTAL.n, 
					POSTAL_EXPIRATION_CONDITION, getRelativeExpirationTime(POSTAL_DELAY_OFFSET));
			final int disposalCount = db.delete(Tables.POSTAL_DISPOSAL.n, 
					DISPOSAL_POSTAL_ORPHAN_CONDITION, null);
			logger.trace("Postal garbage {} {}", expireCount, disposalCount);
			return expireCount;
		} catch (IllegalArgumentException ex) {
			logger.error("deletePostalGarbage {}", ex.getLocalizedMessage());
		} catch (SQLiteException ex) {
			logger.error("deletePostalGarbage {}", ex.getLocalizedMessage());
		}
		return 0;
	}
	private static final String DISPOSAL_POSTAL_ORPHAN_CONDITION = new StringBuilder()
	.append(DisposalField.TYPE.q(null)).append('=').append(Tables.POSTAL.cv())
	.append(" AND NOT EXISTS (SELECT * ")
	.append(" FROM ").append(Tables.POSTAL.q())
	.append(" WHERE ").append(DisposalField.REQUEST.q(null))
	.append('=').append(Tables.POSTAL.q()).append(".").append(RequestField._ID.q(null))
	.append(')')
	.toString();

	private static final String POSTAL_EXPIRATION_CONDITION = new StringBuilder()
	.append(RequestField.EXPIRATION.q(null))
	.append('<').append('?')
	.toString();

	private static final long POSTAL_DELAY_OFFSET = 8 * 60 * 60; // 1 hr in seconds



	/**
	 * ===========================
	 *  RETRIEVAL
	 * ===========================
	 * 
	 * The retrieval table is for holding retrieval requests.
	 */


	/** Insert method helper */
	protected ContentValues initializeRetrievalDefaults(ContentValues values) {
		final Long now = Long.valueOf(System.currentTimeMillis());

		initializeRequestDefaults(values);

		if (!values.containsKey(RetrievalField.PROJECTION.n())) {
			values.put(RetrievalField.PROJECTION.n(), "");
		}
		if (!values.containsKey(RetrievalField.SELECTION.n())) {
			values.put(RetrievalField.SELECTION.n(), "");
		}
		if (!values.containsKey(RetrievalField.ARGS.n())) {
			values.put(RetrievalField.ARGS.n(), "");
		}
		if (!values.containsKey(RetrievalField.ORDERING.n())) {
			values.put(RetrievalField.ORDERING.n(), "");
		}
		if (!values.containsKey(RetrievalField.LIMIT.n())) {
			values.put(RetrievalField.LIMIT.n(), -1);
		}
		if (!values.containsKey(RetrievalField.CONTINUITY_TYPE.n())) {
			values.put(RetrievalField.CONTINUITY_TYPE.n(),
					ContinuityType.ONCE.o);
		}
		if (!values.containsKey(RetrievalField.CONTINUITY_VALUE.n())) {
			values.put(RetrievalField.CONTINUITY_VALUE.n(), now);
		}
		return values;
	}


	public enum RetrievalField  implements TableField {

		PROJECTION("projection", "TEXT"),
		// The fields/columns wanted.

		SELECTION("selection", "TEXT"),
		// The rows/tuples wanted.

		ARGS("args", "TEXT"),
		// The values using in the selection.

		ORDERING("ordering", "TEXT"),
		// The order the values are to be returned in.

		LIMIT("maxrows", "INTEGER"),
		// The maximum number of items to retrieve
		// as items are obtained the count should be decremented

		CONTINUITY_TYPE("continuity_type", "INTEGER"),
		CONTINUITY_VALUE("continuity_value", "INTEGER");
		// The meaning changes based on the continuity type.
		// - ONCE : undefined
		// - TEMPORAL : chronic, this differs slightly from the expiration
		//      which deals with the request this deals with the time stamps
		//      of the requested objects.
		// - QUANTITY : the maximum number of objects to return

		final public TableFieldState impl;

		private RetrievalField(String name, String type) {
			this.impl = new TableFieldState(name,type);
		}

		/**
		 * required by TableField interface
		 */
		public String q(String tableRef) { return this.impl.quoted(tableRef); }
		public String cv() { return this.impl.cvQuoted(); }
		public String n() { return this.impl.n; }
		public String t() { return this.impl.t; }
		public String ddl() { return this.impl.ddl(); }
	};

	public static interface RetrievalConstants extends RequestConstants {

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (RequestField field : RequestField.values()) {
					columns.add(field.n());
				}
				for (RetrievalField field : RetrievalField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (RequestField field : RequestField.values()) {
					projection.put(field.n(), field.n());
				}
				for (RetrievalField field : RetrievalField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	};


	public RetrievalWorker getRetrievalWorker() {
		return new RetrievalWorker();
	}
	/** 
	 * Store access class
	 */
	public class RetrievalWorker {

		private RetrievalWorker() {
		}

		/**
		 *
		 */
		public void upsert() {
			final DistributorDataStore parent = DistributorDataStore.this;

			synchronized(parent) {	

			}
		}

	}

	/**
	 * Query
	 */

	/**
	 * @param projection
	 * @param whereClause
	 * @param whereArgs
	 * @param sortOrder
	 * @return
	 */
	public synchronized Cursor queryRetrieval(String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		return queryRequest(RETRIEVAL_VIEW_NAME, projection, whereClause, whereArgs, sortOrder);
	}
	public synchronized Cursor queryRetrievalReady() {
		this.openRead();
		try {
			return db.rawQuery(RETRIEVAL_STATUS_QUERY, null);
		} catch(SQLiteException ex) {
			logger.error("sql error {}", ex.getLocalizedMessage());
		}
		return null;
	}
	private static final String RETRIEVAL_STATUS_QUERY = 
			RequestStatusQuery(Tables.RETRIEVAL, Tables.RETRIEVAL_DISPOSAL);

	private static final String RETRIEVAL_VIEW_NAME = Tables.RETRIEVAL.n;

	public synchronized Cursor queryRetrievalByKey(String[] projection, 
			final String uuid, final String sortOrder) {
		return queryRequestByUuid(projection, uuid, sortOrder);
	}

	/**
	 * Upsert
	 */

	/**
	 * @param cv
	 * @param status
	 * @return
	 */
	public synchronized long upsertRetrieval(ContentValues cv, DistributorState status) {
		PLogger.STORE_RETRIEVE_DML.trace("upsert retrieval: {} @ {}",
				cv, status);
		final RequestWorker requestor = this.getRetrievalRequestWorker();
		return requestor.upsert(cv, status);
	}

	/**
	 * Update
	 */
	public synchronized long updateRetrievalByKey(long requestId, ContentValues cv, final DistributorState state) {
		final RequestWorker requestor = this.getRetrievalRequestWorker();
		return requestor.updateById(requestId, cv, state);
	}
	public synchronized long updateRetrievalByKey(long requestId, String channel, final DisposalState state) {
		final DisposalWorker worker = this.getRetrievalDisposalWorker();
		return worker.upsertByRequest(requestId, channel, state);
	}

	/**
	 * Update
	 */
	public synchronized int deleteRetrieval(String whereClause, String[] whereArgs) {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int count = db.delete(Tables.RETRIEVAL.n, whereClause, whereArgs);
			final int disposalCount = db.delete(
					Tables.RETRIEVAL_DISPOSAL.n, DISPOSAL_RETRIEVAL_ORPHAN_CONDITION, null);
			logger.trace("Retrieval delete {} {}", count, disposalCount);
			return count;
		} catch (IllegalArgumentException ex) {
			logger.error("delete retrieval {} {}", whereClause, whereArgs);
		}
		return 0;
	}
	public synchronized int deleteRetrievalGarbage() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int expireCount = db.delete(Tables.RETRIEVAL.n, 
					RETRIEVAL_EXPIRATION_CONDITION, getRelativeExpirationTime(RETRIEVAL_DELAY_OFFSET));
			final int disposalCount = db.delete(
					Tables.RETRIEVAL_DISPOSAL.n, 
					DISPOSAL_RETRIEVAL_ORPHAN_CONDITION, null);
			logger.trace("Retrieval garbage {} {}", expireCount, disposalCount);
			return expireCount;
		} catch (IllegalArgumentException ex) {
			logger.error("deleteRetrievalGarbage {}", ex.getLocalizedMessage());
		} catch (SQLiteException ex) {
			logger.error("deleteRetrievalGarbage {}", ex.getLocalizedMessage());
		}

		return 0;
	}
	private static final String DISPOSAL_RETRIEVAL_ORPHAN_CONDITION = new StringBuilder()
	.append(DisposalField.TYPE.q(null)).append('=').append(Tables.RETRIEVAL.cv())
	.append(" AND NOT EXISTS (SELECT * ")
	.append(" FROM ").append(Tables.RETRIEVAL.q())
	.append(" WHERE ").append(DisposalField.REQUEST.q(null))
	.append('=').append(Tables.RETRIEVAL.q()).append(".").append(RequestField._ID.q(null))
	.append(')')
	.toString();

	private static final String RETRIEVAL_EXPIRATION_CONDITION = new StringBuilder()
	.append(RequestField.EXPIRATION.q(null))
	.append('<').append('?')
	.toString();

	private static final long RETRIEVAL_DELAY_OFFSET = 8 * 60 * 60; // 8 hrs in seconds

	/**
	 * purge all records from the retrieval table and cascade to the disposal table.
	 * @return
	 */
	public synchronized int purgeRetrieval() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			db.delete(Tables.RETRIEVAL_DISPOSAL.n, DISPOSAL_PURGE, new String[]{ Tables.RETRIEVAL.qv()});
			return db.delete(Tables.RETRIEVAL.n, null, null);
		} catch (IllegalArgumentException ex) {
			logger.error("purgeRetrieval");
		}
		return 0;
	}


	/**
	 * ===================
	 *  INTEREST
	 * ===================
	 * 
	 * The interest table is for holding local subscription requests.
	 */

	/** Insert method helper */
	protected ContentValues initializeInterestDefaults(ContentValues values) {

		initializeRequestDefaults(values);

		if (!values.containsKey(InterestField.FILTER.n())) {
			values.put(InterestField.FILTER.n(), "");
		}
		return values;
	}

	public enum InterestField  implements TableField {

		FILTER("filter", "TEXT");
		// The rows/tuples wanted.

		// TODO : what about message rates?

		final public TableFieldState impl;

		private InterestField(String name, String type) {
			this.impl = new TableFieldState(name,type);
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

	public static interface InterestConstants {

		public static final String DEFAULT_SORT_ORDER = ""; 
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public static final String PARENT_KEY_REF = null;

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (RequestField field : RequestField.values()) {
					columns.add(field.n());
				}
				for (InterestField field : InterestField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (RequestField field : RequestField.values()) {
					projection.put(field.n(), field.n());
				}
				for (InterestField field : InterestField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	}

	public InterestWorker getInterestWorker(final Cursor pending) {
		return new InterestWorker(pending);
	}
	public InterestWorker getInterestWorker(final AmmoRequest request) {
		return new InterestWorker(request);
	}
	/** 
	 * Store access class
	 */
	public class InterestWorker {

		final public int id;
		final public String topic;
		final public String subtopic;
		final public String auid;
		final public UUID uuid;

		private InterestWorker(final Cursor pending) {

			// For each item in the cursor, ask the content provider to
			// serialize it, then pass it off to the NPS.
			this.id = pending.getInt(pending.getColumnIndex(RequestField._ID.cv()));
			this.topic = pending.getString(pending.getColumnIndex(RequestField.TOPIC.cv()));
			this.subtopic = pending.getString(pending.getColumnIndex(RequestField.SUBTOPIC.cv()));
			this.uuid = UUID.fromString(pending.getString(pending.getColumnIndex(RequestField.UUID.cv())));
			this.auid = pending.getString(pending.getColumnIndex(RequestField.AUID.cv()));
	}

		private InterestWorker(final AmmoRequest request) {
			this.id = -1;
			this.uuid = UUID.randomUUID();
			this.auid = request.uid;
			this.topic = request.topic.asString();
			this.subtopic = (request.subtopic == null) ? Topic.DEFAULT : request.subtopic.asString();
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("\n\t").append("id=[")
			.append(this.id).append(':')
			.append(this.uuid).append(':')
			.append(this.auid).append(']');
			sb.append("\n\t").append("topic=[").append(this.topic).append(':')
			.append(this.subtopic).append(']');
			/*
			if (this.selection != null && this.selection.length() > 0) {
				sb.append("\n\t").append("filter=[").append(this.selection).append(']');
			}
			*/
			return sb.toString();
		}
		/**
		 *
		 */
		public void upsert() {
			final DistributorDataStore parent = DistributorDataStore.this;

			synchronized(parent) {	

			}
		}
	}

	/**
	 * Query
	 */
	public synchronized Cursor queryInterest(String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		return queryRequest(INTEREST_VIEW_NAME, projection, whereClause, whereArgs, sortOrder);
	}

	public synchronized Cursor queryInterestReady() {
		this.openRead();
		try {
			return db.rawQuery(INTEREST_STATUS_QUERY, null);
		} catch(SQLiteException ex) {
			logger.error("sql error {}", ex.getLocalizedMessage());
		}
		return null;
	}
	public synchronized Cursor queryInterestByKey(String[] projection,
			final String topic, final String subtopic, String sortOrder) {
		return queryRequestByTopic(INTEREST_VIEW_NAME, projection, topic, subtopic, sortOrder);
	}

	private static final String INTEREST_STATUS_QUERY = 
			RequestStatusQuery(Tables.INTEREST, Tables.INTEREST_DISPOSAL);

	private static final String INTEREST_VIEW_NAME = Tables.INTEREST.n;

	/**
	 * Upsert
	 */
	public synchronized long upsertInterest(ContentValues cv, DistributorState status) {
		PLogger.STORE_INTEREST_DML.trace("upsert interest: {} @ {}",
				cv, status);
		final RequestWorker requestor = this.getInterestRequestWorker();
		return requestor.upsert(cv, status);
	}


	/**
	 * Update
	 */
	public synchronized long updateInterestByKey(long requestId, ContentValues cv, final DistributorState state) {
		final RequestWorker requestor = this.getInterestRequestWorker();
		return requestor.updateById(requestId, cv, state);
	}
	public synchronized long updateInterestByKey(long requestId, String channel, final DisposalState state) {
		final DisposalWorker worker = this.getInterestDisposalWorker();
		return worker.upsertByRequest(requestId, channel, state);
	}

	/**
	 * Delete
	 */
	public synchronized int deleteInterest(String whereClause, String[] whereArgs) {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int count = db.delete(Tables.INTEREST.n, whereClause, whereArgs);
			logger.trace("Interest delete {} {}", count);
			return count;
		} catch (IllegalArgumentException ex) {
			logger.error("delete interest {} {}", whereClause, whereArgs);
		}
		return 0;
	}
	public synchronized int deleteInterestGarbage() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int expireCount = db.delete(Tables.INTEREST.n, 
					INTEREST_EXPIRATION_CONDITION, 
					getRelativeExpirationTime(INTEREST_DELAY_OFFSET));

			logger.trace("Interest garbage {} {}", new Object[] {expireCount, INTEREST_EXPIRATION_CONDITION} );
			return expireCount;
		} catch (IllegalArgumentException ex) {
			logger.error("deleteInterestGarbage {}", ex.getLocalizedMessage());
		} catch (SQLiteException ex) {
			logger.error("deleteInterestGarbage {}", ex.getLocalizedMessage());
		}
		return 0;
	}

	private static final String INTEREST_EXPIRATION_CONDITION = new StringBuilder()
	.append(RequestField.EXPIRATION.n())
	.append('<').append('?')
	.toString();

	private static final long INTEREST_DELAY_OFFSET = 365 * 24 * 60 * 60; // 1 yr in seconds

	/**
	 * purge all records from the interest table and cascade to the disposal table.
	 * @return
	 */
	public synchronized int purgeInterest() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			db.delete(Tables.INTEREST_DISPOSAL.n, DISPOSAL_PURGE, new String[]{ Tables.INTEREST.qv()});
			return db.delete(Tables.INTEREST.n, null, null);
		} catch (IllegalArgumentException ex) {
			logger.error("purgeInterest");
		}
		return 0;
	}



	/**
	 * ===================
	 *  DISPOSAL
	 * ===================
	 * 
	 * The channel disposal table is for holding 
	 * request disposition status for each channel.
	 * Once the message has been sent acknowledgments 
	 * will produce multiple additional recipient messages
	 * which are placed in the recipient table.
	 */


	/**
	 * An association between channel and request.
	 * This 
	 */
	public enum DisposalField  implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CHANNEL("channel", "TEXT"),
		// The name of the channel over which the message could-be/was sent

		REQUEST("request", "INTEGER"),
		// The _id of the parent request

		TYPE("type", "INTEGER"),
		// Meaning the parent type: interest, retrieval, postal, publish
		// This is redundant on the Request tuple, it is provided for performance.

		STATE("state", "INTEGER");
		// State of the request in the channel
		// see ChannelDisposalState for valid values

		final public TableFieldState impl;

		private DisposalField(String name, String type) {
			this.impl = new TableFieldState(name,type);
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

	public static interface DisposalConstants {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public static final String PARENT_KEY_REF = new StringBuilder()
		.append(" FOREIGN KEY(").append(DisposalField.REQUEST.n()).append(")")
		.append(" REFERENCES ").append(Tables.REQUEST.n)
		.append("(").append(RequestField._ID.n()).append(")")
		.append(" ON DELETE CASCADE ")
		.append(",")
		.append(" FOREIGN KEY(").append(DisposalField.CHANNEL.n()).append(")")
		.append(" REFERENCES ").append(Tables.CHANNEL.n)
		.append("(").append(ChannelField.NAME.n()).append(")")
		.append(" ON UPDATE CASCADE ")
		.append(" ON DELETE CASCADE ")
		.toString();

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (DisposalField field : DisposalField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (DisposalField field : DisposalField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	}


	/** 
	 * Store access class
	 */
	public class DisposalWorker {
		protected final SQLiteDatabase db;
		protected final Tables request;
		protected final Tables disposal;

		private final String DISPOSAL_STATUS_QUERY;
		private final String DISPOSAL_UPDATE_CLAUSE;

		private DisposalWorker(SQLiteDatabase db, Tables request, Tables disposal) {
			this.db = db;
			this.request = request;
			this.disposal = disposal;

			this.DISPOSAL_STATUS_QUERY = new StringBuilder()
			.append(" SELECT * ")
			.append(" FROM ").append(this.disposal.q()).append(" AS d ")
			.append(" WHERE ").append(DisposalField.TYPE.q("d")).append("=? ")
			.append("   AND ").append(DisposalField.REQUEST.q("d")).append("=? ")
			.toString();

			this.DISPOSAL_UPDATE_CLAUSE = new StringBuilder()
			.append(DisposalField.REQUEST.q(null)).append("=?")
			.append(" AND ")
			.append(DisposalField.CHANNEL.q(null)).append("=?").toString();
		}

		public Cursor query(String[] projection, String whereClause,
				String[] whereArgs, String sortOrder) {
			synchronized (DistributorDataStore.this) {
				try {
					final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

					qb.setTables(this.disposal.n);
					qb.setProjectionMap(DisposalConstants.PROJECTION_MAP);

					// Get the database and run the query.
					return qb.query(this.db, projection, whereClause, whereArgs, null, null,
							(!TextUtils.isEmpty(sortOrder)) ? sortOrder
									: DisposalConstants.DEFAULT_SORT_ORDER);
				} catch (IllegalArgumentException ex) {
					logger.error("query disposal {} {}", whereClause, whereArgs);
				}
				return null;
			}
		}


		/**
		 * Get the state of the channels for the given request
		 * 
		 * @param parent
		 * @param type
		 * @return
		 */
		public Cursor queryByParent(int parent) {
			synchronized(DistributorDataStore.this) {	
				try {
					logger.trace("disposal ready {} {} {}", new Object[]{DISPOSAL_STATUS_QUERY, parent} );
					return db.rawQuery(DISPOSAL_STATUS_QUERY, new String[]{String.valueOf(parent)});
				} catch(SQLiteException ex) {
					logger.error("sql error {}", ex.getLocalizedMessage());
				}
				return null;
			}
		}

		/**
		 * Upsert
		 */
		private long[] upsertByRequest(final long requestId, final DistributorState status) {
			synchronized(DistributorDataStore.this) {	
				try {
					final long[] idArray = new long[status.size()];
					int ix = 0;
					for (Entry<String,DisposalState> entry : status.entrySet()) {
						idArray[ix] = upsertByRequest(requestId, entry.getKey(), entry.getValue());
						ix++;
					}
					return idArray;
				} catch (IllegalArgumentException ex) {
					logger.error("upsert disposal by parent {} {} {}", new Object[]{requestId, status});
				}
				return null;
			}
		}
		private long upsertByRequest(long requestId, String channel, DisposalState status) {
			synchronized(DistributorDataStore.this) {	
				Cursor cursor = null;
				try {

					final ContentValues cv = new ContentValues();
					cv.put(DisposalField.REQUEST.cv(), requestId);
					cv.put(DisposalField.CHANNEL.cv(), channel);
					cv.put(DisposalField.STATE.cv(), (status == null) ? DisposalState.PENDING.o : status.o);

					final String requestIdStr = String.valueOf(requestId);
					final int updateCount = this.db.update(this.disposal.n, cv, 
							DISPOSAL_UPDATE_CLAUSE, new String[]{requestIdStr , channel } );
					if (updateCount > 0) {
						cursor = this.db.query(this.disposal.n, new String[]{DisposalField._ID.n()}, 
								DISPOSAL_UPDATE_CLAUSE, new String[]{requestIdStr, channel },
								null, null, null);
						final int rowCount = cursor.getCount();
						if (rowCount > 1) {
							logger.error("you have a duplicates {} {}", rowCount, cv);
						}
						cursor.moveToFirst();
						final long key = cursor.getInt(0); // we only asked for one column so it better be it.
						cursor.close();
						return key;
					}
					return this.db.insert(this.disposal.n, DisposalField.TYPE.n(), cv);
				} catch (IllegalArgumentException ex) {
					logger.error("upsert disposal {} {} {} {}", new Object[]{requestId, channel, status});
				} finally {
					if (cursor != null) cursor.close();
				}
				return 0;
			}

		}
	}

	public DisposalWorker getPostalDisposalWorker() {
		return new DisposalWorker(this.db, Tables.POSTAL, Tables.POSTAL_DISPOSAL);
	}

	public DisposalWorker getRetrievalDisposalWorker() {
		return new DisposalWorker(this.db, Tables.RETRIEVAL, Tables.RETRIEVAL_DISPOSAL);
	}

	public DisposalWorker getInterestDisposalWorker() {
		return new DisposalWorker(this.db, Tables.INTEREST, Tables.INTEREST_DISPOSAL);
	}



	/**
	 * ===================
	 *  RECIPIENT
	 * ===================
	 * 
	 * The recipient table extends the disposal table.
	 * Once the message has been sent any acknowledgments will produce 
	 * multiple additional recipient messages.
	 */
	public enum RecipientField  implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		REQUEST("request", "INTEGER"),
		// The _ID of the parent Request

		DISPOSAL("type", "INTEGER"),
		// Meaning the parent type: interest, retrieval, postal, publish

		STATE("state", "INTEGER");
		// State of the request on the channel

		final public TableFieldState impl;

		private RecipientField(String name, String type) {
			this.impl = new TableFieldState(name,type);
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

	public static interface RecipientConstants {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public static final String PARENT_KEY_REF = new StringBuilder()
		.append(" FOREIGN KEY(").append(RecipientField.DISPOSAL.n()).append(")")
		.append(" REFERENCES ").append(Tables.POSTAL_DISPOSAL.n)
		.append("(").append(DisposalField._ID.n()).append(")")
		.append(" ON DELETE CASCADE ")
		.toString();

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (RecipientField field : RecipientField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (RecipientField field : RecipientField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	}


	public RecipientWorker getRecipientWorker() {
		return new RecipientWorker();
	}
	/** 
	 * Store access class
	 */
	public class RecipientWorker {

		private RecipientWorker() {
		}

		/**
		 *
		 */
		public void upsert() {
			final DistributorDataStore parent = DistributorDataStore.this;

			synchronized(parent) {	

			}
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

		NAME("name", "TEXT"),
		// The name of the channel, must match policy channel name

		STATE("state", "INTEGER");
		// The channel state (active inactive)

		final public TableFieldState impl;

		private ChannelField(String name, String type) {
			this.impl = new TableFieldState(name,type);
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

		public static final String PARENT_KEY_REF = null;

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


	public ChannelWorker getChannelWorker() {
		return new ChannelWorker();
	}
	/** 
	 * Store access class
	 */
	public class ChannelWorker {

		private ChannelWorker() {
		}

		/**
		 *
		 */
		public void upsert() {
			final DistributorDataStore parent = DistributorDataStore.this;

			synchronized(parent) {	

			}
		}

	}
	/**
	 * Query
	 */

	public synchronized Cursor queryChannel(String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Tables.CHANNEL.n);
			qb.setProjectionMap(ChannelConstants.PROJECTION_MAP);

			// Get the database and run the query.
			return qb.query(this.db, projection, whereClause, whereArgs, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: ChannelConstants.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query channel {} {}", whereClause, whereArgs);
		}
		return null;
	}

	static final private String REQUEST_UPDATE_CLAUSE = new StringBuilder()
	.append(RequestField.TOPIC.q(null)).append("=?")
	.append(" AND ")
	.append(RequestField.SUBTOPIC.q(null)).append("=?")
	.append(" AND ")
	.append(RequestField.PROVIDER.q(null)).append("=?")
	.toString();

	static final private String REQUEST_UPDATE_CLAUSE_KEY = new StringBuilder()
	.append(RequestField.UUID.q(null)).append("=?").toString();

	static final private String ROWID_CLAUSE = "_rowid_=?";

	/**
	 * Upsert
	 */

	public synchronized long upsertChannelByName(String channel, ChannelState status) {
		try {
			final ContentValues cv = new ContentValues();		
			cv.put(ChannelField.STATE.cv(), status.cv());
			PLogger.STORE_CHANNEL_DML.trace("upsert channel: {} @ {}",
					channel, status);

			final int updateCount = this.db.update(Tables.CHANNEL.n, cv, 
					CHANNEL_UPDATE_CLAUSE, new String[]{ channel } );
			if (updateCount > 0) return 0;

			cv.put(ChannelField.NAME.cv(), channel);
			db.insert(Tables.CHANNEL.n, ChannelField.NAME.n(), cv);
		} catch (IllegalArgumentException ex) {
			logger.error("upsert channel {} {}", channel, status);
		}
		return 0;
	}
	static final private String CHANNEL_UPDATE_CLAUSE = new StringBuilder()
	.append(ChannelField.NAME.q(null)).append("=?").toString();

	/**
	 * These are related to upsertChannelByName() inasmuch as it 
	 * resets the failed state to pending.
	 * @param name
	 * @return the number of failed items updated
	 */
	static final private ContentValues DISPOSAL_PENDING_VALUES;
	static {
		DISPOSAL_PENDING_VALUES = new ContentValues();
		DISPOSAL_PENDING_VALUES.put(DisposalField.STATE.cv(), DisposalState.PENDING.o); 
	}

	/**
	 * Update
	 */
	/**
	 * Delete
	 */


	/**
	 * When a channel is deactivated all of its subscriptions  
	 * will need to be re-done on re-connect.
	 * Retrievals and postals won't have this problem,
	 * TODO unless they are queued.
	 * @param channel
	 * @return
	 */
	public synchronized int deactivateDisposalStateByChannel(String channel) {
		try {
			this.db.update(Tables.POSTAL_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_DEACTIVATE_CLAUSE, new String[]{ channel } );

			this.db.update(Tables.RETRIEVAL_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_DEACTIVATE_CLAUSE, new String[]{ channel } );

			this.db.update(Tables.INTEREST_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_DEACTIVATE_CLAUSE, new String[]{ channel } );
		} catch (IllegalArgumentException ex) {
			logger.error("deactivateDisposalStateByChannel {} ", channel);
		}
		return 0;
	}	
	static final private String DISPOSAL_DEACTIVATE_CLAUSE = new StringBuilder()
	.append(DisposalField.CHANNEL.q(null)).append("=?")
	.append(" AND ")
	.append(DisposalField.STATE.q(null))
	.append(" NOT IN ( ").append(DisposalState.BAD.q()).append(')')
	.toString();

	/**
	 * When a channel is activated nothing really needs to be done.
	 * This method is provided as a place holder.
	 * 
	 * @param channel
	 * @return
	 */
	public int activateDisposalStateByChannel(String channel) {
		return -1;
	}

	/** 
	 * When a channel is repaired it any failed requests may be retried.
	 * @param channel
	 * @return
	 */
	public synchronized int repairDisposalStateByChannel(String channel) {
		try {
			this.db.update(Tables.POSTAL_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_REPAIR_CLAUSE, new String[]{ channel } );

			this.db.update(Tables.RETRIEVAL_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_REPAIR_CLAUSE, new String[]{ channel } );

			this.db.update(Tables.INTEREST_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_REPAIR_CLAUSE, new String[]{ channel } );
		} catch (IllegalArgumentException ex) {
			logger.error("repairDisposalStateByChannel {}", channel);
		}
		return 0;
	}
	static final private String DISPOSAL_REPAIR_CLAUSE = new StringBuilder()
	.append(DisposalField.CHANNEL.q(null)).append("=?")
	.append(" AND ")
	.append(DisposalField.STATE.q(null))
	.append(" IN ( ").append(DisposalState.BAD.q()).append(')')
	.toString();


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

		this.applCacheInterestDir = new File(this.applCacheDir, "interest");
		this.applCacheInterestDir.mkdir();

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
	static private String[] getRelativeExpirationTime(long delay) {
		final long absTime = System.currentTimeMillis() - (delay * 1000);
		return new String[]{String.valueOf(absTime)}; 
	}

	private static final String DISPOSAL_PURGE = new StringBuilder()
	.append(DisposalField.TYPE.q(null))
	.append('=').append('?')
	.toString();

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


	// ========= INTEREST : DELETE ================

	public final File applDir;
	public final File applCacheDir;
	public final File applCachePostalDir;
	public final File applCacheRetrievalDir;
	public final File applCacheInterestDir;
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



	protected class DataStoreHelper extends SQLiteOpenHelper {
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

			//db.beginTransaction();
			String sqlCreateRef = null; 
			// so you have something when you catch

			/**
			 *  ===== PRESENCE
			 */
			try {
				final Tables table = Tables.PRESENCE;

				final StringBuilder createSql = new StringBuilder()
				.append(" CREATE TABLE ")
				.append(table.q())
				.append(" ( ").append(ddl(PresenceField.values())).append(')')
				.append(';');

				sqlCreateRef = createSql.toString();
				PLogger.STORE_DDL.trace("{}", sqlCreateRef);
				db.execSQL(sqlCreateRef);

			} catch (SQLException ex) {
				logger.error("failed create PRESENCE {} {}",
						sqlCreateRef.toString(),
						ex.getLocalizedMessage());
				return;
			}

			/**
			 *  ===== CAPABILITY
			 */
			try {	
				final Tables table = Tables.CAPABILITY;

				final StringBuilder createSql = new StringBuilder()
				.append(" CREATE TABLE ")
				.append(table.q())
				.append(" ( ")
				.append(ddl(RequestField.values())).append(',')
				.append(ddl(CapabilityField.values())).append(')')
				.append(';');

				sqlCreateRef = createSql.toString();
				PLogger.STORE_DDL.trace("{}", sqlCreateRef);
				db.execSQL(sqlCreateRef);

			} catch (SQLException ex) {
				logger.error("failed create CAPABILITY {} {}",
						sqlCreateRef, ex.getLocalizedMessage());
				return;
			}

			/**
			 *  ===== CHANNEL
			 */
			try {
				final Tables table = Tables.CHANNEL;

				final StringBuilder createSql = new StringBuilder()
				.append("CREATE TABLE ")
				.append(table.q())
				.append(" ( ").append(ddl(ChannelField.values())).append(')')
				.append(';');

				sqlCreateRef = createSql.toString();
				PLogger.STORE_DDL.trace("{}", sqlCreateRef);
				db.execSQL(sqlCreateRef);

			} catch (SQLException ex) {
				logger.error("create CHANNEL {} {}",
						sqlCreateRef, ex.getLocalizedMessage());
				return;
			}



			/**
			 *  ===== POSTAL
			 */
			try {	
				final Tables request = Tables.POSTAL;
				final Tables disposal = Tables.POSTAL_DISPOSAL;
				final TableField[] fields = PostalField.values();

				final StringBuilder createRequestSql = 
						new StringBuilder()
				.append("CREATE TABLE ")
				.append(request.q())
				.append(" ( ")
				.append(ddl(RequestField.values())).append(',')
				.append(ddl(fields)).append(')')
				.append(';');
				sqlCreateRef = createRequestSql.toString();
				PLogger.STORE_DDL.trace("{}", sqlCreateRef);
				db.execSQL(sqlCreateRef);

				final StringBuilder createDisposalSql = new StringBuilder()
				.append(" CREATE TABLE ")
				.append(disposal.q())
				.append(" ( ").append(ddl(DisposalField.values())).append(')')
				.append(';');

				sqlCreateRef = createDisposalSql.toString();
				PLogger.STORE_DDL.trace("{}", sqlCreateRef);
				db.execSQL(sqlCreateRef);

				db.execSQL(new StringBuilder()
				.append("CREATE UNIQUE INDEX ") 
				.append(disposal.qIndex())
				.append(" ON ").append(disposal.q())
				.append(" ( ").append(DisposalField.REQUEST.q(null))
				.append(" , ").append(DisposalField.CHANNEL.q(null))
				.append(" ) ")
				.toString() );

			} catch (SQLException ex) {
				logger.error("create REQUEST {} {}",
						sqlCreateRef, ex.getLocalizedMessage());
				return;
			}

			/**
			 *  ===== RETRIEVAL
			 */
			try {	
				final Tables request = Tables.RETRIEVAL;
				final Tables disposal = Tables.RETRIEVAL_DISPOSAL;
				final TableField[] fields = RetrievalField.values();

				final StringBuilder createRequestSql = 
						new StringBuilder()
				.append("CREATE TABLE ")
				.append(request.q())
				.append(" ( ")
				.append(ddl(RequestField.values())).append(',')
				.append(ddl(fields)).append(')')
				.append(';');
				sqlCreateRef = createRequestSql.toString();
				PLogger.STORE_DDL.trace("{}", sqlCreateRef);
				db.execSQL(sqlCreateRef);

				final StringBuilder createDisposalSql = new StringBuilder()
				.append(" CREATE TABLE ")
				.append(disposal.q())
				.append(" ( ").append(ddl(DisposalField.values())).append(')')
				.append(';');

				sqlCreateRef = createDisposalSql.toString();
				PLogger.STORE_DDL.trace("{}", sqlCreateRef);
				db.execSQL(sqlCreateRef);

				db.execSQL(new StringBuilder()
				.append("CREATE UNIQUE INDEX ") 
				.append(disposal.qIndex())
				.append(" ON ").append(disposal.q())
				.append(" ( ").append(DisposalField.REQUEST.q(null))
				.append(" , ").append(DisposalField.CHANNEL.q(null))
				.append(" ) ")
				.toString() );

			} catch (SQLException ex) {
				logger.error("create REQUEST {} {}",
						sqlCreateRef, ex.getLocalizedMessage());
				return;
			}
			/**
			 *  ===== INTEREST
			 */
			try {	
				final Tables request = Tables.INTEREST;
				final Tables disposal = Tables.INTEREST_DISPOSAL;
				final TableField[] fields = InterestField.values();

				final StringBuilder createRequestSql = 
						new StringBuilder()
				.append("CREATE TABLE ")
				.append(request.q())
				.append(" ( ")
				.append(ddl(RequestField.values())).append(',')
				.append(ddl(fields)).append(')')
				.append(';');
				sqlCreateRef = createRequestSql.toString();
				PLogger.STORE_DDL.trace("{}", sqlCreateRef);
				db.execSQL(sqlCreateRef);

				final StringBuilder createDisposalSql = new StringBuilder()
				.append(" CREATE TABLE ")
				.append(disposal.q())
				.append(" ( ").append(ddl(DisposalField.values())).append(')')
				.append(';');

				sqlCreateRef = createDisposalSql.toString();
				PLogger.STORE_DDL.trace("{}", sqlCreateRef);
				db.execSQL(sqlCreateRef);

				db.execSQL(new StringBuilder()
				.append("CREATE UNIQUE INDEX ") 
				.append(disposal.qIndex())
				.append(" ON ").append(disposal.q())
				.append(" ( ").append(DisposalField.REQUEST.q(null))
				.append(" , ").append(DisposalField.CHANNEL.q(null))
				.append(" ) ")
				.toString() );

			} catch (SQLException ex) {
				logger.error("create REQUEST {} {}",
						sqlCreateRef, ex.getLocalizedMessage());
				return;
			}

			//db.endTransaction();
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
			// Examine or otherwise prepare the database
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



	// ===========================================================
	// Enumerated types in the tables.
	// ===========================================================


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
	 * The states of a request.
	 * The DISTRIBUTE state indicates that the
	 * total state is an aggregate of the distribution
	 * of the request across the relevant channels.
	 * see the ChannelStatus
	 * 
	 * NEW : either all pending or none
	 * DISTRIBUTE : the request is being actively processed
	 * EXPIRED : the expiration time of the request has arrived
	 * COMPLETE : the distribution rule has been fulfilled
	 * INCOMPLETE : the distribution rule cannot be completely fulfilled
	 */
	public enum DisposalTotalState {
		NEW(0x01, "new"),
		DISTRIBUTE(0x02, "distribute"),
		EXPIRED(0x04, "expired"),
		COMPLETE(0x08, "complete"),
		INCOMPLETE(0x10, "incomplete"),
		FAILED(0x20, "failed");

		final public int o;
		final public String t;

		private DisposalTotalState(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}
		public String q() {
			return new StringBuilder().append("'").append(this.o).append("'").toString();
		}
		public String cv() {
			return String.valueOf(this.o);
		}
		static public DisposalTotalState getInstance(String ordinal) {
			return DisposalTotalState.values()[Integer.parseInt(ordinal)];
		}
		static public DisposalTotalState getInstanceById(int o) {
			for (DisposalTotalState candidate : DisposalTotalState.values()) {
				if (candidate.o == o) return candidate;
			}
			return null;
		}

	};

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
	/**
	 * The states of a request over a particular channel.
	 * Some of these states are used by the DisposalChannelField.STATE
	 * and some by the DisposalPresenceField.STATE.
	 * 
	 * These aggregate to give the RequestDisposal.DISTRIBUTE value.
	 */
	public enum DisposalState {
		NEW      (0x0001, "new"),
		REJECTED (0x0002, "rejected"),   // channel is temporarily rejecting req (probably down)
		BAD      (0x0080, "bad"),        // message is problematic, don't try again
		PENDING  (0x0004, "pending"),    // cannot send but not bad
		QUEUED   (0x0008, "queued"),     // message in channel queue
		BUSY     (0x0100, "full"),       // channel queue was busy (usually full queue)
		SENT     (0x0010, "sent"),       // message is sent synchronously
		TOLD     (0x0020, "told"),       // message sent asynchronously
		DELIVERED(0x0040, "delivered"),  // async message acknowledged
		RECEIVED (0x0200, "received"),   // async message received
		;

		final public int o;
		final public String t;

		private DisposalState(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}
		@Override
		public String toString() {
			return this.t;
		}
		public String q() {
			return new StringBuilder().append("'").append(this.o).append("'").toString();
		}
		public String cv() {
			return String.valueOf(this.o);
		}
		static public DisposalState getInstance(String ordinal) {
			return DisposalState.values()[Integer.parseInt(ordinal)];
		}
		/**
		 * This method indicates if the goal has been met.
		 * Note that false does not mean the goal will not be reachable
		 * it only means that it has not yet been reached.
		 */
		public boolean goalReached(boolean goalCondition) {
			switch (this) {
			case QUEUED:
			case SENT:
			case DELIVERED:
				if (goalCondition == true) return true;
				break;
			case PENDING:
			case REJECTED:
			case BUSY:
			case BAD:
				if (goalCondition == false) return true;
				break;
			}
			return false;
		}
		public DisposalState and(boolean clauseSuccess) {
			if (clauseSuccess) return this;
			return DisposalState.DELIVERED;
		}

		public int checkAggregate(final int aggregate) {
			return this.o & aggregate;
		}

		public int aggregate(final int aggregate) {
			return this.o | aggregate;
		}

		static public DisposalState getInstanceById(int o) {
			for (DisposalState candidate : DisposalState.values()) {
				if (candidate.o == o) return candidate;
			}
			return null;
		}
	};

	/**
	 * Indicates how delayed messages are to be prioritized.
	 * once : indicates that only one copy should be kept (the latest)
	 * temporal : only things within a particular time span
	 * quantity : only a certain number of items
	 */
	public enum ContinuityType {
		ONCE(1, "once"),
		TEMPORAL(2, "temporal"),
		QUANTITY(3, "quantity");

		final public int o;
		final public String t;

		private ContinuityType(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}
		/**
		 * Produce string of the form...
		 * '<field-ordinal-value>';
		 */
		public String quote() {
			return new StringBuilder().append("'").append(this.o).append("'").toString();
		}
		public String cv() {
			return String.valueOf(this.o);
		}
		static public ContinuityType getInstance(String ordinal) {
			return ContinuityType.values()[Integer.parseInt(ordinal)];
		}
	};

	/**
	 * Indicates message priority.
	 * 
	 */
	public enum PriorityType {
		FLASH(0x80, "FLASH"),
		URGENT(0x40, "URGENT"),
		IMPORTANT(0x20, "IMPORTANT"),
		NORMAL(0x10, "NORMAL"),
		BACKGROUND(0x08, "BACKGROUND");

		final public int o;
		final public String t;

		private PriorityType(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}
		/**
		 * Produce string of the form...
		 * '<field-ordinal-value>';
		 */
		public String quote() {
			return new StringBuilder().append("'").append(this.o).append("'").toString();
		}
		public String cv() {
			return String.valueOf(this.o);
		}
		static public PriorityType getInstance(String ordinal) {
			return PriorityType.values()[Integer.parseInt(ordinal)];
		}
		static public PriorityType getInstanceById(int o) {
			for (PriorityType candidate : PriorityType.values()) {
				final int lower = candidate.o;
				final int upper = lower << 1;
				if (upper > o && lower >= o) return candidate;
			}
			return null;
		}
		public CharSequence toString(int priorityId) {
			final StringBuilder sb = new StringBuilder().append(this.o);
			if (priorityId > this.o) {
				sb.append("+").append(priorityId-this.o);
			}
			return sb.toString();
		}
	};

	/** 
	 * Description: Indicates if the uri indicates a table or whether the data has been preserialized.
	 *     DIRECT : the serialized data is found in the data field (or a suitable file).
	 *     INDIRECT : the serialized data is obtained from the named uri.
	 *     DEFERRED : the same as INDIRECT but the serialization doesn't happen until the data is sent.
	 * <P>Type: EXCLUSIVE</P> 
	 */
	public enum SeriaizeType {
		DIRECT(1, "DIRECT"),
		INDIRECT(2, "INDIRECT"),
		DEFERRED(3, "DEFERRED");

		final public int o;
		final public String t;

		private SeriaizeType(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}
		/**
		 * Produce string of the form...
		 * '<field-ordinal-value>';
		 */
		public String quote() {
			return new StringBuilder().append("'").append(this.o).append("'").toString();
		}
		public String cv() {
			return String.valueOf(this.o);
		}
		static public SeriaizeType getInstance(String ordinal) {
			return SeriaizeType.values()[Integer.parseInt(ordinal)];
		}
	};


}

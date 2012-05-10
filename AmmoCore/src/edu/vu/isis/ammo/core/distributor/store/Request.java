package edu.vu.isis.ammo.core.distributor.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.text.TextUtils;
import edu.vu.isis.ammo.api.type.SerialMoment;
import edu.vu.isis.ammo.core.distributor.Dispersal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.store.Channel.ChannelField;
import edu.vu.isis.ammo.core.distributor.store.Channel.ChannelState;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalField;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalState;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalWorker;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableField;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableFieldState;

public class Request {
	private static final Logger logger = LoggerFactory.getLogger("class.store.request");
	
	/**
	 * ===========================
	 *  REQUEST
	 * ===========================
	 */
	


	// ===========================================================
	// Enumerated types in the tables.
	// ===========================================================

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


		/**
		 * FIXME
		 * How to combine the policy and application priority?
		 * 
		 * @param policyPriority
		 * @param applPriority
		 * @return
		 */
		static public int aggregatePriority(int policyPriority, int applPriority) {
			return policyPriority; //  + applPriority;
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



	/**
	 * The Request table holds application requests.
	 * These requests are to express subscribe in data of certain types 
	 * or to announce the presence of information of a certain type.
	 * The request can apply to the past or the future.
	 *
	 */

	public static ContentValues initializeDefaults(ContentValues values) {
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
			values.put(RequestField.DISPOSITION.n(), DisposalState.PENDING.o);
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

		return values;
	}


	public enum RequestField implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		UUID("uuid", "TEXT UNIQUE"),
		// This is a unique identifier for the request
		// It is used to look up the appropriate provider

		CREATED("created", "INTEGER"),
		// When the request was made

		MODIFIED("modified", "INTEGER"),
		// When the request was last modified

		TOPIC("topic", "TEXT"),
		// This along with the cost is used to decide how to deliver the specific object.

		SUBTOPIC("subtopic", "TEXT"),
		// This is used in conjunction with topic. 
		// It can be used to identify a recipient, a group, a target, etc.

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

		EXPIRATION("expiration", "INTEGER");
		// Time-stamp at which point the request 
		// becomes stale and can be discarded.

		final public TableFieldState impl;

		private RequestField(String name, String type) {
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
	};

	public static interface RequestConstants {

		public static final String DEFAULT_SORT_ORDER = 
				new StringBuilder().append(RequestField.MODIFIED.n()).append(" DESC ").toString();
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public static final String FOREIGN_KEY = null;

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


	public static RequestWorker getPostalWorker(final DistributorDataStore store) {
		return new RequestWorker(store, Tables.POSTAL, Tables.POSTAL_DISPOSAL);
	}

	public static RequestWorker getSubscribeWorker(final DistributorDataStore store) {
		return new RequestWorker(store, Tables.SUBSCRIBE, Tables.SUBSCRIBE_DISPOSAL);
	}

	public static RequestWorker getRetrievalWorker(final DistributorDataStore store) {
		return new RequestWorker(store, Tables.RETRIEVAL, Tables.RETRIEVAL_DISPOSAL);
	}
	/** 
	 * Store access class
	 */
	static public class RequestWorker {

		protected final DistributorDataStore store;
		protected final SQLiteDatabase db;
		protected final Tables request;
		protected final Tables disposal;

		protected RequestWorker(final DistributorDataStore store, final Tables request, final Tables disposal) {
			this.store = store;
			this.db = store.db;
			this.request = request;
			this.disposal = disposal;
		}

		/**
		 * Update
		 */
		protected long updateById(long requestId, ContentValues cv, Dispersal state) {
			synchronized (this.store) {
				if (state == null && cv == null) return -1;

				if (cv == null) cv = new ContentValues();

				this.db.beginTransaction();
				if (state != null) {
					final DisposalWorker disposal = new DisposalWorker(this.store, this.request, this.disposal);
					disposal.upsertByRequest(requestId, state);
					cv.put(RequestField.DISPOSITION.n(), state.aggregate().cv());
				}	
				try {
					final String whereClause = new StringBuilder()
					.append(RequestField._ID.q(null)).append("=?")
					.toString();

					final String[] whereArgs = new String[]{ String.valueOf(requestId) };

					long count = this.db.update(this.request.n, cv, whereClause, whereArgs);
					this.db.setTransactionSuccessful();
					return count;

				} catch (IllegalArgumentException ex) {
					logger.error("updateRequestById {} {}", requestId, cv);
				} finally {
					this.db.endTransaction();
				}

				return 0;
			}
		}

	}

	@SuppressWarnings("unused")
	static final private String REQUEST_UUID_CLAUSE = new StringBuilder()
	.append(RequestField.UUID.q(null)).append("=?").toString();


	/**
	 * This builds a query which returns rows 
	 * for which work could still be performed.
	 * This is the case if *any* of the channels is
	 * still marked as pending.
	 * 
	 * @param request
	 * @param disposal
	 * @return
	 */
	public static String statusQuery(Tables request, Tables disposal) {
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

	public static Cursor query(final DistributorDataStore store,
			String rel, 
			String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		synchronized (store) {
		try {
			store.openRead();
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(rel);

			// Get the database and run the query.
			final SQLiteDatabase db = store.helper.getReadableDatabase();
			return qb.query(db, projection, whereClause, whereArgs, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: RequestConstants.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query request {} {}", whereClause, whereArgs);
		}
		return null;
		}
	}

	public static Cursor queryByTopic(final DistributorDataStore store,
			String rel, String[] projection,
			final String topic, final String subtopic, String sortOrder, String defaultSortOrder) {
		synchronized (store) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(rel);

			// Get the database and run the query.
			final SQLiteDatabase db = store.helper.getReadableDatabase();
			return qb.query(db, projection, REQUEST_TOPIC_QUERY, new String[]{topic, subtopic}, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder : defaultSortOrder);
		} catch (IllegalArgumentException ex) {
			logger.error("query subscribe by key {} [{}:{}]", new Object[]{ projection, topic, subtopic });
		}
		return null;
		}
	}
	static private final String REQUEST_TOPIC_QUERY = new StringBuilder()
	.append(RequestField.TOPIC.q(null)).append("=?")
	.append(" AND ")
	.append(RequestField.SUBTOPIC.q(null)).append("=?")
	.toString();


	static final String REQUEST_EXPIRATION_CONDITION = new StringBuilder()
	.append(RequestField.EXPIRATION.q(null))
	.append('<').append('?')
	.toString();

}

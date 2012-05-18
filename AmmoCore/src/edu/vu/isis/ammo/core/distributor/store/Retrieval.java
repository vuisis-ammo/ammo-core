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
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.text.TextUtils;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.Dispersal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalField;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalState;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalWorker;
import edu.vu.isis.ammo.core.distributor.store.Request.ContinuityType;
import edu.vu.isis.ammo.core.distributor.store.Request.RequestConstants;
import edu.vu.isis.ammo.core.distributor.store.Request.RequestField;
import edu.vu.isis.ammo.core.distributor.store.Request.RequestWorker;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableField;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableFieldState;

public class Retrieval {
	private static final Logger logger = LoggerFactory.getLogger("class.store.subscribe");


	static public void onCreate(SQLiteDatabase db) {
		String sqlCreateRef = null; 

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
			.append(TableHelper.ddl(RequestField.values())).append(',')
			.append(TableHelper.ddl(fields)).append(')')
			.append(';');
			sqlCreateRef = createRequestSql.toString();
			PLogger.STORE_DDL.trace("{}", sqlCreateRef);
			db.execSQL(sqlCreateRef);

			final StringBuilder createDisposalSql = new StringBuilder()
			.append(" CREATE TABLE ")
			.append(disposal.q())
			.append(" ( ").append(TableHelper.ddl(DisposalField.values())).append(',')
			.append(Disposal.DISPOSAL_FOREIGN_KEY(request))
			.append(')')
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
	}

	/**
	 * ===========================
	 *  RETRIEVAL
	 * ===========================
	 * 
	 * The retrieval table is for holding retrieval requests.
	 */


	/** Insert method helper */
	protected static ContentValues initializeDefaults(ContentValues values) {
		final Long now = Long.valueOf(System.currentTimeMillis());

		Request.initializeDefaults(values);

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

	/*
	public RetrievalWorker getRetrievalWorker() {
		return new RetrievalWorker();
	}
	 */
	/** 
	 * Store access class
	 */
	public class RetrievalWorker extends RequestWorker {

		private RetrievalWorker(final DistributorDataStore store, AmmoRequest ar, AmmoService svc) {
			super(store, Tables.RETRIEVAL, Tables.RETRIEVAL_DISPOSAL);
		}

		private RetrievalWorker(final DistributorDataStore store, Cursor pending, AmmoService svc) {
			super(store, Tables.RETRIEVAL, Tables.RETRIEVAL_DISPOSAL);
		}

		/**
		 *
		 */
		public void upsert(final DistributorDataStore store) {

			synchronized(store) {	

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
	public static Cursor query(final DistributorDataStore store, String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		synchronized (store) {
		return Request.query(store, RETRIEVAL_VIEW_NAME, projection, whereClause, whereArgs, sortOrder);
		}
	}
	public static Cursor queryReady(final DistributorDataStore store) {
		synchronized (store) {
		store.openRead();
		try {
			return store.db.rawQuery(RETRIEVAL_STATUS_QUERY, null);
		} catch(SQLiteException ex) {
			logger.error("sql error {}", ex.getLocalizedMessage());
		}
		return null;
		}
	}
	private static final String RETRIEVAL_STATUS_QUERY = 
			Request.statusQuery(Tables.RETRIEVAL, Tables.RETRIEVAL_DISPOSAL);

	private static final String RETRIEVAL_VIEW_NAME = Tables.RETRIEVAL.n;

	public static Cursor queryByKey(final DistributorDataStore store,
			final String[] projection, final String uuid, final String sortOrder) {
		synchronized (store) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Tables.RETRIEVAL.n);

			// Get the database and run the query.
			final SQLiteDatabase db = store.helper.getReadableDatabase();
			return qb.query(db, projection, RETRIEVAL_UUID_QUERY, new String[]{ uuid }, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: RetrievalConstants.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query retrieval by key {} {} {}", new Object[]{ projection, uuid });
		}
		return null;
		}
	}
	static private final String RETRIEVAL_UUID_QUERY = new StringBuilder()
	.append(RequestField.UUID.q(null)).append("=?")
	.toString();

	/**
	 * Upsert
	 */

	/**
	 * @param cv
	 * @param status
	 * @return
	 */

	/**
	 * Update
	 */

	public static long updateByKey(final DistributorDataStore store, long requestId, ContentValues cv, final Dispersal state) {
		synchronized (store) {
			final RequestWorker requestor = Request.getRetrievalWorker(store);
		
		return requestor.updateById(requestId, cv, state);
		}
	}

	public static long updateByKey(final DistributorDataStore store, long requestId, String channel, final DisposalState state) {
		synchronized (store) {
			final DisposalWorker worker = Disposal.getRetrievalWorker(store);
		
		return worker.upsertByRequest(requestId, channel, state);
		}
	}

	/**
	 * Update
	 */
	public static int delete(final DistributorDataStore store, String whereClause, String[] whereArgs) {
		synchronized (store) {
		try {
			final SQLiteDatabase db = store.helper.getWritableDatabase();
			final int count = db.delete(Tables.RETRIEVAL.n, whereClause, whereArgs);
			logger.trace("Retrieval delete count: [{}]", count);
			return count;
		} catch (IllegalArgumentException ex) {
			logger.error("delete retrieval {} {}", whereClause, whereArgs);
		}
		return 0;
		}
	}
	public static int deleteGarbage(final DistributorDataStore store) {
		synchronized (store) {
		try {
			final SQLiteDatabase db = store.helper.getWritableDatabase();
			final int expireCount = db.delete(Tables.RETRIEVAL.n, 
					Request.REQUEST_EXPIRATION_CONDITION, 
					DistributorDataStore.getRelativeExpirationTime(RETRIEVAL_DELAY_OFFSET));
			logger.trace("Retrieval garbage count: [{}]", expireCount);
			return expireCount;
		} catch (IllegalArgumentException ex) {
			logger.error("deleteRetrievalGarbage {}", ex.getLocalizedMessage());
		} catch (SQLiteException ex) {
			logger.error("deleteRetrievalGarbage {}", ex.getLocalizedMessage());
		}

		return 0;
		}
	}

	private static final long RETRIEVAL_DELAY_OFFSET = 8 * 60 * 60; // 8 hrs in seconds

	/**
	 * purge all records from the retrieval table and cascade to the disposal table.
	 * @return
	 */
	public static int purge(final DistributorDataStore store) {
		synchronized (store) {
		try {
			final SQLiteDatabase db = store.helper.getWritableDatabase();
			return db.delete(Tables.RETRIEVAL.n, null, null);
		} catch (IllegalArgumentException ex) {
			logger.error("purgeRetrieval");
		}
		return 0;
		}
	}


}
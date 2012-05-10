package edu.vu.isis.ammo.core.distributor.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.provider.BaseColumns;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.api.type.Selection;
import edu.vu.isis.ammo.api.type.TimeTrigger;
import edu.vu.isis.ammo.api.type.Topic;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.Dispersal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalField;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalState;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalTotalState;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalWorker;
import edu.vu.isis.ammo.core.distributor.store.Request.PriorityType;
import edu.vu.isis.ammo.core.distributor.store.Request.RequestField;
import edu.vu.isis.ammo.core.distributor.store.Request.RequestWorker;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableField;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableFieldState;

public class Subscribe {
	private static final Logger logger = LoggerFactory.getLogger("class.store.subscribe");


	static public void onCreate(SQLiteDatabase db) {
		String sqlCreateRef = null; 
		/**
		 *  ===== SUBSCRIBE
		 */
		try {	
			final Tables request = Tables.SUBSCRIBE;
			final Tables disposal = Tables.SUBSCRIBE_DISPOSAL;
			final TableField[] fields = SubscribeField.values();

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
	 * ===================
	 *  SUBSCRIBE
	 * ===================
	 * 
	 * The subscribe table is for holding local subscription requests.
	 */

	/** Insert method helper */
	protected static ContentValues initializeDefaults(ContentValues values) {

		Request.initializeDefaults(values);

		if (!values.containsKey(SubscribeField.FILTER.n())) {
			values.put(SubscribeField.FILTER.n(), "");
		}
		return values;
	}

	public enum SubscribeField  implements TableField {

		FILTER("filter", "TEXT");
		// The rows/tuples wanted.

		// TODO : what about message rates?

		final public TableFieldState impl;

		private SubscribeField(String name, String type) {
			this.impl = TableFieldState.getInstance(name, type);
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

	public static interface SubscribeConstants {

		public static final String DEFAULT_SORT_ORDER = ""; 
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
				for (SubscribeField field : SubscribeField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (RequestField field : RequestField.values()) {
					projection.put(field.n(), field.n());
				}
				for (SubscribeField field : SubscribeField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	}

	public static SubscribeWorker getWorker(final DistributorDataStore store, final Cursor pending, AmmoService svc) {
		return new SubscribeWorker(store, pending, svc);
	}
	public static SubscribeWorker getWorker(final DistributorDataStore store, final AmmoRequest request, AmmoService svc) {
		return new SubscribeWorker(store, request, svc);
	}
	/** 
	 * Store access class
	 */
	public static class SubscribeWorker extends RequestWorker {

		final public int id;
		final public String topic;
		final public String subtopic;
		final public String auid;
		final public UUID uuid;

		public final Provider provider;
		public final DistributorPolicy.Topic policy;
		public final int priority;
		public final TimeTrigger expire;
		public final Selection select;

		public DisposalTotalState totalState = null;
		public Dispersal dispersal = null;

		private SubscribeWorker(final DistributorDataStore store, final AmmoRequest ar, AmmoService svc) {
			super(store, Tables.SUBSCRIBE, Tables.SUBSCRIBE_DISPOSAL);

			this.id = -1;
			this.uuid = UUID.randomUUID();
			this.auid = ar.uid;
			this.topic = ar.topic.asString();
			this.subtopic = (ar.subtopic == null) ? Topic.DEFAULT : ar.subtopic.asString();
			this.provider = ar.provider;
			this.policy = svc.policy().matchSubscribe(topic);

			this.priority = PriorityType.aggregatePriority(policy.routing.priority, ar.priority);
			this.expire = ar.expire;
			this.select = ar.select;

			this.dispersal = policy.makeRouteMap();
			this.totalState = DisposalTotalState.NEW;
		}

		private SubscribeWorker(final DistributorDataStore store, final Cursor pending, AmmoService svc) {
			super(store, Tables.SUBSCRIBE, Tables.SUBSCRIBE_DISPOSAL);

			this.id = pending.getInt(pending.getColumnIndex(RequestField._ID.cv()));
			this.provider = new Provider(pending.getString(pending.getColumnIndex(RequestField.PROVIDER.n())));
			this.topic = pending.getString(pending.getColumnIndex(RequestField.TOPIC.n()));
			this.subtopic = pending.getString(pending.getColumnIndex(RequestField.SUBTOPIC.n()));
			this.uuid = UUID.fromString(pending.getString(pending.getColumnIndex(RequestField.UUID.n())));
			this.auid = pending.getString(pending.getColumnIndex(RequestField.AUID.n()));
			this.policy = (svc == null) ? null : svc.policy().matchSubscribe(topic);

			this.priority = pending.getInt(pending.getColumnIndex(RequestField.PRIORITY.n()));
			final long expireEnc = pending.getLong(pending.getColumnIndex(RequestField.EXPIRATION.n()));
			this.expire = new TimeTrigger(expireEnc);

			String select = "";
			try {
				select = pending.getString(pending.getColumnIndex(SubscribeField.FILTER.n()));	
			} catch (Exception ex) {
				logger.warn("no selection");
			} finally {
				this.select = new Selection(select);
			}

			this.dispersal = this.policy.makeRouteMap();
			Cursor channelCursor = null;
			try { 
				channelCursor = Disposal.getSubscribeWorker(this.store).queryByParent(this.id);

				for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; moreChannels = channelCursor.moveToNext()) {
					final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalField.CHANNEL.n()));
					final short channelState = channelCursor.getShort(channelCursor.getColumnIndex(DisposalField.STATE.n()));
					this.dispersal.put(channel, DisposalState.getInstanceById(channelState));
				}
			} finally {
				if (channelCursor != null) channelCursor.close();
			}
			logger.trace("process subscribe row: id=[{}] topic=[{}:{}] select=[{}] dispersal=[{}]", 
					new Object[] { this.id, this.topic, this.subtopic, this.select, this.dispersal});

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

		public long upsert(final DisposalTotalState totalState) {
			synchronized(this.store) {	
				// build key
				if (uuid == null) {
					logger.error("subscribe requests must have a uuid: [{}]", this);
					return -1;
				}
				final String topic = this.topic;
				final String subtopic = this.subtopic;
				final String provider = this.provider.cv();

				final ContentValues cv = new ContentValues();
				cv.put(RequestField.UUID.cv(), this.uuid.toString());
				cv.put(RequestField.AUID.cv(), this.auid);
				cv.put(RequestField.TOPIC.cv(), this.topic);
				cv.put(RequestField.SUBTOPIC.cv(), this.subtopic);
				cv.put(RequestField.PROVIDER.cv(), this.provider.cv());

				cv.put(RequestField.PRIORITY.cv(), this.priority);
				cv.put(RequestField.EXPIRATION.cv(), this.expire.cv());

				cv.put(RequestField.CREATED.cv(), System.currentTimeMillis());				
				cv.put(RequestField.DISPOSITION.cv(), totalState.cv());

				if (this.select != null) {
					cv.put(SubscribeField.FILTER.cv(), this.select.cv());
				}

				PLogger.STORE_SUBSCRIBE_DML.trace("upsert subscribe: {} @ {}",
						totalState, cv);

				this.db.beginTransaction();
				Cursor cursor  = null;
				try {
					final long rowid;
					final String whereClause = SUBSCRIBE_UPDATE_CLAUSE_KEY;
					final String[] whereArgs = new String[]{ topic, subtopic, provider };

					cursor = this.db.query(this.request.n, 
							new String[] {RequestField._ID.n()}, 
							whereClause, whereArgs, null, null, null);

					if (cursor.moveToFirst()) {
						long id = -1;
						try {
							id = cursor.getLong(cursor.getColumnIndex(RequestField._ID.n()));
							final String[] rowid_arg = new String[]{ Long.toString(id) };
							this.db.update(this.request.n, cv, DistributorDataStore.ROWID_CLAUSE, rowid_arg );
							cursor.close();
						} catch (CursorIndexOutOfBoundsException ex) {
							logger.error("upsert failed {} {}", 
									id,
									cursor.getColumnIndex(RequestField._ID.n()));
						} finally {
							rowid = id;
						}
						PLogger.STORE_SUBSCRIBE_DML.trace("updated row=[{}] : args=[{}] clause=[{}]",
								new Object[]{id, whereArgs, whereClause});
					} else {
						rowid = this.db.insert(this.request.n, RequestField.CREATED.n(), cv);
						PLogger.STORE_SUBSCRIBE_DML.trace("inserted row=[{}] : values=[{}]",
								rowid, cv);
					}

					final DisposalWorker dworker = new DisposalWorker(this.store, this.request, this.disposal);
					dworker.upsertByRequest(rowid, this.dispersal);
					this.db.setTransactionSuccessful();

					return rowid;
				} catch (IllegalArgumentException ex) {
					logger.error("upsert {} {}", cv, this.dispersal);
				} finally {
					if (cursor != null) cursor.close();
					this.db.endTransaction();
				}
				return -1;
			}
		}
	}

	static final private String SUBSCRIBE_UPDATE_CLAUSE_KEY = new StringBuilder()
	.append(RequestField.TOPIC.q(null)).append("=?")
	.append(" AND ")
	.append(RequestField.SUBTOPIC.q(null)).append("=?")
	.append(" AND ")
	.append(RequestField.PROVIDER.q(null)).append("=?")
	.toString();



	/**
	 * Query
	 */
	public static Cursor query(final DistributorDataStore store, String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		synchronized (store) {
			return Request.query(store, SUBSCRIBE_VIEW_NAME, projection, whereClause, whereArgs, sortOrder);
		}
	}

	public static Cursor queryReady(final DistributorDataStore store) {
		synchronized (store) {
			store.openRead();
			try {
				return store.db.rawQuery(SUBSCRIBE_STATUS_QUERY, null);
			} catch(SQLiteException ex) {
				logger.error("sql error {}", ex.getLocalizedMessage());
			}
			return null;
		}
	}
	public static Cursor queryByKey(final DistributorDataStore store,
			String[] projection,
			final String topic, final String subtopic, String sortOrder) {
		synchronized (store) {
			return Request.queryByTopic(store, SUBSCRIBE_VIEW_NAME, projection, topic, subtopic, sortOrder, SubscribeConstants.DEFAULT_SORT_ORDER);
		}
	}

	private static final String SUBSCRIBE_STATUS_QUERY = 
			Request.statusQuery(Tables.SUBSCRIBE, Tables.SUBSCRIBE_DISPOSAL);

	private static final String SUBSCRIBE_VIEW_NAME = Tables.SUBSCRIBE.n;

	/**
	 * Upsert
	 */
	/*
	public static long upsertSubscribe(ContentValues cv, DistributorState status) {
	synchronized (store) {
		PLogger.STORE_SUBSCRIBE_DML.trace("upsert subscribe: {} @ {}",
				cv, status);
		final RequestWorker requestor = this.getSubscribeRequestWorker();
		return requestor.upsert(cv, status);
	}
	}
	 */


	/**
	 * Update
	 */

	public static long updateByKey(final DistributorDataStore store, long requestId, ContentValues cv, final Dispersal state) {
		synchronized (store) {
			final RequestWorker requestor = Request.getSubscribeWorker(store);

			return requestor.updateById(requestId, cv, state);
		}
	}

	public static long updateByKey(final DistributorDataStore store, long requestId, String channel, final DisposalState state) {
		synchronized (store) {
			final DisposalWorker worker = Disposal.getSubscribeWorker(store);

			return worker.upsertByRequest(requestId, channel, state);
		}
	}

	/**
	 * Delete
	 */
	public static int delete(final DistributorDataStore store, String whereClause, String[] whereArgs) {
		synchronized (store) {
			try {
				final SQLiteDatabase db = store.helper.getWritableDatabase();
				final int count = db.delete(Tables.SUBSCRIBE.n, whereClause, whereArgs);
				logger.trace("Subscribe delete count: [{}]", count);
				return count;
			} catch (IllegalArgumentException ex) {
				logger.error("delete subscribe {} {}", whereClause, whereArgs);
			}
			return 0;
		}
	}
	public static int deleteGarbage(final DistributorDataStore store) {
		synchronized (store) {
			try {
				final SQLiteDatabase db = store.helper.getWritableDatabase();
				final int expireCount = db.delete(Tables.SUBSCRIBE.n, 
						Request.REQUEST_EXPIRATION_CONDITION, 
						DistributorDataStore.getRelativeExpirationTime(SUBSCRIBE_DELAY_OFFSET));
				logger.trace("Subscribe garbage count: [{}]", expireCount);
				return expireCount;
			} catch (IllegalArgumentException ex) {
				logger.error("deleteSubscribeGarbage {}", ex.getLocalizedMessage());
			} catch (SQLiteException ex) {
				logger.error("deleteSubscribeGarbage {}", ex.getLocalizedMessage());
			}
			return 0;
		}
	}

	private static final long SUBSCRIBE_DELAY_OFFSET = 365 * 24 * 60 * 60; // 1 yr in seconds

	/**
	 * purge all records from the subscribe table and cascade to the disposal table.
	 * @return
	 */
	public static int purge(final DistributorDataStore store) {
		synchronized (store) {
			try {
				final SQLiteDatabase db = store.helper.getWritableDatabase();
				return db.delete(Tables.SUBSCRIBE.n, null, null);
			} catch (IllegalArgumentException ex) {
				logger.error("purgeSubscribe");
			}
			return 0;
		}
	}


}

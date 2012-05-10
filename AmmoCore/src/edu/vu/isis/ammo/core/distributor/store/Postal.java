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
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Notice;
import edu.vu.isis.ammo.api.type.Notice.Threshold;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.api.type.SerialMoment;
import edu.vu.isis.ammo.api.type.TimeTrigger;
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
import edu.vu.isis.ammo.core.distributor.store.Request.RequestConstants;
import edu.vu.isis.ammo.core.distributor.store.Request.RequestField;
import edu.vu.isis.ammo.core.distributor.store.Request.RequestWorker;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableField;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableFieldState;
import edu.vu.isis.ammo.util.FullTopic;

public class Postal {
	private static final Logger logger = LoggerFactory.getLogger("class.store.postal");


	static public void onCreate(SQLiteDatabase db) {
		String sqlCreateRef = null; 
		try {	
			final Tables request = Tables.POSTAL;
			final Tables disposal = Tables.POSTAL_DISPOSAL;
			final TableField[] fields = PostalField.values();

			final StringBuilder createRequestSql = 
					new StringBuilder()
			.append(" CREATE TABLE ")
			.append(request.q())
			.append(" ( ")
			.append(TableHelper.ddl(RequestField.values())).append(',')
			.append(TableHelper.ddl(fields))
			.append(')')
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
	 *  POSTAL
	 * ===========================
	 */

	/** Insert method helper */
	public static ContentValues initializeDefaults(ContentValues values) {

		Request.initializeDefaults(values);

		if (!values.containsKey(PostalField.DATA.n())) {
			values.put(PostalField.DATA.n(), "");
		}
		return values;
	}

	static public PostalWorker getWorker(final DistributorDataStore store, final AmmoRequest ar, final AmmoService svc) {
		return new PostalWorker(store, ar, svc);
	}
	static public PostalWorker getWorker(final DistributorDataStore store, final Cursor pending, final AmmoService svc) {
		return new PostalWorker(store, pending, svc);
	}
	static public PostalWorker getWorkerByKey(final DistributorDataStore store, String uuid) {
		Cursor cursor = null;
		try {
			cursor = Request.query(store, Tables.POSTAL.q(), null, 
					POSTAL_KEY_CLAUSE, new String[]{ uuid }, null);
			if (! cursor.moveToFirst()) {
				logger.warn("not found postal=[{}]", uuid);
				return null;
			}
			final PostalWorker worker = getWorker(store, cursor, null);
			cursor.close();
			return worker;
		} finally {
			if (cursor != null) cursor.close();
		}		
	}

	private static String POSTAL_KEY_CLAUSE = new StringBuilder()
	.append(RequestField.UUID.cv()).append("=?").toString();




	/**
	 * The postal table is for holding retrieval requests.
	 */
	public enum PostalField  implements TableField {

		PAYLOAD("payload", "TEXT"),
		// The payload instead of content provider

		QUANTIFIER("quantifier", "INTEGER"),
		// Indicates the expected distribution quantity
		// See the Quantifier api/type for details

		DATA("data", "TEXT"),
		// If null then the data file corresponding to the
		// column name and record id should be used. 
		// This is done when the data
		// size is larger than that allowed for a field contents.

		NOTICE_SENT("notice_sent", "INTEGER"),
		NOTICE_DEVICE_DELIVERED("notice_delivery", "INTEGER"),
		NOTICE_RECEIPT("notice_receipt", "INTEGER"),
		NOTICE_GATEWAY_DELIVERED("notice_gate_in", "INTEGER"),
		NOTICE_PLUGIN_DELIVERED("notice_gate_out", "INTEGER");
		/**
		 * These notices represent the action to be taken 
		 * as the message crosses the threshold specified.
		 * 0x00 or null indicate that no action is to be taken.
		 * Otherwise the integer represents a list of bits.
		 */

		final public TableFieldState impl;

		private PostalField(String name, String type) {
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
	static public class PostalWorker extends RequestWorker{
		public final int id;

		public final UUID uuid;
		public final String auid;
		public final String topic;	
		public final String subtopic;
		public final Provider provider;
		public DistributorPolicy.Topic policy;

		public final SerialMoment serialMoment; 
		public final int priority;
		public final TimeTrigger expire;
		public final Notice notice;

		public DisposalTotalState totalState = null;
		public Dispersal dispersal = null;
		public Payload payload = null;

		private PostalWorker(final DistributorDataStore store, final AmmoRequest ar, final AmmoService svc) {
			super(store, Tables.POSTAL, Tables.POSTAL_DISPOSAL);

			this.id = -1;
			this.uuid = UUID.fromString(ar.uuid);
			this.auid = ar.uid;
			this.topic = ar.topic.asString();
			this.subtopic = (ar.subtopic == null) ? "" : ar.subtopic.asString();
			this.provider = ar.provider;
			this.policy = svc.policy().matchPostal(topic);

			this.serialMoment = ar.moment;
			this.notice = (ar.notice == null) ? Notice.newInstance() : ar.notice;

			this.priority = PriorityType.aggregatePriority(policy.routing.priority, ar.priority);
			this.expire = ar.expire;

			this.dispersal = policy.makeRouteMap();
			this.totalState = DisposalTotalState.NEW;
		}

		private PostalWorker(final DistributorDataStore store, final Cursor pending, final AmmoService svc) {
			super(store, Tables.POSTAL, Tables.POSTAL_DISPOSAL);

			this.id = pending.getInt(pending.getColumnIndex(RequestField._ID.cv()));

			this.provider = new Provider(pending.getString(pending.getColumnIndex(RequestField.PROVIDER.n())));
			this.topic = pending.getString(pending.getColumnIndex(RequestField.TOPIC.n()));
			this.subtopic = pending.getString(pending.getColumnIndex(RequestField.SUBTOPIC.n()));
			this.uuid = UUID.fromString(pending.getString(pending.getColumnIndex(RequestField.UUID.n())));
			this.auid = pending.getString(pending.getColumnIndex(RequestField.AUID.n()));
			this.serialMoment = new SerialMoment(pending.getInt(pending.getColumnIndex(RequestField.SERIAL_MOMENT.n())));


			this.priority = pending.getInt(pending.getColumnIndex(RequestField.PRIORITY.n()));
			final long expireEnc = pending.getLong(pending.getColumnIndex(RequestField.EXPIRATION.n()));
			this.expire = new TimeTrigger(expireEnc);

			this.notice = Notice.newInstance();

			try {
				this.notice.setItem(Threshold.SENT, pending.getInt(pending.getColumnIndex(PostalField.NOTICE_SENT.n())));
				this.notice.setItem(Threshold.DEVICE_DELIVERY, pending.getInt(pending.getColumnIndex(PostalField.NOTICE_DEVICE_DELIVERED.n())));
				this.notice.setItem(Threshold.GATE_DELIVERY, pending.getInt(pending.getColumnIndex(PostalField.NOTICE_GATEWAY_DELIVERED.n())));
				this.notice.setItem(Threshold.PLUGIN_DELIVERY, pending.getInt(pending.getColumnIndex(PostalField.NOTICE_PLUGIN_DELIVERED.n())));

				if (svc != null) {
					this.policy = svc.policy().matchPostal(topic);

					this.payload = new Payload(pending.getString(pending.getColumnIndex(PostalField.PAYLOAD.n())));

					this.dispersal = this.policy.makeRouteMap();
					Cursor channelCursor = null;
					try { 
						channelCursor = Disposal.getPostalWorker(this.store).queryByParent(this.id);

						for (boolean moreChannels = channelCursor.moveToFirst(); moreChannels; moreChannels = channelCursor.moveToNext()) {
							final String channel = channelCursor.getString(channelCursor.getColumnIndex(DisposalField.CHANNEL.n()));
							final short channelState = channelCursor.getShort(channelCursor.getColumnIndex(DisposalField.STATE.n()));
							this.dispersal.put(channel, DisposalState.getInstanceById(channelState));
						}
					} finally {
						if (channelCursor != null) channelCursor.close();
					}
					logger.trace("process postal row: uid=[{}:{}] topic=[{}:{}] dispersal=[{}]", 
							new Object[] { this.id, this.uuid, this.topic, this.subtopic, this.dispersal });
				}
			} catch (IllegalStateException ex) {
				if (logger.isWarnEnabled()) {
					final StringBuilder sb = new StringBuilder().append(':');
					for (String name : pending.getColumnNames()) {
						sb.append(name).append('=').append(pending.getColumnIndex(name)).append(" ");
					}
					logger.warn("broken columns=[{}]", sb.toString());
				}
			} 
		}

		public PostalWorker payload(byte[] payload) {
			this.payload = new Payload(payload);
			return this;
		}

		/**
		 * The upsert behavior is conditioned based on several factors.
		 * Generally postal requests are keyed by either their: 
		 * - topic/subtopic and provider, or
		 * - topic/subtopic payload
		 * 
		 * If a previous uuid is seen what does that mean?
		 * 
		 * @param totalState
		 * @param payload
		 * @return
		 */
		public long upsert(final DisposalTotalState totalState) {
			synchronized(this.store) {	
				// build key
				if (uuid == null) {
					logger.error("postal requests must have a uuid: [{}]", this);
					return -1;
				}

				final String topic = this.topic;
				final String subtopic = (this.subtopic == null) ? "" : this.subtopic;
				final String provider = this.provider.cv();

				final ContentValues cv = new ContentValues();
				cv.put(RequestField.UUID.cv(), this.uuid.toString());
				cv.put(RequestField.AUID.cv(), this.auid);
				cv.put(RequestField.TOPIC.cv(), this.topic);
				cv.put(RequestField.SUBTOPIC.cv(), this.subtopic);
				cv.put(RequestField.PROVIDER.cv(), this.provider.cv());

				cv.put(RequestField.SERIAL_MOMENT.cv(), this.serialMoment.cv());
				cv.put(RequestField.PRIORITY.cv(), this.priority);
				cv.put(RequestField.EXPIRATION.cv(), this.expire.cv());

				cv.put(RequestField.CREATED.cv(), System.currentTimeMillis());				
				cv.put(RequestField.DISPOSITION.cv(), totalState.cv());

				if (payload == null) {
					cv.put(PostalField.PAYLOAD.cv(), "");
				} else {
					cv.put(PostalField.PAYLOAD.cv(), payload.toString());
				}

				cv.put(PostalField.NOTICE_SENT.cv(), this.notice.atSend.via.v);	
				cv.put(PostalField.NOTICE_DEVICE_DELIVERED.cv(), this.notice.atDeviceDelivered.via.v);	
				cv.put(PostalField.NOTICE_GATEWAY_DELIVERED.cv(), this.notice.atGatewayDelivered.via.v);	
				cv.put(PostalField.NOTICE_PLUGIN_DELIVERED.cv(), this.notice.atPluginDelivered.via.v);	

				// values.put(PostalTableSchema.UNIT.cv(), 50);
				PLogger.STORE_POSTAL_DML.trace("upsert postal: {} @ {}",
						totalState, cv);

				this.db.beginTransaction();
				Cursor cursor  = null;
				try {
					final long rowid;
					final String whereClause = POSTAL_UPDATE_CK_CLAUSE;
					final String[] whereArgs = new String[]{ topic, subtopic, provider };

					cursor = this.db.query(Tables.POSTAL.n, 
							new String[] {RequestField._ID.n()}, 
							whereClause, whereArgs, null, null, null);

					if (cursor.moveToFirst()) {
						rowid = cursor.getLong(cursor.getColumnIndex(RequestField._ID.n()));
						final String[] rowid_arg = new String[]{ Long.toString(rowid) };
						this.db.update(this.request.n, cv, DistributorDataStore.ROWID_CLAUSE, rowid_arg );
						cursor.close();
						PLogger.STORE_POSTAL_DML.trace("updated row=[{}] : args=[{}] clause=[{}]",
								new Object[]{rowid, whereArgs, whereClause});
					} else {
						rowid = this.db.insert(this.request.n, RequestField.CREATED.n(), cv);
						PLogger.STORE_POSTAL_DML.trace("inserted row=[{}] : values=[{}]",
								rowid, cv);
					}

					final DisposalWorker dworker = new DisposalWorker(this.store, this.request, this.disposal);
					dworker.upsertByRequest(rowid, dispersal);
					this.db.setTransactionSuccessful();

					return rowid;
				} catch (IllegalArgumentException ex) {
					logger.error("upsert {} {}", cv, dispersal);
				} finally {
					if (cursor != null) cursor.close();
					this.db.endTransaction();
				}
				return -1;
			}
		}

		public int delete(String providerId) {
			final String whereClause = POSTAL_UPDATE_CK_CLAUSE;
			final String[] whereArgs = new String[] {this.topic, this.subtopic, providerId};

			try {
				final SQLiteDatabase db = this.store.helper.getWritableDatabase();
				final int count = db.delete(Tables.POSTAL.n, whereClause, whereArgs);
				logger.trace("Postal delete count: [{}]", count);
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

	static final private String POSTAL_UPDATE_CK_CLAUSE = new StringBuilder()
	.append(RequestField.TOPIC.q(null)).append("=?")
	.append(" AND ")
	.append(RequestField.SUBTOPIC.q(null)).append("=?")		
	.append(" AND ")
	.append(RequestField.PROVIDER.q(null)).append("=?")
	.toString();


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

	static public Cursor query(final DistributorDataStore store, String[] projection, String whereClause,
			String[] whereArgs, String sortOrder) {
		return Request.query(store, Tables.POSTAL.n, projection, whereClause, whereArgs, sortOrder);
	}


	static public Cursor queryReady(final DistributorDataStore store) {
		store.openRead();
		try {
			return store.db.rawQuery(POSTAL_STATUS_QUERY, null);
		} catch(SQLiteException ex) {
			logger.error("sql error {}", ex.getLocalizedMessage());
		}
		return null;
	}
	private static final String POSTAL_STATUS_QUERY = 
			Request.statusQuery(Tables.POSTAL, Tables.POSTAL_DISPOSAL);

	/**
	 * Update
	 */

	/**
	 * @param requestId
	 * @param cv
	 * @param state
	 * @return
	 */

	static public long updateByKey(final DistributorDataStore store, long requestId, ContentValues cv, Dispersal state) {
		final RequestWorker requestor = Request.getPostalWorker(store);
		return requestor.updateById(requestId, cv, state);
	}

	static public long updateByKey(final DistributorDataStore store, long requestId, String channel, final DisposalState state) {
		final DisposalWorker worker = Disposal.getPostalWorker(store);
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
	public static int delete(final DistributorDataStore store, String whereClause, String[] whereArgs) {
		synchronized (store) {
			try {
				final int count = store.db.delete(Tables.POSTAL.n, whereClause, whereArgs);
				logger.trace("Postal delete count: [{}]", count);
				return count;
			} catch (IllegalArgumentException ex) {
				logger.error("delete postal {} {}", whereClause, whereArgs);
			}
			return 0;
		}
	}

	public static int deleteGarbage(final DistributorDataStore store) {
		synchronized (store) {
			try {
				final SQLiteDatabase db = store.helper.getWritableDatabase();
				final int expireCount = db.delete(Tables.POSTAL.n, 
						Request.REQUEST_EXPIRATION_CONDITION, 
						DistributorDataStore.getRelativeExpirationTime(POSTAL_DELAY_OFFSET));
				logger.trace("Postal garbage count: [{}]", expireCount);
				return expireCount;
			} catch (IllegalArgumentException ex) {
				logger.error("deletePostalGarbage {}", ex.getLocalizedMessage());
			} catch (SQLiteException ex) {
				logger.error("deletePostalGarbage {}", ex.getLocalizedMessage());
			}
			return 0;
		}
	}


	private static final long POSTAL_DELAY_OFFSET = 8 * 60 * 60; // 1 hr in seconds



}

package edu.vu.isis.ammo.core.distributor.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.text.TextUtils;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.Dispersal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.store.Channel.ChannelField;
import edu.vu.isis.ammo.core.distributor.store.Request.RequestField;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableField;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableFieldState;

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

public class Disposal {
	private static final Logger logger = LoggerFactory.getLogger("class.store.disposal");

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
	 * An association between channel and request.
	 * This 
	 */
	public enum DisposalField  implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CHANNEL("channel", "TEXT"),
		// The name of the channel over which the message could-be/was sent

		REQUEST("request", "INTEGER"),
		// The _id of the parent request

		STATE("state", "INTEGER");
		// State of the request in the channel
		// see ChannelDisposalState for valid values

		final public TableFieldState impl;

		private DisposalField(String name, String type) {
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

	public static interface DisposalConstants {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();


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

	public static String DISPOSAL_FOREIGN_KEY(Tables request) {
		return new StringBuilder()
		.append(" FOREIGN KEY(").append(DisposalField.REQUEST.n()).append(")")
		.append(" REFERENCES ").append(request.n)
		.append("(").append(RequestField._ID.n()).append(")")
		.append(" ON DELETE CASCADE ")
		.append(",")
		.append(" FOREIGN KEY(").append(DisposalField.CHANNEL.n()).append(")")
		.append(" REFERENCES ").append(Tables.CHANNEL.n)
		.append("(").append(ChannelField.NAME.n()).append(")")
		.append(" ON UPDATE CASCADE ")
		.append(" ON DELETE CASCADE ")
		.toString();
	}


	/** 
	 * Store access class
	 */
	public static class DisposalWorker {
		protected final DistributorDataStore store;
		protected final SQLiteDatabase db;
		protected final Tables request;
		protected final Tables disposal;

		private final String DISPOSAL_STATUS_QUERY;
		private final String DISPOSAL_UPDATE_CLAUSE;

		public DisposalWorker(DistributorDataStore store, Tables request, Tables disposal) {
			this.store = store;
			this.db = this.store.db;
			this.request = request;
			this.disposal = disposal;

			this.DISPOSAL_STATUS_QUERY = new StringBuilder()
			.append(" SELECT * ")
			.append(" FROM ").append(this.disposal.q()).append(" AS d ")
			.append(" WHERE ").append(DisposalField.REQUEST.q("d")).append("=? ")
			.toString();

			this.DISPOSAL_UPDATE_CLAUSE = new StringBuilder()
			.append(DisposalField.REQUEST.q(null)).append("=?")
			.append(" AND ")
			.append(DisposalField.CHANNEL.q(null)).append("=?").toString();
		}

		public Cursor query(String[] projection, String whereClause,
				String[] whereArgs, String sortOrder) {
			synchronized (this.store) {
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
			synchronized(this.store) {	
				try {
					logger.trace("disposal ready: [{}] -> [{}]", new Object[]{parent, DISPOSAL_STATUS_QUERY} );
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
		long[] upsertByRequest(final long requestId, final Dispersal status) {
			logger.trace("upsert into=[{}] : id=[{}] status=[{}]", new Object[]{ this.disposal, requestId, status});
			synchronized(this.store) {	
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
		long upsertByRequest(long requestId, String channel, DisposalState status) {
			synchronized(this.store) {	
				Cursor cursor = null;
				final ContentValues cv = new ContentValues();
				db.beginTransaction();
				try {					
					cv.put(DisposalField.REQUEST.cv(), requestId);
					cv.put(DisposalField.CHANNEL.cv(), channel);
					cv.put(DisposalField.STATE.cv(), (status == null) ? DisposalState.PENDING.o : status.o);

					final String requestIdStr = String.valueOf(requestId);
					final int updateCount = this.db.update(this.disposal.n, cv, 
							DISPOSAL_UPDATE_CLAUSE, new String[]{requestIdStr, channel } );
					if (updateCount < 1) {
						final long row = this.db.insert(this.disposal.n, DisposalField._ID.n(), cv);
						PLogger.STORE_DISPOSAL_DML.debug("inserting row=[{}] into=[{}] : cv=[{}]", 
								new Object[]{ row, this.disposal, cv} );
						this.db.setTransactionSuccessful();
						return row;
					} else if (updateCount > 1) {
						logger.error("duplicate [{}] count=[{}]", this.disposal, updateCount);
					}

					// some rows were updated we wish to return the key of the updated row
					cursor = this.db.query(this.disposal.n, new String[]{DisposalField._ID.n()}, 
							DISPOSAL_UPDATE_CLAUSE, new String[]{requestIdStr, channel },
							null, null, null);

					cursor.moveToFirst();
					final long row = cursor.getInt(0); // we only asked for one column so it better be it.
					cursor.close();

					PLogger.STORE_DISPOSAL_DML.debug("updated row=[{}] into=[{}] : cv=[{}]", 
							new Object[]{ row, this.disposal, cv} );
					this.db.setTransactionSuccessful();
					return row;

				} catch (SQLiteConstraintException ex) {
					logger.error("upsert error: {}: {}", 
							ex.getLocalizedMessage(), cv);
				} catch (IllegalArgumentException ex) {
					logger.error("upsert disposal {} {} {}", new Object[]{requestId, channel, status});
				} finally {
					if (cursor != null) cursor.close();
					this.db.endTransaction();
				}
				return 0;
			}

		}
	}


	public static DisposalWorker getPostalWorker(final DistributorDataStore store) {
		return new DisposalWorker(store, Tables.POSTAL, Tables.POSTAL_DISPOSAL);
	}

	public static DisposalWorker getRetrievalWorker(final DistributorDataStore store) {
		return new DisposalWorker(store, Tables.RETRIEVAL, Tables.RETRIEVAL_DISPOSAL);
	}

	public static DisposalWorker getSubscribeWorker(final DistributorDataStore store) {
		return new DisposalWorker(store, Tables.SUBSCRIBE, Tables.SUBSCRIBE_DISPOSAL);
	}


	/**
	 * When a channel is deactivated all of its subscriptions  
	 * will need to be re-done on re-connect.
	 * Retrievals and postals won't have this problem,
	 * 
	 * TODO
	 * Discussion:
	 * What do we want to do with queued requests?
	 * Should they be reset to pending or left queued?
	 * This depends on what a channel does when it disconnects.
	 * Does it clear its queue indiscriminately?  I probably should.
	 * In that case this method should set queued items back to pending.
	 * 
	 * @param channel
	 * @return
	 */
	public static int deactivateDisposalStateByChannel(final DistributorDataStore store, String channel) {
		synchronized (store) {
		try {
			store.db.update(Tables.POSTAL_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_DEACTIVATE_POSTAL_CLAUSE, new String[]{ channel } );

			store.db.update(Tables.RETRIEVAL_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_DEACTIVATE_RETRIEVAL_CLAUSE, new String[]{ channel } );

			store.db.update(Tables.SUBSCRIBE_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_DEACTIVATE_SUBSCRIBE_CLAUSE, new String[]{ channel } );

		} catch (IllegalArgumentException ex) {
			logger.error("deactivateDisposalStateByChannel {} ", channel);
		}
		return 0;
		}
	}	
	static final private String DISPOSAL_DEACTIVATE_POSTAL_CLAUSE = new StringBuilder()
	.append(DisposalField.CHANNEL.q(null)).append("=?")
	.append(" AND ")
	.append(DisposalField.STATE.q(null))
	.append(" NOT IN ( ")
	.append(DisposalState.BAD.q()).append(',')
	.append(DisposalState.QUEUED.q()).append(',')
	.append(DisposalState.SENT.q()).append(')')
	.toString();

	static final private String DISPOSAL_DEACTIVATE_RETRIEVAL_CLAUSE = new StringBuilder()
	.append(DisposalField.CHANNEL.q(null)).append("=?")
	.append(" AND ")
	.append(DisposalField.STATE.q(null))
	.append(" NOT IN ( ")
	.append(DisposalState.BAD.q()).append(',')
	.append(DisposalState.QUEUED.q()).append(',')
	.append(DisposalState.SENT.q()).append(')')
	.toString();

	static final private String DISPOSAL_DEACTIVATE_SUBSCRIBE_CLAUSE = new StringBuilder()
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
	public static int activateDisposalStateByChannel(final DistributorDataStore store, String channel) {
		return -1;
	}

	/** 
	 * When a channel is repaired it any failed requests may be retried.
	 * @param channel
	 * @return
	 */
	/**
	 * These are related to upsertChannelByName() inasmuch as it 
	 * resets the failed state to pending.
	 * @param name
	 * @return the number of failed items updated
	 */
	static final public ContentValues DISPOSAL_PENDING_VALUES;
	static {
		DISPOSAL_PENDING_VALUES = new ContentValues();
		DISPOSAL_PENDING_VALUES.put(DisposalField.STATE.cv(), DisposalState.PENDING.o); 
	}

	public static int repairDisposalStateByChannel(final DistributorDataStore store, String channel) {
		synchronized (store) {
			try {
				store.db.update(Tables.POSTAL_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
						DISPOSAL_REPAIR_CLAUSE, new String[]{ channel } );

				store.db.update(Tables.RETRIEVAL_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
						DISPOSAL_REPAIR_CLAUSE, new String[]{ channel } );

				store.db.update(Tables.SUBSCRIBE_DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
						DISPOSAL_REPAIR_CLAUSE, new String[]{ channel } );
			} catch (IllegalArgumentException ex) {
				logger.error("repairDisposalStateByChannel {}", channel);
			}
			return 0;
		}
	}
	static final private String DISPOSAL_REPAIR_CLAUSE = new StringBuilder()
	.append(DisposalField.CHANNEL.q(null)).append("=?")
	.append(" AND ")
	.append(DisposalField.STATE.q(null))
	.append(" IN ( ").append(DisposalState.BAD.q()).append(')')
	.toString();

}

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
import edu.vu.isis.ammo.core.distributor.store.RelationHelper.RelationField;

public class Capability {
	private final static Logger logger = LoggerFactory.getLogger("class.store.capability");

	// The device identifier 
	// (required)
	private final long id;

	// The device identifier 
	// (required)
	private final String origin;

	// This along with the cost is used to decide how to deliver the specific object.
	// (required)
	private final String topic;
	// (optional)
	private final String subtopic;

	// The name of the operator using the channel
	private final String operator;

	// Time-stamp at which point the request 
	// becomes stale and can be discarded.
	private final int expiration;

	// When the operator first used this channel
	// The first field indicates the first time the peer was observed.
	private final long first;

	// When the operator was last seen "speaking" on the channel
	// The latest field indicates the last time the peer was observed.
	private long latest;

	// How many times the peer has been seen since FIRST
	// Each time LATEST is changed this COUNT should be incremented
	private int count;

	// what about message rates?

	private Capability() {
		this.id = -1;
		this.origin = null;
		this.topic = null;
		this.subtopic = null;
		this.operator = null;
		this.expiration = -1;
		this.first = System.currentTimeMillis();
		this.latest = this.first;
		this.count = 1;
	}
	/*
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
	private Capability(String name, String type) {
		this.id
		this.impl = RelationFieldState.getInstance(name,type);
	}
*/

	/**
	 * CapabilityWorker
	 * An actor for updating the PRESENCE component of the store.
	 * 
	 * @param deviceId
	 * @return
	 */
	/*
	public static CapabilityWorker getWorker(final DistributorDataStore parent, final IAmmoRequest ar, final AmmoService svc,
			String device, String operator) {
		return new CapabilityWorker(parent, (AmmoRequest) ar, svc, device, operator);
	}
	*/
	/** 
	 * Capability store access class
	 */
	/*
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

			this.device = pending.getString(pending.getColumnIndex(Item.ORIGIN.n()));
			this.operator = pending.getString(pending.getColumnIndex(CapabilityField.OPERATOR.n()));
			this.topic = pending.getString(pending.getColumnIndex(CapabilityField.TOPIC.n()));
			this.subtopic = pending.getString(pending.getColumnIndex(CapabilityField.SUBTOPIC.n()));

			final long expireEnc = pending.getLong(pending.getColumnIndex(CapabilityField.EXPIRATION.n()));
			this.expire = null; // new TimeTrigger(expireEnc);
		}
		*/

		/**
		 * returns the number of rows affected.
		 * 
		 * @param deviceId
		 * @return
		 */
	/*
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
		*/

		/**
		 * @return
		 */
	/*
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
	*/


}

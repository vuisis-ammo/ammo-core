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

package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.text.TextUtils;
import edu.vu.isis.ammo.core.distributor.store.Capability;
import edu.vu.isis.ammo.core.distributor.store.Presence;
import edu.vu.isis.ammo.core.distributor.store.RelationsHelper;
import edu.vu.isis.ammo.core.provider.CapabilitySchema;
import edu.vu.isis.ammo.core.provider.PresenceSchema;
import edu.vu.isis.ammo.core.provider.Relations;

/**
 * The Distributor Store Object is managed by the distributor thread. The
 * distributor thread manages two queues.
 * <ul>
 * <li>coming from the AIDL calls from clients</li>
 * <li>coming from the gateway</li>
 * </ul>
 */
public class DistributorDataStore {
	// ===========================================================
	// Constants
	// ===========================================================
	private final static Logger logger = LoggerFactory.getLogger("dist.store");
	public static final int VERSION = 41;
	public static final String SQLITE_NAME = "distributor.db";

	public static final long CONVERT_MINUTES_TO_MILLISEC = 60L * 1000L;

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
	 * Data Store Table definitions The postal tables record requests that data
	 * be sent out.
	 * <p>
	 * POSTed data is specifically named and distributed. The retrieval and
	 * subscribe tables record request that data be obtained.
	 * <p>
	 * RETRIEVAL data is obtained from a source.
	 * <p>
	 * SUBSCRIBEd data is obtained by topic.
	 * <p>
	 * The disposition table keeps track of the status of the delivery. It is
	 * used in conjunction with the distribution policy. The disposition table
	 * may have several entries for each request. There is one row for each
	 * potential channel over which the request could be sent. There will be one
	 * row for each potential channel from the policy. As the channel is used it
	 * will be marked. Once all clauses which may use a channel become true the
	 * clauses are removed. The rule for disposition rows is cascade delete.
	 */

	// ===========================================================
	// Enumerated types in the tables.
	// ===========================================================

	/**
	 * Indicates if the provider indicates a table entry or whether the data has
	 * been pre-serialized.
	 */
	public enum SerializeType {
		/** the serialized data is found in the data field (or a suitable file) */
		DIRECT(1),
		/** the serialized data is obtained from the named provider uri */
		INDIRECT(2),
		/**
		 * the same as INDIRECT but the serialization doesn't happen until the
		 * data is sent.
		 */
		DEFERRED(3);

		public int o; // ordinal

		private SerializeType(int o) {
			this.o = o;
		}

		/**
		 * Produce string of the form... '<field-ordinal-value>';
		 */
		public String quote() {
			return new StringBuilder().append("'").append(this.o).append("'")
					.toString();
		}

		public static SerializeType byOrdinal(int serialType) {
			switch (serialType) {
			case 1:
				return DIRECT;
			case 2:
				return INDIRECT;
			case 3:
				return DEFERRED;
			}
			throw new IllegalArgumentException("unknown SerialType "
					+ Integer.toString(serialType));
		}

		public String cv() {
			return String.valueOf(this.o);
		}

		static public SerializeType getInstance(String ordinal) {
			return SerializeType.values()[Integer.parseInt(ordinal)];
		}

		static public SerializeType getInstance(int ordinal) {
			return SerializeType.values()[ordinal];
		}
	};

	public enum ChannelState {
		/** The channel is connected and ready to receive requests */
		ACTIVE(1),
		/** The channel is not connected */
		INACTIVE(2),
		/** the channel is connected but not ready to receive requests */
		BUSY(3);

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
			return new StringBuilder().append("'").append(this.o).append("'")
					.toString();
		}
	}

	/**
	 * The states of a request. The DISTRIBUTE state indicates that the total
	 * state is an aggregate of the distribution of the request across the
	 * relevant channels. see the ChannelDisposal
	 */
	public enum DisposalTotalState {
		/** either all pending or none */
		NEW(0x01, "new"),
		/** the request is being actively processed */
		DISTRIBUTE(0x02, "distribute"),
		/** the expiration time of the request has arrived */
		EXPIRED(0x04, "expired"),
		/** the distribution rule has been fulfilled */
		COMPLETE(0x08, "complete"),
		/** the distribution rule cannot be completely fulfilled */
		INCOMPLETE(0x10, "incomplete"),
		/** the request failed */
		FAILED(0x20, "failed");

		final public int o;
		final public String t;

		private DisposalTotalState(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}

		public String q() {
			return new StringBuilder().append("'").append(this.o).append("'")
					.toString();
		}

		public String cv() {
			return String.valueOf(this.o);
		}

		static public DisposalTotalState getInstance(String ordinal) {
			return DisposalTotalState.values()[Integer.parseInt(ordinal)];
		}

		static public DisposalTotalState getInstanceById(int o) {
			for (DisposalTotalState candidate : DisposalTotalState.values()) {
				if (candidate.o == o)
					return candidate;
			}
			return null;
		}

	};

	/**
	 * The states of a request over a particular channel. The DISTRIBUTE
	 * RequestDisposal indicates that the total state is an aggregate of the
	 * distribution of the request across the relevant channels.
	 */
	public enum ChannelStatus {
		/** channel is ready to receive requests */
		READY(0x0001, "ready"),
		/** channel queue is empty */
		EMPTY(0x0002, "empty"),
		/** channel is temporarily down */
		DOWN(0x0004, "down"),
		/** channel queue is full */
		FULL(0x0008, "busy"), ;

		final public int o;
		final public String t;

		private ChannelStatus(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}

		public String q() {
			return new StringBuilder().append("'").append(this.o).append("'")
					.toString();
		}

		public DisposalState inferDisposal() {
			switch (this) {
			case DOWN:
				return DisposalState.REJECTED;
			case FULL:
				return DisposalState.BUSY;
			default:
				logger.warn("don't call with {}", this);
				throw new IllegalArgumentException();
			}
		}

	};

	/**
	 * The states of a request over a particular channel. The DISTRIBUTE
	 * RequestDisposal indicates that the total state is an aggregate of the
	 * distribution of the request across the relevant channels. <h3>Reliability
	 * </h3> If there is a reasonable expectation of an acknowledgment then the
	 * state should be set to told. On reconnect the told messages should be
	 * re-told.
	 */
	public enum DisposalState {
		/** an initial transient state */
		NEW(0x0001, "new"),
		/** channel is temporarily rejecting requests (probably down) */
		REJECTED(0x0002, "rejected"),
		/** message is problematic, don't try again */
		BAD(0x0080, "bad"),
		/** cannot send, channel unavailable, but not because the message is bad */
		PENDING(0x0004, "pending"),
		/** message in channel queue */
		QUEUED(0x0008, "queued"),
		/** channel queue was busy (full channel queue) */
		BUSY(0x0100, "full"),
		/**
		 * message has been sent asynchronously with no expectation of an
		 * acknowledgment
		 */
		SENT(0x0010, "sent"),
		/**
		 * message sent asynchronously, with an expectation of an acknowledgment
		 */
		TOLD(0x0020, "told"),
		/** asynchronously send (told) message has been acknowledged */
		DELIVERED(0x0040, "delivered"), ;

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
			return new StringBuilder().append("'").append(this.o).append("'")
					.toString();
		}

		public String cv() {
			return String.valueOf(this.o);
		}

		static public DisposalState getInstance(String ordinal) {
			return DisposalState.values()[Integer.parseInt(ordinal)];
		}

		/**
		 * This method indicates if the goal has been met. Note that false does
		 * not mean the goal will not be reachable it only means that it has not
		 * yet been reached.
		 */
		public boolean goalReached(boolean goalCondition) {
			switch (this) {
			case QUEUED:
			case SENT:
			case DELIVERED:
				if (goalCondition == true)
					return true;
				break;
			case PENDING:
			case REJECTED:
			case BUSY:
			case BAD:
				if (goalCondition == false)
					return true;
				break;
			case NEW:
			case TOLD:
			default:
				break;
			}
			return false;
		}

		public DisposalState and(boolean clauseSuccess) {
			if (clauseSuccess)
				return this;
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
				if (candidate.o == o)
					return candidate;
			}
			return null;
		}
	};

	/**
	 * Indicates how delayed messages are to be prioritized.
	 */
	public enum ContinuityType {
		/** indicates that only one copy should be kept (the latest) */
		ONCE(1, "once"),
		/** only things within a particular time span */
		TEMPORAL(2, "temporal"),
		/** only a certain number of items */
		QUANTITY(3, "quantity");

		final public int o;
		final public String t;

		private ContinuityType(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}

		/**
		 * Produce string of the form... '<field-ordinal-value>';
		 */
		public String quote() {
			return new StringBuilder().append("'").append(this.o).append("'")
					.toString();
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
	 */
	public enum PriorityType {
		/** Gets sent immediately */
		FLASH(0x80, "FLASH"),
		/** Time critical */
		URGENT(0x40, "URGENT"),
		/** Should be sent but not timing critical */
		IMPORTANT(0x20, "IMPORTANT"),
		/** Not particularly time critical, but may be of interest generally */
		NORMAL(0x10, "NORMAL"),
		/**
		 * Large and should not be allowed to interfere, processed in background
		 */
		BACKGROUND(0x08, "BACKGROUND");

		final public int o;
		final public String t;

		private PriorityType(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}

		/**
		 * Produce string of the form... '<field-ordinal-value>';
		 */
		public String quote() {
			return new StringBuilder().append("'").append(this.o).append("'")
					.toString();
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
				if (upper > o && lower >= o)
					return candidate;
			}
			return null;
		}

		public CharSequence toString(int priorityId) {
			final StringBuilder sb = new StringBuilder().append(this.o);
			if (priorityId > this.o) {
				sb.append("+").append(priorityId - this.o);
			}
			return sb.toString();
		}
	};

	/**
	 * Description: Indicates if the uri indicates a table or whether the data
	 * has been pre-serialized.
	 * <P>
	 * Type: EXCLUSIVE
	 * </P>
	 */
	public enum SerializeMode {
		/** The serialized data is found in the data field (or a suitable file). */
		DIRECT(1, "DIRECT"),
		/** The serialized data is obtained from the named uri. */
		INDIRECT(2, "INDIRECT"),
		/**
		 * The same as INDIRECT but the serialization doesn't happen until the
		 * data is sent.
		 */
		DEFERRED(3, "DEFERRED");

		final public int o;
		final public String t;

		private SerializeMode(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
		}

		/**
		 * Produce string of the form... '<field-ordinal-value>';
		 */
		public String quote() {
			return new StringBuilder().append("'").append(this.o).append("'")
					.toString();
		}

		public String cv() {
			return String.valueOf(this.o);
		}

		static public SerializeMode getInstance(String ordinal) {
			return SerializeMode.values()[Integer.parseInt(ordinal)];
		}
	};

	// ===========================================================
	// Enumerated types in the tables.
	// ===========================================================

	/**
	 * The postal table is for holding retrieval requests.
	 */
	public static interface PostalTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID
				+ " ASC";

		public static final String[] COLUMNS = new String[PostalTableSchema
				.values().length];
		public static final Map<String, String> PROJECTION_MAP = new HashMap<String, String>(
				PostalTableSchema.values().length);
	};

	static {
		int ix = 0;
		for (PostalTableSchema field : PostalTableSchema.values()) {
			PostalTable.COLUMNS[ix++] = field.n;
			PostalTable.PROJECTION_MAP.put(field.n, field.n);
		}
	};

	public enum PostalTableSchema {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		/** This is a unique identifier for the request */
		UUID("uuid", "TEXT"),

		/** When the request was made */
		CREATED("created", "INTEGER"),

		/** When the request was last modified */
		MODIFIED("modified", "INTEGER"),

		/**
		 * This along with the cost is used to decide how to deliver the
		 * specific object.
		 */
		TOPIC("topic", "TEXT"),

		/**
		 * This specifies a forced channel be used and no other. NULL indicates
		 * that the normal processing to select a channel be used.
		 */
		CHANNEL("channel", "TEXT"),

		/**
		 * (optional) The appplication specific unique identifier This is used
		 * in notice intents so the application can relate.
		 */
		AUID("auid", "TEXT"),

		/** The uri of the content provider */
		PROVIDER("provider", "TEXT"),

		/**
		 * The payload instead of content provider Very similar to DATA maybe
		 * these should be combined.
		 */
		PAYLOAD("payload", "BLOB"),

		/**
		 * If null then the data file corresponding to the column name and
		 * record id should be used. This is done when the data size is larger
		 * than that allowed for a field contents.
		 */
		DATA("data", "BLOB"),

		/** The current best guess of the status of the request. */
		DISPOSITION("disposition", "INTEGER"),

		/**
		 * A description of what is to be done when various state-transition
		 * occur.
		 */
		NOTICE("notice", "BLOB"),

		/**
		 * Controls the order this message be sent. Negative priorities
		 * indicated less than normal.
		 */
		PRIORITY("priority", "INTEGER"),

		/** ? */
		ORDER("serialize_type", "INTEGER"),

		/** Time-stamp at which point entry becomes stale. */
		EXPIRATION("expiration", "INTEGER"),

		/**
		 * Units associated with {@link #VALUE}. Used to determine whether
		 * should occur.
		 */
		UNIT("unit", "TEXT"),

		/**
		 * Arbitrary value linked to importance that entry is transmitted and
		 * battery drain.
		 */
		WORTH("value", "INTEGER");

		/** the well known name */
		final public String n;
		/** the data type */
		final public String t;

		private PostalTableSchema(String n, String t) {
			this.n = n;
			this.t = t;
		}

		/**
		 * Produce string of the form... "<field-name>" <field-type> e.g. "dog"
		 * TEXT
		 */
		public String addfield() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.append(' ').append(this.t).toString();
		}

		/**
		 * Produce string of the form... "<field-name>"
		 */
		public String q() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.toString();
		}

		public String cv() {
			return String.valueOf(this.n);
		}
	};

	/**
	 * The retrieval table is for holding retrieval requests.
	 */
	public static interface RetrievalTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID
				+ " ASC";

		public static final String[] COLUMNS = new String[RetrievalTableSchema
				.values().length];
		public static final Map<String, String> PROJECTION_MAP = new HashMap<String, String>(
				RetrievalTableSchema.values().length);
	};

	static {
		int ix = 0;
		for (RetrievalTableSchema field : RetrievalTableSchema.values()) {
			RetrievalTable.COLUMNS[ix++] = field.n;
			RetrievalTable.PROJECTION_MAP.put(field.n, field.n);
		}
	};

	public enum RetrievalTableSchema {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		/** This is a unique identifier for the request */
		UUID("uuid", "TEXT"),

		/** When the request was made */
		CREATED("created", "INTEGER"),

		/** When the request was last modified */
		MODIFIED("modified", "INTEGER"),

		/** This is the data type */
		TOPIC("topic", "TEXT"),

		/**
		 * This is a unique identifier for the request as specified by the
		 * application
		 */
		AUID("auid", "TEXT"),

		/** The uri of the content provider */
		PROVIDER("provider", "TEXT"),

		/**
		 * A description of what is to be done when various state-transition
		 * occur.
		 */
		NOTICE("notice", "BLOB"),

		/**
		 * What order should this message be sent. Negative priorities indicated
		 * less than normal.
		 */
		PRIORITY("priority", "INTEGER"),

		/** The fields/columns wanted. */
		PROJECTION("projection", "TEXT"),

		/** The rows/tuples wanted. */
		SELECTION("selection", "TEXT"),

		/** The values using in the selection. */
		ARGS("args", "TEXT"),

		/** The order the values are to be returned in. */
		ORDERING("ordering", "TEXT"),

		/**
		 * The maximum number of items to retrieve as items are obtained the
		 * count should be decremented
		 */
		LIMIT("maxrows", "INTEGER"),

		/** The current best guess of the status of the request. */
		DISPOSITION("disposition", "INTEGER"),

		/** Time-stamp at which request entry becomes stale. */
		EXPIRATION("expiration", "INTEGER"),

		/**
		 * Units associated with {@link #VALUE}. Used to determine whether
		 * should occur.
		 */
		UNIT("unit", "TEXT"),

		/**
		 * Arbitrary value linked to importance that entry is transmitted and
		 * battery drain.
		 */
		VALUE("value", "INTEGER"),

		/**
		 * If the If null then the data file corresponding to the column name
		 * and record id should be used. This is done when the data size is
		 * larger than that allowed for a field contents.
		 */
		DATA("data", "TEXT"),

		/**
		 * The meaning changes based on the continuity type.
		 * <ul>
		 * <li>ONCE : undefined
		 * <li>TEMPORAL : chronic, this differs slightly from the expiration
		 * which deals with the request this deals with the time stamps of the
		 * requested objects.
		 * <li>QUANTITY : the maximum number of objects to return
		 * </ul>
		 */
		CONTINUITY_TYPE("continuity_type", "INTEGER"), CONTINUITY_VALUE(
				"continuity_value", "INTEGER");

		final public String n; // name
		final public String t; // type

		private RetrievalTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}

		/**
		 * Produce string of the form... "<field-name>" <field-type> e.g. "dog"
		 * TEXT
		 */
		public String addfield() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.append(' ').append(this.t).toString();
		}

		/**
		 * Produce string of the form... "<field-name>"
		 */
		public String q() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.toString();
		}

		public String cv() {
			return String.valueOf(this.n);
		}
	};

	/**
	 * The subscription table is for holding subscription requests.
	 */
	public static interface SubscribeTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID
				+ " ASC";

		public static final String[] COLUMNS = new String[SubscribeTableSchema
				.values().length];
		public static final Map<String, String> PROJECTION_MAP = new HashMap<String, String>(
				SubscribeTableSchema.values().length);
	}

	static {
		int ix = 0;
		for (SubscribeTableSchema field : SubscribeTableSchema.values()) {
			SubscribeTable.COLUMNS[ix++] = field.n;
			SubscribeTable.PROJECTION_MAP.put(field.n, field.n);
		}
	}

	public enum SubscribeTableSchema {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		/** This is a unique identifier for the request */
		UUID("uuid", "TEXT"),

		/** When the request was made */
		CREATED("created", "INTEGER"),

		/** When the request was last modified */
		MODIFIED("modified", "INTEGER"),

		/** The data type of the objects being subscribed to */
		TOPIC("topic", "TEXT"),

		/** The application UUID for the request */
		AUID("auid", "TEXT"),

		/** The uri of the content provider */
		PROVIDER("provider", "TEXT"),

		/** The current best guess of the status of the request. */
		DISPOSITION("disposition", "INTEGER"),

		/** Time-stamp at which request entry becomes stale. */
		EXPIRATION("expiration", "INTEGER"),

		/** The rows/tuples wanted. */
		SELECTION("selection", "TEXT"),

		/**
		 * A description of what is to be done when various state-transition
		 * occur.
		 */
		NOTICE("notice", "BLOB"),

		/**
		 * What order should this message be sent. Negative priorities indicated
		 * less than normal.
		 */
		PRIORITY("priority", "INTEGER");

		final public String n; // name
		final public String t; // type

		private SubscribeTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}

		/**
		 * Produce string of the form... "<field-name>" <field-type> e.g. "dog"
		 * TEXT
		 */

		public String addfield() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.append(' ').append(this.t).toString();
		}

		/**
		 * Produce string of the form... "<field-name>"
		 */
		public String q() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.toString();
		}

		public String cv() {
			return String.valueOf(this.n);
		}
	}

	/**
	 * The disposal table is for holding request disposition status.
	 */
	public static interface DisposalTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID
				+ " ASC";

		public static final String[] COLUMNS = new String[DisposalTableSchema
				.values().length];
		public static final Map<String, String> PROJECTION_MAP = new HashMap<String, String>(
				DisposalTableSchema.values().length);
	}

	static {
		int ix = 0;
		for (DisposalTableSchema field : DisposalTableSchema.values()) {
			DisposalTable.COLUMNS[ix++] = field.n;
			DisposalTable.PROJECTION_MAP.put(field.n, field.n);
		}
	}

	public enum DisposalTableSchema {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		/** Meaning the parent type: subscribe, retrieval, postal */
		TYPE("type", "INTEGER"),

		/** The _id of the parent */
		PARENT("parent", "INTEGER"),

		/** The name of the channel over which the message could be sent */
		CHANNEL("channel", "TEXT"),

		/** Where the request is on the channel */
		STATE("state", "INTEGER");

		/** the well known name */
		final public String n;
		/** the data type */
		final public String t;

		private DisposalTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}

		/**
		 * Produce string of the form... "<field-name>" <field-type> e.g. "dog"
		 * TEXT
		 */
		public String addfield() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.append(' ').append(this.t).toString();
		}

		/**
		 * Produce string of the form... "<field-name>"
		 */
		public String q() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.toString();
		}

		public String cv() {
			return String.valueOf(this.n);
		}
	}

	/**
	 * The channel table is for holding current channel status. This could be
	 * done with a concurrent hash map but that would put more logic in the java
	 * code and less in sqlite.
	 */
	public static interface ChannelTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID
				+ " ASC";

		public static final String[] COLUMNS = new String[ChannelTableSchema
				.values().length];
		public static final Map<String, String> PROJECTION_MAP = new HashMap<String, String>(
				ChannelTableSchema.values().length);
	}

	static {
		int ix = 0;
		for (ChannelTableSchema field : ChannelTableSchema.values()) {
			ChannelTable.COLUMNS[ix++] = field.n;
			ChannelTable.PROJECTION_MAP.put(field.n, field.n);
		}
	}

	public enum ChannelTableSchema {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		/** The name of the channel, must match policy channel name */
		NAME("name", "TEXT"),

		/** The channel state (active inactive) */
		STATE("state", "INTEGER");

		/** the well known name */
		final public String n;
		/** the data type */
		final public String t;

		private ChannelTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}

		/**
		 * Produce string of the form... "<field-name>" <field-type> e.g. "dog"
		 * TEXT
		 */
		public String addfield() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.append(' ').append(this.t).toString();
		}

		/**
		 * Produce string of the form... "<field-name>"
		 */
		public String q() {
			return new StringBuilder().append('"').append(this.n).append('"')
					.toString();
		}

		public String cv() {
			return String.valueOf(this.n);
		}
	}

	// Views.
	public interface Views {
		// Nothing to put here yet.
	}

	public interface Indices {
		// Nothing to put here yet.
	}

	static private String backingName = null;

	static public void inMemory() {
		DistributorDataStore.backingName = null;
	}

	static public void backingFileName(String name) {
		DistributorDataStore.backingName = name;
	}

	// ===========================================================
	// Methods
	// ===========================================================
	/**
	 * Use Relations.NAME - to create mounted database. Use of null for the name
	 * causes an in memory database.
	 */
	public DistributorDataStore(Context context) {
		this.context = context;
		this.helper = new DataStoreHelper(this.context,
				DistributorDataStore.backingName, null, VERSION);

		// ========= INITIALIZE CONSTANTS ========
		this.applDir = context.getDir("support", Context.MODE_PRIVATE);

		if (!this.applDir.exists()) {
			if (!this.applDir.mkdirs()) {
				logger.error(
						"cannot create files check permissions in manifest : {}",
						this.applDir.toString());
			}
		}

		this.applCacheDir = new File(this.applDir, "cache");
		if (!this.applCacheDir.exists())
			this.applCacheDir.mkdir();

		this.applCachePostalDir = new File(this.applCacheDir, "postal");
		if (!this.applCachePostalDir.exists())
			this.applCachePostalDir.mkdir();

		this.applCacheRetrievalDir = new File(this.applCacheDir, "retrieval");
		if (!this.applCacheRetrievalDir.exists())
			this.applCacheRetrievalDir.mkdir();

		this.applCachePublicationDir = new File(this.applCacheDir,
				"publication");
		if (!this.applCachePublicationDir.exists())
			this.applCachePublicationDir.mkdir();

		this.applCacheSubscriptionDir = new File(this.applCacheDir,
				"subscription");
		if (!this.applCacheSubscriptionDir.exists())
			this.applCacheSubscriptionDir.mkdir();

		this.applTempDir = new File(this.applDir, "tmp");
		if (!this.applTempDir.exists())
			this.applTempDir.mkdir();

		this.openWrite();
		logger.trace("datastore constructed");
	}

	/**
	 * It is possible for the distributor database to become corrupted. While
	 * this is not a common behavior it does happen from time to time. It is
	 * believed that this is caused by abrupt shutdown of the service.
	 */
	public synchronized DistributorDataStore openRead() {
		if (this.db != null && this.db.isReadOnly())
			return this;

		this.db = this.helper.getReadableDatabase();
		return this;
	}

	public synchronized DistributorDataStore openWrite() {
		if (this.db != null && this.db.isOpen() && !this.db.isReadOnly())
			this.db.close();

		this.db = this.helper.getWritableDatabase();
		return this;
	}

	public synchronized void close() {
		this.db.close();
	}

	/**
	 * Query set.
	 * 
	 * @param projection
	 * @param selection
	 * @param selectionArgs
	 * @param sortOrder
	 * @return
	 */
	public synchronized Cursor queryPostal(String[] projection,
			String selection, String[] selectionArgs, String sortOrder) {
		try {
			this.openRead();
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Relations.POSTAL.n);
			qb.setProjectionMap(PostalTable.PROJECTION_MAP);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, selection, selectionArgs, null,
					null, (!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: PostalTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query postal {} {}", selection, selectionArgs);
		}
		return null;
	}

	/**
	 * This method returns those postal requests which are not reliably
	 * delivered. In this case reliably means that it is not certain that the
	 * request was delivered.
	 * 
	 * @return
	 */
	public synchronized Cursor queryPostalReady() {
		this.openRead();
		try {
			logger.trace("postal query \n{}", POSTAL_STATUS_QUERY);
			return db.rawQuery(POSTAL_STATUS_QUERY, null);
		} catch (SQLiteException ex) {
			logger.error("sql error", ex);
		}
		return null;
	}

	private static final String POSTAL_STATUS_QUERY;
	static {
		final RelationsHelper rel_postal = new RelationsHelper(Relations.POSTAL);
		final RelationsHelper rel_disposal = new RelationsHelper(
				Relations.DISPOSAL);
		final RelationsHelper rel_channel = new RelationsHelper(
				Relations.CHANNEL);
		POSTAL_STATUS_QUERY = new StringBuilder().append(" SELECT ")
				.append(" * ").append(" FROM ").append(rel_postal.q())
				.append(" AS p ").append(" WHERE EXISTS (SELECT * ")
				.append(" FROM ").append(rel_disposal.q()).append(" AS d ")
				.append(" INNER JOIN ").append(rel_channel.q())
				.append(" AS c ").append(" ON d.")
				.append(DisposalTableSchema.CHANNEL.q()).append("=c.")
				.append(ChannelTableSchema.NAME.q()).append(" WHERE p.")
				.append(PostalTableSchema._ID.q()).append("=d.")
				.append(DisposalTableSchema.PARENT.q()).append("   AND d.")
				.append(DisposalTableSchema.TYPE.q()).append('=')
				.append(rel_postal.qv()).append("   AND c.")
				.append(ChannelTableSchema.STATE.q()).append('=')
				.append(ChannelState.ACTIVE.q()).append("   AND d.")
				.append(DisposalTableSchema.STATE.q()).append(" IN (")
				.append(DisposalState.REJECTED.q()).append(',')
				.append(DisposalState.BUSY.q()).append(',')
				.append(DisposalState.PENDING.q()).append(',')
				.append(DisposalState.TOLD.q()).append(')')
				.append(')')
				// close exists clause
				.append(" ORDER BY ").append(PostalTableSchema.PRIORITY.q())
				.append(" DESC ").append(',').append(PostalTableSchema._ID.q())
				.append(" ASC ").append(';').toString();
	}

	public synchronized Cursor queryRetrieval(String[] projection,
			String selection, String[] selectArgs, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Relations.RETRIEVAL.n);
			qb.setProjectionMap(RetrievalTable.PROJECTION_MAP);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, selection, selectArgs, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: RetrievalTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query retrieval {} {}", selection, selectArgs);
		}
		return null;
	}

	public synchronized Cursor queryRetrievalByKey(String[] projection,
			String uuid, String topic, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Relations.RETRIEVAL.n);
			qb.setProjectionMap(RetrievalTable.PROJECTION_MAP);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, RETRIEVAL_QUERY, new String[] {
					uuid, topic }, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: RetrievalTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query retrieval by key {} {} {}", new Object[] {
					projection, uuid, topic });
		}
		return null;
	}

	static private final String RETRIEVAL_QUERY = new StringBuilder()
			.append(RetrievalTableSchema.UUID.q()).append("=?").append(" AND ")
			.append(RetrievalTableSchema.TOPIC.q()).append("=?").toString();

	public synchronized Cursor queryRetrievalReady() {
		try {
			logger.trace("retrieval ready {}", RETRIEVAL_STATUS_QUERY);
			return db.rawQuery(RETRIEVAL_STATUS_QUERY, null);
		} catch (SQLiteException ex) {
			logger.error("sql error", ex);
		}
		return null;
	}

	private static final String RETRIEVAL_STATUS_QUERY;
	static {
		final RelationsHelper rel_retrieval = new RelationsHelper(
				Relations.RETRIEVAL);
		final RelationsHelper rel_disposal = new RelationsHelper(
				Relations.DISPOSAL);
		final RelationsHelper rel_channel = new RelationsHelper(
				Relations.CHANNEL);
		RETRIEVAL_STATUS_QUERY = new StringBuilder().append(" SELECT ")
				.append(" * ").append(" FROM ").append(rel_retrieval.q())
				.append(" AS p ").append(" WHERE EXISTS (SELECT * ")
				.append(" FROM ").append(rel_disposal.q()).append(" AS d ")
				.append(" INNER JOIN ").append(rel_channel.q())
				.append(" AS c ").append(" ON d.")
				.append(DisposalTableSchema.CHANNEL.q()).append("=c.")
				.append(ChannelTableSchema.NAME.q()).append(" WHERE p.")
				.append(RetrievalTableSchema._ID.q()).append("=d.")
				.append(DisposalTableSchema.PARENT.q()).append("   AND d.")
				.append(DisposalTableSchema.TYPE.q()).append("=")
				.append(rel_retrieval.qv()).append("   AND c.")
				.append(ChannelTableSchema.STATE.q()).append('=')
				.append(ChannelState.ACTIVE.q()).append("   AND d.")
				.append(DisposalTableSchema.STATE.q()).append(" IN (")
				.append(DisposalState.REJECTED.q()).append(',')
				.append(DisposalState.PENDING.q())
				.append(')')
				.append(')')
				// close exists clause
				.append(" ORDER BY ").append(RetrievalTableSchema.PRIORITY.q())
				.append(" DESC ").append(',')
				.append(RetrievalTableSchema._ID.q()).append(" ASC ")
				.toString();
	}

	public synchronized Cursor querySubscribe(String[] projection,
			String selection, String[] selectArgs, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Relations.SUBSCRIBE.n);
			qb.setProjectionMap(SubscribeTable.PROJECTION_MAP);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, selection, selectArgs, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: SubscribeTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query subscribe {} {}", selection, selectArgs);
		}
		return null;
	}

	public synchronized Cursor querySubscribeByKey(String[] projection,
			String topic, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Relations.SUBSCRIBE.n);
			qb.setProjectionMap(SubscribeTable.PROJECTION_MAP);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, SUSCRIBE_QUERY,
					new String[] { topic }, null, null, (!TextUtils
							.isEmpty(sortOrder)) ? sortOrder
							: SubscribeTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query subscribe by key {} {}", projection, topic);
		}
		return null;
	}

	static private final String SUSCRIBE_QUERY = new StringBuilder()
			.append(SubscribeTableSchema.TOPIC.q()).append("=?").toString();

	public synchronized Cursor querySubscribeReady() {
		try {
			return this.db.rawQuery(SUBSCRIBE_STATUS_QUERY, null);
		} catch (SQLiteException ex) {
			logger.error("sql error", ex);
		}
		return null;
	}

	private static final String SUBSCRIBE_STATUS_QUERY;
	static {
		final RelationsHelper rel_subscribe = new RelationsHelper(
				Relations.SUBSCRIBE);
		final RelationsHelper rel_disposal = new RelationsHelper(
				Relations.DISPOSAL);
		final RelationsHelper rel_channel = new RelationsHelper(
				Relations.CHANNEL);
		SUBSCRIBE_STATUS_QUERY = new StringBuilder().append(" SELECT ")
				.append(" * ").append(" FROM ").append(rel_subscribe.q())
				.append(" AS p ").append(" WHERE EXISTS (SELECT * ")
				.append(" FROM ").append(rel_disposal.q()).append(" AS d ")
				.append(" INNER JOIN ").append(rel_channel.q())
				.append(" AS c ").append(" ON d.")
				.append(DisposalTableSchema.CHANNEL.q()).append("=c.")
				.append(ChannelTableSchema.NAME.q()).append(" WHERE p.")
				.append(SubscribeTableSchema._ID.q()).append("=d.")
				.append(DisposalTableSchema.PARENT.q()).append("   AND d.")
				.append(DisposalTableSchema.TYPE.q()).append("=")
				.append(rel_subscribe.qv()).append("   AND c.")
				.append(ChannelTableSchema.STATE.q()).append('=')
				.append(ChannelState.ACTIVE.q()).append("   AND d.")
				.append(DisposalTableSchema.STATE.q()).append(" IN (")
				.append(DisposalState.REJECTED.q()).append(',')
				.append(DisposalState.PENDING.q())
				.append(')')
				.append(')')
				// close exists clause
				.append(" ORDER BY ").append(SubscribeTableSchema.PRIORITY.q())
				.append(" DESC ").append(',')
				.append(SubscribeTableSchema._ID.q()).append(" ASC ")
				.toString();
	}

	public synchronized Cursor queryDisposal(String[] projection,
			String selection, String[] selectionArgs, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Relations.DISPOSAL.n);
			qb.setProjectionMap(DisposalTable.PROJECTION_MAP);

			// Get the database and run the query.
			return qb.query(this.db, projection, selection, selectionArgs,
					null, null, (!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: DisposalTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query disposal {} {}", selection, selectionArgs);
		}
		return null;
	}

	/**
	 * Get the state of the channels for the given request
	 * 
	 * @param parent
	 * @param type
	 * @return
	 */
	public synchronized Cursor queryDisposalByParent(int type, int parent) {
		try {
			logger.trace("disposal ready {} {} {}", new Object[] {
					DISPOSAL_STATUS_QUERY, type, parent });
			return db
					.rawQuery(
							DISPOSAL_STATUS_QUERY,
							new String[] { String.valueOf(type),
									String.valueOf(parent) });
		} catch (SQLiteException ex) {
			logger.error("sql error", ex);
		}
		return null;
	}

	private static final String DISPOSAL_STATUS_QUERY;
	static {
		final RelationsHelper rel_disposal = new RelationsHelper(
				Relations.DISPOSAL);
		DISPOSAL_STATUS_QUERY = new StringBuilder().append("SELECT * ")
				.append(" FROM ").append(rel_disposal.q()).append(" AS d ")
				.append(" WHERE d.").append(DisposalTableSchema.TYPE.q())
				.append("=? ").append("   AND d.")
				.append(DisposalTableSchema.PARENT.q()).append("=? ")
				.toString();
	}

	public synchronized Cursor queryChannel(String[] projection,
			String selection, String[] selectionArgs, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Relations.CHANNEL.n);
			qb.setProjectionMap(ChannelTable.PROJECTION_MAP);

			// Get the database and run the query.
			return qb.query(this.db, projection, selection, selectionArgs,
					null, null, (!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: ChannelTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query channel {} {}", selection, selectionArgs);
		}
		return null;
	}

	/**
	 * Upsert is a portmanteau of update and insert, thus, if a record with a
	 * matching key exists then update otherwise insert.
	 */
	public synchronized long upsertPostal(ContentValues cv, Dispersal status) {
		try {
			logger.trace("upsert postal status=[{}] cv=[{}]", status, cv);
			final String topic = cv.getAsString(PostalTableSchema.TOPIC.cv());
			final String provider = cv.getAsString(PostalTableSchema.PROVIDER
					.cv());

			final long key;
			final String[] updateArgs = new String[] { topic, provider };
			if (0 < this.db.update(Relations.POSTAL.n, cv,
					POSTAL_UPDATE_CLAUSE, updateArgs)) {
				final Cursor cursor = this.db.query(Relations.POSTAL.n,
						new String[] { PostalTableSchema._ID.n },
						POSTAL_UPDATE_CLAUSE, updateArgs, null, null, null);
				cursor.moveToFirst();
				key = cursor.getInt(0); // we only asked for one column so it
										// better be it.
				cursor.close();
			} else {
				key = this.db.insert(Relations.POSTAL.n,
						PostalTableSchema.CREATED.n, cv);
			}
			for (Entry<String, DisposalState> entry : status.entrySet()) {
				upsertDisposalByParent(Relations.POSTAL, key, entry.getKey(),
						entry.getValue());
			}
			return key;
		} catch (IllegalArgumentException ex) {
			logger.error("upsert postal {} {}", cv, status);
		}
		return 0;
	}

	static final private String POSTAL_UPDATE_CLAUSE = new StringBuilder()
			.append(PostalTableSchema.TOPIC.q()).append("=?").append(" AND ")
			.append(PostalTableSchema.PROVIDER.q()).append("=?").toString();

	public synchronized long upsertRetrieval(ContentValues cv, Dispersal status) {
		try {
			final String uuid = cv.getAsString(RetrievalTableSchema.UUID.cv());
			final String topic = cv
					.getAsString(RetrievalTableSchema.TOPIC.cv());
			final String provider = cv
					.getAsString(RetrievalTableSchema.PROVIDER.cv());

			final long key;
			final String[] updateArgs = new String[] { uuid, topic, provider };
			if (0 < this.db.update(Relations.RETRIEVAL.n, cv,
					RETRIEVAL_UPDATE_CLAUSE, updateArgs)) {
				final Cursor cursor = this.db.query(Relations.RETRIEVAL.n,
						new String[] { RetrievalTableSchema._ID.n },
						RETRIEVAL_UPDATE_CLAUSE, updateArgs, null, null, null);
				cursor.moveToFirst();
				key = cursor.getInt(0); // we only asked for one column so it
										// better be it.
				cursor.close();
			} else {
				key = this.db.insert(Relations.RETRIEVAL.n,
						RetrievalTableSchema.CREATED.n, cv);
			}
			for (Entry<String, DisposalState> entry : status.entrySet()) {
				final String entityChannel = entry.getKey();
				final DisposalState entityStatus = entry.getValue();
				logger.trace("upsert retrieval {} {} {}", new Object[] { key,
						entityChannel, entityStatus });
				upsertDisposalByParent(Relations.RETRIEVAL, key, entityChannel,
						entityStatus);
			}
			return key;
		} catch (IllegalArgumentException ex) {
			logger.error("upsert retrieval {} {}", cv, status);
		}
		return 0;
	}

	static final private String RETRIEVAL_UPDATE_CLAUSE = new StringBuilder()
			.append(RetrievalTableSchema.UUID.q()).append("=?").append(" AND ")
			.append(RetrievalTableSchema.TOPIC.q()).append("=?")
			.append(" AND ").append(RetrievalTableSchema.PROVIDER.q())
			.append("=?").toString();

	/**
	 *
	 */
	public synchronized long upsertSubscribe(ContentValues cv, Dispersal status) {
		try {
			final String topic = cv
					.getAsString(SubscribeTableSchema.TOPIC.cv());
			final String provider = cv
					.getAsString(SubscribeTableSchema.PROVIDER.cv());

			final long key;
			final String[] updateArgs = new String[] { topic, provider };
			if (0 < this.db.update(Relations.SUBSCRIBE.n, cv,
					SUBSCRIBE_UPDATE_CLAUSE, updateArgs)) {
				final Cursor cursor = this.db.query(Relations.SUBSCRIBE.n,
						new String[] { SubscribeTableSchema._ID.n },
						SUBSCRIBE_UPDATE_CLAUSE, updateArgs, null, null, null);
				cursor.moveToFirst();
				key = cursor.getInt(0); // we only asked for one column so it
										// better be it.
				cursor.close();
			} else {
				key = this.db.insert(Relations.SUBSCRIBE.n,
						SubscribeTableSchema.CREATED.n, cv);
			}
			for (Entry<String, DisposalState> entry : status.entrySet()) {
				this.upsertDisposalByParent(Relations.SUBSCRIBE, key,
						entry.getKey(), entry.getValue());
			}
			return key;
		} catch (IllegalArgumentException ex) {
			logger.error("upsert subscribe {} {}", cv, status);
		}
		return 0;
	}

	static final private String SUBSCRIBE_UPDATE_CLAUSE = new StringBuilder()
			.append(SubscribeTableSchema.TOPIC.q()).append("=?")
			.append(" AND ").append(SubscribeTableSchema.PROVIDER.q())
			.append("=?").toString();

	private synchronized long[] upsertDisposalByParent(Relations type, long id,
			Dispersal status) {
		try {
			final long[] idArray = new long[status.size()];
			int ix = 0;
			for (Entry<String, DisposalState> entry : status.entrySet()) {
				idArray[ix] = upsertDisposalByParent(type, id, entry.getKey(),
						entry.getValue());
				ix++;
			}
			return idArray;
		} catch (IllegalArgumentException ex) {
			logger.error("upsert disposal by parent {} {} {}", new Object[] {
					id, type, status });
		}
		return null;
	}

	private synchronized long upsertDisposalByParent(Relations type, long id,
			String channel, DisposalState status) {
		try {
			final String typeVal = new RelationsHelper(type).cv();

			final ContentValues cv = new ContentValues();
			cv.put(DisposalTableSchema.TYPE.cv(), typeVal);
			cv.put(DisposalTableSchema.PARENT.cv(), id);
			cv.put(DisposalTableSchema.CHANNEL.cv(), channel);
			cv.put(DisposalTableSchema.STATE.cv(),
					(status == null) ? DisposalState.PENDING.o : status.o);

			final int updateCount = this.db.update(Relations.DISPOSAL.n, cv,
					DISPOSAL_UPDATE_CLAUSE,
					new String[] { typeVal, String.valueOf(id), channel });
			if (updateCount > 0) {
				final Cursor cursor = this.db.query(Relations.DISPOSAL.n,
						new String[] { DisposalTableSchema._ID.n },
						DISPOSAL_UPDATE_CLAUSE,
						new String[] { typeVal, String.valueOf(id), channel },
						null, null, null);
				final int rowCount = cursor.getCount();
				if (rowCount > 1) {
					logger.error("you have a duplicates {} {}", rowCount, cv);
				}
				cursor.moveToFirst();
				final long key = cursor.getInt(0); // we only asked for one
													// column so it better be
													// it.
				cursor.close();
				return key;
			}
			return this.db.insert(Relations.DISPOSAL.n,
					DisposalTableSchema.TYPE.n, cv);
		} catch (IllegalArgumentException ex) {
			logger.error("upsert disposal {} {} {} {}", new Object[] { id,
					type, channel, status });
		}
		return 0;
	}

	static final private String DISPOSAL_UPDATE_CLAUSE = new StringBuilder()
			.append(DisposalTableSchema.TYPE.q()).append("=?").append(" AND ")
			.append(DisposalTableSchema.PARENT.q()).append("=?")
			.append(" AND ").append(DisposalTableSchema.CHANNEL.q())
			.append("=?").toString();

	public synchronized long upsertChannelByName(String channel,
			ChannelState status) {
		try {
			final ContentValues cv = new ContentValues();
			cv.put(ChannelTableSchema.STATE.cv(), status.cv());

			final int updateCount = this.db.update(Relations.CHANNEL.n, cv,
					CHANNEL_UPDATE_CLAUSE, new String[] { channel });
			if (updateCount > 0)
				return 0;

			cv.put(ChannelTableSchema.NAME.cv(), channel);
			db.insert(Relations.CHANNEL.n, ChannelTableSchema.NAME.n, cv);
		} catch (IllegalArgumentException ex) {
			logger.error("upsert channel {} {}", channel, status);
		}
		return 0;
	}

	static final private String CHANNEL_UPDATE_CLAUSE = new StringBuilder()
			.append(ChannelTableSchema.NAME.q()).append("=?").toString();

	/**
	 * These are related to upsertChannelByName() inasmuch as it resets the
	 * failed state to pending.
	 * 
	 * @param name
	 * @return the number of failed items updated
	 */
	static final private ContentValues DISPOSAL_PENDING_VALUES;
	static {
		DISPOSAL_PENDING_VALUES = new ContentValues();
		DISPOSAL_PENDING_VALUES.put(DisposalTableSchema.STATE.cv(),
				DisposalState.PENDING.o);
	}

	/**
	 * When a channel is deactivated all of its subscriptions will need to be
	 * re-done on re-connect. Retrievals and postals won't have this problem,
	 * TODO unless they are queued.
	 * 
	 * @param channel
	 * @return
	 */
	public synchronized int deactivateDisposalStateByChannel(String channel) {
		try {
			return this.db.update(Relations.DISPOSAL.n,
					DISPOSAL_PENDING_VALUES, DISPOSAL_DEACTIVATE_CLAUSE,
					new String[] { channel });
		} catch (IllegalArgumentException ex) {
			logger.error("deactivateDisposalStateByChannel {} ", channel);
		}
		return 0;
	}

	static final private String DISPOSAL_DEACTIVATE_CLAUSE;
	static {
		final RelationsHelper rel_subscribe = new RelationsHelper(
				Relations.SUBSCRIBE);
		DISPOSAL_DEACTIVATE_CLAUSE = new StringBuilder()
				.append(DisposalTableSchema.CHANNEL.q()).append("=?")
				.append(" AND ").append(DisposalTableSchema.TYPE.q())
				.append(" IN (").append(rel_subscribe.qv()).append(')')
				.append(" AND ").append(DisposalTableSchema.STATE.q())
				.append(" NOT IN (").append(DisposalState.BAD.q()).append(')')
				.toString();
	}

	/**
	 * When a channel is activated nothing really needs to be done. This method
	 * is provided as a place holder.
	 * 
	 * @param channel
	 * @return
	 */
	public int activateDisposalStateByChannel(String channel) {
		return -1;
	}

	/**
	 * When a channel is repaired it any failed requests may be retried.
	 * 
	 * @param channel
	 * @return
	 */
	public synchronized int repairDisposalStateByChannel(String channel) {
		try {
			return this.db.update(Relations.DISPOSAL.n,
					DISPOSAL_PENDING_VALUES, DISPOSAL_REPAIR_CLAUSE,
					new String[] { channel });
		} catch (IllegalArgumentException ex) {
			logger.error("repairDisposalStateByChannel {}", channel);
		}
		return 0;
	}

	static final private String DISPOSAL_REPAIR_CLAUSE = new StringBuilder()
			.append(DisposalTableSchema.CHANNEL.q()).append("=?")
			.append(" AND ").append(DisposalTableSchema.STATE.q())
			.append(" IN ( ").append(DisposalState.BAD.q()).append(')')
			.toString();

	/**
	 * Update an object represented in the database. Any reasonable update will
	 * need to know how to select an existing object.
	 */
	public synchronized long updatePostalByKey(long id, ContentValues cv,
			Dispersal state) {
		if (state == null && cv == null)
			return -1;
		if (cv == null)
			cv = new ContentValues();

		if (state != null) {
			this.upsertDisposalByParent(Relations.POSTAL, id, state);
			cv.put(PostalTableSchema.DISPOSITION.n, state.aggregate().cv());
		}
		try {
			return this.db.update(Relations.POSTAL.n, cv, "\"_id\"=?",
					new String[] { String.valueOf(id) });
		} catch (IllegalArgumentException ex) {
			logger.error("updatePostalByKey {} {}", id, cv);
		}
		return 0;
	}

	public synchronized long updatePostalByKey(long id, String channel,
			final DisposalState state) {
		// TODO update the parent object with the aggregate state
		return this
				.upsertDisposalByParent(Relations.POSTAL, id, channel, state);
	}

	public synchronized long updateRetrievalByKey(long id, ContentValues cv,
			final Dispersal state) {
		if (state == null && cv == null)
			return -1;
		if (cv == null)
			cv = new ContentValues();

		if (state != null) {
			this.upsertDisposalByParent(Relations.RETRIEVAL, id, state);
			cv.put(RetrievalTableSchema.DISPOSITION.n, state.aggregate().cv());
		}
		try {
			logger.trace("update retrieval by key {} {}", id, cv);
			return this.db.update(Relations.RETRIEVAL.n, cv, "\"_id\"=?",
					new String[] { String.valueOf(id) });
		} catch (IllegalArgumentException ex) {
			logger.error("updateRetrievalByKey {} {}", id, cv);
		}
		return 0;
	}

	public synchronized long updateRetrievalByKey(long id, String channel,
			final DisposalState state) {
		return this.upsertDisposalByParent(Relations.RETRIEVAL, id, channel,
				state);
	}

	public synchronized long updateSubscribeByKey(long id, ContentValues cv,
			final Dispersal state) {
		if (state == null && cv == null)
			return -1;
		if (cv == null)
			cv = new ContentValues();

		if (state != null) {
			this.upsertDisposalByParent(Relations.SUBSCRIBE, id, state);
			cv.put(SubscribeTableSchema.DISPOSITION.n, state.aggregate().cv());
		}
		try {
			return this.db.update(Relations.SUBSCRIBE.n, cv, "\"_id\"=?",
					new String[] { String.valueOf(id) });
		} catch (IllegalArgumentException ex) {
			logger.error("updateSubscribeByKey {} {}", id, cv);
		}
		return 0;
	}

	public synchronized long updateSubscribeByKey(long id, String channel,
			final DisposalState state) {
		return this.upsertDisposalByParent(Relations.SUBSCRIBE, id, channel,
				state);
	}

	/** Insert method helper */
	public ContentValues initializePostalDefaults(ContentValues values) {
		final Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(PostalTableSchema.TOPIC.n)) {
			values.put(PostalTableSchema.TOPIC.n, "unknown");
		}
		if (!values.containsKey(PostalTableSchema.PROVIDER.n)) {
			values.put(PostalTableSchema.PROVIDER.n, "unknown");
		}
		if (!values.containsKey(RetrievalTableSchema.DISPOSITION.n)) {
			values.put(RetrievalTableSchema.DISPOSITION.n,
					DisposalState.PENDING.o);
		}
		if (!values.containsKey(PostalTableSchema.NOTICE.n)) {
			values.put(PostalTableSchema.NOTICE.n, "");
		}
		if (!values.containsKey(PostalTableSchema.PRIORITY.n)) {
			values.put(PostalTableSchema.PRIORITY.n, PriorityType.NORMAL.o);
		}
		if (!values.containsKey(PostalTableSchema.ORDER.n)) {
			values.put(PostalTableSchema.ORDER.n, SerializeType.INDIRECT.o);
		}

		if (!values.containsKey(PostalTableSchema.EXPIRATION.n)) {
			values.put(PostalTableSchema.EXPIRATION.n, now
					+ DEFAULT_POSTAL_LIFESPAN);
		}
		if (!values.containsKey(PostalTableSchema.UNIT.n)) {
			values.put(PostalTableSchema.UNIT.n, "unknown");
		}
		if (!values.containsKey(PostalTableSchema.WORTH.n)) {
			values.put(PostalTableSchema.WORTH.n, -1);
		}
		if (!values.containsKey(PostalTableSchema.DATA.n)) {
			values.put(PostalTableSchema.DATA.n, "");
		}
		if (!values.containsKey(PostalTableSchema.CREATED.n)) {
			values.put(PostalTableSchema.CREATED.n, now);
		}
		if (!values.containsKey(PostalTableSchema.MODIFIED.n)) {
			values.put(PostalTableSchema.MODIFIED.n, now);
		}

		return values;
	}

	/** Insert method helper */
	protected ContentValues initializeRetrievalDefaults(ContentValues values) {
		final Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(RetrievalTableSchema.DISPOSITION.n)) {
			values.put(RetrievalTableSchema.DISPOSITION.n,
					DisposalState.PENDING.o);
		}
		if (!values.containsKey(RetrievalTableSchema.NOTICE.n)) {
			values.put(RetrievalTableSchema.NOTICE.n, "");
		}
		if (!values.containsKey(RetrievalTableSchema.PRIORITY.n)) {
			values.put(RetrievalTableSchema.PRIORITY.n, PriorityType.NORMAL.o);
		}
		if (!values.containsKey(RetrievalTableSchema.PROVIDER.n)) {
			values.put(RetrievalTableSchema.PROVIDER.n, "unknown");
		}
		if (!values.containsKey(RetrievalTableSchema.TOPIC.n)) {
			values.put(RetrievalTableSchema.TOPIC.n, "unknown");
		}
		if (!values.containsKey(RetrievalTableSchema.PROJECTION.n)) {
			values.put(RetrievalTableSchema.PROJECTION.n, "");
		}
		if (!values.containsKey(RetrievalTableSchema.SELECTION.n)) {
			values.put(RetrievalTableSchema.SELECTION.n, "");
		}
		if (!values.containsKey(RetrievalTableSchema.ARGS.n)) {
			values.put(RetrievalTableSchema.ARGS.n, "");
		}
		if (!values.containsKey(RetrievalTableSchema.ORDERING.n)) {
			values.put(RetrievalTableSchema.ORDERING.n, "");
		}
		if (!values.containsKey(RetrievalTableSchema.LIMIT.n)) {
			values.put(RetrievalTableSchema.LIMIT.n, -1);
		}
		if (!values.containsKey(RetrievalTableSchema.CONTINUITY_TYPE.n)) {
			values.put(RetrievalTableSchema.CONTINUITY_TYPE.n,
					ContinuityType.ONCE.o);
		}
		if (!values.containsKey(RetrievalTableSchema.CONTINUITY_VALUE.n)) {
			values.put(RetrievalTableSchema.CONTINUITY_VALUE.n, now);
		}
		if (!values.containsKey(RetrievalTableSchema.EXPIRATION.n)) {
			values.put(RetrievalTableSchema.EXPIRATION.n, now
					+ DEFAULT_RETRIEVAL_LIFESPAN);
		}
		if (!values.containsKey(RetrievalTableSchema.CREATED.n)) {
			values.put(RetrievalTableSchema.CREATED.n, now);
		}
		if (!values.containsKey(RetrievalTableSchema.MODIFIED.n)) {
			values.put(RetrievalTableSchema.MODIFIED.n, now);
		}

		return values;
	}

	/** Insert method helper */
	protected ContentValues initializeSubscriptionDefaults(ContentValues values) {
		final Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(SubscribeTableSchema.DISPOSITION.n)) {
			values.put(SubscribeTableSchema.DISPOSITION.n,
					DisposalState.PENDING.o);
		}
		if (!values.containsKey(SubscribeTableSchema.PROVIDER.n)) {
			values.put(SubscribeTableSchema.PROVIDER.n, "unknown");
		}
		if (!values.containsKey(SubscribeTableSchema.TOPIC.n)) {
			values.put(SubscribeTableSchema.TOPIC.n, "unknown");
		}

		if (!values.containsKey(SubscribeTableSchema.SELECTION.n)) {
			values.put(SubscribeTableSchema.SELECTION.n, "");
		}
		if (!values.containsKey(SubscribeTableSchema.EXPIRATION.n)) {
			values.put(SubscribeTableSchema.EXPIRATION.n, now
					+ DEFAULT_SUBSCRIBE_LIFESPAN);
		}
		if (!values.containsKey(SubscribeTableSchema.NOTICE.n)) {
			values.put(SubscribeTableSchema.NOTICE.n, "");
		}
		if (!values.containsKey(SubscribeTableSchema.PRIORITY.n)) {
			values.put(SubscribeTableSchema.PRIORITY.n, PriorityType.NORMAL.o);
		}
		if (!values.containsKey(SubscribeTableSchema.CREATED.n)) {
			values.put(SubscribeTableSchema.CREATED.n, now);
		}
		if (!values.containsKey(SubscribeTableSchema.MODIFIED.n)) {
			values.put(SubscribeTableSchema.MODIFIED.n, now);
		}
		return values;
	}

	/**
	 * Delete set
	 * 
	 * @param cv
	 * @return
	 */

	// ======== HELPER ============
	/**
	 * Compute the expiration time which is the current time plus some lifespan.
	 * Return the result as an array of strings.
	 * 
	 * @param lifespan
	 * @return
	 */
	static private String[] deriveExpirationTime(long lifespan) {
		final long absTime = System.currentTimeMillis() + lifespan;
		return new String[] { String.valueOf(absTime) };
	}

	private static final String DISPOSAL_PURGE = new StringBuilder()
			.append(DisposalTableSchema.TYPE.q()).append('=').append('?')
			.toString();

	// ========= POSTAL : DELETE ================

	public synchronized int deletePostal(String selection,
			String[] selectionArgs) {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int count = db.delete(Relations.POSTAL.n, selection,
					selectionArgs);
			final int disposalCount = db.delete(Relations.DISPOSAL.n,
					DISPOSAL_POSTAL_ORPHAN_CONDITION, null);
			logger.trace("Postal delete {} {}", count, disposalCount);
			return count;
		} catch (IllegalArgumentException ex) {
			logger.error("delete postal {} {}", selection, selectionArgs);
		}
		return 0;
	}

	public synchronized int deletePostalGarbage() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int expireCount = db.delete(Relations.POSTAL.n,
					POSTAL_EXPIRATION_CONDITION, deriveExpirationTime(0));
			final int disposalCount = db.delete(Relations.DISPOSAL.n,
					DISPOSAL_POSTAL_ORPHAN_CONDITION, null);
			logger.trace("Postal garbage {} {}", expireCount, disposalCount);
			return expireCount;
		} catch (IllegalArgumentException ex) {
			logger.error("deletePostalGarbage", ex);
		} catch (SQLiteException ex) {
			logger.error("deletePostalGarbage", ex);
		}
		return 0;
	}

	private static final String DISPOSAL_POSTAL_ORPHAN_CONDITION;
	static {
		final RelationsHelper rel_postal = new RelationsHelper(Relations.POSTAL);
		DISPOSAL_POSTAL_ORPHAN_CONDITION = new StringBuilder()
				.append(DisposalTableSchema.TYPE.q()).append('=')
				.append(rel_postal.cv()).append(" AND NOT EXISTS (SELECT * ")
				.append(" FROM ").append(rel_postal.q()).append(" WHERE ")
				.append(DisposalTableSchema.PARENT.q()).append('=')
				.append(rel_postal.q()).append(".")
				.append(PostalTableSchema._ID.q()).append(')').toString();
	}

	private static final String POSTAL_EXPIRATION_CONDITION = new StringBuilder()
			.append('"').append(PostalTableSchema.EXPIRATION.n).append('"')
			.append('<').append('?').toString();

	public static final long DEFAULT_POSTAL_LIFESPAN = CONVERT_MINUTES_TO_MILLISEC * 8 * 60; // 8
																								// hr

	// ========= RETRIEVAL : DELETE ================

	public synchronized int deleteRetrieval(String selection,
			String[] selectionArgs) {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int count = db.delete(Relations.RETRIEVAL.n, selection,
					selectionArgs);
			final int disposalCount = db.delete(Relations.DISPOSAL.n,
					DISPOSAL_RETRIEVAL_ORPHAN_CONDITION, null);
			logger.trace("Retrieval delete {} {}", count, disposalCount);
			return count;
		} catch (IllegalArgumentException ex) {
			logger.error("delete postal {} {}", selection, selectionArgs);
		}
		return 0;
	}

	public synchronized int deleteRetrievalGarbage() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int expireCount = db.delete(Relations.RETRIEVAL.n,
					RETRIEVAL_EXPIRATION_CONDITION, deriveExpirationTime(0));
			final int disposalCount = db.delete(Relations.DISPOSAL.n,
					DISPOSAL_RETRIEVAL_ORPHAN_CONDITION, null);
			logger.trace("Retrieval garbage {} {}", expireCount, disposalCount);
			return expireCount;
		} catch (IllegalArgumentException ex) {
			logger.error("deleteRetrievalGarbage", ex);
		} catch (SQLiteException ex) {
			logger.error("deleteRetrievalGarbage", ex);
		}

		return 0;
	}

	private static final String DISPOSAL_RETRIEVAL_ORPHAN_CONDITION;
	static {
		final RelationsHelper rel_retrieval = new RelationsHelper(
				Relations.RETRIEVAL);
		DISPOSAL_RETRIEVAL_ORPHAN_CONDITION = new StringBuilder()
				.append(DisposalTableSchema.TYPE.q()).append('=')
				.append(rel_retrieval.cv())
				.append(" AND NOT EXISTS (SELECT * ").append(" FROM ")
				.append(rel_retrieval.q()).append(" WHERE ")
				.append(DisposalTableSchema.PARENT.q()).append('=')
				.append(rel_retrieval.q()).append(".")
				.append(RetrievalTableSchema._ID.q()).append(')').toString();
	}

	private static final String RETRIEVAL_EXPIRATION_CONDITION = new StringBuilder()
			.append('"').append(RetrievalTableSchema.EXPIRATION.n).append('"')
			.append('<').append('?').toString();

	public static final long DEFAULT_RETRIEVAL_LIFESPAN = CONVERT_MINUTES_TO_MILLISEC * 30; // 30
																							// minutes

	/**
	 * purge all records from the retrieval table and cascade to the disposal
	 * table.
	 * 
	 * @return
	 */
	public synchronized int purgeRetrieval() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			db.delete(Relations.DISPOSAL.n, DISPOSAL_PURGE,
					new String[] { new RelationsHelper(Relations.RETRIEVAL)
							.qv() });
			return db.delete(Relations.RETRIEVAL.n, null, null);
		} catch (IllegalArgumentException ex) {
			logger.error("purgeRetrieval");
		}
		return 0;
	}

	// ========= SUBSCRIBE : DELETE ================

	public synchronized int deleteSubscribe(String selection,
			String[] selectionArgs) {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int count = db.delete(Relations.SUBSCRIBE.n, selection,
					selectionArgs);
			final int disposalCount = db.delete(Relations.DISPOSAL.n,
					DISPOSAL_SUBSCRIBE_ORPHAN_CONDITION, null);
			logger.trace("Subscribe delete {} {}", count, disposalCount);
			return count;
		} catch (IllegalArgumentException ex) {
			logger.error("delete postal {} {}", selection, selectionArgs);
		}
		return 0;
	}

	public synchronized int deleteSubscribeGarbage() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int expireCount = db.delete(Relations.SUBSCRIBE.n,
					SUBSCRIBE_EXPIRATION_CONDITION, deriveExpirationTime(0));
			final int disposalCount = db.delete(Relations.DISPOSAL.n,
					DISPOSAL_SUBSCRIBE_ORPHAN_CONDITION, null);
			logger.trace("Subscribe garbage {} {} {}", new Object[] {
					expireCount, disposalCount,
					DISPOSAL_SUBSCRIBE_ORPHAN_CONDITION });
			return expireCount;
		} catch (IllegalArgumentException ex) {
			logger.error("deleteSubscribeGarbage", ex);
		} catch (SQLiteException ex) {
			logger.error("deleteSubscribeGarbage", ex);
		}
		return 0;
	}

	private static final String DISPOSAL_SUBSCRIBE_ORPHAN_CONDITION;
	static {
		final RelationsHelper rel_subscribe = new RelationsHelper(
				Relations.SUBSCRIBE);
		DISPOSAL_SUBSCRIBE_ORPHAN_CONDITION = new StringBuilder()
				.append(DisposalTableSchema.TYPE.q()).append('=')
				.append(rel_subscribe.cv())
				.append(" AND NOT EXISTS (SELECT * ").append(" FROM ")
				.append(rel_subscribe.q()).append(" WHERE ")
				.append(DisposalTableSchema.PARENT.q()).append('=')
				.append(rel_subscribe.q()).append(".")
				.append(SubscribeTableSchema._ID.q()).append(')').toString();
	}

	private static final String SUBSCRIBE_EXPIRATION_CONDITION = new StringBuilder()
			.append('"').append(SubscribeTableSchema.EXPIRATION.n).append('"')
			.append('<').append('?').toString();

	public static final long DEFAULT_SUBSCRIBE_LIFESPAN = Long.MAX_VALUE; // never

	// CONVERT_MINUTES_TO_MILLISEC * 365 * 24 * 60; // 1 year

	/**
	 * purge all records from the subscribe table and cascade to the disposal
	 * table.
	 * 
	 * @return
	 */
	public synchronized int purgeSubscribe() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			db.delete(Relations.DISPOSAL.n, DISPOSAL_PURGE,
					new String[] { new RelationsHelper(Relations.SUBSCRIBE)
							.qv() });
			return db.delete(Relations.SUBSCRIBE.n, null, null);
		} catch (IllegalArgumentException ex) {
			logger.error("purgeSubscribe");
		}
		return 0;
	}

	public final File applDir;
	public final File applCacheDir;
	public final File applCachePostalDir;
	public final File applCacheRetrievalDir;
	public final File applCachePublicationDir;
	public final File applCacheSubscriptionDir;
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
		private final Logger logger = LoggerFactory
				.getLogger("dist.store.helper");

		// ===========================================================
		// Fields
		// ===========================================================

		// ===========================================================
		// Constructors
		// ===========================================================
		public DataStoreHelper(Context context, String name,
				CursorFactory factory, int version) {
			super(context, name, factory, version);
		}

		// ===========================================================
		// SQLiteOpenHelper Methods
		// ===========================================================

		@Override
		public synchronized void onCreate(SQLiteDatabase db) {
			logger.trace("bootstrapping database");

			try {
				final StringBuilder sb = new StringBuilder();
				for (PostalTableSchema field : PostalTableSchema.values()) {
					if (sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(RelationsHelper.sqlCreate(Relations.POSTAL,
						sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				final StringBuilder sb = new StringBuilder();
				for (RetrievalTableSchema field : RetrievalTableSchema.values()) {
					if (sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(RelationsHelper.sqlCreate(Relations.RETRIEVAL,
						sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				final StringBuilder sb = new StringBuilder();
				for (SubscribeTableSchema field : SubscribeTableSchema.values()) {
					if (sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(RelationsHelper.sqlCreate(Relations.SUBSCRIBE,
						sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				final StringBuilder sb = new StringBuilder();
				for (DisposalTableSchema field : DisposalTableSchema.values()) {
					if (sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(RelationsHelper.sqlCreate(Relations.DISPOSAL,
						sb.toString()).toString());

				// === INDICIES ======
				final RelationsHelper rel_disposal = new RelationsHelper(
						Relations.DISPOSAL);
				db.execSQL(new StringBuilder().append("CREATE UNIQUE INDEX ")
						.append(rel_disposal.qIndex()).append(" ON ")
						.append(rel_disposal.q()).append(" ( ")
						.append(DisposalTableSchema.TYPE.q()).append(" , ")
						.append(DisposalTableSchema.PARENT.q()).append(" , ")
						.append(DisposalTableSchema.CHANNEL.q()).append(" ) ")
						.toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				final StringBuilder sb = new StringBuilder();
				for (ChannelTableSchema field : ChannelTableSchema.values()) {
					if (sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(RelationsHelper.sqlCreate(Relations.CHANNEL,
						sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			// === PRELOAD ======

			// === VIEWS ======

		}

		@Override
		public synchronized void onUpgrade(SQLiteDatabase db, int oldVersion,
				int newVersion) {
			logger.warn(
					"Upgrading database from version {} to {} which will destroy all old data",
					oldVersion, newVersion);
			this.clear(db);

			onCreate(db);
		}

		/**
		 * Called when the database has been opened. The implementation checks
		 * isReadOnly() before updating the database.
		 */
		@Override
		public void onOpen(SQLiteDatabase db) {
			// Examine or otherwise prepare the database
		}

		@Override
		public synchronized SQLiteDatabase getWritableDatabase() {
			try {
				return super.getWritableDatabase();

			} catch (SQLiteDiskIOException ex) {
				logger.error("corrupted database", ex);
			}
			try {
				this.archive();
				return super.getReadableDatabase();
			} catch (SQLiteException ex) {
				logger.error("unrecoverablly corrupted database", ex);
			}
			return null;
		}

		@Override
		public synchronized SQLiteDatabase getReadableDatabase() {
			try {
				return super.getReadableDatabase();
			} catch (SQLiteDiskIOException ex) {
				logger.error("corrupted database", ex);
			}
			try {
				this.archive();
				return super.getReadableDatabase();
			} catch (SQLiteException ex) {
				logger.error("unrecoverablly corrupted database", ex);
			}
			return null;
		}

		public synchronized void clear(SQLiteDatabase db) {
			for (Relations table : Relations.values()) {
				try {
					db.execSQL(RelationsHelper.sqlDrop(table).toString());
				} catch (SQLiteException ex) {
					logger.warn("defective database being dropped", ex);
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
			if (backup.exists())
				backup.delete();

			final File original = context
					.getDatabasePath(DistributorDataStore.SQLITE_NAME);
			logger.info("backup of database {} -> {}", original, backup);
			if (original.renameTo(backup)) {
				logger.info("archival succeeded");
				return true;
			}
			if (!context.deleteDatabase(DistributorDataStore.SQLITE_NAME)) {
				logger.warn("file should have been renamed, deleted instead");
			}
			return false;
		}
	}

	/**
	 * Build a cursor to present the presence.
	 * 
	 * @return
	 */
	public Cursor queryPresenceAll() {
		final Presence collection = Presence.INSTANCE;
		final MatrixCursor cursor = new MatrixCursor(
				PresenceSchema.FIELD_NAMES, collection.size());
		final EnumSet<PresenceSchema> set = EnumSet.allOf(PresenceSchema.class);
		for (final Presence.Item item : Presence.queryAll()) {
			cursor.addRow(item.getValues(set));
		}
		return cursor;
	}

	/**
	 * Same as queryPresence() but with a filter.
	 * 
	 * @param operator
	 * @return
	 */
	public Cursor queryPresenceByOperator(String operator) {
		final Presence collection = Presence.INSTANCE;
		final MatrixCursor cursor = new MatrixCursor(
				PresenceSchema.FIELD_NAMES, collection.size());
		final EnumSet<PresenceSchema> set = EnumSet.allOf(PresenceSchema.class);
		for (final Presence.Item item : Presence.queryAll()) {
			if (!item.key.operator.equals(operator))
				continue;
			cursor.addRow(item.getValues(set));
		}
		return cursor;
	}

	/**
	 * Build a cursor to present the capability.
	 * 
	 * @return
	 */
	public Cursor queryCapabilityAll() {
		final Capability collection = Capability.INSTANCE;
		final MatrixCursor cursor = new MatrixCursor(
				CapabilitySchema.FIELD_NAMES, collection.size());
		final EnumSet<CapabilitySchema> set = EnumSet
				.allOf(CapabilitySchema.class);
		for (final Capability.Item item : Capability.queryAll()) {
			cursor.addRow(item.getValues(set));
		}
		return cursor;
	}

	public int deletePresence() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int deleteCapability() {
		// TODO Auto-generated method stub
		return 0;
	}

}

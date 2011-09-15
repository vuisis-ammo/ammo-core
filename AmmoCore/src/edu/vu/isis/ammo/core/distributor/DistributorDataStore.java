package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.text.TextUtils;
import edu.vu.isis.ammo.core.distributor.DistributorThread.ChannelState;

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
	private final static Logger logger = LoggerFactory.getLogger("ammo:dds");
	public static final int VERSION = 13;

	// ===========================================================
	// Fields
	// ===========================================================
	private final Context context;
	private SQLiteDatabase db;
	private final MyHelper helper;
	// ===========================================================
	// Schema
	// ===========================================================

	/**
	 * Data Store Table definitions
	 * 
	 * The postal and publish tables record requests that data be sent out.
	 * POSTed data is specifically named and distributed.
	 * PUBLISHed data is observed and as it changes in the content provider it is delivered.
	 * The retrieval and subscribe tables record request that data be obtained.
	 * RETRIEVAL data is obtained from a source.
	 * SUBSCRIBEd data is obtained by topic.
	 * 
	 * The disposition table keeps track of the status of the delivery.
	 * It is used in conjunction with the distribution policy.
	 * The disposition table may have several entries for each request.
	 * There is one row for each potential channel over which the 
	 * request could be sent.
	 * There will be one row for each potential channel from the policy.
	 * As the channel is used it will be marked.
	 * Once all clauses which may use a channel become true the 
	 * clauses are removed.
	 * The rule for disposition rows is cascade delete.
	 */
	public enum Tables {
		POSTAL(1, "postal"),
		PUBLISH(2, "publication"),
		RETRIEVAL(3, "retrieval"),
		SUBSCRIBE(4, "subscription"),
		DISPOSAL(5, "disposition"),
		CHANNEL(6, "channel");

		final public int o;
		final public String n;

		private Tables(int ordinal, String name) {
			this.o = ordinal;
			this.n = name;
		}

		public static final String NAME = "distributor.db";

		public String q() {
			return new StringBuilder().append('"').append(this.n).append('"').toString();
		}
		public String qv() {
			return new StringBuilder().append('\'').append(this.n).append('\'').toString();
		}
		/**
		 * Produce string builders of the form...
		 * CREATE TABLE "<table-name>" ( <row defs> );
		 *
		 */
		public String sqlCreate(String fields) {
			return new StringBuilder()
			.append("CREATE TABLE ")
			.append('"').append(this.n).append('"')
			.append(" (").append(fields).append(");")
			.toString();
		}

		/**
		 * Produce string builders of the form...
		 * DROP TABLE "<table-name>";
		 *
		 */
		public String sqlDrop() {
			return new StringBuilder()
			.append("DROP TABLE ")
			.append('"').append(this.n).append('"')
			.append(";")
			.toString();
		}
		public String cv() {
			return this.n;
		}
	};


	// ===========================================================
	// Enumerated types in the tables.
	// ===========================================================

	/**
	 * Indicates if the provider indicates a table entry or whether the
	 * data has been pre-serialized.
	 */
	public enum SerializeType {
		DIRECT(1),
		// the serialized data is found in the data field (or a suitable file)

		INDIRECT(2),
		// the serialized data is obtained from the named provider uri

		DEFERRED(3);
		// the same as INDIRECT but the serialization doesn't happen until the data is sent.

		public int o; // ordinal

		private SerializeType(int o) {
			this.o = o;
		}
		/**
		 * Produce string of the form...
		 * '<field-ordinal-value>';
		 *
		 */
		public String quote() {
			return new StringBuilder().append("'").append(this.o).append("'").toString();
		}
		public static SerializeType byOrdinal(int serialType) {
			switch(serialType) {
			case 1:
				return DIRECT;
			case 2:
				return INDIRECT;
			case 3:
				return DEFERRED;
			}
			throw new IllegalArgumentException("unknown SerialType "+Integer.toString(serialType));
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

	/**
	 * Status of the entry (sent or not sent).
	 * <P>
	 * Type: EXCLUSIVE
	 * </P>
	 */

	public enum DisposalState {
		PENDING(1, "PENDING"),
		QUEUED(2, "QUEUED"),
		SENT(3, "SENT"),
		JOURNAL(4, "JOURNAL"),
		FAIL(5, "FAIL"),
		EXPIRED(6, "EXPIRED"),
		SATISFIED(7, "SATISFIED");

		final public int o;
		final public String t;

		private DisposalState(int ordinal, String title) {
			this.o = ordinal;
			this.t = title;
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
		FLASH(128, "FLASH"),
		URGENT(64, "URGENT"),
		IMPORTANT(32, "IMPORTANT"),
		NORMAL(16, "NORMAL"),
		BACKGROUND(8, "BACKGROUND");

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
	}

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
	}

	// ===========================================================
	// Enumerated types in the tables.
	// ===========================================================

	/**
	 * The postal table is for holding retrieval requests.
	 */
	public static interface PostalTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[PostalTableSchema.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(PostalTableSchema.values().length);
	};
	static {
		int ix = 0;
		for (PostalTableSchema field : PostalTableSchema.values()) {
			PostalTable.COLUMNS[ix++] = field.n;
			PostalTable.PROJECTION_MAP.put(field.n, field.n);
		}
	};

	private static final String POSTAL_GARBAGE = new StringBuilder()
	.append(PostalTableSchema.DISPOSITION.q())
	.append(" IN (")
	.append(DisposalState.SATISFIED.q()).append(",")
	.append(DisposalState.EXPIRED.q()).append(")")
	.toString();

	private static final String POSTAL_RESEND = new StringBuilder()
	.append(PostalTableSchema.DISPOSITION.q())
	.append(" IN (")
	.append(DisposalState.PENDING.q()).append(",")
	.append(DisposalState.QUEUED.q()).append(",")
	.append(DisposalState.FAIL.q()).append(")")
	.toString();

	private static final String POSTAL_SEND = new StringBuilder()
	.append(PostalTableSchema.DISPOSITION.q())
	.append(" IN (")
	.append(DisposalState.PENDING.q()).append(")")
	.toString();

	private static final String POSTAL_EXPIRATION_CONDITION; 
	private static final ContentValues POSTAL_EXPIRATION_UPDATE;
	static {
		POSTAL_EXPIRATION_CONDITION = new StringBuilder()
		.append('"').append(PostalTableSchema.EXPIRATION.n).append('"')
		.append('<').append('?')
		.toString();

		POSTAL_EXPIRATION_UPDATE = new ContentValues();
		POSTAL_EXPIRATION_UPDATE.put(PostalTableSchema.DISPOSITION.cv(), DisposalState.EXPIRED.cv());
	}

	public enum PostalTableSchema  {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CREATED("created", "INTEGER"),
		// When the request was made

		MODIFIED("modified", "INTEGER"),
		// When the request was last modified

		TOPIC("topic", "TEXT"),
		// This along with the cost is used to decide how to deliver the specific object.

		PROVIDER("provider", "TEXT"),
		// The uri of the content provider

		PAYLOAD("payload", "TEXT"),
		// The payload instead of content provider

		NOTICE("notice", "BLOB"),
		// A description of what is to be done when various state-transition occur.

		PRIORITY("priority", "INTEGER"),
		// What order should this message be sent. Negative priorities indicated less than normal.

		ORDER("serialize_type", "INTEGER"),

		DISPOSITION("disposition", "INTEGER"),
		// The current best guess of the status of the request.

		EXPIRATION("expiration", "INTEGER"),
		// Time-stamp at which point entry becomes stale.

		UNIT("unit", "TEXT"),
		// Units associated with {@link #VALUE}. Used to determine whether should occur.

		VALUE("value", "INTEGER"),
		// Arbitrary value linked to importance that entry is transmitted and battery drain.

		DATA("data", "TEXT");
		// If the If null then the data file corresponding to the
		// column name and record id should be used. This is done when the data
		// size is larger than that allowed for a field contents.

		final public String n; // name
		final public String t; // type

		private PostalTableSchema(String n, String t) {
			this.n = n;
			this.t = t;
		}
		/**
		 * Produce string of the form...
		 * "<field-name>" <field-type>
		 * e.g.
		 * "dog" TEXT
		 */
		public String addfield() {
			return new StringBuilder()
			.append('"').append(this.n).append('"').append(' ').append(this.t)
			.toString();
		}
		/**
		 * Produce string of the form...
		 * "<field-name>"
		 */
		public String q() {
			return new StringBuilder()
			.append('"').append(this.n).append('"')
			.toString();
		}
		public String cv() {
			return String.valueOf(this.n);
		}
	};


	/**
	 * The publication table is for holding publication requests.
	 */
	public static interface PublishTable extends BaseColumns {
		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[PublishTableSchema.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(PublishTableSchema.values().length);
	}
	static {
		int ix = 0;
		for (PublishTableSchema field : PublishTableSchema.values()) {
			PublishTable.COLUMNS[ix++] = field.n;
			PublishTable.PROJECTION_MAP.put(field.n, field.n);
		}
	}

	public enum PublishTableSchema {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CREATED("created", "INTEGER"),
		// When the request was made

		MODIFIED("modified", "INTEGER"),
		// When the request was last modified

		TOPIC("topic", "TEXT"),
		// This along with the cost is used to decide how to deliver the specific object.

		PROVIDER("provider", "TEXT"),
		// The uri of the content provider

		DISPOSITION("disposition", "INTEGER"),
		// The current best guess of the status of the request.

		EXPIRATION("expiration", "INTEGER");
		// Time-stamp at which request entry becomes stale.

		final public String n; // name
		final public String t; // type

		private PublishTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}
		/**
		 * Produce string of the form...
		 * "<field-name>" <field-type>
		 * e.g.
		 * "dog" TEXT
		 */
		public String addfield() {
			return new StringBuilder()
			.append('"').append(this.n).append('"').append(' ').append(this.t)
			.toString();
		}
		/**
		 * Produce string of the form...
		 * "<field-name>"
		 */
		public String q() {
			return new StringBuilder()
			.append('"').append(this.n).append('"')
			.toString();
		}
		public String cv() {
			return String.valueOf(this.n);
		}
	}

	/**
	 * The retrieval table is for holding retrieval requests.
	 */
	public static interface RetrievalTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[RetrievalTableSchema.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(RetrievalTableSchema.values().length);
	};
	static {
		int ix = 0;
		for (RetrievalTableSchema field : RetrievalTableSchema.values()) {
			RetrievalTable.COLUMNS[ix++] = field.n;
			RetrievalTable.PROJECTION_MAP.put(field.n, field.n);
		}
	};


	private static final String RETRIEVAL_GARBAGE;
	private static final String RETRIEVAL_SEND;
	private static final String RETRIEVAL_RESEND;

	static {
		RETRIEVAL_GARBAGE = new StringBuilder()
		.append(RetrievalTableSchema.DISPOSITION.q())
		.append(" IN (")
		.append(DisposalState.SATISFIED.q()).append(",")
		.append(DisposalState.EXPIRED.q()).append(")")
		.toString();

		RETRIEVAL_SEND = new StringBuilder()
		.append(RetrievalTableSchema.DISPOSITION.q())
		.append(" IN (")
		.append(DisposalState.PENDING.q()).append(",")
		.append(DisposalState.FAIL.q()).append(")")
		.toString();

		RETRIEVAL_RESEND = new StringBuilder()
		.append(RetrievalTableSchema.DISPOSITION.q())
		.append(" IN (")
		.append(DisposalState.PENDING.q()).append(",")
		.append(DisposalState.QUEUED.q()).append(",")
		.append(DisposalState.FAIL.q()).append(",")
		.append(DisposalState.SENT.q()).append(")")
		.toString();
	}

	private static final String RETRIEVAL_EXPIRATION_CONDITION; 
	private static final ContentValues RETRIEVAL_EXPIRATION_UPDATE;
	static {
		RETRIEVAL_EXPIRATION_CONDITION = new StringBuilder()
		.append(RetrievalTableSchema.EXPIRATION.q())
		.append('<').append('?')
		.toString();

		RETRIEVAL_EXPIRATION_UPDATE= new ContentValues();
		RETRIEVAL_EXPIRATION_UPDATE.put(RetrievalTableSchema.DISPOSITION.cv(), DisposalState.EXPIRED.cv());
	}


	public enum RetrievalTableSchema {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CREATED("created", "INTEGER"),
		// When the request was made

		MODIFIED("modified", "INTEGER"),
		// When the request was last modified

		TOPIC("topic", "TEXT"),
		// This along with the cost is used to decide how to deliver the specific object.

		PROVIDER("provider", "TEXT"),
		// The uri of the content provider

		NOTICE("notice", "BLOB"),
		// A description of what is to be done when various state-transition occur.

		PRIORITY("priority", "INTEGER"),
		// What order should this message be sent. Negative priorities indicated less than normal.

		PROJECTION("projection", "TEXT"),
		// The fields/columns wanted.

		SELECTION("selection", "TEXT"),
		// The rows/tuples wanted.

		ARGS("args", "TEXT"),
		// The values using in the selection.

		ORDERING("ordering", "TEXT"),
		// The order the values are to be returned in.

		DISPOSITION("disposition", "INTEGER"),
		// The current best guess of the status of the request.

		EXPIRATION("expiration", "INTEGER"),
		// Time-stamp at which request entry becomes stale.

		UNIT("unit", "TEXT"),
		// Units associated with {@link #VALUE}. Used to determine whether should occur.

		VALUE("value", "INTEGER"),
		// Arbitrary value linked to importance that entry is transmitted and battery drain.

		DATA("data", "TEXT"),
		// If the If null then the data file corresponding to the
		// column name and record id should be used. This is done when the data
		// size is larger than that allowed for a field contents.

		CONTINUITY_TYPE("continuity_type", "INTEGER"),
		CONTINUITY_VALUE("continuity_value", "INTEGER");
		// The meaning changes based on the continuity type.
		// - ONCE : undefined
		// - TEMPORAL : chronic, this differs slightly from the expiration
		//      which deals with the request this deals with the time stamps
		//      of the requested objects.
		// - QUANTITY : the maximum number of objects to return

		final public String n; // name
		final public String t; // type

		private RetrievalTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}
		/**
		 * Produce string of the form...
		 * "<field-name>" <field-type>
		 * e.g.
		 * "dog" TEXT
		 */
		public String addfield() {
			return new StringBuilder()
			.append('"').append(this.n).append('"').append(' ').append(this.t)
			.toString();
		}
		/**
		 * Produce string of the form...
		 * "<field-name>"
		 */
		public String q() {
			return new StringBuilder()
			.append('"').append(this.n).append('"')
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
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[SubscribeTableSchema.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(SubscribeTableSchema.values().length);
	}
	static {
		int ix = 0;
		for (SubscribeTableSchema field : SubscribeTableSchema.values()) {
			SubscribeTable.COLUMNS[ix++] = field.n;
			SubscribeTable.PROJECTION_MAP.put(field.n, field.n);
		}
	}
	static final private String SELECT_SUBSCRIPTION_URI;
	static final private String SELECT_SUBSCRIPTION_TYPE;
	static final private String SELECT_SUBSCRIPTION_URI_TYPE;
	static {
		SELECT_SUBSCRIPTION_URI = "\""+SubscribeTableSchema.PROVIDER.n +"\"=?";
		SELECT_SUBSCRIPTION_TYPE = "\"" + SubscribeTableSchema.TOPIC.n + "\"=?";
		SELECT_SUBSCRIPTION_URI_TYPE = SELECT_SUBSCRIPTION_URI + " AND " + SELECT_SUBSCRIPTION_TYPE;
	}

	private static final String SUBSCRIPTION_GARBAGE;
	private static final String SUBSCRIPTION_RESEND;
	private static final String SUBSCRIPTION_SEND;

	static {
		SUBSCRIPTION_GARBAGE = new StringBuilder()
		.append(SubscribeTableSchema.DISPOSITION.q())
		.append(" IN (")
		.append(DisposalState.EXPIRED.q()).append(")")
		.toString();

		SUBSCRIPTION_SEND = new StringBuilder()
		.append(SubscribeTableSchema.DISPOSITION.q())
		.append(" IN (")
		.append(DisposalState.PENDING.q()).append(",")
		.append(DisposalState.FAIL.q()).append(")")           
		.toString();

		SUBSCRIPTION_RESEND = new StringBuilder()
		.append(SubscribeTableSchema.DISPOSITION.q())
		.append(" IN (")
		.append(DisposalState.PENDING.q()).append(",")
		.append(DisposalState.QUEUED.q()).append(",")
		.append(DisposalState.FAIL.q()).append(",")
		.append(DisposalState.SENT.q()).append(")")
		.toString();
	}

	private static final String SUBSCRIPTION_EXPIRATION_CONDITION; 
	private static final ContentValues SUBSCRIPTION_EXPIRIATION_UPDATE;
	static {
		StringBuilder sb = new StringBuilder();
		sb.append('"').append(SubscribeTableSchema.EXPIRATION).append('"')
		.append('<').append('?');
		SUBSCRIPTION_EXPIRATION_CONDITION = sb.toString();

		SUBSCRIPTION_EXPIRIATION_UPDATE= new ContentValues();
		SUBSCRIPTION_EXPIRIATION_UPDATE.put(SubscribeTableSchema.DISPOSITION.cv(),
				DisposalState.EXPIRED.cv());
	}


	public enum SubscribeTableSchema {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CREATED("created", "INTEGER"),
		// When the request was made

		MODIFIED("modified", "INTEGER"),
		// When the request was last modified

		TOPIC("topic", "TEXT"),
		// This along with the cost is used to decide how to deliver the specific object.

		PROVIDER("provider", "TEXT"),
		// The uri of the content provider

		DISPOSITION("disposition", "INTEGER"),
		// The current best guess of the status of the request.

		EXPIRATION("expiration", "INTEGER"),
		// Time-stamp at which request entry becomes stale.

		SELECTION("selection", "TEXT"),
		// The rows/tuples wanted.

		NOTICE("notice", "BLOB"),
		// A description of what is to be done when various state-transition occur.

		PRIORITY("priority", "INTEGER");
		// What order should this message be sent. Negative priorities indicated less than normal.


		final public String n; // name
		final public String t; // type

		private SubscribeTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}
		/**
		 * Produce string of the form...
		 * "<field-name>" <field-type>
		 * e.g.
		 * "dog" TEXT
		 */

		public String addfield() {
			return new StringBuilder()
			.append('"').append(this.n).append('"').append(' ').append(this.t)
			.toString();
		}
		/**
		 * Produce string of the form...
		 * "<field-name>"
		 */
		public String q() {
			return new StringBuilder()
			.append('"').append(this.n).append('"')
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
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[DisposalTableSchema.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(DisposalTableSchema.values().length);
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

		TYPE("type", "INTEGER"),
		// Meaning the parent type: subscribe, retrieval, postal, publish

		PARENT("parent", "INTEGER"),
		// The _id of the parent

		CHANNEL("channel", "TEXT"),
		// The name of the channel over which the message could be sent

		STATE("state", "INTEGER");
		// Where the request is on the channel


		final public String n; // name
		final public String t; // type

		private DisposalTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}
		/**
		 * Produce string of the form...
		 * "<field-name>" <field-type>
		 * e.g.
		 * "dog" TEXT
		 */

		public String addfield() {
			return new StringBuilder()
			.append('"').append(this.n).append('"').append(' ').append(this.t)
			.toString();
		}
		/**
		 * Produce string of the form...
		 * "<field-name>"
		 */
		public String q() {
			return new StringBuilder()
			.append('"').append(this.n).append('"')
			.toString();
		}
		public String cv() {
			return String.valueOf(this.n);
		}
	}


	/**
	 * The channel table is for holding current channel status.
	 * This could be done with a concurrent hash map but that
	 * would put more logic in the java code and less in sqlite.
	 */
	public static interface ChannelTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[ChannelTableSchema.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(ChannelTableSchema.values().length);
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

		NAME("name", "TEXT"),
		// The name of the channel, must match policy channel name

		STATE("state", "INTEGER");
		// The channel state (active inactive)

		final public String n; // name
		final public String t; // type

		private ChannelTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}
		/**
		 * Produce string of the form...
		 * "<field-name>" <field-type>
		 * e.g.
		 * "dog" TEXT
		 */

		public String addfield() {
			return new StringBuilder()
			.append('"').append(this.n).append('"').append(' ').append(this.t)
			.toString();
		}
		/**
		 * Produce string of the form...
		 * "<field-name>"
		 */
		public String q() {
			return new StringBuilder()
			.append('"').append(this.n).append('"')
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


	// ===========================================================
	// Methods
	// ===========================================================

	public DistributorDataStore(Context context) {
		this.context = context;
		this.helper = new MyHelper(this.context, Tables.NAME, null, VERSION);

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

		this.applCachePublicationDir = new File(this.applCacheDir, "publication");
		this.applCachePublicationDir.mkdir();

		this.applCacheSubscriptionDir = new File(this.applCacheDir, "subscription");
		this.applCacheSubscriptionDir.mkdir();

		this.applTempDir = new File(this.applDir, "tmp");
		this.applTempDir.mkdir();

		this.openWrite();
	}


	public DistributorDataStore openRead() {
		if (this.db != null && this.db.isOpen()) this.db.close();
		this.db = this.helper.getReadableDatabase();
		return this;
	}
	public DistributorDataStore openWrite() {
		if (this.db != null && this.db.isOpen()) this.db.close();
		this.db = this.helper.getWritableDatabase();
		return this;
	}

	public void close() {
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
	public Cursor queryPostal(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(Tables.POSTAL.n);
		qb.setProjectionMap(PostalTable.PROJECTION_MAP);

		// Get the database and run the query.
		SQLiteDatabase db = this.helper.getReadableDatabase();
		return qb.query(db, projection, selection, selectionArgs, null, null,
				(!TextUtils.isEmpty(sortOrder)) ? sortOrder
						: PostalTable.DEFAULT_SORT_ORDER);
	}

	private static final String POSTAL_STATUS_QUERY = new StringBuilder()
	.append(" SELECT ").append(" * ")
	.append(" FROM ").append(Tables.POSTAL.q()).append(" AS p ")
	.append(" WHERE EXISTS (SELECT * ")
	.append(" FROM ").append(Tables.DISPOSAL.q()).append(" AS d ")
	.append(" INNER JOIN ").append(Tables.CHANNEL.q()).append(" AS c ")
	.append(" ON d.").append(DisposalTableSchema.CHANNEL.q()).append("=c.").append(ChannelTableSchema.NAME.q())
	.append(" WHERE p.").append(PostalTableSchema._ID.q()).append("=d.").append(DisposalTableSchema.PARENT.q())
	.append("   AND d.").append(DisposalTableSchema.TYPE.q()).append("=").append(Tables.POSTAL.qv())
	.append("   AND c.").append(ChannelTableSchema.STATE.q()).append('=').append(ChannelState.ACTIVE.q())
	.append("   AND d.").append(DisposalTableSchema.STATE.q())
	.append(" IN (").append(DisposalState.PENDING.q()).append("));")
	.toString();

	public Cursor queryPostalReady(boolean resend) {
		return db.rawQuery(POSTAL_STATUS_QUERY, null);
	}

	public Cursor queryPublish(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(Tables.PUBLISH.n);
		qb.setProjectionMap(PublishTable.PROJECTION_MAP);

		// Get the database and run the query.
		SQLiteDatabase db = this.helper.getReadableDatabase();
		return qb.query(db, projection, selection, selectionArgs, null, null,
				(!TextUtils.isEmpty(sortOrder)) ? sortOrder
						: PublishTable.DEFAULT_SORT_ORDER);
	}

	public Cursor queryRetrieval(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(Tables.RETRIEVAL.n);
		qb.setProjectionMap(RetrievalTable.PROJECTION_MAP);

		// Get the database and run the query.
		SQLiteDatabase db = this.helper.getReadableDatabase();
		return qb.query(db, projection, selection, selectionArgs, null, null,
				(!TextUtils.isEmpty(sortOrder)) ? sortOrder
						: RetrievalTable.DEFAULT_SORT_ORDER);
	}

	private static final String RETRIEVAL_STATUS_QUERY = new StringBuilder()
	.append(" SELECT ").append(" * ")
	.append(" FROM ").append(Tables.RETRIEVAL.q()).append(" AS p ")
	.append(" WHERE EXISTS (SELECT * ")
	.append(" FROM ").append(Tables.DISPOSAL.q()).append(" AS d ")
	.append(" INNER JOIN ").append(Tables.CHANNEL.q()).append(" AS c ")
	.append(" ON d.").append(DisposalTableSchema.CHANNEL.q()).append("=c.").append(ChannelTableSchema.NAME.q())
	.append(" WHERE p.").append(RetrievalTableSchema._ID.q()).append("=d.").append(DisposalTableSchema.PARENT.q())
	.append("   AND d.").append(DisposalTableSchema.TYPE.q()).append("=").append(Tables.RETRIEVAL.qv())
	.append("   AND c.").append(ChannelTableSchema.STATE.q()).append('=').append(ChannelState.ACTIVE.q())
	.append("   AND d.").append(DisposalTableSchema.STATE.q())
	.append(" IN (").append(DisposalState.PENDING.q()).append("));")
	.toString();

	public Cursor queryRetrievalReady(boolean resend) {
		return db.rawQuery(RETRIEVAL_STATUS_QUERY, null);
	}

	public Cursor querySubscribe(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(Tables.SUBSCRIBE.n);
		qb.setProjectionMap(SubscribeTable.PROJECTION_MAP);

		// Get the database and run the query.
		SQLiteDatabase db = this.helper.getReadableDatabase();
		return qb.query(db, projection, selection, selectionArgs, null, null,
				(!TextUtils.isEmpty(sortOrder)) ? sortOrder
						: SubscribeTable.DEFAULT_SORT_ORDER);
	}

	private static final String SUBSCRIBE_STATUS_QUERY = new StringBuilder()
	.append(" SELECT ").append(" * ")
	.append(" FROM ").append(Tables.SUBSCRIBE.q()).append(" AS p ")
	.append(" WHERE EXISTS (SELECT * ")
	.append(" FROM ").append(Tables.DISPOSAL.q()).append(" AS d ")
	.append(" INNER JOIN ").append(Tables.CHANNEL.q()).append(" AS c ")
	.append(" ON d.").append(DisposalTableSchema.CHANNEL.q()).append("=c.").append(ChannelTableSchema.NAME.q())
	.append(" WHERE p.").append(SubscribeTableSchema._ID.q()).append("=d.").append(DisposalTableSchema.PARENT.q())
	.append("   AND d.").append(DisposalTableSchema.TYPE.q()).append("=").append(Tables.SUBSCRIBE.qv())
	.append("   AND c.").append(ChannelTableSchema.STATE.q()).append('=').append(ChannelState.ACTIVE.q())
	.append("   AND d.").append(DisposalTableSchema.STATE.q())
	.append(" IN (").append(DisposalState.PENDING.q()).append("));")
	.toString();

	public Cursor querySubscribeReady(boolean resend) {
		return this.db.rawQuery(SUBSCRIBE_STATUS_QUERY, null);
	}



	public Cursor queryDisposal(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(Tables.DISPOSAL.n);
		qb.setProjectionMap(DisposalTable.PROJECTION_MAP);

		// Get the database and run the query.
		SQLiteDatabase db = this.helper.getReadableDatabase();
		return qb.query(this.db, projection, selection, selectionArgs, null, null,
				(!TextUtils.isEmpty(sortOrder)) ? sortOrder
						: DisposalTable.DEFAULT_SORT_ORDER);
	}

	private static final String DISPOSAL_STATUS_QUERY = new StringBuilder()
	.append("SELECT * ")
	.append(" FROM ").append(Tables.DISPOSAL.q()).append(" AS d ")
	.append(" INNER JOIN ").append(Tables.CHANNEL.q()).append(" AS c ")
	.append(" ON d.").append(DisposalTableSchema.CHANNEL).append("=c.").append(ChannelTableSchema.NAME)
	.append(" WHERE d.").append(DisposalTableSchema.PARENT).append("=?.")
	.append("   AND d.").append(DisposalTableSchema.TYPE).append("=?")
	.append("   AND c.").append(ChannelTableSchema.STATE.q()).append('=').append(ChannelState.ACTIVE.q())
	.append("   AND d.").append(DisposalTableSchema.STATE.q())
	.append(" IN (").append(DisposalState.PENDING.q()).append("));")
	.append(';')
	.toString();

	public Cursor queryDisposalReady(int parent, String type) {
		return db.rawQuery(DISPOSAL_STATUS_QUERY, new String[]{String.valueOf(parent), type});
	}


	/**
	 * Upsert is a portmanteau of update and insert, thus,
	 * if a record with a matching key exists then update
	 * otherwise insert.
	 */

	static final private String POSTAL_UPDATE_CLAUSE = new StringBuilder()
	.append(PostalTableSchema.TOPIC.q()).append("=?")
	.append(" AND ").append(PostalTableSchema.PROVIDER.q()).append("=?")
	.toString();
	
	public long upsertPostal(ContentValues cv, Map<String,Boolean> status) {
		final String topic = cv.getAsString(PostalTableSchema.TOPIC.cv());
		final String provider = cv.getAsString(PostalTableSchema.PROVIDER.cv());

		final long key;
		final String[] updateArgs = new String[]{ topic, provider };
		if (0 < this.db.update(Tables.POSTAL.n, cv, POSTAL_UPDATE_CLAUSE, updateArgs )) {
			final Cursor cursor = this.db.query(Tables.POSTAL.n, 
					new String[]{PostalTableSchema._ID.n},
					POSTAL_UPDATE_CLAUSE, updateArgs, null, null, null);
			cursor.moveToFirst();
			key = cursor.getInt(0); // we only asked for one column so it better be it.
			cursor.close();
		} else {
		   key = this.db.insert(Tables.POSTAL.n, PostalTableSchema.CREATED.n, cv);
		}
		for (Entry<String,Boolean> entry : status.entrySet()) {
			upsertDisposalByParent(key, Tables.POSTAL, entry.getKey(), entry.getValue());
		}
		return key;
	}

	static final private String PUBLISH_UPDATE_CLAUSE = new StringBuilder()
	.append(PublishTableSchema.TOPIC.q()).append("=?")
	.append(" AND ").append(PublishTableSchema.PROVIDER.q()).append("=?")
	.toString();
	
	public long upsertPublish(ContentValues cv, Map<String,Boolean> status) {
		final String topic = cv.getAsString(PublishTableSchema.TOPIC.cv());
		final String provider = cv.getAsString(PublishTableSchema.PROVIDER.cv());

		final long key;
		final String[] updateArgs = new String[]{ topic, provider };
		if (0 < this.db.update(Tables.PUBLISH.n, cv, PUBLISH_UPDATE_CLAUSE, updateArgs )) {
			final Cursor cursor = this.db.query(Tables.PUBLISH.n, 
					new String[]{PublishTableSchema._ID.n},
					PUBLISH_UPDATE_CLAUSE, updateArgs, null, null, null);
			cursor.moveToFirst();
			key = cursor.getInt(0); // we only asked for one column so it better be it.
			cursor.close();
		} else {
		   key = this.db.insert(Tables.PUBLISH.n, PublishTableSchema.CREATED.n, cv);
		}
		for (Entry<String,Boolean> entry : status.entrySet()) {
			upsertDisposalByParent(key, Tables.PUBLISH, entry.getKey(), entry.getValue());
		}
		return key;
	}

	static final private String RETRIEVAL_UPDATE_CLAUSE = new StringBuilder()
	.append(RetrievalTableSchema.TOPIC.q()).append("=?")
	.append(" AND ").append(RetrievalTableSchema.PROVIDER.q()).append("=?")
	.toString();
	
	public long upsertRetrieval(ContentValues cv, Map<String,Boolean> status) {
		final String topic = cv.getAsString(RetrievalTableSchema.TOPIC.cv());
		final String provider = cv.getAsString(RetrievalTableSchema.PROVIDER.cv());

		final long key;
		final String[] updateArgs = new String[]{ topic, provider };
		if (0 < this.db.update(Tables.RETRIEVAL.n, cv, RETRIEVAL_UPDATE_CLAUSE, updateArgs )) {
			final Cursor cursor = this.db.query(Tables.RETRIEVAL.n, 
					new String[]{RetrievalTableSchema._ID.n},
					RETRIEVAL_UPDATE_CLAUSE, updateArgs, null, null, null);
			cursor.moveToFirst();
			key = cursor.getInt(0); // we only asked for one column so it better be it.
			cursor.close();
		} else {
		   key = this.db.insert(Tables.RETRIEVAL.n, RetrievalTableSchema.CREATED.n, cv);
		}
		for (Entry<String,Boolean> entry : status.entrySet()) {
			upsertDisposalByParent(key, Tables.RETRIEVAL, entry.getKey(), entry.getValue());
		}
		return key;
	}
	/**
	 *
	 */
	static final private String SUBSCRIBE_UPDATE_CLAUSE = new StringBuilder()
	.append(SubscribeTableSchema.TOPIC.q()).append("=?")
	.append(" AND ").append(SubscribeTableSchema.PROVIDER.q()).append("=?")
	.toString();
	
	public long upsertSubscribe(ContentValues cv, Map<String,Boolean> status) {
		final String topic = cv.getAsString(SubscribeTableSchema.TOPIC.cv());
		final String provider = cv.getAsString(SubscribeTableSchema.PROVIDER.cv());

		final long key;
		final String[] updateArgs = new String[]{ topic, provider };
		if (0 < this.db.update(Tables.SUBSCRIBE.n, cv, SUBSCRIBE_UPDATE_CLAUSE, updateArgs )) {
			final Cursor cursor = this.db.query(Tables.SUBSCRIBE.n, 
					new String[]{SubscribeTableSchema._ID.n},
				SUBSCRIBE_UPDATE_CLAUSE, updateArgs, null, null, null);
			cursor.moveToFirst();
			key = cursor.getInt(0); // we only asked for one column so it better be it.
			cursor.close();
		} else {
		   key = this.db.insert(Tables.SUBSCRIBE.n, SubscribeTableSchema.CREATED.n, cv);
		}
		for (Entry<String,Boolean> entry : status.entrySet()) {
			upsertDisposalByParent(key, Tables.SUBSCRIBE, entry.getKey(), entry.getValue());
		}
		return key;
	}

	public long upsertDisposal(ContentValues cv) {
		return this.db.insert(Tables.DISPOSAL.n, DisposalTableSchema.STATE.n, cv);
	}
	static final private String DISPOSAL_UPDATE_CLAUSE = new StringBuilder()
	.append(DisposalTableSchema.PARENT.q()).append("=?").append(" AND ")
	.append(DisposalTableSchema.CHANNEL.q()).append("=?").toString();

	public void upsertDisposalByParent(long id, Tables type, Map<String,Boolean> status) {
		for (Entry<String,Boolean> entry : status.entrySet()) {
			upsertDisposalByParent(id, type, entry.getKey(), entry.getValue());
		}
	}
	public void upsertDisposalByParent(long id, Tables type, String channel, Boolean status) {
		final String typeVal = type.cv();

		final ContentValues cv = new ContentValues();
		cv.put(DisposalTableSchema.TYPE.cv(), typeVal);
		cv.put(DisposalTableSchema.PARENT.cv(), id);
		cv.put(DisposalTableSchema.CHANNEL.cv(), channel);
		cv.put(DisposalTableSchema.STATE.cv(), status);

		final int updateCount = this.db.update(Tables.DISPOSAL.n, cv, 
				DISPOSAL_UPDATE_CLAUSE, new String[]{ typeVal, channel } );
		if (updateCount > 0) return;
		this.db.insert(Tables.DISPOSAL.n, DisposalTableSchema.STATE.n, cv);
	}

	public void upsertChannelByName(String channel, ChannelState status) {
		DistributorDataStore.upsertChannelByName(this.db, channel, status);
	}
	static private void upsertChannelByName(SQLiteDatabase db, String channel, ChannelState status) {
		final ContentValues cv = new ContentValues();
		cv.put(ChannelTableSchema.NAME.cv(), channel);
		cv.put(ChannelTableSchema.STATE.cv(), status.cv());

		final int updateCount = db.update(Tables.CHANNEL.n, cv, 
				DISPOSAL_UPDATE_CLAUSE, new String[]{ channel } );
		if (updateCount > 0) return;
		db.insert(Tables.CHANNEL.n, ChannelTableSchema.NAME.n, cv);
	}

	/**
	 * Update an object represented in the database.
	 * Any reasonable update will need to know how to select an existing object.
	 */
	public long updatePostalByKey(Integer id, ContentValues cv) {
		return this.db.update(Tables.POSTAL.n, cv, "\"_id\"=?", new String[]{ String.valueOf(id) } );
	}

	public long updatePublishByKey(Integer id, ContentValues cv) {
		return this.db.update(Tables.PUBLISH.n, cv, "\"_id\"=?", new String[]{ String.valueOf(id) } );
	}

	public long updateRetrievalByKey(Integer id, ContentValues cv) {
		return this.db.update(Tables.POSTAL.n, cv, "\"_id\"=?", new String[]{ String.valueOf(id) } );
	}

	public long updateSubscribeByKey(Integer id, ContentValues cv) {
		return this.db.update(Tables.SUBSCRIBE.n, cv, "\"_id\"=?", new String[]{ String.valueOf(id) } );
	}


	/** Insert method helper */
	public ContentValues initializePostalDefaults(ContentValues values) {
		Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(PostalTableSchema.TOPIC.n)) {
			values.put(PostalTableSchema.TOPIC.n,"unknown");
		}
		if (!values.containsKey(PostalTableSchema.PROVIDER.n)) {
			values.put(PostalTableSchema.PROVIDER.n,"unknown");
		}
		if (!values.containsKey(PostalTableSchema.NOTICE.n)) {
			values.put(PostalTableSchema.NOTICE.n, "");
		}
		if (!values.containsKey(PostalTableSchema.PRIORITY.n)) {
			values.put(PostalTableSchema.PRIORITY.n, PriorityType.NORMAL.o);
		}
		if (!values.containsKey(PostalTableSchema.ORDER.n)) {
			values.put(PostalTableSchema.ORDER.n,
					SerializeType.INDIRECT.o);
		}
		if (!values.containsKey(PostalTableSchema.DISPOSITION.n)) {
			values.put(PostalTableSchema.DISPOSITION .n,
					DisposalState.PENDING.o);
		}
		if (!values.containsKey(PostalTableSchema.EXPIRATION.n)) {
			values.put(PostalTableSchema.EXPIRATION.n, now);
		}
		if (!values.containsKey(PostalTableSchema.UNIT.n)) {
			values.put(PostalTableSchema.UNIT.n, "unknown");
		}
		if (!values.containsKey(PostalTableSchema.VALUE.n)) {
			values.put(PostalTableSchema.VALUE.n, -1);
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
	protected ContentValues initializePublicationDefaults(ContentValues values) {
		Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(PublishTableSchema.DISPOSITION.n)) {
			values.put(PublishTableSchema.DISPOSITION.n,
					DisposalState.PENDING.o);
		}
		if (!values.containsKey(PublishTableSchema.PROVIDER.n)) {
			values.put(PublishTableSchema.PROVIDER.n, "unknown");
		}
		if (!values.containsKey(PublishTableSchema.TOPIC.n)) {
			values.put(PublishTableSchema.TOPIC.n, "unknown");
		}
		if (!values.containsKey(PublishTableSchema.EXPIRATION.n)) {
			values.put(PublishTableSchema.EXPIRATION.n, now);
		}
		if (!values.containsKey(PublishTableSchema.CREATED.n)) {
			values.put(PublishTableSchema.CREATED.n, now);
		}
		if (!values.containsKey(PublishTableSchema.MODIFIED.n)) {
			values.put(PublishTableSchema.MODIFIED.n, now);
		}
		return values;
	}

	/** Insert method helper */
	protected ContentValues initializeRetrievalDefaults(ContentValues values) {
		Long now = Long.valueOf(System.currentTimeMillis());

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
		if (!values.containsKey(RetrievalTableSchema.CONTINUITY_TYPE.n)) {
			values.put(RetrievalTableSchema.CONTINUITY_TYPE.n,
					ContinuityType.ONCE.o);
		}
		if (!values.containsKey(RetrievalTableSchema.CONTINUITY_VALUE.n)) {
			values.put(RetrievalTableSchema.CONTINUITY_VALUE.n, now);
		}
		if (!values.containsKey(RetrievalTableSchema.EXPIRATION.n)) {
			values.put(RetrievalTableSchema.EXPIRATION.n, now);
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
		Long now = Long.valueOf(System.currentTimeMillis());

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
			values.put(SubscribeTableSchema.EXPIRATION.n, now);
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
	public int delete(String table, String selection, String[] selectionArgs) {
		SQLiteDatabase db = this.helper.getWritableDatabase();
		return db.delete(table, selection, selectionArgs);
	}

	public int deletePostal(String selection, String[] selectionArgs) {
		SQLiteDatabase db = this.helper.getWritableDatabase();
		return db.delete(Tables.POSTAL.n, selection, selectionArgs);
	}

	public int deletePublish(String selection, String[] selectionArgs) {
		SQLiteDatabase db = this.helper.getWritableDatabase();
		return db.delete(Tables.PUBLISH.n, selection, selectionArgs);
	}
	public int deleteRetrieval(String selection, String[] selectionArgs) {
		SQLiteDatabase db = this.helper.getWritableDatabase();
		return db.delete(Tables.RETRIEVAL.n, selection, selectionArgs);
	}
	public int deleteSubscribe(String selection, String[] selectionArgs) {
		SQLiteDatabase db = this.helper.getWritableDatabase();
		return db.delete(Tables.SUBSCRIBE.n, selection, selectionArgs);
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

	protected class MyHelper extends SQLiteOpenHelper {
		// ===========================================================
		// Constants
		// ===========================================================
		private final Logger logger = LoggerFactory.getLogger(MyHelper.class);

		// ===========================================================
		// Fields
		// ===========================================================

		/** Nothing to put here */

		// ===========================================================
		// Constructors
		// ===========================================================
		public MyHelper(Context context, String name, CursorFactory factory, int version) {
			super(context, name, factory, version);
		}

		// ===========================================================
		// SQLiteOpenHelper Methods
		// ===========================================================

		@Override
		public void onCreate(SQLiteDatabase db) {
			logger.info("Bootstrapping database");

			try {
				final StringBuilder sb = new StringBuilder();
				for (PostalTableSchema field : PostalTableSchema.values()) {
					if(sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(Tables.POSTAL.sqlCreate(sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				final StringBuilder sb = new StringBuilder();
				for (PublishTableSchema field : PublishTableSchema.values()) {
					if(sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(Tables.PUBLISH.sqlCreate(sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				final StringBuilder sb = new StringBuilder();
				for (RetrievalTableSchema field : RetrievalTableSchema.values()) {
					if(sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(Tables.RETRIEVAL.sqlCreate(sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				final StringBuilder sb = new StringBuilder();
				for (SubscribeTableSchema field : SubscribeTableSchema.values()) {
					if(sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(Tables.SUBSCRIBE.sqlCreate(sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				final StringBuilder sb = new StringBuilder();
				for (DisposalTableSchema field : DisposalTableSchema.values()) {
					if(sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(Tables.DISPOSAL.sqlCreate(sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				final StringBuilder sb = new StringBuilder();
				for (ChannelTableSchema field : ChannelTableSchema.values()) {
					if(sb.length() != 0)
						sb.append(",");
					sb.append(field.addfield());
				}
				db.execSQL(Tables.CHANNEL.sqlCreate(sb.toString()).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}


			// === PRELOAD ======
			/**
			 * This is really only for testing.
			 * 
			 */
			DistributorDataStore.upsertChannelByName(db, "multicast", ChannelState.ACTIVE);
			DistributorDataStore.upsertChannelByName(db, "gateway",  ChannelState.ACTIVE);

			// === VIEWS ======

			// === INDICIES ======
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			logger.warn("Upgrading database from version {} to {} which will destroy all old data",
					oldVersion, newVersion);
			for (Tables table : Tables.values()) {
				try {
					db.execSQL(table.sqlDrop().toString());
				} catch (SQLiteException ex) {
					logger.warn("defective database being dropped {}", ex.getLocalizedMessage());
				}
			}
			onCreate(db);
		}

	}

}

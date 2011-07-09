package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Environment;
import android.provider.SyncStateContract.Columns;
import android.text.TextUtils;

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
	private final static Logger logger = LoggerFactory.getLogger(DistributorDataStore.class);

	// ===========================================================
	// Fields
	// ===========================================================
	private final Context context;
	private SQLiteDatabase db;
	private MyHelper helper;
	// ===========================================================
	// Schema
	// ===========================================================
	
	/**
	 * Data Store Table definitions
	 */
	public enum Tables {
		POSTAL("postal"), 
		RETRIEVAL("retrieval"), 
		PUBLISH("publication"), 
		SUBSCRIBE("subscription");

		private String n;
		public String n() { return this.n; }

		private Tables(String name) {
			this.n = name;
		}
		
		public static final int VERSION = 6;
		public static final String NAME = "distributor.db";
		
		public StringBuilder sqlCreate(StringBuilder sb) {
			StringBuilder wrap = new StringBuilder();
			return wrap.append("CREATE TABLE ")
			    .append('"').append(this.n).append('"')
				.append(" (").append(sb).append(");");
		}
		
		public StringBuilder sqlDrop(StringBuilder sb) {
			StringBuilder wrap = new StringBuilder();
			return wrap.append("DROP TABLE ")
			    .append('"').append(this.n).append('"')
				.append(" (").append(sb).append(");");
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
			public String val() {
				return new StringBuilder().append("'").append(this.o).append("'").toString();
			}
	   };
	   
		/**
		 * Status of the entry (sent or not sent).
		 * <P>
		 * Type: EXCLUSIVE
		 * </P>
		 */

		public enum Disposition {
			PENDING(1, "PENDING"), 
			QUEUED(2, "QUEUED"), 
			SENT(3, "SENT"),
			JOURNAL(4, "JOURNAL"),
			FAIL(5, "FAIL"),
			EXPIRED(6, "EXPIRED"),
			SATISFIED(7, "SATISFIED");

			private int o;
			public int o() { return this.o; }
			@SuppressWarnings("unused")
			private String t;

			private Disposition(int ordinal, String title) {
				this.o = ordinal;
				this.t = title;
			}
			public String val() {
				return new StringBuilder().append("'").append(this.o).append("'").toString();
			}
		};
		
		/**
		 * 
		 */
		public enum ContinuityType {
			ONCE(1, "PENDING"), 
			TEMPORAL(2, "QUEUED"), 
			QUANTITY(3, "SENT");

			private int o;
			@SuppressWarnings("unused")
			private String t;

			private ContinuityType(int ordinal, String title) {
				this.o = ordinal;
				this.t = title;
			}
			public String val() {
				return new StringBuilder().append("'").append(this.o).append("'").toString();
			}
		};
		
		public enum PriorityType {
			FLASH(128, "FLASH"),
			URGENT(64, "URGENT"),
			IMPORTANT(32, "IMPORTANT"),
			NORMAL(16, "NORMAL"),
			BACKGROUND(8, "BACKGROUND");
			
			private int o;
			@SuppressWarnings("unused")
			private String t;

			private PriorityType(int ordinal, String title) {
				this.o = ordinal;
				this.t = title;
			}
			public String val() {
				return new StringBuilder().append("'").append(this.o).append("'").toString();
			}
		}
	
		// ===========================================================
		// Enumerated types in the tables.
		// ===========================================================
		
	/**
	 * The postal table is for holding retrieval requests.
	 */
	public static interface PostalTable extends Columns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = Columns._ID + " ASC";

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

	public enum PostalTableSchema  {
		_ID("_id", "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CREATED("created", "INTEGER"),
		// When the request was made
		
		MODIFIED("modified", "INTEGER"),
		// When the request was last modified
			
		CP_TYPE("cp_type", "TEXT"),  
		// This along with the cost is used to decide how to deliver the specific object.
		
		PROVIDER("provider", "TEXT"), 
		// The uri of the content provider 
		
		NOTICE("notice", "BLOB"), 
		// A description of what is to be done when various state-transition occur.		

		PRIORITY("priority", "INTEGER"), 
		// What order should this message be sent. Negative priorities indicated less than normal.
		
		SERIALIZE_TYPE("serialize_type", "INTEGER"), 
		
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
		
		public String n; // name
		public String t; // type

		private PostalTableSchema(String n, String t) {
			this.n = n;
			this.t = t;
		}
		/**
		 * A helper method to construct the sql create field.
		 */
		public StringBuilder addfield() {
			return new StringBuilder().append('"').append(this.n).append('"').append(' ').append(this.t);
		}
		public String col() {
			return new StringBuilder().append('"').append(this.n).append('"').toString();
		}
	};

	
	/**
	 * The publication table is for holding publication requests.
	 */
	public static interface PublishTable extends Columns {
		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = Columns._ID + " ASC";

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
		_ID("_id", "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CREATED("created", "INTEGER"),
		// When the request was made
		
		MODIFIED("modified", "INTEGER"), 
		// When the request was last modified
			
		DATA_TYPE("data_type", "TEXT"),  
		// This along with the cost is used to decide how to deliver the specific object.
		
		PROVIDER("provider", "TEXT"), 
		// The uri of the content provider 
		
		DISPOSITION("disposition", "INTEGER"), 
		// The current best guess of the status of the request.
		
		EXPIRATION("expiration", "INTEGER"); 
		// Time-stamp at which request entry becomes stale.
		
		public String n; // name
		public String t; // type

		private PublishTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}
		/**
		 * A helper method to construct the sql create field.
		 */
		public StringBuilder addfield() {
			return new StringBuilder().append('"').append(this.n).append('"').append(' ').append(this.t);
		}
		public String col() {
			return new StringBuilder().append('"').append(this.n).append('"').toString();
		}
	}

	/**
	 * The retrieval table is for holding retrieval requests.
	 */
	public static interface RetrievalTable extends Columns {
		
		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = Columns._ID + " ASC";

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
	
	public enum RetrievalTableSchema {
		_ID("_id", "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CREATED("created", "INTEGER"),
		// When the request was made
		
		MODIFIED("modified", "INTEGER"), 
		// When the request was last modified
			
		DATA_TYPE("data_type", "TEXT"),  
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
		
		public String n; // name
		public String t; // type

		private RetrievalTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}
		/**
		 * A helper method to construct the sql create field.
		 */
		public StringBuilder addfield() {
			return new StringBuilder().append('"').append(this.n).append('"').append(' ').append(this.t);
		}
		public String col() {
			return new StringBuilder().append('"').append(this.n).append('"').toString();
		}
	};
	

	/**
	 * The subscription table is for holding subscription requests.
	 */
	public static interface SubscribeTable extends Columns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = Columns._ID + " ASC";
		
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
	
	public enum SubscribeTableSchema {
		_ID("_id", "INTEGER PRIMARY KEY AUTOINCREMENT"),

		CREATED("created", "INTEGER"),
		// When the request was made
		
		MODIFIED("modified", "INTEGER"),
		// When the request was last modified
			
		DATA_TYPE("data_type", "TEXT"),  
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
		

		public String n; // name
		public String t; // type

		private SubscribeTableSchema(String name, String type) {
			this.n = name;
			this.t = type;
		}
		/**
		 * A helper method to construct the sql create field.
		 */
		public StringBuilder addfield() {
			return new StringBuilder().append('"').append(this.n).append('"').append(' ').append(this.t);
		}
		public String col() {
			return new StringBuilder().append('"').append(this.n).append('"').toString();
		}
	}
	

	// Views.
	public interface Views {
		// Nothing to put here yet.
	}


	// ===========================================================
	// Methods
	// ===========================================================

	public DistributorDataStore(Context context) {
		this.context = context;
		this.helper = new MyHelper(this.context);
	}
	
	public DistributorDataStore open() {
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
			String[] selectionArgs, String sortOrder) 
	{
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(Tables.POSTAL.n());
		qb.setProjectionMap(PostalTable.PROJECTION_MAP);

		// Get the database and run the query.
		SQLiteDatabase db = this.helper.getReadableDatabase();
		return qb.query(db, projection, selection, selectionArgs, null, null, 
				(!TextUtils.isEmpty(sortOrder)) ? sortOrder
						: PostalTable.DEFAULT_SORT_ORDER);
	}
	
	public Cursor queryPublish(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) 
	{
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(Tables.PUBLISH.n());
		qb.setProjectionMap(PublishTable.PROJECTION_MAP);

		// Get the database and run the query.
		SQLiteDatabase db = this.helper.getReadableDatabase();
		return qb.query(db, projection, selection, selectionArgs, null, null, 
				(!TextUtils.isEmpty(sortOrder)) ? sortOrder
						: PublishTable.DEFAULT_SORT_ORDER);
	}
	
	public Cursor queryRetrieval(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) 
	{
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(Tables.RETRIEVAL.n());
		qb.setProjectionMap(RetrievalTable.PROJECTION_MAP);

		// Get the database and run the query.
		SQLiteDatabase db = this.helper.getReadableDatabase();
		return qb.query(db, projection, selection, selectionArgs, null, null, 
				(!TextUtils.isEmpty(sortOrder)) ? sortOrder
						: RetrievalTable.DEFAULT_SORT_ORDER);
	}
	
	public Cursor querySubscribe(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) 
	{
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		qb.setTables(Tables.SUBSCRIBE.n());
		qb.setProjectionMap(SubscribeTable.PROJECTION_MAP);

		// Get the database and run the query.
		SQLiteDatabase db = this.helper.getReadableDatabase();
		return qb.query(db, projection, selection, selectionArgs, null, null, 
				(!TextUtils.isEmpty(sortOrder)) ? sortOrder
						: SubscribeTable.DEFAULT_SORT_ORDER);
	}
	

	/**
	 * Insert set, if the record already exists then update it.
	 * 
	 * @param cv
	 * @return
	 */
	public long insertPostal(ContentValues cv) {
		return this.db.insert(Tables.POSTAL.n(), PostalTableSchema.CREATED.n, cv);
	}
	
	public long insertPublish(ContentValues cv) {
		return this.db.insert(Tables.PUBLISH.n(), PublishTableSchema.CREATED.n, cv);
	}
	
	public long insertRetrieval(ContentValues cv) {
		return this.db.insert(Tables.RETRIEVAL.n(), RetrievalTableSchema.CREATED.n, cv);
	}
	
	public long insertSubscribe(ContentValues cv) {
		return this.db.insert(Tables.SUBSCRIBE.n(), SubscribeTableSchema.CREATED.n, cv);
	}
		

	/** Insert method helper */
	public ContentValues initializePostalDefaults(ContentValues values) {
		Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(PostalTableSchema.CP_TYPE.n)) {
			values.put(PostalTableSchema.CP_TYPE.col(),"unknown");
		}
		if (!values.containsKey(PostalTableSchema.PROVIDER.n)) {
			values.put(PostalTableSchema.PROVIDER.col(),"unknown");
		}
		if (!values.containsKey(PostalTableSchema.NOTICE.n)) {
			values.put(PostalTableSchema.NOTICE.col(), "");
		}
		if (!values.containsKey(PostalTableSchema.PRIORITY.n)) {
			values.put(PostalTableSchema.PRIORITY.col(), PriorityType.NORMAL.o);
		}
		if (!values.containsKey(PostalTableSchema.SERIALIZE_TYPE.n)) {
			values.put(PostalTableSchema.SERIALIZE_TYPE.col(),
					SerializeType.INDIRECT.o);
		}
		if (!values.containsKey(PostalTableSchema.DISPOSITION.n)) {
			values.put(PostalTableSchema.DISPOSITION .col(),
					Disposition.PENDING.o);
		}
		if (!values.containsKey(PostalTableSchema.EXPIRATION.n)) {
			values.put(PostalTableSchema.EXPIRATION.col(), now);
		}
		if (!values.containsKey(PostalTableSchema.UNIT.n)) {
			values.put(PostalTableSchema.UNIT.col(), "unknown");
		}
		if (!values.containsKey(PostalTableSchema.VALUE.n)) {
			values.put(PostalTableSchema.VALUE.col(), -1);
		}
		if (!values.containsKey(PostalTableSchema.DATA.n)) {
			values.put(PostalTableSchema.DATA.col(), "");
		}
		if (!values.containsKey(PostalTableSchema.CREATED.n)) {
			values.put(PostalTableSchema.CREATED.col(), now);
		}
		if (!values.containsKey(PostalTableSchema.MODIFIED.n)) {
			values.put(PostalTableSchema.MODIFIED.col(), now);
		}
		
		return values;
	}

	/** Insert method helper */
	protected ContentValues initializePublicationDefaults(ContentValues values) {
		Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(PublishTableSchema.DISPOSITION.n)) {
			values.put(PublishTableSchema.DISPOSITION.col(),
					Disposition.PENDING.o);
		}
		if (!values.containsKey(PublishTableSchema.PROVIDER.n)) {
			values.put(PublishTableSchema.PROVIDER.col(), "unknown");
		}
		if (!values.containsKey(PublishTableSchema.DATA_TYPE.n)) {
			values.put(PublishTableSchema.DATA_TYPE.col(), "unknown");
		}
		if (!values.containsKey(PublishTableSchema.EXPIRATION.n)) {
			values.put(PublishTableSchema.EXPIRATION.col(), now);
		}
		if (!values.containsKey(PublishTableSchema.CREATED.n)) {
			values.put(PublishTableSchema.CREATED.col(), now);
		}
		if (!values.containsKey(PublishTableSchema.MODIFIED.n)) {
			values.put(PublishTableSchema.MODIFIED.col(), now);
		}
		return values;
	}

	/** Insert method helper */
	protected ContentValues initializeRetrievalDefaults(ContentValues values) {
		Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(RetrievalTableSchema.DISPOSITION.n)) {
			values.put(RetrievalTableSchema.DISPOSITION.col(),
					Disposition.PENDING.o);
		}
		if (!values.containsKey(RetrievalTableSchema.NOTICE.n)) {
			values.put(RetrievalTableSchema.NOTICE.col(), "");
		}
		if (!values.containsKey(RetrievalTableSchema.PRIORITY.n)) {
			values.put(RetrievalTableSchema.PRIORITY.col(), PriorityType.NORMAL.o);
		}
		if (!values.containsKey(RetrievalTableSchema.PROVIDER.n)) {
			values.put(RetrievalTableSchema.PROVIDER.col(), "unknown");
		}
		if (!values.containsKey(RetrievalTableSchema.DATA_TYPE.n)) {
			values.put(RetrievalTableSchema.DATA_TYPE.col(), "unknown");
		}
		if (!values.containsKey(RetrievalTableSchema.PROJECTION.n)) {
			values.put(RetrievalTableSchema.PROJECTION.col(), "");
		}
		if (!values.containsKey(RetrievalTableSchema.SELECTION.n)) {
			values.put(RetrievalTableSchema.SELECTION.col(), "");
		}
		if (!values.containsKey(RetrievalTableSchema.ARGS.n)) {
			values.put(RetrievalTableSchema.ARGS.col(), "");
		}
		if (!values.containsKey(RetrievalTableSchema.ORDERING.n)) {
			values.put(RetrievalTableSchema.ORDERING.col(), "");
		}
		if (!values.containsKey(RetrievalTableSchema.CONTINUITY_TYPE.n)) {
			values.put(RetrievalTableSchema.CONTINUITY_TYPE.col(),
					ContinuityType.ONCE.o);
		}
		if (!values.containsKey(RetrievalTableSchema.CONTINUITY_VALUE.n)) {
			values.put(RetrievalTableSchema.CONTINUITY_VALUE.col(), now);
		}
		if (!values.containsKey(RetrievalTableSchema.EXPIRATION.n)) {
			values.put(RetrievalTableSchema.EXPIRATION.col(), now);
		}
		if (!values.containsKey(RetrievalTableSchema.CREATED.n)) {
			values.put(RetrievalTableSchema.CREATED.col(), now);
		}
		if (!values.containsKey(RetrievalTableSchema.MODIFIED.n)) {
			values.put(RetrievalTableSchema.MODIFIED.col(), now);
		}
		
		return values;
	}

	/** Insert method helper */
	protected ContentValues initializeSubscriptionDefaults(ContentValues values) {
		Long now = Long.valueOf(System.currentTimeMillis());

		if (!values.containsKey(SubscribeTableSchema.DISPOSITION.n)) {
			values.put(SubscribeTableSchema.DISPOSITION.col(),
					Disposition.PENDING.o);
		}
		if (!values.containsKey(SubscribeTableSchema.PROVIDER.n)) {
			values.put(SubscribeTableSchema.PROVIDER.col(), "unknown");
		}
		if (!values.containsKey(SubscribeTableSchema.DATA_TYPE.n)) {
			values.put(SubscribeTableSchema.DATA_TYPE.col(), "unknown");
		}
		
		if (!values.containsKey(SubscribeTableSchema.SELECTION.n)) {
			values.put(SubscribeTableSchema.SELECTION.col(), "");
		}
		if (!values.containsKey(SubscribeTableSchema.EXPIRATION.n)) {
			values.put(SubscribeTableSchema.EXPIRATION.col(), now);
		}
		if (!values.containsKey(SubscribeTableSchema.NOTICE.n)) {
			values.put(SubscribeTableSchema.NOTICE.col(), "");
		}
		if (!values.containsKey(SubscribeTableSchema.PRIORITY.n)) {
			values.put(SubscribeTableSchema.PRIORITY.col(), PriorityType.NORMAL.o);
		}
		if (!values.containsKey(SubscribeTableSchema.CREATED.n)) {
			values.put(SubscribeTableSchema.CREATED.col(), now);
		}
		if (!values.containsKey(SubscribeTableSchema.MODIFIED.n)) {
			values.put(SubscribeTableSchema.MODIFIED.col(), now);
		}
		return values;
	}

	/**
	 * Delete set
	 * 
	 * @param cv
	 * @return
	 */
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

	static public final File applDir;
	static public final File applCacheDir;
	static public final File applCachePostalDir;
	static public final File applCacheRetrievalDir;
	static public final File applCachePublicationDir;
	static public final File applCacheSubscriptionDir;
	static public final File applTempDir;
	static {
		applDir = new File(Environment.getExternalStorageDirectory(),
				"support/edu.vu.isis.ammo.core");
		applDir.mkdirs();
		if (!applDir.mkdirs()) {
			logger.error("cannot create files check permissions in manifest : "
					+ applDir.toString());
		}

		applCacheDir = new File(applDir, "cache/distributor");
		applCacheDir.mkdirs();

		applCachePostalDir = new File(applCacheDir, "postal");
		applCacheDir.mkdirs();

		applCacheRetrievalDir = new File(applCacheDir, "retrieval");
		applCacheDir.mkdirs();

		applCachePublicationDir = new File(applCacheDir, "publication");
		applCacheDir.mkdirs();

		applCacheSubscriptionDir = new File(applCacheDir, "subscription");
		applCacheDir.mkdirs();

		applTempDir = new File(applDir, "tmp/distributor");
		applTempDir.mkdirs();
	}

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
		public MyHelper(Context context) {
			super(context, DistributorDataStore.Tables.NAME, null,
					DistributorDataStore.Tables.VERSION);
		}

		// ===========================================================
		// SQLiteOpenHelper Methods
		// ===========================================================
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			logger.info("Bootstrapping database");
			
			try {
				StringBuilder sb = new StringBuilder();
				for (PostalTableSchema field : PostalTableSchema.values()) {
					if(sb.length() != 0)
				        sb.append(",");
				    sb.append(field.addfield());
				}
				db.execSQL(Tables.POSTAL.sqlCreate(sb).toString());
				
			} catch (SQLException ex) {
				ex.printStackTrace();
			}
			
			try {
				StringBuilder sb = new StringBuilder();
				for (PublishTableSchema field : PublishTableSchema.values()) {
					if(sb.length() != 0)
				        sb.append(",");
				    sb.append(field.addfield());
				}
				db.execSQL(Tables.PUBLISH.sqlCreate(sb).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}
			
			try {
				StringBuilder sb = new StringBuilder();
				for (RetrievalTableSchema field : RetrievalTableSchema.values()) {
					if(sb.length() != 0)
				        sb.append(",");
				    sb.append(field.addfield());
				}
				db.execSQL(Tables.RETRIEVAL.sqlCreate(sb).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}
			
			try {
				StringBuilder sb = new StringBuilder();
				for (SubscribeTableSchema field : SubscribeTableSchema.values()) {
					if(sb.length() != 0)
				        sb.append(",");
				    sb.append(field.addfield());
				}
				db.execSQL(Tables.SUBSCRIBE.sqlCreate(sb).toString());

			} catch (SQLException ex) {
				ex.printStackTrace();
			}
			
			// create views, triggers, indices and preload
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			logger.warn("Upgrading database from version {} to {} which will destroy all old data",
							oldVersion, newVersion);
			for (Tables table : Tables.values()) {
				db.execSQL(table.sqlDrop(new StringBuilder()).toString());
			}
			onCreate(db);
		}

	}

}

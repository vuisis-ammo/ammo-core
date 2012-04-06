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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Moment;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.api.type.TimeTrigger;
import edu.vu.isis.ammo.core.AmmoService;

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
	public static final int VERSION = 27;

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
	 * Data Store Table definitions
	 * 
	 * The postal table records requests that data be sent out.
	 * POSTed data is specifically named and distributed.
	 * The retrieval and subscribe tables record request that data be obtained.
	 * RETRIEVAL data is obtained from a source.
	 * SUBSCRIBEd data is obtained by topic.
	 * The subscribe table is very similar to the interest table.
	 * The interest table is for local interest the subscribe table is for remote interest.
	 * 
	 * The disposal table keeps track of the status of the delivery.
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
		REQUEST(0, "request"),
		POSTAL(1, "postal"),
		RETRIEVAL(2, "retrieval"),
		INTEREST(3, "interest"),
		SUBSCRIBE(4, "interest"),
		DISPOSAL(5, "disposal"),
		CHANNEL(6, "channel"),
		PRESENCE(7, "presence"),
		RECIPIENT(8, "recipient");

		final public int o;
		final public String n;

		private Tables(int ordinal, String name) {
			this.o = ordinal;
			this.n = name;
		}

		public static final String NAME = "distributor.db";

		// The quoted table name
		public String q() {
			return new StringBuilder().append('"').append(this.n).append('"').toString();
		}
		// The quoted table name as a value
		public String qv() {
			return new StringBuilder().append('\'').append(this.cv()).append('\'').toString();
		}
		public String cv() {
			return String.valueOf(this.o);
		}
		// The quoted index name
		public String qIndex() {
			return new StringBuilder().append('"').append(this.n).append("_index").append('"').toString();
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
		
		/**
		 * Produce string builders of the form...
		 * CREATE TABLE "<table-name>" ( 
		 *    <column defs> 
		 *    FOREIGN KEY(<primary key>) REFERENCES artist(<pk cols>)
		 *    );
		 *
		 */
		public List<String> sqlCreate() {
			final StringBuilder sb = new StringBuilder().append("CREATE TABLE ");
			sb.append('"').append(this.n).append('"');
			sb.append(" ( ");
			final List<TableField> fields = Arrays.asList(this.getFields());
			if (fields.size() > 0) {
				final TableField first = fields.get(1);
				sb.append('"').append(first.n()).append('"').append(' ').append(first.t());
				
				for (TableField field : fields.subList(1, fields.size()) ) {
				    sb.append(",");
					sb.append('"').append(field.n()).append('"').append(' ').append(field.t());
				}
			}
			
			final String parent_key = this.getParentKey();
			if (parent_key.length() > 0) {			
				sb.append(parent_key);
			}
			sb.append(", PRIMARY KEY(").append(BaseColumns._ID).append(')');
			sb.append(");");
			
			final List<String> sql = new ArrayList<String>(2);
			sql.add(sb.toString());
			
			final StringBuilder pkidx = new StringBuilder()
			   .append("CREATE UNIQUE INDEX ").append(this.n).append("_pkidx")
			   .append(" ON ").append(this.n).append("(").append(BaseColumns._ID)
			   .append(");");
			sql.add(pkidx.toString());
			
			return sql;
		}
		
		private TableField[] getFields() {		
			switch (this) {
			case POSTAL:     return PostalField.values(); 
			case RETRIEVAL:  return RetrievalField.values(); 
			case INTEREST:   return InterestField.values(); 
			case DISPOSAL:   return DisposalField.values(); 
			case CHANNEL:    return ChannelField.values(); 
			case PRESENCE:   return PresenceField.values(); 
			case RECIPIENT:  return RecipientField.values(); 
			}
			return null;
		}
			
		private String getParentKey() {		
			switch (this) {
			case REQUEST:    return RequestTable.PARENT_KEY_REF; 
			case POSTAL:     return PostalTable.PARENT_KEY_REF; 
			case RETRIEVAL:  return RetrievalTable.PARENT_KEY_REF; 
			case INTEREST:   return InterestTable.PARENT_KEY_REF; 
			case DISPOSAL:   return DisposalTable.PARENT_KEY_REF; 
			case CHANNEL:    return ChannelTable.PARENT_KEY_REF; 
			case PRESENCE:   return PresenceTable.PARENT_KEY_REF; 
			case RECIPIENT:  return RecipientTable.PARENT_KEY_REF; 
			}
			return null;
		}

	};
	

	// ===========================================================
	// Enumerated types in the tables.
	// ===========================================================

	/**
	 * Indicates if the provider indicates a table entry or whether the
	 * data has been pre-serialized.
	 */
	public enum SerialMoment {
		APRIORI(1),  // a.k.a. DIRECT
		// the serialized object is placed in the payload directly.

		EAGER(2), // a.k.a. INDIRECT
		// the serialized data is obtained from the named 
		// provider by uri as soon as the request is received.

		LAZY(3); // a.k.a. DEFFERED
		// the serialized data is obtained from the named 
		// provider by uri, but the serialization doesn't 
		// happen until the data is sent, i.e. the channel
		// is available.

		public int o; // ordinal

		private SerialMoment(int o) {
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
		public static SerialMoment byOrdinal(int serialType) {
			switch(serialType) {
			case 1:
				return APRIORI;
			case 2:
				return EAGER;
			case 3:
				return LAZY;
			}
			throw new IllegalArgumentException("unknown SerialType "+Integer.toString(serialType));
		}
		public String cv() {
			return String.valueOf(this.o);
		}
		static public SerialMoment getInstance(String ordinal) {
			return SerialMoment.values()[Integer.parseInt(ordinal)];
		}
		static public SerialMoment getInstance(int ordinal) {
			return SerialMoment.values()[ordinal];
		}
	};

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

	// ===========================================================
	// Enumerated types in the tables.
	// ===========================================================


	public interface TableField {	
		// public List<TableFieldState> getState();
		public String q(String tableRef);  // get the quoted field name 
		public String cv(); // get the field name suitable for using in ContentValues
		public String n(); // get the name from the implementation
		public String t(); // get the type from the implementation
	}
	
	static public class TableFieldState {
		final public String n;
		final public String t;
		
		private TableFieldState(String name, String type) {	
			this.n = name;
			this.t = type;
		}
		
		//public String quoted() {
		//	return new StringBuilder()
		//	.append('"').append(this.n).append('"')
		//	.toString();
		//}
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
		 */
		public String addfield() {
			return new StringBuilder()
			.append('"').append(this.n).append('"').append(' ').append(this.t)
			.toString();
		}
		//public static List<TableField> getFields() {
		//	final List<TableField>  tf = new ArrayList<TableField>(PostalField.values().length);
		//	for (PostalField field : PostalField.values()) {
		//		tf.add(TableField.newInstance(field.n, field.t));
		//	}
		//	return tf;
		//}
	}

	/**
	 * The presence table is for holding information about visible peers.
	 * A peer is a particular device over a specific channel.
	 * 
	 * The created field indicates the first time the peer was observed.
	 * The latest field indicates the last time the peer was observed.
	 */
	public enum PresenceField implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		OPERATOR("name", "TEXT"),
		// The name of the operator using the channel

		FIRST("first", "INTEGER"),
		// When the operator first used this channel
		
		LATEST("latest", "INTEGER"),
		// When the operator was last seen "speaking" on the channel
		
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
	};
	public static interface PresenceTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[PresenceField.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(PresenceField.values().length);
		
		public static final String PARENT_KEY_REF = null;
	};
	static {
		int ix = 0;
		for (PresenceField field : PresenceField.values()) {
			PresenceTable.COLUMNS[ix++] = field.n();
			PresenceTable.PROJECTION_MAP.put(field.n(), field.n());
		}
	};

	
	/**
	 * The notice table is for noticing when a request crosses a threshold.
	 *
	 */
	public enum NoticeField implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		OPERATOR("name", "TEXT"),
		// The name of the operator using the channel

		FIRST("first", "INTEGER"),
		// When the operator first used this channel
		
		LATEST("latest", "INTEGER"),
		// When the operator was last seen "speaking" on the channel
		
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

		private NoticeField(String n, String t) {
			this.impl = new TableFieldState(n,t);
		}
        
		/**
		 * required by TableField interface
		 */
		public String q(String tableRef) { return this.impl.quoted(tableRef); }
		public String cv() { return this.impl.cvQuoted(); }
		public String n() { return this.impl.n; }
		public String t() { return this.impl.t; }
	};
	
	public static interface NoticeTable extends BaseColumns {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[NoticeField.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(NoticeField.values().length);
		
		public static final String PARENT_KEY_REF = null;
	};
	static {
		int ix = 0;
		for (NoticeField field : NoticeField.values()) {
			NoticeTable.COLUMNS[ix++] = field.n();
			NoticeTable.PROJECTION_MAP.put(field.n(), field.n());
		}
	};


	
	/**
	 * The Request table holds application requests.
	 * These requests are to express interest in data of certain types 
	 * or to announce the presence of information of a certain type.
	 * The request can apply to the past or the future.
	 *
	 */
	
	public enum RequestField implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

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
	};
	
	public static interface RequestTable {

		public static final String DEFAULT_SORT_ORDER = 
				new StringBuilder().append(RequestField.MODIFIED.n()).append(" DESC ").toString();
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[RequestField.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(RequestField.values().length);
		
		public static final String PARENT_KEY_REF = null;
	};
	static {
		final List<String> columns = Arrays.asList(RequestTable.COLUMNS);
		for (RequestField field : RequestField.values()) {
			columns.add(field.n());
			RequestTable.PROJECTION_MAP.put(field.n(), field.n());
		}
	};


	/**
	 * The postal table is for holding retrieval requests.
	 */
	
	public enum PostalField  implements TableField {
		REQUEST("request", "INTEGER PRIMARY KEY"),
		// The parent key
		
		PAYLOAD("payload", "TEXT"),
		// The payload instead of content provider

		UNIT("unit", "TEXT"),
		// Units associated with {@link #VALUE}. Used to determine whether should occur.

		VALUE("value", "INTEGER"),
		// Arbitrary value linked to importance that entry is transmitted and battery drain.

		DATA("data", "TEXT");
		// If the If null then the data file corresponding to the
		// column name and record id should be used. This is done when the data
		// size is larger than that allowed for a field contents.

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
	};
	
	public static interface PostalTable extends RequestTable {
		public static final String[] COLUMNS = new String[PostalField.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(PostalField.values().length);
	};
	static {
		final List<String> columns = Arrays.asList(PostalTable.COLUMNS);
		for (PostalField field : PostalField.values()) {
			columns.add(field.n());
			PostalTable.PROJECTION_MAP.put(field.n(), field.n());
		}
	};

	/**
	 * The retrieval table is for holding retrieval requests.
	 */
	public enum RetrievalField  implements TableField {
		REQUEST("request", "INTEGER PRIMARY KEY"),
		// The parent key
		
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
	};
	
	public static interface RetrievalTable extends RequestTable {

		public static final String[] COLUMNS = new String[RetrievalField.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(RetrievalField.values().length);
	};
	static {
		final List<String> columns = Arrays.asList(RetrievalTable.COLUMNS);
		for (RetrievalField field : RetrievalField.values()) {
			columns.add(field.n());
			RetrievalTable.PROJECTION_MAP.put(field.n(), field.n());
		}
	};
	
	/**
	 * The subscription table is for holding subscription requests.
	 */
	public enum InterestField  implements TableField {
		REQUEST("request", "INTEGER PRIMARY KEY"),
		// The parent key
		
		SELECTION("selection", "TEXT");
		// The rows/tuples wanted.

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
	}
	
	public static interface InterestTable {

		public static final String DEFAULT_SORT_ORDER = ""; 
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[InterestField.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(InterestField.values().length);
		
		public static final String PARENT_KEY_REF = new StringBuilder()
		   .append(" FOREIGN KEY(").append(InterestField.REQUEST.n()).append(")")
		   .append(" REFERENCES ").append(Tables.REQUEST.n)
		   .append("(").append(RequestField._ID.n()).append(")")
		   .append(" ON DELETE CASCADE ")
		   .toString();
	}
	static {
		final List<String> columns = Arrays.asList(InterestTable.COLUMNS);
		for (InterestField field : InterestField.values()) {
			columns.add(field.n());
			InterestTable.PROJECTION_MAP.put(field.n(), field.n());
		}
	}


	/**
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
		
		REQUEST("parent", "INTEGER"),
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
	}
	public static interface DisposalTable {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[DisposalField.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(DisposalField.values().length);
		
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
	}
	static {
		final List<String> columns = Arrays.asList(DisposalTable.COLUMNS);
		for (DisposalField field : DisposalField.values()) {
			columns.add(field.n());
			DisposalTable.PROJECTION_MAP.put(field.n(), field.n());
		}
	}
	
	/**
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
	}
	
	public static interface RecipientTable {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = new String[RecipientField.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(RecipientField.values().length);
		
		public static final String PARENT_KEY_REF = new StringBuilder()
		   .append(" FOREIGN KEY(").append(RecipientField.DISPOSAL.n()).append(")")
		   .append(" REFERENCES ").append(Tables.DISPOSAL.n)
		   .append("(").append(DisposalField._ID.n()).append(")")
		   .append(" ON DELETE CASCADE ")
		   .toString();
	}
	static {
		final List<String> columns = Arrays.asList(RecipientTable.COLUMNS);
		for (RecipientField field : RecipientField.values()) {
			columns.add(field.n());
			RecipientTable.PROJECTION_MAP.put(field.n(), field.n());
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

		public static final String[] COLUMNS = new String[ChannelField.values().length];
		public static final Map<String,String> PROJECTION_MAP =
				new HashMap<String,String>(ChannelField.values().length);
		
		public static final String PARENT_KEY_REF = null;
	};
	static {
		final List<String> columns = Arrays.asList(ChannelTable.COLUMNS);
		for (ChannelField field : ChannelField.values()) {
			columns.add(field.n());
			ChannelTable.PROJECTION_MAP.put(field.n(), field.n());
		}
	};

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
	}
	
	// ===========================================================
	// Methods
	// ===========================================================

	public DistributorDataStore(Context context) {
		this.context = context;
		this.helper = new DataStoreHelper(this.context, null, null, VERSION); // Tables.NAME - to create in memory database

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

	/**
	 * Query set.
	 *
	 * @param projection
	 * @param selection
	 * @param selectionArgs
	 * @param sortOrder
	 * @return
	 */
	
	private static String RequestStatusQuery(Tables table) {
	return new StringBuilder()
	.append(" SELECT ").append(" * ")
	.append(" FROM ")
	.append(Tables.REQUEST.q()).append(" AS r ")
	.append(" WHERE ")
	.append(RequestField.TYPE.q("r")).append("=").append(table.o)
	.append(" AND EXISTS (SELECT * ")
	.append(" FROM ").append(Tables.DISPOSAL.q()).append(" AS d ")
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
	
	private static final String RequestViewCreate(String view, Tables table) {
	 return new StringBuilder()
	.append(" CREATE VIEW ").append(view).append(" AS ")
	.append(" SELECT ").append(" * ")
	.append(" FROM ")
	.append(Tables.REQUEST.q()).append(" AS r ")
	.append(" WHERE ")
	.append(RequestField.TYPE.q("r")).append('=').append('\'').append(table.o).append('\'')
	.append(';')
	.toString();
	}
	
	public synchronized Cursor queryRequest(String rel, 
			String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		try {
			this.openRead();
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(rel);
			qb.setProjectionMap(RequestTable.PROJECTION_MAP);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, selection, selectionArgs, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: RequestTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query postal {} {}", selection, selectionArgs);
		}
		return null;
	}
	
	public synchronized Cursor queryRequestByUuid(String[] projection, String uuid, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Tables.REQUEST.n);
			//qb.setProjectionMap(projection);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, REQUEST_UUID_QUERY, new String[]{ uuid }, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: RetrievalTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query retrieval by key {} {} {}", new Object[]{ projection, uuid });
		}
		return null;
	}
	static private final String REQUEST_UUID_QUERY = new StringBuilder()
	.append(RequestField.UUID.q(null)).append("=?")
	//.append(" AND ")
	//.append(RequestField.TOPIC.q(null)).append("=?")
	.toString();
	
	public synchronized Cursor queryRequestByTopic(String rel, String[] projection,
			String topic, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Tables.REQUEST.n);
			// qb.setProjectionMap(InterestTable.PROJECTION_MAP);

			// Get the database and run the query.
			final SQLiteDatabase db = this.helper.getReadableDatabase();
			return qb.query(db, projection, REQUEST_TOPIC_QUERY, new String[]{topic}, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: InterestTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query interest by key {} {}", projection, topic);
		}
		return null;
	}
	static private final String REQUEST_TOPIC_QUERY = new StringBuilder()
	.append(RequestField.TOPIC.q(null)).append("=?")
	.toString();
	
	//============ POSTAL METHODS ===================
	
	public synchronized Cursor queryPostal(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		return queryRequest(POSTAL_VIEW_NAME, projection, selection, selectionArgs, sortOrder);
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
	private static final String POSTAL_STATUS_QUERY = RequestStatusQuery(Tables.POSTAL);
	
	private static final String POSTAL_VIEW_NAME = new StringBuilder()
	  .append(Tables.POSTAL.q()).append("_view").toString();
	
	
	//============ RETRIEVAL METHODS ===================
	public synchronized Cursor queryRetrieval(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		return queryRequest(RETRIEVAL_VIEW_NAME, projection, selection, selectionArgs, sortOrder);
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
	private static final String RETRIEVAL_STATUS_QUERY = RequestStatusQuery(Tables.RETRIEVAL);
	
	private static final String RETRIEVAL_VIEW_NAME = new StringBuilder()
	  .append(Tables.RETRIEVAL.q()).append("_view").toString();
		
	public synchronized Cursor queryRetrievalByKey(String[] projection, String uuid, String topic, String sortOrder) {
		return queryRequestByUuid(projection, uuid, sortOrder);
	}
	
	//============ INTEREST METHODS ===================
	public synchronized Cursor queryInterest(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		return queryRequest(INTEREST_VIEW_NAME, projection, selection, selectionArgs, sortOrder);
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
			String topic, String sortOrder) {
		return queryRequestByTopic(INTEREST_VIEW_NAME, projection, topic, sortOrder);
	}
	
	private static final String INTEREST_STATUS_QUERY = RequestStatusQuery(Tables.INTEREST);
	
	private static final String INTEREST_VIEW_NAME = new StringBuilder()
	  .append(Tables.INTEREST.q()).append("_view").toString();
	
	//============ DISPOSAL METHODS ===================
	public synchronized Cursor queryDisposal(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Tables.DISPOSAL.n);
			qb.setProjectionMap(DisposalTable.PROJECTION_MAP);

			// Get the database and run the query.
			return qb.query(this.db, projection, selection, selectionArgs, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
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
			logger.trace("disposal ready {} {} {}", new Object[]{DISPOSAL_STATUS_QUERY, type, parent} );
			return db.rawQuery(DISPOSAL_STATUS_QUERY, new String[]{String.valueOf(type), String.valueOf(parent)});
		} catch(SQLiteException ex) {
			logger.error("sql error {}", ex.getLocalizedMessage());
		}
		return null;
	}
	private static final String DISPOSAL_STATUS_QUERY = new StringBuilder()
	.append(" SELECT * ")
	.append(" FROM ").append(Tables.DISPOSAL.q()).append(" AS d ")
	.append(" WHERE ").append(DisposalField.TYPE.q("d")).append("=? ")
	.append("   AND ").append(DisposalField.REQUEST.q("d")).append("=? ")
	.toString();

	//============ CHANNEL METHODS ===================
	public synchronized Cursor queryChannel(String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		try {
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

			qb.setTables(Tables.CHANNEL.n);
			qb.setProjectionMap(ChannelTable.PROJECTION_MAP);

			// Get the database and run the query.
			return qb.query(this.db, projection, selection, selectionArgs, null, null,
					(!TextUtils.isEmpty(sortOrder)) ? sortOrder
							: ChannelTable.DEFAULT_SORT_ORDER);
		} catch (IllegalArgumentException ex) {
			logger.error("query channel {} {}", selection, selectionArgs);
		}
		return null;
	}

	static final private String REQUEST_UPDATE_CLAUSE = new StringBuilder()
		.append(RequestField.TOPIC.q(null)).append("=?")
		.append(" AND ")
		.append(RequestField.PROVIDER.q(null)).append("=?")
		.toString();
	
	static final private String ROWID_CLAUSE = "_rowid_=?";
	
	/**
	 * Data Manipulation : upsert, delete
	 */
	
	//============ REQUEST METHODS ===================
	public synchronized long upsertRequest(ContentValues cv, DistributorState status, 
			String viewName, Tables table ) {
		try {
			final String uuid = cv.getAsString(RequestField.UUID.cv());
			final String topic = cv.getAsString(RequestField.TOPIC.cv());
			final String provider = cv.getAsString(RequestField.PROVIDER.cv());

			final long rowid;
			final String[] updateArgs = (uuid != null) 
					? new String[]{ uuid, topic, provider }
			        : new String[]{ topic, provider };
			final Cursor cursor = this.db.query(viewName, new String[] {RequestField._ID.q(null)}, 
					REQUEST_UPDATE_CLAUSE, updateArgs, null, null, null);
			if (cursor.getCount() > 0) {
				rowid = cursor.getLong(cursor.getColumnIndex(RequestField._ID.q(null)));
				final String[] rowid_arg = new String[]{ Long.toString(rowid) };
				this.db.update(Tables.REQUEST.n, cv, ROWID_CLAUSE, rowid_arg );
				this.db.update(table.n, cv, ROWID_CLAUSE, rowid_arg );
			} else {
				rowid = this.db.insert(Tables.REQUEST.n, RequestField.CREATED.n(), cv);
				cv.put(PostalField.REQUEST.n(), rowid);
				this.db.insert(table.n, RequestField.CREATED.n(), cv);
			}
			upsertDisposalByRequest(rowid, status);
			return rowid;
		} catch (IllegalArgumentException ex) {
			logger.error("upsert {} {}", cv, status);
		}
		return -1;
	}
	/**
	 * Upsert is a portmanteau of update and insert, thus,
	 * if a record with a matching key exists then update
	 * otherwise insert.INTEGER PRIMARY KEY 
	 */
	
	//============ REQUEST METHODS ===================
	public PostalRunner getPostalRunner(final AmmoRequest ar, final AmmoService svc) {
		return new PostalRunner(ar, svc);
	}
	public PostalRunner getPostalRunner(final Cursor pending, final AmmoService svc) {
		return new PostalRunner(pending, svc);
	}
	public class PostalRunner {
		public final UUID uuid;
		public final String auid;
		public final String topic;	
		public final Provider provider;
		public final DistributorPolicy.Topic policy;
		public final Moment serialMoment; 
		public final int priority;
		public final TimeTrigger expire;
		
		public DisposalTotalState totalState = null;
		public DistributorState status = null;
		public Payload payload = null;
		
		
		private PostalRunner(final AmmoRequest ar, final AmmoService svc) {
			
			this.uuid = UUID.fromString(ar.uuid); //UUID.randomUUID();
			this.auid = ar.uid;
			this.topic = ar.topic.asString();
			this.provider = ar.provider;
			this.policy = svc.policy().matchPostal(topic);
			this.serialMoment = ar.moment;
			
			this.priority = policy.routing.priority+ar.priority;
			this.expire = ar.expire;
		}
		
		private PostalRunner(final Cursor pending, final AmmoService svc) {
			final long id = pending.getInt(pending.getColumnIndex(RequestField._ID.n()));
			this.provider = new Provider(pending.getString(pending.getColumnIndex(RequestField.PROVIDER.n())));
			this.payload = new Payload(pending.getString(pending.getColumnIndex(PostalField.PAYLOAD.n())));
			this.topic = pending.getString(pending.getColumnIndex(RequestField.TOPIC.n()));
			this.uuid = UUID.fromString(pending.getString(pending.getColumnIndex(RequestField.UUID.n())));
			this.auid = pending.getString(pending.getColumnIndex(RequestField.AUID.n()));
			this.serialMoment = new Moment(pending.getInt(pending.getColumnIndex(RequestField.SERIAL_MOMENT.n())));
			this.policy = svc.policy().matchPostal(topic);
		
			this.priority = 
		}
		public long upsert(final DisposalTotalState totalState, final byte[] payload) {
			synchronized(DistributorDataStore.this) {	
				final ContentValues rqstValues = new ContentValues();
				rqstValues.put(RequestField.UUID.cv(), this.uuid.toString());
				rqstValues.put(RequestField.AUID.cv(), this.auid);
				rqstValues.put(RequestField.TOPIC.cv(), this.topic);
				rqstValues.put(RequestField.PROVIDER.cv(), this.provider.cv());
				
				rqstValues.put(RequestField.SERIAL_MOMENT.cv(), this.serialMoment.cv());
				rqstValues.put(RequestField.PRIORITY.cv(), this.policy.routing.priority+this.priority);
				rqstValues.put(RequestField.EXPIRATION.cv(), this.expire.cv());
				
				rqstValues.put(RequestField.CREATED.cv(), System.currentTimeMillis());				
				rqstValues.put(RequestField.DISPOSITION.cv(), totalState.cv());
				if (payload != null) rqstValues.put(PostalField.PAYLOAD.cv(), payload);
				
				final ContentValues noticeValues = new ContentValues();
				// values.put(PostalTableSchema.ORDER.cv(), ar.order.cv());
		
				// values.put(PostalTableSchema.UNIT.cv(), 50);
				return upsertRequest(rqstValues, status, POSTAL_VIEW_NAME, Tables.POSTAL);
			}
		}
		
		public int delete(String tupleId) {
			final String select = new StringBuilder()
		        .append(RequestField.PROVIDER.q(null)).append("=?")
		        .append(" AND ")
				.append(RequestField.TOPIC.q(null)).append("=?")
				.toString();
			final String[] args = new String[] {tupleId, this.topic};
			
			try {
				final SQLiteDatabase db = DistributorDataStore.this.helper.getWritableDatabase();
				final int count = db.delete(Tables.POSTAL.n, select, args);
				final int disposalCount = db.delete(Tables.DISPOSAL.n, DISPOSAL_POSTAL_ORPHAN_CONDITION, null);
				logger.trace("Postal delete {} {}", count, disposalCount);
				return count;
			} catch (IllegalArgumentException ex) {
				logger.error("delete postal {} {}", select, args);
			}
			return 0;
		}
	}
	
	public synchronized long upsertRetrieval(ContentValues cv, DistributorState status) {
		return upsertRequest(cv, status, RETRIEVAL_VIEW_NAME, Tables.RETRIEVAL);
	}
	
	public synchronized long upsertInterest(ContentValues cv, DistributorState status) {
		return upsertRequest(cv, status, RETRIEVAL_VIEW_NAME, Tables.RETRIEVAL);
	}

	//============ DISPOSAL METHODS ===================
	private synchronized long[] upsertDisposalByRequest(long requestId, DistributorState status) {
		try {
			final long[] idArray = new long[status.size()];
			int ix = 0;
			for (Entry<String,DisposalState> entry : status.entrySet()) {
				idArray[ix] = upsertDisposalByRequest(requestId, entry.getKey(), entry.getValue());
				ix++;
			}
			return idArray;
		} catch (IllegalArgumentException ex) {
			logger.error("upsert disposal by parent {} {} {}", new Object[]{requestId, status});
		}
		return null;
	}
	private synchronized long upsertDisposalByRequest(long requestId, String channel, DisposalState status) {
		try {
			final ContentValues cv = new ContentValues();
			cv.put(DisposalField.REQUEST.cv(), requestId);
			cv.put(DisposalField.CHANNEL.cv(), channel);
			cv.put(DisposalField.STATE.cv(), (status == null) ? DisposalState.PENDING.o : status.o);

			final String requestIdStr = String.valueOf(requestId);
			final int updateCount = this.db.update(Tables.DISPOSAL.n, cv, 
					DISPOSAL_UPDATE_CLAUSE, new String[]{requestIdStr , channel } );
			if (updateCount > 0) {
				final Cursor cursor = this.db.query(Tables.DISPOSAL.n, new String[]{DisposalField._ID.n()}, 
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
			return this.db.insert(Tables.DISPOSAL.n, DisposalField.TYPE.n(), cv);
		} catch (IllegalArgumentException ex) {
			logger.error("upsert disposal {} {} {} {}", new Object[]{requestId, channel, status});
		}
		return 0;
	}
	static final private String DISPOSAL_UPDATE_CLAUSE = new StringBuilder()
	.append(DisposalField.REQUEST.q(null)).append("=?")
	.append(" AND ")
	.append(DisposalField.CHANNEL.q(null)).append("=?").toString();


	public synchronized long upsertChannelByName(String channel, ChannelState status) {
		try {
			final ContentValues cv = new ContentValues();		
			cv.put(ChannelField.STATE.cv(), status.cv());

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
	 * When a channel is deactivated all of its subscriptions  
	 * will need to be re-done on re-connect.
	 * Retrievals and postals won't have this problem,
	 * TODO unless they are queued.
	 * @param channel
	 * @return
	 */
	public synchronized int deactivateDisposalStateByChannel(String channel) {
		try {
			return this.db.update(Tables.DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
					DISPOSAL_DEACTIVATE_CLAUSE, new String[]{ channel } );
		} catch (IllegalArgumentException ex) {
			logger.error("deactivateDisposalStateByChannel {} ", channel);
		}
		return 0;
	}	
	static final private String DISPOSAL_DEACTIVATE_CLAUSE = new StringBuilder()
	.append(DisposalField.CHANNEL.q(null)).append("=?")
	.append(" AND ")
	.append(DisposalField.TYPE.q(null)).append(" IN ( ")
	.append(Tables.INTEREST.qv()).append(')')
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
			return this.db.update(Tables.DISPOSAL.n, DISPOSAL_PENDING_VALUES, 
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

	/**
	 * Update an object represented in the database.
	 * Any reasonable update will need to know how to select an existing object.
	 */
	public synchronized long updateRequestById(long requestId, ContentValues cv, DistributorState state) {
		if (state == null && cv == null) return -1;
		if (cv == null) cv = new ContentValues();
		
		if (state != null) {
			this.upsertDisposalByRequest(requestId, state);
			cv.put(RequestField.DISPOSITION.n(), state.aggregate().cv());
		}	
		try {
			return this.db.update(Tables.POSTAL.n, cv, "\"_id\"=?", new String[]{ String.valueOf(requestId) } );
		} catch (IllegalArgumentException ex) {
			logger.error("updatePostalByKey {} {}", requestId, cv);
		}
		return 0;
	}
	public synchronized long updatePostalByKey(long requestId, ContentValues cv, DistributorState state) {
		return updateRequestById(requestId, cv, state);
	}
	public synchronized long updatePostalByKey(long requestId, String channel, final DisposalState state) {
		return this.upsertDisposalByRequest(requestId, channel, state);
	}

	public synchronized long updateRetrievalByKey(long requestId, ContentValues cv, final DistributorState state) {
		return updateRequestById(requestId, cv, state);
	}
	public synchronized long updateRetrievalByKey(long requestId, String channel, final DisposalState state) {
		return this.upsertDisposalByRequest(requestId, channel, state);
	}

	public synchronized long updateInterestByKey(long requestId, ContentValues cv, final DistributorState state) {
		return updateRequestById(requestId, cv, state);
	}
	public synchronized long updateInterestByKey(long requestId, String channel, final DisposalState state) {
		return this.upsertDisposalByRequest(requestId, channel, state);
	}


	public ContentValues initializeRequestDefaults(ContentValues values) {
		final Long now = Long.valueOf(System.currentTimeMillis());
		
		if (!values.containsKey(RequestField.TOPIC.n())) {
			values.put(RequestField.TOPIC.n(),"unknown");
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
					SerialMoment.EAGER.o);
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
		
	/** Insert method helper */
	public ContentValues initializePostalDefaults(ContentValues values) {
		
		initializeRequestDefaults(values);
		
		if (!values.containsKey(PostalField.UNIT.n())) {
			values.put(PostalField.UNIT.n(), "unknown");
		}
		if (!values.containsKey(PostalField.VALUE.n())) {
			values.put(PostalField.VALUE.n(), -1);
		}
		if (!values.containsKey(PostalField.DATA.n())) {
			values.put(PostalField.DATA.n(), "");
		}
		return values;
	}

	/** Insert method helper */
	protected ContentValues initializePublicationDefaults(ContentValues values) {
		// final Long now = Long.valueOf(System.currentTimeMillis());

		initializeRequestDefaults(values);
		
		return values;
	}

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

	/** Insert method helper */
	protected ContentValues initializeSubscriptionDefaults(ContentValues values) {


		initializeRequestDefaults(values);
		
		if (!values.containsKey(InterestField.SELECTION.n())) {
			values.put(InterestField.SELECTION.n(), "");
		}
		return values;
	}

	/**
	 * Delete set
	 */

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

	// ========= POSTAL : DELETE ================

	public synchronized int deletePostalGarbage() {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int expireCount = db.delete(Tables.POSTAL.n, 
					POSTAL_EXPIRATION_CONDITION, getRelativeExpirationTime(POSTAL_DELAY_OFFSET));
			final int disposalCount = db.delete(Tables.DISPOSAL.n, 
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

	
	// ========= RETRIEVAL : DELETE ================

	public synchronized int deleteRetrieval(String selection, String[] selectionArgs) {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int count = db.delete(Tables.RETRIEVAL.n, selection, selectionArgs);
			final int disposalCount = db.delete(Tables.DISPOSAL.n, DISPOSAL_RETRIEVAL_ORPHAN_CONDITION, null);
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
			final int expireCount = db.delete(Tables.RETRIEVAL.n, 
					RETRIEVAL_EXPIRATION_CONDITION, getRelativeExpirationTime(RETRIEVAL_DELAY_OFFSET));
			final int disposalCount = db.delete(Tables.DISPOSAL.n, 
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
			db.delete(Tables.DISPOSAL.n, DISPOSAL_PURGE, new String[]{ Tables.RETRIEVAL.qv()});
			return db.delete(Tables.RETRIEVAL.n, null, null);
		} catch (IllegalArgumentException ex) {
			logger.error("purgeRetrieval");
		}
		return 0;
	}

	// ========= INTEREST : DELETE ================

	public synchronized int deleteInterest(String selection, String[] selectionArgs) {
		try {
			final SQLiteDatabase db = this.helper.getWritableDatabase();
			final int count = db.delete(Tables.INTEREST.n, selection, selectionArgs);
			logger.trace("Interest delete {} {}", count);
			return count;
		} catch (IllegalArgumentException ex) {
			logger.error("delete postal {} {}", selection, selectionArgs);
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
			db.delete(Tables.DISPOSAL.n, DISPOSAL_PURGE, new String[]{ Tables.INTEREST.qv()});
			return db.delete(Tables.INTEREST.n, null, null);
		} catch (IllegalArgumentException ex) {
			logger.error("purgeInterest");
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
		private final Logger logger = LoggerFactory.getLogger(DataStoreHelper.class);

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

		private StringBuilder addFieldsToCreation(List<TableField> fields) {
			if (fields.size() < 1) return null;
			
			final StringBuilder sb = new StringBuilder();
			final TableField first = fields.get(1);
			sb.append(first.q(null)).append(' ').append(first.t());
			
			for (TableField field : fields.subList(1, fields.size()) ) {
			    sb.append(",").append(field.q(null)).append(' ').append(field.t());
			}
			return sb;
		}
		
		@Override
		public synchronized void onCreate(SQLiteDatabase db) {
			logger.trace("bootstrapping database");

			try {	
				final StringBuilder sb = new StringBuilder().append("CREATE TABLE ");
				sb.append('"').append(Tables.REQUEST.n).append('"');
				sb.append(" ( ");
				sb.append(addFieldsToCreation(Arrays.asList(Tables.REQUEST.getFields())));
				sb.append(addFieldsToCreation(Arrays.asList(Tables.POSTAL.getFields())));
				sb.append(addFieldsToCreation(Arrays.asList(Tables.RETRIEVAL.getFields())));
				sb.append(addFieldsToCreation(Arrays.asList(Tables.INTEREST.getFields())));
				sb.append(");");
				
				db.execSQL(sb.toString());
				
				final StringBuilder pkidx = new StringBuilder()
				   .append("CREATE UNIQUE INDEX ").append(Tables.REQUEST.n).append("_pkidx")
				   .append(" ON ").append(Tables.REQUEST.n).append("(").append(RequestField._ID.q(null))
				   .append(");");
				
				db.execSQL(pkidx.toString());

				final StringBuilder spo = new StringBuilder().append("CREATE TABLE ");
				spo.append('"').append(Tables.REQUEST.n).append('"');
				sb.append(" ( ");
				sb.append(addFieldsToCreation(Arrays.asList(Tables.REQUEST.getFields())));
				sb.append(addFieldsToCreation(Arrays.asList(Tables.POSTAL.getFields())));
				sb.append(addFieldsToCreation(Arrays.asList(Tables.RETRIEVAL.getFields())));
				sb.append(addFieldsToCreation(Arrays.asList(Tables.INTEREST.getFields())));
				sb.append(");");
				
				db.execSQL(RequestViewCreate(POSTAL_VIEW_NAME, Tables.POSTAL));
				db.execSQL(RequestViewCreate(RETRIEVAL_VIEW_NAME, Tables.RETRIEVAL));
				db.execSQL(RequestViewCreate(INTEREST_VIEW_NAME, Tables.INTEREST));
				
			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				for(final String sql : Tables.DISPOSAL.sqlCreate() )
					db.execSQL(sql);
				
				// === Additional Indices ======
				db.execSQL(new StringBuilder()
				.append("CREATE UNIQUE INDEX ") 
				.append(Tables.DISPOSAL.qIndex())
				.append(" ON ").append(Tables.DISPOSAL.q())
				.append(" ( ").append(DisposalField.TYPE.q(null))
				.append(" , ").append(DisposalField.REQUEST.q(null))
				.append(" , ").append(DisposalField.CHANNEL.q(null))
				.append(" ) ")
				.toString() );

			} catch (SQLException ex) {
				ex.printStackTrace();
			}

			try {
				for(final String sql : Tables.CHANNEL.sqlCreate() )
				db.execSQL(sql);
			} catch (SQLException ex) {
				ex.printStackTrace();
			}
			
			try {
				for(final String sql : Tables.PRESENCE.sqlCreate() )
				db.execSQL(sql);
			} catch (SQLException ex) {
				ex.printStackTrace();
			}


		}

		@Override
		public synchronized void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			logger.warn("Upgrading database from version {} to {} which will destroy all old data",
					oldVersion, newVersion);
			this.clear(db);
			
			onCreate(db);
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
		
	

		public synchronized void clear(SQLiteDatabase db) {
			for (Tables table : Tables.values()) {
				try {
					db.execSQL(table.sqlDrop().toString());
				} catch (SQLiteException ex) {
					logger.warn("defective database being dropped {}", ex.getLocalizedMessage());
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
			
			final File original = context.getDatabasePath(Tables.NAME);
			logger.info("backup of database {} -> {}", original, backup);
			if (original.renameTo(backup)) {
				logger.info("archival succeeded");
				return true;
			}
			if (! context.deleteDatabase(Tables.NAME)) {
				logger.warn("file should have been renamed, deleted instead"); 
			}
			return false;
		}
	}
	

}

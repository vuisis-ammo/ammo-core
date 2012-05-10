package edu.vu.isis.ammo.core.distributor.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.provider.BaseColumns;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.store.Disposal.DisposalField;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableField;
import edu.vu.isis.ammo.core.distributor.store.TableHelper.TableFieldState;

public class Recipient {
	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory.getLogger("class.store.recipient");

	/**
	 * ===================
	 *  RECIPIENT
	 * ===================
	 * 
	 * The recipient table extends the disposal table.
	 * Once the message has been sent any acknowledgments will produce 
	 * multiple additional recipient messages.
	 */
	public enum RecipientField  implements TableField {
		_ID(BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"),

		REQUEST("request", "INTEGER"),
		// The _ID of the parent Request

		DISPOSAL("type", "INTEGER"),
		// Meaning the parent type: subscribe, retrieval, postal, publish

		STATE("state", "INTEGER");
		// State of the request on the channel

		final public TableFieldState impl;

		private RecipientField(String name, String type) {
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

	public static interface RecipientConstants {

		public static final String DEFAULT_SORT_ORDER = ""; // "modified_date DESC";
		public static final String PRIORITY_SORT_ORDER = BaseColumns._ID + " ASC";

		public static final String[] COLUMNS = Initializer.getColumns();
		public static final Map<String,String> PROJECTION_MAP = Initializer.getProjection();

		public static final String FOREIGN_KEY = new StringBuilder()
		.append(" FOREIGN KEY(").append(RecipientField.DISPOSAL.n()).append(")")
		.append(" REFERENCES ").append(Tables.POSTAL_DISPOSAL.n)
		.append("(").append(DisposalField._ID.n()).append(")")
		.append(" ON DELETE CASCADE ")
		.toString();

		public class Initializer {
			private static String[] getColumns() {
				final List<String> columns = new ArrayList<String>();
				for (RecipientField field : RecipientField.values()) {
					columns.add(field.n());
				}
				return columns.toArray(new String[columns.size()]);
			}
			private static Map<String,String> getProjection() {
				final Map<String,String> projection = new HashMap<String,String>();
				for (RecipientField field : RecipientField.values()) {
					projection.put(field.n(), field.n());
				}
				return projection;
			}
		};
	}


	public static RecipientWorker getWorker(final DistributorDataStore store) {
		return new RecipientWorker(store);
	}
	/** 
	 * Store access class
	 */
	public static class RecipientWorker {
		final DistributorDataStore store;

		private RecipientWorker(final DistributorDataStore store) {
			this.store = store;
		}

		/**
		 *
		 */
		public void upsert() {
			synchronized(this.store) {	

			}
		}

	}



}

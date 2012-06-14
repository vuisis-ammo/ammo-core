package edu.vu.isis.ammo.core.distributor.store;

import java.util.Arrays;
import java.util.List;

public class PersistenceHelper {
	/**
	 * Interface for defining sets of fields.
	 */

	public interface TableField {	
		// public List<TableFieldState> getState();

		/**
		 * Get the quoted field name.
		 * If the table ref is provided then prefix
		 * the quoted string with it.
		 * e.g. if tableRef is "r" and the name is "foo"
		 * then the returned value is 'r."foo"'
		 * 
		 * @param tableRef
		 * @return the field name enclosed in double quotes.
		 */
		public String q(String tableRef); 

		/**
		 * Get the field name suitable for using in ContentValues
		 * 
		 * @return the same as n() but returns "null" as a string
		 *          when the name is null.
		 */
		public String cv();

		/**
		 * Get the name from the implementation.
		 *
		 * @return the unquoted/unmodified field name.
		 */
		public String n(); 

		/**
		 * Get the type from the implementation.
		 * 
		 * @return the unquoted type name.
		 */
		public String t(); 

		/**
		 * form the field clause suitable for use in sql create
		 * @return
		 */
		public String ddl();
	}

	/**
	 * A holder class for the functions implementing the
	 * methods of the TableField interface.
	 */
	static public class TableFieldState {
		final public String n;
		final public String t;

		private TableFieldState(String name, String type) {	
			this.n = name;
			this.t = type;
		}

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
		 * suitable for use in the table creation.
		 */
		public String ddl() {
			return new StringBuilder()
			.append('"').append(this.n).append('"').append(' ').append(this.t)
			.toString();
		}

		public static TableFieldState getInstance(String name, String type) {
			return new TableFieldState(name, type);
		}
	}

	static public String ddl(TableField[] values) {
		final List<TableField> fields = Arrays.asList(values);
		final StringBuilder sb = new StringBuilder();
		if (fields.size() < 1) return "";

		final TableField first = fields.get(0);
		sb.append(first.ddl());

		for (TableField field : fields.subList(1, fields.size()) ) {
			sb.append(",").append(field.ddl());
		}
		return sb.toString();
	}


}

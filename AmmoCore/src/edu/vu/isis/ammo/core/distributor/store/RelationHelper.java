package edu.vu.isis.ammo.core.distributor.store;


public class RelationHelper {
	/**
	 * Interface for defining sets of fields.
	 */

	public static class RelationField {	

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

		public String q(String tableRef) { 

			if (tableRef == null) {
				return new StringBuilder()
				.append('"').append(this).append('"')
				.toString();
			}
			return new StringBuilder()
			.append(tableRef).append('.')
			.append('"').append(this).append('"')
			.toString();
		}

		/**
		 * Get the field name suitable for using in ContentValues
		 * 
		 * @return the same as n() but returns "null" as a string
		 *          when the name is null.
		 */
		public String cv() {
			return String.valueOf(this);
		}

		/**
		 * Get the name from the implementation.
		 *
		 * @return the unquoted/unmodified field name.
		 */
		public String n() {
			return "name";
		}

		/**
		 * Get the type from the implementation.
		 * 
		 * @return the unquoted type name.
		 */
		public String t() {
			return "title";
		}

		/**
		 * form the field clause suitable for use in sql create
		 * @return
		 */
		// public String ddl();
	}

	public static RelationField newField(String id, String string) {
        return new RelationField();
	}

}

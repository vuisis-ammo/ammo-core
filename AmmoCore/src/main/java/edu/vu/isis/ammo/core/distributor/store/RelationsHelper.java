/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package edu.vu.isis.ammo.core.distributor.store;

import edu.vu.isis.ammo.core.provider.Relations;


public class RelationsHelper {
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
	

	final private Relations relation;
	
	public RelationsHelper(final Relations that) {
		this.relation = that;
	}
	
	// The quoted table name
	public  String q() {
		return new StringBuilder().append('"').append(relation.n).append('"').toString();
	}
	// The quoted table name as a value
	public String qv() {
		return new StringBuilder().append('\'').append(this.cv()).append('\'').toString();
	}
	public String cv() {
		return String.valueOf(relation.nominal);
	}
	// The quoted index name
	public String qIndex() {
		return new StringBuilder().append('"').append(relation.n).append("_index").append('"').toString();
	}

	/**
	 * Produce string builders of the form...
	 * CREATE TABLE "<table-name>" ( <row defs> );
	 * @param postal 
	 *
	 */

	public static String sqlCreate(Relations relation, String fields) {
		return new StringBuilder()
		.append("CREATE TABLE ")
		.append('"').append(relation.n).append('"')
		.append(" (").append(fields).append(");")
		.toString();
	}

	/**
	 * Produce string builders of the form...
	 * DROP TABLE "<table-name>";
	 *
	 */
	public static String sqlDrop(Relations relation) {
		return new StringBuilder()
		.append("DROP TABLE ")
		.append('"').append(relation.n).append('"')
		.append(";")
		.toString();
	}

	/**
	 * The ordinal value provided by the uri matcher is used to 
	 * index into the enum.
	 * 
	 * @param ordinal
	 * @return the corresponding enum Relation object
	 */
	public static Relations getValue(int ordinal) {
		return Relations.values()[ordinal];
	}

}

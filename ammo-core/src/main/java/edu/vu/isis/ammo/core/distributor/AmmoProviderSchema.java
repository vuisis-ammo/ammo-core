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


/**
 * These fields are replicated in the generated code.
 * see AmmoGenerator/content_provider/template/java/ammo_content_provider.stg
 */
public class AmmoProviderSchema {

	public static final String _DISPOSITION = "_disp"; 
	public static final String _RECEIVED_DATE = "_received_date";

	/**
	 * Indicate the source of the last update to the tuple.
	 */
	public enum Disposition {
		/** the last update for this record is from a remote received message. */
		REMOTE(0), 
		/** the last update was produced locally (probably the creation). */
		LOCAL(1);

		private final int code;

		private Disposition(int code) {
			this.code = code;
		}

		public int toCode() {
			return this.code;
		}

		public static Disposition fromCode(final int code) {
			switch (code) {
			case 0: return REMOTE;
			case 1: return LOCAL;
			}
			return LOCAL;
		}

		@Override
		public String toString() {
			return this.name();
		}
		
		public static Disposition fromString(final String value) {
			try {
				return (value == null) ? Disposition.LOCAL 
						: (value.startsWith( "REMOTE" )) ? Disposition.REMOTE
						: Disposition.LOCAL;
			} catch (Exception ex) {
				return Disposition.LOCAL;
			}
		}
	}
}

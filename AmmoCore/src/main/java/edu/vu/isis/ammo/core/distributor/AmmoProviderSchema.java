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

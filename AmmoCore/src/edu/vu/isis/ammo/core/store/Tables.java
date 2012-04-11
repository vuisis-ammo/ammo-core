package edu.vu.isis.ammo.core.store;


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
	PRESENCE(1, "presence"),
	CAPABILITY(2, "capability"),
	CHANNEL(3, "channel"),
	REQUEST(4, "request"),
	POSTAL(5, "postal"),
	RETRIEVAL(6, "retrieval"),
	INTEREST(7, "interest"),
	DISPOSAL(8, "disposal"),
	RECIPIENT(9, "recipient");

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

}

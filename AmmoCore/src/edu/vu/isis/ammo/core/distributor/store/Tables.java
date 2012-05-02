package edu.vu.isis.ammo.core.distributor.store;


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
	PRESENCE(101, "presence"),
	CAPABILITY(102, "capability"),
	CHANNEL(103, "channel"),
	REQUEST(200, "request"),
	POSTAL(2100, "postal"),
	POSTAL_DISPOSAL(211, "postal_disposal"),
	RETRIEVAL(220, "retrieval"),
	RETRIEVAL_DISPOSAL(221, "retrieval_disposal"),
	SUBSCRIBE(230, "subscribe"),
	SUBSCRIBE_DISPOSAL(231, "subscribe_disposal"),
	RECIPIENT(300, "recipient");

	final public int o;
	final public String n;

	private Tables(int ordinal, String name) {
		this.o = ordinal;
		this.n = name;
	}

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

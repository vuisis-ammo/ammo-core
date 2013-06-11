package edu.vu.isis.ammo.core;

/**
 * The channel status map.
 * <p>
 * It should not be changed by the main thread.
 */
public enum ChannelChange {
	/** When the channel is ready for active use */
	ACTIVATE(1),
	/** When the channel is definitely not ready for active use */
	DEACTIVATE(2),
	/** When the channel may be usable */
	REPAIR(3);

	final public int o; // ordinal

	private ChannelChange(int o) {
		this.o = o;
	}

	public int cv() {
		return this.o;
	}

	static public ChannelChange getInstance(int ordinal) {
		return ChannelChange.values()[ordinal];
	}

	public String q() {
		return new StringBuilder().append("'").append(this.o).append("'")
				.toString();
	}
}
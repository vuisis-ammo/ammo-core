package edu.vu.isis.ammo.core.distributor.store;

import edu.vu.isis.ammo.core.provider.TemporalState;

/**
 * This class is primarily concerned with deriving the TemporalState.
 * 
 */
abstract public class TemporalItem {
	/**
	 * The time delay from last seen to absent.
	 */
	public static final int ABSENT_LIFESPAN = 20 * 60 * 1000; 
	public static final int LOST_LIFESPAN = 5 * 60 * 1000;
	public static final int MISSED_LIFESPAN = 1 * 60 * 1000;
	public static final int RARE_RATE_LIMIT = 1;

	public final long first;
	public long latest;
	public int count;

	public TemporalItem() {
		this.first = System.currentTimeMillis();
		this.latest = this.first;
		this.count = 1;
	}
	
	public void update() {
		this.latest = Long.valueOf(System.currentTimeMillis());
		this.count++;
	}
	
	public long getExpiration() {
		return 0;
	}
	
	public TemporalState getState() {
		final long now = Long.valueOf(System.currentTimeMillis());
		final long elapsedTime = now - this.latest;
		
		if (elapsedTime > ABSENT_LIFESPAN) return TemporalState.ABSENT;
		if (elapsedTime > LOST_LIFESPAN) return TemporalState.LOST;
		if (elapsedTime > MISSED_LIFESPAN) return TemporalState.MISSED;
		if (this.latest != this.first && (count/(this.latest - this.first)) < RARE_RATE_LIMIT) return TemporalState.RARE;
		return TemporalState.PRESENT;
	}
	
	@Override 
	public String toString() {
		return new StringBuilder().
				append("first=\"").append(this.first).append("\",").
				append("latest=\"").append(this.latest).append("\",").
				append("count=\"").append(this.count).append("\"").
				toString();
	}
}

package edu.vu.isis.ammo.core.distributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;

/**
 * The dispersal vector is related to the distribution policy
 * and a particular request.
 * The particular request will have a topic which maps to the distribution policy.
 * When all of the clauses of a topic's policy have been 
 * satisfied the total is true, otherwise it is false.
 *
 */
public class DispersalVector {

	private final Map<String, DisposalState> stateMap;
	private boolean total;
	
	private DispersalVector() {
		this.stateMap = new HashMap<String, DisposalState>();
		this.total = false;
	}
	
	static public DispersalVector newInstance() {
		return new DispersalVector();
	}
	
	public boolean total() {
		return this.total;
	}
	
	public DispersalVector total(boolean state) {
		this.total = state;
		return this;
	}
	
	
	public DisposalState put(String key, DisposalState value) {
		return stateMap.put(key, value);
	}
	
	public DisposalState get(String key) {
		return stateMap.get(key);
	}
	
	public boolean containsKey(String key) {
		return stateMap.containsKey(key);
	}
	
	public Set<Entry<String, DisposalState>> entrySet() {
		return stateMap.entrySet();
	}
	
	public int size() {
		return stateMap.size();
	}
	
	public DispersalVector and(boolean clause) {
		this.total &= clause;
		return this;
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder().append("status:");
		for( final Map.Entry<String,DisposalState> entry : this.stateMap.entrySet()) {
		   sb.append('\n').append(entry.getKey()).append(" : ").append(entry.getValue());
		}
		return sb.toString();
	}
	
	
}

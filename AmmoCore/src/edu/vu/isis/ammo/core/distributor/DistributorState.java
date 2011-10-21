package edu.vu.isis.ammo.core.distributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelDisposal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.RequestDisposal;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Routing;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;

/**
 * The dispersal vector is related to the distribution policy
 * and a particular request.
 * The particular request will have a topic which maps to the distribution policy.
 * When all of the clauses of a topic's policy have been 
 * satisfied the total is true, otherwise it is false.
 *
 */
public class DistributorState {
	private static final Logger logger = LoggerFactory.getLogger("ammo-dsp");

	private final Map<String, ChannelDisposal> stateMap;
	private boolean total;
	public final Routing policy;
	
	private DistributorState(Routing policy) {
		this.stateMap = new HashMap<String, ChannelDisposal>();
		this.total = false;
		this.policy = policy;
	}
	
	static public DistributorState newInstance(Routing routing) {
		return new DistributorState(routing);
	}
	
	public boolean total() {
		return this.total;
	}
	
	public DistributorState total(boolean state) {
		this.total = state;
		return this;
	}
	
	
	public ChannelDisposal put(String key, ChannelDisposal value) {
		return stateMap.put(key, value);
	}
	
	public ChannelDisposal get(String key) {
		return stateMap.get(key);
	}
	
	public boolean containsKey(String key) {
		return stateMap.containsKey(key);
	}
	
	public Set<Entry<String, ChannelDisposal>> entrySet() {
		return stateMap.entrySet();
	}
	
	public int size() {
		return stateMap.size();
	}
	
	public DistributorState and(boolean clause) {
		this.total &= clause;
		return this;
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder().append("status:");
		for( final Map.Entry<String,ChannelDisposal> entry : this.stateMap.entrySet()) {
		   sb.append('\n').append(entry.getKey()).append(" : ").append(entry.getValue());
		}
		return sb.toString();
	}
	
	/**
	 * Attempt to satisfy the distribution policy for this message's topic.
	 * 
	 * The policies are processed in "short circuit" disjunctive normal form.
	 * Each clause is evaluated there results are 'anded' together, hence disjunction. 
	 * Within a clause each literal is handled in order until one matches
	 * its prescribed condition, effecting a short circuit conjunction.
	 * (It is short circuit as only the first literal to be true is evaluated.)
	 * 
	 * In order for a topic to evaluate to success all of its clauses must evaluate true.
	 * In order for a clause to be true at least (exactly) one of its literals must succeed.
	 * A literal is true if the condition of the term matches the 
	 * 'condition' attribute for the term.   
	 * 
	 * @see scripts/tests/distribution_policy.xml for an example.
	 */
	public DistributorState multiplexRequest(AmmoService that, RequestSerializer serializer) {
		logger.trace("::multiplex request");

		if (this.policy == null) {
			logger.error("no matching routing topic");
			final AmmoGatewayMessage agmb = serializer.act(Encoding.getDefault());
			final ChannelDisposal actualCondition =
					that.sendRequest(agmb, DistributorPolicy.DEFAULT);
			this.put(DistributorPolicy.DEFAULT, actualCondition);
			return this;
		} 
		// evaluate rule
		this.total(true);
		
		for (final DistributorPolicy.Clause clause : this.policy.clauses) {
			boolean clauseSuccess = false;
			
			// evaluate clause
			for (DistributorPolicy.Literal literal : clause.literals) {
				final String term = literal.term;
				final boolean goalCondition = literal.condition;
				final ChannelDisposal priorCondition = 
						(this.containsKey(term)) ? this.get(term) : ChannelDisposal.PENDING;
				logger.debug("prior {} {} {}", new Object[]{term, priorCondition, goalCondition});
				if (priorCondition.goalReached(goalCondition)) {
					clauseSuccess = true;
					logger.trace("clause previously satisfied {} {}", this, clause);
					break;
				}
			}
			if (clauseSuccess) continue;
			
			// evaluate clause
			for (DistributorPolicy.Literal literal : clause.literals) {
				final String term = literal.term;
				final boolean goalCondition = literal.condition;
				final ChannelDisposal priorCondition = 
						(this.containsKey(term)) ? this.get(term) : ChannelDisposal.PENDING;
						
				final ChannelDisposal actualCondition;
				switch (priorCondition) {
				case PENDING:
					// this.deliver(term);
					final AmmoGatewayMessage agmb = serializer.act(literal.encoding);
					actualCondition = that.sendRequest(agmb, term);
					this.put(term, actualCondition);
					logger.trace("attempting {} over {}", agmb, term);
					break;
				default:
					actualCondition = priorCondition;
				} 
				if (actualCondition.goalReached(goalCondition)) {
					clauseSuccess = true;
					logger.trace("clause satisfied {} {}", this, clause);
					break;
				}
			}
			this.and(clauseSuccess);
		}
		return this;
	}

	/**
	 * examine the states of each channel and generate an aggregate status.
	 * @return
	 */
	public RequestDisposal aggregate() {
		if (this.total) return RequestDisposal.COMPLETE;
		
		int aggregated = 0x0000;
		for (final Entry<String,ChannelDisposal> entry : this.stateMap.entrySet()) {
			aggregated |= entry.getValue().o;
		}
		switch (ChannelDisposal.values()[aggregated]) {
		case FAILED:
			return  RequestDisposal.INCOMPLETE;
		case PENDING:
		case NEW:
			return  RequestDisposal.NEW;
		case QUEUED: 
			return RequestDisposal.DISTRIBUTE;
		case SENT: 
		case TOLD:
		case DELIVERED:
			return RequestDisposal.COMPLETE;
		}
		if (0 < (aggregated & ChannelDisposal.FAILED.o))
			return RequestDisposal.INCOMPLETE;
		if (0 < (aggregated & (ChannelDisposal.PENDING.o | ChannelDisposal.NEW.o) ))
			return RequestDisposal.DISTRIBUTE;
		if (0 < (aggregated & (ChannelDisposal.SENT.o | ChannelDisposal.TOLD.o | ChannelDisposal.DELIVERED.o) ))
			return RequestDisposal.COMPLETE;
		return RequestDisposal.DISTRIBUTE;
	}
	
}

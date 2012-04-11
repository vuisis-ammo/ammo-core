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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Routing;
import edu.vu.isis.ammo.core.store.DistributorDataStore.ChannelStatus;
import edu.vu.isis.ammo.core.store.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.store.DistributorDataStore.DisposalTotalState;

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

	private final Map<String, DisposalState> stateMap;
	private boolean total;
	public final Routing policy;

	private DistributorState(Routing policy) {
		this.stateMap = new HashMap<String, DisposalState>();
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

	public DistributorState and(boolean clause) {
		this.total &= clause;
		return this;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder()
		        .append("type: ").append(this.type).append(" ")
				.append("status:");
		for( final Map.Entry<String,DisposalState> entry : this.stateMap.entrySet()) {
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

			final ChannelStatus channelStatus = that.checkChannel(DistributorPolicy.DEFAULT);
			final DisposalState actualCondition;
			switch (channelStatus) {
			case READY:
				actualCondition = serializer.act(that,Encoding.getDefault(),DistributorPolicy.DEFAULT);
				break;
			default:
				actualCondition = channelStatus.inferDisposal();	
			}
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
				final DisposalState priorCondition = (this.containsKey(term)) ? this.get(term) : DisposalState.PENDING;
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
				final DisposalState priorCondition = (this.containsKey(term)) ? this.get(term) : DisposalState.PENDING;

				DisposalState actualCondition;
				switch (priorCondition) {
				case PENDING:
					final ChannelStatus channelStatus = that.checkChannel(term);
					switch (channelStatus) {
					case READY:
						actualCondition = serializer.act(that,literal.encoding,term);
						break;
					default:
						actualCondition = channelStatus.inferDisposal();	
					}
					this.put(term, actualCondition);
					logger.trace("attempting message over {}", term);
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
	public DisposalTotalState aggregate() {
		if (this.total) return DisposalTotalState.COMPLETE;

		int aggregated = 0x0000;
		for (final Entry<String,DisposalState> entry : this.stateMap.entrySet()) {
			aggregated |= entry.getValue().o;
		}
		if (0 < (aggregated & (DisposalState.REJECTED.o | DisposalState.BUSY.o) ))
			return DisposalTotalState.INCOMPLETE;
		if (0 < (aggregated & (DisposalState.PENDING.o | DisposalState.NEW.o) ))
			return DisposalTotalState.DISTRIBUTE;
		if (0 < (aggregated & (DisposalState.SENT.o | DisposalState.TOLD.o | DisposalState.DELIVERED.o) ))
			return DisposalTotalState.COMPLETE;
		if (0 < (aggregated & (DisposalState.BAD.o) ))
			return DisposalTotalState.FAILED;
		return DisposalTotalState.DISTRIBUTE;
	}

	private String type;
	public DistributorState setType(String type) {
		this.type = type;
		return this;
	}

}

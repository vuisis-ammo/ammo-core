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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.NetworkManager;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelStatus;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalTotalState;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Routing;

/**
 * The dispersal vector is related to the distribution policy and a particular
 * request. The particular request will have a topic which maps to the
 * distribution policy. When all of the clauses of a topic's policy have been
 * satisfied the total is true, otherwise it is false.
 */
public class Dispersal {
    public static final Logger logger = LoggerFactory.getLogger("dist.state");

    private final Map<String, DisposalState> stateMap;
    private boolean total;
    public final Routing policy;
    public final String channelFilter;

    private Dispersal(Routing policy, final String channelFilter) {
        this.stateMap = new HashMap<String, DisposalState>();
        this.total = false;
        this.policy = policy;
        this.channelFilter = channelFilter;
    }

    static public Dispersal newInstance(Routing routing, final String channelFilter) {
        return new Dispersal(routing, channelFilter);
    }

    public boolean total() {
        return this.total;
    }

    public Dispersal total(boolean state) {
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

    public Dispersal and(boolean clause) {
        this.total &= clause;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder()
                .append("type: ").append(this.type).append(" ")
                .append("status:");
        for (final Map.Entry<String, DisposalState> entry : this.stateMap.entrySet()) {
            sb.append('\n').append(entry.getKey()).append(" : ").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Attempt to satisfy the distribution policy for this message's topic. The
     * policies are processed in "short circuit" disjunctive normal form. Each
     * clause is evaluated there results are 'anded' together, hence
     * disjunction. Within a clause each literal is handled in order until one
     * matches its prescribed condition, effecting a short circuit conjunction.
     * (It is short circuit as only the first literal to be true is evaluated.)
     * In order for a topic to evaluate to success all of its clauses must
     * evaluate true. In order for a clause to be true at least (exactly) one of
     * its literals must succeed. A literal is true if the condition of the term
     * matches the 'condition' attribute for the term.
     * 
     * @see scripts/tests/distribution_policy.xml for an example. Given the
     *      presence of a channel filter... - skip any attempt at delivery
     *      unless the forced channel matches the term - mark the clause true if
     *      it doesn't contain the forced channel
     */
    public Dispersal multiplexRequest(final NetworkManager that, final RequestSerializer serializer) {
        logger.trace("::multiplex request");

        if (this.policy == null) {
            logger.error("no matching routing topic");

            final ChannelStatus channelStatus = that.checkChannel(DistributorPolicy.DEFAULT);
            final DisposalState actualCondition;
            switch (channelStatus) {
                case READY:
                    actualCondition = serializer.act(that, Encoding.DEFAULT,
                            DistributorPolicy.DEFAULT);
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

            // evaluate clause status based on previous attempts
            boolean haschannelFilter = false;
            for (DistributorPolicy.Literal literal : clause.literals) {
                final String term = literal.term;
                if (this.channelFilter != null && !term.equals(this.channelFilter)) {
                    haschannelFilter = true;
                }
                final boolean goalCondition = literal.condition;
                final DisposalState priorCondition = (this.containsKey(term)) ? this.get(term)
                        : DisposalState.PENDING;
                logger.debug("prior {} {} {}", new Object[] {
                        term, priorCondition, goalCondition
                });
                if (priorCondition.goalReached(goalCondition)) {
                    clauseSuccess = true;
                    logger.trace("clause previously satisfied {} {}", this, clause);
                    break;
                }
            }
            if (this.channelFilter != null && !haschannelFilter) {
                logger.info(
                        "clause is practically successful due to forced channel=[{}], clause type=[{}]",
                        this.channelFilter, this.type);
                continue;
            }
            if (clauseSuccess)
                continue;

            // evaluate clause based on
            for (DistributorPolicy.Literal literal : clause.literals) {
                final String term = literal.term;
                if (this.channelFilter != null && term.equals(this.channelFilter))
                    continue;

                final boolean goalCondition = literal.condition;
                final DisposalState priorCondition = (this.containsKey(term)) ? this.get(term)
                        : DisposalState.PENDING;

                final DisposalState actualCondition;
                switch (priorCondition) {
                    case PENDING:
                        final ChannelStatus channelStatus = that.checkChannel(term);
                        switch (channelStatus) {
                            case READY:
                                actualCondition = serializer.act(that, literal.encoding, term);
                                break;
                            case EMPTY:
                            case DOWN:
                            case FULL:
                            default:
                                actualCondition = channelStatus.inferDisposal();
                                break;
                        }
                        this.put(term, actualCondition);
                        logger.trace("attempting message over [{}] condition [{}]", 
                                term, actualCondition);
                        break;
                    default:
                        actualCondition = priorCondition;
                }
                if (actualCondition == null) {
                    logger.error("actual condition indeterminate, term={}", term);
                } else if (actualCondition.goalReached(goalCondition)) {
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
     * 
     * @return
     */
    public DisposalTotalState aggregate() {
        if (this.total)
            return DisposalTotalState.COMPLETE;

        int aggregated = 0x0000;
        for (final Entry<String, DisposalState> entry : this.stateMap.entrySet()) {
            aggregated |= entry.getValue().o;
        }
        if (0 < (aggregated & (DisposalState.REJECTED.o | DisposalState.BUSY.o)))
            return DisposalTotalState.INCOMPLETE;
        if (0 < (aggregated & (DisposalState.PENDING.o | DisposalState.NEW.o)))
            return DisposalTotalState.DISTRIBUTE;
        if (0 < (aggregated & (DisposalState.SENT.o | DisposalState.TOLD.o | DisposalState.DELIVERED.o)))
            return DisposalTotalState.COMPLETE;
        if (0 < (aggregated & (DisposalState.BAD.o)))
            return DisposalTotalState.FAILED;
        return DisposalTotalState.DISTRIBUTE;
    }

    private String type;

    public Dispersal setType(String type) {
        this.type = type;
        return this;
    }

}

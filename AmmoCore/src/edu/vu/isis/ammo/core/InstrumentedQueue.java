package edu.vu.isis.ammo.core;

/**
 * This class is to be used in conjunction with Blocking Queues.
 *
 *
 */
public interface InstrumentedQueue {
    /**
     * What percentage of the time is the input the bottleneck.
     */
    public int inputBottleneck();
    /**
     * What percentage of the time is the output the bottleneck.
     */
    public int outputBottleneck();


}

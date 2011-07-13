package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  A set of functional loggers.
 *  These are used to trace functional threads.
 *
 */
public abstract class FLogger {
    /**
     * This logger traces retrieval messages.
     * It includes following through to the distributor, into the
     * network service, through the channel to the gateway.
     */
    public static final Logger request = LoggerFactory.getLogger(FLogger.class,"request");

    /**
     * This logger tracks the retrieved content, and its deserialization
     * into a content provider.
     */
    public static final Logger response = LoggerFactory.getLogger(FLogger.class,"response");

    /**
     * When a postal request is made the logger traces its execution.
     * This includes following its progress through the distributor,
     * into the network service, through the channel to the gateway.
     * This working in conjunction with subscribe.
     */
    public static final Logger postal = LoggerFactory.getLogger(FLogger.class,"postal");

    /**
     * This logger traces subscribe messages.
     * It includes following through to the distributor, into the
     * network service, through the channel to the gateway.
     * It also logs the postal content, and its deserialization
     * into a content provider.
     */
    public static final Logger subscribe = LoggerFactory.getLogger(FLogger.class,"subscribe");

    /**
     * This logger traces retrieval messages.
     * It includes following through to the distributor, into the
     * network service, through the channel to the gateway.
     * It also logs the retrieved content, and its deserialization
     * into a content provider.
     */
    public static final Logger retrieval = LoggerFactory.getLogger(FLogger.class,"retrieval");

}

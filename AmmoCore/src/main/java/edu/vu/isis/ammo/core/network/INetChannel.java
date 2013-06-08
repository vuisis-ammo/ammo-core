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

package edu.vu.isis.ammo.core.network;

import android.content.Context;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;

/**
 * The NetChannel is some mechanism for establishing a network connection over
 * which requests will be sent to "the cloud". Typically this will be a socket.
 */

public interface INetChannel {
    
    public enum State {
        PENDING, // the work is pending
        EXCEPTION, // the run failed by some unhandled exception
    
        CONNECTING, // trying to connect
        CONNECTED, // the socket is good an active
        BUSY, // the socket is busy and no new work should be queued
        READY, // the socket can now take additional requests
    
        DISCONNECTED, // the socket is disconnected
        STALE, // indicating there is a message
        LINK_WAIT, // indicating the underlying link is down
        LINK_ACTIVE, // indicating the underlying link is down -- unused
        DISABLED, // indicating the link is disabled
    
        WAIT_CONNECT, // waiting for connection
        SENDING, // indicating the next thing is the size
        TAKING, // indicating the next thing is the size
        INTERRUPTED, // the run was canceled via an interrupt
    
        SHUTDOWN, // the run is being stopped -- unused
        START, // indicating the next thing is the size
        RESTART, // indicating the next thing is the size
        WAIT_RECONNECT, // waiting for connection
        STARTED, // indicating there is a message
        SIZED, // indicating the next thing is a checksum
        CHECKED, // indicating the bytes are being read
        DELIVER, // indicating the message has been read
        
        State(int colorRes, int textRes) {
            
        }
    }

    void reset();

    boolean isConnected();

    void enable();

    void disable();

    boolean isBusy();

    /**
     * The method to post things to the channel input queue.
     * 
     * @param req
     * @return
     */
    DisposalState sendRequest(AmmoGatewayMessage agm);

    // String getLocalIpAddress();

    /**
     * This method indicates that when the channel connects it is not yet
     * active, ChannelChange.ACTIVATE, until the authentication is complete. In
     * the default case channels are non-authenticating.
     * 
     * @return false if non-authenticating, true for authenticating.
     */
    boolean isAuthenticatingChannel();

    /**
     * Initialize - load configuration files - set context
     * 
     * @param context
     */
    void init(Context context);

    /**
     * Cause object state to be written to the logger.
     */
    void toLog(String context);

    /**
     * Returns channels send and receive status
     */
    String getSendReceiveStats();
    String getSendBitStats();
    String getReceiveBitStats();

    public void linkUp(String name);

    public void linkDown(String name);
}

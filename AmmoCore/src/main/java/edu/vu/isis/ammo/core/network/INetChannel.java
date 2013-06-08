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
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;

/**
 * The NetChannel is some mechanism for establishing a network connection over
 * which requests will be sent to "the cloud". Typically this will be a socket.
 */

public interface INetChannel {
    
    public enum State {
        PENDING(R.color.status_pending, R.string.status_pending), // the work is pending
        EXCEPTION(R.color.status_exception, R.string.status_exception), // the run failed by some unhandled exception
    
        CONNECTING(R.color.status_connecting, R.string.status_connecting), // trying to connect
        CONNECTED(R.color.status_transmitting, R.string.status_transmitting), // the socket is good an active
        BUSY(R.color.status_busy, R.string.status_busy), // the socket is busy and no new work should be queued
        READY(R.color.status_ready, R.string.status_ready), // the socket can now take additional requests
    
        DISCONNECTED(R.color.status_disconnected, R.string.status_disconnected), // the socket is disconnected
        STALE(R.color.status_stale, R.string.status_stale), // indicating there is a message
        LINK_WAIT(R.color.status_link_wait, R.string.status_link_wait), // indicating the underlying link is down
        LINK_ACTIVE(R.color.status_link_active, R.string.status_link_active), // indicating the underlying link is down -- unused
        DISABLED(R.color.status_disabled, R.string.status_disabled), // indicating the link is disabled
    
        WAIT_CONNECT(R.color.status_waiting_conn, R.string.status_waiting), // waiting for connection
        SENDING(R.color.status_sending, R.string.status_sending), // indicating the next thing is the size
        TAKING(R.color.status_taking, R.string.status_taking), // indicating the next thing is the size
        INTERRUPTED(R.color.status_interrupted, R.string.status_interrupted), // the run was canceled via an interrupt
    
        SHUTDOWN(R.color.status_shutdown, R.string.status_shutdown), // the run is being stopped -- unused
        START(R.color.status_start, R.string.status_start), // indicating the next thing is the size
        RESTART(R.color.status_start, R.string.status_start), // indicating the next thing is the size
        WAIT_RECONNECT(R.color.status_waiting_conn, R.string.status_waiting), // waiting for connection
        STARTED(R.color.status_started, R.string.status_started), // indicating there is a message
        SIZED(R.color.status_sized, R.string.status_sized), // indicating the next thing is a checksum
        CHECKED(R.color.status_checked, R.string.status_checked), // indicating the bytes are being read
        DELIVER(R.color.status_deliver, R.string.status_deliver); // indicating the message has been read
        
        private final int mColorRes;
        private final int mTextRes;
        
        State(int colorRes, int textRes) {
            mColorRes = colorRes;
            mTextRes = textRes;
        }
        
        public int getColorRes() {
            return mColorRes;
        }
        
        public int getTextRes() {
            return mTextRes;
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

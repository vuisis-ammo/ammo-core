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
import edu.vu.isis.ammo.core.internal.DisposalState;

/**
 * The NetChannel is some mechanism for establishing a network connection over
 * which requests will be sent to "the cloud". Typically this will be a socket.
 */

public interface INetChannel {

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

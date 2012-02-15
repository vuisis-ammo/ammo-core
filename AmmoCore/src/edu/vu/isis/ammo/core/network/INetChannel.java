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

import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;

/**
 * The NetChannel is some mechanism for establishing a network connection
 * over which requests will be sent to "the cloud".
 * Typically this will be a socket.
 *
 */

public interface INetChannel {
    int PENDING         =  0; // the run failed by some unhandled exception
    int EXCEPTION       =  1; // the run failed by some unhandled exception

    int CONNECTING      = 20; // trying to connect
    int CONNECTED       = 21; // the socket is good an active


    int DISCONNECTED    = 30; // the socket is disconnected
    int STALE           = 31; // indicating there is a message
    int LINK_WAIT       = 32; // indicating the underlying link is down
    int LINK_ACTIVE     = 33; // indicating the underlying link is down -- unused
    int DISABLED		= 34; // indicating the link is disabled


    int WAIT_CONNECT    = 40; // waiting for connection
    int SENDING         = 41; // indicating the next thing is the size
    int TAKING          = 42; // indicating the next thing is the size
    int INTERRUPTED     = 43; // the run was canceled via an interrupt

    int SHUTDOWN        = 51; // the run is being stopped -- unused
    int START           = 52; // indicating the next thing is the size
    int RESTART         = 53; // indicating the next thing is the size
    int WAIT_RECONNECT  = 54; // waiting for connection
    int STARTED         = 55; // indicating there is a message
    int SIZED           = 56; // indicating the next thing is a checksum
    int CHECKED         = 57; // indicating the bytes are being read
    int DELIVER         = 58; // indicating the message has been read

    String showState(int state);

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
    DisposalState sendRequest( AmmoGatewayMessage agm );
    //String getLocalIpAddress();


    /**
     * This method indicates that when the channel connects it is not
     * yet active, ChannelChange.ACTIVATE, until the authentication is complete.
     * In the default case channels are non-authenticating.
     * 
     * @return false if non-authenticating, true for authenticating.
     */
	 boolean isAuthenticatingChannel();
}

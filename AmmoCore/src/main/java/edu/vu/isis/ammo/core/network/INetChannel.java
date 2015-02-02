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


package edu.vu.isis.ammo.core.network;

import android.content.Context;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;

/**
 * The NetChannel is some mechanism for establishing a network connection over
 * which requests will be sent to "the cloud". Typically this will be a socket.
 */

public interface INetChannel {
    int PENDING = 0; // the work is pending
    int EXCEPTION = 1; // the run failed by some unhandled exception

    int CONNECTING = 20; // trying to connect
    int CONNECTED = 21; // the socket is good an active
    int BUSY = 22; // the socket is busy and no new work should be queued
    int READY = 23; // the socket can now take additional requests

    int DISCONNECTED = 30; // the socket is disconnected
    int STALE = 31; // indicating there is a message
    int LINK_WAIT = 32; // indicating the underlying link is down
    int LINK_ACTIVE = 33; // indicating the underlying link is down -- unused
    int DISABLED = 34; // indicating the link is disabled

    int WAIT_CONNECT = 40; // waiting for connection
    int SENDING = 41; // indicating the next thing is the size
    int TAKING = 42; // indicating the next thing is the size
    int INTERRUPTED = 43; // the run was canceled via an interrupt

    int SHUTDOWN = 51; // the run is being stopped -- unused
    int START = 52; // indicating the next thing is the size
    int RESTART = 53; // indicating the next thing is the size
    int WAIT_RECONNECT = 54; // waiting for connection
    int STARTED = 55; // indicating there is a message
    int SIZED = 56; // indicating the next thing is a checksum
    int CHECKED = 57; // indicating the bytes are being read
    int DELIVER = 58; // indicating the message has been read

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

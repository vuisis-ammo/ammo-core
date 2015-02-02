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

///////////////////////////////////////////////////////////////////////////////
//
// IChannelManager.java
//

package edu.vu.isis.ammo.core.network;

import edu.vu.isis.ammo.core.pb.AmmoMessages;


/**
 * Used by channel classes to interact with the AmmoService.
 */
public interface IChannelManager
{
   /**
     * Used to acquire the session id by which subsequent communication will be
     * tracked.
     *
     * @param void
     *
     * @return boolean
     */
    boolean auth();


    /**
     * @param message
     * @param payload_checksum
     *
     * @return boolean
     */
    boolean deliver( AmmoGatewayMessage message );


    /**
     * @param channel
     * @param connStatus
     * @param sendStatus
     * @param recvStatus
     *
     * @return boolean
     */
    void statusChange( NetChannel channel,
    		int lastConnStatus, int connStatus,
    		int lastSendStatus, int sendStatus,
    		int lastRecvStatus, int recvStatus );

    /**
     * @param void
     *
     * @return boolean
     */
    boolean isAnyLinkUp();

    void authorizationSucceeded( NetChannel channel, AmmoGatewayMessage agm );

    // FIXME: this is a temporary hack to get authentication working again,
    // until Nilabja's new code is implemented.  Remove this afterward, and
    // make the AmmoService's method private again (if it doesn't go away).
    public AmmoMessages.MessageWrapper.Builder buildAuthenticationRequest();
    
    public String getOperatorId();

}

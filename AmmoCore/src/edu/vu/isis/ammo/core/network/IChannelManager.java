/*
Copyright(c) 2010-2012

This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under contract [contract citation, subcontract and prime contract]. 
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.

 */

///////////////////////////////////////////////////////////////////////////////
//
// IChannelManager.java
//

package edu.vu.isis.ammo.core.network;

import edu.vu.isis.ammo.core.pb.AmmoMessages;


/**
 * Used by channel classes to interact with the NetworkService.
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
                       int connStatus,
                       int sendStatus,
                       int recvStatus );

    /**
     * @param void
     *
     * @return boolean
     */
    boolean isAnyLinkUp();

    void authorizationSucceeded( NetChannel channel, AmmoGatewayMessage agm );

    // FIXME: this is a temporary hack to get authentication working again,
    // until Nilabja's new code is implemented.  Remove this afterward, and
    // make the NetworkService's method private again (if it doesn't go away).
    public AmmoMessages.MessageWrapper.Builder buildAuthenticationRequest();

}

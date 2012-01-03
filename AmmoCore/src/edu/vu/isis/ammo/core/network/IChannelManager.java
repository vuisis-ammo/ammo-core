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

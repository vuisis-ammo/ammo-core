///////////////////////////////////////////////////////////////////////////////
//
// IChannelManager.java
//

package edu.vu.isis.ammo.core.network;


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
    void statusChange( INetChannel channel,
                       int connStatus,
                       int sendStatus,
                       int recvStatus );

    /**
     * @param void
     *
     * @return boolean
     */
    boolean isAnyLinkUp();
}

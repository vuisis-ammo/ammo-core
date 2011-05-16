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
    public boolean auth();


    /**
     * @param message
     * @param checksum
     *
     * @return boolean
     */
    public boolean deliver( byte[] message,
                            long checksum );


    /**
     * @param channel
     * @param connStatus
     * @param sendStatus
     * @param recvStatus
     *
     * @return boolean
     */
    public boolean statusChange( INetChannel channel,
                                 int connStatus,
                                 int sendStatus,
                                 int recvStatus);


    /**
     * @param void
     *
     * @return boolean
     */
    public boolean postToQueue();


    /**
     * @param void
     *
     * @return boolean
     */
    public boolean isAnyLinkUp();
}

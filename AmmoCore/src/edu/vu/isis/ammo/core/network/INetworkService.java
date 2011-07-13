/**
 * Defines the methods and intent actions to be used by client classes.
 * These can be done via a binder if necessary.
 */
package edu.vu.isis.ammo.core.network;

import java.util.List;

import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.Netlink;

/**
 * This interface is used by the Distributor and others to interact with the
 * NetworkService. Classes like TcpChannel that are created and managed by
 * the NetworkService should use the IChannelManager interface instead.
 */
public interface INetworkService {

    // Intent action constants
    String PREPARE_FOR_STOP = "edu.vu.isis.ammo.core.network.NetworkService.PREPARE_FOR_STOP";
    String UPDATE_IP = "edu.vu.isis.ammo.core.network.NetworkService.UPDATE_IP";
    String ACTION = "edu.vu.isis.ammo.core.network.NetworkService.ACTION";
    String ACTION_RECONNECT = "edu.vu.isis.ammo.core.network.NetworkService.RUN_STATUS_BEGIN_ACTION";
    String ACTION_DISCONNECT = "edu.vu.isis.ammo.core.network.NetworkService.RUN_STATUS_HALT_ACTION";

    /**
     * An interface which takes a status indicating success (true) or failure (false).
     * In the sending of the message.
     */
    interface OnSendHandler {
        boolean ack(boolean status);
    }
    interface DeliveryHandler {
        public boolean deliver(byte[] message, long checksum);
    }

    // methods
    void teardown();
    boolean isConnected();

    List<Gateway> getGatewayList();
    List<Netlink> getNetlinkList();
    
    /**
     * Pass control to the distributor service to handle the message.
     * The network service proxy is responsible for receiving messages from
     * multiple channels but not for processing them.
     *
     * The only processing is to determine which type of message has been
     * received and passing it to the distributor service.
     *
     * @param callback
     */
    boolean sendRequest(NetworkService.Request distributorMessage);
    public boolean deliver(byte[] message, long checksum);
    void setCallback(DeliveryHandler handler);
    
}

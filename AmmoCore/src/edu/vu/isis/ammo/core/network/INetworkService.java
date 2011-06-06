/**
 * Defines the methods and intent actions to be used by client classes.
 * These can be done via a binder if necessary.
 */
package edu.vu.isis.ammo.core.network;

import edu.vu.isis.ammo.core.distributor.IDistributorService;


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

    // Callback interfaces
    interface OnSendMessageHandler {
        boolean ack(boolean status);
    }

    // methods
    void teardown();
    boolean isConnected();

    /**
     * Posting a data item to the gateway for distribution.
     *
     * @param uri
     * @param mimeType
     * @param data
     * @return
     */
    boolean dispatchPushRequest(String uri, String mimeType, byte []data, OnSendMessageHandler handler);

    /**
     * Enrolling with the gateway for a data stream.
     *
     * @param requestId the uri from the subscription table tuple
     * @param mimeType the identifier used but the gateway to find what is wanted
     * @param selection the selection criteria for items of the specified mimeType
     *
     * @return was the request posted successfully
     */
    boolean dispatchRetrievalRequest(String requestId, String mimeType, String selection, OnSendMessageHandler handler);

    /**
     * Subscribe with the gateway for a data stream.
     *
     * @param mimeType the identifier used but the gateway to find what is wanted
     * @param selection the selection criteria for items of the specified mimeType
     *
     * @return was the request posted successfully
     */
    boolean dispatchSubscribeRequest(String mimeType, String selection, OnSendMessageHandler handler);

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
    void setDistributorServiceCallback(IDistributorService callback);
}

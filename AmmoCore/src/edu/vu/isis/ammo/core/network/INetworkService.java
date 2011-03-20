/**
 * Defines the methods and intent actions to be used by client classes.
 * These can be done via a binder if necessary.
 */
package edu.vu.isis.ammo.core.network;

import edu.vu.isis.ammo.core.distributor.IDistributorService;

public interface INetworkService {

	// Intent action constants
	public static final String PREPARE_FOR_STOP = "edu.vu.isis.ammo.core.network.NetworkService.PREPARE_FOR_STOP";
	public static final String UPDATE_IP = "edu.vu.isis.ammo.core.network.NetworkService.UPDATE_IP";
	public static final String ACTION = "edu.vu.isis.ammo.core.network.NetworkService.ACTION";
	
	public static final String ACTION_RECONNECT = "edu.vu.isis.ammo.core.network.NetworkService.RUN_STATUS_BEGIN_ACTION";
	public static final String ACTION_DISCONNECT = "edu.vu.isis.ammo.core.network.NetworkService.RUN_STATUS_HALT_ACTION";

	// Callback interfaces
	public static interface OnConnectHandler {
		public boolean auth();
	}
	public static interface OnSendMessageHandler {
		public boolean ack(boolean status);
	}
	public static interface OnReceiveMessageHandler {
		public boolean deliver(byte[] message, long checksum);
	}
	
	public static interface OnStatusChangeHandler {
		public boolean statusChange(INetChannel channel, int connStatus, int sendStatus, int recvStatus);
	}
	
	// methods
	public void teardown();
	
	public boolean isConnected();
	/**
	 * Used to acquire the session id by which subsequent communication will be
	 * tracked.
	 */
	public boolean auth();

	/**
	 * Posting a data item to the gateway for distribution.
	 * 
	 * @param uri
	 * @param mimeType
	 * @param data
	 * @return
	 */
	public boolean dispatchPushRequest(String uri, String mimeType, byte []data, OnSendMessageHandler handler);
	
	/**
	 * Enrolling with the gateway for a data stream.
	 * 
	 * @param requestId the uri from the subscription table tuple
	 * @param mimeType the identifier used but the gateway to find what is wanted
	 * @param selection the selection criteria for items of the specified mimeType
	 * 
	 * @return was the request posted successfully
	 */
	public boolean dispatchRetrievalRequest(String requestId, String mimeType, String selection, OnSendMessageHandler handler);
	
	/**
	 * Subscribe with the gateway for a data stream.
	 * 
	 * @param mimeType the identifier used but the gateway to find what is wanted
	 * @param selection the selection criteria for items of the specified mimeType
	 * 
	 * @return was the request posted successfully
	 */
	public boolean dispatchSubscribeRequest(String mimeType, String selection, OnSendMessageHandler handler);
	
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
	public void setDistributorServiceCallback(IDistributorService callback) ;
	
	
}

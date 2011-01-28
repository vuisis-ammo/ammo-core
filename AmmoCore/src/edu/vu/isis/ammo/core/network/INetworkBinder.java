package edu.vu.isis.ammo.core.network;

import android.os.IBinder;
import edu.vu.isis.ammo.core.distributor.IDistributorService;

public interface INetworkBinder extends IBinder {

	public static final String PREPARE_FOR_STOP = "edu.vu.isis.ammo.core.network.NetworkProxyService.PREPARE_FOR_STOP";
	public static final String UPDATE_IP = "edu.vu.isis.ammo.core.network.NetworkProxyService.UPDATE_IP";
	public static final String ACTION = "edu.vu.isis.ammo.core.network.NetworkProxyService.ACTION";
	
	public static final String ACTION_RECONNECT = "edu.vu.isis.ammo.core.network.NetworkProxyService.RUN_STATUS_BEGIN_ACTION";
	public static final String ACTION_DISCONNECT = "edu.vu.isis.ammo.core.network.NetworkProxyService.RUN_STATUS_HALT_ACTION";

	public void teardown();
	
	public boolean isConnected();
	/**
	 * Used to acquire the session id by which subsequent communication will be
	 * tracked.
	 */
	public boolean authenticateGatewayConnection();

	/**
	 * Posting a data item to the gateway for distribution.
	 * 
	 * @param uri
	 * @param mimeType
	 * @param data
	 * @return
	 */
	public boolean dispatchPushRequestToGateway(String uri, String mimeType, byte []data);
	
	/**
	 * Enrolling with the gateway for a data stream.
	 * 
	 * @param requestId the uri from the subscription table tuple
	 * @param mimeType the identifier used but the gateway to find what is wanted
	 * @param selection the selection criteria for items of the specified mimeType
	 * 
	 * @return was the request posted successfully
	 */
	public boolean dispatchPullRequestToGateway(String requestId, String mimeType, String selection);
	
	/**
	 * Subscribe with the gateway for a data stream.
	 * 
	 * @param mimeType the identifier used but the gateway to find what is wanted
	 * @param selection the selection criteria for items of the specified mimeType
	 * 
	 * @return was the request posted successfully
	 */
	public boolean dispatchSubscribeRequestToGateway(String mimeType, String selection);
	
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

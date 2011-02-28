/**
 * 
 */
package edu.vu.isis.ammo.core.network;

import edu.vu.isis.ammo.core.distributor.IDistributorService;
import android.os.Binder;


/**
 * This implements the INetworkBinder interface.
 * Which interface is used by the Distributor service.
 * 
 * @author feisele
 *
 */

public class NetworkBinder extends Binder implements INetworkService, android.os.IBinder
{		
	final NetworkService netsvc;
	
	private NetworkBinder(NetworkService netsvc) {
		this.netsvc = netsvc;
	}
	
	static public NetworkBinder getInstance(NetworkService netsvc) {
		return new NetworkBinder(netsvc);
	}
	
	/**
	 *  We should expect immediately after this call is made 
	 *  that the caller will unbind from the service. 
	 */
	@Override
	public void teardown() {
		netsvc.teardown();
	}
	
	/**
	 * Before the Distributor begins processing a message, it asks
	 * for the connection to be prepared (if not already). Returns the result of preparing 
	 * connection.
	 */
	/*
	public NPSReturnCode prepareConnection() {
		NPSReturnCode retCode = NPSReturnCode.OK;
		if (tcpSocket == null || !tcpSocket.isConnected()) {
			Log.d(TAG, "Establishing network connection...");
			retCode = this.authenticateGatewayConnection();
			
		}
		
		return retCode;
	}
	*/
	
	// =========================================================== 
	// Overrides
	// ===========================================================

	@Override
	public boolean isConnected() {
		return netsvc.isConnected();
	}
	
	/**
	 * Setup a TCP connection to the gateway...
	 * ...to acquire the session id by which subsequent communication will be tracked.
	 */
	@Override
	public boolean authenticateGatewayConnection() {
		return netsvc.authenticateGatewayConnection();
	}
	
	@Override
	public boolean dispatchPushRequestToGateway(String uri, String mimeType, byte []data) {
        return netsvc.dispatchPushRequestToGateway(uri, mimeType, data);
	}
	
	@Override
	public boolean dispatchRetrievalRequestToGateway(String subscriptionId, String mimeType, String selection) {
		return netsvc.dispatchRetrievalRequestToGateway(subscriptionId, mimeType, selection);
	}

	@Override
	public boolean dispatchSubscribeRequestToGateway(String mimeType, String selection) {
		return netsvc.dispatchSubscribeRequestToGateway(mimeType, selection);
	}
	
	@Override
	public void setDistributorServiceCallback(IDistributorService callback) {
		netsvc.setDistributorServiceCallback(callback);
	}
	
}

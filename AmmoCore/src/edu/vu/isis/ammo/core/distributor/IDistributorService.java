package edu.vu.isis.ammo.core.distributor;

import edu.vu.isis.ammo.core.pb.AmmoMessages.DataMessage;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PullResponse;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;


/**
 * This interface declares different callback methods that will be used by 
 * different services within the Core Application. 
 * @author Demetri Miller
 *
 */
public interface IDistributorService {
	
	public static final String LAUNCH = "edu.vu.isis.ammo.core.distributor.DistributorService.LAUNCH";
	public static final String BIND = "edu.vu.isis.ammo.core.distributor.DistributorService.BIND";
	public static final String PREPARE_FOR_STOP = "edu.vu.isis.ammo.core.distributor.DistributorService.PREPARE_FOR_STOP";
	public static final String SEND_SERIALIZED = "edu.vu.isis.ammo.core.distributor.DistributorService.SEND_SERIALIZED";
   
	/** Content Observer callbacks */
	public void repostToNetworkService();
	public void repostToNetworkService2(); // same as repostToNetworkService but not threaded
	
	public void processPostalChange(boolean repost);
	public void processRetrievalChange(boolean repost);
	public void processSubscriptionChange(boolean repost);
	public void processPublicationChange(boolean repost);
	
	/** Network Proxy Service callbacks */
	public void finishTeardown();
	public boolean dispatchPushResponse(PushAcknowledgement resp);
	public boolean dispatchRetrievalResponse(PullResponse resp);
	public boolean dispatchSubscribeResponse(DataMessage resp);
	/**
	 * There is no response to a publish unless there is a subscription?
	 */
	// public boolean dispatchPublishResponse(PushAcknowledgement resp);
	
	
}

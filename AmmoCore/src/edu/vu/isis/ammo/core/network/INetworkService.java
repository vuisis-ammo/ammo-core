/**
 * Defines the methods and intent actions to be used by client classes.
 * These can be done via a binder if necessary.
 */
package edu.vu.isis.ammo.core.network;

import java.util.List;

import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy;
import edu.vu.isis.ammo.core.model.Channel;
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

    // Callback interfaces
    interface OnSendMessageHandler {
    	boolean ack(String channel, DisposalState status);
    }

    // methods
    void teardown();
    boolean isConnected();

    public DisposalState sendRequest(AmmoGatewayMessage agm, String channel, DistributorPolicy.Topic topic );

    List<Channel> getGatewayList();
    List<Netlink> getNetlinkList();
}

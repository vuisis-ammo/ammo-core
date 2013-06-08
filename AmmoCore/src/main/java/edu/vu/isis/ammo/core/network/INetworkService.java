/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.core.network;

import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;


/**
 * This interface is used by the Distributor and others to interact with the
 * AmmoService. Classes like TcpChannel that are created and managed by
 * the AmmoService should use the IChannelManager interface instead.
 */
public interface INetworkService {

    // Intent action constants
    public static final String PREPARE_FOR_STOP = "edu.vu.isis.ammo.core.network.NetworkService.PREPARE_FOR_STOP";
    public static final String UPDATE_IP = "edu.vu.isis.ammo.core.network.NetworkService.UPDATE_IP";
    public static final String ACTION = "edu.vu.isis.ammo.core.network.NetworkService.ACTION";
    public static final String ACTION_RECONNECT = "edu.vu.isis.ammo.core.network.NetworkService.RUN_STATUS_BEGIN_ACTION";
    public static final String ACTION_DISCONNECT = "edu.vu.isis.ammo.core.network.NetworkService.RUN_STATUS_HALT_ACTION";

    // Callback interfaces
    public interface OnSendMessageHandler {
    	boolean ack(String channel, DisposalState status);
    }

    // methods
    public void teardown();
    public boolean isConnected();
    
    // Channel accessors
    public TcpChannel getTcpChannel();
    public TcpChannel getTcpMedialChannel();
    public MulticastChannel getMulticastChannel();
    public ReliableMulticastChannel getReliableMulticastChannel();
    public JournalChannel getJournalChannel();
    public SerialChannel getSerialChannel();
    
    public DisposalState sendRequest(AmmoGatewayMessage agm, String channel);

}

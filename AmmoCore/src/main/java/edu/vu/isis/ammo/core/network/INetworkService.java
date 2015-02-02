/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.vu.isis.ammo.core.network;

import java.util.List;

import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.model.ModelChannel;
import edu.vu.isis.ammo.core.model.Netlink;


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

    public DisposalState sendRequest(AmmoGatewayMessage agm, String channel);

    public List<ModelChannel> getGatewayList();
    public List<Netlink> getNetlinkList();
}

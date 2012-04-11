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
// TcpSecurityObject.java

package edu.vu.isis.ammo.core.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelDisposal;
import edu.vu.isis.ammo.core.pb.AmmoMessages;



public class TcpSecurityObject implements ISecurityObject,
                                          INetworkService.OnSendMessageHandler
{
    private static final Logger logger = LoggerFactory.getLogger("class.TcpSecurityObject");

    TcpSecurityObject( TcpChannel iChannel )
    {
        logger.trace( "Constructor of TcpSecurityObject." );
        mChannel = iChannel;
    }


    public void authorize( AmmoMessages.MessageWrapper.Builder mwb  )
    {
        logger.trace( "TcpSecurityObject::authorize()." );

        // This code is a hack to have authentication work before Nilabja's new
        // code is ready.
        AmmoGatewayMessage.Builder agmb = AmmoGatewayMessage.newBuilder( mwb, this );
        agmb.isGateway();

        mChannel.putFromSecurityObject( agmb.build() );
        mChannel.finishedPuttingFromSecurityObject();
    }


    public boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.trace( "Delivering message to TcpSecurityObject." );

        // For now we haven't implemented any security.  Just
        // authorize if we receive a packet back from the server.
        mChannel.authorizationSucceeded( agm );

        // If authorization fails, call mChannel.authorizationFailed();

        return true;
    }


    public boolean ack( String channel, ChannelDisposal status )
    {
        return true;
    }


    private TcpChannel mChannel;
}

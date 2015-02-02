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

// ReliableMulticastSecurityObject.java

package edu.vu.isis.ammo.core.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.pb.AmmoMessages;


public class ReliableMulticastSecurityObject implements ISecurityObject,
                                                        INetworkService.OnSendMessageHandler
{
    private static final Logger logger = LoggerFactory.getLogger("net.rmcast.security");

    ReliableMulticastSecurityObject( ReliableMulticastChannel iChannel )
    {
        logger.trace( "Constructor of ReliableMulticastSecurityObject." );
        mChannel = iChannel;
    }


    public void authorize( AmmoMessages.MessageWrapper.Builder mwb  )
    {
        logger.trace( "ReliableMulticastSecurityObject::authorize()." );

        // This code is a hack to have authentication work before Nilabja's new
        // code is ready.
        //AmmoGatewayMessage agm = AmmoGatewayMessage.getInstance( mwb, this );

        //mChannel.putFromSecurityObject( agm );
        //mChannel.finishedPuttingFromSecurityObject();
    }


    public boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.trace( "Delivering message to ReliableMulticastSecurityObject." );

        // For now we haven't implemented any security.  Just
        // authorize if we receive a packet back from the server.
        //mChannel.authorizationSucceeded( agm );

        // If authorization fails, call mChannel.authorizationFailed();

        return true;
    }


    public boolean ack( String channel, DisposalState status )
    {
        return true;
    }

    @SuppressWarnings("unused")
	private ReliableMulticastChannel mChannel;
}

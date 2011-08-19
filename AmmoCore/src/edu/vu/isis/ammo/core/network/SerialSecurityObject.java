// SerialSecurityObject.java

package edu.vu.isis.ammo.core.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.pb.AmmoMessages;



public class SerialSecurityObject implements ISecurityObject,
                                             INetworkService.OnSendMessageHandler
{
    private static final Logger logger = LoggerFactory.getLogger( "security.mcast" );

    SerialSecurityObject( SerialChannel iChannel )
    {
        logger.info( "Constructor of SerialSecurityObject." );
        mChannel = iChannel;
    }


    public void authorize( AmmoMessages.MessageWrapper.Builder mwb  )
    {
        logger.info( "SerialSecurityObject::authorize()." );

        // This code is a hack to have authentication work before Nilabja's new
        // code is ready.
        //AmmoGatewayMessage agm = AmmoGatewayMessage.getInstance( mwb, this );

        //mChannel.putFromSecurityObject( agm );
        //mChannel.finishedPuttingFromSecurityObject();
    }


    public boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.info( "Delivering message to SerialSecurityObject." );

        // For now we haven't implemented any security.  Just
        // authorize if we receive a packet back from the server.
        //mChannel.authorizationSucceeded( agm );

        // If authorization fails, call mChannel.authorizationFailed();

        return true;
    }


    public boolean ack( Class<? extends INetChannel> clazz, boolean status )
    {
        return true;
    }


    private SerialChannel mChannel;
}

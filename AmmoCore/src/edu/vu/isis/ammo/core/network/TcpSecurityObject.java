// TcpSecurityObject.java

package edu.vu.isis.ammo.core.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.pb.AmmoMessages;


public class TcpSecurityObject implements ISecurityObject
{
    private static final Logger logger = LoggerFactory.getLogger( TcpSecurityObject.class );

    TcpSecurityObject( TcpChannel iChannel )
    {
        logger.info( "Constructor of TcpSecurityObject." );
        mChannel = iChannel;
    }


    public void authorize()
    {
        logger.info( "TcpSecurityObject::authorize()." );

        AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType( AmmoMessages.MessageWrapper.MessageType.HEARTBEAT );
        mw.setMessagePriority(AmmoGatewayMessage.PriorityLevel.AUTH.v);

        AmmoMessages.Heartbeat.Builder message = AmmoMessages.Heartbeat.newBuilder();
        message.setSequenceNumber( 23 ); // Jesus 23rd birthday
        mw.setHeartbeat( message );

        AmmoGatewayMessage agm = AmmoGatewayMessage.getInstance(mw, null);

        mChannel.putFromSecurityObject( agm );
        mChannel.finishedPuttingFromSecurityObject();
    }


    public boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.info( "Delivering message to TcpSecurityObject." );

        // For now we haven't implemented any security.  Just
        // authorize if we receive a packet back from the server.
        mChannel.authorizationSucceeded();

        // If authorization fails, call mChannel.authorizationFailed();

        return true;
    }


    private TcpChannel mChannel;
}

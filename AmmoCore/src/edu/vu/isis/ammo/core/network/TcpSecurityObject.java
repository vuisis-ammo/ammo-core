// TcpSecurityObject.java

package edu.vu.isis.ammo.core.network;


import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.network.NetworkService.MsgHeader;
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

        // Start the authorization process.
        AmmoMessages.MessageWrapper.Builder mw = AmmoMessages.MessageWrapper.newBuilder();
        mw.setType( AmmoMessages.MessageWrapper.MessageType.HEARTBEAT );

        AmmoMessages.Heartbeat.Builder message = AmmoMessages.Heartbeat.newBuilder();
        message.setSequenceNumber( System.currentTimeMillis() );

        mw.setHeartbeat( message );

        byte[] protocByteBuf = mw.build().toByteArray();
        MsgHeader msgHeader = MsgHeader.getInstance( protocByteBuf, true );

        mChannel.putFromSecurityObject( msgHeader.size,
                                        msgHeader.checksum,
                                        protocByteBuf,
                                        null );
        mChannel.finishedPuttingFromSecurityObject();
    }


    public boolean deliverMessage( byte[] message,
                                   long checksum )
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

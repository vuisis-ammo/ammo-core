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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * We can pass in fewer of these arguments to the constructor if we make this
 * a private class of the SerialChannel.  Wait until we see how big this class
 * will get before we decide.
 */
public class SerialRetransmitter
{
    /**
     *
     */
    public SerialRetransmitter( SerialChannel channel, IChannelManager channelManager )
    {
        logger.trace( "SerialRetransmitter::SerialRetransmitter()" );
        mChannel = channel;
        mChannelManager = channelManager;
    }


    /**
     * All messages received are passed to this method. It delivers the message
     * to the channel manager if it is a new message (rather than one older than
     * the current most recent message of this terse topic for the given slot.
     * It also caches it as appropriate.
     */
    public void processReceivedMessage( AmmoGatewayMessage agm, boolean receiverEnabled )
    {
        logger.trace( "SerialRetransmitter::processReceivedMessage()" );

        // FIXME: Here is where the main receiving functionality will go.


        if ( receiverEnabled ) {
            mChannel.deliverMessage( agm );
        } else {
            logger.trace( "Receiving disabled, discarding message." );
        }
    }


    /**
     * If an AmmoGatewayMessage is returned, it will be sent in by the
     * SenderThread in the current slot.  If nothing will fit in the
     * bytesThatWillFit bytes that are remaining, return null and nothing
     * will be sent.
     */
    public AmmoGatewayMessage createRetransmitPacket( long bytesThatWillFit )
    {
        logger.trace( "SerialRetransmitter::addMessage()" );

        AmmoGatewayMessage agm = null;

        // FIXME: Here is where the packet containing the retransmitted
        // packets will be constructed.

        return agm;
    }


    private SerialChannel mChannel;
    private IChannelManager mChannelManager;
    private static final Logger logger = LoggerFactory.getLogger( "net.serial.retran" );
}

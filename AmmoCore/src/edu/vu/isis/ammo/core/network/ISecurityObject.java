// ISecurityObject.java

package edu.vu.isis.ammo.core.network;

import edu.vu.isis.ammo.core.pb.AmmoMessages;


public interface ISecurityObject
{
    public void authorize( AmmoMessages.MessageWrapper.Builder mwb );
    public boolean deliverMessage( AmmoGatewayMessage message );
}

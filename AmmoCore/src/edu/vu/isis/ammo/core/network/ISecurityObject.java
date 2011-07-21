// ISecurityObject.java

package edu.vu.isis.ammo.core.network;

import edu.vu.isis.ammo.core.pb.AmmoMessages;


public interface ISecurityObject
{
    public void authorize();
    public boolean deliverMessage( AmmoGatewayMessage message );
}

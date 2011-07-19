// ISecurityObject.java

package edu.vu.isis.ammo.core.network;


public interface ISecurityObject
{
    public void authorize();
    public boolean deliverMessage( AmmoGatewayMessage message );
}

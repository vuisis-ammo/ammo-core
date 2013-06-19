package edu.vu.isis.ammo.core.network;

public interface IChannelObserver {

    void notifyUpdate(ReliableMulticastChannel channel);
    void notifyUpdate(MulticastChannel channel);
    void notifyUpdate(TcpChannel channel);
    void notifyUpdate(SerialChannel channel);
    
}

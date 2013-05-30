package edu.vu.isis.ammo.core.network;


public abstract class AddressedChannel extends NetChannel {

    protected String mAddress;
    protected int mPort;
    
    protected AddressedChannel(String name) {
        super(name);
    }
    
    public String getAddress() {
        return mAddress;
    }
    
    public int getPort() {
        return mPort;
    }

}

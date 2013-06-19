package edu.vu.isis.ammo.core.network;


public abstract class AddressedChannel extends NetChannel {

    private String mAddress;
    private int mPort;
    
    protected AddressedChannel(String name) {
        super(name);
    }
    
    public String getAddress() {
        return mAddress;
    }
    
    public int getPort() {
        return mPort;
    }
    
    protected void setAddress(String address) {
        mAddress = address;
        notifyObserver();
    }
    
    protected boolean setPort(int port) {
        if (mPort == port) return false;
        mPort = port;
        notifyObserver();
        return true;
    }

}

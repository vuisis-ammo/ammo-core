package edu.vu.isis.ammo.core.network;

import java.util.zip.CRC32;


public interface INetChannel
{
    int PENDING         =  0; // the run failed by some unhandled exception
    int EXCEPTION       =  1; // the run failed by some unhandled exception

    int CONNECTING      = 20; // trying to connect
    int CONNECTED       = 21; // the socket is good an active

    
    int DISCONNECTED    = 30; // the socket is disconnected
    int STALE           = 31; // indicating there is a message
    int LINK_WAIT       = 32; // indicating the underlying link is down
    int LINK_ACTIVE     = 33; // indicating the underlying link is down -- unused
    int DISABLED		= 34; // indicating the link is disabled
    
    
    int WAIT_CONNECT    = 40; // waiting for connection
    int SENDING         = 41; // indicating the next thing is the size
    int TAKING          = 42; // indicating the next thing is the size
    int INTERRUPTED     = 43; // the run was canceled via an interrupt

    int SHUTDOWN        = 51; // the run is being stopped -- unused
    int START           = 52; // indicating the next thing is the size
    int RESTART         = 53; // indicating the next thing is the size
    int WAIT_RECONNECT  = 54; // waiting for connection
    int STARTED         = 55; // indicating there is a message
    int SIZED           = 56; // indicating the next thing is a checksum
    int CHECKED         = 57; // indicating the bytes are being read
    int DELIVER         = 58; // indicating the message has been read
    
    String showState(int state);

    boolean isConnected();
    boolean isEnabled();
    boolean enable();
    boolean disable(); // From TcpChannel
    boolean close(); // From JournalChannel
    boolean setConnectTimeout(int value);
    boolean setSocketTimeout(int value);
    void setFlatLineTime(long flatLineTime);
    boolean setHost(String host);
    boolean setPort(int port);

    String toString();
    void linkUp();
    void linkDown();
    void reset();
    boolean sendRequest( int size,
                         CRC32 checksum,
                         byte[] payload,
                         INetworkService.OnSendHandler handler );
    String getLocalIpAddress();
}

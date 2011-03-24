package edu.vu.isis.ammo.core.network;

public interface INetChannel {
	static public final int PENDING         =  0; // the run failed by some unhandled exception
	static public final int EXCEPTION       =  1; // the run failed by some unhandled exception
	
	static public final int CONNECTING      =  2; // trying to connect
	static public final int CONNECTED       =  3; // the socket is good an active
	
	static public final int DISCONNECTED    =  4; // the socket is disconnected
	static public final int STALE           =  5; // indicating there is a message
	static public final int LINK_WAIT       =  6; // indicating the underlying link is down 
	
	static public final int WAIT_CONNECT    =  7; // waiting for connection
	static public final int SENDING         =  8; // indicating the next thing is the size
	static public final int TAKING          =  9; // indicating the next thing is the size
	static public final int INTERRUPTED     = 10; // the run was canceled via an interrupt
	
	
	static public final int SHUTDOWN        = 11; // the run is being stopped
	static public final int START           = 12; // indicating the next thing is the size
	static public final int RESTART         = 13; // indicating the next thing is the size
	static public final int WAIT_RECONNECT  = 14; // waiting for connection
	static public final int STARTED         = 15; // indicating there is a message
	static public final int SIZED           = 16; // indicating the next thing is a checksum
	static public final int CHECKED         = 17; // indicating the bytes are being read
	static public final int DELIVER         = 18; // indicating the message has been read
	
}

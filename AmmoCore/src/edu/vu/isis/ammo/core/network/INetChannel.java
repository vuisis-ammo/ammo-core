package edu.vu.isis.ammo.core.network;

public interface INetChannel {
	static public final int PENDING         =  0; // the run failed by some unhandled exception
	static public final int EXCEPTION       =  1; // the run failed by some unhandled exception
	
	static public final int CONNECTING      = 20; // trying to connect
	static public final int CONNECTED       = 21; // the socket is good an active
	
	static public final int DISCONNECTED    = 30; // the socket is disconnected
	static public final int STALE           = 31; // indicating there is a message
	static public final int LINK_WAIT       = 32; // indicating the underlying link is down
	static public final int LINK_ACTIVE     = 33; // indicating the underlying link is down 
	
	static public final int WAIT_CONNECT    = 40; // waiting for connection
	static public final int SENDING         = 41; // indicating the next thing is the size
	static public final int TAKING          = 42; // indicating the next thing is the size
	static public final int INTERRUPTED     = 43; // the run was canceled via an interrupt
	
	
	static public final int SHUTDOWN        = 51; // the run is being stopped
	static public final int START           = 52; // indicating the next thing is the size
	static public final int RESTART         = 53; // indicating the next thing is the size
	static public final int WAIT_RECONNECT  = 54; // waiting for connection
	static public final int STARTED         = 55; // indicating there is a message
	static public final int SIZED           = 56; // indicating the next thing is a checksum
	static public final int CHECKED         = 57; // indicating the bytes are being read
	static public final int DELIVER         = 58; // indicating the message has been read
	
}

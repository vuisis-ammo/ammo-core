/**
 * 
 */
package edu.vu.isis.ammo.core.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import edu.vu.isis.ammo.core.CorePreferences;
import edu.vu.isis.ammo.core.network.NetworkService.TcpReceiverThread;

/**
 * @author phreed
 *
 */
public class AmmoTcpSocket {
	private static final Logger logger = LoggerFactory.getLogger(AmmoTcpSocket.class);
	
	private boolean isStale = true;
	private Socket tcpSocket = null;
	private TcpReceiverThread receiverThread = null;
	private int connectTimeout = 500;
	private int socketTimeout = 5 * 1000; // milliseconds.
	private static final String DEFAULT_HOST = "10.0.2.2";
	private String gatewayName = null;
	private InetAddress gatewayIpAddr = null;
	private int gatewayPort = 32896;
	
	public AmmoTcpSocket() {
		super();
		this.isStale = true;
	}
	
	public void setStale() {
		this.isStale = true;
		
		if (this.receiverThread != null) {
			this.receiverThread.close();
			this.receiverThread = null;
		}
	}
	
	public void setConnectTimeout(int value) {
		this.connectTimeout = value;
	}
	public void setSocketTimeout(int value) {
		this.socketTimeout = value;
	}
	
	public void setHost(String host) {
		this.gatewayName = host;
	}
	public void setPort(int port) {
		this.gatewayPort = port;
	}
	
	public void reconnect() {
		if (this.gatewayName == null) this.gatewayName = DEFAULT_HOST;
		if (this.gatewayPort < 1) return;
		
		this.tcpSocket = new Socket();
		InetSocketAddress sockAddr = new InetSocketAddress(gatewayIpAddr, gatewayPort);
		try {
			tcpSocket.connect(sockAddr, this.connectTimeout);
		} catch (IOException e) {
			tcpSocket = null;
		}
		if (tcpSocket == null) return;
		try {
			this.tcpSocket.setSoTimeout(this.socketTimeout);
		} catch (SocketException ex) {
			return;
		}
	}
	
	public boolean isConnected() {
		if (tcpSocket == null) return false;
		if (tcpSocket.isClosed()) return false;
		return tcpSocket.isConnected();
	}
	
	public void setReceiverThread(TcpReceiverThread thread) {
		this.receiverThread = thread;
		
	}
	public boolean startReceiverThread() {
		if (this.receiverThread == null) return false;
		this.receiverThread.start();
		return true;
	}
	
	/**
	 * Close the socket.
	 * @return whether the socket was closed.
	 *         false may simply indicate that the socket was already closed.
	 */
	public boolean close() {
		if (this.tcpSocket == null) return false;
		if (this.tcpSocket.isClosed()) return false;
		try {
			this.tcpSocket.close();
			return true;
		} catch (IOException e) {
			logger.warn("could not close socket");
		}
		return false;
	}
	
	public boolean hasSocket() {
		if (this.tcpSocket == null) return false;
		if (this.tcpSocket.isClosed()) return false;
		return true;
	}
	
	public InputStream getInputStream() {
		try {
			return this.tcpSocket.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
    public boolean disconnect() {
		if (this.receiverThread == null) return false;
		if (!this.receiverThread.hasSocket()) return false;
		
		this.receiverThread.interrupt();
		this.receiverThread.close();
		this.receiverThread = null;
	    return true;
    }

	public OutputStream getOutputStream() {
		try {
			return this.tcpSocket.getOutputStream();
		} catch (IOException e) {
			logger.warn("could not get output stream");
			return null;
		}
	}
	
	
}

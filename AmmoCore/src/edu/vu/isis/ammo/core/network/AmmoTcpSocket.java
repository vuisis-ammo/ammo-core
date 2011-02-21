/**
 * 
 */
package edu.vu.isis.ammo.core.network;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Looper;

/**
 * @author phreed
 *
 */
public class AmmoTcpSocket {
	private static final Logger logger = LoggerFactory.getLogger(AmmoTcpSocket.class);
	
	private boolean isEnabled = true;
	private boolean isStale = true;
	private Socket tcpSocket = null;
	private TcpReceiverThread receiverThread = null;
	private int connectTimeout = 500;
	private int socketTimeout = 5 * 1000; // milliseconds.
	private static final String DEFAULT_HOST = "10.0.2.2";
	private String gatewayName = null;
	private InetAddress gatewayIpAddr = null;
	private int gatewayPort = 32896;
	private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
	private NetworkService driver = null;
	
	public AmmoTcpSocket(NetworkService driver) {
		super();
		this.isStale = true;
		this.driver = driver;
	}
	
	public void setStale() {
		this.isStale = true;
		
		if (this.receiverThread != null) {
			this.receiverThread.close();
			this.receiverThread = null;
		}
	}
	
	public boolean enable() {
		if (this.isEnabled == true) return false;
		this.isEnabled = true;
		this.reconnect();
		return true;
	}
	public boolean diable() {
		if (this.isEnabled == false) return false;
		this.isEnabled = false;
		return true;
	}
	
	public boolean setConnectTimeout(int value) {
		this.connectTimeout = value;
		return true;
	}
	public boolean setSocketTimeout(int value) {
		this.socketTimeout = value;
		return true;
	}
	
	public boolean setHost(String host) {
		if (gatewayName == host) return false;
		this.isStale = true;
		this.gatewayName = host;
		return true;
	}
	public boolean setPort(int port) {
		if (gatewayPort == port) return false;
		this.isStale = true;
		this.gatewayPort = port;
		return true;
	}
	
	/**
	 * We don't need to reconnect unless.
	 * 1) the connection has been lost 
	 * 2) the connection has been marked stale
	 * 3) the connection is enabled.
	 * 
	 * @return
	 */
	public boolean reconnect() {
		if (!this.isStale 
		 && this.isConnected()
		 && !this.isEnabled) {
			return false;
		}
		if (this.gatewayName == null) this.gatewayName = DEFAULT_HOST;
		if (this.gatewayPort < 1) return false;
		
		this.tcpSocket = new Socket();
		InetSocketAddress sockAddr = new InetSocketAddress(gatewayIpAddr, gatewayPort);
		try {
			tcpSocket.connect(sockAddr, this.connectTimeout);
		} catch (IOException e) {
			tcpSocket = null;
		}
		if (tcpSocket == null) return false;
		try {
			this.tcpSocket.setSoTimeout(this.socketTimeout);
		} catch (SocketException ex) {
			return false;
		}
		this.receiverThread = new TcpReceiverThread(this);
		return true;
	}
	
	public boolean isConnected() {
		if (tcpSocket == null) return false;
		if (tcpSocket.isClosed()) return false;
		return tcpSocket.isConnected();
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
	
    public boolean disconnect() {
		if (this.receiverThread == null) return false;
		if (!this.receiverThread.hasSocket()) return false;
		
		this.receiverThread.interrupt();
		this.receiverThread.close();
		this.receiverThread = null;
	    return true;
    }

	/** 
	 * do your best to send the message.
	 * 
	 * @param size
	 * @param checksum
	 * @param message
	 * @return
	 */
	public boolean sendGatewayRequest(int size, int checksum, byte[] message) 
	{
		if (! this.reconnect()) return false;
		
		DataOutputStream dos;
		try {
			
			dos = new DataOutputStream(tcpSocket.getOutputStream());
			ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE + Integer.SIZE);
			// ByteOrder order = buf.order();
			buf.order(this.endian);
			buf.putInt(size).putInt(checksum);
			dos.write(buf.array());
			dos.write(message);
		} catch (SocketException e) {
			e.printStackTrace();
			return false;
		} catch (IOException ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * A thread for receiving incoming messages on the tcp socket.
	 * The main method is run().
	 *
	 */
	public class TcpReceiverThread extends Thread {
		final private NetworkService driver;

		private AmmoTcpSocket parent = null;
		volatile private int mState;
		
		static private final int SHUTDOWN = 0; // the run is being stopped
		static private final int START = 1;    // indicating the next thing is the size
		static private final int SIZED = 2;    // indicating the next thing is a checksum
		static private final int CHECKED = 3;  // indicating the bytes are being read
		static private final int DELIVER = 4;  // indicating the message has been read
		
		private TcpReceiverThread(AmmoTcpSocket aSocket) {
			this.driver = aSocket.driver;
			this.parent = aSocket;
		}

		public void close() {
			this.mState = SHUTDOWN;
			parent.close();
		}
		public boolean hasSocket() { return this.parent.hasSocket(); }

		@Override
		public void start() {
			logger.debug("tcp receiver thread");
			super.start();
		}

		/**
		 * Initiate a connection to the server and then wait for a response.
		 * All responses are of the form:
		 * size     : int32
		 * checksum : int32
		 * bytes[]  : <size>
		 * This is done via a simple state machine.
		 * If the checksum doesn't match the connection is dropped and restarted.
		 * 
		 * Once the message has been read it is passed off to...
		 */
		@Override
		public void run() { 
			Looper.prepare();
			InputStream insock;
			try {
				insock = this.parent.tcpSocket.getInputStream();
			} catch (IOException e1) {
				logger.error("could not open input stream on socket");
				return;
			}
			BufferedInputStream bis = new BufferedInputStream(insock, 1024);
			if (bis == null) return;

			mState = START;

			int bytesToRead = 0; // indicates how many bytes should be read
			int bytesRead = 0;   // indicates how many bytes have been read
			CRC32 checksum = null;
			
			byte[] message = null;
			byte[] byteToReadBuffer = new byte[Integer.SIZE];
			byte[] checksumBuffer = new byte[Integer.SIZE];
			
			boolean loop = true;
			while (loop) {
				try {
					switch (mState) {
					case SHUTDOWN:
						loop = false;
						break;
					case START:  // look for the size
						{
							bis.read(byteToReadBuffer);
							ByteBuffer bbuf = ByteBuffer.wrap(byteToReadBuffer);
							bbuf.order(this.parent.endian);
							bytesToRead = bbuf.getInt();
							
							if (bytesToRead < 0) break; // bad read keep trying
							if (bytesToRead > 100000) {
								logger.warn("a message with "+bytesToRead);
							}
							this.mState = SIZED;
						}
						break;
					case SIZED: // look for the checksum
						{
							bis.read(checksumBuffer);
							checksum = new CRC32();
							checksum.update(checksumBuffer);
							message = new byte[bytesToRead];
						
							logger.info(checksumBuffer.toString(), checksum.toString());
							bytesRead = 0;
							this.mState = CHECKED;
						} 
						break;
					case CHECKED: // read the message
						{
							while (bytesRead < bytesToRead) {
								int temp = bis.read(message, 0, bytesToRead - bytesRead);
								if (temp >= 0) {
									bytesRead += temp;
								}
							}
							if (bytesRead < bytesToRead)
								break;
							this.mState = DELIVER;
						}
						break;
					case DELIVER: // deliver the message to the gateway
						if (!this.driver.deliverGatewayResponse(message, checksum)) {
							loop = false;
						}
						message = null;
						this.mState = START;
						break;
					}
				} catch (SocketTimeoutException ex) {
					// if the message times out then it will need to be retransmitted.
					if (this.mState != SHUTDOWN) this.mState = START;
				} catch (IOException ex) {
					this.mState = SHUTDOWN;
					logger.warn(ex.getMessage());
				}
			}
			logger.debug("no longer listening, thread closed");
			try { bis.close(); } catch (IOException e) {}
			this.parent.close(); 
		}
	}
	
}

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
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.AsyncTask;
import android.os.Looper;

/**
 * Two long running threads and one short.
 * The long threads are for sending and recieving messages.
 * The short thread is to connect the socket.
 * The sent messages are placed into a queue if the socket is connected.
 * 
 * @author phreed
 *
 */
public class TcpChannel {
	private static final Logger logger = LoggerFactory.getLogger(TcpChannel.class);
	
	private BlockingQueue<GwMessage> sendQueue = new LinkedBlockingQueue<GwMessage>(20);
	
	private boolean isEnabled = false;
	private boolean isStale = true;
	
	private Socket socket = null;
	private ConnectorTask connectorTask;
	private ReceiverThread receiverThread;
	private SenderThread senderThread;
	
	private int connectTimeout = 500;
	private int socketTimeout = 5 * 1000; // milliseconds.
	private static final String DEFAULT_HOST = "10.0.2.2";
	private String gatewayHost = null;
	private int gatewayPort = 32896;
	
	private static Boolean isConnected = false;  // condition variable
	
	private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
	private NetworkService driver = null;
	private final Object syncObj;
		
	private TcpChannel(NetworkService driver) {
		super();
		logger.trace("::<constructor>");
		this.syncObj = this;
		this.isStale = true;
		this.driver = driver;
		TcpChannel.isConnected = false;
	}
	
	public static TcpChannel getInstance(NetworkService driver) {
		logger.trace("::getInstance");
		synchronized (TcpChannel.isConnected) {
			TcpChannel instance = new TcpChannel(driver);
			
			instance.senderThread = instance.new SenderThread(instance);
			instance.receiverThread = instance.new ReceiverThread(instance);
			return instance;
		}
	}
	
	public void setStale() {
		synchronized (this) {
			logger.trace("::setStale {}", this.isStale);
			this.isStale = true;
			this.tryConnect(true);
		}
	}
	public boolean isStale() {
		logger.trace("::isStale {}", this.isStale);
		return this.isStale;
	}
	
	/** 
	 * Was the status changed as a result of enabling the connection.
	 * @return
	 */
	public boolean isEnabled() { return this.isEnabled(); }
	public boolean enable() {
		logger.trace("::enable");
		if (this.isEnabled == true) 
			return false;
		this.isEnabled = true;
		this.setStale();
		this.tryConnect(false);
		return true;
	}
	public boolean disable() {
		logger.trace("::disable");
		if (this.isEnabled == false) 
			return false;
		this.isEnabled = false;
		this.setStale();
		return true;
	}
	
	public boolean setConnectTimeout(int value) {
		logger.trace("::setConnectTimeout {}", value);
		this.connectTimeout = value;
		return true;
	}
	public boolean setSocketTimeout(int value) {
		logger.trace("::setSocketTimeout {}", value);
		this.socketTimeout = value;
		return true;
	}
	
	public boolean setHost(String host) {
		logger.trace("::setHost {}", host);
		if (gatewayHost == host) return false;
		this.setStale();
		this.gatewayHost = host;
		return true;
	}
	public boolean setPort(int port) {
		logger.trace("::setPort {}", port);
		if (gatewayPort == port) return false;
		this.setStale();
		this.gatewayPort = port;
		return true;
	}
	
	public String toString() {
		return "socket: host["+this.gatewayHost+"] port["+this.gatewayPort+"]";
	}
	
	/**
	 * We don't need to reconnect unless.
	 * 1) the connection has been lost 
	 * 2) the connection has been marked stale
	 * 3) the connection is enabled.
	 * 4) an explicit reconnection was requested
	 * 
	 * @return
	 */
	public boolean tryConnect(boolean reconnect) {
		logger.trace("::tryConnect");
		synchronized (TcpChannel.isConnected) {
			if (reconnect) return connect_aux(reconnect);
			if (!this.isEnabled) return true;
			if (this.isStale) return connect_aux(reconnect);
			if (!this.checkConnection()) return connect_aux(reconnect);
		}
		return false;
	}
	private boolean connect_aux(boolean reconnect) {
		if (this.connectorTask == null) {
			this.connectorTask = new ConnectorTask();
			this.connectorTask.execute(this);
			return true;
		}
		if (! connectorTask.getStatus().equals(AsyncTask.Status.FINISHED)) 
			return false;
		this.connectorTask = new ConnectorTask();
		this.connectorTask.execute(this);
		return true;
	}

	private class ConnectorTask extends AsyncTask<TcpChannel, Void, TcpChannel> {
	    /** The system calls this to perform work in a worker thread and
	      * delivers it the parameters given to AsyncTask.execute() */
	    protected TcpChannel doInBackground(TcpChannel... parentSet) {
	    	logger.trace("::reconnect");
	    	if (parentSet.length < 1) return null;
	    	
			TcpChannel parent = parentSet[0];
			if (parent.gatewayHost == null) parent.gatewayHost = DEFAULT_HOST;
			if (parent.gatewayPort < 1) return parent;
			InetAddress gatewayIpAddr = null;
			try {
				gatewayIpAddr = InetAddress.getByName(parent.gatewayHost);
			} catch (UnknownHostException e) {
				logger.info("could not resolve host name");
				return parent;
			}
			parent.socket = new Socket();
			InetSocketAddress sockAddr = new InetSocketAddress(gatewayIpAddr, parent.gatewayPort);
			try {
				parent.socket.connect(sockAddr, parent.connectTimeout);
			} catch (IOException ex) {
				logger.warn("connection failed : " + ex.getLocalizedMessage());
				parent.socket = null;
			}
			if (parent.socket == null) return parent;
			try {
				parent.socket.setSoTimeout(parent.socketTimeout);
			} catch (SocketException ex) {
				return parent;
			}
			return parent;
	    }
	    
	    /** The system calls this to perform work in the UI thread and delivers
	      * the result from doInBackground() */
	    protected void onPostExecute(TcpChannel parent) {
	    	parent.isStale = false;
			synchronized (TcpChannel.isConnected) {
				TcpChannel.isConnected = true;
				try {
					TcpChannel.isConnected.notifyAll();
				} catch (IllegalMonitorStateException ex) {
					logger.warn("connection made notification but no one is waiting");
				}
			}
	    }
	}
	
	public boolean checkConnection() {
		logger.trace("::isConnected");
		if (!TcpChannel.isConnected) return false;
		if (this.socket == null) return false;
		if (this.socket.isClosed()) return false;
		return this.socket.isConnected();
	}
	
	/**
	 * Close the socket.
	 * @return whether the socket was closed.
	 *         false may simply indicate that the socket was already closed.
	 */
	public boolean close() {
		synchronized (this.syncObj) {
			logger.trace("::close");
			if (this.socket == null) return false;
			if (this.socket.isClosed()) return false;
			try {
				this.socket.close();
				return true;
			} catch (IOException e) {
				logger.warn("could not close socket");
			}
			return false;
		}
	}
	
	public boolean hasSocket() {
		logger.trace("::hasSocket");
		if (this.socket == null) return false;
		if (this.socket.isClosed()) return false;
		return true;
	}
	
    public boolean disconnect() {
    	synchronized (this.syncObj) {
    		logger.trace("::disconnect");
			this.senderThread.interrupt();
			this.receiverThread.interrupt();
		    return true;
    	}
    }

	/** 
	 * do your best to send the message.
	 * 
	 * @param size
	 * @param checksum
	 * @param message
	 * @return
	 */
	public boolean sendRequest(int size, CRC32 checksum, byte[] payload) 
	{
		synchronized (this.syncObj) {
			logger.trace("::sendGatewayRequest");
			if (! TcpChannel.isConnected) {
				this.tryConnect(false);
				return false;
			}
			try {
				this.sendQueue.put(new GwMessage(size, checksum, payload));
			} catch (InterruptedException e) {
				return false;
			}
			return true;
		}
	}
	
	public class GwMessage {
		public final int size;
		public final CRC32 checksum;
		public final byte[] payload;
		public GwMessage(int size, CRC32 checksum, byte[] payload) {
			this.size = size; this.checksum = checksum; this.payload = payload;
		}
	}
	
	/**
	 * A thread for receiving incoming messages on the socket.
	 * The main method is run().
	 *
	 */
	public class SenderThread extends Thread {
		private TcpChannel parent;
		private final BlockingQueue<GwMessage> queue;
		
		private SenderThread(TcpChannel parent) {
			logger.trace("::<constructor>");
			this.parent = parent;
			this.queue = parent.sendQueue;
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
			logger.trace("::run");
			DataOutputStream dos;
			try {		
				dos = new DataOutputStream(socket.getOutputStream());
				ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE + Integer.SIZE);
				// ByteOrder order = buf.order();
				buf.order(parent.endian);
				
				while (true) {
					synchronized(TcpChannel.isConnected) {
						while (!TcpChannel.isConnected) {
							try {
								TcpChannel.isConnected.wait();
							} catch (InterruptedException ex) {
								logger.warn("thread interupted {}",ex.getLocalizedMessage());
								return; // looks like the thread is being shut down.
							}
						}
					}
					GwMessage msg = queue.take();
					buf.putInt(msg.size);
					long cvalue = msg.checksum.getValue();
					byte[] checksum = new byte[] {
				                (byte)(cvalue >>> 24),
				                (byte)(cvalue >>> 16),
				                (byte)(cvalue >>> 8),
				                (byte)cvalue};
					buf.put(checksum, 0, 4);
					dos.write(buf.array());
					dos.write(msg.payload);
				}
			} catch (SocketException ex) {
				ex.printStackTrace();
			} catch (IOException ex) {
				ex.printStackTrace();
			} catch (InterruptedException ex) {
				logger.warn("interupted writing messages");
			}
		}
	}
	/**
	 * A thread for receiving incoming messages on the socket.
	 * The main method is run().
	 *
	 */
	public class ReceiverThread extends Thread {
		final private NetworkService driver;

		private TcpChannel parent = null;
		volatile private int mState;
		
		static private final int SHUTDOWN = 0; // the run is being stopped
		static private final int START    = 1; // indicating the next thing is the size
		static private final int STARTED  = 2; // indicating there is a message
		static private final int SIZED    = 3; // indicating the next thing is a checksum
		static private final int CHECKED  = 4; // indicating the bytes are being read
		static private final int DELIVER  = 5; // indicating the message has been read
		
		private ReceiverThread(TcpChannel aSocket) {
			logger.trace("::<constructor>");
			this.driver = aSocket.driver;
			this.parent = aSocket;
		}

		public void close() {
			logger.trace("::close");
			this.mState = SHUTDOWN;
			parent.close();
		}
		public boolean hasSocket() { return this.parent.hasSocket(); }

		@Override
		public void start() {
			super.start();
			logger.trace("::start");
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
			logger.trace("::run");
			Looper.prepare();
			InputStream insock;
			try {
				insock = this.parent.socket.getInputStream();
			} catch (IOException ex) {
				logger.error("could not open input stream on socket {}", ex.getLocalizedMessage());
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
			
			while (true) {	
				synchronized(TcpChannel.isConnected) {
					while (!TcpChannel.isConnected) {
						try {
							TcpChannel.isConnected.wait();
						} catch (InterruptedException ex) {
							logger.warn("thread interupted {}",ex.getLocalizedMessage());
							shutdown(bis); // looks like the thread is being shut down.
							return;
						}
					}
				}
				try {
					switch (mState) {
						case START:  // look for the size
							bis.read(byteToReadBuffer);
							this.mState = STARTED;
							break;
					}
					synchronized (this.parent.syncObj) { 
						logger.debug("read loop");
						switch (mState) {
						case STARTED:  // look for the size
							{
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
							this.driver.deliverGatewayResponse(message, checksum);
							message = null;
							this.mState = START;
							break;
						}
					}
				} catch (SocketTimeoutException ex) {
					// if the message times out then it will need to be retransmitted.
					if (this.mState != SHUTDOWN) this.mState = START;
				} catch (IOException ex) {
					logger.warn(ex.getMessage());
					shutdown(bis);
					return;
				}
			}
			
		}
		
		private void shutdown(BufferedInputStream bis) {
			logger.debug("no longer listening, thread closing");
			try { bis.close(); } catch (IOException e) {}
			return;
		}
	}
	
	// ********** UTILITY METHODS ****************
	
	/**
	 * A routine to get the local ip address
	 * TODO use this someplace
	 * 
	 * @return
	 */
	public String getLocalIpAddress() {
		logger.trace("::getLocalIpAddress");
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) { 
						return inetAddress.getHostAddress().toString(); 
					}
				}
			}
		} catch (SocketException ex) {
			logger.error( ex.toString());
		}
		return null;
	}
}

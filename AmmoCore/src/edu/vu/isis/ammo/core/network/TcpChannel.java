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

import android.os.Looper;

/**
 * Two long running threads and one short.
 * The long threads are for sending and receiving messages.
 * The short thread is to connect the socket.
 * The sent messages are placed into a queue if the socket is connected.
 * 
 * @author phreed
 *
 */
public class TcpChannel {
	private static final Logger logger = LoggerFactory.getLogger(TcpChannel.class);
	
	private boolean isEnabled = false;
	
	private Socket socket = null;
	private ConnectorThread connectorThread;
	private ReceiverThread receiverThread;
	private SenderThread senderThread;
	
	private int connectTimeout = 500;
	private int socketTimeout = 5 * 1000; // milliseconds.
	
	private String gatewayHost = null;
	private int gatewayPort = -1;
	
	private class ConnectionState {
		private int state; 
		public ConnectionState() { this.state = ConnectorThread.STALE; }
		public void set(int state) { this.state = state; }
		public int get() { return this.state; }
		
		public boolean isConnected() { 
			synchronized (this) {
				return this.state == ConnectorThread.CONNECTED; 
			}
		}
		
		public boolean isStale() { 
			logger.trace("Thread <{}>::isStale", Thread.currentThread().getId());
			synchronized (this) {
				return this.state == ConnectorThread.STALE;
			}
		}
	}
	private final ConnectionState connectionState;  // condition variable
	
	private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
	private final Object syncObj;
		
	private TcpChannel() {
		super();
		logger.trace("Thread <{}>TcpChannel::<constructor>", Thread.currentThread().getId());
		this.syncObj = this;
		this.connectionState = new ConnectionState();
	}
	
	public static TcpChannel getInstance(NetworkService driver) {
		logger.trace("Thread <{}>::getInstance", Thread.currentThread().getId());
		TcpChannel instance = new TcpChannel();
		synchronized (instance.syncObj) {
			instance.connectorThread = new ConnectorThread(instance, driver);
			instance.senderThread = new SenderThread(instance, driver);
			instance.receiverThread = new ReceiverThread(instance, driver);
			return instance;
		}
	}
	
	public boolean isConnected() { return this.connectionState.isConnected(); }
	/** 
	 * Was the status changed as a result of enabling the connection.
	 * @return
	 */
	public boolean isEnabled() { return this.isEnabled; }
	public boolean enable() {
		logger.trace("Thread <{}>::enable", Thread.currentThread().getId());
		synchronized (this.syncObj) {
			if (this.isEnabled == true) 
				return false;
			this.isEnabled = true;
			
			this.connectorThread.start();
			this.senderThread.start();
			this.receiverThread.start();
		}
		return true;
	}
	public boolean disable() {
		logger.trace("Thread <{}>::disable", Thread.currentThread().getId());
		synchronized (this.syncObj) {
			if (this.isEnabled == false) 
				return false;
			this.isEnabled = false;
			
			this.connectorThread.stop();
			this.senderThread.stop();
			this.receiverThread.stop();
		}
		return true;
	}
	
	public boolean setConnectTimeout(int value) {
		logger.trace("Thread <{}>::setConnectTimeout {}", Thread.currentThread().getId(), value);
		this.connectTimeout = value;
		return true;
	}
	public boolean setSocketTimeout(int value) {
		logger.trace("Thread <{}>::setSocketTimeout {}", Thread.currentThread().getId(), value);
		this.socketTimeout = value;
		this.setStale();
		return true;
	}
	
	public boolean setHost(String host) {
		logger.trace("Thread <{}>::setHost {}", Thread.currentThread().getId(), host);
		if (gatewayHost == host) return false;
		this.gatewayHost = host;
		this.setStale();
		return true;
	}
	public boolean setPort(int port) {
		logger.trace("Thread <{}>::setPort {}", Thread.currentThread().getId(), port);
		if (gatewayPort == port) return false;
		this.gatewayPort = port;
		this.setStale();
		return true;
	}
	
	public String toString() {
		return "socket: host["+this.gatewayHost+"] port["+this.gatewayPort+"]";
	}
	
	/**
	 * forces a reconnection.
	 */
	public void setStale() { 
		logger.trace("Thread <{}>::setStale", Thread.currentThread().getId());
		synchronized (this.connectionState) {
			//this.connectionState.
			this.connectionState.set(ConnectorThread.STALE);
			this.connectionState.notifyAll();
		}
	}


    /**
     * manages the connection.
     * enable or disable expresses the operator intent.
     * There is no reason to run the thread unless the channel is enabled.
     * 
     * Any of the properties of the channel 
     * @author phreed
     *
     */
	private static class ConnectorThread extends Thread {
		private static final Logger logger = LoggerFactory.getLogger(ConnectorThread.class);
	
		private static final String DEFAULT_HOST = "10.0.2.2";
		private static final int DEFAULT_PORT = 32896;
		private static final int GATEWAY_RETRY_TIME = 20000; // 20 seconds
		
		static private final int CONNECTED     = 0; // the socket is good an active
		static private final int CONNECTING    = 1; // trying to connect
		static private final int DISCONNECTED  = 2; // the socket is disconnected
		static private final int DISABLED      = 3; // the connection intentionally disabled
		static private final int STALE         = 4; // indicating there is a message
		static private final int LINK_WAIT     = 5; // indicating the underlying link is down 
		
		private TcpChannel parent;
		private INetworkService.OnConnectHandler handler;
		
		private ConnectorThread(TcpChannel parent, INetworkService.OnConnectHandler handler) {
			logger.trace("Thread <{}>ConnectorThread::<constructor>", Thread.currentThread().getId());
			this.parent = parent;
			this.handler = handler;
		}
		
		/**
		 * A state machine based.
		 * Most of the time this machine will be in a CONNECTED state.
		 * In that CONNECTED state the machine wait for the connection state to 
		 * change or for an interrupt indicating that the thread is being shut down.
		 *  
		 *  The state machine takes care of the following constraints:
		 * We don't need to reconnect unless.
		 * 1) the connection has been lost 
		 * 2) the connection has been marked stale
		 * 3) the connection is enabled.
		 * 4) an explicit reconnection was requested
		 * 
		 * @return
		 */
		@Override
		public void run() { 
			logger.trace("{}::run", Thread.currentThread().getId());
			synchronized(parent.connectionState) {
				MAINTAIN_CONNECTION: while (true) {
					switch (parent.connectionState.get()) {
				
						case STALE: 
							disconnect();
							this.parent.connectionState.set(LINK_WAIT);
							break;
							
						case LINK_WAIT:
							if (isLinkUp()) {
							  this.parent.connectionState.set(DISCONNECTED);
							} 
							// on else wait for link to come up TODO triggered through broadcast receiver
							break;
							
						case DISCONNECTED:
							if (! this.reconnect() ) {
								this.parent.connectionState.set(CONNECTING);
							} else {
								this.parent.connectionState.set(CONNECTED);
							}
							break;

						case CONNECTING: // keep trying
							if (this.reconnect() ) {
								this.parent.connectionState.set(CONNECTED);
							} else {
								try {
									Thread.sleep(GATEWAY_RETRY_TIME);
								} catch (InterruptedException ex) {
									this.parent.connectionState.set(STALE);
								}
							}
							break;
						
						case CONNECTED:
							handler.authenticate();
						default: {
							logger.info("connected to {}:{}", this.parent.gatewayHost, this.parent.gatewayPort);
							
							this.parent.connectionState.notifyAll();
							try {
							    this.parent.connectionState.wait();
							    // wait for somebody to change the connection status
							    
							} catch (InterruptedException ex) {
								logger.info("connection intentionally disabled {}", parent.connectionState );
								parent.connectionState.set(STALE);
								break MAINTAIN_CONNECTION;
		
							}
						}
					}
				}
				try {
					this.parent.socket.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			} 
	    }
		
		private boolean disconnect() {
			logger.trace("::disconnect");
			try {
				if (this.parent.socket == null) return true;
				this.parent.socket.close();
			} catch (IOException e) {
				return false;
			}
			return true;
		}
	    
		private boolean isLinkUp() { return true; }
	  
		/**
		 * connects to the gateway
		 * @return
		 */
		private boolean reconnect() {
			logger.trace("Thread <>SenderThread::run", Thread.currentThread().getId());
			
			String host = (parent.gatewayHost == null) ? parent.gatewayHost : DEFAULT_HOST;
			int port =  (parent.gatewayPort > 10) ? parent.gatewayPort : DEFAULT_PORT;
			InetAddress ipaddr = null;
			try {
				ipaddr = InetAddress.getByName(host);
			} catch (UnknownHostException e) {
				logger.info("could not resolve host name");
				return false;
			}
			parent.socket = new Socket();
			InetSocketAddress sockAddr = new InetSocketAddress(ipaddr, port);
			try {
				parent.socket.connect(sockAddr, parent.connectTimeout);
			} catch (IOException ex) {
				logger.warn("connection failed : " + ex.getLocalizedMessage());
				parent.socket = null;
			}
			if (parent.socket == null) return false;
			try {
				parent.socket.setSoTimeout(parent.socketTimeout);
			} catch (SocketException ex) {
				return false;
			}
			logger.info("connection established : " + ipaddr + ":" + parent.gatewayPort);
			return false;
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
	public boolean sendRequest(int size, CRC32 checksum, byte[] payload, INetworkService.OnSendMessageHandler handler) 
	{
		synchronized (this.syncObj) {
			this.senderThread.putMsg(new GwMessage(size, checksum, payload, handler) );
			return true;
		}
	}
	
	public class GwMessage {
		public final int size;
		public final CRC32 checksum;
		public final byte[] payload;
		public final INetworkService.OnSendMessageHandler handler;
		public GwMessage(int size, CRC32 checksum, byte[] payload, INetworkService.OnSendMessageHandler handler) {
			this.size = size; this.checksum = checksum; this.payload = payload; this.handler = handler;
		}
	}
	
	/**
	 * A thread for receiving incoming messages on the socket.
	 * The main method is run().
	 *
	 */
	public static class SenderThread extends Thread {
		private static final Logger logger = LoggerFactory.getLogger(SenderThread.class);
	
		private final TcpChannel parent;
		private final INetworkService.OnSendMessageHandler handler;
		
		private final BlockingQueue<GwMessage> queue;
		
		private SenderThread(TcpChannel parent, INetworkService.OnSendMessageHandler handler) {
			logger.trace("Thread <{}>SenderThread::<constructor>", Thread.currentThread().getId());
			this.parent = parent;
			this.handler = handler;
			this.queue = new LinkedBlockingQueue<GwMessage>(20);
		}
		
		public void putMsg(GwMessage msg) {
			try {
				this.queue.put(msg);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
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
			logger.trace("Thread <>SenderThread::run", Thread.currentThread().getId());
			DataOutputStream dos;
			try {			
				// one integer for size & four bytes for checksum
			    ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE/Byte.SIZE + 4);
				buf.order(parent.endian);
				GwMessage msg = null;
				while (true) {
					try {
						synchronized(parent.connectionState) {
							while (! parent.connectionState.isConnected()) {
								try {
								    logger.trace("Thread <{}>SenderThread::connectionState.wait",
								    		Thread.currentThread().getId());
								    parent.connectionState.wait();
								} catch (InterruptedException ex) {
									logger.warn("thread interupted {}",ex.getLocalizedMessage());
									return ; // looks like the thread is being shut down.
								}
							}
							dos = new DataOutputStream(parent.socket.getOutputStream());
						}
						msg = queue.take();
						buf.rewind();
						buf.putInt(msg.size);
						long cvalue = msg.checksum.getValue();
						byte[] checksum = new byte[] {
						    (byte)cvalue,
					                (byte)(cvalue >>> 8),
					                (byte)(cvalue >>> 16),
					                (byte)(cvalue >>> 24)
						};
						logger.debug("checksum [{}]", checksum);
						buf.put(checksum, 0, 4);
						dos.write(buf.array());
						dos.write(msg.payload);
						dos.flush();
						
						// legitimately sent to gateway.
						if (msg.handler != null) this.handler.acknowledge(true);
						
					} catch (SocketException ex) {
						logger.warn("socket disconnected while writing messages");
						if (msg.handler != null) this.handler.acknowledge(false);
						synchronized(parent.connectionState) {
							parent.connectionState.set(ConnectorThread.STALE);
							parent.connectionState.notifyAll();
						}
						
					} catch (IOException ex) {
						logger.warn("io exception writing messages");
						if (msg.handler != null) this.handler.acknowledge(false);
						synchronized(parent.connectionState) {
							parent.connectionState.set(ConnectorThread.STALE);
							parent.connectionState.notifyAll();
						}
					} 
				}
			} catch (InterruptedException ex) {
				logger.warn("interupted writing messages");
			}
			synchronized(parent.connectionState) {
				parent.connectionState.set(ConnectorThread.STALE);
				parent.connectionState.notifyAll();
			}
		}
	}
	/**
	 * A thread for receiving incoming messages on the socket.
	 * The main method is run().
	 *
	 */
	public static class ReceiverThread extends Thread {
		private static final Logger logger = LoggerFactory.getLogger(SenderThread.class);
	
		final private INetworkService.OnReceiveMessageHandler handler;

		private TcpChannel parent = null;
		volatile private int state;
		
		static private final int SHUTDOWN = 0; // the run is being stopped
		static private final int START    = 1; // indicating the next thing is the size
		static private final int STARTED  = 2; // indicating there is a message
		static private final int SIZED    = 3; // indicating the next thing is a checksum
		static private final int CHECKED  = 4; // indicating the bytes are being read
		static private final int DELIVER  = 5; // indicating the message has been read
		
		private ReceiverThread(TcpChannel parent, INetworkService.OnReceiveMessageHandler handler ) {
			logger.trace("Thread <{}>ReceiverThread::<constructor>", Thread.currentThread().getId());
			this.parent = parent;
			this.handler = handler;
		}

		public void close() {
			logger.trace("Thread <{}>::close", Thread.currentThread().getId());
			this.state = SHUTDOWN;
		}
		
		@Override
		public void start() {
			super.start();
			logger.trace("Thread <{}>::start", Thread.currentThread().getId());
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
			logger.trace("Thread <{}>ReceiverThread::run", Thread.currentThread().getId());
			Looper.prepare();
			
			state = START;

			int bytesToRead = 0; // indicates how many bytes should be read
			int bytesRead = 0;   // indicates how many bytes have been read
			CRC32 checksum = null;
			
			byte[] message = null;
			byte[] byteToReadBuffer = new byte[Integer.SIZE];
			byte[] checksumBuffer = new byte[Integer.SIZE];
			BufferedInputStream bis = null;
			
			while (true) {
				switch (state) {
					case START:  // look for the size
						synchronized(parent.connectionState) {
							while (! parent.connectionState.isConnected()) {
								try {
								    logger.trace("Thread <{}>ReceiverThread::connectionState.wait", 
								    		Thread.currentThread().getId());
								    parent.connectionState.wait();
								} catch (InterruptedException ex) {
									logger.warn("thread interupted {}",ex.getLocalizedMessage());
									shutdown(bis); // looks like the thread is being shut down.
									return;
								}
							}
							InputStream insock;
							try {
								insock = this.parent.socket.getInputStream();
								bis = new BufferedInputStream(insock, 1024);
							} catch (SocketTimeoutException ex) {
								logger.trace("timeout on socket");
								continue;
							} catch (IOException ex) {
								logger.error("could not open input stream on socket {}", ex.getLocalizedMessage());
								parent.connectionState.set(ConnectorThread.STALE);
								parent.connectionState.notifyAll();
								continue;
							}	
							if (bis == null) continue;
							
							try {
								bis.read(byteToReadBuffer);
							} catch (IOException e) {
								parent.connectionState.set(ConnectorThread.STALE);
								parent.connectionState.notifyAll();
							}
						}
						this.state = STARTED;
						break;
				
					case STARTED:  // look for the size
						{
							ByteBuffer bbuf = ByteBuffer.wrap(byteToReadBuffer);
							bbuf.order(this.parent.endian);
							bytesToRead = bbuf.getInt();
							
							if (bytesToRead < 0) break; // bad read keep trying
							if (bytesToRead > 100000) {
								logger.warn("a message with "+bytesToRead);
							}
							this.state = SIZED;
						}
						break;
					case SIZED: // look for the checksum
						{
							synchronized(parent.connectionState) {
								try {
									bis.read(checksumBuffer);
								} catch (IOException e) {
									parent.connectionState.set(ConnectorThread.STALE);
									parent.connectionState.notifyAll();
									break;
								}
							}
							checksum = new CRC32();
							checksum.update(checksumBuffer);
							message = new byte[bytesToRead];
						
							logger.info(checksumBuffer.toString(), checksum.toString());
							bytesRead = 0;
							this.state = CHECKED;
						} 
						break;
					case CHECKED: // read the message
						{
							synchronized(parent.connectionState) {
								while (bytesRead < bytesToRead) {
									try {
										int temp = bis.read(message, 0, bytesToRead - bytesRead);
										bytesRead += (temp >= 0) ? temp : 0;
									} catch (IOException ex) {
										parent.connectionState.set(ConnectorThread.STALE);
										parent.connectionState.notifyAll();
										break;
									}
								}
							}
							if (bytesRead < bytesToRead)
								break;
							this.state = DELIVER;
						}
						break;
					case DELIVER: // deliver the message to the gateway
						this.handler.deliver(message, checksum);
						message = null;
						this.state = START;
						break;
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
		logger.trace("Thread <{}>::getLocalIpAddress", Thread.currentThread().getId());
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

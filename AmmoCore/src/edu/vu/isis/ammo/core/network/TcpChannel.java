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

	private int connectTimeout = 5 * 1000;     // this should come from network preferences
	private int socketTimeout = 5 * 1000; // milliseconds.

	private String gatewayHost = null;
	private int gatewayPort = -1;


	private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
	private final Object syncObj;

	private TcpChannel(NetworkService driver) {
		super();
		logger.trace("Thread <{}>TcpChannel::<constructor>", Thread.currentThread().getId());
		this.syncObj = this;
		this.connectorThread = new ConnectorThread(this, driver);
		this.senderThread = new SenderThread(this, driver);
		this.receiverThread = new ReceiverThread(this, driver);
	}

	public static TcpChannel getInstance(NetworkService driver) {
		logger.trace("Thread <{}>::getInstance", Thread.currentThread().getId());
		TcpChannel instance = new TcpChannel(driver);
		return instance;
	}

	public boolean isConnected() { return this.connectorThread.isConnected(); }
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
		this.reset();
		return true;
	}

	public boolean setHost(String host) {
		logger.trace("Thread <{}>::setHost {}", Thread.currentThread().getId(), host);
		if ( gatewayHost != null && gatewayHost.equals(host) ) return false;
		this.gatewayHost = host;
		this.reset();
		return true;
	}
	public boolean setPort(int port) {
		logger.trace("Thread <{}>::setPort {}", Thread.currentThread().getId(), port);
		if (gatewayPort == port) return false;
		this.gatewayPort = port;
		this.reset();
		return true;
	}

	public String toString() {
		return "socket: host["+this.gatewayHost+"] port["+this.gatewayPort+"]";
	}

	/**
	 * forces a reconnection.
	 */
	public void reset() { 
		logger.trace("Thread <{}>::reset", Thread.currentThread().getId());
		this.connectorThread.reset();
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

		private TcpChannel parent;
		private INetworkService.OnConnectHandler handler;
		private final State state;

		private ConnectorThread(TcpChannel parent, INetworkService.OnConnectHandler handler) {
			logger.trace("Thread <{}>ConnectorThread::<constructor>", Thread.currentThread().getId());
			this.parent = parent;
			this.handler = handler;

			this.state = new State();
		}
		
		private class State {
			private int value;

			static private final int CONNECTED     = 0; // the socket is good an active
			static private final int CONNECTING    = 1; // trying to connect
			static private final int DISCONNECTED  = 2; // the socket is disconnected
			static private final int STALE         = 4; // indicating there is a message
			static private final int LINK_WAIT     = 5; // indicating the underlying link is down 
			
			private long version; // used to uniquely name the connection
			
			public State() { 
				this.value = STALE; 
				this.version = Long.MIN_VALUE;
			}
			public synchronized void set(int state) {
				logger.trace("Thread <{}>State::set {}", Thread.currentThread().getId(), state);
				if (state == STALE) {
					logger.error("set stale only from the special setStale method");
					return;
				}
				if (state == CONNECTED) 
					version++;
				this.value = state; 
				this.notifyAll(); 
			}
			public synchronized int get() { return this.value; }

			public synchronized boolean isConnected() { 
				return this.value == CONNECTED; 
			}

			public synchronized boolean failure(long version) {
				if (version != this.version) return true;
				if (this.value != CONNECTED) return true;
				this.value = STALE;
				this.notifyAll(); 
				return true;
			}
		}
		
		public boolean isConnected() { 
			return this.state.isConnected(); 
		}
		public long getVersion() { 
			return this.state.version; 
		}
		
		/**
		 * reset forces the channel closed if open.
		 */
		public void reset() { 
			this.state.failure(this.state.version);
		}
		
		public void failure(long version) { 
			this.state.failure(version); 
		}
			

		/**
		 * A value machine based.
		 * Most of the time this machine will be in a CONNECTED value.
		 * In that CONNECTED value the machine wait for the connection value to 
		 * change or for an interrupt indicating that the thread is being shut down.
		 *  
		 *  The value machine takes care of the following constraints:
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
			logger.trace("Thread <{}>ConnectorThread::run", Thread.currentThread().getId());
			MAINTAIN_CONNECTION: while (true) {
				switch (this.state.get()) {

				case State.STALE: 
					disconnect();
					this.state.set(State.LINK_WAIT);
					break;

				case State.LINK_WAIT:
					if (isLinkUp()) {
						this.state.set(State.DISCONNECTED);
					} 
					// on else wait for link to come up TODO triggered through broadcast receiver
					break;

				case State.DISCONNECTED:
					if ( !this.connect() ) {
						this.state.set(State.CONNECTING);
					} else {
						this.state.set(State.CONNECTED);
					}
					break;

				case State.CONNECTING: // keep trying
				if ( this.connect() ) {
					this.state.set(State.CONNECTED);
				} else {
					try {
						Thread.sleep(GATEWAY_RETRY_TIME);
					} catch (InterruptedException ex) {
						logger.info("sleep interrupted - intentional disable, exiting thread ...");
						this.state.set(State.STALE);
						break MAINTAIN_CONNECTION;
					}
				}
				break;

				case State.CONNECTED:
					handler.auth();
				default: {
					try {
						synchronized (this.state) {
							this.state.wait();   // wait for somebody to change the connection status
						}
					} catch (InterruptedException ex) {
						logger.info("connection intentionally disabled {}", this.state );
						this.state.set(State.STALE);
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

		private boolean disconnect() {
			logger.trace("Thread <{}>ConnectorThread::disconnect", Thread.currentThread().getId());
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
		private boolean connect() {
			logger.trace("Thread <{}>ConnectorThread::connect", Thread.currentThread().getId());

			String host = (parent.gatewayHost != null) ? parent.gatewayHost : DEFAULT_HOST;
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
				logger.warn("connection to {}:{} failed : " + ex.getLocalizedMessage(), ipaddr, port);
				parent.socket = null;
				return false;
			}
			if (parent.socket == null) return false;
			try {
				parent.socket.setSoTimeout(parent.socketTimeout);
			} catch (SocketException ex) {
				return false;
			}
			logger.info("connection to {}:{} established ", ipaddr, port);
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
		
		static private final int WAIT_CONNECT  = 1; // waiting for connection
		static private final int SENDING       = 2; // indicating the next thing is the size
		static private final int TAKING        = 3; // indicating the next thing is the size


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
		 * This is done via a simple value machine.
		 * If the checksum doesn't match the connection is dropped and restarted.
		 * 
		 * Once the message has been read it is passed off to...
		 */
		@Override
		public void run() { 
			logger.trace("Thread <{}>SenderThread::run", Thread.currentThread().getId());

			int state = TAKING;

			DataOutputStream dos = null;
			try {            
				// one integer for size & four bytes for checksum
				ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE/Byte.SIZE + 4);
				buf.order(parent.endian);
				GwMessage msg = null;
				long version = Long.MAX_VALUE;

				while (true) {
					switch (state) {
					case WAIT_CONNECT:
						try {
							// if connected then proceed
							// keep the working socket so that if something goes wrong
							// the socket can be checked to see if it has changed
							// in the interim.			
							synchronized (parent.connectorThread.state) {
								while (! parent.connectorThread.isConnected()) {
									try {
										logger.trace("Thread <{}>SenderThread::value.wait",
												Thread.currentThread().getId());

										parent.connectorThread.state.wait();
									} catch (InterruptedException ex) {
										logger.warn("thread interupted {}",ex.getLocalizedMessage());
										return ; // looks like the thread is being shut down.
									}
								}
								long newversion = parent.connectorThread.getVersion();
								if (newversion != version) {
									dos = new DataOutputStream(this.parent.socket.getOutputStream());
									version = newversion;
								}
							}

						} catch (SocketException ex) {
							logger.warn("socket disconnected while writing a message");
							if (msg.handler != null) this.handler.ack(false);
							parent.connectorThread.failure(version);
						} catch (IOException ex) {
							logger.warn("io exception writing messages");
							if (msg.handler != null) this.handler.ack(false);
							parent.connectorThread.failure(version);
						} 
						state = SENDING;
						break;

					case TAKING:
						msg = queue.take(); // THE MAIN BLOCKING CALL
						state = WAIT_CONNECT;
						break;
						
					case SENDING:
						
						// refresh dos to current socket (current means version match)

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
						try {
							dos.write(buf.array());
							dos.write(msg.payload);
							dos.flush();
						} catch (SocketException ex) {
							logger.warn("socket disconnected while writing a message");
							if (msg.handler != null) this.handler.ack(false);
							parent.connectorThread.failure(version);
							state = TAKING;
							break;

						} catch (IOException ex) {
							logger.warn("io exception writing messages");
							if (msg.handler != null) this.handler.ack(false);
							parent.connectorThread.failure(version);
							state = TAKING;
							break;
						} 

						// legitimately sent to gateway.
						if (msg.handler != null) this.handler.ack(true);
						state = TAKING;
						break;
					}
				}
			} catch (InterruptedException ex) {
				logger.warn("interupted writing messages");
			}
			logger.warn("sender thread exiting ...");
		}
	}
	/**
	 * A thread for receiving incoming messages on the socket.
	 * The main method is run().
	 *
	 */
	public static class ReceiverThread extends Thread {
		private static final Logger logger = LoggerFactory.getLogger(ReceiverThread.class);

		final private INetworkService.OnReceiveMessageHandler handler;

		private TcpChannel parent = null;
		// private TcpChannel.ConnectorThread;
		volatile private int state;

		static private final int SHUTDOWN      = 0; // the run is being stopped
		static private final int START         = 1; // indicating the next thing is the size
		static private final int WAIT_CONNECT  = 2; // waiting for connection
		static private final int STARTED       = 3; // indicating there is a message
		static private final int SIZED         = 4; // indicating the next thing is a checksum
		static private final int CHECKED       = 5; // indicating the bytes are being read
		static private final int DELIVER       = 6; // indicating the message has been read

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
		 * This is done via a simple value machine.
		 * If the checksum doesn't match the connection is dropped and restarted.
		 * 
		 * Once the message has been read it is passed off to...
		 */
		@Override
		public void run() { 
			logger.trace("Thread <{}>ReceiverThread::run", Thread.currentThread().getId());
			Looper.prepare();

			state = WAIT_CONNECT;

			int bytesToRead = 0; // indicates how many bytes should be read
			int bytesRead = 0;   // indicates how many bytes have been read
			long checksum = 0;

			byte[] message = null;
			byte[] byteToReadBuffer = new byte[Integer.SIZE/Byte.SIZE];
			byte[] checksumBuffer = new byte[Long.SIZE/Byte.SIZE];
			BufferedInputStream bis = null;
			long version = Long.MAX_VALUE;
			
			while (true) {
				switch (state) {
				case WAIT_CONNECT:  // look for the size
					synchronized (parent.connectorThread.state) {
						while (! parent.connectorThread.isConnected() ) {
							try {
								logger.trace("Thread <{}>ReceiverThread::value.wait", 
										Thread.currentThread().getId());

								parent.connectorThread.state.wait();
							} catch (InterruptedException ex) {
								logger.warn("thread interupted {}",ex.getLocalizedMessage());
								shutdown(bis); // looks like the thread is being shut down.
								return;
							}
						}
						version = parent.connectorThread.getVersion();
					}

					try {
						InputStream insock = this.parent.socket.getInputStream();
						bis = new BufferedInputStream(insock, 1024);
					} catch (IOException ex) {
						logger.error("could not open input stream on socket {}", ex.getLocalizedMessage());
						parent.connectorThread.failure(version);
						continue;
					}    
					if (bis == null) continue;
					
				case START:
					try {
						bis.read(byteToReadBuffer);
					} catch (SocketTimeoutException ex) {
						// logger.trace("timeout on socket");
						continue;
					} catch (IOException e) {
						logger.error("read error  ...");
						parent.connectorThread.failure(version);
						this.state = WAIT_CONNECT;
						break; // read error - set our value back to wait for connect
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
					try {
						bis.read(checksumBuffer, 0, 4);
					} catch (SocketTimeoutException ex) {
						logger.trace("timeout on socket");
						continue;
					} catch (IOException e) {
						logger.trace("read error on socket");
						parent.connectorThread.failure(version);
						this.state = WAIT_CONNECT;
						break;
					}
					ByteBuffer bbuf = ByteBuffer.wrap(checksumBuffer);
					bbuf.order(this.parent.endian);
					checksum =  bbuf.getLong();
					
					message = new byte[bytesToRead];

					logger.info(checksumBuffer.toString(), checksum);
					bytesRead = 0;
					this.state = CHECKED;
				} 
				break;
				case CHECKED: // read the message
					while (bytesRead < bytesToRead) {
						try {
							int temp = bis.read(message, 0, bytesToRead - bytesRead);
							bytesRead += (temp >= 0) ? temp : 0;
						} catch (SocketTimeoutException ex) {
							logger.trace("timeout on socket");
							continue;
						} catch (IOException ex) {
							logger.trace("read error on socket");
							this.state = WAIT_CONNECT;
							parent.connectorThread.failure(version);
							break;
						}
					}
					if (bytesRead < bytesToRead)
						break;
					this.state = DELIVER;
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

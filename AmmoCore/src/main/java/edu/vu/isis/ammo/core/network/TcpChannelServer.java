/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.vu.isis.ammo.core.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ServerSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.PLogger;

/**
 * Two long running threads and one short. The long threads are for sending and
 * receiving messages. The short thread is to connect the socket. The sent
 * messages are placed into a queue if the socket is connected.
 * 
 */
public class TcpChannelServer extends TcpChannelAbstract {

	// ===========================================================
	// Constants
	// ===========================================================

	// a class based logger to be used by static methods ...
	private static final Logger classlogger = LoggerFactory
			.getLogger("net.server");

	// ===========================================================
	// Factory
	// ===========================================================
	public static TcpChannelServer getInstance(String name,
			IChannelManager iChannelManager) {
		classlogger.trace("Thread <{}>::getInstance", Thread.currentThread().getId());
		
		final TcpChannelServer instance = new TcpChannelServer(name, iChannelManager);
		return instance;
	}

	// ===========================================================
	// Members
	// ===========================================================

	private String serverHost = null;
	private int serverPort = -1;

	// Heartbeat-related members.
	private final long mHeartbeatInterval = 10 * 1000; // ms
	
	/**
	 * Construct a server the bulk of whose implementation is handled by the parent.
	 * 
	 * @param name
	 * @param iChannelManager
	 */
	private TcpChannelServer(String name, IChannelManager iChannelManager) {
		super(name, iChannelManager, "server");
		logger.trace("Thread <{}>TcpChannelServer::<constructor>", Thread
				.currentThread().getId());
	}

	public boolean setPort(int port) {
		logger.trace("Thread <{}>::setPort {}", Thread.currentThread().getId(),
				port);
		if (serverPort == port)
			return false;
		this.serverPort = port;
		this.reset();
		return true;
	}

	@Override
	public String toString() {
		return new StringBuilder().append("channel ").append(super.toString())
				.append("socket: host[").append(this.serverHost).append("] ")
				.append("port[").append(this.serverPort).append("]").toString();
	}

	@Override
	protected edu.vu.isis.ammo.core.network.TcpChannelAbstract.ConnectorThread newConnectorThread(
			TcpChannelAbstract parent) {
		if (!(parent instanceof TcpChannelServer)) {
			logger.error("not really parent");
			return null;
		}
		final TcpChannelServer realParent = (TcpChannelServer) parent;
		return new ConnectorThread(realParent);
	}

	/**
	 * manages the connection. enable or disable expresses the operator intent.
	 * There is no reason to run the thread unless the channel is enabled.
	 * 
	 * Any of the properties of the channel
	 * 
	 */
	private class ConnectorThread extends TcpChannelAbstract.ConnectorThread {

		private final int DEFAULT_SERVER_PORT = INetPrefKeys.DEFAULT_SERVER_PORT;
		private ServerSocket server;

		private final TcpChannelServer realParent;

		private ConnectorThread(TcpChannelServer parent) {
			super(parent, "server");
			this.realParent = parent;
		}

		/**
		 * A value machine based. Most of the time this machine will be in a
		 * CONNECTED value. In that CONNECTED value the machine wait for the
		 * connection value to change or for an interrupt indicating that the
		 * thread is being shut down.
		 * 
		 * The value machine takes care of the following constraints: We don't
		 * need to reconnect unless. 1) the connection has been lost 2) the
		 * connection has been marked stale 3) the connection is enabled. 4) an
		 * explicit reconnection was requested
		 * 
		 * @return
		 */
		@Override
		public void run() {

			// start the server and wait for a client connect
			logger.info("Thread <{}>ConnectorThread::run", Thread
					.currentThread().getId());
			for (;;) {
				logger.trace("channel goal {}",
						(parent.shouldBeEnabled) ? "enable" : "disable");
				if (!parent.shouldBeEnabled) {
					logger.trace("disabling channel...");
					state.set(NetChannel.DISABLED);
				}

				// if disabled, wait till we're enabled again
				synchronized (state) {
					logger.trace("channel is {}", state.get());
					while (state.get() == DISABLED) {
						try {
							state.wait(BURP_TIME);
							logger.trace("burp {}", state);
						} catch (InterruptedException ex) {
							logger.trace("interrupting channel wait.");
						}
					}
				}

				logger.trace("set to disconnected state");
				state.setUnlessDisabled(NetChannel.DISCONNECTED);
				final int port = (realParent.serverPort > 10) ? realParent.serverPort
						: DEFAULT_SERVER_PORT;

				try {
					// open the server socket
					server = ServerSocketFactory.getDefault()
							.createServerSocket(port);
					logger.info("Opened server socket {}",
							server.getLocalSocketAddress());

					// got a socket, wait for a client connection
					while (server != null && !server.isClosed()) {
						Socket client = null;
						try {
							state.setUnlessDisabled(NetChannel.WAIT_CONNECT);
							logger.info("Awaiting client connection...");
							client = server.accept();
							state.setUnlessDisabled(NetChannel.CONNECTING);

							logger.info("Received client connection, send GTG...");
							client.getOutputStream().write(1);

							logger.info("Prepare socket and threads");
							initSocket(client);

							// I think we're good
							state.setUnlessDisabled(NetChannel.CONNECTED);

							// while the socket is good, send a heartbeat
							while (!client.isClosed()) {
								int s = state.get();
								if (s == DISABLED || s == STALE) {
									logger.info("dropped connection {}", s);
									disconnect();
								} else {
									sendHeartbeatIfNeeded();
									synchronized (state) {
										state.wait(mHeartbeatInterval);
									}
								}
							}

						} catch (Exception e) {
							logger.error(
									"Failed to handle client connection on {}",
									port, e);
							state.setUnlessDisabled(NetChannel.DISCONNECTED);
							if (client != null)
								try {
									client.close();
								} catch (Exception ignored) {
								}
						}
					}

				} catch (Exception e) {
					logger.error("Failed to open server socket on {}", port, e);
					state.setUnlessDisabled(NetChannel.DISCONNECTED);
					if (server != null)
						try {
							server.close();
						} catch (Exception ignored) {
						}
				}
			}

		}

		private void initSocket(Socket socket) throws Exception {
			logger.trace("Thread <{}>ConnectorThread::connect", Thread
					.currentThread().getId());

			if (parent.mSocket != null) {
				logger.error("Tried to create mSocket when we already had one.");
				parent.mSocket.close();
			}

			final long startConnectionMark = System.currentTimeMillis();
			parent.mSocket = socket;

			parent.mSocket.setSoTimeout(parent.socketTimeout);
			final long finishConnectionMark = System.currentTimeMillis();
			logger.info("connection time to establish={} ms",
					finishConnectionMark - startConnectionMark);

			parent.mDataInputStream = new DataInputStream(
					parent.mSocket.getInputStream());
			parent.mDataOutputStream = new DataOutputStream(
					parent.mSocket.getOutputStream());

			parent.mSocket.setSoTimeout(parent.socketTimeout);
			logger.info("connection time to establish={} ms",
					finishConnectionMark - startConnectionMark);

			parent.mDataInputStream = new DataInputStream(
					parent.mSocket.getInputStream());
			parent.mDataOutputStream = new DataOutputStream(
					parent.mSocket.getOutputStream());

			logger.info("connection established to {}",
					socket.getLocalSocketAddress());

			makeThreads();
		}

		/**
		 * Close the server socket.
		 */
		@Override
		protected boolean disconnect() {
			super.disconnect();

			if (server != null) {
				logger.debug("Closing socket...");
				try {
					server.close();
				} catch (Exception e) {
					logger.error("Failed to close socket", e);
				}
				server = null;
			}
			return true;
		}

	}

	@Override
	public void toLog(String context) {
		PLogger.SET_PANTHR_GW.debug(" {}:{} timeout={} sec", new Object[] {
				serverHost, serverPort, flatLineTime });
	}
}

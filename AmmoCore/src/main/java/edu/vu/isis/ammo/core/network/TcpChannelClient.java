/**
Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
 */
package edu.vu.isis.ammo.core.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import edu.vu.isis.ammo.core.PLogger;

/**
 * Two long running threads and one short. The long threads are for sending and
 * receiving messages. The short thread is to connect the socket. The sent
 * messages are placed into a queue if the socket is connected.
 * 
 */
public class TcpChannelClient extends TcpChannelAbstract {

	// ===========================================================
	// Constants
	// ===========================================================

	// a class based logger to be used by static methods ...
	private static final Logger classlogger = LoggerFactory.getLogger("net.server");

	// ===========================================================
	// Factory
	// ===========================================================
	public static TcpChannelClient getInstance(String name, IChannelManager iChannelManager) {
		classlogger.trace("Thread <{}>::getInstance", Thread.currentThread()
				.getId());
		final TcpChannelClient instance = new TcpChannelClient(name,
				iChannelManager);
		return instance;
	}

	// ===========================================================
	// Members
	// ===========================================================

	private String gatewayHost = null;
	private int gatewayPort = -1;

	private TcpChannelClient(String name, IChannelManager iChannelManager) {
		super(name, iChannelManager, "client");
		logger.trace("Thread <{}>TcpChannel::<constructor>", Thread
				.currentThread().getId());
	}

	public boolean setConnectTimeout(int value) {
		logger.trace("Thread <{}>::setConnectTimeout {}", Thread
				.currentThread().getId(), value);
		this.connectTimeout = value;
		return true;
	}

	public void setFlatLineTime(long flatLineTime) {
		// this.flatLineTime = flatLineTime; // currently broken
	}

	/**
	 * If the host is being set to its current value the return false
	 * indicating it was not changed.
	 * 
	 * @param host
	 * @return
	 */
	public boolean setHost(String host) {
		logger.trace("Thread <{}>::setHost {}", 
				Thread.currentThread().getId(),
				host);
		if (this.gatewayHost != null && this.gatewayHost.equals(host)) {
			return false;
		}
		this.gatewayHost = host;
		this.reset();
		return true;
	}

	/**
	 * If the port is being set to its current value the return false
	 * indicating it was not changed.
	 * 
	 * @param host
	 * @return
	 */
	public boolean setPort(int port) {
		logger.trace("Thread <{}>::setPort {}", 
				Thread.currentThread().getId(),
				port);
		if (gatewayPort == port) {
			return false;
		}
		this.gatewayPort = port;
		this.reset();
		return true;
	}

	@Override
	public String toString() {
		return new StringBuilder().append("channel ").append(super.toString())
				.append("socket: host[").append(this.gatewayHost).append("] ")
				.append("port[").append(this.gatewayPort).append("]")
				.toString();
	}

	@Override
	protected edu.vu.isis.ammo.core.network.TcpChannelAbstract.ConnectorThread newConnectorThread(
			TcpChannelAbstract parent) {
		if (!(parent instanceof TcpChannelClient)) {
			classlogger.error("not really parent");
			return null;
		}
		final TcpChannelClient realParent = (TcpChannelClient) parent;
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

		private final String DEFAULT_CLIENT_HOST = "192.168.1.100";
		private final int DEFAULT_CLIENT_PORT = 33289;

		private final TcpChannelClient realParent;

		private ConnectorThread(TcpChannelClient parent) {
			super(parent, "client");
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
			try {
				logger.info("Thread <{}>ConnectorThread::run", 
						Thread.currentThread().getId());
				
				MAINTAIN_CONNECTION: while (true) {
					logger.trace("connector state: {}", this.showState());

					if (! this.parent.shouldBeEnabled) {
						this.state.set(NetChannel.DISABLED);
					}
					switch (this.state.get()) {
					case NetChannel.DISABLED:
						try {
							synchronized (this.state) {
								logger.trace("this.state.get() = {}",
										this.state.get());
								this.parent.statusChange();
								disconnect();

								// Wait for a link interface.
								while (this.state.isDisabled()) {
									logger.trace("Looping in Disabled");
									this.state.wait(BURP_TIME);
								}
							}
						} catch (InterruptedException ex) {
							logger.warn("connection intentionally disabled {}",
									this.state);
							this.state.setUnlessDisabled(NetChannel.STALE);
							break MAINTAIN_CONNECTION;
						}
						break;
					case NetChannel.STALE:
						disconnect();
						this.state.setUnlessDisabled(NetChannel.LINK_WAIT);
						break;

					case NetChannel.LINK_WAIT:
						this.parent.statusChange();
						try {
							synchronized (this.state) {
								while (!parent.isAnyLinkUp()
										&& !this.state.isDisabled()) {
									this.state.wait(BURP_TIME); // wait for a
																// link
																// interface
								}
								this.state
										.setUnlessDisabled(NetChannel.DISCONNECTED);
							}
						} catch (InterruptedException ex) {
							logger.warn("connection intentionally disabled {}",
									this.state);
							this.state.setUnlessDisabled(NetChannel.STALE);
							break MAINTAIN_CONNECTION;
						}
						this.parent.statusChange();
						// or else wait for link to come up, triggered through
						// broadcast receiver
						break;

					case NetChannel.DISCONNECTED:
						this.parent.statusChange();
						if (!this.connect()) {
							this.state.setUnlessDisabled(NetChannel.CONNECTING);
						} else {
							this.state.setUnlessDisabled(NetChannel.CONNECTED);
						}
						break;

					case NetChannel.CONNECTING: // keep trying
						try {
							this.parent.statusChange();
							long attempt = this.getAttempt();
							synchronized (this.state) {
								this.state
										.wait(NetChannel.CONNECTION_RETRY_DELAY);
								if (this.connect()) {
									this.state
											.setUnlessDisabled(NetChannel.CONNECTED);
								} else {
									this.state.failureUnlessDisabled(attempt);
								}
							}
							this.parent.statusChange();
						} catch (InterruptedException ex) {
							logger.trace("sleep interrupted - intentional disable, exiting thread ...");
							this.reset();
							break MAINTAIN_CONNECTION;
						}
						break;

					case NetChannel.CONNECTED: {
						this.parent.statusChange();
						try {
							synchronized (this.state) {
								while (this.isConnected()) {
									if (HEARTBEAT_ENABLED)
										parent.sendHeartbeatIfNeeded();

									// wait for somebody to change the
									// connection status
									this.state.wait(BURP_TIME);

									if (HEARTBEAT_ENABLED
											&& parent.hasWatchdogExpired()) {
										logger.warn("Watchdog timer expired!!");
										this.state
												.failureUnlessDisabled(getAttempt());
									}
								}
							}
						} catch (InterruptedException ex) {
							logger.warn("connection intentionally disabled {}",
									this.state);
							this.state.setUnlessDisabled(NetChannel.STALE);
							break MAINTAIN_CONNECTION;
						}
						this.parent.statusChange();
					}
						break;

					default:
						try {
							long attempt = this.getAttempt();
							this.parent.statusChange();
							synchronized (this.state) {
								this.state
										.wait(NetChannel.CONNECTION_RETRY_DELAY);
								this.state.failureUnlessDisabled(attempt);
							}
							this.parent.statusChange();
						} catch (InterruptedException ex) {
							logger.trace("sleep interrupted - intentional disable, exiting thread ...");
							this.reset();
							break MAINTAIN_CONNECTION;
						}
					}
				}

			} catch (Exception ex) {
				this.state.setUnlessDisabled(NetChannel.EXCEPTION);
				logger.error("channel exception", ex);
			}
			try {
				if (this.parent.mSocket == null) {
					logger.error("channel closing without active socket}");
					return;
				}
				this.parent.mSocket.close();
			} catch (IOException ex) {
				logger.error("channel closing without proper socket", ex);
			}
			logger.error("channel closing");
		}

		private boolean connect() {
			logger.trace("Thread <{}>ConnectorThread::connect", Thread
					.currentThread().getId());

			// Resolve the hostname to an IP address.
			String host = (realParent.gatewayHost != null) ? realParent.gatewayHost
					: DEFAULT_CLIENT_HOST;
			int port = (realParent.gatewayPort > 10) ? realParent.gatewayPort
					: DEFAULT_CLIENT_PORT;
			InetAddress ipaddr = null;
			try {
				ipaddr = InetAddress.getByName(host);
			} catch (UnknownHostException e) {
				logger.warn("could not resolve host name");
				return false;
			}

			// Create the Socket
			final InetSocketAddress sockAddr = new InetSocketAddress(ipaddr,
					port);
			try {
				if (parent.mSocket != null)
					logger.error("Tried to create mSocket when we already had one.");

				final long startConnectionMark = System.currentTimeMillis();
				parent.mSocket = new Socket();
				parent.mSocket.connect(sockAddr, parent.connectTimeout);

				parent.mSocket.setSoTimeout(parent.socketTimeout);
				final long finishConnectionMark = System.currentTimeMillis();
				logger.info("connection time to establish={} ms",
						finishConnectionMark - startConnectionMark);

				parent.mDataInputStream = new DataInputStream(
						parent.mSocket.getInputStream());
				parent.mDataOutputStream = new DataOutputStream(
						parent.mSocket.getOutputStream());

			} catch (AsynchronousCloseException ex) {
				logger.warn("connection async close failure to {}:{} ", ipaddr,
						port, ex);
				parent.mSocket = null;
				return false;
			} catch (ClosedChannelException ex) {
				logger.info("connection closed channel failure to {}:{} ",
						ipaddr, port, ex);
				parent.mSocket = null;
				return false;
			} catch (ConnectException ex) {
				logger.info("connection failed to {}:{}", ipaddr, port, ex);
				parent.mSocket = null;
				return false;
			} catch (SocketException ex) {
				logger.warn("connection timeout={} sec, socket {}:{}",
						parent.connectTimeout / 1000, ipaddr, port, ex);
				parent.mSocket = null;
				return false;
			} catch (Exception ex) {
				logger.warn("connection failed to {}:{}", ipaddr, port, ex);
				parent.mSocket = null;
				return false;
			}

			logger.info("connection established to {}:{}", ipaddr, port);

			makeThreads();

			return true;
		}
	}

	@Override
	public void toLog(String context) {
		PLogger.SET_PANTHR_GW.debug(" {}:{} timeout={} sec", gatewayHost,
				gatewayPort, flatLineTime);
	}

}

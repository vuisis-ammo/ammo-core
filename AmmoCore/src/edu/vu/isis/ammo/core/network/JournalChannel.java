/**
 * 
 */
package edu.vu.isis.ammo.core.network;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.AsyncTask;
import android.os.Environment;

/**
 * Two long running threads and one short.
 * The long threads are for sending and recieving messages.
 * The short thread is to connect the socket.
 * The sent messages are placed into a queue if the socket is connected.
 * 
 * @author phreed
 *
 */
public class JournalChannel extends NetChannel {
	private static final Logger logger = LoggerFactory.getLogger(JournalChannel.class);
	
	private BlockingQueue<GwMessage> sendQueue = new LinkedBlockingQueue<GwMessage>(20);
	
	private boolean isStale;
	private boolean isEnabled;
	
	public static final File journalDir = new File(Environment.getExternalStorageDirectory(), "ammo_core");
	public File journalFile = new File(journalDir, "network_proxy_service.journal");

	private BufferedOutputStream ostream = null;
	private ConnectorTask connectorTask;
	private SenderThread senderThread;
	
	private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
	private final Object syncObj;
	private static Boolean isConnected = false;  // condition variable
	
	// SDCARD Fields
	private boolean journalingSwitch = true;
	public boolean isJournaling() { return journalingSwitch; }
	public void setJournalSwitch(boolean value) { journalingSwitch = value; }
	public boolean getJournalSwitch() { return journalingSwitch; }
	public boolean toggleJournalSwitch() { return journalingSwitch = journalingSwitch ? false : true; }
	
	
	private JournalChannel(NetworkService driver) {
		super();
		logger.trace("::<constructor>");
		this.syncObj = this;
		this.isStale = true;
		this.isEnabled = false;
		JournalChannel.isConnected = false;
	}
	
	public static JournalChannel getInstance(NetworkService driver) {
		logger.trace("::getInstance");
		synchronized (JournalChannel.isConnected) {
			JournalChannel instance = new JournalChannel(driver);
			
			instance.senderThread = instance.new SenderThread(instance);
			return instance;
		}
	}
	
	/**
	 * The journal used when direct communication with the 
	 * ammo android gateway plugin is not immediately available.
	 * The journal is a file containing the PushRequests (not RetrievalRequest's or *Response's).
	 */
	public void setupJournal() {
		logger.trace("::setupJournal");
		if (!journalingSwitch) return;
		if (ostream != null) return;
		try {
			if (! journalDir.exists()) { journalDir.mkdirs(); }
			FileOutputStream fos = new FileOutputStream(journalFile.getCanonicalPath(), true);
			ostream = new BufferedOutputStream(fos);
		} catch (IOException e) {
			e.printStackTrace();
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
	
	public String toString() {
		return "journal: ["+this.isEnabled+"]";
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
		synchronized (JournalChannel.isConnected) {
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

	private class ConnectorTask extends AsyncTask<JournalChannel, Void, JournalChannel> {
	    /** The system calls this to perform work in a worker thread and
	      * delivers it the parameters given to AsyncTask.execute() */
	    protected JournalChannel doInBackground(JournalChannel... parentSet) {
	    	logger.trace("::reconnect");
	    	if (parentSet.length < 1) return null;
	    	
			JournalChannel parent = parentSet[0];
			// open the journal file
			return parent;
	    }
	    
	    /** The system calls this to perform work in the UI thread and delivers
	      * the result from doInBackground() */
	    protected void onPostExecute(JournalChannel parent) {
	    	parent.isStale = false;
			synchronized (JournalChannel.isConnected) {
				JournalChannel.isConnected = true;
				try {
					JournalChannel.isConnected.notifyAll();
				} catch (IllegalMonitorStateException ex) {
					logger.warn("connection made notification but no one is waiting");
				}
			}
	    }
	}
	
	public boolean checkConnection() {
		logger.trace("::isConnected");
		if (!JournalChannel.isConnected) return false;
		if (this.ostream == null) return false;
		return true;
	}
	
	/**
	 * Close the socket.
	 * @return whether the socket was closed.
	 *         false may simply indicate that the socket was already closed.
	 */
	public boolean close() {
		synchronized (this.syncObj) {
			logger.trace("::close");
			if (this.ostream == null) return false;
			try {
				this.ostream.close();
				return true;
			} catch (IOException e) {
				logger.warn("could not close socket");
			}
			return false;
		}
	}
	
	public boolean hasSocket() {
		logger.trace("::hasSocket");
		if (this.ostream == null) return false;
		return true;
	}
	
    public boolean disconnect() {
    	synchronized (this.syncObj) {
    		logger.trace("::disconnect");
			this.senderThread.interrupt();
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
	@Override
	public boolean sendRequest(NetChannel.GwMessage msg) {
		synchronized (this.syncObj) {
			logger.trace("::sendGatewayRequest");
			if (! JournalChannel.isConnected) {
				this.tryConnect(false);
				return false;
			}
			try {
				this.sendQueue.put(msg);
			} catch (InterruptedException ex) {
				ex.getStackTrace();
				return false;
			}
			return true;
		}
	}
	
	/**
	 * A thread for receiving incoming messages on the tcp socket.
	 * The main method is run().
	 *
	 */
	public class SenderThread extends Thread {
		private JournalChannel parent;
		private final BlockingQueue<GwMessage> queue;
		
		private SenderThread(JournalChannel parent) {
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
			BufferedOutputStream dos;
			try {		
				dos = JournalChannel.this.ostream;
				ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE/Byte.SIZE + Integer.SIZE/Byte.SIZE);
				// ByteOrder order = buf.order();
				buf.order(parent.endian);
				
				while (true) {
					synchronized(JournalChannel.isConnected) {
						while (!JournalChannel.isConnected) {
							try {
								JournalChannel.isConnected.wait();
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

	// The following methods are stubbed out to get things to compile.
	// JournalChannel is not currently used, but if we ever need to
	// get it working again, we will need to implement all of the
	// methods in the INetChannel interface.
	public String showState(int state) { return ""; }

	public boolean isConnected() { return false; }
	public boolean setConnectTimeout(int value) { return false; }
	public boolean setSocketTimeout(int value) { return false; }
	public void setFlatLineTime(long flatLineTime) {}
	public boolean setHost(String host) { return false; }
	public boolean setPort(int port) { return false; }

	public void linkUp() {}
	public void linkDown() {}
	public void reset() {}
	public String getLocalIpAddress() { return ""; }
	

}

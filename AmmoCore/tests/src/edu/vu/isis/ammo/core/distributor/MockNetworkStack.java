
package edu.vu.isis.ammo.core.distributor;

import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class stands in place of the various mechanisms used by Channels
 * generally. The network is emulated by queues of ByteBuffer objects.
 */
public class MockNetworkStack extends Socket {
    private static final Logger logger = LoggerFactory.getLogger("trial.net.mock");
    /**
     * Use these queues to imitate the network stack
     */
    final private BlockingQueue<ByteBuffer> input;
    final private BlockingQueue<ByteBuffer> output;

    final public AtomicBoolean throwClosedChannelException;
    final public AtomicBoolean throwInterruptedException;
    final public AtomicBoolean throwSocketException;
    final public AtomicBoolean throwException;

    public MockNetworkStack() {
        this.input = new LinkedBlockingQueue<ByteBuffer>();
        this.output = new LinkedBlockingQueue<ByteBuffer>();

        this.throwInterruptedException = new AtomicBoolean(false);
        this.throwClosedChannelException = new AtomicBoolean(false);
        this.throwSocketException = new AtomicBoolean(false);
        this.throwException = new AtomicBoolean(false);
    }

    /**
     * This method works in conjunction with receive().
     */
    public void putReceivable(ByteBuffer buf) {
        buf.flip();
        this.input.offer(buf);
    }

    /**
     * This method is called by the MockChannel. It returns the next item in the
     * Mock Network.
     * 
     * @return
     * @throws InterruptedException
     * @throws ClosedChannelException
     * @throws Exception
     */
    public ByteBuffer receive() throws InterruptedException, ClosedChannelException, Exception {
        if (this.throwInterruptedException.get()) {
            throw new InterruptedException();
        }
        if (this.throwClosedChannelException.get()) {
            throw new ClosedChannelException();
        }
        if (this.throwException.get()) {
            throw new Exception("mock socket exception");
        }
        return this.output.take();
    }

    /**
     * This method works in conjunction with send().
     */
    public ByteBuffer getSent() {
        try {
            return this.input.take();
        } catch (InterruptedException ex) {
            logger.error("unsendable ", ex);
            return null;
        }
    }

    public void send(ByteBuffer buf) throws SocketException, Exception {
        if (this.throwSocketException.get()) {
            throw new SocketException("mock socket exception");
        }
        if (this.throwException.get()) {
            throw new Exception("mock socket exception");
        }
        buf.flip();
        this.input.offer(buf);
    }

    private final static Charset charset = Charset.forName("UTF-8");
    @SuppressWarnings("unused")
    private final static CharsetEncoder encoder = charset.newEncoder();
    private final static CharsetDecoder decoder = charset.newDecoder();

    /**
     * Decode the byte buffer into a string.
     * 
     * @param buf
     * @return
     */
    public static String asString(ByteBuffer buf) {
        final int pos = buf.position();
        decoder.reset();
        final CharBuffer cbuf = CharBuffer.allocate(buf.remaining());
        decoder.decode(buf, cbuf, true);
        decoder.flush(cbuf);
        final String result = cbuf.toString();
        buf.position(pos);
        return result;
    }

}

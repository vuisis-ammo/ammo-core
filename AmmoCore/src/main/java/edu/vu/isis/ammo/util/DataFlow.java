
package edu.vu.isis.ammo.util;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.net.Uri;

/**
 * This simple Future which can only be bound to a value once.
 */
public class DataFlow<V> implements Future<V> {
    final static private Logger logger = LoggerFactory.getLogger("util.dataflow");
    
    final AtomicReference<V> value;

    public DataFlow() {
        this.value = new AtomicReference<V>();
    }

    public DataFlow(V value) {
        this();
        this.value.set(value);
    }

    /**
     * The bind can only happen once.
     * 
     * @param value
     * @return
     */
    public DataFlow<V> bind(final V value) {
        if (this.value.compareAndSet(null, value)) {
            // this.notifyAll();
        } else {
            logger.warn("attempt to rebind <{}> <{}>", this.value.get(), value);
        }
        return this;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        synchronized (this) {
            if (this.value.get() == null) {
                this.wait();
            }
        }
        return this.value.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        synchronized (this) {
            if (this.value.get() == null) {
                this.wait(unit.toMillis(timeout));
            }
        }
        return this.value.get();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return this.value.get() != null;
    }

    /**
     * All serializer methods return this dataflow variable.
     */
    public static class ByteBufferFuture extends DataFlow<ByteBuffer> {

        /**
         * Use this factory when the payload is not empty but is 
         * as yet unknown.
         * 
         * @return
         */
        public static ByteBufferFuture getUnboundInstance() {
            return new ByteBufferFuture();
        }
        private ByteBufferFuture() {
            super();
        }
        /**
         * Use this factory when it is known that the 
         * payload is empty.
         * 
         * @return
         */
        public static ByteBufferFuture getEmptyInstance() {
            final byte[] empty = new byte[0];
            return new ByteBufferFuture(ByteBuffer.wrap(empty));
        }

        private ByteBufferFuture(ByteBuffer value) {
            super(value);
        }

        /**
         * Use this factory when the payload is known and non-empty.
         * @param value
         * @return
         */
        public static ByteBufferFuture wrap(byte[] value) {
            return new ByteBufferFuture(ByteBuffer.wrap(value));
        }
        
        /**
         * Use this method to bind a value to a previously unknown
         * and unbound payload value.
         * 
         * @param value
         * @return
         */
        public ByteBufferFuture bind(byte[] value) {
            this.bind(ByteBuffer.wrap(value));
            return this;
        }
    }

    /**
     * All deserializers return this dataflow variable
     */
    public static class UriFuture extends DataFlow<Uri> {

        public UriFuture() {
            super();
        }

        public UriFuture(Uri tupleUri) {
            super(tupleUri);
        }

    }

}

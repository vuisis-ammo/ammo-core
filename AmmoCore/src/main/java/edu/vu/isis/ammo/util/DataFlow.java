
package edu.vu.isis.ammo.util;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import android.net.Uri;

/**
 * This simple Future which can only be bound to a value once.
 */
public class DataFlow<V> implements Future<V> {
    final AtomicReference<V> value;

    public DataFlow() {
        this.value = new AtomicReference<V>();
    }

    public DataFlow(V value) {
        this();
        this.value.set(value);
    }

    public DataFlow<V> bind(final V value) {
        if (!this.value.compareAndSet(null, value))
            return this;
        this.notifyAll();
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

        public static ByteBufferFuture getEmptyInstance() {
            final byte[] empty = new byte[0];
            return new ByteBufferFuture(ByteBuffer.wrap(empty));
        }

        public ByteBufferFuture(ByteBuffer value) {
            super(value);
        }

        public static ByteBufferFuture wrap(byte[] value) {
            return new ByteBufferFuture(ByteBuffer.wrap(value));
        }
        
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

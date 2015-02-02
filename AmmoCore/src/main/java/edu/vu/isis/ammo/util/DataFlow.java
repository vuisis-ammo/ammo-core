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

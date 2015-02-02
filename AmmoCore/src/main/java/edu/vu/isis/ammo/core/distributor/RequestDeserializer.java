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


package edu.vu.isis.ammo.core.distributor;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Order;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.serializer.CustomAdaptorCache;

public class RequestDeserializer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("dist.deserializer");
 

    public final Item item;
    private static AtomicInteger masterSequence = new AtomicInteger(0);
    private final Builder creator;

    public static Builder newBuilder(final DistributorThread distributorThread,
            final CustomAdaptorCache ammoAdaptorCache) {
        return new Builder(distributorThread, ammoAdaptorCache);
    }
    
    public static class Builder {
        public final CustomAdaptorCache ammoAdaptorCache;
        public final DistributorThread distributor;
        
        private Builder(final DistributorThread distributor, final CustomAdaptorCache ammoAdaptorCache) {
            this.distributor = distributor;
            this.ammoAdaptorCache = ammoAdaptorCache;
        }
        
        public RequestDeserializer build(final Item item) {
            return new RequestDeserializer(this, item);
        }
        
        public RequestDeserializer toProvider(
                final int priority, final Context context, final String channelName,
                final Uri provider, final Encoding encoding, final byte[] data) 
        {
            return new RequestDeserializer(this, 
                    new Item(priority, DeserializerOperation.TO_PROVIDER, 
                    context, channelName, provider, encoding, "", data));
        }
        
    }
    public enum DeserializerOperation {
        TO_PROVIDER,
        TO_REROUTE
    }

    public static class Item implements Comparable<Item> {
        final public int priority;
        final public int sequence;
        
        final public DeserializerOperation operation;

        final public Context context;
        final public String channelName;
        final public Uri provider;
        final public Encoding encoding;
        final public String mimeType;
        final public byte[] data;
        final public long timestamp;

        public Item(final int priority, final DeserializerOperation operation,
                final Context context, final String channelName,
                final Uri provider, final Encoding encoding, final String mimeType, final byte[] data)
        {
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
            this.sequence = masterSequence.getAndIncrement();
            
            this.operation = operation;

            this.context = context;
            this.channelName = channelName;
            this.provider = provider;
            this.encoding = encoding;
            this.mimeType = mimeType;
            this.data = data;
        }
        

        @Override
        public String toString() {
            return this.toString(new StringBuilder()).toString();
        }
        
        public StringBuilder toString(final StringBuilder sb) {
            sb.append("provider: ").append(this.provider).append('\n');
            sb.append("encoding: ").append(this.encoding).append('\n');
            sb.append("data: ").append(this.mimeType).append(" -> ").append(this.data).append('\n');
            return sb;
        }


        public Item alternateOperation(final DeserializerOperation altOperartion) {
            return new Item(this.priority, altOperartion, this.context, this.channelName, this.provider,
                    this.encoding, this.mimeType, this.data);
        }

        public static int compare(Item i1, Item i2) {
            logger.debug("compare msgs: priority=[{}:{}] sequence=[{}:{}]", 
                    i1.priority, i2.priority, 
                    i1.sequence, i2.sequence );
            if (i1.priority > i2.priority) return -1;
            if (i1.priority < i2.priority) return 1;

            // if priority is same then process in the insertion order (FIFO)
            if (i1.sequence > i2.sequence) return 1;
            if (i1.sequence < i2.sequence) return -1;
            return 0;
        }

        @Override
        public int compareTo(Item that) {
            return Item.compare(this, that);
        }

    }

    /**
    * Creates a new RequestDeserializerThread.
    *
    * @param distributor The distributor thread to be used for re-posting
    *                    messages to be forwarded.
    */
    private RequestDeserializer(final Builder creator, final Item item) {
        this.item = item;
        this.creator = creator;
    }

    /**
     * A functor to be used in cases such as PriorityQueue.
     * This gives a partial ordering, rather than total ordering of the natural order.
     * This overrides the default comparison of the AmmoGatewayMessage.
     *
     * The ordering is as follows:
     * priority : larger
     * sequence : smaller
     */
    public static class Prioritizer implements Comparator<Runnable> {
        @Override
        public int compare(Runnable o1, Runnable o2) {
            if (o1 == o2) return 0;
            if (!(o1 instanceof RequestDeserializer)) {
                throw new ClassCastException("could not cast to RequestDeserializer for comparison");
            }
            if (!(o2 instanceof RequestDeserializer)) {
                throw new ClassCastException("could not cast to RequestDeserializer for comparison");
            }
            final Item i1 = ((RequestDeserializer)o1).item;
            final Item i2 = ((RequestDeserializer)o2).item;
            return Item.compare(i1, i2);
        }
    }

    /**
     * proxy for the RequestSerializer deserializeToProvider method.
     * 
     * @param context
     * @param provider
     * @param encoding
     * @param data
     * @return
     */
 
    public RequestDeserializer toReroute() 
    {
        return new RequestDeserializer(this.creator, 
                this.item.alternateOperation(DeserializerOperation.TO_REROUTE));
    }

    
    @Override
    public void run() {
        // Process.THREAD_PRIORITY_FOREGROUND(-2) and THREAD_PRIORITY_DEFAULT(0) 
        Process.setThreadPriority( -7 );
        logger.info("deserializer thread start @prio: {}", Process.getThreadPriority( Process.myTid() ) );

        if(item.operation == DeserializerOperation.TO_PROVIDER) {
            try {
                logger.trace("deserialize received message <{}>", item);
                final Uri tupleUri = RequestSerializer.deserializeToProvider(
                        this.creator.ammoAdaptorCache,
                        item.context.getContentResolver(),
                        item.channelName, item.provider, item.encoding, item.data);
                
                logger.info("Ammo inserted received message in remote content provider=[{}], as [{}]", 
                        item.provider,  tupleUri);
       
            } catch (Exception ex) {
                logger.error("insert failed provider: [{}], remaining in insert queue [{}]", 
                        item.provider, ex);
            }
           
        } else if(item.operation == DeserializerOperation.TO_REROUTE) {
            final ContentValues cv = RequestSerializer
                    .deserializeToContentValues(item.data, item.encoding, item.mimeType, 
                            this.creator.distributor.contractStore);
            // Create a new request to forward this on to the distributor
            InternalRequestBuilder builder = new InternalRequestBuilder(null);
            builder.payload(cv);
            builder.topic(item.mimeType);
            builder.priority(item.priority);
            // should be the channel that we don't want to use
            builder.useChannel(item.channelName); 
            builder.expire(new edu.vu.isis.ammo.api.type.TimeInterval(1000));
            // first come, first serve for now; TODO: configure by policy
            builder.order(Order.OLDEST_FIRST); 
            final AmmoRequest postRequest;
            try {
                postRequest = (AmmoRequest) builder.post();
            } catch (RemoteException ex) {
                logger.error("could not post rerouted request", ex);
                return;
            }
            this.creator.distributor.distributeRequest(postRequest);
        } else {
            logger.error("item with unknown operation: [{}]", item);
        }
    }

}

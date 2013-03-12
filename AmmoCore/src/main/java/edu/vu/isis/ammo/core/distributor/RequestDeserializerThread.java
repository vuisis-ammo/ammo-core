package edu.vu.isis.ammo.core.distributor;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.type.Order;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.serializer.CustomAdaptorCache;

public class RequestDeserializerThread extends Thread {
    private static final Logger logger = LoggerFactory.getLogger("dist.deserializer");

    private final PriorityBlockingQueue<Item> queue;
    private AtomicInteger masterSequence;
    private final CustomAdaptorCache ammoAdaptorCache;
    
    private final DistributorThread distributor;
    
    public enum DeserializerOperation {
        TO_PROVIDER,
        TO_REROUTE
    }

    public class Item {
        final public int priority;
        final public int sequence;
        
        final public DeserializerOperation operation;

        final public Context context;
        final public String channelName;
        final public Uri provider;
        final public Encoding encoding;
        final public String mimeType;
        final public byte[] data;

        public Item(final int priority, final DeserializerOperation operation,
                final Context context, final String channelName,
                final Uri provider, final Encoding encoding, final String mimeType, final byte[] data)
        {
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
    }

    /**
    * Creates a new RequestDeserializerThread.
    *
    * @param distributor The distributor thread to be used for re-posting
    *                    messages to be forwarded.
    */
    public RequestDeserializerThread(DistributorThread distributor) {
        super(new StringBuilder("Serializer-").append(Thread.activeCount()).toString());
        this.masterSequence = new AtomicInteger(0);
        this.queue = new PriorityBlockingQueue<Item>(200, new PriorityOrder());
        this.distributor = distributor;
        this.ammoAdaptorCache = new CustomAdaptorCache(distributor.getContext());
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
    public static class PriorityOrder implements Comparator<Item> {
        @Override
        public int compare(Item o1, Item o2) {
            logger.debug("compare msgs: priority=[{}:{}] sequence=[{}:{}]", 
                    new Object[]{o1.priority, o2.priority, 
                    o1.sequence, o2.sequence} );
            if (o1.priority > o2.priority) return -1;
            if (o1.priority < o2.priority) return 1;

            // if priority is same then process in the insertion order
            if (o1.sequence > o2.sequence) return 1;
            if (o1.sequence < o2.sequence) return -1;
            return 0;
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
    public boolean toProvider(int priority, Context context, final String channelName,
            Uri provider, Encoding encoding, byte[] data) 
    {
        this.queue.offer(new Item(priority, DeserializerOperation.TO_PROVIDER, context, channelName, provider, encoding, "", data));
        return true;
    }
    
    public boolean toReroute(int priority, Context context, final String channelName,
            Encoding encoding, String mimeType, byte[] data) 
    {
        this.queue.offer(new Item(priority, DeserializerOperation.TO_REROUTE, context, channelName, null, encoding, mimeType, data));
        return true;
    }

    @Override
    public void run()
    {
        Process.setThreadPriority( -7 ); // Process.THREAD_PRIORITY_FOREGROUND(-2) and THREAD_PRIORITY_DEFAULT(0) 
        logger.info("deserializer thread start @prio: {}", Process.getThreadPriority( Process.myTid() ) );

        try {
            while (true) {

                final Item item = this.queue.take();

                if(item.operation == DeserializerOperation.TO_PROVIDER) {
                    try {

                        final Uri tupleUri = RequestSerializer.deserializeToProvider(
                                this.ammoAdaptorCache,
                                item.context.getContentResolver(),
                                item.channelName, item.provider, item.encoding, item.data);
                        logger.info("Ammo inserted received message in remote content provider=[{}], as [{}], remaining in insert queue [{}]", 
                                item.provider,  tupleUri, queue.size());
                        
                    } catch (Exception ex) {
                        logger.error("insert failed provider: [{}], remaining in insert queue [{}]", 
                                item.provider, queue.size(), ex);
                    }
                   
                } else if(item.operation == DeserializerOperation.TO_REROUTE) {
                    final ContentValues cv = RequestSerializer.deserializeToContentValues(item.data, item.encoding, item.mimeType, this.distributor.contractStore);
                    //Create a new request to forward this on to the distributor
                    InternalRequestBuilder builder = new InternalRequestBuilder(null);
                    builder.payload(cv);
                    builder.topic(item.mimeType);
                    builder.priority(item.priority);
                    builder.useChannel(item.channelName); //should be the channel that we don't want to use
                    builder.expire(new edu.vu.isis.ammo.api.type.TimeInterval(1000));
                    builder.order(Order.OLDEST_FIRST); //first come, first serve for now; TODO: configure by policy
                    AmmoRequest postRequest = (AmmoRequest) builder.post();
                    this.distributor.distributeRequest(postRequest);
                }
            }

        } catch (Exception ex) {
            logger.info("thread being stopped ex=[{}]", ex);
        }
    }
}

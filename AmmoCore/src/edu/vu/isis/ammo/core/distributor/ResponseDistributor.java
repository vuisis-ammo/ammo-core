
package edu.vu.isis.ammo.core.distributor;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.net.Uri;
import android.os.Process;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.util.AsyncQueryHelper;

public class ResponseDistributor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("dist.deserializer");
    private static final Logger tlogger = LoggerFactory.getLogger("test.queue.insert");

    private final PriorityBlockingQueue<Item> queue;
    private AtomicInteger masterSequence;

    public class Item {
        final public int priority;
        final public int sequence;

        final public Context context;
        final public String channelName;
        final public Uri provider;
        final public Encoding encoding;
        final public byte[] data;

        public Item(final int priority, final Context context, final String channelName,
                final Uri provider, final Encoding encoding, final byte[] data)
        {
            this.priority = priority;
            this.sequence = masterSequence.getAndIncrement();

            this.context = context;
            this.channelName = channelName;
            this.provider = provider;
            this.encoding = encoding;
            this.data = data;
        }
    }

    public ResponseDistributor() {
        this.masterSequence = new AtomicInteger(0);
        this.queue = new PriorityBlockingQueue<Item>(200, new PriorityOrder());
    }

    /**
     * A functor to be used in cases such as PriorityQueue. This gives a partial
     * ordering, rather than total ordering of the natural order. This overrides
     * the default comparison of the AmmoGatewayMessage. The ordering is as
     * follows: priority : larger sequence : smaller
     */
    public static class PriorityOrder implements Comparator<Item> {
        @Override
        public int compare(Item o1, Item o2) {
            logger.debug("compare msgs: priority=[{}:{}] sequence=[{}:{}]",
                    new Object[] {
                            o1.priority, o2.priority,
                            o1.sequence, o2.sequence
                    });
            if (o1.priority > o2.priority)
                return -1;
            if (o1.priority < o2.priority)
                return 1;

            // if priority is same then process in the insertion order
            if (o1.sequence > o2.sequence)
                return 1;
            if (o1.sequence < o2.sequence)
                return -1;
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
        this.queue.offer(new Item(priority, context, channelName, provider, encoding, data));
        return true;
    }

    @Override
    public void run()
    {
        /**
         * Process.THREAD_PRIORITY_FOREGROUND(-2) and THREAD_PRIORITY_DEFAULT(0)
         */
        Process.setThreadPriority(-7);
        logger.info("deserializer thread start @prio: {}",
                Process.getThreadPriority(Process.myTid()));

        try {
            while (true) {

                final Item item = this.queue.take();

                try {
                    RequestSerializer.deserializeToProvider(
                            new AsyncQueryHelper.InsertResultHandler() {
                                
                                @Override
                                public void run() {
                                    logger.info(
                                            "Ammo inserted received message in remote content provider=[{}] inserted in [{}], remaining in insert queue [{}]",
                                            new Object[] {
                                                    item.provider, this.resultTuple, queue.size()
                                            });
                                }
                            },
                            item.context, item.context.getContentResolver(),
                            item.channelName, item.provider, item.encoding, item.data);
                   
                    tlogger.info(PLogger.TEST_QUEUE_FORMAT, new Object[] {
                            System.currentTimeMillis(), "insert_queue", this.queue.size()
                    });

                } catch (Exception ex) {
                    logger.error("insert failed provider: [{}], remaining in insert queue [{}]",
                            new Object[] {
                                    item.provider, queue.size()
                            }, ex);
                }

            }

        } catch (Exception ex) {
            logger.info("thread being stopped ex=[{}]", ex);
        }
    }
}

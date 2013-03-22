/**
 * 
 */
package edu.vu.isis.ammo.core.distributor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.PLogger;

/**
 * An executor which places request responses into the appropriate place.
 * It does this by running RequestDeserializer runnable objects.
 *
 */
public class ResponseExecutor extends ThreadPoolExecutor {
    private static final Logger logger = LoggerFactory.getLogger("dist.resp.exec");
    private static final Logger tlogger = LoggerFactory.getLogger("test.queue.insert");
   
    private static final int N_THREADS = 4;

    public static ResponseExecutor newInstance(final DistributorThread parent) {
        final RequestDeserializer.Prioritizer prioritizer = new RequestDeserializer.Prioritizer();
        
        final PriorityBlockingQueue<Runnable> responseQueue = 
                new PriorityBlockingQueue<Runnable>(200, prioritizer);
        
        return new ResponseExecutor( parent, responseQueue );
    }
    
    private ResponseExecutor(final DistributorThread parent, final BlockingQueue<Runnable> responseQueue) {
        super(N_THREADS, N_THREADS,
                0L, TimeUnit.MILLISECONDS, responseQueue);
        
        /** Saturation Policy */
        this.setRejectedExecutionHandler( new ThreadPoolExecutor.CallerRunsPolicy() );
        
    }
    
    /**
     * Print the log message indicating the status of the command.
     * 
     * @parm runnable is the task which just completed.
     * @parm throwable is the exception (null on success) thrown during the running of the runnable.
     */
    @Override
    protected void afterExecute(Runnable command, Throwable throwable) {
        super.afterExecute(command, throwable);
        if (throwable != null) {
            logger.error("runnable failed", throwable);
            return;
        }
        if (!(command instanceof RequestDeserializer)) {
            logger.error("invalid runnable for response executor");
            return;
        }
        final RequestDeserializer responseDeserializer = (RequestDeserializer)command;
        final long currentTime = System.currentTimeMillis();
        tlogger.info(PLogger.TEST_QUEUE_FORMAT, 
                currentTime, 
                "insert_queue", 
                this.getQueue().size(),
                currentTime - responseDeserializer.item.timestamp);
       
    }

    @Override
    public void execute(Runnable command) {
        //super(new StringBuilder("Serializer-").append(Thread.activeCount()).toString());
        if (!(command instanceof RequestDeserializer)) {
            logger.error("unexpected command <{}>", command);
            return;
        }
        final RequestDeserializer responseDeserializer= (RequestDeserializer)command;
        super.execute(responseDeserializer);
    }
    
    

}

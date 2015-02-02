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
   
    private static final int N_THREADS = 1;

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

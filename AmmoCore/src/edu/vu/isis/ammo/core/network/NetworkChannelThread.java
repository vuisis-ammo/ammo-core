package edu.vu.isis.ammo.core.network;

import java.util.concurrent.PriorityBlockingQueue;

import android.os.AsyncTask;

/**
 * This is a producer consumer thread it takes messages from on
 * PriorityBlockingQueue and places them on another BlockingQueue.
 */
public class NetworkChannelThread extends AsyncTask<INetChannel, Integer, Void> {

    private PriorityBlockingQueue<NetworkService.Request> queue;

    public NetworkChannelThread(PriorityBlockingQueue<NetworkService.Request> queue) {
        this.queue = queue;
    }

    /**
     * The requests need to be transmogrified to work with the NetChannel.
     */
    @Override
    protected Void doInBackground(INetChannel... them) {
        while(true) {
            try {
                NetworkService.Request req = this.queue.take();
                for (INetChannel that : them) {
                    NetChannel.GwMessage msg =
                        new NetChannel.GwMessage(req.header.size, req.header.checksum,
                                                 req.builder.build().toByteArray(), req.handler);
                    that.sendRequest(msg);
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

}

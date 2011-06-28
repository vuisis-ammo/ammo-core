package edu.vu.isis.ammo.core.distributor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.PriorityBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import com.google.protobuf.ByteString;

import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.pb.AmmoMessages;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PushAcknowledgement;
import edu.vu.isis.ammo.core.provider.DistributorSchema.RetrievalTableSchema;
import edu.vu.isis.ammo.core.provider.DistributorSchema.SubscriptionTableSchema;

public class DistributorReceiverThread extends
	AsyncTask<DistributorService, Integer, Void> 
{
	private static final Logger logger = LoggerFactory.getLogger(DistributorSenderThread.class);
	
    private final PriorityBlockingQueue<NetworkService.Response> queue;
    
    public DistributorReceiverThread(PriorityBlockingQueue<NetworkService.Response> queue) {
    	super();
    	this.queue = queue;
    }
    
	@Override
	protected Void doInBackground(DistributorService... them) {
		NetworkService.Response response;
		
		try {
			while (null != (response = this.queue.take())) {
			  AmmoMessages.MessageWrapper mw = response.msg;
			 switch (mw.getType()) {

			    case DATA_MESSAGE:
			    	for (DistributorService that : them) 
			    		dispatchSubscribeResponse(mw, that);
			        break;

			    case PUSH_ACKNOWLEDGEMENT:
			    	for (DistributorService that : them) 
			    		dispatchPushResponse(mw, that);
			        break;

			    case PULL_RESPONSE:
			    	for (DistributorService that : them) 
			    		dispatchRetrievalResponse(mw, that);
			        break;
			    }
			}
		} catch (InterruptedException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		}
		return null;
	}
	
	 // ================================================
    // Calls originating from NetworkService
    // ================================================

    /**
     * Typically just an acknowledgment.
     * Get response to PushRequest from the gateway.
     * (PushResponse := PushAcknowledgement)
     *
     * @param mw
     * @return
     */
    private boolean dispatchPushResponse(AmmoMessages.MessageWrapper mw, DistributorService that) {
        logger.info("::receivePushResponse");

        if (mw == null) return false;
        if (! mw.hasPushAcknowledgement()) return false;
        PushAcknowledgement resp = mw.getPushAcknowledgement();

        return true;
    }

    /**
     * Get response to RetrievalRequest, PullResponse, from the gateway.
     *
     * @param mw
     * @return
     */
    private boolean dispatchRetrievalResponse(AmmoMessages.MessageWrapper mw, DistributorService that) {
        logger.info("::receivePullResponse");

        if (mw == null) return false;
        if (! mw.hasPullResponse()) return false;
        final AmmoMessages.PullResponse resp = mw.getPullResponse();

        String uriStr = resp.getRequestUid(); // resp.getUri(); --- why do we have uri in data message and retrieval response?
        final Uri uri = Uri.parse(uriStr);
        final ContentResolver cr = that.getContentResolver();
        
        try {
            Uri serialUri = Uri.withAppendedPath(uri, "_serial");
            OutputStream outstream = cr.openOutputStream(serialUri);

            if (outstream == null) {
                logger.error( "could not open output stream to content provider: {} ",serialUri);
                return false;
            }
            ByteString data = resp.getData();

            if (data != null) {
                outstream.write(data.toByteArray());
            }
            outstream.close();

            // This update/delete the retrieval request as it has been fulfilled.
            
            String selection = "\"" + RetrievalTableSchema.URI +"\" = '" + uri +"'";
            Cursor cursor = cr.query(RetrievalTableSchema.CONTENT_URI, null, selection, null, null);
            if (!cursor.moveToFirst()) {
                logger.info("no matching retrieval: {}", selection);
                cursor.close();
                return false;
            }
            final Uri retrieveUri = RetrievalTableSchema.getUri(cursor);
            cursor.close ();
            ContentValues values = new ContentValues();
            values.put(RetrievalTableSchema.DISPOSITION, RetrievalTableSchema.DISPOSITION_SATISFIED);

            @SuppressWarnings("unused")
            int numUpdated = cr.update(retrieveUri, values,null, null);
            
        } catch (FileNotFoundException e) {
            logger.warn("could not connect to content provider");
            return false;
        } catch (IOException e) {
            logger.warn("could not write to the content provider");
        }
        return true;
    }

    /**
     * Update the content providers as appropriate. These are typically received
     * in response to subscriptions.
     * 
     * The subscribing uri isn't sent with the subscription to the gateway
     * therefore it needs to be recovered from the subscription table.
     */

    private boolean dispatchSubscribeResponse(AmmoMessages.MessageWrapper mw, DistributorService that) {
        logger.info("::receiveSubscribeResponse");

        if (mw == null) return false;
        if (! mw.hasDataMessage()) return false;
        final AmmoMessages.DataMessage resp = mw.getDataMessage();

        String mime = resp.getMimeType();
        final ContentResolver cr = that.getContentResolver();
        String tableUriStr = null;

        try {
            Cursor subCursor = cr.query(
                    SubscriptionTableSchema.CONTENT_URI,
                    null,
                    "\"" + SubscriptionTableSchema.MIME + "\" = '" + mime + "'",
                    null, null);

            if (!subCursor.moveToFirst()) {
                logger.info("no matching subscription");
                subCursor.close();
                return false;
            }
            tableUriStr = subCursor.getString(subCursor.getColumnIndex(SubscriptionTableSchema.URI));
            subCursor.close();

            Uri tableUri = Uri.withAppendedPath(Uri.parse(tableUriStr),"_serial");
            OutputStream outstream = cr.openOutputStream(tableUri);

            if (outstream == null) {
                logger.error("the content provider {} is not available", tableUri);
                return false;
            }
            outstream.write(resp.getData().toByteArray());
            outstream.flush();
            outstream.close();
            return true;
        } catch (IllegalArgumentException ex) {
            logger.warn("could not serialize to content provider: {} : {}", tableUriStr, ex.getLocalizedMessage());
            return false;
        } catch (FileNotFoundException ex) {
            logger.warn("could not connect to content provider using openFile: {}", ex.getLocalizedMessage());
            return false;
        } catch (IOException ex) {
            logger.warn("could not write to the content provider {}", ex.getLocalizedMessage());
            return false;
        } 
    }
	
}


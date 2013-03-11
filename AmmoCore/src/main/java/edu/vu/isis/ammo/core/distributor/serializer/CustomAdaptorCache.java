package edu.vu.isis.ammo.core.distributor.serializer;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.provider.AmmoSyncRequest;

public class CustomAdaptorCache  {
    final static private Logger logger = LoggerFactory.getLogger("adaptor.ammo.class");

	/**
	 * This maintains a set of persistent connections to content provider
	 * adapter services.
	 */
    final private Context context;
	final private Map<String, AmmoSyncRequest> adaptorMap;
	
	public CustomAdaptorCache(final Context context) {
	    this.context = context;
	    this.adaptorMap = new HashMap<String, AmmoSyncRequest>(10);
	}
	
	private static class AdaptorProxy {
	    private AdaptorProxy() {
	    
	    }
	}

    private AmmoSyncRequest getAdaptorInstance(final String name, final String key, final byte[] data) {
        if (this.adaptorMap.containsKey(key)) {
            return this.adaptorMap.get(key);  
        }

        final byte[] data_ = data;
        final ServiceConnection connection = new ServiceConnection() {
            final private CustomAdaptorCache master = CustomAdaptorCache.this;
            final private Map<String, AmmoSyncRequest> cache = master.adaptorMap;
            final private String key_ = key;
            
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                final AmmoSyncRequest adaptor;
                if (!cache.containsKey(key_)) {
                    adaptor = AmmoSyncRequest.Stub.asInterface(service);
                    cache.put(key, adaptor);
                } else {
                    adaptor = cache.get(key);
                }

                // call the deserialize function here ....
                //try {
                 //   adaptor.  .deserialize(encoding.name(), key, data_);
                //} catch (RemoteException ex) {
                //    ex.printStackTrace();
                //}
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                logger.debug("service disconnected");
                cache.remove(key);
            }
        };
        final Intent intent = new Intent();
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        return null;
        
    }

    public Future<ByteBuffer> serialize(final Uri tupleUri, final Encoding encode) {
        // TODO Auto-generated method stub
        return null;
    }

    public void deserialize(final Uri tupleUri, final Encoding encode, final byte[] data) {
        // TODO Auto-generated method stub
        return;
    }
	

}

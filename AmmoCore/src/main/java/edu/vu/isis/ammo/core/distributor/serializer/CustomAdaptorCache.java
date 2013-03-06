package edu.vu.isis.ammo.core.distributor.serializer;

import java.util.HashMap;
import java.util.Map;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import edu.vu.isis.ammo.api.IDistributorAdaptor;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;

public class CustomAdaptorCache  {

	/**
	 * This maintains a set of persistent connections to content provider
	 * adapter services.
	 */
	final private Map<String, AdaptorProxy> ammoAdaptorMap;
	
	public CustomAdaptorCache() {
	    ammoAdaptorMap = new HashMap<String, AdaptorProxy>(10);
	}
	
	private static class AdaptorProxy {
	    private AdaptorProxy() {
	    
	    }
	}

    public void toProvider(String name, String key, byte[] data) {
        if (this.ammoAdaptorCache.containsKey(key)) {
            final IDistributorAdaptor adaptor = this.ammoAdaptorCache.get(key);
            try {
                final String uriString = adaptor.deserialize(encoding.name(), key, data);
                Uri.parse(uriString);

            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
            return null;
        }

        final byte[] data_ = data;
        final ServiceConnection connection = new ServiceConnection() {
            final private CustomSerializer master = CustomSerializer.this;
            final private CustomAdaptorCache cache = master.ammoAdaptorCache;
            
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                
                if (!cache.containsKey(key)) {
                    cache.put(key,
                            IDistributorAdaptor.Stub.asInterface(service));
                }
                final IDistributorAdaptor adaptor = cache.get(key);

                // call the deserialize function here ....
                try {
                    adaptor.deserialize(encoding.name(), key, data_);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                logger.debug("service disconnected");
                cache.remove(key);
            }
        };
        final Intent intent = new Intent();
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        
    }
	

}

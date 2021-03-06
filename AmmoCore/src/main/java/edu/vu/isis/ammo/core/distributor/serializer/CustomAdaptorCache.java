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

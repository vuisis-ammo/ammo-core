package edu.vu.isis.ammo.core.distributor.serializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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
import edu.vu.isis.ammo.core.distributor.RequestSerializer.DeserializedMessage;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;

public class CustomSerializer implements ISerializer {

	/**
	 * This maintains a set of persistent connections to content provider
	 * adapter services.
	 */
	final private CustomAdaptorCache ammoAdaptorCache;

	final private Uri provider;
	final private Context context;
	final private Encoding encoding;

	public CustomSerializer(final CustomAdaptorCache adaptorCache, final Context context, 
			final Uri provider, final Encoding encoding) {
	    this.ammoAdaptorCache = adaptorCache;
		this.context = context;
		this.provider = provider;
		this.encoding = encoding;
	}
	
	@Override
	public byte[] serialize(IContentItem item) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DeserializedMessage deserialize(byte[] data, List<String> fieldNames, List<FieldType> dataTypes) {	
		logger.debug("deserialize custom to provider");

		final String key = provider.toString();
		this.ammoAdaptorCache.toProvider(encoding.name(), key, data);
		return null;
	}
	
	private static class AdaptorProxy {
	    private AdaptorProxy() {
	    
	    }
	}
	

}

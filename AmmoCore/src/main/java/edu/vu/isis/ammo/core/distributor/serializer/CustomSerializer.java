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
	final static private Map<String, IDistributorAdaptor> remoteServiceMap;
	static {
		remoteServiceMap = new HashMap<String, IDistributorAdaptor>(10);
	}

	final private Uri provider;
	final private Context context;
	final private Encoding encoding;

	public CustomSerializer(final Context context,
			final Uri provider, final Encoding encoding) {
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
		if (CustomSerializer.remoteServiceMap.containsKey(key)) {
			final IDistributorAdaptor adaptor = CustomSerializer.remoteServiceMap
					.get(key);
			try {
				final String uriString = adaptor.deserialize(encoding.name(),
						key, data);
				Uri.parse(uriString);

			} catch (RemoteException ex) {
				ex.printStackTrace();
			}
			return null;
		}

		final byte[] data_ = data;
		final ServiceConnection connection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {

				if (!CustomSerializer.remoteServiceMap.containsKey(key)) {
					CustomSerializer.remoteServiceMap.put(key,
							IDistributorAdaptor.Stub.asInterface(service));
				}
				final IDistributorAdaptor adaptor = CustomSerializer.remoteServiceMap
						.get(key);

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
				CustomSerializer.remoteServiceMap.remove(key);
			}
		};
		final Intent intent = new Intent();
		context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
		return null;
	}
	

}

package edu.vu.isis.ammo.core.distributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.lang.IllegalArgumentException;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;

import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelDisposal;
import edu.vu.isis.ammo.api.IDistributorAdaptor;		
import android.os.AsyncTask;

/**
 * The purpose of these objects is lazily serialize an object.
 * Once it has been serialized once a copy is kept.
 *
 */
public class RequestSerializer {
	private static final Logger logger = LoggerFactory.getLogger("ammo-serial");

	public interface OnReady  {
		public AmmoGatewayMessage run(Encoding encode, byte[] serialized);
	}
	public interface OnSerialize  {
		public byte[] run(Encoding encode);
	}

	public final Provider provider;
	public final Payload payload;
	private OnReady readyActor;
	private OnSerialize serializeActor;
	private AmmoGatewayMessage agm;

	final static private Map<String,IDistributorAdaptor> remoteServiceMap;
	static {
		remoteServiceMap = new HashMap<String,IDistributorAdaptor>(10);
	}

	private RequestSerializer(Provider provider, Payload payload) {
		this.provider = provider;
		this.payload = payload;
		this.agm = null;


		this.readyActor = new RequestSerializer.OnReady() {
			@Override
			public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {
				logger.info("ready actor not defined {}", encode);
				return null;
			}
		};	
		this.serializeActor = new RequestSerializer.OnSerialize() {
			@Override
			public byte[] run(Encoding encode) {
				logger.info("serialize actor not defined {}", encode);
				return null;
			}
		};
	}

	public static RequestSerializer newInstance() {
		return new RequestSerializer(null, null);
	}
	public static RequestSerializer newInstance(Provider provider, Payload payload) {
		return new RequestSerializer(provider, payload);
	}

	public ChannelDisposal act(final AmmoService that,final Encoding encode,final String channel) {

		final AsyncTask<Void, Void, Void> action = new AsyncTask<Void, Void, Void> (){

			final RequestSerializer parent = RequestSerializer.this;
			@Override
			protected Void doInBackground(Void...none) {
				if (parent.agm == null) {
					final byte[] agmBytes = parent.serializeActor.run(encode);
					parent.agm = parent.readyActor.run(encode, agmBytes);
				}
				that.sendRequest(agm, channel);
				return null;
			}

			@Override
			protected void onProgressUpdate(Void... none) {
			}

			@Override
			protected void onPostExecute(Void result) {
			}
		};

		action.execute();
		return ChannelDisposal.QUEUED;
	}

	public void setAction(OnReady action) {
		this.readyActor = action;
	}

	public void setSerializer(OnSerialize onSerialize) {
		this.serializeActor = onSerialize;
	}


	/**
	 * The JSON serialization is in the following form...
	 * serialized tuple : A list of non-null bytes which serialize the tuple, 
	 *   this is provided/supplied to the ammo enabled content provider via insert/query.
	 *   The serialized tuple may be null terminated or the byte array may simply end.
	 * field blobs : A list of name:value pairs where name is the field name and value is 
	 *   the field's data blob associated with that field.
	 *   There may be multiple field blobs.
	 *   
	 *   field name : A null terminated name, 
	 *   field data length : A 4 byte big-endian length, indicating the number of bytes in the data blob.
	 *   field data blob : A set of bytes whose quantity is that of the field data length
	 *   
	 * Note the serializeFromProvider and serializeFromProvider are symmetric, 
	 * any change to one will necessitate a corresponding change to the other.
	 */  

	public static byte[] serializeFromProvider(final ContentResolver resolver, 
			final Uri tupleUri, final DistributorPolicy.Encoding encoding) 
					throws FileNotFoundException, IOException {

		logger.trace("serializing using encoding {}", encoding);
		switch (encoding.getPayload()) {
		case JSON: 
		{
			logger.trace("Serialize the non-blob data");

			final Uri serialUri = Uri.withAppendedPath(tupleUri, encoding.getPayloadSuffix());
			final Cursor tupleCursor;
			try {
				tupleCursor = resolver.query(serialUri, null, null, null, null);
			} catch(IllegalArgumentException ex) {
				logger.warn("unknown content provider {}", ex.getLocalizedMessage());
				return null;
			}
			if (tupleCursor == null) return null;

			if (! tupleCursor.moveToFirst()) return null;
			if (tupleCursor.getColumnCount() < 1) return null;

			final byte[] tuple;
			final JSONObject json = new JSONObject();
			tupleCursor.moveToFirst();

			for (final String name : tupleCursor.getColumnNames()) {
				if (name.startsWith("_")) continue; // don't send the local fields

				final String value = tupleCursor.getString(tupleCursor.getColumnIndex(name));
				if (value == null || value.length() < 1) continue;
				try {
					json.put(name, value);
				} catch (JSONException ex) {
					logger.warn("invalid content provider {}", ex.getStackTrace());
				}
			}
			tuple = json.toString().getBytes();
			tupleCursor.close(); 

			logger.trace("Serialize the blob data (if any)");

			logger.trace("getting the names of the blob fields");
			final Uri blobUri = Uri.withAppendedPath(tupleUri, "_blob");
			final Cursor blobCursor;
			try {
				blobCursor = resolver.query(blobUri, null, null, null, null);
			} catch(IllegalArgumentException ex) {
				logger.warn("unknown content provider {}", ex.getLocalizedMessage());
				return null;
			}
			if (blobCursor == null) return tuple;
			if (! blobCursor.moveToFirst()) return tuple;
			if (blobCursor.getColumnCount() < 1) return tuple;

			logger.trace("getting the blob fields");
			final int blobCount = blobCursor.getColumnCount();
			final List<String> blobFieldNameList = new ArrayList<String>(blobCount);
			final List<ByteArrayOutputStream> fieldBlobList = new ArrayList<ByteArrayOutputStream>(blobCount);
			final byte[] buffer = new byte[1024]; 
			for (int ix=0; ix < blobCursor.getColumnCount(); ix++) {
				final String fieldName = blobCursor.getColumnName(ix);
				logger.trace("processing blob {}", fieldName);
				blobFieldNameList.add(fieldName);

				final Uri fieldUri = Uri.withAppendedPath(tupleUri, blobCursor.getString(ix));
				try {
					final AssetFileDescriptor afd = resolver.openAssetFileDescriptor(fieldUri, "r");
					if (afd == null) {
						logger.warn("could not acquire file descriptor {}", serialUri);
						throw new IOException("could not acquire file descriptor "+fieldUri);
					}
					final ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();

					final InputStream instream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
					final BufferedInputStream bis = new BufferedInputStream(instream);
					final ByteArrayOutputStream fieldBlob = new ByteArrayOutputStream();
					for (int bytesRead = 0; (bytesRead = bis.read(buffer)) != -1;) {
						fieldBlob.write(buffer, 0, bytesRead);
					}
					bis.close();
					fieldBlobList.add(fieldBlob);

				} catch (IOException ex) {
					logger.info("unable to create stream {} {}",serialUri, ex.getMessage());
					throw new FileNotFoundException("Unable to create stream");
				}
			}

			logger.trace("loading larger tuple buffer");
			final ByteArrayOutputStream bigTuple = new ByteArrayOutputStream();

			bigTuple.write(tuple); 
			bigTuple.write(0x0);

			for (int ix=0; ix < blobCount; ix++) {
				final String fieldName = blobFieldNameList.get(ix);
				bigTuple.write(fieldName.getBytes());
				bigTuple.write(0x0);

				final ByteArrayOutputStream fieldBlob = fieldBlobList.get(ix);
				final ByteBuffer bb = ByteBuffer.allocate(4);
				bb.order(ByteOrder.BIG_ENDIAN); 
				final int size = fieldBlob.size();
				bb.putInt(size);
				bigTuple.write(bb.array());
				bigTuple.write(fieldBlob.toByteArray());
				bigTuple.write(bb.array());
			}
			blobCursor.close();
			final byte[] finalTuple = bigTuple.toByteArray();
			bigTuple.close();
			return finalTuple;
		}

		case TERSE: 
		{
			logger.error("terse serialization not implemented");
			return null;
		}
		// TODO custom still needs a lot of work
		// It will presume the presence of a SyncAdaptor for the content provider.
		case CUSTOM:
		default:
		{
			final Uri serialUri = Uri.withAppendedPath(tupleUri, encoding.getPayloadSuffix());
			final Cursor tupleCursor;
			try {
				tupleCursor = resolver.query(serialUri, null, null, null, null);
			} catch(IllegalArgumentException ex) {
				logger.warn("unknown content provider {}", ex.getLocalizedMessage());
				return null;
			}
			if (tupleCursor == null) return null;

			if (! tupleCursor.moveToFirst()) return null;
			if (tupleCursor.getColumnCount() < 1) return null;

			tupleCursor.moveToFirst();

			final String tupleString = tupleCursor.getString(0);
			return tupleString.getBytes();
		}
		}
	}

	/**
	 * @see serializeFromProvider with which this method is symmetric.
	 */
	public static Uri deserializeToProvider(final Context context, 
			final Uri provider, final Encoding encoding, final byte[] data) {

		logger.debug("deserialize message");

		final ContentResolver resolver = context.getContentResolver();
		final ByteBuffer dataBuff = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

		switch (encoding.getPayload()) {
		case JSON: 
		{
			int position = 0;
			for (; position < data.length && data[position] != (byte)0x0; position++) {}

			final int length = position;
			final byte[] payload = new byte[length];
			System.arraycopy(data, 0, payload, 0, length);
			final Uri tupleUri;
			try {
				final JSONObject input = (JSONObject) new JSONTokener(new String(payload)).nextValue();
				final ContentValues cv = new ContentValues();
				for (@SuppressWarnings("unchecked")
				final Iterator<String> iter = input.keys(); iter.hasNext();) {
					final String key = iter.next();
					cv.put(key, input.getString(key));
				}
				cv.put(AmmoProviderSchema._RECEIVED_DATE, System.currentTimeMillis());
				cv.put(AmmoProviderSchema._DISPOSITION, AmmoProviderSchema.Disposition.REMOTE.name());
				tupleUri = resolver.insert(provider, cv);
				if (tupleUri == null) {
					logger.warn("could not insert {} into {}", cv, provider);
					return null;
				}
			} catch (JSONException ex) {
				logger.warn("invalid JSON content {}", ex.getLocalizedMessage());
				return null;
			} catch (SQLiteException ex) {
				logger.warn("invalid sql insert {}", ex.getLocalizedMessage());
				return null;
			} catch (IllegalArgumentException ex) {
				logger.warn("bad provider or values: {}", ex.getLocalizedMessage());
				return null;
			}		
			if (position == data.length) return tupleUri;

			// process the blobs
			final long tupleId = ContentUris.parseId(tupleUri);
			final Uri.Builder uriBuilder = provider.buildUpon();
			final Uri.Builder updateTuple = ContentUris.appendId(uriBuilder, tupleId);

			position++; // move past the null terminator
			dataBuff.position(position);
			while (dataBuff.position() < data.length) {
				// get the field name
				final int nameStart = dataBuff.position();
				int nameLength;
				for (nameLength=0; position < data.length; nameLength++, position++) {
					if (data[position] == 0x0) break;
				}
				final String fieldName = new String(data, nameStart, nameLength);
				position++; // move past the null			
				dataBuff.position(position);
				final int dataLength = dataBuff.getInt();

				if (dataLength > dataBuff.remaining()) {
					logger.error("payload size is wrong {} {}", 
							dataLength, data.length);
					return null;
				}
				final byte[] blob = new byte[dataLength];
				final int blobStart = dataBuff.position();
				System.arraycopy(data, blobStart, blob, 0, dataLength);
				dataBuff.position(blobStart+dataLength);
				final int dataLengthFinal = dataBuff.getInt();
				if (dataLengthFinal != dataLength) {
					logger.error("data length mismatch {} {}", dataLength, dataLengthFinal);
				}

				final Uri fieldUri = updateTuple.appendPath(fieldName).build();			
				try {
					final OutputStream outstream = resolver.openOutputStream(fieldUri);
					if (outstream == null) {
						logger.error( "could not open output stream to content provider: {} ",fieldUri);
						return null;
					}
					outstream.write(blob);
					outstream.close();
				} catch (FileNotFoundException ex) {
					logger.error( "blob file not found: {} {}",fieldUri, ex.getStackTrace());
				} catch (IOException ex) {
					logger.error( "error writing blob file: {} {}",fieldUri, ex.getStackTrace());
				}
			}	
			return tupleUri;
		}
		case TERSE: 
		{
			logger.error("terse deserialization not implemented");
			return null;
		}
		// TODO as with the serializer the CUSTOM section will presume for the
		// content provider the existence of a SyncAdaptor
		case CUSTOM:
		default:
		{
			// get a service connection using ServiceConnection, then 
			// call a AsyncTaskLoader to do the deserialization ... 
			// fire and forget ..... 

//			final String key = provider.toString();
//			if ( RequestSerializer.remoteServiceMap.containsKey(key)) {
//				final IDistributorAdaptor adaptor = RequestSerializer.remoteServiceMap.get(key);
//				return adaptor.deserialize(encoding.name(), key, data);
//			}
//			final ServiceConnection connection = new ServiceConnection() {
//				@Override
//				public void onServiceConnected(ComponentName name, IBinder service) {
//
//					if (! RequestSerializer.remoteServiceMap.containsKey(key)) {
//						RequestSerializer.remoteServiceMap.put(key, IDistributorAdaptor.Stub.asInterface(service));	
//					}
//					final IDistributorAdaptor adaptor = RequestSerializer.remoteServiceMap.get(key);
//
//					// call the deserialize function here ....
//					return adaptor.deserialize(encoding.name(), key, data);
//				}
//
//				@Override
//				public void onServiceDisconnected(ComponentName name) {
//					logger.debug("service disconnected");
//					RequestSerializer.remoteServiceMap.remove(key);
//				}
//			};
//			final Intent intent = new Intent();
//			context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
		}
		}
		return null;

	}
}


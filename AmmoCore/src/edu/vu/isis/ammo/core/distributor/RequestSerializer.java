/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
 */
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONArray;
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
import android.os.RemoteException;
import edu.vu.isis.ammo.api.IDistributorAdaptor;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;

/**
 * The purpose of these objects is lazily serialize an object.
 * Once it has been serialized once a copy is kept.
 *
 */
public class RequestSerializer {
	private static final Logger logger = LoggerFactory.getLogger("dist.serializer");

	public enum FieldType {
		NULL(0), 
		BOOL(1), 
		BLOB(2), 
		FLOAT(3),
		INTEGER(4), 
		LONG(5), 
		TEXT(6), 
		REAL(7),
		FK(8), 
		GUID(9), 
		EXCLUSIVE(10), 
		INCLUSIVE(11),
		TIMESTAMP(12),
		SHORT(13);

		private final int code;

		private FieldType(int code) {
			this.code = code;
		}

		private static final Map<Integer,FieldType> codemap = 
				new HashMap<Integer,FieldType>();
		static {
			for (FieldType t : FieldType.values()) {
				FieldType.codemap.put(t.code, t);
			}
		}

		public int toCode() {
			return this.code;
		}

		public static FieldType fromCode(final int code) {
			return FieldType.codemap.get(code);
		}
	}

	/**
	 * The presence of the BLOB_MARKER_FIELD as the first byte in the 
	 * footer for a blob data section indicates where the blob should 
	 * be placed in the content provider.
	 */
	public static final byte BLOB_MARKER_FIELD = (byte)0xff;

	private enum FieldTypeEnum { 
        FIELD_TYPE_FILE, FIELD_TYPE_BLOB; 

        /**
         * The problem here is that the blob may have trailing null bytes.
         * e.g.
         *  fieldName = [data], fieldNameBlob= [[100, 97, 116, 97]], blob = [[100, 97, 116, 97, 0]]
         * These should result it a match.
         */
				public static FieldTypeEnum infer(String fieldName, byte[] blob) {
       
			   	final byte[] fieldNameBlob; 
          try {
              fieldNameBlob = fieldName.getBytes("UTF-8");
          } catch (java.io.UnsupportedEncodingException ex) {
              return FIELD_TYPE_BLOB;
          }

				 logger.trace("processing blob fieldName = [{}], fieldNameBlob= [{}], blob = [{}]", 
             new Object[] {fieldName, fieldNameBlob, blob});

				  if (blob == null) return FIELD_TYPE_FILE;
				  if (blob.length < 1) return FIELD_TYPE_FILE;

          if (fieldNameBlob.length == blob.length)
              return Arrays.equals(blob, fieldNameBlob) ? FIELD_TYPE_FILE : FIELD_TYPE_BLOB;

          if (fieldNameBlob.length > blob.length) return FIELD_TYPE_BLOB;

          int i;
          for (i = 0; i < fieldNameBlob.length; i++) {
             if (fieldNameBlob[i] != blob[i]) return FIELD_TYPE_BLOB;
          }
          for (; i < blob.length; i++) {
               if (blob[i] != (byte)0) return FIELD_TYPE_BLOB;
          }
				  return FIELD_TYPE_FILE;
		}
	}
	
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

	/**
	 * This maintains a set of persistent connections to 
	 * content provider adapter services.
	 */
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
				logger.trace("ready actor not defined {}", encode);
				return null;
			}
		};	
		this.serializeActor = new RequestSerializer.OnSerialize() {
			@Override
			public byte[] run(Encoding encode) {
				logger.trace("serialize actor not defined {}", encode);
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

	public DisposalState act(final AmmoService that, final Encoding encode, final String channel) {
		final RequestSerializer parent = RequestSerializer.this;
		final Encoding local_encode = encode;
		final String local_channel = channel;
		if (parent.agm == null) {
			final byte[] agmBytes = parent.serializeActor.run(local_encode);
			parent.agm = parent.readyActor.run(local_encode, agmBytes);
		}
		if (parent.agm == null)
			return null;

		return that.sendRequest(parent.agm, local_channel);
	}

	public void setAction(OnReady action) {
		this.readyActor = action;
	}

	public void setSerializer(OnSerialize onSerialize) {
		this.serializeActor = onSerialize;
	}


	public static byte[] serializeFromContentValues(ContentValues cv, final DistributorPolicy.Encoding encoding) {

		logger.trace("serializing using content values and encoding {}", encoding);
		switch (encoding.getType()) {
		case JSON: 
		{
			return encodeAsJson (cv);
		}

		case TERSE: 
		{
			// Need to be implemented ...
		}
		// TODO custom still needs a lot of work
		// It will presume the presence of a SyncAdaptor for the content provider.
		case CUSTOM:
		default:
		{
		}
		}
		return null;
	}

	private static byte[] encodeAsJson (ContentValues cv) {
		// encoding in json for now ...
		Set<java.util.Map.Entry<String, Object>> data = cv.valueSet();
		Iterator<java.util.Map.Entry<String, Object>> iter = data.iterator();       
		final JSONObject json = new JSONObject();

		while (iter.hasNext())
		{
			Map.Entry<String, Object> entry = 
					(Map.Entry<String, Object>)iter.next();         
			try {
				if (entry.getValue() instanceof String)
					json.put(entry.getKey(), cv.getAsString(entry.getKey()));
				else if (entry.getValue() instanceof Integer)
					json.put(entry.getKey(), cv.getAsInteger(entry.getKey()));
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}

		return json.toString().getBytes();
	}

	/**
	 * This simple Future which can only be bound to a value once.
	 */
	public static class DataFlow<V> implements Future<V> {
		final AtomicReference<V> value;

		public DataFlow() {
			this.value = new  AtomicReference<V>(); 
		}

		public DataFlow(V value) {
			this();
			this.value.set(value);
		}

		public void bind(final V value) {
			if (! this.value.compareAndSet(null, value)) return;
			this.notifyAll();
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public V get() throws InterruptedException, ExecutionException {
			synchronized (this) {
				if (this.value.get() == null) {
					this.wait();
				}
			}
			return this.value.get();
		}

		@Override
		public V get(long timeout, TimeUnit unit) 
				throws InterruptedException, ExecutionException, TimeoutException {
			synchronized (this) {
				if (this.value.get() == null) {
					this.wait(unit.toMillis(timeout));
				}
			}
			return this.value.get();
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return this.value.get() != null;
		}
	}


	/**
	 * All serializer methods return this dataflow variable.
	 */
	public static class ByteBufferFuture extends DataFlow<ByteBuffer> {

		public ByteBufferFuture() {
			super();
		}

		public ByteBufferFuture(ByteBuffer value) {
			super(value);
		}

		public static ByteBufferFuture wrap(byte[] value) {
			return new ByteBufferFuture(ByteBuffer.wrap(value));
		}
	}
	/**
	 *
	 */  

	public static byte[] serializeFromProvider(final ContentResolver resolver, 
			final Uri tupleUri, final DistributorPolicy.Encoding encoding) 
					throws TupleNotFoundException, NonConformingAmmoContentProvider, IOException {

		logger.trace("serializing using encoding {}", encoding);
		final ByteBufferFuture result;
		final Encoding.Type encodingType = encoding.getType();
		switch (encodingType) {
		case CUSTOM:
			result = RequestSerializer.serializeCustomFromProvider(resolver, tupleUri, encoding);
			break;
		case JSON: 
			result = RequestSerializer.serializeJsonFromProvider(resolver, tupleUri, encoding);
			break;
		case TERSE: 
			result = RequestSerializer.serializeTerseFromProvider(resolver, tupleUri, encoding);
			break;
		default:
			result = RequestSerializer.serializeCustomFromProvider(resolver, tupleUri, encoding);
		}
		try {
			return result.get().array();
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		} catch (ExecutionException ex) {
			ex.printStackTrace();
		} 
		return null;
	}

	/**
	 * All deserializers return this dataflow variable
	 */
	public static class UriFuture extends DataFlow<Uri> {

		public UriFuture() {
			super();
		}

		public UriFuture(Uri tupleUri) {
			super(tupleUri);
		}

	}

	/**
	 * @see serializeFromProvider with which this method is symmetric.
	 */
	public static Uri deserializeToProvider(final Context context, final String channelName,
			final Uri provider, final Encoding encoding, final byte[] data) {

		logger.debug("deserialize message");

		final UriFuture uri;
		switch (encoding.getType()) {
		case CUSTOM:
			uri = RequestSerializer.deserializeCustomToProvider(context, channelName, provider, encoding, data);
			break;
		case JSON: 
			uri = RequestSerializer.deserializeJsonToProvider(context, channelName, provider, encoding, data);
			break;
		case TERSE: 
			uri = RequestSerializer.deserializeTerseToProvider(context, channelName, provider, encoding, data);
			break;
		default:
			uri = RequestSerializer.deserializeCustomToProvider(context, channelName, provider, encoding, data);	
		}
    if (uri == null) return null;
		try {
			return uri.get();
		} catch (InterruptedException ex) {
			logger.error("interrupted thread ", ex);
			return null;
		} catch (ExecutionException ex) {
			logger.error("execution error thread ", ex);
			return null;
		}
	}


	/**
	 * A pair of functions which communicate with a Content Adaptor Service.
	 * These Content Adaptor Services are created by the code generator.
	 *
	 */
	public static ByteBufferFuture serializeCustomFromProvider(final ContentResolver resolver, 
			final Uri tupleUri, final DistributorPolicy.Encoding encoding) 
					throws TupleNotFoundException, NonConformingAmmoContentProvider, IOException {

		final Uri serialUri = Uri.withAppendedPath(tupleUri, encoding.getPayloadSuffix());
		final Cursor tupleCursor;
		try {
			tupleCursor = resolver.query(serialUri, null, null, null, null);
		} catch(IllegalArgumentException ex) {
			logger.warn("unknown content provider", ex);
			return null;
		}
		if (tupleCursor == null) {
			throw new TupleNotFoundException("while serializing from provider", tupleUri);
		}

		if (! tupleCursor.moveToFirst()) {
			tupleCursor.close();
			return null;
		}
		int columnCount = tupleCursor.getColumnCount();
		if (columnCount < 1) {
			tupleCursor.close();
			return null;
		}
		tupleCursor.moveToFirst();

		final String tupleString = tupleCursor.getString(0);
		tupleCursor.close();
		return ByteBufferFuture.wrap(tupleString.getBytes());
	}

	/**
	 * Invoke the custom deserializer.
	 * 
	 * @param context
	 * @param channelName
	 * @param provider
	 * @param encoding
	 * @param data
	 * @return
	 */
	public static UriFuture deserializeCustomToProvider(final Context context, final String channelName,
			final Uri provider, final Encoding encoding, final byte[] data) {

		final String key = provider.toString();
		if ( RequestSerializer.remoteServiceMap.containsKey(key)) {
			final IDistributorAdaptor adaptor = RequestSerializer.remoteServiceMap.get(key);
			try {
				final String uriString = adaptor.deserialize(encoding.name(), key, data);
				Uri.parse(uriString);

			} catch (RemoteException ex) {
				ex.printStackTrace();
			}
			return null;
		}
		final ServiceConnection connection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {

				if (! RequestSerializer.remoteServiceMap.containsKey(key)) {
					RequestSerializer.remoteServiceMap.put(key, IDistributorAdaptor.Stub.asInterface(service));	
				}
				final IDistributorAdaptor adaptor = RequestSerializer.remoteServiceMap.get(key);

				// call the deserialize function here ....
				try {
					adaptor.deserialize(encoding.name(), key, data);
				} catch (RemoteException ex) {
					ex.printStackTrace();
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				logger.debug("service disconnected");
				RequestSerializer.remoteServiceMap.remove(key);
			}
		};
		final Intent intent = new Intent();
		context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
		return null;
	}

	/**
	 * The JSON serialization is of the following form...
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
	public static ByteBufferFuture serializeJsonFromProvider(final ContentResolver resolver, 
			final Uri tupleUri, final DistributorPolicy.Encoding encoding) 
					throws TupleNotFoundException, NonConformingAmmoContentProvider, IOException {

		logger.trace("Serialize the non-blob data");

		final Uri serialUri = Uri.withAppendedPath(tupleUri, encoding.getPayloadSuffix());
		Cursor tupleCursor = null;
		final byte[] tuple;
		final JSONObject json;
		try {
		try {
			tupleCursor = resolver.query(serialUri, null, null, null, null);
		} catch(IllegalArgumentException ex) {
			logger.warn("unknown content provider {}", ex.getLocalizedMessage());
			return null;
		}
		if (tupleCursor == null) {
			throw new TupleNotFoundException("while serializing from provider", tupleUri);
		}
		if ( tupleCursor.getCount() < 1) {
			logger.warn("tuple no longe present {}", tupleUri);
			return null;
		}
			if (! tupleCursor.moveToFirst()) { return null; }
			if (tupleCursor.getColumnCount() < 1) { return null; }

			json = new JSONObject();
		tupleCursor.moveToFirst();

		for (final String name : tupleCursor.getColumnNames()) {
			if (name.startsWith("_")) continue; // don't send the local fields

			final String value = tupleCursor.getString(tupleCursor.getColumnIndex(name));
			if (value == null || value.length() < 1) continue;
			try {
				json.put(name, value);
			} catch (JSONException ex) {
				logger.warn("invalid content provider", ex);
			}
			}
		} finally {
			if (tupleCursor != null) tupleCursor.close(); 
		}
		// FIXME FPE final Writer can we be more efficient? not copy bytes so often?
		tuple = json.toString().getBytes();

		logger.info("Serialized message, content {}", json.toString() );

		logger.trace("loading larger tuple buffer");
		final ByteArrayOutputStream bigTuple = new ByteArrayOutputStream();

		bigTuple.write(tuple); 
		bigTuple.write(0x0);

		logger.trace("Serialize the blob data (if any)");

		final Uri blobUri = Uri.withAppendedPath(tupleUri, "_blob");
		Cursor blobCursor = null;
		final int blobCount;
		try {
		try {
			blobCursor = resolver.query(blobUri, null, null, null, null);
		} catch(IllegalArgumentException ex) {
			logger.warn("unknown content provider", ex);
			return null;
		}
		if (blobCursor == null) {
			PLogger.API_STORE.debug("json tuple=[{}]", tuple);
			return ByteBufferFuture.wrap(tuple);
		}
		if (! blobCursor.moveToFirst()) {
			PLogger.API_STORE.debug("json tuple=[{}]", tuple);
			return ByteBufferFuture.wrap(tuple);
		}
			blobCount = blobCursor.getColumnCount();
		if (blobCount < 1)  {
			PLogger.API_STORE.debug("json tuple=[{}]", tuple);
			return ByteBufferFuture.wrap(tuple);
		}

		logger.trace("getting the blob fields");	
		final byte[] buffer = new byte[1024]; 
		for (int ix=0; ix < blobCursor.getColumnCount(); ix++) {

			final String fieldName = blobCursor.getColumnName(ix);
				bigTuple.write(fieldName.getBytes());
				bigTuple.write(0x0);

				// "Manual merge" of fix for blob/file handling
				/*
				 * If it is a file field type the value is a string,
				 * If a blob then the field type is a blob, but
				 * you can not check the field type directly so we
				 * let the exception happen if we try the wrong type.
				 */
				final String fileName;
				final byte[] blob;
				final FieldTypeEnum dataType;
				try {
				    String tempFileName = null;
				    byte[] tempBlob = null;
				    FieldTypeEnum tempDataType = null;
				    try {
					tempFileName = blobCursor.getString(ix);
					tempDataType = FieldTypeEnum.FIELD_TYPE_FILE;
				    } catch (Exception ex) {
					tempBlob = blobCursor.getBlob(ix);
					tempDataType = FieldTypeEnum.infer(fieldName, tempBlob);
				    }
				    fileName = tempFileName;
				    blob = tempBlob;	
				    dataType = tempDataType;
				} catch (Exception ex) {
				    logger.error("something bad happened reading the field value", ex);
				    continue;
				}
				

				switch (dataType) {
				case FIELD_TYPE_BLOB:
					try {
						logger.trace("field name=[{}] blob=[{}]", fieldName, blob);
						final ByteBuffer fieldBlobBuffer = ByteBuffer.wrap(blob);

						final ByteBuffer bb = ByteBuffer.allocate(4);
						bb.order(ByteOrder.BIG_ENDIAN); 
						final int size = fieldBlobBuffer.capacity();
						bb.putInt(size);
						bigTuple.write(bb.array());

						bigTuple.write(fieldBlobBuffer.array());

						bigTuple.write(BLOB_MARKER_FIELD);
						bigTuple.write(bb.array(),1,bb.array().length-1);
					} finally { }
					break;
				case FIELD_TYPE_FILE: 
					logger.trace("field name=[{}] ", fieldName);
					final Uri fieldUri = Uri.withAppendedPath(tupleUri, fieldName);
			try {
				final AssetFileDescriptor afd = resolver.openAssetFileDescriptor(fieldUri, "r");
				if (afd == null) {
							logger.warn("could not acquire file descriptor {}", fieldUri);
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
						final ByteBuffer fieldBlobBuffer = ByteBuffer.wrap(fieldBlob.toByteArray());

                        // write it out
						final ByteBuffer bb = ByteBuffer.allocate(4);
						bb.order(ByteOrder.BIG_ENDIAN); 
						final int size = fieldBlobBuffer.capacity();
						bb.putInt(size);
						bigTuple.write(bb.array());

						bigTuple.write(fieldBlobBuffer.array());
						bigTuple.write(bb.array());

			} catch (IOException ex) {
				logger.trace("unable to create stream {}", serialUri, ex);
				throw new FileNotFoundException("Unable to create stream");
			}
					break;
				default:
					logger.warn("not a known data type {}", dataType);
				}
			}
		} finally {
			if (blobCursor != null) blobCursor.close();
		}

		final byte[] finalTuple = bigTuple.toByteArray();
		bigTuple.close();
		PLogger.API_STORE.debug("json tuple=[{}] size=[{}]", 
				tuple, finalTuple.length);
		PLogger.API_STORE.trace("json finalTuple=[{}]", 
				finalTuple);
		return ByteBufferFuture.wrap(finalTuple);
	}

	/**
	 * JSON encoding (deprecated)
	 * This method interacts directly with the content provider.
	 * It should only be used with content providers which are known
	 * to be responsive.
	 * 
	 * @param context
	 * @param provider
	 * @param encoding
	 * @param data
	 * @return
	 */
	public static UriFuture deserializeJsonToProvider(final Context context, final String channelName,
			final Uri provider, final Encoding encoding, final byte[] data) {

		final ContentResolver resolver = context.getContentResolver();
		final ByteBuffer dataBuff = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
		// find the end of the json portion of the data
		int position = 0;
		for (; position < data.length && data[position] != (byte)0x0; position++) {}

		final int length = position;
		final byte[] payload = new byte[length];
		System.arraycopy(data, 0, payload, 0, length);
		final JSONObject input;
		try {
			final String parsePayload = new String(payload);
			final Object value = new JSONTokener(parsePayload).nextValue();
			if (value instanceof JSONObject) {
				input = (JSONObject) value;
			} else if (value instanceof JSONArray) {
				logger.warn("invalid JSON payload=[{}]", parsePayload);
				return null;
			} else if (value == JSONObject.NULL) {
				logger.warn("null JSON payload=[{}]", parsePayload);
				return null;
			} else {
				logger.warn("{} JSON payload=[{}]", value.getClass().getName(), parsePayload);
				return null;
			}
		} catch (ClassCastException ex) {
			logger.warn("invalid JSON content", ex);
			return null;
		} catch (JSONException ex) {
			logger.warn("invalid JSON content", ex);
			return null;
		}
		final ContentValues cv = new ContentValues();
		cv.put(AmmoProviderSchema._RECEIVED_DATE, System.currentTimeMillis());
		final StringBuilder sb = new StringBuilder()
		.append(AmmoProviderSchema.Disposition.REMOTE.name())
		.append('.')
		.append(channelName);
		cv.put(AmmoProviderSchema._DISPOSITION, sb.toString());

		for (final Iterator<?> iter = input.keys(); iter.hasNext();) {
			final Object keyObj = iter.next();
			if (keyObj instanceof String) {
				final String key = (String) keyObj;
				final Object value;
				try {
					value = input.get(key);
				} catch (JSONException ex) {
					logger.error("invalid JSON key=[{}] ex=[{}]", key, ex);
					continue;
				}
				if (value instanceof String) {
					cv.put(key, (String) value);
				} else {
					logger.error("value has unexpected typ JSON key=[{}] value=[{}]", key, value);
					continue;
				}
			} else {
				logger.error("invalid JSON key=[{}]", keyObj);
			}
		}

		final Uri tupleUri;
		try {
			tupleUri = resolver.insert(provider, cv); // TBD SKN --- THIS IS A  SYNCHRONOUS IPC? we will block here for a while ...
			if (tupleUri == null) {
				logger.warn("could not insert {} into {}", cv, provider);
				return null;
			}
			logger.info("Deserialized Received message, content {}", cv);

		} catch (SQLiteException ex) {
			logger.warn("invalid sql insert", ex);
			return null;
		} catch (IllegalArgumentException ex) {
			logger.warn("bad provider or values", ex);
			return null;
		}		
		if (position == data.length) return new UriFuture(tupleUri);

		// process the blobs
		final long tupleId = ContentUris.parseId(tupleUri);
		final Uri.Builder uriBuilder = provider.buildUpon();
		final Uri.Builder updateTuple = ContentUris.appendId(uriBuilder, tupleId);

		position++; // move past the null terminator
		dataBuff.position(position);
		int blobCount = 0;
		while (dataBuff.position() < data.length) {
			// get the field name
			final int nameStart = dataBuff.position();
			int nameLength;
			for (nameLength=0; position < data.length; nameLength++, position++) {
				if (data[position] == 0x0) break;
			}
			final String fieldName = new String(data, nameStart, nameLength);
			position++; // move past the null			

			// get the last three bytes of the length, to be used as a simple checksum
			dataBuff.position(position);
			dataBuff.get();
			final byte[] beginningPsuedoChecksum = new byte[3];
			dataBuff.get(beginningPsuedoChecksum);

			// get the blob length for real
			dataBuff.position(position);
			final int dataLength = dataBuff.getInt();

			if (dataLength > dataBuff.remaining()) {
				logger.error("payload size is wrong {} {}", 
						dataLength, data.length);
				return null;
			}
			// get the blob data
			final byte[] blob = new byte[dataLength];
			final int blobStart = dataBuff.position();
			System.arraycopy(data, blobStart, blob, 0, dataLength);
			dataBuff.position(blobStart+dataLength);

			// check for storage type
			final byte storageMarker = dataBuff.get();

			// get and compare the beginning and ending checksum
			final byte[] endingPsuedoChecksum = new byte[3];
			dataBuff.get(endingPsuedoChecksum);
			if (! Arrays.equals(endingPsuedoChecksum, beginningPsuedoChecksum)) {
				logger.error("blob checksum mismatch {} {}", endingPsuedoChecksum, beginningPsuedoChecksum);
				break;
			}

			// write the blob to the appropriate place
			switch (storageMarker) {
			case BLOB_MARKER_FIELD:
				blobCount++;
				cv.put(fieldName, blob);
				break;
			default:
			final Uri fieldUri = updateTuple.appendPath(fieldName).build();			
			try {
				final OutputStream outstream = resolver.openOutputStream(fieldUri);
				if (outstream == null) {
					logger.error( "failed to open output stream to content provider: {} ",
							fieldUri);
					return null;
				}
				outstream.write(blob);
				outstream.close();
			} catch (SQLiteException ex) {
				logger.error("in provider {} could not open output stream {}", 
						fieldUri, ex.getLocalizedMessage());
			} catch (FileNotFoundException ex) {
				logger.error( "blob file not found: {}",fieldUri, ex);
			} catch (IOException ex) {
				logger.error( "error writing blob file: {}",fieldUri, ex);
			}
		}	
		}
		if (blobCount > 0) {
			try {
				final Uri blobUri = resolver.insert(provider, cv); 
				if (blobUri == null) {
					logger.warn("could not insert {} into {}", cv, provider);
					return null;
				}
				logger.trace("Deserialized Received message blobs, content {}", cv);

			} catch (SQLiteException ex) {
				logger.warn("invalid sql blob insert", ex);
				return null;
			} catch (IllegalArgumentException ex) {
				logger.warn("bad provider or blob values", ex);
				return null;
			}		
		}
		return new UriFuture(tupleUri);
	}

	/**
	 * Terse encoding (deprecated)
	 * This is a compressed encoding to be used over networks with limited bandwidth.
	 */
	public static ByteBufferFuture serializeTerseFromProvider(final ContentResolver resolver, 
			final Uri tupleUri, final DistributorPolicy.Encoding encoding) 
					throws TupleNotFoundException, NonConformingAmmoContentProvider, IOException {
		/**
		 * 1) query to find out about the fields to send: name, position, type
		 * 2) serialize the fields 
		 */
		logger.debug("Using terse serialization");

		final Cursor serialMetaCursor;
		try {
			final Uri dUri = Uri.withAppendedPath(tupleUri, "_data_type");
			serialMetaCursor = resolver.query(dUri, null, null, null, null);
		} catch(IllegalArgumentException ex) {
			logger.warn("unknown content provider ", ex);
			return null;
		}
		if (serialMetaCursor == null) {
			throw new NonConformingAmmoContentProvider("while getting metadata from provider", tupleUri);
		}

		if (! serialMetaCursor.moveToFirst()) {
			serialMetaCursor.close();
			return null;
		}
		final int columnCount = serialMetaCursor.getColumnCount();
		if (columnCount < 1) {
			serialMetaCursor.close();
			return null;
		}

		final Map<String,Integer> serialMap = new HashMap<String,Integer>(columnCount);
		final String[] serialOrder = new String[columnCount];
		int ix = 0;
		for (final String key : serialMetaCursor.getColumnNames()) {
			final int value = serialMetaCursor.getInt(serialMetaCursor.getColumnIndex(key));
			serialMap.put(key, value);
			serialOrder[ix] = key;
			ix++;
		}
		serialMetaCursor.close(); 

		final Cursor tupleCursor;
		try {
			tupleCursor = resolver.query(tupleUri, null, null, null, null);
		} catch(IllegalArgumentException ex) {
			logger.warn("unknown content provider ", ex);
			return null;
		}
		if (tupleCursor == null) {
			throw new TupleNotFoundException("while serializing from provider", tupleUri);
		}

		if (! tupleCursor.moveToFirst()) {
			tupleCursor.close();
			return null;
		}
		if (tupleCursor.getColumnCount() < 1) {
			tupleCursor.close();
			return null;
		}

		final ByteBuffer tuple = ByteBuffer.allocate(2048);

		// For the new serialization for the 152s, write the data we want to tuple.
		for (final String key : serialOrder) {
			if (! serialMap.containsKey(key)) continue;

			final int type = serialMap.get(key);
			final int columnIndex = tupleCursor.getColumnIndex(key);
			switch (FieldType.fromCode(type)) {
			case NULL:
				break;
			case LONG:
			case FK: {			
				final long longValue = tupleCursor.getLong( columnIndex );
				tuple.putLong(longValue);
				break; }
			case TIMESTAMP: {
				final long longValue = tupleCursor.getLong( columnIndex );
				final int intValue = (int)(longValue/1000); // SKN - we will send seconds only on serial
				tuple.putInt(intValue);
				break; }
			case TEXT:
			case GUID: {
				// The database will return null if the string is empty,
				// so detect that and write a zero length if it happens.
				// Don't modify this code without testing on the serial
				// channel using radios.
				String svalue = tupleCursor.getString( columnIndex );
				int length = (svalue == null) ? 0 : svalue.length();
				tuple.putShort( (short) length );
				if (length > 0)
					tuple.put( svalue.getBytes("UTF8") );
				// for ( int i = 0; i < length; i++ ) {
				// 	char c = svalue.charAt(i);
				// 	tuple.putChar( c );
				// }
				// FIXME use UTF8 not UTF16, this loop is not needed.
				// the length should correspondingly be short not long
				// do the deserialize as well
				break; }
			case SHORT: {
				final short shortValue = tupleCursor.getShort(columnIndex);
				tuple.putShort(shortValue);
				break; }
			case BOOL:
			case INTEGER:
			case EXCLUSIVE:
			case INCLUSIVE: {
				final int intValue = tupleCursor.getInt(columnIndex);
				tuple.putInt(intValue);
				break; }
			case REAL:
			case FLOAT: {
				final double doubleValue = tupleCursor.getDouble( columnIndex );
				tuple.putDouble(doubleValue);
				break; }
			case BLOB: {
				final byte[] bytesValue = tupleCursor.getBlob(columnIndex);
				// check that bytes count does not exceed our buffer size
				tuple.putShort((short)bytesValue.length);
				tuple.put(bytesValue);
				break; }
			default:
				logger.warn("unhandled data type {}", type);
			}
		}
		// we only process one
		tupleCursor.close();
		tuple.flip();
		final byte[] tupleBytes = new byte[tuple.limit()];
		tuple.get(tupleBytes);
		PLogger.API_STORE.debug("terse tuple=[{}]", tuple);
		return ByteBufferFuture.wrap(tupleBytes);

	}

	/**
	 * @param context
	 * @param provider
	 * @param encoding
	 * @param data
	 * @return
	 */
	private static UriFuture deserializeTerseToProvider(final Context context, final String channelName,
			final Uri provider, final Encoding encoding, final byte[] data) {
		{
			final ContentResolver resolver = context.getContentResolver();
			/**
			 * 1) perform a query to get the field: names, types.
			 * 2) parse the incoming data using the order of the names
			 *    and their types as a guide.
			 */
			logger.debug("Using terse deserialization");

			final Cursor serialMetaCursor;
			try {
				serialMetaCursor = resolver.query(Uri.withAppendedPath(provider, "_data_type"), 
						null, null, null, null);
			} catch(IllegalArgumentException ex) {
				logger.warn("unknown content provider ", ex);
				return null;
			}
			if (serialMetaCursor == null) return null;

			if (! serialMetaCursor.moveToFirst()) {
				serialMetaCursor.close();
				return null;
			}
			int columnCount = serialMetaCursor.getColumnCount();
			if (columnCount < 1) {
				serialMetaCursor.close();
				return null;
			}

			final ByteBuffer tuple = ByteBuffer.wrap(data);
			final ContentValues wrap = new ContentValues();

			for (final String key : serialMetaCursor.getColumnNames()) {
				final int type = serialMetaCursor.getInt(serialMetaCursor.getColumnIndex(key));
				switch (FieldType.fromCode(type)) {
				case NULL:
					//wrap.put(key, null);
					break;
				case SHORT: {
					final short shortValue = tuple.getShort();
					wrap.put(key, shortValue);
					break; }
				case LONG:
				case FK: {
					final long longValue = tuple.getLong();
					wrap.put(key, longValue);
					break; }
				case TIMESTAMP: {
					final int intValue = tuple.getInt();
					final long longValue = 1000l*(long)intValue; // seconds --> milliseconds
					wrap.put(key, longValue);
					break; }
				case TEXT:
				case GUID: {
					final short textLength = tuple.getShort();
					if (textLength > 0) {
						try {
							byte [] textBytes = new byte[textLength];
							tuple.get(textBytes, 0, textLength);
							String textValue = new String(textBytes, "UTF8");
							wrap.put(key, textValue);
						} catch ( java.io.UnsupportedEncodingException ex ) {
							logger.error("Error in string encoding{}",
									new Object[] { ex.getStackTrace() } );
						}
					}
					// final char[] textValue = new char[textLength];
					// for (int ix=0; ix < textLength; ++ix) {
					// 	textValue[ix] = tuple.getChar();
					// }
					break; }
				case BOOL:
				case INTEGER:
				case EXCLUSIVE:
				case INCLUSIVE: {
					final int intValue = tuple.getInt();
					wrap.put(key, intValue);
					break; }
				case REAL:
				case FLOAT: {
					final double doubleValue = tuple.getDouble();
					wrap.put(key, doubleValue);
					break; }
				case BLOB: {
					final short bytesLength = tuple.getShort();
					if (bytesLength > 0) {
						final byte[] bytesValue = new byte[bytesLength];
						tuple.get(bytesValue, 0, bytesLength);
						wrap.put(key, bytesValue);
					}
					break; }
				default:
					logger.warn("unhandled data type {}", type);
				}
			}
			serialMetaCursor.close();

			wrap.put(AmmoProviderSchema._RECEIVED_DATE, System.currentTimeMillis());
			final StringBuilder sb = new StringBuilder()
			.append(AmmoProviderSchema.Disposition.REMOTE.name())
			.append('.')
			.append(channelName);
			wrap.put(AmmoProviderSchema._DISPOSITION, sb.toString());

			final Uri tupleUri = resolver.insert(provider, wrap);
			return new UriFuture(tupleUri);
		}

	}
}


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
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.os.RemoteException;
import android.util.SparseArray;
import edu.vu.isis.ammo.api.IDistributorAdaptor;
import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.NetworkManager;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.serializer.ContentValuesContentItem;
import edu.vu.isis.ammo.core.distributor.serializer.CustomAdaptorCache;
import edu.vu.isis.ammo.core.distributor.serializer.ISerializer;
import edu.vu.isis.ammo.core.distributor.serializer.JsonSerializer;
import edu.vu.isis.ammo.core.distributor.serializer.TerseSerializer;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;
import edu.vu.isis.ammo.util.ByteBufferAdapter;
import edu.vu.isis.ammo.util.ByteBufferInputStream;
import edu.vu.isis.ammo.util.ExpandoByteBufferAdapter;

/**
 * The purpose of these objects is lazily serialize an object. Once it has been
 * serialized a copy is kept.
 */
public class RequestSerializer {
	/* package */static final Logger logger = LoggerFactory
			.getLogger("dist.serializer");

	/**
	 * This enumeration's codes must match those of the AmmoGen files.
	 */
	public enum FieldType {
		/** no value specified */
		NULL(0),
		/** true or false */
		BOOL(1),
		/** Binary large object but not a file */
		BLOB(2),
		/** an approximation to a real number */
		FLOAT(3),
		/** either positive or negative integer */
		INTEGER(4),
		/** when an integer is not big enough */
		LONG(5),
		/** text string */
		TEXT(6),
		/** prefer over float */
		REAL(7),
		/** a foreign key reference */
		FK(8),
		/** a globally unique identifier */
		GUID(9),
		/** a single item from a set */
		EXCLUSIVE(10),
		/** a subset */
		INCLUSIVE(11),
		/** an integer timestamp in milliseconds */
		TIMESTAMP(12),
		/** a two byte integer */
		SHORT(13),
		/** the contents of a file, a named blob */
		FILE(14);

		private final int code;

		private FieldType(int code) {
			this.code = code;
		}

		private static final SparseArray<FieldType> codemap = 
              new SparseArray<FieldType>();
		static {
			for (FieldType t : FieldType.values()) {
				FieldType.codemap.put(t.code, t);
			}
		}

		private static final HashMap<String, FieldType> stringMap = new HashMap<String, FieldType>();
		static {
			stringMap.put("NULL", NULL);
			stringMap.put("BOOL", BOOL);
			stringMap.put("BLOB", BLOB);
			stringMap.put("FLOAT", FLOAT);
			stringMap.put("INTEGER", INTEGER);
			stringMap.put("LONG", LONG);
			stringMap.put("TEXT", TEXT);
			stringMap.put("REAL", REAL);
			stringMap.put("FK", FK);
			stringMap.put("GUID", GUID);
			stringMap.put("EXCLUSIVE", EXCLUSIVE);
			stringMap.put("INCLUSIVE", INCLUSIVE);
			stringMap.put("TIMESTAMP", TIMESTAMP);
			stringMap.put("SHORT", SHORT);
			stringMap.put("FILE", FILE);
		}

		public int toCode() {
			return this.code;
		}

		public static FieldType fromCode(final int code) {
			return FieldType.codemap.get(code);
		}

		public static FieldType fromContractString(String dtype) {
			return stringMap.get(dtype);
		}
	}

	/**
	 * The presence of the BLOB_MARKER_FIELD as the first byte in the footer for
	 * a blob data section indicates where the blob should be placed in the
	 * content provider.
	 */
	public static final byte BLOB_MARKER_FIELD = (byte) 0xff;

	/**
	 * The blob type enum makes a distinction between blobs which are small and
	 * those which are large. The basic idea is delivery and storage will be
	 * performed differently depending upon the relative size of the blob.
	 */
	public enum BlobTypeEnum {
		/** blob too large to send via binder */
		LARGE,
		/** blob sufficiently small to send via binder */
		SMALL;

		/**
		 * The difficulty here is that the blob may have trailing null bytes.
		 * e.g. fieldName = [data], fieldNameBlob= [[100, 97, 116, 97]], blob =
		 * [[100, 97, 116, 97, 0]] These should result it a match.
		 */
		public static BlobTypeEnum infer(String fieldName, byte[] blob) {

			final byte[] fieldNameBlob;
			try {
				fieldNameBlob = fieldName.getBytes("UTF-8");
			} catch (java.io.UnsupportedEncodingException ex) {
				return SMALL;
			}

			logger.trace(
					"processing blob fieldName = [{}], fieldNameBlob= [{}], blob = [{}]",
					fieldName, fieldNameBlob, blob);

			if (blob == null)
				return LARGE;
			if (blob.length < 1)
				return LARGE;

			if (fieldNameBlob.length == blob.length)
				return Arrays.equals(blob, fieldNameBlob) ? LARGE : SMALL;

			if (fieldNameBlob.length > blob.length)
				return SMALL;

			int i;
			for (i = 0; i < fieldNameBlob.length; i++) {
				if (fieldNameBlob[i] != blob[i])
					return SMALL;
			}
			for (; i < blob.length; i++) {
				if (blob[i] != (byte) 0)
					return SMALL;
			}
			return LARGE;
		}
	}

	public interface OnReady {
        public AmmoGatewayMessage run(Encoding encode, ByteBufferAdapter serialized);
	}

	public interface OnSerialize {
        public ByteBufferAdapter run(Encoding encode);
	}

	public final Provider provider;
	public final Payload payload;
	private OnReady readyActor;
	private OnSerialize serializeActor;
	private AmmoGatewayMessage agm;

	public static class BlobData {
		public final BlobTypeEnum blobType;
		public final byte[] blob;

		public BlobData(BlobTypeEnum blobType, byte[] blob) {
			this.blobType = blobType;
			this.blob = blob;
		}
	}

	public static class DeserializedMessage {
		public ContentValues cv;
		public Map<String, BlobData> blobs;

		public DeserializedMessage() {
			cv = new ContentValues();
			blobs = new HashMap<String, BlobData>();
		}
    /**
     * This maintains a set of persistent connections to content provider
     * adapter services.
     */
    final static private Map<String, IDistributorAdaptor> remoteServiceMap;
    static {
        remoteServiceMap = new HashMap<String, IDistributorAdaptor>(10);
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
			public void run(final Encoding encode) {
				logger.trace("serialize actor not defined {}", encode);
			}

            @Override
            public byte[] getBytes() {
                logger.trace("serialize actor not defined {}");
				return null;
			}
		};
	}

	public static RequestSerializer newInstance() {
		return new RequestSerializer(null, null);
	}

	public static RequestSerializer newInstance(Provider provider,
			Payload payload) {
		return new RequestSerializer(provider, payload);
	}

	/**
     * The primary function of the request serializer is to serialize and
     * deliver a request. This method fulfills that purpose.
     * <p>
     * This is performed by two actors registered with the serializer.
     * 
     * @param that
     * @param encode
     * @param channel
     * @return
     */
	public DisposalState act(final NetworkManager that, final Encoding encode,
			final String channel) {
		final RequestSerializer parent = RequestSerializer.this;
		final Encoding local_encode = encode;
		final String local_channel = channel;
		if (parent.agm == null) {
            final ByteBufferAdapter agmBytes = parent.serializeActor.run(local_encode);
            parent.agm = parent.readyActor.run(local_encode, agmBytes);
		}
		if (parent.agm == null)
			return null;

		return that.sendRequest(parent.agm, local_channel);
	}

	/**
	 * The actor sends the serialized data to its destination.
	 */
	public void setReadyActor(final OnReady onReady) {
		this.readyActor = onReady;
	}

	/**
	 * The serializer performs the serialization of the object to bytes.
	 * 
	 * @param onSerialize
	 */
	public void setSerializeActor(final OnSerialize onSerialize) {
		this.serializeActor = onSerialize;
	}

	/**
	 * Given a set of content values serialize them into a byte array. The type
	 * of serialization is controlled by the topic and the encoding. The meta
	 * data for the content values is specified by the contract which is
	 * extracted from the contract data store by the topic.
	 * 
	 * @param cv
	 * @param encoding
	 * @param topic
	 * @param contractStore
	 * @return
	 */
	public static ByteBufferAdapter serializeFromContentValues(ContentValues cv,
			final DistributorPolicy.Encoding encoding, final String topic,
			final ContractStore contractStore) {
		logger.trace("serializing using content values and encoding {}",
				encoding);

		final ISerializer serializer;
		switch (encoding.getType()) {
		case JSON: {		
            /* TODO proposed replacement
            return ByteBufferAdapter.obtain(encodeAsJson(cv));
            */
            serializer = new JsonSerializer();
			break;
		}

		case TERSE: {
			serializer = new TerseSerializer();
			break;
		}
		// TODO custom still needs a lot of work
		// It will presume the presence of a SyncAdaptor for the content
		// provider.
		case CUSTOM:
		default: {
			throw new UnsupportedOperationException(
					"Custom serialization from ContentValues is not supported");
		}
		}
		final ContractStore.Relation relation = contractStore
				.getRelationForType(topic);

		final ContentValuesContentItem item = new ContentValuesContentItem(cv,
				relation, encoding);

		byte[] result = null;

		try {
			result = serializer.serialize(item);
		} catch (IOException e) {
			logger.error(
					"IOException occurred while serializing from content values",
					e);
		}

		return result;
	}

   /**
   * The following proposed method localizes the encoding a bit.
   */
   private static byte[] encodeAsJson(ContentValues cv) {
        // encoding in json for now ...
        if (cv == null) {
            return null;
        }
        final Set<Map.Entry<String, Object>> data = cv.valueSet();
        final Iterator<Map.Entry<String, Object>> iter = data.iterator();
        final JSONObject json = new JSONObject();

        while (iter.hasNext())
        {
            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) iter.next();
            try {
                if (entry.getValue() instanceof String)
                    json.put(entry.getKey(), cv.getAsString(entry.getKey()));
                else if (entry.getValue() instanceof Integer)
                    json.put(entry.getKey(), cv.getAsInteger(entry.getKey()));
            } catch (JSONException ex) {
                logger.warn("cannot encode content values as json", ex);
                return null;
            }
        }

        return json.toString().getBytes();
    }

	/**
	 * Given a byte array, serialize it into a set of content values. The type
	 * of serialization is controlled by the topic and the encoding. The meta
	 * data for the content values is specified by the contract which is
	 * extracted from the contract data store by the topic.
	 * 
	 * @param data
	 * @param encoding
	 * @param mimeType
	 * @param contractStore
	 * @return
	 */
	public static ContentValues deserializeToContentValues(byte[] data,
			final DistributorPolicy.Encoding encoding, final String mimeType,
			final ContractStore contractStore) {

		final ISerializer serializer;
		switch (encoding.getType()) {
		case JSON: {
			serializer = new JsonSerializer();
		}
			break;
		case TERSE: {
			serializer = new TerseSerializer();
		}
			break;
		case CUSTOM:
		default: {
			throw new UnsupportedOperationException(
					"Custom serialization from ContentValues is not supported");
		}
		}
		final ContractStore.Relation relation = contractStore
				.getRelationForType(mimeType);

		final List<String> fieldNames = new ArrayList<String>(relation
				.getFields().size());
		final List<FieldType> dataTypes = new ArrayList<FieldType>(relation
				.getFields().size());

		for (ContractStore.Field f : relation.getFields()) {
			fieldNames.add(f.getName().getSnake());
			dataTypes.add(FieldType.fromContractString(f.getDtype()));
		}

		final DeserializedMessage msg = serializer.deserialize(data,
				fieldNames, dataTypes);

		for (Entry<String, BlobData> blobEntry : msg.blobs.entrySet()) {
			// TODO: this is going to put large blobs/files into our CV
			// object... might not be desirable
			msg.cv.put(blobEntry.getKey(), blobEntry.getValue().blob);
		}
		return msg.cv;
	}

	
	/**
	 * Deserialize the received data to the appropriate.
	 * 
	 * @param ammoAdaptorCache
	 * @param resolver
	 * @param channelName
	 * @param provider
	 * @param encoding
	 * @param data
	 * @return
	 */
	   public static Uri deserializeToProvider(final CustomAdaptorCache ammoAdaptorCache, 
	            final ContentResolver resolver, final String channelName,
	            final Uri provider, final Encoding encoding, final byte[] data) {
	      
               final ISerializer serializer;
               switch (encoding.getType()) {
               case CUSTOM:
                   ammoAdaptorCache.deserialize(provider, encoding, data);
                   return null;
               case JSON:
                   serializer = new JsonSerializer();
                   break;
               case TERSE:
                   serializer = new TerseSerializer();
                   break;
               default:
                   ammoAdaptorCache.deserialize(provider, encoding, data);
                   return null;
               }

               return RequestSerializer.deserializeToProviderLocal(
                       serializer, resolver,
                       channelName, provider, encoding, data);
	   }

	/**
	 * @see serializeFromProvider with which this method is symmetric.
	 */
	public static Uri deserializeToProviderLocal(final ISerializer serializer,
			final ContentResolver resolver, final String channelName,
			final Uri provider, final Encoding encoding, final byte[] data) {

		logger.debug("deserialize message <{}>", data);

		/**
		 * 1) perform a query to get the field: names, types.
		 * 
		 * 2) parse the incoming data using the order of the names and their
		 * types as a guide.
		 */
		// TODO: Move this someplace else? (Into ContentProviderContentItem?)
		Cursor serialMetaCursor = null;
		try {
            final Uri baseDataTypeUri = Uri.withAppendedPath(provider, "_data_type");

            final Uri encodingSpecificUri = Uri.withAppendedPath(baseDataTypeUri, encoding.name());

            try {
                serialMetaCursor = resolver.query(encodingSpecificUri, null, null, null, null);
            } catch (IllegalArgumentException ex) {
                logger.warn("Data-type specific metadata doesn't exist...  falling back to old behavior");
                //row didn't exist, move on to fallback behavior
            }

            if(serialMetaCursor == null) {
                //Fallback logic to maintain backwards compatibility...  always fall back to the _data_type URI
                //(intentionally different from ContentProviderContentItem fallback logic--  we only use this
                //metadata for terse right now, and we don't (in an old-style provider) have enough information
                //to do this for JSON)

                serialMetaCursor = resolver.query(baseDataTypeUri, null, null, null, null);
            }
		} catch (IllegalArgumentException ex) {
			logger.warn("unknown content provider", ex);
			return null;
		}
		if (serialMetaCursor == null) {
			return null;
		}
		if (!serialMetaCursor.moveToFirst()) {
			serialMetaCursor.close();
			return null;
		}
		int columnCount = serialMetaCursor.getColumnCount();
		if (columnCount < 1) {

			serialMetaCursor.close();
			return null;
		}

		final List<String> columnNames = Arrays.asList(serialMetaCursor.getColumnNames());
		final List<FieldType> dataTypes = new ArrayList<FieldType>(columnNames.size());

		for (final String key : columnNames) {
			dataTypes.add(FieldType.fromCode(serialMetaCursor
					.getInt(serialMetaCursor.getColumnIndex(key))));
		}

		final DeserializedMessage msg = serializer.deserialize(data, columnNames, dataTypes);

		final Uri tupleUri;
		try {
			tupleUri = resolver.insert(provider, msg.cv); // TBD SKN --- THIS IS
															// A
			// SYNCHRONOUS IPC? we
			// will block here for a
			// while ...
			if (tupleUri == null) {
				logger.warn("could not insert {} into {}", msg.cv, provider);
				return null;
			}
			logger.info("Deserialized Received message, content {}", msg.cv);
		} catch (SQLiteException ex) {
			logger.warn("invalid sql insert", ex);
			return null;
		} catch (IllegalArgumentException ex) {
			logger.warn("bad provider or values", ex);
			return null;
		}

		// TODO: decide if we want to do an early return if there aren't any
		// blobs
		// if (position == data.length)
		// return new UriFuture(tupleUri);

		msg.cv.put(AmmoProviderSchema._RECEIVED_DATE,
				System.currentTimeMillis());
		final StringBuilder sb = new StringBuilder()
				.append(AmmoProviderSchema.Disposition.REMOTE.name())
				.append('.').append(channelName);
		msg.cv.put(AmmoProviderSchema._DISPOSITION, sb.toString());

		// write the blob to the appropriate place
		// do this for each blob
		final long tupleId = ContentUris.parseId(tupleUri);
		final Uri.Builder uriBuilder = provider.buildUpon();
		final Uri.Builder updateTuple = ContentUris.appendId(uriBuilder,
				tupleId);

		int blobCount = 0;
		for (String fieldName : msg.blobs.keySet()) {
			BlobData blobData = msg.blobs.get(fieldName);
			switch (blobData.blobType) {
			case SMALL:
				blobCount++;
				msg.cv.put(fieldName, blobData.blob);
				break;
			default:
				final Uri fieldUri = updateTuple.appendPath(fieldName).build();
				try {
					PLogger.API_STORE.debug("write blob uri=[{}]", fieldUri);
					final OutputStream outstream = resolver
							.openOutputStream(fieldUri);
					if (outstream == null) {
						logger.error(
								"failed to open output stream to content provider: {} ",
								fieldUri);
						return null;
					}
					outstream.write(blobData.blob);
					outstream.close();
				} catch (SQLiteException ex) {
					logger.error(
							"in provider {} could not open output stream {}",
							fieldUri, ex.getLocalizedMessage());
				} catch (FileNotFoundException ex) {
					logger.error("blob file not found: {}", fieldUri, ex);
				} catch (IOException ex) {
					logger.error("error writing blob file: {}", fieldUri, ex);
				}
			}
		}
		if (blobCount > 0) {
			try {
				PLogger.API_STORE.debug("insert blob uri=[{}]", provider);
				final Uri blobUri = resolver.insert(provider, msg.cv);
				if (blobUri == null) {
					logger.warn("could not insert {} into {}", msg.cv, provider);
					return null;
				}
				logger.trace("Deserialized Received message blobs, content {}",
						msg.cv);

			} catch (SQLiteException ex) {
				logger.warn("invalid sql blob insert", ex);
				return null;
			} catch (IllegalArgumentException ex) {
				logger.warn("bad provider or blob values", ex);
				return null;
			}
		}

		return tupleUri;
	}

	/**
	 * A pair of functions which communicate with a Content Adaptor Service.
	 * These Content Adaptor Services are created by the code generator.
	 */
	public static byte[] serializeCustomFromProvider(
			final ContentResolver resolver, final Uri tupleUri,
			final DistributorPolicy.Encoding encoding)
			throws TupleNotFoundException, NonConformingAmmoContentProvider,
			IOException {

		final Uri serialUri = Uri.withAppendedPath(tupleUri,
				encoding.getPayloadSuffix());

		final Cursor tupleCursor;
		try {
			tupleCursor = resolver.query(serialUri, null, null, null, null);
		} catch (IllegalArgumentException ex) {
			logger.warn("unknown content provider ", ex);
			return null;
		}
		if (tupleCursor == null) {
			throw new TupleNotFoundException("while serializing from provider",
					tupleUri);
		}

		if (!tupleCursor.moveToFirst()) {
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
		return tupleString.getBytes();

	}


}

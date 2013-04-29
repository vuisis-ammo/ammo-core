package edu.vu.isis.ammo.core.distributor.serializer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.res.AssetFileDescriptor;
import android.os.ParcelFileDescriptor;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.BlobData;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.BlobTypeEnum;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.DeserializedMessage;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;

/**
 * @author jwilliams
 *
 */
public class JsonSerializer implements ISerializer {
    static final Logger logger = LoggerFactory.getLogger("dist.serializer.json");
    
    /**
     * The presence of the BLOB_MARKER_FIELD as the first byte in the footer for
     * a blob data section indicates where the blob should be placed in the
     * content provider.
     */
    public static final byte BLOB_MARKER_FIELD = (byte) 0xff;

    @Override
    public byte[] serialize(final IContentItem item) throws IOException {
        logger.debug("Serialize the non-blob data <{}>", item);
        
        final JSONObject json = new JSONObject();
        int countBinaryFields = 0;
        for (final String field : item.keySet()) {
            final FieldType type = item.getTypeForKey(field);
            switch (type) {
                case BLOB:
                    countBinaryFields++;
                    break;
                case FILE: {
                    countBinaryFields++;
                    final String value = item.getAsString(field);
                    try {
                        json.put(field, value);
                    } catch (JSONException ex) {
                        logger.warn("invalid content provider", ex);
                    }
                }
                    break;
                default: {
                    final String value = item.getAsString(field);
                    if (value == null || value.length() < 1)
                        continue;
                    try {
                        json.put(field, value);
                    } catch (JSONException ex) {
                        logger.warn("invalid content provider", ex);
                    }
                }
            }
        }
        final String jsonString = json.toString();
        final byte[] tuple = jsonString.getBytes();
        logger.debug("serialized payload <{}> <{}>", jsonString, tuple);
        if (countBinaryFields < 1) {
            return tuple;
        }

        logger.trace("loading larger tuple buffer");
        ByteArrayOutputStream bigTuple = null;
        try {
            bigTuple = new ByteArrayOutputStream();

            bigTuple.write(tuple);
            bigTuple.write(0x0);

            logger.trace("Serialize the blob data (if any)");
            final byte[] buffer = new byte[1024];
            for (final String field : item.keySet()) {
                final FieldType type = item.getTypeForKey(field);
                switch (type) {
                    case BLOB:
                    case FILE:
                        break;
                    default:
                        continue;
                }
                bigTuple.write(field.getBytes());
                bigTuple.write(0x0);

                switch (type) {
                    case BLOB:
                        final byte[] tempBlob = item.getAsByteArray(field);
                        final BlobTypeEnum tempBlobType = BlobTypeEnum.infer(field,
                                tempBlob);
                        switch (tempBlobType) {
                            case SMALL:
                                try {
                                    logger.trace("field name=[{}] blob=[{}]", field,
                                            tempBlob);
                                    final ByteBuffer fieldBlobBuffer = ByteBuffer
                                            .wrap(tempBlob);

                                    final ByteBuffer bb = ByteBuffer.allocate(4);
                                    bb.order(ByteOrder.BIG_ENDIAN);
                                    final int size = fieldBlobBuffer.capacity();
                                    bb.putInt(size);
                                    bigTuple.write(bb.array());

                                    bigTuple.write(fieldBlobBuffer.array());

                                    bigTuple.write(BLOB_MARKER_FIELD);
                                    bigTuple.write(bb.array(), 1, bb.array().length - 1);
                                } finally {
                                }
                                break;
                            case LARGE:
                            default:
                                break;
                        }
                        continue;
                    default:
                        break;
                }

                logger.trace("field name=[{}] ", field);
                try {
                    final AssetFileDescriptor afd = item.getAssetFileDescriptor(field);
                    if(afd != null) {
                        final ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();
    
                        final InputStream instream = new ParcelFileDescriptor.AutoCloseInputStream(
                                pfd);
                        final ByteBuffer fieldBlobBuffer;
                        ByteArrayOutputStream fieldBlob = null;
                        BufferedInputStream bis = null;
                        try {
                            bis = new BufferedInputStream(instream);
                            fieldBlob = new ByteArrayOutputStream();
                            for (int bytesRead = 0; (bytesRead = bis.read(buffer)) != -1;) {
                                fieldBlob.write(buffer, 0, bytesRead);
                            }
                            fieldBlobBuffer = ByteBuffer.wrap(fieldBlob.toByteArray());
                        } finally {
                            if (bis != null)
                                bis.close();
                            if (fieldBlob != null)
                                fieldBlob.close();
                        }
    
                        // write it out
                        final ByteBuffer bb = ByteBuffer.allocate(4);
                        bb.order(ByteOrder.BIG_ENDIAN);
                        final int size = fieldBlobBuffer.capacity();
                        bb.putInt(size);
                        bigTuple.write(bb.array());
    
                        bigTuple.write(fieldBlobBuffer.array());
                        bigTuple.write(bb.array());
                    } else {
                        logger.error("Didn't get an asset file descriptor for field {}", field);
                    }
                } catch (IOException ex) {
                    logger.trace("unable to create stream {}", field, ex);
                    throw new FileNotFoundException("Unable to create stream");
                }

            }
            final byte[] finalTuple = bigTuple.toByteArray();
            bigTuple.close();
            PLogger.API_STORE.debug("json tuple=[{}] size=[{}]",
                    tuple, finalTuple.length);
            PLogger.API_STORE.trace("json finalTuple=[{}]",
                    finalTuple);
            return finalTuple;

        } finally {
            if (bigTuple != null)
                bigTuple.close();
        }
    }

    @Override
    public DeserializedMessage deserialize(final byte[] data, final List<String> fieldNames,
            final List<FieldType> dataTypes) {
        final DeserializedMessage msg = new DeserializedMessage();
        final ByteBuffer dataBuff = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        // find the end of the json portion of the data
        int position = 0;
        for (; position < data.length && data[position] != (byte) 0x0; position++) {
        }

        final int length = position;
        final byte[] payload = new byte[length];
        System.arraycopy(data, 0, payload, 0, length);
        final JSONObject input;
        final String parsePayload = new String(payload);
        try {  
            final Object value = new JSONTokener(parsePayload).nextValue();
            if (value instanceof JSONObject) {
                input = (JSONObject) value;
                PLogger.API_STORE.trace("JSON payload=[{}]", value);
            } else if (value instanceof JSONArray) {
                PLogger.API_STORE.warn("invalid JSON payload=[{}]", parsePayload);
                return null;
            } else if (value == JSONObject.NULL) {
                PLogger.API_STORE.warn("null JSON payload=[{}]", parsePayload);
                return null;
            } else {
                PLogger.API_STORE.warn("{} JSON payload=[{}]", value.getClass().getName(),
                        parsePayload);
                return null;
            }
        } catch (ClassCastException ex) {
            PLogger.API_STORE.warn("invalid JSON content, <{}>", parsePayload, ex);
            return null;
        } catch (JSONException ex) {
            PLogger.API_STORE.warn("invalid JSON content, <{}>", parsePayload, ex);
            return null;
        }

        for (final Iterator<?> iter = input.keys(); iter.hasNext();) {
            final Object keyObj = iter.next();
            if (keyObj instanceof String) {
                final String key = (String) keyObj;
                final Object value;
                try {
                    value = input.get(key);
                } catch (JSONException ex) {
                    PLogger.API_STORE.error("invalid JSON key=[{}]", key, ex);
                    continue;
                }
                if (value == null) {
                    msg.cv.put(key, "");
                    PLogger.API_STORE.error("json value is null key=[{}]", key);
                    continue;
                } else if (value instanceof String) {
                    msg.cv.put(key, (String) value);
                } else if (value instanceof Boolean) {
                    msg.cv.put(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    msg.cv.put(key, (Integer) value);
                } else if (value instanceof Long) {
                    msg.cv.put(key, (Long) value);
                } else if (value instanceof Double) {
                    msg.cv.put(key, (Double) value);
                } else if (value instanceof JSONObject) {
                    PLogger.API_STORE.error(
                            "value has unexpected type=[JSONObject] key=[{}] value=[{}]", key,
                            value);
                    continue;
                } else if (value instanceof JSONArray) {
                    PLogger.API_STORE
                            .error("value has unexpected type=[JSONArray] key=[{}] value=[{}]",
                                    key, value);
                    continue;
                } else {
                    PLogger.API_STORE.error("value has unexpected type JSON key=[{}] value=[{}]",
                            key, value);
                    continue;
                }
            } else {
                PLogger.API_STORE.error("invalid JSON key=[{}]", keyObj);
            }
        }

        // if we're already at the end of the message, we're done
        if (position == data.length) {
            return msg;
        }

        // process the blobs
        position++; // move past the null terminator
        dataBuff.position(position);
        while (dataBuff.position() < data.length) {
            // get the field name
            final int nameStart = dataBuff.position();
            int nameLength;
            for (nameLength = 0; position < data.length; nameLength++, position++) {
                if (data[position] == 0x0)
                    break;
            }
            final String fieldName = new String(data, nameStart, nameLength);
            position++; // move past the null

            // get the last three bytes of the length, to be used as a simple
            // checksum
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
            dataBuff.position(blobStart + dataLength);

            // check for storage type
            final byte storageMarker = dataBuff.get();

            // get and compare the beginning and ending checksum
            final byte[] endingPsuedoChecksum = new byte[3];
            dataBuff.get(endingPsuedoChecksum);
            if (!Arrays.equals(endingPsuedoChecksum, beginningPsuedoChecksum)) {
                logger.error("blob checksum mismatch {} {}", endingPsuedoChecksum,
                        beginningPsuedoChecksum);
                break;
            }
            BlobTypeEnum blobType;

            if (storageMarker == BLOB_MARKER_FIELD) {
                blobType = BlobTypeEnum.SMALL;
            } else {
                blobType = BlobTypeEnum.LARGE;
            }
            msg.blobs.put(fieldName, new BlobData(blobType, blob));
        }

        return msg;
    }
    
    /** 
     * historical artifact
    
	 * The JSON serialization is of the following form...
	 * <dl>
	 * <dt>serialized tuple
	 * <dt>
	 * <dd>A list of non-null bytes which serialize the tuple, this is
	 * provided/supplied to the ammo enabled content provider via insert/query.
	 * The serialized tuple may be null terminated or the byte array may simply
	 * end.</dd>
	 * <dt>field blobs</dt>
	 * <dd>A list of name:value pairs where name is the field name and value is
	 * the field's data blob associated with that field. There may be multiple
	 * field blobs.
	 * <dl>
	 * <dt>field name</dt>
	 * <dd>A null terminated name,</dd>
	 * <dt>field data length</dt>
	 * <dd>A 4 byte big-endian integer length, indicating the number of bytes in
	 * the data blob.</dd>
	 * <dt>field data blob</dt>
	 * <dd>A set of bytes whose quantity is that of the field data length</dd>
	 * <dt>field data validation and metadata</dt>
	 * <dd>A 4 byte field, the validation quality is achieved by replicating the
	 * field data length. The metadata indicates the qualitative size of the
	 * blob.</dd>
	 * </dl>
	 * </dd>
	 * </dl>
	 * Note the serializeFromProvider and serializeFromProvider are symmetric,
	 * any change to one will necessitate a corresponding change to the other.
	
	public static ByteBufferFuture serializeJsonFromProvider(
			final ContentResolver resolver, final Uri tupleUri,
			final DistributorPolicy.Encoding encoding)
			throws TupleNotFoundException, NonConformingAmmoContentProvider,
			IOException {

		logger.trace("Serialize the non-blob data");

		// Asserted maximum useful size of trace logging message (e.g. size of
		// PLI msg)
		final int TRACE_CUTOFF_SIZE = 512;

		final Uri serialUri = Uri.withAppendedPath(tupleUri,
				encoding.getPayloadSuffix());
		Cursor tupleCursor = null;
		final byte[] tuple;
		final JSONObject json;
		try {
			try {
				tupleCursor = resolver.query(serialUri, null, null, null, null);
			} catch (IllegalArgumentException ex) {
				logger.warn("unknown content provider {}",
						ex.getLocalizedMessage());
				return null;
			}
			if (tupleCursor == null) {
				throw new TupleNotFoundException(
						"while serializing from provider", tupleUri);
			}
			if (tupleCursor.getCount() < 1) {
				logger.warn("tuple no longe present {}", tupleUri);
				return null;
			}
			if (!tupleCursor.moveToFirst()) {
				return null;
			}
			if (tupleCursor.getColumnCount() < 1) {
				return null;
			}

			json = new JSONObject();
			tupleCursor.moveToFirst();

			for (final String name : tupleCursor.getColumnNames()) {
				if (name.startsWith("_"))
					continue; // don't send the local fields

				final String value = tupleCursor.getString(tupleCursor
						.getColumnIndex(name));
				if (value == null || value.length() < 1)
					continue;
				try {
					json.put(name, value);
				} catch (JSONException ex) {
					logger.warn("invalid content provider", ex);
				}
			}
		} finally {
			if (tupleCursor != null)
				tupleCursor.close();
		}
		// FIXME FPE final Writer can we be more efficient? not copy bytes so
		// often?
		tuple = json.toString().getBytes();

		logger.info("Serialized message, content {}", json.toString());

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
				PLogger.API_STORE.debug("get blobs uri=[{}]", blobUri);
				blobCursor = resolver.query(blobUri, null, null, null, null);
			} catch (IllegalArgumentException ex) {
				logger.warn("unknown content provider", ex);
				return null;
			}
			if (blobCursor == null) {
				PLogger.API_STORE.debug("null blob json tuple=[{}]", tuple);
				return ByteBufferFuture.wrap(tuple);
			}
			if (!blobCursor.moveToFirst()) {
				PLogger.API_STORE.debug("no blob json tuple=[{}]", tuple);
				return ByteBufferFuture.wrap(tuple);
			}
			blobCount = blobCursor.getColumnCount();
			if (blobCount < 1) {
				PLogger.API_STORE.debug("empty blob json tuple=[{}]", tuple);
				return ByteBufferFuture.wrap(tuple);
			}

			logger.trace("getting the blob fields");
			final byte[] buffer = new byte[1024];
			for (int ix = 0; ix < blobCursor.getColumnCount(); ix++) {

				final String fieldName = blobCursor.getColumnName(ix);
				bigTuple.write(fieldName.getBytes());
				bigTuple.write(0x0);

				// "Manual merge" of fix for blob/file handling
				
				 * If it is a file field type the value is a string, If a blob
				 * then the field type is a blob, but you can not check the
				 * field type directly so we let the exception happen if we try
				 * the wrong type.
				
				@SuppressWarnings("unused")
				final String fileName;
				final byte[] blob;
				final BlobTypeEnum dataType;
				try {
					String tempFileName = null;
					byte[] tempBlob = null;
					BlobTypeEnum tempDataType = null;
					try {
						tempFileName = blobCursor.getString(ix);
						if (tempFileName == null || tempFileName.length() < 1) {
							tempDataType = BlobTypeEnum.SMALL;
						} else {
							tempDataType = BlobTypeEnum.LARGE;
						}
					} catch (Exception ex) {
						tempBlob = blobCursor.getBlob(ix);
						tempDataType = BlobTypeEnum.infer(fieldName, tempBlob);
					}
					fileName = tempFileName;
					blob = tempBlob;
					dataType = tempDataType;
				} catch (Exception ex) {
					logger.error(
							"something bad happened reading the field value",
							ex);
					continue;
				}

				switch (dataType) {
				case SMALL:
					try {
						PLogger.API_STORE.trace("small blob field name=[{}]",
								fieldName);
						logger.trace("field name=[{}] blob=[{}]", fieldName,
								blob);

						final ByteBuffer bb = ByteBuffer.allocate(4);
						bb.order(ByteOrder.BIG_ENDIAN);
						final int size = (blob == null) ? 0 : blob.length;
						bb.putInt(size);
						bigTuple.write(bb.array());
						if (blob != null) {
							bigTuple.write(blob);
						}
						bigTuple.write(BLOB_MARKER_FIELD);
						bigTuple.write(bb.array(), 1, bb.array().length - 1);
					} finally {
					}
					break;
				case LARGE:
					PLogger.API_STORE.trace("large blob field name=[{}]",
							fieldName);
					logger.trace("field name=[{}] ", fieldName);
					final Uri fieldUri = Uri.withAppendedPath(tupleUri,
							fieldName);
					try {
						final AssetFileDescriptor afd = resolver
								.openAssetFileDescriptor(fieldUri, "r");
						if (afd == null) {
							logger.warn("could not acquire file descriptor {}",
									fieldUri);
							throw new IOException(
									"could not acquire file descriptor "
											+ fieldUri);
						}
						final ParcelFileDescriptor pfd = afd
								.getParcelFileDescriptor();

						final InputStream instream = new ParcelFileDescriptor.AutoCloseInputStream(
								pfd);
						final BufferedInputStream bis = new BufferedInputStream(
								instream);
						final ByteArrayOutputStream fieldBlob = new ByteArrayOutputStream();
						for (int bytesRead = 0; (bytesRead = bis.read(buffer)) != -1;) {
							fieldBlob.write(buffer, 0, bytesRead);
						}
						bis.close();
						final ByteBuffer fieldBlobBuffer = ByteBuffer
								.wrap(fieldBlob.toByteArray());

						// write it out
						final ByteBuffer bb = ByteBuffer.allocate(4);
						bb.order(ByteOrder.BIG_ENDIAN);
						final int size = fieldBlobBuffer.capacity();
						bb.putInt(size);
						bigTuple.write(bb.array());

						bigTuple.write(fieldBlobBuffer.array());
						bigTuple.write(bb.array());
					} catch (SQLiteException ex) {
						logger.error("unable to create stream {}", serialUri,
								ex);
						continue;

					} catch (IOException ex) {
						logger.trace("unable to create stream {}", serialUri,
								ex);
						throw new FileNotFoundException(
								"Unable to create stream");
					} catch (Exception ex) {
						logger.error(
								"content provider unable to create stream {}",
								serialUri, ex);
						continue;
					}
					break;
				default:
					logger.warn("not a known data type {}", dataType);
				}
			}
		} finally {
			if (blobCursor != null)
				blobCursor.close();
		}

		final byte[] finalTuple = bigTuple.toByteArray();
		bigTuple.close();
		PLogger.API_STORE.debug("json tuple=[{}] size=[{}]", tuple,
				finalTuple.length);
		if (finalTuple.length <= TRACE_CUTOFF_SIZE) {
			PLogger.API_STORE.trace("json finalTuple=[{}]", finalTuple);
		} else {
			PLogger.API_STORE.trace("json tuple=[{}] size=[{}]", tuple,
					finalTuple.length);
		}
		return ByteBufferFuture.wrap(finalTuple);
	}

	
	 * This is the improved version of JSON encoding. It requests the meta data
	 * about the relation explicitly rather than trying to infer it from the
	 * returned data.
	
	public static ByteBufferFuture serializeJsonFromProvider2(
			final ContentResolver resolver, final Uri tupleUri,
			final DistributorPolicy.Encoding encoding)
			throws TupleNotFoundException, NonConformingAmmoContentProvider,
			IOException {

		
		 * 1) query to find out about the fields to send: name, position, type
		 * 2) serialize the fields
		
		logger.debug("Using json serialization");

		Cursor serialMetaCursor = null;
		final Map<String, FieldType> serialMap;
		final String[] serialOrder;
		try {
			try {
				final Uri dUri = Uri.withAppendedPath(tupleUri, "_data_type");
				serialMetaCursor = resolver.query(dUri, null, null, null, null);
			} catch (IllegalArgumentException ex) {
				logger.warn("unknown content provider ", ex);
				return null;
			}
			if (serialMetaCursor == null) {
				throw new NonConformingAmmoContentProvider(
						"while getting metadata from provider", tupleUri);
			}

			if (!serialMetaCursor.moveToFirst()) {
				return null;
			}
			final int columnCount = serialMetaCursor.getColumnCount();
			if (columnCount < 1) {
				return null;
			}

			serialMap = new HashMap<String, FieldType>(columnCount);
			serialOrder = new String[columnCount];
			int ix = 0;
			for (final String key : serialMetaCursor.getColumnNames()) {
				if (key.startsWith("_"))
					continue; // don't send any local fields
				final int value = serialMetaCursor.getInt(serialMetaCursor
						.getColumnIndex(key));
				serialMap.put(key, FieldType.fromCode(value));
				serialOrder[ix] = key;
				ix++;
			}
		} finally {
			if (serialMetaCursor != null)
				serialMetaCursor.close();
		}

		logger.trace("Serialize the non-binary data");
		Cursor tupleCursor = null;
		final byte[] tuple;
		try {
			try {
				tupleCursor = resolver.query(tupleUri, null, null, null, null);
			} catch (IllegalArgumentException ex) {
				logger.warn("unknown content provider ", ex);
				return null;
			}
			if (tupleCursor == null) {
				throw new TupleNotFoundException(
						"while serializing from provider", tupleUri);
			}

			if (!tupleCursor.moveToFirst()) {
				return null;
			}
			if (tupleCursor.getColumnCount() < 1) {
				logger.warn("tuple no longe present {}", tupleUri);
				return null;
			}

			logger.trace("Serialize the non-blob data");

			final JSONObject json = new JSONObject();
			int countBinaryFields = 0;
			for (final Map.Entry<String, FieldType> entry : serialMap
					.entrySet()) {
				final String name = entry.getKey();
				final FieldType type = entry.getValue();
				switch (type) {
				case BLOB:
					countBinaryFields++;
					break;
				case FILE: {
					countBinaryFields++;
					final String value = tupleCursor.getString(tupleCursor
							.getColumnIndex(name));
					try {
						json.put(name, value);
					} catch (JSONException ex) {
						logger.warn("invalid content provider", ex);
					}
				}
					break;
				default: {
					final String value = tupleCursor.getString(tupleCursor
							.getColumnIndex(name));
					if (value == null || value.length() < 1)
						continue;
					try {
						json.put(name, value);
					} catch (JSONException ex) {
						logger.warn("invalid content provider", ex);
					}
				}
				}
			}
			tuple = json.toString().getBytes();
			if (countBinaryFields < 1)
				return ByteBufferFuture.wrap(tuple);

			logger.trace("loading larger tuple buffer");
			ByteArrayOutputStream bigTuple = null;
			try {
				bigTuple = new ByteArrayOutputStream();

				bigTuple.write(tuple);
				bigTuple.write(0x0);

				logger.trace("Serialize the blob data (if any)");
				final byte[] buffer = new byte[1024];
				for (final Map.Entry<String, FieldType> entry : serialMap
						.entrySet()) {
					final String fieldName = entry.getKey();
					final FieldType type = entry.getValue();
					switch (type) {
					case BLOB:
					case FILE:
						break;
					default:
						continue;
					}
					bigTuple.write(fieldName.getBytes());
					bigTuple.write(0x0);

					switch (type) {
					case BLOB:
						final byte[] tempBlob = tupleCursor.getBlob(tupleCursor
								.getColumnIndex(entry.getKey()));
						final BlobTypeEnum tempBlobType = BlobTypeEnum.infer(
								fieldName, tempBlob);
						switch (tempBlobType) {
						case SMALL:
							try {
								logger.trace("field name=[{}] blob=[{}]",
										fieldName, tempBlob);
								final ByteBuffer fieldBlobBuffer = ByteBuffer
										.wrap(tempBlob);

								final ByteBuffer bb = ByteBuffer.allocate(4);
								bb.order(ByteOrder.BIG_ENDIAN);
								final int size = fieldBlobBuffer.capacity();
								bb.putInt(size);
								bigTuple.write(bb.array());

								bigTuple.write(fieldBlobBuffer.array());

								bigTuple.write(BLOB_MARKER_FIELD);
								bigTuple.write(bb.array(), 1,
										bb.array().length - 1);
							} finally {
							}
							break;
						case LARGE:
						default:
							break;
						}
						continue;
					default:
						break;
					}

					logger.trace("field name=[{}] ", fieldName);
					final Uri fieldUri = Uri.withAppendedPath(tupleUri,
							fieldName);
					try {
						final AssetFileDescriptor afd = resolver
								.openAssetFileDescriptor(fieldUri, "r");
						if (afd == null) {
							logger.warn("could not acquire file descriptor {}",
									fieldUri);
							throw new IOException(
									"could not acquire file descriptor "
											+ fieldUri);
						}
						final ParcelFileDescriptor pfd = afd
								.getParcelFileDescriptor();

						final InputStream instream = new ParcelFileDescriptor.AutoCloseInputStream(
								pfd);
						final ByteBuffer fieldBlobBuffer;
						ByteArrayOutputStream fieldBlob = null;
						BufferedInputStream bis = null;
						try {
							bis = new BufferedInputStream(instream);
							fieldBlob = new ByteArrayOutputStream();
							for (int bytesRead = 0; (bytesRead = bis
									.read(buffer)) != -1;) {
								fieldBlob.write(buffer, 0, bytesRead);
							}
							fieldBlobBuffer = ByteBuffer.wrap(fieldBlob
									.toByteArray());
						} finally {
							if (bis != null)
								bis.close();
							if (fieldBlob != null)
								fieldBlob.close();
						}

						// write it out
						final ByteBuffer bb = ByteBuffer.allocate(4);
						bb.order(ByteOrder.BIG_ENDIAN);
						final int size = fieldBlobBuffer.capacity();
						bb.putInt(size);
						bigTuple.write(bb.array());

						bigTuple.write(fieldBlobBuffer.array());
						bigTuple.write(bb.array());

					} catch (IOException ex) {
						logger.trace("unable to create stream {}", tupleUri, ex);
						throw new FileNotFoundException(
								"Unable to create stream");
					}

				}
				final byte[] finalTuple = bigTuple.toByteArray();
				bigTuple.close();
				PLogger.API_STORE.debug("json tuple=[{}] size=[{}]", tuple,
						finalTuple.length);
				PLogger.API_STORE.trace("json finalTuple=[{}]", finalTuple);
				return ByteBufferFuture.wrap(finalTuple);

			} finally {
				if (bigTuple != null)
					bigTuple.close();
			}

		} finally {
			if (tupleCursor != null)
				tupleCursor.close();
		}
	}

	
	 * JSON encoding (deprecated) This method interacts directly with the
	 * content provider. It should only be used with content providers which are
	 * known to be responsive.
	 * 
	 * @param context
	 * @param provider
	 * @param encoding
	 * @param data
	 * @return
	
	public static UriFuture deserializeJsonToProvider(final Context context,
			final ContentResolver resolver, final String channelName,
			final Uri provider, final Encoding encoding, final byte[] data) {

		DeserializedMessage msg = deserializeJson(data);

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
		return new UriFuture(tupleUri);
	}

	private static DeserializedMessage deserializeJson(byte[] data) {
		DeserializedMessage msg = new DeserializedMessage();
		final ByteBuffer dataBuff = ByteBuffer.wrap(data).order(
				ByteOrder.BIG_ENDIAN);
		// find the end of the json portion of the data
		int position = 0;
		for (; position < data.length && data[position] != (byte) 0x0; position++) {
		}

		final int length = position;
		final byte[] payload = new byte[length];
		System.arraycopy(data, 0, payload, 0, length);
		final JSONObject input;
		try {
			final String parsePayload = new String(payload);
			final Object value = new JSONTokener(parsePayload).nextValue();
			if (value instanceof JSONObject) {
				input = (JSONObject) value;
				PLogger.API_STORE.trace("JSON payload=[{}]", value);
			} else if (value instanceof JSONArray) {
				PLogger.API_STORE.warn("invalid JSON payload=[{}]",
						parsePayload);
				return null;
			} else if (value == JSONObject.NULL) {
				PLogger.API_STORE.warn("null JSON payload=[{}]", parsePayload);
				return null;
			} else {
				PLogger.API_STORE.warn("{} JSON payload=[{}]", value.getClass()
						.getName(), parsePayload);
				return null;
			}
		} catch (ClassCastException ex) {
			PLogger.API_STORE.warn("invalid JSON content", ex);
			return null;
		} catch (JSONException ex) {
			PLogger.API_STORE.warn("invalid JSON content", ex);
			return null;
		}

		for (final Iterator<?> iter = input.keys(); iter.hasNext();) {
			final Object keyObj = iter.next();
			if (keyObj instanceof String) {
				final String key = (String) keyObj;
				final Object value;
				try {
					value = input.get(key);
				} catch (JSONException ex) {
					PLogger.API_STORE.error("invalid JSON key=[{}]", key, ex);
					continue;
				}
				if (value == null) {
					msg.cv.put(key, "");
					PLogger.API_STORE.error("json value is null key=[{}]", key);
					continue;
				} else if (value instanceof String) {
					msg.cv.put(key, (String) value);
				} else if (value instanceof Boolean) {
					msg.cv.put(key, (Boolean) value);
				} else if (value instanceof Integer) {
					msg.cv.put(key, (Integer) value);
				} else if (value instanceof Long) {
					msg.cv.put(key, (Long) value);
				} else if (value instanceof Double) {
					msg.cv.put(key, (Double) value);
				} else if (value instanceof JSONObject) {
					PLogger.API_STORE
							.error("value has unexpected type=[JSONObject] key=[{}] value=[{}]",
									key, value);
					continue;
				} else if (value instanceof JSONArray) {
					PLogger.API_STORE
							.error("value has unexpected type=[JSONArray] key=[{}] value=[{}]",
									key, value);
					continue;
				} else {
					PLogger.API_STORE
							.error("value has unexpected type JSON key=[{}] value=[{}]",
									key, value);
					continue;
				}
			} else {
				PLogger.API_STORE.error("invalid JSON key=[{}]", keyObj);
			}
		}

		// if we're already at the end of the message, we're done
		if (position == data.length) {
			return msg;
		}

		// process the blobs
		position++; // move past the null terminator
		dataBuff.position(position);
		while (dataBuff.position() < data.length) {
			// get the field name
			final int nameStart = dataBuff.position();
			int nameLength;
			for (nameLength = 0; position < data.length; nameLength++, position++) {
				if (data[position] == 0x0)
					break;
			}
			final String fieldName = new String(data, nameStart, nameLength);
			position++; // move past the null

			// get the last three bytes of the length, to be used as a simple
			// checksum
			dataBuff.position(position);
			dataBuff.get();
			final byte[] beginningPsuedoChecksum = new byte[3];
			dataBuff.get(beginningPsuedoChecksum);

			// get the blob length for real
			dataBuff.position(position);
			final int dataLength = dataBuff.getInt();

			if (dataLength > dataBuff.remaining()) {
				logger.error("payload size is wrong {} {}", dataLength,
						data.length);
				return null;
			}
			// get the blob data
			final byte[] blob = new byte[dataLength];
			final int blobStart = dataBuff.position();
			System.arraycopy(data, blobStart, blob, 0, dataLength);
			dataBuff.position(blobStart + dataLength);

			// check for storage type
			final byte storageMarker = dataBuff.get();

			// get and compare the beginning and ending checksum
			final byte[] endingPsuedoChecksum = new byte[3];
			dataBuff.get(endingPsuedoChecksum);
			if (!Arrays.equals(endingPsuedoChecksum, beginningPsuedoChecksum)) {
				logger.error("blob checksum mismatch {} {}",
						endingPsuedoChecksum, beginningPsuedoChecksum);
				break;
			}
			BlobTypeEnum blobType;

			if (storageMarker == BLOB_MARKER_FIELD) {
				blobType = BlobTypeEnum.SMALL;
			} else {
				blobType = BlobTypeEnum.LARGE;
			}
			msg.blobs.put(fieldName, new BlobData(blobType, blob));
		}

		return msg;
	}

	*/

}

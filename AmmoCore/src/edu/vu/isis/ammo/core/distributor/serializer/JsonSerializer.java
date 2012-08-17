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
    static final Logger logger = LoggerFactory.getLogger("dist.serializer");
    
    /**
     * The presence of the BLOB_MARKER_FIELD as the first byte in the footer for
     * a blob data section indicates where the blob should be placed in the
     * content provider.
     */
    public static final byte BLOB_MARKER_FIELD = (byte) 0xff;

    @Override
    public byte[] serialize(IContentItem item) throws IOException {
        logger.trace("Serialize the non-blob data");
        
        byte[] tuple = null;

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
        tuple = json.toString().getBytes();
        if (countBinaryFields < 1)
            return tuple;

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
    public DeserializedMessage deserialize(byte[] data, List<String> fieldNames,
            List<FieldType> dataTypes) {
        DeserializedMessage msg = new DeserializedMessage();
        final ByteBuffer dataBuff = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
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

}

package edu.vu.isis.ammo.core.distributor.serializer;

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
    public byte[] serialize(IContentItem item) {
        // TODO Auto-generated method stub
        return null;
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

package edu.vu.isis.ammo.core.distributor.serializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.ByteBufferFuture;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.DeserializedMessage;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;

public class TerseSerializer implements ISerializer {

    @Override
    public byte[] serialize(IContentItem item) throws IOException {
        final ByteBuffer tuple = ByteBuffer.allocate(2048);

        // For the new serialization for the 152s, write the data we want to
        // tuple.
        for (final String key : item.getOrderedKeys()) {
            final FieldType type = item.getTypeForKey(key);
            switch (type) {
                case NULL:
                    break;
                case LONG:
                case FK: {
                    final long longValue = item.getAsLong(key);
                    tuple.putLong(longValue);
                    break;
                }
                case TIMESTAMP: {
                    final long longValue = item.getAsLong(key);
                    final int intValue = (int) (longValue / 1000); // SKN - we
                                                                   // will send
                                                                   // seconds
                                                                   // only on
                                                                   // serial
                    tuple.putInt(intValue);
                    break;
                }
                case TEXT:
                case GUID: {
                    // The database will return null if the string is empty,
                    // so detect that and write a zero length if it happens.
                    // Don't modify this code without testing on the serial
                    // channel using radios.
                    String svalue = item.getAsString(key);
                    
                    if(svalue != null) {
                        byte[] serializedString = svalue.getBytes("UTF8");
                        int length = serializedString.length;
                        if(length <= Short.MAX_VALUE) { 
                            tuple.putShort((short) length);
                            tuple.put(serializedString);
                        } else {
                            tuple.putShort((short) 0);
                            logger.warn("Omitting too-long string of length {}", length);
                        }
                    } else {
                        tuple.putShort((short) 0);
                    }
                    
                    break;
                }
                case SHORT: {
                    final short shortValue = item.getAsShort(key);
                    tuple.putShort(shortValue);
                    break;
                }
                case BOOL: {
                    final boolean boolValue = item.getAsBoolean(key);
                    byte byteValue;
                    if(boolValue == true) {
                        byteValue = 1;
                    } else {
                        byteValue = 0;
                    }
                    tuple.put(byteValue);
                    break;
                }
                case INTEGER:
                case EXCLUSIVE:
                case INCLUSIVE: {
                    final int intValue = item.getAsInteger(key);
                    tuple.putInt(intValue);
                    break;
                }
                case REAL:
                case FLOAT: {
                    final double doubleValue = item.getAsDouble(key);
                    tuple.putDouble(doubleValue);
                    break;
                }
                case BLOB: {
                    final byte[] bytesValue = item.getAsByteArray(key);
                    // check that bytes count does not exceed our buffer size
                    tuple.putShort((short) bytesValue.length);
                    tuple.put(bytesValue);
                    break;
                }
                default:
                    logger.warn("unhandled data type {}", type);
            }
        }
        // we only process one
        tuple.flip();
        final byte[] tupleBytes = new byte[tuple.limit()];
        tuple.get(tupleBytes);
        PLogger.API_STORE.debug("terse tuple=[{}]", tuple);
        return tupleBytes;
    }

    @Override
    public DeserializedMessage deserialize(byte[] data, List<String> fieldNames,
            List<FieldType> dataTypes) {
        // TODO Auto-generated method stub
        return null;
    }

}

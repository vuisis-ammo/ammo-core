package edu.vu.isis.ammo.core.distributor.serializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.DeserializedMessage;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;

public class TerseSerializer implements ISerializer {
    static final Logger logger = LoggerFactory.getLogger("dist.serializer.terse");

    /**
     * The tuple must be created before calling this method.
     * final ByteBuf tuple = Unpooled.buffer(2048)
     * should do the trick.
     *
     * @param tuple
     * @param item The item to be serialized
     * @return
     * @throws IOException
     */
    @Override
    public ByteBuf serialize(final ByteBuf tuple, final IContentItem item) throws IOException {
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
                    tuple.writeLong(longValue);
                    break;
                }
                case TIMESTAMP: {
                    final long longValue = item.getAsLong(key);
                    final int intValue = (int) (longValue / 1000); // SKN - we
                                                                   // will send
                                                                   // seconds
                                                                   // only on
                                                                   // serial
                    tuple.writeInt(intValue);
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
                            tuple.writeShort((short) length);
                            tuple.writeBytes(serializedString);
                        } else {
                            tuple.writeShort((short) 0);
                            logger.warn("Omitting too-long string of length {}", length);
                        }
                    } else {
                        tuple.writeShort((short) 0);
                    }
                    
                    break;
                }
                case SHORT: {
                    final short shortValue = item.getAsShort(key);
                    tuple.writeShort(shortValue);
                    break;
                }
                case BOOL: {
                    final boolean boolValue = item.getAsBoolean(key);
                    final int byteValue;
                    if(boolValue == true) {
                        byteValue = 1;
                    } else {
                        byteValue = 0;
                    }
                    tuple.writeByte(byteValue);
                    break;
                }
                case INTEGER:
                case EXCLUSIVE:
                case INCLUSIVE: {
                    final int intValue = item.getAsInteger(key);
                    tuple.writeInt(intValue);
                    break;
                }
                case REAL:
                case FLOAT: {
                    final double doubleValue = item.getAsDouble(key);
                    tuple.writeDouble(doubleValue);
                    break;
                }
                case BLOB: {
                    final byte[] bytesValue = item.getAsByteArray(key);
                    if(bytesValue != null) {
                    // check that bytes count does not exceed our buffer size
                    tuple.writeShort((short) bytesValue.length);
                    tuple.writeBytes(bytesValue);
                    } else {
                        tuple.writeShort((short) 0);
                    }
                    break;
                }
                default:
                    logger.warn("unhandled data type {}", type);
            }
        }
        PLogger.API_STORE.debug("terse tuple=[{}]", tuple);
        return tuple;
    }

    @Override
    public DeserializedMessage deserialize(final ByteBuf tuple,
                                           final List<String> fieldNames, final List<FieldType> dataTypes)
    {
        final DeserializedMessage decodedObject = new DeserializedMessage();

        int i = 0;
        for (String key : fieldNames) {
            FieldType type = dataTypes.get(i);
            i++;
            switch (type) {
                case NULL:
                    // wrap.put(key, null);
                    break;
                case SHORT: {
                    final short shortValue = tuple.readShort();
                    decodedObject.cv.put(key, shortValue);
                    break;
                }
                case LONG:
                case FK: {
                    final long longValue = tuple.readLong();
                    decodedObject.cv.put(key, longValue);
                    break;
                }
                case TIMESTAMP: {
                    final int intValue = tuple.readInt();
                    final long longValue = 1000l * (long) intValue; // seconds
                                                                    // -->
                                                                    // milliseconds
                    decodedObject.cv.put(key, longValue);
                    break;
                }
                case TEXT:
                case GUID: {
                    final short textLength = tuple.readShort();
                    if (textLength > 0) {
                        try {
                            byte[] textBytes = new byte[textLength];
                            tuple.readBytes(textBytes, 0, textLength);
                            String textValue = new String(textBytes, "UTF8");
                            decodedObject.cv.put(key, textValue);
                        } catch (java.io.UnsupportedEncodingException ex) {
                            logger.error("Error in string encoding", ex);
                        }
                    }
                    // final char[] textValue = new char[textLength];
                    // for (int ix=0; ix < textLength; ++ix) {
                    // textValue[ix] = tuple.getChar();
                    // }
                    break;
                }
                case BOOL:
                case INTEGER:
                case EXCLUSIVE:
                case INCLUSIVE: {
                    final int intValue = tuple.readInt();
                    decodedObject.cv.put(key, intValue);
                    break;
                }
                case REAL:
                case FLOAT: {
                    final double doubleValue = tuple.readDouble();
                    decodedObject.cv.put(key, doubleValue);
                    break;
                }
                case BLOB: {
                    final short bytesLength = tuple.readShort();
                    if (bytesLength > 0) {
                        final byte[] bytesValue = new byte[bytesLength];
                        tuple.readBytes(bytesValue, 0, bytesLength);
                        //TODO: put this in the DecodedMessage blob field like in the JSON serializer
                        decodedObject.cv.put(key, bytesValue);                     }
                    break;
                }
                case FILE: {
                	throw new UnsupportedOperationException("no file");
                }
                default:
                    logger.warn("unhandled data type {}", type);
            }
        }
        return decodedObject;
    }

}

/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package edu.vu.isis.ammo.core.distributor.serializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.DeserializedMessage;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;

public class TerseSerializer implements ISerializer {
    static final Logger logger = LoggerFactory.getLogger("dist.serializer.terse");

    @Override
    public byte[] serialize(final IContentItem item) throws IOException {
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
                    if(bytesValue != null) {
                    // check that bytes count does not exceed our buffer size
                    tuple.putShort((short) bytesValue.length);
                    tuple.put(bytesValue);
                    } else {
                        tuple.putShort((short) 0);
                    }
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
    public DeserializedMessage deserialize(final byte[] data, final List<String> fieldNames,
            final List<FieldType> dataTypes) {
        final DeserializedMessage decodedObject = new DeserializedMessage();

        final ByteBuffer tuple = ByteBuffer.wrap(data);
        int i = 0;
        for (String key : fieldNames) {
            FieldType type = dataTypes.get(i);
            i++;
            switch (type) {
                case NULL:
                    // wrap.put(key, null);
                    break;
                case SHORT: {
                    final short shortValue = tuple.getShort();
                    decodedObject.cv.put(key, shortValue);
                    break;
                }
                case LONG:
                case FK: {
                    final long longValue = tuple.getLong();
                    decodedObject.cv.put(key, longValue);
                    break;
                }
                case TIMESTAMP: {
                    final int intValue = tuple.getInt();
                    final long longValue = 1000l * (long) intValue; // seconds
                                                                    // -->
                                                                    // milliseconds
                    decodedObject.cv.put(key, longValue);
                    break;
                }
                case TEXT:
                case GUID: {
                    final short textLength = tuple.getShort();
                    if (textLength > 0) {
                        try {
                            byte[] textBytes = new byte[textLength];
                            tuple.get(textBytes, 0, textLength);
                            String textValue = new String(textBytes, "UTF8");
                            decodedObject.cv.put(key, textValue);
                        } catch (java.io.UnsupportedEncodingException ex) {
                            logger.error("Error in string encoding{}",
                                    new Object[] {
                                        ex.getStackTrace()
                                    });
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
                    final int intValue = tuple.getInt();
                    decodedObject.cv.put(key, intValue);
                    break;
                }
                case REAL:
                case FLOAT: {
                    final double doubleValue = tuple.getDouble();
                    decodedObject.cv.put(key, doubleValue);
                    break;
                }
                case BLOB: {
                    final short bytesLength = tuple.getShort();
                    if (bytesLength > 0) {
                        final byte[] bytesValue = new byte[bytesLength];
                        tuple.get(bytesValue, 0, bytesLength);
                        decodedObject.cv.put(key, bytesValue); //TODO: put this in the DecodedMessage blob field like in the JSON serializer
                    }
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

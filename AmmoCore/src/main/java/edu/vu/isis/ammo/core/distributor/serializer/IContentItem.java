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

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

import android.content.res.AssetFileDescriptor;

import edu.vu.isis.ammo.core.distributor.RequestSerializer;

/**
 * Represents an item that could have been extracted from a
 * content provider.  There are two initial implementations
 * of this object; one that wraps a cursor returned from a
 * content provider, and one that wraps a ContentValues
 * object.
 * 
 * This class is used in the RequestSerializer, to provide
 * a common interface for serialization of data contained in
 * a provider and contained in an independent ContentValues
 * object (not in a content provider).
 * 
 * @author jwilliams
 */
public interface IContentItem extends Closeable {
    
    /**
     * Closes any underlying resources held by this content item.  This should
     * be called when the serializer is done with this item, to allow resources
     * such as database cursors to be closed in a timely manner.
     */
    public void close();
    
    /**
     * Gets a set of all the keys.
     * @return a set of all the keys
     */
    public Set<String> keySet();
    
    /**
     * Gets an array of all the keys, in serialization order.
     * @return an array of all the keys, in the order that they
     * should be serialized.
     */
    public String[] getOrderedKeys();
    
    /**
     * Gets the type of a key (as specified by the contract or content provider).
     * @param key
     * @return the type of the key
     */
    public RequestSerializer.FieldType getTypeForKey(String key);
    
    public AssetFileDescriptor getAssetFileDescriptor(String field) throws IOException;
    
    /**
     * Gets a value.
     * @param key the value to get
     * @return the data for the value.
     */
    public Object get(String key);
    
    /**
     * Gets a value and converts it to a Boolean.
     * 
     * @param key the value to get
     * @return the Boolean value, or null if the value is missing or not convertible
     */
    public Boolean getAsBoolean(String key);
    
    /**
     * Gets a value and converts it to a Byte.
     * 
     * @param key the value to get
     * @return the Byte value, or null if the value is missing or not convertible
     */
    public Byte getAsByte(String key);
    
    /**
     * Gets a value and converts it to a byte array.
     * 
     * @param key the value to get
     * @return the byte array, or null if the value is missing or not convertible
     */
    public byte[] getAsByteArray(String key);
    
    /**
     * Gets a value and converts it to a Double.
     * 
     * @param key the value to get
     * @return the Double value, or null if the value is missing or not convertible
     */
    public Double getAsDouble(String key);
    
    /**
     * Gets a value and converts it to a Float.
     * 
     * @param key the value to get
     * @return the Float value, or null if the value is missing or not convertible
     */
    public Float getAsFloat(String key);
    
    /**
     * Gets a value and converts it to a Integer.
     * 
     * @param key the value to get
     * @return the Integer value, or null if the value is missing or not convertible
     */
    public Integer getAsInteger(String key);
    
    /**
     * Gets a value and converts it to a Long.
     * 
     * @param key the value to get
     * @return the Long value, or null if the value is missing or not convertible
     */
    public Long getAsLong(String key);
    
    /**
     * Gets a value and converts it to a Short.
     * 
     * @param key the value to get
     * @return the Short value, or null if the value is missing or not convertible
     */
    public Short getAsShort(String key);
    
    /**
     * Gets a value and converts it to a String.
     * 
     * @param key the value to get
     * @return the String value, or null if the value is missing or not convertible
     */
    public String getAsString(String key);
    
}

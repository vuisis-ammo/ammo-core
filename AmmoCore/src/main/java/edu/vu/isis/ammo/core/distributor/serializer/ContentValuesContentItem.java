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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;

import edu.vu.isis.ammo.core.distributor.ContractStore;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;

public class ContentValuesContentItem implements IContentItem {
    static final Logger logger = LoggerFactory.getLogger("dist.serializer.cursoritem");
    
    private final ContentValues cv;
    private final Map<String, FieldType> fieldTypes;
    private String[] serialOrder;
    private Set<String> serialSet;
    
    public ContentValuesContentItem(ContentValues cv, ContractStore.Relation relation, Encoding encoding) {
        this.cv = cv;
        
        fieldTypes = new HashMap<String, FieldType>(cv.size());
        
        int i = 0;
        
        for (ContractStore.Field f : relation.getFields()) {
            fieldTypes.put(f.getName().getSnake(), FieldType.fromContractString(f.getDtype()));
        }
        
        //Get the message object for this encoding, if it exists
        boolean foundEncoding = false;
        for(ContractStore.Message m : relation.getMessages()) {
            if(m.getEncoding().equals(encoding.name())) {
                foundEncoding = true;
                serialOrder = new String[m.getFields().size()];
                serialSet = new HashSet<String>();
                
                for(ContractStore.MessageFieldRef f : m.getFields()) {
                    serialOrder[i] = f.getName().getSnake();
                    serialSet.add(f.getName().getSnake());
                    i++;
                    
                    //Serialized types can be overridden on a per-encoding basis
                    if(!f.getType().equals("")) {
                        fieldTypes.put(f.getName().getSnake(), FieldType.fromContractString(f.getType()));
                    }
                }
            }
        }
        
        if(!foundEncoding) {
            //if we didn't find an encoding-specific message, we fall back to serializing all fields,
            //in the order they appear in the contract
            serialSet = new HashSet<String>();
            serialOrder = new String[relation.getFields().size()];
            for (ContractStore.Field f : relation.getFields()) {
                serialOrder[i] = f.getName().getSnake();
                i++;
                serialSet.add(f.getName().getSnake());
            }
        }
    }

    @Override
    public void close() {
        //don't have anything to close
    }

    @Override
    public Set<String> keySet() {
        return serialSet;
    }
    
    public String[] getOrderedKeys() {
        return serialOrder;
    }

    @Override
    public FieldType getTypeForKey(String key) {
        return fieldTypes.get(key);
    }

    @Override
    public Object get(String key) {
        return cv.get(key);
    }

    @Override
    public Boolean getAsBoolean(String key) {
        return cv.getAsBoolean(key);
    }

    @Override
    public Byte getAsByte(String key) {
        return cv.getAsByte(key);
    }

    @Override
    public byte[] getAsByteArray(String key) {
        return cv.getAsByteArray(key);
    }

    @Override
    public Double getAsDouble(String key) {
        return cv.getAsDouble(key);
    }

    @Override
    public Float getAsFloat(String key) {
        return cv.getAsFloat(key);
    }

    @Override
    public Integer getAsInteger(String key) {
        return cv.getAsInteger(key);
    }

    @Override
    public Long getAsLong(String key) {
        return cv.getAsLong(key);
    }

    @Override
    public Short getAsShort(String key) {
        return cv.getAsShort(key);
    }

    @Override
    public String getAsString(String key) {
        return cv.getAsString(key);
    }

    @Override
    public AssetFileDescriptor getAssetFileDescriptor(String field) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public String toString() {
        return this.toString(new StringBuilder()).toString();
    }
    
    public StringBuilder toString(final StringBuilder sb) {
        sb.append("values: ").append(this.cv).append(", ");
        sb.append("field types: ").append('\n');
        for (final Entry<String, FieldType> entry : this.fieldTypes.entrySet()) {
            sb.append(entry.getKey()).append(" -> ").append(entry.getValue()).append(",\n");
        }
        sb.append("serial set: ").append('\n');
        for (final String entry : this.serialSet) {
            sb.append(entry).append(",\n");
        }
        return sb;
    }


}

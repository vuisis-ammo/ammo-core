package edu.vu.isis.ammo.core.distributor.serializer;

import java.util.List;

import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;

import android.content.ContentValues;

public interface ISerializer {
    /**
     * Serializes a content item.
     * 
     * @param item The item to be serialized
     * @return the serialized content
     */
    public byte[] serialize(IContentItem item);
    
    /**
     * Deserializes a content item.
     * 
     * @param data The data to deserialize from.
     * @param fieldNames an ordered list of field names
     * @param dataTypes an ordered list of data types for the fields in fieldNames
     * @return a DeserializedMessage object containing the deserialized data
     */
    public DeserializedMessage deserialize(byte[] data, List<String> fieldNames, List<FieldType> dataTypes);
}

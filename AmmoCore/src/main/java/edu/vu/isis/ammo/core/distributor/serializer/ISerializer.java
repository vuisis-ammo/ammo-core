package edu.vu.isis.ammo.core.distributor.serializer;

import java.io.IOException;
import java.util.List;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.distributor.RequestSerializer.DeserializedMessage;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;

public interface ISerializer {
    static final Logger logger = LoggerFactory.getLogger("dist.serializer");
    /**
     * Serializes a content item.
     * 
     * @param item The item to be serialized
     * @return the serialized content
     * @throws IOException 
     */
    public ByteBuf serialize(final ByteBuf buf, final IContentItem item) throws IOException;
    
    /**
     * Deserializes a content item.
     * 
     * @param data The data to deserialize from.
     * @param fieldNames an ordered list of field names
     * @param dataTypes an ordered list of data types for the fields in fieldNames
     * @return a DeserializedMessage object containing the deserialized data
     */
    public DeserializedMessage deserialize(final ByteBuf data, final List<String> fieldNames, final List<FieldType> dataTypes);
}

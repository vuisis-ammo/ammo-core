package edu.vu.isis.ammo.core.distributor.serializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;

import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.distributor.NonConformingAmmoContentProvider;
import edu.vu.isis.ammo.core.distributor.TupleNotFoundException;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;



public class ContentProviderContentItem implements IContentItem {
    static final Logger logger = LoggerFactory.getLogger("dist.serializer.cursoritem");
    
    private Cursor cursor;
    private final ContentResolver resolver;
    private final Uri tupleUri;
    private Map<String, FieldType> fieldMap;
    private String[] serialOrder;
    
    public ContentProviderContentItem(Uri tupleUri, ContentResolver res, Encoding encoding) throws NonConformingAmmoContentProvider, TupleNotFoundException {
        this.cursor = null;
        this.resolver = res;
        this.tupleUri = tupleUri;
        
        //Preload the list of keys and their types
        Cursor serialMetaCursor = null;
        Cursor blobMetaCursor = null;
        
        this.fieldMap = null;
        serialOrder = null;
        
        boolean jsonFallbackMode = false;
        
        try {
            try {
                final Uri baseDataTypeUri = Uri.withAppendedPath(tupleUri, "_data_type");
                
                final Uri encodingSpecificUri = Uri.withAppendedPath(baseDataTypeUri, encoding.name());
                
                try {
                    serialMetaCursor = resolver.query(encodingSpecificUri, null, null, null, null);
                } catch (IllegalArgumentException ex) {
                    logger.warn("Data-type specific metadata doesn't exist...  falling back to old behavior");
                    //row didn't exist, move on to fallback behavior
                }
                blobMetaCursor = null; //only used by JSON serialization as a fallback
                
                if(serialMetaCursor == null) {
                    //Fallback logic to maintain backwards compatibility...  if terse, we fall back to
                    //the _data_type URI; if json, we use the old heuristics (pull names of columns
                    //containing strings from /_serial; pull names of blobs from /_blob)
                    switch(encoding.getType()) {
                        case TERSE:
                            serialMetaCursor = resolver.query(baseDataTypeUri, null, null, null, null);
                            break;
                        case JSON:
                            final Uri serialUri = Uri.withAppendedPath(tupleUri, encoding.getPayloadSuffix());
                            serialMetaCursor = resolver.query(serialUri, null, null, null, null);
                            
                            //Note:  blobMetaCursor can be null even in the old-style providers, if the provider
                            //doesn't have any blobs
                            final Uri blobUri = Uri.withAppendedPath(tupleUri, "_blob");
                            blobMetaCursor = resolver.query(blobUri, null, null, null, null);
                            
                            jsonFallbackMode = true;
                            break;
                        default:
                            //Custom encoding (for now) falls back to the same way terse does it
                            serialMetaCursor = resolver.query(baseDataTypeUri, null, null, null, null);
                            break;
                    }
                    
                }
            } catch (IllegalArgumentException ex) {
                logger.warn("unknown content provider ", ex);
                return;
            }
            if (serialMetaCursor == null) {
                throw new NonConformingAmmoContentProvider("while getting metadata from provider",
                        tupleUri);
            }

            if (!serialMetaCursor.moveToFirst()) {
                //TODO: log a warning or throw an exception here
                return;
            }
            final int columnCount = serialMetaCursor.getColumnCount();
            if (columnCount < 1) {
                //TODO: log a warning or throw an exception here
                return;
            }

            fieldMap = new HashMap<String, FieldType>(columnCount);
            serialOrder = new String[columnCount];
            int ix = 0;
            for (final String key : serialMetaCursor.getColumnNames()) {
                if (key.startsWith("_")) {
                    continue; // don't send any local fields
                }
                int columnIndex = serialMetaCursor.getColumnIndex(key);
                final int value = serialMetaCursor.getInt(columnIndex);
                if(jsonFallbackMode == false) {
                    fieldMap.put(key, FieldType.fromCode(value));
                } else {
                    //If blobMetaCursor is non-null, we're in fallback mode for JSON encoding...
                    //treat all fields from this cursor as string-typed (since we don't have access
                    //to that metadata)
                    logger.warn("Putting key {} of type TEXT", key);
                    fieldMap.put(key,  FieldType.TEXT);
                }
                serialOrder[ix] = key;
                ix++;
            }
            
            //If blobMetaCursor is non-null, we're in fallback mode for JSON encoding 
            //and the table has blobs...  look up blob fields in that cursor
            if(blobMetaCursor != null) {
                
                if(blobMetaCursor.getCount() > 1) {
                    logger.warn("Returned blobMetaCursor has {} rows (should be 1)", cursor.getCount());
                }
                
                if (blobMetaCursor.moveToFirst()) {
                    //TODO: warn on this condition
                
                    if (blobMetaCursor.getColumnCount() >= 1) {
                        for(final String key : blobMetaCursor.getColumnNames()) {
                            if(key.startsWith("_")) {
                                continue; //don't send any local fields
                            }
                            
                            
                           //identify whether this is a blob or a file (using the bad old heuristic)
                            try {
                                String tempFileName = blobMetaCursor.getString(blobMetaCursor.getColumnIndex(key));
                                if (tempFileName == null || tempFileName.length() < 1) {
                                    logger.warn("Putting key {} of type BLOB", key);
                                    fieldMap.put(key,  FieldType.BLOB);
                                } else {
                                    logger.warn("Putting key {} of type FILE", key);
                                    fieldMap.put(key,  FieldType.FILE);
                                }
                            } catch (Exception ex) {
                                logger.warn("Putting key {} of type BLOB", key);
                                fieldMap.put(key,  FieldType.BLOB);
                            }
                        }
                    } else {
                        logger.warn("blobMetaCursor tuple no longer present {}", tupleUri);
                    }
                } else {
                    logger.warn("blobMetaCursor couldn't move to first row");
                }
            }
            
        } finally {
            if (serialMetaCursor != null) {
                serialMetaCursor.close();
            }
            
            if(blobMetaCursor != null) {
                blobMetaCursor.close();
            }
        }
        
       
        //Get the cursor to our object
        try {
            cursor = resolver.query(tupleUri, null, null, null, null);
        } catch (IllegalArgumentException ex) {
            logger.warn("unknown content provider ", ex);
            return;
        }
        if (cursor == null) {
            throw new TupleNotFoundException("while serializing from provider", tupleUri);
        }
        
        if(cursor.getCount() > 1) {
            logger.warn("Returned cursor has {} rows (should be 1)", cursor.getCount());
        }

        if (!cursor.moveToFirst()) {
            //TODO: warn on this condition
            return;
        }
        if (cursor.getColumnCount() < 1) {
            logger.warn("tuple no longer present {}", tupleUri);
            return;
        }
    }

    @Override
    public Set<String> keySet() {
        if(fieldMap != null && cursor != null) {
            return fieldMap.keySet();
        } else {
            return null; //probably should throw an exception here
        }
    }
    
    @Override
    /**
     * Gets the list of keys, in serialization order.
     * 
     * Note:  For JSON in fallback mode (no json-specific data type list exists), this list
     * WILL NOT include BLOB-typed fields (since they're accessed through a separate cursor).
     * Therefore, the JSON encoder should use the keyset() method instead, to get keys from
     * the unordered set of all keys.
     */
    public String[] getOrderedKeys() {
        return serialOrder;
    }

    @Override
    public FieldType getTypeForKey(String key) {
        if(fieldMap != null && cursor != null) {
            return fieldMap.get(key);
        } else {
            return null; //probably should throw an exception here
        }
    }
    
    @Override
    public AssetFileDescriptor getAssetFileDescriptor(String field) throws IOException {
        final Uri fieldUri = Uri.withAppendedPath(tupleUri, field);
        final AssetFileDescriptor afd = resolver.openAssetFileDescriptor(fieldUri,
                "r");
        if (afd == null) {
            logger.warn("could not acquire file descriptor {}", fieldUri);
            throw new IOException("could not acquire file descriptor " + fieldUri);
        }
        return afd;
    }

    @Override
    public Object get(String key) {
        if(cursor != null) {
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                return cursor.getString(columnIndex);
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public Boolean getAsBoolean(String key) {
        if(cursor != null) {
            //cursor doesn't have a getBoolean() method, so we get an
            //int and truncate it (boolean values are stored in the DB
            //as integers)
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                int intValue = cursor.getInt(columnIndex);
                if(intValue == 0) {
                    return false;
                } else {
                    return true;
                }
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public Byte getAsByte(String key) {
        //cursor doesn't have a getByte() method, so we get an
        //int and truncate it (byte values are stored in the DB
        //as integers)
        if(cursor != null) {
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                return (byte) cursor.getInt(columnIndex);
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public byte[] getAsByteArray(String key) {
        if(cursor != null) {
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                return cursor.getBlob(columnIndex);
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public Double getAsDouble(String key) {
        if(cursor != null) {
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                return cursor.getDouble(columnIndex);
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public Float getAsFloat(String key) {
        if(cursor != null) {
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                return cursor.getFloat(columnIndex);
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public Integer getAsInteger(String key) {
        if(cursor != null) {
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                return cursor.getInt(columnIndex);
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public Long getAsLong(String key) {
        if(cursor != null) {
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                return cursor.getLong(columnIndex);
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public Short getAsShort(String key) {
        if(cursor != null) {
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                return cursor.getShort(columnIndex);
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public String getAsString(String key) {
        if(cursor != null) {
            int columnIndex = cursor.getColumnIndex(key);
            if(columnIndex != -1) {
                return cursor.getString(columnIndex);
            } else {
                logger.warn("Column {} doesn't appear to exist.  Skipping.", key);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
        if(cursor != null) {
            cursor.close();
        }
    }

}

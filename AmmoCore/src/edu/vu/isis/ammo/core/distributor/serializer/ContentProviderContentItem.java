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

import edu.vu.isis.ammo.core.distributor.NonConformingAmmoContentProvider;
import edu.vu.isis.ammo.core.distributor.TupleNotFoundException;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;



public class ContentProviderContentItem implements IContentItem {
    static final Logger logger = LoggerFactory.getLogger("dist.serializer.cursoritem");
    
    private Cursor cursor;
    private final ContentResolver resolver;
    private final Uri tupleUri;
    private Map<String, FieldType> fieldMap;
    private Map<String, Integer> columnIndexMap;
    
    public ContentProviderContentItem(Uri tupleUri, ContentResolver res) throws NonConformingAmmoContentProvider, TupleNotFoundException {
        this.cursor = null;
        this.resolver = res;
        this.tupleUri = tupleUri;
        
        //Preload the list of keys and their types
        Cursor serialMetaCursor = null;
        
        this.fieldMap = null;
        String[] serialOrder = null; //TODO: do we need this?  Should we even compute this?
        
        try {
            try {
                final Uri dUri = Uri.withAppendedPath(tupleUri, "_data_type");
                serialMetaCursor = resolver.query(dUri, null, null, null, null);
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
                fieldMap.put(key, FieldType.fromCode(value));
                serialOrder[ix] = key;
                ix++;
            }
        } finally {
            if (serialMetaCursor != null) {
                serialMetaCursor.close();
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
            return cursor.getString(cursor.getColumnIndex(key));
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
            int intValue = cursor.getInt(cursor.getColumnIndex(key));
            if(intValue == 0) {
                return false;
            } else {
                return true;
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
            return (byte) cursor.getInt(cursor.getColumnIndex(key));
        } else {
            return null;
        }
    }

    @Override
    public byte[] getAsByteArray(String key) {
        if(cursor != null) {
            return cursor.getBlob(cursor.getColumnIndex(key));
        } else {
            return null;
        }
    }

    @Override
    public Double getAsDouble(String key) {
        if(cursor != null) {
            return cursor.getDouble(cursor.getColumnIndex(key));
        } else {
            return null;
        }
    }

    @Override
    public Float getAsFloat(String key) {
        if(cursor != null) {
            return cursor.getFloat(cursor.getColumnIndex(key));
        } else {
            return null;
        }
    }

    @Override
    public Integer getAsInteger(String key) {
        if(cursor != null) {
            return cursor.getInt(cursor.getColumnIndex(key));
        } else {
            return null;
        }
    }

    @Override
    public Long getAsLong(String key) {
        if(cursor != null) {
            return cursor.getLong(cursor.getColumnIndex(key));
        } else {
            return null;
        }
    }

    @Override
    public Short getAsShort(String key) {
        if(cursor != null) {
            return cursor.getShort(cursor.getColumnIndex(key));
        } else {
            return null;
        }
    }

    @Override
    public String getAsString(String key) {
        if(cursor != null) {
            return cursor.getString(cursor.getColumnIndex(key));
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

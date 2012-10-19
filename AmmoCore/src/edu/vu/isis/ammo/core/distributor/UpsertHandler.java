
package edu.vu.isis.ammo.core.distributor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.util.AsyncQueryHelper.InsertResultHandler;

public class UpsertHandler extends AsyncQueryHandler {

    private static final Logger logger = LoggerFactory.getLogger("dist.upsert");
    private final ContentResolver resolver;
    private final int position;
    private final ContentValues cv;
    private final Uri provider;
    private final byte[] data;

    public UpsertHandler(
            final ContentResolver resolver, final int position,
            final ContentValues cv, final Uri provider, final byte[] data) 
    {
        super(resolver);
        this.position = position;
        this.cv = cv;
        this.provider = provider;
        this.data = data;
        this.resolver = resolver;
    }

    @Override
    protected void onInsertComplete(int token, Object cookie, Uri tupleUri) {
        logger.debug("insert complete {}:{}", token, tupleUri);
        if (tupleUri == null) {
            logger.warn("could not insert {} into {}", cv, provider);
            return;
        }
        logger.info("Deserialized Received message, content {}", cv);
        PLogger.TEST_FUNCTIONAL.info("cv: {}, provider:{}", cv, provider);
        if (this.position == data.length)
            return;

        // process the blobs
        final long tupleId = ContentUris.parseId(tupleUri);
        final Uri.Builder uriBuilder = provider.buildUpon();
        final Uri.Builder updateTuple = ContentUris.appendId(uriBuilder, tupleId);

        int position = this.position;

        final ByteBuffer dataBuff = ByteBuffer.wrap(this.data).order(ByteOrder.BIG_ENDIAN);
        position++; // move past the null terminator
        dataBuff.position(position);
        int blobCount = 0;
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

            // get the last three bytes of the length, to be used as
            // a simple
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
                return;
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

            // write the blob to the appropriate place
            switch (storageMarker) {
                case RequestSerializer.BLOB_MARKER_FIELD:
                    blobCount++;
                    cv.put(fieldName, blob);
                    break;
                default:
                    final Uri fieldUri = updateTuple.appendPath(fieldName).build();
                    try {
                        PLogger.API_STORE.debug("write blob uri=[{}]", fieldUri);
                        final OutputStream outstream = resolver
                                .openOutputStream(fieldUri);
                        if (outstream == null) {
                            logger.error(
                                    "failed to open output stream to content provider: {} ",
                                    fieldUri);
                            return;
                        }
                        outstream.write(blob);
                        outstream.close();
                    } catch (SQLiteException ex) {
                        logger.error("in provider {} could not open output stream {}",
                                fieldUri, ex.getLocalizedMessage());
                    } catch (FileNotFoundException ex) {
                        logger.error("blob file not found: {}", fieldUri, ex);
                    } catch (IOException ex) {
                        logger.error("error writing blob file: {}", fieldUri, ex);
                    }
            }
        }
        if (blobCount > 0) {
            try {
                PLogger.API_STORE.debug("insert blob uri=[{}]", provider);
                final Uri blobUri = this.resolver.insert(provider, cv);
                if (blobUri == null) {
                    logger.warn("could not insert {} into {}", cv, provider);
                    return;
                }
                logger.trace("Deserialized Received message blobs, content {}", cv);

            } catch (SQLiteException ex) {
                logger.warn("invalid sql blob insert", ex);
                return;
            } catch (IllegalArgumentException ex) {
                logger.warn("bad provider or blob values", ex);
                return;
            }
        }
        if (!(cookie instanceof InsertResultHandler)) {
            return;
        }
        final InsertResultHandler postProcessor = (InsertResultHandler) cookie;
        postProcessor.run(tupleUri);
    }

    @Override
    protected void onQueryComplete(int token, Object cookie, Cursor serialMetaCursor) {
        if (serialMetaCursor == null)
            return;
    }
}

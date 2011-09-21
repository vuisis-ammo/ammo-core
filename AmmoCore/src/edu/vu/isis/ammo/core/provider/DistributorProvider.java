package edu.vu.isis.ammo.core.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.Tables;
import edu.vu.isis.ammo.core.provider.DistributorSchema;



public class DistributorProvider extends ContentProvider {
	// =================================
	// Constants
	// =================================
	private static final UriMatcher uriMatcher;
	   static {
		   uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		   for (Tables table : Tables.values()) {
			   uriMatcher.addURI(DistributorSchema.AUTHORITY, table.n, table.ordinal());
		   }
	   }
	
	// =================================
	// Fields
	// =================================
	Logger logger = LoggerFactory.getLogger(PreferenceProvider.class);

	// =================================
	// Content Provider Overrides
	// =================================
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		return null;
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		final DistributorDataStore dds = new DistributorDataStore(getContext()).openRead();
		final Cursor c;
		switch(Tables.values()[uriMatcher.match(uri)]) {
		case POSTAL:
			c = dds.queryPostal(projection, selection, selectionArgs, sortOrder);
			break;
		case PUBLISH:
			c = dds.queryPublish(projection, selection, selectionArgs, sortOrder);
			break;
		case RETRIEVAL:
			c = dds.queryRetrieval(projection, selection, selectionArgs, sortOrder);
			break;
		case SUBSCRIBE:
			c = dds.querySubscribe(projection, selection, selectionArgs, sortOrder);
			break;
		case DISPOSAL:
			c = dds.queryDisposal(projection, selection, selectionArgs, sortOrder);
			break;
		case CHANNEL:
			c = dds.queryChannel(projection, selection, selectionArgs, sortOrder);
			break;
		default:
			c = null;
		}
		
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		return 0;
	}
}
